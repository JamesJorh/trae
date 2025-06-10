# 🚀 快速开始指南

这个指南将帮助你在5分钟内设置并使用Jenkins AI分析共享库。

## 📋 前置条件

- ✅ Jenkins 2.190+ (支持Pipeline)
- ✅ Git仓库（GitHub、GitLab等）
- ✅ AI API密钥（如DeepSeek、OpenAI等）
- ✅ 基本的Jenkins管理权限

## ⚡ 5分钟快速部署

### 步骤1: 上传到Git仓库

**选项A: 使用自动化脚本（推荐）**

```bash
# Windows用户
cd /d "d:\codes\新建文件夹\trae"
scripts\deploy.bat --init --repo https://github.com/your-org/jenkins-ai-lib.git

# Linux/Mac用户
cd "d:/codes/新建文件夹/trae"
chmod +x scripts/deploy.sh
./scripts/deploy.sh --init --repo https://github.com/your-org/jenkins-ai-lib.git
```

**选项B: 手动上传**

```bash
git init
git add .
git commit -m "初始化Jenkins AI分析共享库"
git remote add origin https://github.com/your-org/jenkins-ai-lib.git
git push -u origin main
```

### 步骤2: 在Jenkins中配置共享库

1. 进入 **Manage Jenkins** → **Configure System**
2. 找到 **Global Pipeline Libraries** 部分
3. 点击 **Add** 添加新库：
   - **Name**: `ai-analysis-lib`
   - **Default version**: `main`
   - **Retrieval method**: Modern SCM
   - **Source Code Management**: Git
   - **Project Repository**: `https://github.com/your-org/jenkins-ai-lib.git`
4. 点击 **Save**

### 步骤3: 配置AI API凭据

1. 进入 **Manage Jenkins** → **Manage Credentials**
2. 选择 **Global** 域
3. 点击 **Add Credentials**：
   - **Kind**: Username with password
   - **Username**: `ai-api-user`
   - **Password**: `你的AI API密钥`
   - **ID**: `ai-api-encrypted-key`
   - **Description**: `AI API密钥`
4. 点击 **OK**

### 步骤4: 测试共享库

创建一个新的Pipeline任务来测试：

```groovy
@Library('ai-analysis-lib') _

pipeline {
    agent any
    
    stages {
        stage('测试构建') {
            steps {
                echo "开始测试..."
                // 故意制造一个失败来测试AI分析
                sh 'exit 1'
            }
        }
    }
    
    post {
        failure {
            script {
                echo "构建失败，启动AI分析..."
                
                def result = aiAnalysis.analyzeFailure()
                aiAnalysis.printAnalysisResult(result)
                
                echo "✅ AI分析测试成功！"
            }
        }
    }
}
```

## 🎯 实际项目集成

### 基础集成

在你的现有Pipeline中添加AI分析：

```groovy
@Library('ai-analysis-lib') _

pipeline {
    agent any
    
    stages {
        // 你的现有构建阶段
        stage('Build') {
            steps {
                sh 'npm install'
                sh 'npm run build'
            }
        }
        
        stage('Test') {
            steps {
                sh 'npm test'
            }
        }
    }
    
    post {
        failure {
            script {
                // 添加AI分析
                def analysisResult = aiAnalysis.analyzeFailure()
                aiAnalysis.printAnalysisResult(analysisResult)
                
                // 可选：发送通知
                if (analysisResult.urgency == 'high') {
                    // 发送紧急通知逻辑
                    echo "🚨 高优先级问题需要立即处理！"
                }
            }
        }
    }
}
```

### 高级集成（包含通知）

```groovy
@Library('ai-analysis-lib') _

pipeline {
    agent any
    
    environment {
        SLACK_CHANNEL = '#ci-cd'
        EMAIL_RECIPIENTS = 'team@company.com'
    }
    
    stages {
        // 你的构建阶段
    }
    
    post {
        failure {
            script {
                // AI分析
                def analysisResult = aiAnalysis.analyzeFailure([
                    maxLogLines: 300,
                    timeout: 45
                ])
                
                // 发送通知
                aiNotification.sendNotification(analysisResult, [
                    email: [
                        enabled: true,
                        recipients: env.EMAIL_RECIPIENTS
                    ],
                    slack: [
                        enabled: true,
                        channel: env.SLACK_CHANNEL
                    ]
                ])
            }
        }
    }
}
```

## 🔧 常见配置

### 环境变量配置

在Jenkins全局环境变量中设置：

| 变量名 | 值 | 说明 |
|--------|----|----- |
| `DEFAULT_AI_API_URL` | `https://ark.cn-beijing.volces.com/api/v3` | AI API地址 |
| `DEFAULT_AI_TIMEOUT` | `30` | API超时时间 |
| `DEFAULT_RECIPIENTS` | `team@company.com` | 默认邮件接收者 |

### 不同AI服务配置

**DeepSeek API**:
```groovy
aiAnalysis.analyzeFailure([
    apiUrl: 'https://ark.cn-beijing.volces.com/api/v3',
    model: 'deepseek-v3-250324'
])
```

**OpenAI API**:
```groovy
aiAnalysis.analyzeFailure([
    apiUrl: 'https://api.openai.com/v1',
    model: 'gpt-4'
])
```

**Azure OpenAI**:
```groovy
aiAnalysis.analyzeFailure([
    apiUrl: 'https://your-resource.openai.azure.com',
    model: 'gpt-4'
])
```

## 🚨 故障排除

### 问题1: "共享库未找到"

**解决方案**:
1. 检查库名称是否正确：`@Library('ai-analysis-lib')`
2. 确认共享库已在Jenkins中正确配置
3. 验证Git仓库地址和凭据

### 问题2: "凭据未找到"

**解决方案**:
1. 检查凭据ID：`ai-api-encrypted-key`
2. 确认凭据在正确的域中
3. 验证Pipeline有权限访问凭据

### 问题3: "AI API调用失败"

**解决方案**:
1. 检查网络连接
2. 验证API密钥有效性
3. 确认API URL正确
4. 检查防火墙设置

### 问题4: "Groovy语法错误"

**解决方案**:
1. 检查Pipeline语法
2. 确认使用正确的函数名
3. 验证参数格式

## 📚 更多资源

- 📖 [完整文档](README.md)
- 🔐 [凭据配置指南](jenkins-setup/credentials-setup.md)
- 💡 [完整示例](examples/complete-pipeline-example.groovy)
- 🛠️ [API参考](README.md#🔧-api参考)

## 🆘 获取帮助

如果遇到问题：

1. 查看Jenkins构建日志
2. 检查[常见问题](README.md#🚨-故障排除)
3. 在GitHub上创建Issue
4. 联系DevOps团队

## 🎉 成功案例

配置成功后，你将看到类似这样的AI分析结果：

```
=== AI构建失败分析结果 ===
错误类型: dependency
具体类型: npm包依赖冲突
根本原因: package.json中的依赖版本冲突导致安装失败
解决方案: 更新package-lock.json并重新安装依赖
紧急程度: medium
需要运维: false
快速修复: 1. 删除node_modules和package-lock.json
          2. 运行npm install
          3. 提交更新的package-lock.json
预防措施: 定期更新依赖并使用npm audit检查安全问题
=========================
```

---

**🎊 恭喜！你已经成功设置了Jenkins AI分析共享库！**

现在你的CI/CD流程具备了智能分析能力，可以自动诊断构建失败并提供解决方案。