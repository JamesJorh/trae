#!/usr/bin/env groovy

/**
 * 完整的生产级Pipeline示例
 * 展示如何在真实项目中集成AI分析共享库
 * 
 * 这个示例包含：
 * - 多阶段构建流程
 * - 条件部署
 * - 完整的错误处理
 * - AI分析和通知
 * - 性能监控
 */

@Library('ai-analysis-lib') _

pipeline {
    agent any
    
    // 参数化构建
    parameters {
        choice(
            name: 'DEPLOY_ENV',
            choices: ['dev', 'staging', 'prod'],
            description: '部署环境'
        )
        booleanParam(
            name: 'SKIP_TESTS',
            defaultValue: false,
            description: '跳过测试阶段'
        )
        booleanParam(
            name: 'ENABLE_AI_ANALYSIS',
            defaultValue: true,
            description: '启用AI分析'
        )
    }

    environment {
        // 应用配置
        APP_NAME = 'my-awesome-app'
        APP_VERSION = "${env.BUILD_NUMBER}"
        
        // Docker配置
        DOCKER_REGISTRY = 'your-registry.com'
        DOCKER_IMAGE = "${DOCKER_REGISTRY}/${APP_NAME}:${APP_VERSION}"
        
        // AI分析配置
        AI_API_URL = "${env.DEFAULT_AI_API_URL ?: 'https://ark.cn-beijing.volces.com/api/v3'}"
        AI_TIMEOUT = "${env.DEFAULT_AI_TIMEOUT ?: '30'}"
        
        // 通知配置
        SLACK_CHANNEL = '#ci-cd'
        EMAIL_RECIPIENTS = 'dev-team@company.com'
    }

    stages {
        stage('🔍 环境检查') {
            steps {
                script {
                    echo "=== 环境信息 ==="
                    echo "构建节点: ${env.NODE_NAME}"
                    echo "工作空间: ${env.WORKSPACE}"
                    echo "部署环境: ${params.DEPLOY_ENV}"
                    echo "应用版本: ${APP_VERSION}"
                    
                    // 检查必要的工具
                    sh '''
                        echo "检查构建工具..."
                        node --version || echo "Node.js 未安装"
                        npm --version || echo "npm 未安装"
                        docker --version || echo "Docker 未安装"
                    '''
                }
            }
        }

        stage('📥 代码检出') {
            steps {
                echo "检出代码..."
                checkout scm
                
                script {
                    // 获取Git信息
                    env.GIT_COMMIT_SHORT = sh(
                        script: 'git rev-parse --short HEAD',
                        returnStdout: true
                    ).trim()
                    
                    env.GIT_AUTHOR = sh(
                        script: 'git log -1 --pretty=format:"%an"',
                        returnStdout: true
                    ).trim()
                }
                
                echo "提交信息: ${env.GIT_COMMIT_SHORT} by ${env.GIT_AUTHOR}"
            }
        }

        stage('📦 依赖安装') {
            steps {
                echo "安装项目依赖..."
                sh '''
                    # 清理缓存
                    npm cache clean --force
                    
                    # 安装依赖
                    npm ci --production=false
                    
                    # 检查安全漏洞
                    npm audit --audit-level=high
                '''
            }
        }

        stage('🔍 代码质量检查') {
            parallel {
                stage('ESLint检查') {
                    steps {
                        sh 'npm run lint'
                    }
                    post {
                        always {
                            publishHTML([
                                allowMissing: false,
                                alwaysLinkToLastBuild: true,
                                keepAll: true,
                                reportDir: 'reports',
                                reportFiles: 'eslint.html',
                                reportName: 'ESLint Report'
                            ])
                        }
                    }
                }
                
                stage('SonarQube分析') {
                    when {
                        anyOf {
                            branch 'main'
                            branch 'develop'
                        }
                    }
                    steps {
                        withSonarQubeEnv('SonarQube') {
                            sh 'npm run sonar'
                        }
                    }
                }
            }
        }

        stage('🏗️ 构建应用') {
            steps {
                echo "构建应用..."
                sh '''
                    # 构建前端资源
                    npm run build
                    
                    # 检查构建产物
                    ls -la dist/
                    
                    # 构建Docker镜像
                    docker build -t ${DOCKER_IMAGE} .
                    
                    # 检查镜像大小
                    docker images ${DOCKER_IMAGE}
                '''
            }
        }

        stage('🧪 运行测试') {
            when {
                not { params.SKIP_TESTS }
            }
            parallel {
                stage('单元测试') {
                    steps {
                        sh 'npm run test:unit'
                    }
                    post {
                        always {
                            publishTestResults(
                                testResultsPattern: 'reports/junit.xml'
                            )
                            publishCoverage(
                                adapters: [coberturaAdapter('reports/coverage/cobertura.xml')],
                                sourceFileResolver: sourceFiles('STORE_LAST_BUILD')
                            )
                        }
                    }
                }
                
                stage('集成测试') {
                    steps {
                        sh '''
                            # 启动测试环境
                            docker-compose -f docker-compose.test.yml up -d
                            
                            # 等待服务启动
                            sleep 30
                            
                            # 运行集成测试
                            npm run test:integration
                        '''
                    }
                    post {
                        always {
                            sh 'docker-compose -f docker-compose.test.yml down'
                        }
                    }
                }
            }
        }

        stage('🔒 安全扫描') {
            parallel {
                stage('依赖安全扫描') {
                    steps {
                        sh 'npm audit --json > security-audit.json || true'
                        archiveArtifacts artifacts: 'security-audit.json'
                    }
                }
                
                stage('Docker镜像扫描') {
                    steps {
                        script {
                            // 使用Trivy扫描Docker镜像
                            sh """
                                docker run --rm -v /var/run/docker.sock:/var/run/docker.sock \\
                                    aquasec/trivy image --format json --output trivy-report.json \\
                                    ${DOCKER_IMAGE}
                            """
                            archiveArtifacts artifacts: 'trivy-report.json'
                        }
                    }
                }
            }
        }

        stage('📤 推送镜像') {
            when {
                anyOf {
                    branch 'main'
                    branch 'develop'
                    expression { params.DEPLOY_ENV != 'dev' }
                }
            }
            steps {
                script {
                    withCredentials([usernamePassword(
                        credentialsId: 'docker-registry-credentials',
                        usernameVariable: 'DOCKER_USER',
                        passwordVariable: 'DOCKER_PASS'
                    )]) {
                        sh '''
                            echo "${DOCKER_PASS}" | docker login ${DOCKER_REGISTRY} -u "${DOCKER_USER}" --password-stdin
                            docker push ${DOCKER_IMAGE}
                            
                            # 如果是主分支，也推送latest标签
                            if [ "${BRANCH_NAME}" = "main" ]; then
                                docker tag ${DOCKER_IMAGE} ${DOCKER_REGISTRY}/${APP_NAME}:latest
                                docker push ${DOCKER_REGISTRY}/${APP_NAME}:latest
                            fi
                        '''
                    }
                }
            }
        }

        stage('🚀 部署应用') {
            when {
                anyOf {
                    allOf {
                        branch 'main'
                        expression { params.DEPLOY_ENV == 'prod' }
                    }
                    allOf {
                        branch 'develop'
                        expression { params.DEPLOY_ENV == 'staging' }
                    }
                    expression { params.DEPLOY_ENV == 'dev' }
                }
            }
            steps {
                script {
                    echo "部署到 ${params.DEPLOY_ENV} 环境..."
                    
                    // 根据环境选择部署策略
                    switch(params.DEPLOY_ENV) {
                        case 'prod':
                            // 生产环境：蓝绿部署
                            sh '''
                                kubectl set image deployment/${APP_NAME} \\
                                    ${APP_NAME}=${DOCKER_IMAGE} \\
                                    --namespace=production
                                
                                kubectl rollout status deployment/${APP_NAME} \\
                                    --namespace=production --timeout=300s
                            '''
                            break
                            
                        case 'staging':
                            // 测试环境：滚动更新
                            sh '''
                                kubectl set image deployment/${APP_NAME} \\
                                    ${APP_NAME}=${DOCKER_IMAGE} \\
                                    --namespace=staging
                                
                                kubectl rollout status deployment/${APP_NAME} \\
                                    --namespace=staging --timeout=180s
                            '''
                            break
                            
                        case 'dev':
                            // 开发环境：直接替换
                            sh '''
                                docker-compose -f docker-compose.dev.yml down
                                docker-compose -f docker-compose.dev.yml up -d
                            '''
                            break
                    }
                }
            }
        }

        stage('✅ 部署验证') {
            when {
                expression { params.DEPLOY_ENV != 'dev' }
            }
            steps {
                script {
                    def healthCheckUrl = getHealthCheckUrl(params.DEPLOY_ENV)
                    
                    echo "验证部署: ${healthCheckUrl}"
                    
                    // 健康检查
                    retry(5) {
                        sleep 10
                        sh """
                            curl -f ${healthCheckUrl}/health || exit 1
                        """
                    }
                    
                    // 烟雾测试
                    sh """
                        npm run test:smoke -- --baseUrl=${healthCheckUrl}
                    """
                }
            }
        }
    }

    post {
        always {
            echo "清理工作区..."
            
            // 清理Docker镜像
            sh '''
                docker image prune -f
                docker system prune -f
            '''
            
            // 归档构建产物
            archiveArtifacts artifacts: 'dist/**', allowEmptyArchive: true
            
            // 发布测试报告
            publishTestResults testResultsPattern: 'reports/**/*.xml'
        }
        
        success {
            script {
                echo "✅ 构建成功！"
                
                // 发送成功通知
                if (params.DEPLOY_ENV == 'prod') {
                    slackSend(
                        channel: env.SLACK_CHANNEL,
                        color: 'good',
                        message: "🎉 ${APP_NAME} v${APP_VERSION} 已成功部署到生产环境！\n" +
                                "提交: ${env.GIT_COMMIT_SHORT} by ${env.GIT_AUTHOR}\n" +
                                "构建链接: ${env.BUILD_URL}"
                    )
                }
            }
        }
        
        failure {
            script {
                echo "❌ 构建失败！"
                
                if (params.ENABLE_AI_ANALYSIS) {
                    echo "开始AI分析..."
                    
                    try {
                        // 执行AI分析
                        def analysisResult = aiAnalysis.analyzeFailure([
                            maxLogLines: 500,
                            timeout: 60,
                            model: 'deepseek-v3-250324'
                        ])
                        
                        // 输出分析结果
                        aiAnalysis.printAnalysisResult(analysisResult)
                        
                        // 发送详细通知
                        aiNotification.sendNotification(analysisResult, [
                            email: [
                                enabled: true,
                                recipients: env.EMAIL_RECIPIENTS,
                                attachLog: true
                            ],
                            slack: [
                                enabled: true,
                                channel: env.SLACK_CHANNEL
                            ]
                        ])
                        
                        // 根据分析结果采取行动
                        if (analysisResult.urgency == 'high') {
                            echo "🚨 检测到高优先级问题！"
                            
                            // 发送紧急通知
                            slackSend(
                                channel: '#alerts',
                                color: 'danger',
                                message: "🚨 URGENT: ${APP_NAME} 构建失败 - 高优先级问题\n" +
                                        "错误类型: ${analysisResult.error_category}\n" +
                                        "根本原因: ${analysisResult.root_cause}\n" +
                                        "需要立即处理！"
                            )
                        }
                        
                        if (analysisResult.requires_ops == true) {
                            echo "🔧 需要运维团队介入"
                            // 这里可以集成工单系统API
                        }
                        
                        // 保存分析结果
                        writeFile(
                            file: 'ai-analysis-result.json',
                            text: groovy.json.JsonOutput.prettyPrint(
                                groovy.json.JsonOutput.toJson(analysisResult)
                            )
                        )
                        archiveArtifacts artifacts: 'ai-analysis-result.json'
                        
                    } catch (Exception e) {
                        echo "AI分析失败: ${e.getMessage()}"
                        
                        // 发送基础失败通知
                        slackSend(
                            channel: env.SLACK_CHANNEL,
                            color: 'danger',
                            message: "❌ ${APP_NAME} 构建失败\n" +
                                    "构建: #${env.BUILD_NUMBER}\n" +
                                    "分支: ${env.BRANCH_NAME}\n" +
                                    "AI分析失败，请人工检查日志\n" +
                                    "构建链接: ${env.BUILD_URL}"
                        )
                    }
                } else {
                    echo "AI分析已禁用，发送基础通知"
                    
                    // 发送基础失败通知
                    emailext(
                        subject: "构建失败: ${APP_NAME} #${env.BUILD_NUMBER}",
                        body: "构建失败，请检查Jenkins日志: ${env.BUILD_URL}",
                        to: env.EMAIL_RECIPIENTS
                    )
                }
            }
        }
        
        unstable {
            echo "⚠️ 构建不稳定"
            slackSend(
                channel: env.SLACK_CHANNEL,
                color: 'warning',
                message: "⚠️ ${APP_NAME} 构建不稳定\n" +
                        "可能存在测试失败或质量问题\n" +
                        "构建链接: ${env.BUILD_URL}"
            )
        }
    }
}

// 辅助函数：获取健康检查URL
def getHealthCheckUrl(String env) {
    switch(env) {
        case 'prod':
            return 'https://api.company.com'
        case 'staging':
            return 'https://staging-api.company.com'
        case 'dev':
            return 'http://dev-api.company.com'
        default:
            return 'http://localhost:3000'
    }
}