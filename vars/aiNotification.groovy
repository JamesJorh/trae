#!/usr/bin/env groovy

/**
 * Jenkins共享库 - AI分析结果通知
 * 支持多种通知方式：邮件、Slack、钉钉、企业微信等
 */

/**
 * 发送AI分析结果通知
 * @param analysisResult AI分析结果
 * @param options 通知配置选项
 *   - email: 邮件通知配置
 *   - slack: Slack通知配置
 *   - dingtalk: 钉钉通知配置
 *   - wechat: 企业微信通知配置
 */
def sendNotification(Map analysisResult, Map options = [:]) {
    echo "开始发送AI分析结果通知..."
    
    // 构建通知内容
    def notificationContent = buildNotificationContent(analysisResult)
    
    // 发送邮件通知
    if (options.email?.enabled) {
        sendEmailNotification(analysisResult, notificationContent, options.email)
    }
    
    // 发送Slack通知
    if (options.slack?.enabled) {
        sendSlackNotification(analysisResult, notificationContent, options.slack)
    }
    
    // 发送钉钉通知
    if (options.dingtalk?.enabled) {
        sendDingtalkNotification(analysisResult, notificationContent, options.dingtalk)
    }
    
    // 发送企业微信通知
    if (options.wechat?.enabled) {
        sendWechatNotification(analysisResult, notificationContent, options.wechat)
    }
}

/**
 * 构建通知内容
 */
def buildNotificationContent(Map analysisResult) {
    def urgencyEmoji = getUrgencyEmoji(analysisResult.urgency)
    def statusEmoji = "❌"
    
    def content = [
        title: "${statusEmoji} Jenkins构建失败 - AI分析报告",
        summary: "项目 ${env.JOB_NAME} 构建 #${env.BUILD_NUMBER} 失败",
        details: [
            "🏷️ **错误类型**: ${analysisResult.error_category} - ${analysisResult.error_type}",
            "🔍 **根本原因**: ${analysisResult.root_cause}",
            "💡 **解决方案**: ${analysisResult.solution}",
            "${urgencyEmoji} **紧急程度**: ${analysisResult.urgency}",
            "🔧 **需要运维**: ${analysisResult.requires_ops ? '是' : '否'}",
            "⚡ **快速修复**: ${analysisResult.quick_fix}"
        ],
        metadata: [
            "项目": env.JOB_NAME,
            "构建号": env.BUILD_NUMBER,
            "分支": env.BRANCH_NAME ?: 'unknown',
            "构建时长": currentBuild.durationString,
            "构建链接": env.BUILD_URL
        ]
    ]
    
    if (analysisResult.prevention) {
        content.details.add("🛡️ **预防措施**: ${analysisResult.prevention}")
    }
    
    return content
}

/**
 * 获取紧急程度对应的emoji
 */
def getUrgencyEmoji(String urgency) {
    switch(urgency?.toLowerCase()) {
        case 'high':
            return "🚨"
        case 'medium':
            return "⚠️"
        case 'low':
            return "ℹ️"
        default:
            return "❓"
    }
}

/**
 * 发送邮件通知
 */
def sendEmailNotification(Map analysisResult, Map content, Map emailConfig) {
    try {
        def subject = "${content.title} - ${env.JOB_NAME} #${env.BUILD_NUMBER}"
        def body = buildEmailBody(content)
        
        emailext (
            subject: subject,
            body: body,
            to: emailConfig.recipients ?: '${DEFAULT_RECIPIENTS}',
            mimeType: 'text/html',
            attachLog: emailConfig.attachLog ?: false
        )
        
        echo "✅ 邮件通知发送成功"
    } catch (Exception e) {
        echo "❌ 邮件通知发送失败: ${e.getMessage()}"
    }
}

/**
 * 构建邮件正文
 */
def buildEmailBody(Map content) {
    def html = """
    <html>
    <head>
        <style>
            body { font-family: Arial, sans-serif; margin: 20px; }
            .header { background-color: #f8d7da; padding: 15px; border-radius: 5px; margin-bottom: 20px; }
            .content { background-color: #f8f9fa; padding: 15px; border-radius: 5px; }
            .metadata { background-color: #e9ecef; padding: 10px; border-radius: 5px; margin-top: 15px; }
            .detail-item { margin: 8px 0; }
            .metadata-item { margin: 5px 0; }
        </style>
    </head>
    <body>
        <div class="header">
            <h2>${content.title}</h2>
            <p>${content.summary}</p>
        </div>
        
        <div class="content">
            <h3>📋 分析详情</h3>
    """
    
    content.details.each { detail ->
        html += "<div class='detail-item'>${detail}</div>"
    }
    
    html += """
        </div>
        
        <div class="metadata">
            <h3>📊 构建信息</h3>
    """
    
    content.metadata.each { key, value ->
        html += "<div class='metadata-item'><strong>${key}:</strong> ${value}</div>"
    }
    
    html += """
        </div>
    </body>
    </html>
    """
    
    return html
}

/**
 * 发送Slack通知
 */
def sendSlackNotification(Map analysisResult, Map content, Map slackConfig) {
    try {
        def color = getSlackColor(analysisResult.urgency)
        def blocks = buildSlackBlocks(content)
        
        slackSend(
            channel: slackConfig.channel ?: '#ci-cd',
            color: color,
            blocks: blocks,
            teamDomain: slackConfig.teamDomain,
            token: slackConfig.token
        )
        
        echo "✅ Slack通知发送成功"
    } catch (Exception e) {
        echo "❌ Slack通知发送失败: ${e.getMessage()}"
    }
}

/**
 * 获取Slack颜色
 */
def getSlackColor(String urgency) {
    switch(urgency?.toLowerCase()) {
        case 'high':
            return 'danger'
        case 'medium':
            return 'warning'
        case 'low':
            return 'good'
        default:
            return '#808080'
    }
}

/**
 * 构建Slack消息块
 */
def buildSlackBlocks(Map content) {
    def blocks = [
        [
            type: "header",
            text: [
                type: "plain_text",
                text: content.title
            ]
        ],
        [
            type: "section",
            text: [
                type: "mrkdwn",
                text: content.summary
            ]
        ],
        [
            type: "divider"
        ]
    ]
    
    // 添加详情
    def detailsText = content.details.join("\n")
    blocks.add([
        type: "section",
        text: [
            type: "mrkdwn",
            text: detailsText
        ]
    ])
    
    // 添加构建链接按钮
    if (env.BUILD_URL) {
        blocks.add([
            type: "actions",
            elements: [
                [
                    type: "button",
                    text: [
                        type: "plain_text",
                        text: "查看构建详情"
                    ],
                    url: env.BUILD_URL,
                    style: "primary"
                ]
            ]
        ])
    }
    
    return groovy.json.JsonOutput.toJson(blocks)
}

/**
 * 发送钉钉通知
 */
def sendDingtalkNotification(Map analysisResult, Map content, Map dingtalkConfig) {
    try {
        def webhook = dingtalkConfig.webhook
        def message = buildDingtalkMessage(content)
        
        def response = sh(
            script: """
                curl -X POST "${webhook}" \\
                     -H "Content-Type: application/json" \\
                     -d '${groovy.json.JsonOutput.toJson(message)}'
            """,
            returnStdout: true
        )
        
        echo "✅ 钉钉通知发送成功"
    } catch (Exception e) {
        echo "❌ 钉钉通知发送失败: ${e.getMessage()}"
    }
}

/**
 * 构建钉钉消息
 */
def buildDingtalkMessage(Map content) {
    def text = "${content.title}\n\n${content.summary}\n\n"
    text += content.details.join("\n") + "\n\n"
    text += "构建链接: ${env.BUILD_URL}"
    
    return [
        msgtype: "text",
        text: [
            content: text
        ]
    ]
}

/**
 * 发送企业微信通知
 */
def sendWechatNotification(Map analysisResult, Map content, Map wechatConfig) {
    try {
        def webhook = wechatConfig.webhook
        def message = buildWechatMessage(content)
        
        def response = sh(
            script: """
                curl -X POST "${webhook}" \\
                     -H "Content-Type: application/json" \\
                     -d '${groovy.json.JsonOutput.toJson(message)}'
            """,
            returnStdout: true
        )
        
        echo "✅ 企业微信通知发送成功"
    } catch (Exception e) {
        echo "❌ 企业微信通知发送失败: ${e.getMessage()}"
    }
}

/**
 * 构建企业微信消息
 */
def buildWechatMessage(Map content) {
    def text = "${content.title}\n${content.summary}\n\n"
    text += content.details.join("\n") + "\n\n"
    text += "构建链接: ${env.BUILD_URL}"
    
    return [
        msgtype: "text",
        text: [
            content: text
        ]
    ]
}

return this