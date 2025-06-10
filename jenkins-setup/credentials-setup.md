# Jenkins凭据配置指南

本文档详细说明如何在Jenkins中配置AI分析共享库所需的凭据。

## 🔐 必需凭据

### 1. AI API密钥

**凭据ID**: `ai-api-encrypted-key`

**配置步骤**:

1. 进入 **Manage Jenkins** > **Manage Credentials**
2. 选择 **Global** 域（或适当的域）
3. 点击 **Add Credentials**
4. 填写以下信息：
   - **Kind**: `Username with password`
   - **Scope**: `Global (Jenkins, nodes, items, all child items, etc)`
   - **Username**: `ai-api-user` (可以是任意值，主要用于标识)
   - **Password**: `你的AI API密钥`
   - **ID**: `ai-api-encrypted-key`
   - **Description**: `AI API密钥 - 用于构建失败分析`

### 2. Git仓库凭据（如果使用私有仓库）

**凭据ID**: `github-credentials`

**配置步骤**:

1. 进入 **Manage Jenkins** > **Manage Credentials**
2. 选择 **Global** 域
3. 点击 **Add Credentials**
4. 根据认证方式选择：

#### 选项A: 用户名密码
- **Kind**: `Username with password`
- **Username**: 你的Git用户名
- **Password**: 你的Git密码或Personal Access Token
- **ID**: `github-credentials`

#### 选项B: SSH密钥
- **Kind**: `SSH Username with private key`
- **Username**: `git`
- **Private Key**: 选择 "Enter directly" 并粘贴你的私钥
- **ID**: `github-credentials`

### 3. Docker Registry凭据（可选）

**凭据ID**: `docker-registry-credentials`

**配置步骤**:

1. 进入 **Manage Jenkins** > **Manage Credentials**
2. 选择 **Global** 域
3. 点击 **Add Credentials**
4. 填写以下信息：
   - **Kind**: `Username with password`
   - **Username**: Docker Registry用户名
   - **Password**: Docker Registry密码
   - **ID**: `docker-registry-credentials`
   - **Description**: `Docker Registry凭据`

## 🌐 环境变量配置

### 全局环境变量

在 **Manage Jenkins** > **Configure System** > **Global properties** > **Environment variables** 中添加：

| 变量名 | 默认值 | 说明 |
|--------|--------|------|
| `DEFAULT_AI_API_URL` | `https://ark.cn-beijing.volces.com/api/v3` | AI API基础URL |
| `DEFAULT_AI_TIMEOUT` | `30` | API超时时间（秒） |
| `DEFAULT_RECIPIENTS` | `dev-team@company.com` | 默认邮件接收者 |
| `SLACK_TEAM_DOMAIN` | `your-company` | Slack团队域名 |

### Pipeline级别环境变量

在Pipeline中可以覆盖全局设置：

```groovy
pipeline {
    agent any
    
    environment {
        // 覆盖AI API配置
        AI_API_URL = 'https://api.openai.com/v1'
        AI_TIMEOUT = '45'
        
        // 项目特定配置
        SLACK_CHANNEL = '#my-project'
        EMAIL_RECIPIENTS = 'my-team@company.com'
    }
    
    // ... 其他配置
}
```

## 🔒 安全最佳实践

### 1. 凭据范围控制

- 使用最小权限原则
- 为不同环境创建不同的凭据
- 定期轮换API密钥

### 2. 访问控制

```groovy
// 限制凭据使用范围
withCredentials([usernamePassword(
    credentialsId: 'ai-api-encrypted-key',
    usernameVariable: 'AI_API_USER',
    passwordVariable: 'AI_API_KEY'
)]) {
    // 只在这个块中可以访问凭据
    // 凭据不会泄露到日志中
}
```

### 3. 日志安全

- 确保敏感信息不会出现在构建日志中
- 使用 `withCredentials` 包装敏感操作
- 避免在脚本中直接打印凭据值

## 🧪 凭据测试

### 测试AI API凭据

创建一个简单的测试Pipeline：

```groovy
pipeline {
    agent any
    
    stages {
        stage('测试AI API') {
            steps {
                script {
                    withCredentials([usernamePassword(
                        credentialsId: 'ai-api-encrypted-key',
                        usernameVariable: 'AI_API_USER',
                        passwordVariable: 'AI_API_KEY'
                    )]) {
                        def response = sh(
                            script: '''
                                curl -s -X POST "${DEFAULT_AI_API_URL}/chat/completions" \\
                                     -H "Authorization: Bearer ${AI_API_KEY}" \\
                                     -H "Content-Type: application/json" \\
                                     -d '{
                                       "model": "deepseek-v3-250324",
                                       "messages": [
                                         {
                                           "role": "user",
                                           "content": "Hello, this is a test."
                                         }
                                       ],
                                       "max_tokens": 10
                                     }' \\
                                     --max-time 10
                            ''',
                            returnStdout: true
                        )
                        
                        if (response.contains('"choices"')) {
                            echo "✅ AI API凭据测试成功"
                        } else {
                            error "❌ AI API凭据测试失败"
                        }
                    }
                }
            }
        }
    }
}
```

### 测试Git凭据

```groovy
pipeline {
    agent any
    
    stages {
        stage('测试Git访问') {
            steps {
                script {
                    try {
                        checkout([
                            $class: 'GitSCM',
                            branches: [[name: '*/main']],
                            userRemoteConfigs: [[
                                url: 'https://github.com/your-org/your-repo.git',
                                credentialsId: 'github-credentials'
                            ]]
                        ])
                        echo "✅ Git凭据测试成功"
                    } catch (Exception e) {
                        error "❌ Git凭据测试失败: ${e.getMessage()}"
                    }
                }
            }
        }
    }
}
```

## 🚨 故障排除

### 常见问题

#### 1. "凭据未找到"错误

**症状**: `Credentials 'ai-api-encrypted-key' not found`

**解决方案**:
- 检查凭据ID是否正确
- 确认凭据在正确的域中
- 验证Pipeline有权限访问凭据

#### 2. API调用失败

**症状**: `curl: (7) Failed to connect to API`

**解决方案**:
- 检查网络连接
- 验证API URL是否正确
- 确认API密钥有效
- 检查防火墙设置

#### 3. 权限被拒绝

**症状**: `Permission denied` 或 `403 Forbidden`

**解决方案**:
- 检查API密钥权限
- 验证用户角色和权限
- 确认凭据配置正确

### 调试技巧

1. **启用详细日志**:
   ```groovy
   sh 'curl -v ...'  // 添加 -v 参数查看详细输出
   ```

2. **测试网络连接**:
   ```groovy
   sh 'ping -c 3 api.example.com'
   sh 'nslookup api.example.com'
   ```

3. **检查环境变量**:
   ```groovy
   sh 'env | grep -i api'
   ```

## 📞 支持

如果遇到凭据配置问题：

1. 检查Jenkins日志：`Manage Jenkins` > `System Log`
2. 查看构建日志中的详细错误信息
3. 参考[Jenkins官方文档](https://www.jenkins.io/doc/book/using/using-credentials/)
4. 联系系统管理员或DevOps团队

---

**注意**: 请妥善保管所有凭据信息，不要在代码或文档中明文存储敏感信息。