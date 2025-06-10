#!/usr/bin/env groovy

/**
 * Jenkins共享库 - AI构建失败分析
 * 使用方法：
 * @Library('your-shared-library') _
 * 
 * pipeline {
 *     // ... 其他配置
 *     post {
 *         failure {
 *             script {
 *                 def result = aiAnalysis.analyzeFailure()
 *                 echo "AI分析结果: ${result}"
 *             }
 *         }
 *     }
 * }
 */

/**
 * 分析构建失败的主入口函数
 * @param options 配置选项
 *   - maxLogLines: 最大日志行数 (默认: 200)
 *   - timeout: API超时时间 (默认: 30秒)
 *   - model: AI模型名称 (默认: deepseek-v3-250324)
 *   - credentialsId: Jenkins凭据ID (默认: ai-api-encrypted-key)
 *   - apiUrl: AI API地址 (默认: 从环境变量获取)
 * @return Map 包含分析结果的Map对象
 */
def analyzeFailure(Map options = [:]) {
    // 设置默认参数
    def config = [
        maxLogLines: options.maxLogLines ?: 200,
        timeout: options.timeout ?: 30,
        model: options.model ?: 'deepseek-v3-250324',
        credentialsId: options.credentialsId ?: 'ai-api-encrypted-key',
        apiUrl: options.apiUrl ?: (env.DEFAULT_AI_API_URL ?: 'https://ark.cn-beijing.volces.com/api/v3')
    ]
    
    echo "开始AI分析构建失败..."
    
    // 使用凭据包装AI分析调用
    return withCredentials([usernamePassword(
        credentialsId: config.credentialsId,
        usernameVariable: 'AI_API_USER',
        passwordVariable: 'AI_API_KEY'
    )]) {
        try {
            // 1. 提取相关日志
            def buildLog = extractRelevantLog(config.maxLogLines)
            
            // 2. 构建分析上下文
            def analysisContext = buildAnalysisContext(buildLog)
            
            // 3. 调用AI API
            def aiResponse = callAIAPI(analysisContext, config)
            
            // 4. 解析并返回结果
            def result = parseAIResponse(aiResponse)
            
            echo "AI分析完成"
            return result
            
        } catch (Exception e) {
            echo "AI分析失败: ${e.getMessage()}"
            return getDefaultAnalysisResult("AI分析异常: ${e.getMessage()}")
        }
    }
}

/**
 * 提取相关的构建日志
 * @param maxLines 最大日志行数
 * @return String 相关的日志内容
 */
def extractRelevantLog(int maxLines) {
    def fullLog = currentBuild.rawBuild.getLog(maxLines)
    def relevantLines = []
    
    // 错误关键词
    def errorKeywords = [
        'ERROR', 'FATAL', 'Failed', 'Exception', 'error:', 
        'npm ERR!', 'BUILD FAILED', 'FAILURE', 'maven-surefire-plugin',
        'compilation failed', 'Test failed', 'docker build failed',
        'permission denied', 'connection refused', 'timeout'
    ]
    
    def foundError = false
    
    for (int i = 0; i < fullLog.size(); i++) {
        def line = fullLog[i]
        
        // 检查是否包含错误关键词
        if (errorKeywords.any { keyword -> line.toLowerCase().contains(keyword.toLowerCase()) }) {
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

/**
 * 构建AI分析的上下文信息
 * @param logContent 日志内容
 * @return String 格式化的分析上下文
 */
def buildAnalysisContext(String logContent) {
    def context = """
项目信息:
- 项目名称: ${env.JOB_NAME}
- 构建编号: ${env.BUILD_NUMBER}
- 分支: ${env.BRANCH_NAME ?: 'unknown'}
- 构建时长: ${currentBuild.durationString}
- 触发原因: ${currentBuild.getBuildCauses()?.get(0)?.shortDescription ?: 'unknown'}
- 构建节点: ${env.NODE_NAME ?: 'unknown'}
- 工作空间: ${env.WORKSPACE ?: 'unknown'}

构建失败日志:
${logContent}

请分析上述Jenkins构建失败日志，并返回JSON格式的分析结果：
{
  "error_category": "错误分类(network/dependency/code/docker/deployment/permission/test/compilation)",
  "error_type": "具体错误类型",
  "root_cause": "根本原因分析",
  "solution": "具体解决方案",
  "urgency": "紧急程度(low/medium/high)",
  "requires_ops": "是否需要运维介入(true/false)",
  "quick_fix": "快速修复步骤",
  "prevention": "预防措施"
}

请确保返回有效的JSON格式，用中文分析。
"""
    return context
}

/**
 * 调用AI API进行分析
 * @param context 分析上下文
 * @param config 配置参数
 * @return String AI API的响应内容
 */
def callAIAPI(String context, Map config) {
    def requestBody = [
        model: config.model,
        messages: [
            [
                role: "system",
                content: "你是一个专业的DevOps工程师，擅长分析Jenkins构建失败问题。请提供准确、实用的分析和解决方案。请用中文分析错误，并严格按照JSON格式返回结果。"
            ],
            [
                role: "user", 
                content: context
            ]
        ],
        temperature: 0.1,
        max_tokens: 1500
    ]
    
    // 将请求体写入临时文件
    def requestBodyJson = groovy.json.JsonOutput.toJson(requestBody)
    def tempFile = "ai_request_${env.BUILD_NUMBER}_${System.currentTimeMillis()}.json"
    writeFile file: tempFile, text: requestBodyJson
    
    try {
        // 使用curl命令调用API
        def curlCommand = """
            curl -s -X POST "${config.apiUrl}/chat/completions" \\
                 -H "Authorization: Bearer \${AI_API_KEY}" \\
                 -H "Content-Type: application/json" \\
                 -d @${tempFile} \\
                 --max-time ${config.timeout}
        """
        
        def response = sh(
            script: curlCommand,
            returnStdout: true
        ).trim()
        
        return response
        
    } finally {
        // 清理临时文件
        sh "rm -f ${tempFile}"
    }
}

/**
 * 解析AI API的响应
 * @param responseContent AI API响应内容
 * @return Map 解析后的分析结果
 */
def parseAIResponse(String responseContent) {
    try {
        def jsonResponse = readJSON(text: responseContent)
        
        // 检查API响应是否成功
        if (jsonResponse.error) {
            echo "AI API返回错误: ${jsonResponse.error.message}"
            return getDefaultAnalysisResult("AI API错误: ${jsonResponse.error.message}")
        }
        
        def aiContent = jsonResponse.choices[0].message.content

        // 提取JSON部分
        def jsonStart = aiContent.indexOf('{')
        def jsonEnd = aiContent.lastIndexOf('}') + 1

        if (jsonStart >= 0 && jsonEnd > jsonStart) {
            def jsonText = aiContent.substring(jsonStart, jsonEnd)
            def result = readJSON(text: jsonText)
            
            // 验证必要字段
            if (!result.error_category || !result.root_cause || !result.solution) {
                echo "AI响应缺少必要字段"
                return getDefaultAnalysisResult("AI响应格式不完整")
            }
            
            return result
        } else {
            echo "AI响应中未找到有效JSON"
            return getDefaultAnalysisResult("AI响应格式错误")
        }

    } catch (Exception e) {
        echo "解析AI响应失败: ${e.getMessage()}"
        echo "原始响应内容: ${responseContent}"
        return getDefaultAnalysisResult("响应解析失败: ${e.getMessage()}")
    }
}

/**
 * 获取默认的分析结果
 * @param reason 失败原因
 * @return Map 默认分析结果
 */
def getDefaultAnalysisResult(String reason) {
    return [
        error_category: "unknown",
        error_type: "分析失败",
        root_cause: reason,
        solution: "请人工分析构建日志，检查网络连接和API配置",
        urgency: "medium",
        requires_ops: true,
        quick_fix: "1. 检查构建日志中的错误信息\n2. 验证AI API配置\n3. 联系运维团队",
        prevention: "定期检查AI服务可用性"
    ]
}

/**
 * 格式化输出分析结果
 * @param result 分析结果Map
 */
def printAnalysisResult(Map result) {
    echo "=== AI构建失败分析结果 ==="
    echo "错误类型: ${result.error_category}"
    echo "具体类型: ${result.error_type}"
    echo "根本原因: ${result.root_cause}"
    echo "解决方案: ${result.solution}"
    echo "紧急程度: ${result.urgency}"
    echo "需要运维: ${result.requires_ops}"
    echo "快速修复: ${result.quick_fix}"
    if (result.prevention) {
        echo "预防措施: ${result.prevention}"
    }
    echo "========================="
}

// 返回this以支持链式调用
return this