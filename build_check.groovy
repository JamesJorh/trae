pipeline {
    agent any

    environment {
        // 使用全局环境变量
        AI_API_URL = "${env.DEFAULT_AI_API_URL ?: 'https://ark.cn-beijing.volces.com/api/v3'}"
        AI_TIMEOUT = "${env.DEFAULT_AI_TIMEOUT ?: '30'}"
    }

    stages {
        stage('Checkout') {
            steps {
                echo "检出代码..."
            }
        }

        stage('Build') {
            steps {
                echo "构建中：制造一个失败..."
                sh '''
                    echo "开始构建..."
                    echo "模拟语法错误"
                    some_invalid_command_that_does_not_exist
                '''
            }
        }
    }

    post {
        failure {
            script {
                echo "构建失败，开始AI分析..."
                
                // 使用withCredentials包装AI分析调用
                withCredentials([usernamePassword(
                    credentialsId: 'ai-api-encrypted-key',
                    usernameVariable: 'AI_API_USER',
                    passwordVariable: 'AI_API_KEY'
                )]) {
                    try {
                        def analysisResult = analyzeFailureWithAI()
                        echo "=== AI分析结果 ==="
                        echo "错误类型: ${analysisResult.error_category}"
                        echo "根本原因: ${analysisResult.root_cause}"
                        echo "解决方案: ${analysisResult.solution}"
                        echo "紧急程度: ${analysisResult.urgency}"
                        echo "需要运维: ${analysisResult.requires_ops}"
                        echo "快速修复: ${analysisResult.quick_fix}"
                    } catch (Exception e) {
                        echo "AI分析失败: ${e.getMessage()}"
                        echo "请人工分析构建日志"
                    }
                }
            }
        }

        always {
            echo "清理工作区..."
        }
    }
}

// AI分析函数
def analyzeFailureWithAI() {
    // 1. 提取最后200行构建日志
    def buildLog = extractRelevantLog(200)
    
    // 2. 构建分析上下文
    def analysisContext = buildAnalysisContext(buildLog)
    
    // 3. 调用AI API（此时AI_API_KEY环境变量已被注入）
    def aiResponse = callAIAPI(analysisContext)
    
    // 4. 解析并返回结果
    return parseAIResponse(aiResponse)
}

// 提取相关日志
def extractRelevantLog(int maxLines) {
    def fullLog = currentBuild.rawBuild.getLog(maxLines)
    def relevantLines = []
    
    // 寻找错误关键词
    def errorKeywords = ['ERROR', 'FATAL', 'Failed', 'Exception', 'error:', 'npm ERR!', 'BUILD FAILED']
    def foundError = false
    
    for (int i = 0; i < fullLog.size(); i++) {
        def line = fullLog[i]
        
        // 检查是否包含错误关键词
        if (errorKeywords.any { keyword -> line.contains(keyword) }) {
            foundError = true
            // 添加错误前后的上下文
            def startIdx = Math.max(0, i - 5)
            def endIdx = Math.min(fullLog.size() - 1, i + 10)
            
            for (int j = startIdx; j <= endIdx; j++) {
                if (!relevantLines.contains(fullLog[j])) {
                    relevantLines.add(fullLog[j])
                }
            }
        }
    }
    
    // 如果没有找到明显错误，返回最后50行
    if (!foundError) {
        def startIdx = Math.max(0, fullLog.size() - 50)
        relevantLines = fullLog[startIdx..-1]
    }
    
    return relevantLines.join('\n')
}

// 构建分析上下文
def buildAnalysisContext(String logContent) {
    def context = """
项目信息:
- 项目名称: ${env.JOB_NAME}
- 构建编号: ${env.BUILD_NUMBER}
- 分支: ${env.BRANCH_NAME ?: 'unknown'}
- 构建时长: ${currentBuild.durationString}
- 触发原因: ${currentBuild.getBuildCauses()?.get(0)?.shortDescription ?: 'unknown'}

构建失败日志:
${logContent}

请分析上述Jenkins构建失败日志，并返回JSON格式的分析结果：
{
  "error_category": "错误分类(network/dependency/code/docker/deployment/permission)",
  "error_type": "具体错误类型",
  "root_cause": "根本原因分析",
  "solution": "具体解决方案",
  "urgency": "紧急程度(low/medium/high)",
  "requires_ops": "是否需要运维介入(true/false)",
  "quick_fix": "快速修复步骤"
}

请确保返回有效的JSON格式。
"""
    return context
}

// 调用AI API
def callAIAPI(String context) {
    def requestBody = [
        model: "deepseek-v3-250324",  // 或你们使用的模型名称
        messages: [
            [
                role: "system",
                content: "你是一个专业的DevOps工程师，擅长分析Jenkins构建失败问题。请提供准确、实用的分析和解决方案。请用中文分析错误"
            ],
            [
                role: "user", 
                content: context
            ]
        ],
        temperature: 0.1,
        max_tokens: 1000
    ]
    
    // 将请求体写入临时文件
    def requestBodyJson = groovy.json.JsonOutput.toJson(requestBody)
    writeFile file: 'ai_request.json', text: requestBodyJson
    
    // 使用curl命令调用API，避免在httpRequest中直接暴露敏感变量
    def curlCommand = """
        curl -s -X POST "${env.AI_API_URL}/chat/completions" \\
             -H "Authorization: Bearer \${AI_API_KEY}" \\
             -H "Content-Type: application/json" \\
             -d @ai_request.json \\
             --max-time ${env.AI_TIMEOUT}
    """
    
    def response = sh(
        script: curlCommand,
        returnStdout: true
    ).trim()
    
    // 清理临时文件
    sh 'rm -f ai_request.json'
    
    return response
}

// 解析AI响应
def parseAIResponse(String responseContent) {
    try {
        def jsonResponse = readJSON(text: responseContent)
        def aiContent = jsonResponse.choices[0].message.content

        // 提取JSON部分
        def jsonStart = aiContent.indexOf('{')
        def jsonEnd = aiContent.lastIndexOf('}') + 1

        if (jsonStart >= 0 && jsonEnd > jsonStart) {
            def jsonText = aiContent.substring(jsonStart, jsonEnd)
            return readJSON(text: jsonText)
        }

    } catch (Exception e) {
        echo "解析AI响应失败: ${e.getMessage()}"
        echo "原始响应内容: ${responseContent}"
    }

    // 降级处理：返回默认分析结果
    return [
        error_category: "unknown",
        error_type: "解析失败",
        root_cause: "AI分析结果解析异常",
        solution: "请人工分析构建日志",
        urgency: "medium",
        requires_ops: true,
        quick_fix: "检查构建日志中的错误信息"
    ]
}
