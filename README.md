# Jenkins AI分析共享库

这是一个Jenkins共享库，提供AI驱动的构建失败分析功能，帮助快速定位和解决CI/CD流程中的问题。

## 🚀 功能特性

- **智能分析**: 使用AI分析构建失败日志，提供根本原因和解决方案
- **多种通知**: 支持邮件、Slack、钉钉、企业微信等多种通知方式
- **易于集成**: 标准Jenkins共享库，可在任何Pipeline中使用
- **可配置**: 支持自定义AI模型、超时时间、日志行数等参数
- **安全**: 使用Jenkins凭据管理敏感信息

## 📁 项目结构

```
trae/
├── vars/                           # 共享库函数
│   ├── aiAnalysis.groovy          # AI分析核心功能
│   └── aiNotification.groovy      # 通知功能
├── examples/                       # 使用示例
│   └── pipeline-with-ai-analysis.groovy
├── build_check.groovy             # 原始pipeline示例
└── README.md                       # 项目文档
```

## 🛠️ 安装配置

### 1. 上传到Git仓库

将整个项目上传到你的Git仓库（GitHub、GitLab等）。

### 2. 在Jenkins中配置共享库

1. 进入 **Manage Jenkins** > **Configure System**
2. 找到 **Global Pipeline Libraries** 部分
3. 点击 **Add** 添加新库：
   - **Name**: `ai-analysis-lib` (或你喜欢的名称)
   - **Default version**: `main` (或你的默认分支)
   - **Retrieval method**: Modern SCM
   - **Source Code Management**: Git
   - **Project Repository**: 你的Git仓库地址
   - **Credentials**: 如果是私有仓库，选择相应凭据

### 3. 配置AI API凭据

1. 进入 **Manage Jenkins** > **Manage Credentials**
2. 选择合适的域（通常是Global）
3. 点击 **Add Credentials**：
   - **Kind**: Username with password
   - **Username**: 可以留空或填写API用户名
   - **Password**: 填写你的AI API Key
   - **ID**: `ai-api-encrypted-key`
   - **Description**: AI API密钥

### 4. 配置环境变量（可选）

在Jenkins全局环境变量中设置：
- `DEFAULT_AI_API_URL`: AI API地址（默认: https://ark.cn-beijing.volces.com/api/v3）
- `DEFAULT_AI_TIMEOUT`: API超时时间（默认: 30秒）

## 📖 使用方法

### 基础用法

```groovy
@Library('ai-analysis-lib') _

pipeline {
    agent any
    
    stages {
        stage('Build') {
            steps {
                // 你的构建步骤
                sh 'make build'
            }
        }
    }
    
    post {
        failure {
            script {
                // 使用AI分析构建失败
                def result = aiAnalysis.analyzeFailure()
                aiAnalysis.printAnalysisResult(result)
            }
        }
    }
}
```

### 高级用法

```groovy
@Library('ai-analysis-lib') _

pipeline {
    agent any
    
    post {
        failure {
            script {
                // 自定义配置的AI分析
                def analysisResult = aiAnalysis.analyzeFailure([
                    maxLogLines: 300,                    // 分析最近300行日志
                    timeout: 45,                         // API超时45秒
                    model: 'deepseek-v3-250324',        // AI模型
                    credentialsId: 'my-ai-api-key',     // 自定义凭据ID
                    apiUrl: 'https://api.openai.com/v1' // 自定义API地址
                ])
                
                // 发送通知
                aiNotification.sendNotification(analysisResult, [
                    email: [
                        enabled: true,
                        recipients: 'team@company.com'
                    ],
                    slack: [
                        enabled: true,
                        channel: '#ci-cd',
                        teamDomain: 'your-team',
                        token: 'your-slack-token'
                    ],
                    dingtalk: [
                        enabled: true,
                        webhook: 'https://oapi.dingtalk.com/robot/send?access_token=xxx'
                    ]
                ])
                
                // 根据分析结果采取行动
                if (analysisResult.urgency == 'high') {
                    // 高优先级问题的处理逻辑
                    echo "🚨 高优先级问题，立即处理！"
                }
            }
        }
    }
}
```

## 🔧 API参考

### aiAnalysis.analyzeFailure(options)

分析构建失败的主函数。

**参数:**
- `maxLogLines` (int): 最大日志行数，默认200
- `timeout` (int): API超时时间（秒），默认30
- `model` (string): AI模型名称，默认'deepseek-v3-250324'
- `credentialsId` (string): Jenkins凭据ID，默认'ai-api-encrypted-key'
- `apiUrl` (string): AI API地址

**返回值:**
```json
{
  "error_category": "错误分类",
  "error_type": "具体错误类型",
  "root_cause": "根本原因分析",
  "solution": "解决方案",
  "urgency": "紧急程度(low/medium/high)",
  "requires_ops": "是否需要运维介入(true/false)",
  "quick_fix": "快速修复步骤",
  "prevention": "预防措施"
}
```

### aiAnalysis.printAnalysisResult(result)

格式化输出分析结果。

### aiNotification.sendNotification(analysisResult, options)

发送分析结果通知。

**通知配置选项:**

```groovy
[
    email: [
        enabled: true,
        recipients: 'team@company.com',
        attachLog: false
    ],
    slack: [
        enabled: true,
        channel: '#ci-cd',
        teamDomain: 'your-team',
        token: 'your-slack-token'
    ],
    dingtalk: [
        enabled: true,
        webhook: 'https://oapi.dingtalk.com/robot/send?access_token=xxx'
    ],
    wechat: [
        enabled: true,
        webhook: 'https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=xxx'
    ]
]
```

## 🔒 安全注意事项

1. **API密钥安全**: 始终使用Jenkins凭据管理API密钥，不要硬编码在代码中
2. **网络访问**: 确保Jenkins节点可以访问AI API服务
3. **日志敏感信息**: AI分析会读取构建日志，确保日志中不包含敏感信息
4. **权限控制**: 合理设置共享库的使用权限

## 🚀 部署到生产环境

### 1. 版本管理

建议使用Git标签管理版本：

```bash
git tag -a v1.0.0 -m "Release version 1.0.0"
git push origin v1.0.0
```

在Jenkins中可以指定使用特定版本：

```groovy
@Library('ai-analysis-lib@v1.0.0') _
```

### 2. 环境隔离

为不同环境配置不同的共享库版本：
- 开发环境: `@Library('ai-analysis-lib@develop')`
- 测试环境: `@Library('ai-analysis-lib@staging')`
- 生产环境: `@Library('ai-analysis-lib@v1.0.0')`

### 3. 监控和日志

- 监控AI API的调用成功率和响应时间
- 定期检查共享库的使用情况
- 收集用户反馈，持续改进分析准确性

## 🤝 贡献指南

1. Fork本项目
2. 创建特性分支 (`git checkout -b feature/amazing-feature`)
3. 提交更改 (`git commit -m 'Add some amazing feature'`)
4. 推送到分支 (`git push origin feature/amazing-feature`)
5. 创建Pull Request

## 📝 更新日志

### v1.0.0 (2024-01-XX)
- 初始版本发布
- 支持AI构建失败分析
- 支持多种通知方式
- 完整的文档和示例

## 📄 许可证

本项目采用 MIT 许可证 - 查看 [LICENSE](LICENSE) 文件了解详情。

## 🆘 支持

如果你遇到问题或有建议，请：

1. 查看[常见问题](docs/FAQ.md)
2. 搜索现有的[Issues](../../issues)
3. 创建新的Issue描述你的问题

## 🙏 致谢

感谢所有贡献者和使用者的支持！

---

**Happy Building! 🚀**