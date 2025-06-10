#!/usr/bin/env groovy

/**
 * 使用AI分析共享库的Pipeline示例
 * 
 * 使用前需要在Jenkins中配置共享库：
 * 1. 进入 Manage Jenkins > Configure System
 * 2. 找到 Global Pipeline Libraries 部分
 * 3. 添加库配置：
 *    - Name: ai-analysis-lib (或你喜欢的名称)
 *    - Default version: main (或你的默认分支)
 *    - Retrieval method: Modern SCM
 *    - Source Code Management: Git
 *    - Project Repository: 你的Git仓库地址
 */

@Library('ai-analysis-lib') _

pipeline {
    agent any

    environment {
        // AI API配置 - 可以在Jenkins全局环境变量中设置
        AI_API_URL = "${env.DEFAULT_AI_API_URL ?: 'https://ark.cn-beijing.volces.com/api/v3'}"
        AI_TIMEOUT = "${env.DEFAULT_AI_TIMEOUT ?: '30'}"
    }

    stages {
        stage('Checkout') {
            steps {
                echo "检出代码..."
                // checkout scm
            }
        }

        stage('Build') {
            steps {
                echo "开始构建..."
                // 这里放你的实际构建步骤
                script {
                    // 模拟构建失败
                    sh '''
                        echo "模拟构建过程..."
                        echo "ERROR: 构建失败 - 找不到依赖包"
                        exit 1
                    '''
                }
            }
        }

        stage('Test') {
            steps {
                echo "运行测试..."
                // 你的测试步骤
            }
        }

        stage('Deploy') {
            when {
                branch 'main'
            }
            steps {
                echo "部署到生产环境..."
                // 你的部署步骤
            }
        }
    }

    post {
        failure {
            script {
                echo "构建失败，启动AI分析..."
                
                // 使用共享库进行AI分析
                def analysisResult = aiAnalysis.analyzeFailure([
                    maxLogLines: 300,           // 分析最近300行日志
                    timeout: 45,                // API超时45秒
                    model: 'deepseek-v3-250324', // 使用的AI模型
                    credentialsId: 'ai-api-encrypted-key' // Jenkins凭据ID
                ])
                
                // 格式化输出结果
                aiAnalysis.printAnalysisResult(analysisResult)
                
                // 根据分析结果采取不同行动
                if (analysisResult.urgency == 'high') {
                    echo "⚠️ 高优先级问题，发送紧急通知"
                    // 发送紧急通知逻辑
                }
                
                if (analysisResult.requires_ops == true) {
                    echo "🔧 需要运维介入，创建运维工单"
                    // 创建运维工单逻辑
                }
                
                // 可以将分析结果保存到文件或发送到其他系统
                writeFile file: 'ai-analysis-result.json', 
                         text: groovy.json.JsonOutput.prettyPrint(
                             groovy.json.JsonOutput.toJson(analysisResult)
                         )
                
                // 归档分析结果
                archiveArtifacts artifacts: 'ai-analysis-result.json', 
                               allowEmptyArchive: true
            }
        }
        
        success {
            echo "✅ 构建成功！"
        }
        
        always {
            echo "清理工作区..."
            // 清理步骤
        }
    }
}