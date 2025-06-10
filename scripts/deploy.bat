@echo off
setlocal enabledelayedexpansion

REM Jenkins AI分析共享库部署脚本 (Windows版本)
REM 用于快速设置和部署共享库到Git仓库

REM 设置变量
set "REPO_URL="
set "BRANCH=main"
set "TAG="
set "MESSAGE="
set "FORCE=false"
set "INIT_REPO=false"
set "VALIDATE=false"
set "TEST=false"

REM 颜色代码 (Windows 10+)
set "RED=[91m"
set "GREEN=[92m"
set "YELLOW=[93m"
set "BLUE=[94m"
set "NC=[0m"

REM 日志函数
:log_info
echo %BLUE%[INFO]%NC% %~1
goto :eof

:log_success
echo %GREEN%[SUCCESS]%NC% %~1
goto :eof

:log_warning
echo %YELLOW%[WARNING]%NC% %~1
goto :eof

:log_error
echo %RED%[ERROR]%NC% %~1
goto :eof

REM 显示帮助信息
:show_help
echo Jenkins AI分析共享库部署脚本 (Windows版本)
echo.
echo 用法: %~nx0 [选项]
echo.
echo 选项:
echo     -r, --repo URL          Git仓库URL
echo     -b, --branch BRANCH     目标分支 (默认: main)
echo     -t, --tag TAG          创建标签
echo     -m, --message MSG      提交消息
echo     -f, --force            强制推送
echo     -h, --help             显示此帮助信息
echo     --init                 初始化新仓库
echo     --validate             验证库结构
echo     --test                 运行测试
echo.
echo 示例:
echo     %~nx0 --repo https://github.com/your-org/jenkins-ai-lib.git
echo     %~nx0 --repo git@github.com:your-org/jenkins-ai-lib.git --tag v1.0.0
echo     %~nx0 --init --repo https://github.com/your-org/jenkins-ai-lib.git
echo.
goto :eof

REM 检查必要工具
:check_prerequisites
call :log_info "检查必要工具..."

REM 检查Git
git --version >nul 2>&1
if errorlevel 1 (
    call :log_error "Git未安装或不在PATH中"
    call :log_error "请安装Git后重试: https://git-scm.com/download/win"
    exit /b 1
)

REM 检查curl (Windows 10 1803+自带)
curl --version >nul 2>&1
if errorlevel 1 (
    call :log_warning "curl未找到，某些功能可能不可用"
) else (
    call :log_success "curl已安装"
)

call :log_success "Git已安装"
goto :eof

REM 验证库结构
:validate_structure
call :log_info "验证共享库结构..."

REM 检查必要目录
if not exist "vars" (
    call :log_error "缺少必要目录: vars"
    exit /b 1
)
if not exist "examples" (
    call :log_error "缺少必要目录: examples"
    exit /b 1
)
if not exist "jenkins-setup" (
    call :log_error "缺少必要目录: jenkins-setup"
    exit /b 1
)

REM 检查必要文件
if not exist "README.md" (
    call :log_error "缺少必要文件: README.md"
    exit /b 1
)
if not exist "vars\aiAnalysis.groovy" (
    call :log_error "缺少必要文件: vars\aiAnalysis.groovy"
    exit /b 1
)
if not exist "vars\aiNotification.groovy" (
    call :log_error "缺少必要文件: vars\aiNotification.groovy"
    exit /b 1
)

REM 检查Groovy文件
call :log_info "检查Groovy语法..."
for %%f in (vars\*.groovy) do (
    findstr /c:"return this" "%%f" >nul
    if errorlevel 1 (
        call :log_warning "%%f 可能缺少 'return this' 语句"
    )
)

call :log_success "库结构验证通过"
goto :eof

REM 运行测试
:run_tests
call :log_info "运行测试..."

REM 检查示例Pipeline语法
call :log_info "检查示例Pipeline语法..."
for %%f in (examples\*.groovy) do (
    findstr /c:"@Library" "%%f" >nul && findstr /c:"pipeline {" "%%f" >nul
    if not errorlevel 1 (
        call :log_success "%%f 语法检查通过"
    ) else (
        call :log_warning "%%f 可能存在语法问题"
    )
)

REM 检查文档完整性
call :log_info "检查文档完整性..."
findstr /c:"## 🚀 功能特性" README.md >nul && findstr /c:"## 📖 使用方法" README.md >nul
if not errorlevel 1 (
    call :log_success "README.md 文档完整"
) else (
    call :log_warning "README.md 可能不完整"
)

call :log_success "测试完成"
goto :eof

REM 初始化Git仓库
:init_repo
call :log_info "初始化Git仓库..."

if exist ".git" (
    call :log_warning "Git仓库已存在"
) else (
    git init
    call :log_success "Git仓库初始化完成"
)

REM 添加远程仓库
if not "%REPO_URL%"=="" (
    git remote get-url origin >nul 2>&1
    if not errorlevel 1 (
        call :log_info "更新远程仓库URL"
        git remote set-url origin "%REPO_URL%"
    ) else (
        call :log_info "添加远程仓库"
        git remote add origin "%REPO_URL%"
    )
    call :log_success "远程仓库配置完成: %REPO_URL%"
)

REM 创建.gitignore
if not exist ".gitignore" (
    (
        echo # IDE文件
        echo .vscode/
        echo .idea/
        echo *.swp
        echo *.swo
        echo *~
        echo.
        echo # 临时文件
        echo *.tmp
        echo *.log
        echo .DS_Store
        echo Thumbs.db
        echo.
        echo # 构建产物
        echo build/
        echo dist/
        echo target/
        echo.
        echo # 敏感信息
        echo *.key
        echo *.pem
        echo .env
        echo .env.local
    ) > .gitignore
    call :log_success "创建.gitignore文件"
)
goto :eof

REM 部署到Git仓库
:deploy_to_git
call :log_info "部署到Git仓库..."

REM 检查是否有未提交的更改
git status --porcelain | findstr /r ".*" >nul
if not errorlevel 1 (
    call :log_info "添加文件到Git..."
    git add .
    
    call :log_info "提交更改..."
    if "%MESSAGE%"=="" (
        git commit -m "更新Jenkins AI分析共享库"
    ) else (
        git commit -m "%MESSAGE%"
    )
    call :log_success "文件已提交"
) else (
    call :log_info "没有需要提交的更改"
)

REM 推送到远程仓库
if not "%REPO_URL%"=="" (
    call :log_info "推送到远程仓库 (%BRANCH% 分支)..."
    
    if "%FORCE%"=="true" (
        git push -f origin %BRANCH%
    ) else (
        git push origin %BRANCH%
    )
    
    call :log_success "推送完成"
    
    REM 创建标签
    if not "%TAG%"=="" (
        call :log_info "创建标签: %TAG%"
        git tag -a "%TAG%" -m "Release %TAG%"
        git push origin "%TAG%"
        call :log_success "标签 %TAG% 已创建并推送"
    )
)
goto :eof

REM 生成部署报告
:generate_report
call :log_info "生成部署报告..."

REM 获取当前时间
for /f "tokens=1-4 delims=/ " %%a in ('date /t') do set "current_date=%%a %%b %%c %%d"
for /f "tokens=1-2 delims=: " %%a in ('time /t') do set "current_time=%%a:%%b"

REM 获取Git提交哈希
for /f "delims=" %%a in ('git rev-parse HEAD 2^>nul') do set "commit_hash=%%a"
if "%commit_hash%"=="" set "commit_hash=无"

(
    echo # Jenkins AI分析共享库部署报告
    echo.
    echo ## 部署信息
    echo.
    echo - **部署时间**: %current_date% %current_time%
    if not "%REPO_URL%"=="" (
        echo - **Git仓库**: %REPO_URL%
    ) else (
        echo - **Git仓库**: 本地仓库
    )
    echo - **分支**: %BRANCH%
    if not "%TAG%"=="" (
        echo - **标签**: %TAG%
    ) else (
        echo - **标签**: 无
    )
    echo - **提交哈希**: %commit_hash%
    echo.
    echo ## 库结构
    echo.
    echo ```
    dir /s /b | findstr /v ".git"
    echo ```
    echo.
    echo ## 下一步操作
    echo.
    echo 1. **在Jenkins中配置共享库**:
    echo    - 进入 Manage Jenkins ^> Configure System
    echo    - 添加 Global Pipeline Libraries
    if not "%REPO_URL%"=="" (
        echo    - 配置仓库地址: %REPO_URL%
    ) else (
        echo    - 配置仓库地址: 你的仓库地址
    )
    echo.
    echo 2. **配置必要凭据**:
    echo    - AI API密钥: `ai-api-encrypted-key`
    echo    - Git凭据: `github-credentials` ^(如果是私有仓库^)
    echo.
    echo 3. **测试共享库**:
    echo    ```groovy
    echo    @Library^('ai-analysis-lib'^) _
    echo    
    echo    pipeline {
    echo        agent any
    echo        stages {
    echo            stage^('Test'^) {
    echo                steps {
    echo                    script {
    echo                        def result = aiAnalysis.analyzeFailure^(^)
    echo                        echo "测试成功: ${result}"
    echo                    }
    echo                }
    echo            }
    echo        }
    echo    }
    echo    ```
    echo.
    echo ## 参考文档
    echo.
    echo - [README.md]^(README.md^) - 完整使用文档
    echo - [凭据配置指南]^(jenkins-setup/credentials-setup.md^)
    echo - [示例Pipeline]^(examples/^)
    echo.
) > deployment-report.md

call :log_success "部署报告已生成: deployment-report.md"
goto :eof

REM 解析命令行参数
:parse_args
:parse_loop
if "%~1"=="" goto :parse_done

if "%~1"=="-r" (
    set "REPO_URL=%~2"
    shift
    shift
    goto :parse_loop
)
if "%~1"=="--repo" (
    set "REPO_URL=%~2"
    shift
    shift
    goto :parse_loop
)
if "%~1"=="-b" (
    set "BRANCH=%~2"
    shift
    shift
    goto :parse_loop
)
if "%~1"=="--branch" (
    set "BRANCH=%~2"
    shift
    shift
    goto :parse_loop
)
if "%~1"=="-t" (
    set "TAG=%~2"
    shift
    shift
    goto :parse_loop
)
if "%~1"=="--tag" (
    set "TAG=%~2"
    shift
    shift
    goto :parse_loop
)
if "%~1"=="-m" (
    set "MESSAGE=%~2"
    shift
    shift
    goto :parse_loop
)
if "%~1"=="--message" (
    set "MESSAGE=%~2"
    shift
    shift
    goto :parse_loop
)
if "%~1"=="-f" (
    set "FORCE=true"
    shift
    goto :parse_loop
)
if "%~1"=="--force" (
    set "FORCE=true"
    shift
    goto :parse_loop
)
if "%~1"=="--init" (
    set "INIT_REPO=true"
    shift
    goto :parse_loop
)
if "%~1"=="--validate" (
    set "VALIDATE=true"
    shift
    goto :parse_loop
)
if "%~1"=="--test" (
    set "TEST=true"
    shift
    goto :parse_loop
)
if "%~1"=="-h" (
    call :show_help
    exit /b 0
)
if "%~1"=="--help" (
    call :show_help
    exit /b 0
)

call :log_error "未知参数: %~1"
call :show_help
exit /b 1

:parse_done
goto :eof

REM 主函数
:main
REM 解析命令行参数
call :parse_args %*

REM 显示欢迎信息
echo %BLUE%
echo ======================================
echo   Jenkins AI分析共享库部署脚本
echo ======================================
echo %NC%

REM 检查必要工具
call :check_prerequisites
if errorlevel 1 exit /b 1

REM 验证库结构
if "%VALIDATE%"=="true" (
    call :validate_structure
    if errorlevel 1 exit /b 1
)
if "%TEST%"=="true" (
    call :validate_structure
    if errorlevel 1 exit /b 1
)

REM 运行测试
if "%TEST%"=="true" (
    call :run_tests
)

REM 初始化仓库
if "%INIT_REPO%"=="true" (
    call :init_repo
)

REM 部署到Git
if not "%REPO_URL%"=="" (
    call :deploy_to_git
    call :generate_report
) else (
    if exist ".git" (
        call :deploy_to_git
        call :generate_report
    ) else (
        call :log_warning "未指定Git仓库，跳过部署步骤"
        call :log_info "使用 --repo 参数指定仓库地址，或使用 --init 初始化本地仓库"
    )
)

REM 显示完成信息
echo %GREEN%
echo ======================================
echo            部署完成！
echo ======================================
echo %NC%

if not "%REPO_URL%"=="" (
    call :log_info "仓库地址: %REPO_URL%"
    call :log_info "分支: %BRANCH%"
    if not "%TAG%"=="" (
        call :log_info "标签: %TAG%"
    )
)

call :log_info "请查看 deployment-report.md 了解下一步操作"

goto :eof

REM 执行主函数
call :main %*