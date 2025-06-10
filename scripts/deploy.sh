#!/bin/bash

# Jenkins AI分析共享库部署脚本
# 用于快速设置和部署共享库到Git仓库

set -e  # 遇到错误立即退出

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 日志函数
log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

log_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# 检查必要工具
check_prerequisites() {
    log_info "检查必要工具..."
    
    local missing_tools=()
    
    if ! command -v git &> /dev/null; then
        missing_tools+=("git")
    fi
    
    if ! command -v curl &> /dev/null; then
        missing_tools+=("curl")
    fi
    
    if [ ${#missing_tools[@]} -ne 0 ]; then
        log_error "缺少必要工具: ${missing_tools[*]}"
        log_error "请安装缺少的工具后重试"
        exit 1
    fi
    
    log_success "所有必要工具已安装"
}

# 显示帮助信息
show_help() {
    cat << EOF
Jenkins AI分析共享库部署脚本

用法: $0 [选项]

选项:
    -r, --repo URL          Git仓库URL
    -b, --branch BRANCH     目标分支 (默认: main)
    -t, --tag TAG          创建标签
    -m, --message MSG      提交消息
    -f, --force            强制推送
    -h, --help             显示此帮助信息
    --init                 初始化新仓库
    --validate             验证库结构
    --test                 运行测试

示例:
    $0 --repo https://github.com/your-org/jenkins-ai-lib.git
    $0 --repo git@github.com:your-org/jenkins-ai-lib.git --tag v1.0.0
    $0 --init --repo https://github.com/your-org/jenkins-ai-lib.git

EOF
}

# 验证库结构
validate_structure() {
    log_info "验证共享库结构..."
    
    local required_dirs=("vars" "examples" "jenkins-setup")
    local required_files=("README.md" "vars/aiAnalysis.groovy" "vars/aiNotification.groovy")
    
    # 检查目录
    for dir in "${required_dirs[@]}"; do
        if [ ! -d "$dir" ]; then
            log_error "缺少必要目录: $dir"
            return 1
        fi
    done
    
    # 检查文件
    for file in "${required_files[@]}"; do
        if [ ! -f "$file" ]; then
            log_error "缺少必要文件: $file"
            return 1
        fi
    done
    
    # 检查Groovy语法
    log_info "检查Groovy语法..."
    for groovy_file in vars/*.groovy; do
        if [ -f "$groovy_file" ]; then
            # 基本语法检查（检查括号匹配等）
            if ! grep -q "return this" "$groovy_file"; then
                log_warning "$groovy_file 可能缺少 'return this' 语句"
            fi
        fi
    done
    
    log_success "库结构验证通过"
}

# 运行测试
run_tests() {
    log_info "运行测试..."
    
    # 检查示例Pipeline语法
    log_info "检查示例Pipeline语法..."
    for pipeline_file in examples/*.groovy; do
        if [ -f "$pipeline_file" ]; then
            # 基本语法检查
            if grep -q "@Library" "$pipeline_file" && grep -q "pipeline {" "$pipeline_file"; then
                log_success "$pipeline_file 语法检查通过"
            else
                log_warning "$pipeline_file 可能存在语法问题"
            fi
        fi
    done
    
    # 检查文档完整性
    log_info "检查文档完整性..."
    if grep -q "## 🚀 功能特性" README.md && grep -q "## 📖 使用方法" README.md; then
        log_success "README.md 文档完整"
    else
        log_warning "README.md 可能不完整"
    fi
    
    log_success "测试完成"
}

# 初始化Git仓库
init_repo() {
    local repo_url="$1"
    
    log_info "初始化Git仓库..."
    
    if [ -d ".git" ]; then
        log_warning "Git仓库已存在"
    else
        git init
        log_success "Git仓库初始化完成"
    fi
    
    # 添加远程仓库
    if [ -n "$repo_url" ]; then
        if git remote get-url origin &> /dev/null; then
            log_info "更新远程仓库URL"
            git remote set-url origin "$repo_url"
        else
            log_info "添加远程仓库"
            git remote add origin "$repo_url"
        fi
        log_success "远程仓库配置完成: $repo_url"
    fi
    
    # 创建.gitignore
    if [ ! -f ".gitignore" ]; then
        cat > .gitignore << EOF
# IDE文件
.vscode/
.idea/
*.swp
*.swo
*~

# 临时文件
*.tmp
*.log
.DS_Store
Thumbs.db

# 构建产物
build/
dist/
target/

# 敏感信息
*.key
*.pem
.env
.env.local
EOF
        log_success "创建.gitignore文件"
    fi
}

# 部署到Git仓库
deploy_to_git() {
    local repo_url="$1"
    local branch="$2"
    local tag="$3"
    local message="$4"
    local force="$5"
    
    log_info "部署到Git仓库..."
    
    # 检查是否有未提交的更改
    if [ -n "$(git status --porcelain)" ]; then
        log_info "添加文件到Git..."
        git add .
        
        log_info "提交更改..."
        git commit -m "${message:-"更新Jenkins AI分析共享库"}"
        log_success "文件已提交"
    else
        log_info "没有需要提交的更改"
    fi
    
    # 推送到远程仓库
    if [ -n "$repo_url" ]; then
        log_info "推送到远程仓库 ($branch 分支)..."
        
        if [ "$force" = "true" ]; then
            git push -f origin "$branch"
        else
            git push origin "$branch"
        fi
        
        log_success "推送完成"
        
        # 创建标签
        if [ -n "$tag" ]; then
            log_info "创建标签: $tag"
            git tag -a "$tag" -m "Release $tag"
            git push origin "$tag"
            log_success "标签 $tag 已创建并推送"
        fi
    fi
}

# 生成部署报告
generate_report() {
    local repo_url="$1"
    local branch="$2"
    local tag="$3"
    
    log_info "生成部署报告..."
    
    cat > deployment-report.md << EOF
# Jenkins AI分析共享库部署报告

## 部署信息

- **部署时间**: $(date '+%Y-%m-%d %H:%M:%S')
- **Git仓库**: ${repo_url:-"本地仓库"}
- **分支**: ${branch:-"main"}
- **标签**: ${tag:-"无"}
- **提交哈希**: $(git rev-parse HEAD 2>/dev/null || echo "无")

## 库结构

\`\`\`
$(tree -I '.git' 2>/dev/null || find . -type f -not -path './.git/*' | sort)
\`\`\`

## 下一步操作

1. **在Jenkins中配置共享库**:
   - 进入 Manage Jenkins > Configure System
   - 添加 Global Pipeline Libraries
   - 配置仓库地址: ${repo_url:-"你的仓库地址"}

2. **配置必要凭据**:
   - AI API密钥: \`ai-api-encrypted-key\`
   - Git凭据: \`github-credentials\` (如果是私有仓库)

3. **测试共享库**:
   \`\`\`groovy
   @Library('ai-analysis-lib') _
   
   pipeline {
       agent any
       stages {
           stage('Test') {
               steps {
                   script {
                       def result = aiAnalysis.analyzeFailure()
                       echo "测试成功: \${result}"
                   }
               }
           }
       }
   }
   \`\`\`

## 参考文档

- [README.md](README.md) - 完整使用文档
- [凭据配置指南](jenkins-setup/credentials-setup.md)
- [示例Pipeline](examples/)

EOF

    log_success "部署报告已生成: deployment-report.md"
}

# 主函数
main() {
    local repo_url=""
    local branch="main"
    local tag=""
    local message=""
    local force="false"
    local init_repo_flag="false"
    local validate_flag="false"
    local test_flag="false"
    
    # 解析命令行参数
    while [[ $# -gt 0 ]]; do
        case $1 in
            -r|--repo)
                repo_url="$2"
                shift 2
                ;;
            -b|--branch)
                branch="$2"
                shift 2
                ;;
            -t|--tag)
                tag="$2"
                shift 2
                ;;
            -m|--message)
                message="$2"
                shift 2
                ;;
            -f|--force)
                force="true"
                shift
                ;;
            --init)
                init_repo_flag="true"
                shift
                ;;
            --validate)
                validate_flag="true"
                shift
                ;;
            --test)
                test_flag="true"
                shift
                ;;
            -h|--help)
                show_help
                exit 0
                ;;
            *)
                log_error "未知参数: $1"
                show_help
                exit 1
                ;;
        esac
    done
    
    # 显示欢迎信息
    echo -e "${BLUE}"
    echo "======================================"
    echo "  Jenkins AI分析共享库部署脚本"
    echo "======================================"
    echo -e "${NC}"
    
    # 检查必要工具
    check_prerequisites
    
    # 验证库结构
    if [ "$validate_flag" = "true" ] || [ "$test_flag" = "true" ]; then
        validate_structure
    fi
    
    # 运行测试
    if [ "$test_flag" = "true" ]; then
        run_tests
    fi
    
    # 初始化仓库
    if [ "$init_repo_flag" = "true" ]; then
        init_repo "$repo_url"
    fi
    
    # 部署到Git
    if [ -n "$repo_url" ] || [ -d ".git" ]; then
        deploy_to_git "$repo_url" "$branch" "$tag" "$message" "$force"
        generate_report "$repo_url" "$branch" "$tag"
    else
        log_warning "未指定Git仓库，跳过部署步骤"
        log_info "使用 --repo 参数指定仓库地址，或使用 --init 初始化本地仓库"
    fi
    
    # 显示完成信息
    echo -e "${GREEN}"
    echo "======================================"
    echo "           部署完成！"
    echo "======================================"
    echo -e "${NC}"
    
    if [ -n "$repo_url" ]; then
        log_info "仓库地址: $repo_url"
        log_info "分支: $branch"
        if [ -n "$tag" ]; then
            log_info "标签: $tag"
        fi
    fi
    
    log_info "请查看 deployment-report.md 了解下一步操作"
}

# 执行主函数
main "$@"