# ======================================================================
# Campus Library Borrowing System - 全栈一键启动器 (PowerShell)
# ======================================================================
$ErrorActionPreference = "Continue"

# 统一工具链探测，以便在启动前就给出可读的缺依赖提示
. "$PSScriptRoot\toolchain.ps1"

if (Test-Path "$PSScriptRoot\backend") {
    $rootDir = $PSScriptRoot
    $scriptsDir = "$rootDir\scripts"
} else {
    $rootDir = (Resolve-Path "$PSScriptRoot\..").Path
    $scriptsDir = $PSScriptRoot
}

Write-Host "======================================================================" -ForegroundColor Cyan
Write-Host "           📚 校园图书借阅系统 (Campus Library) - 全栈一键启动器" -ForegroundColor Yellow
Write-Host "======================================================================" -ForegroundColor Cyan
Write-Host "项目根目录: $rootDir" -ForegroundColor Gray
Write-Host ""

# ----------------------------------------------------------------------
# 0. 前置检查：工具链与端口占用
# ----------------------------------------------------------------------
Write-Host "[0/4] 前置环境检查..." -ForegroundColor Green

$javaHome = Resolve-JavaHome
if ($javaHome) {
    Write-Host "      JDK:     $javaHome" -ForegroundColor Gray
} else {
    Write-Host "      [警告] 未检测到 JDK，后端将无法启动。请安装 JDK 21 并设置 JAVA_HOME。" -ForegroundColor Yellow
}

$flutterCmd = Resolve-FlutterCmd
if ($flutterCmd) {
    Write-Host "      Flutter: $flutterCmd" -ForegroundColor Gray
} else {
    Write-Host "      [警告] 未检测到 Flutter SDK，前端将无法启动。请设置 FLUTTER_ROOT。" -ForegroundColor Yellow
}

# 端口占用检查：避免"启动成功"的假象
$backendPort = if ($env:SERVER_PORT) { [int]$env:SERVER_PORT } else { 8080 }
$occupiers = @(Get-NetTCPConnection -LocalPort $backendPort -State Listen -ErrorAction SilentlyContinue)
if ($occupiers.Count -gt 0) {
    $ownerPid = $occupiers[0].OwningProcess
    $proc = Get-CimInstance Win32_Process -Filter "ProcessId=$ownerPid" -ErrorAction SilentlyContinue
    $isOurs = $proc -and ($proc.CommandLine -match 'com\.library\.Application' -or $proc.CommandLine -match [regex]::Escape($rootDir))

    # 直接中止，而不是"警告之后照样启动"：
    # 端口被占时后端窗口必然绑定失败退出，前端却会照常跑起来，
    # 用户最终只看到界面上的「无法连接服务器」，真正的原因留在另一个窗口里。
    Write-Host ""
    Write-Host "======================================================================" -ForegroundColor Red
    if ($isOurs) {
        Write-Host " [中止] 端口 $backendPort 上已经有本项目的后端在运行（PID=$ownerPid）。" -ForegroundColor Red
        Write-Host "        如需重启，请先执行 stop-all.bat 停止旧实例。" -ForegroundColor Yellow
        Write-Host "        如需并行再起一份，请换端口：`$env:SERVER_PORT=28080; .\scripts\start-all.ps1" -ForegroundColor Yellow
    } else {
        Write-Host " [中止] 端口 $backendPort 已被其它进程占用，后端无法启动。" -ForegroundColor Red
        Write-Host "        占用进程: PID=$ownerPid ($($proc.Name))" -ForegroundColor Yellow
        Write-Host "        请先结束该进程；或换端口启动（前端会自动跟随）：" -ForegroundColor Yellow
        Write-Host "          `$env:SERVER_PORT=28080; .\scripts\start-all.ps1" -ForegroundColor Yellow
    }
    Write-Host "======================================================================" -ForegroundColor Red
    Write-Host ""
    Write-Host "按任意键退出本窗口..." -ForegroundColor Gray
    try { $null = $Host.UI.RawUI.ReadKey("NoEcho,IncludeKeyDown") } catch { Start-Sleep -Seconds 5 }
    exit 1
}
Write-Host "      端口检查: $backendPort 可用" -ForegroundColor Gray
Write-Host ""

# ----------------------------------------------------------------------
# 1. 检查并启动 Docker 中间件 (PostgreSQL 17 + Redis 8)
# ----------------------------------------------------------------------
Write-Host "[1/4] 正在检查并启动基础设施容器 (PostgreSQL 17 + Redis 8)..." -ForegroundColor Green

# ----------------------------------------------------------------------
# Docker 守护进程预检 (Stage 10-N)
#
# 实际踩过的坑：机器重启后 Docker Desktop 没有随之启动，而脚本此前只打印一句警告
# 就继续去拉后端 —— 后端连不上数据库与 Redis，必然启动失败，用户看到的仍然是
# "前端在跑、后端连不上"，与端口冲突那次是同一种迷惑状态。
# 因此这里改成：能自动拉起 Docker 就拉起，起不来就明确中止。
# ----------------------------------------------------------------------
function Test-DockerDaemon {
    if (-not (Get-Command docker -ErrorAction SilentlyContinue)) { return $false }
    docker info *> $null
    return ($LASTEXITCODE -eq 0)
}

if (-not (Test-DockerDaemon)) {
    Write-Host "      [提示] Docker 守护进程未运行，尝试启动 Docker Desktop..." -ForegroundColor Yellow
    $dockerDesktopCandidates = @(
        (Join-Path $env:LOCALAPPDATA "Programs\DockerDesktop\Docker Desktop.exe"),
        "C:\Program Files\Docker\Docker\Docker Desktop.exe"
    )
    $desktopExe = $dockerDesktopCandidates | Where-Object { Test-Path $_ } | Select-Object -First 1
    if ($desktopExe) {
        try { Start-Process $desktopExe } catch {}
        for ($i = 1; $i -le 24; $i++) {
            Start-Sleep -Seconds 5
            if (Test-DockerDaemon) { break }
            Write-Host -NoNewline "."
        }
        Write-Host ""
    }
}

if (-not (Test-DockerDaemon)) {
    Write-Host ""
    Write-Host "======================================================================" -ForegroundColor Red
    Write-Host " [中止] Docker 守护进程不可用，PostgreSQL / Redis 无法启动。" -ForegroundColor Red
    Write-Host "        没有数据库与缓存，后端必然启动失败，因此这里不再继续。" -ForegroundColor Yellow
    Write-Host "" -ForegroundColor Yellow
    Write-Host " 处理方式：" -ForegroundColor Yellow
    Write-Host "   1) 手工启动 Docker Desktop，等它就绪（托盘图标不再转圈）后重跑本脚本；" -ForegroundColor Yellow
    Write-Host "   2) 若 Docker Desktop 未安装，请先安装：https://www.docker.com/products/docker-desktop/" -ForegroundColor Yellow
    Write-Host "======================================================================" -ForegroundColor Red
    Write-Host ""
    Write-Host "按任意键退出本窗口..." -ForegroundColor Gray
    try { $null = $Host.UI.RawUI.ReadKey("NoEcho,IncludeKeyDown") } catch { Start-Sleep -Seconds 5 }
    exit 1
}

Set-Location $rootDir
docker compose up -d
if ($LASTEXITCODE -ne 0) {
    Write-Host ""
    Write-Host "======================================================================" -ForegroundColor Red
    Write-Host " [中止] docker compose up -d 执行失败，依赖容器未能就绪。" -ForegroundColor Red
    Write-Host "        请查看上方 docker 输出；常见原因是镜像拉取失败（网络）或端口被占用。" -ForegroundColor Yellow
    Write-Host "======================================================================" -ForegroundColor Red
    Write-Host ""
    Write-Host "按任意键退出本窗口..." -ForegroundColor Gray
    try { $null = $Host.UI.RawUI.ReadKey("NoEcho,IncludeKeyDown") } catch { Start-Sleep -Seconds 5 }
    exit 1
}

# 等待两个容器真正 healthy：容器"已启动"不等于"可接受连接"
Write-Host "      正在等待 PostgreSQL 与 Redis 健康就绪..." -ForegroundColor Gray
$containersReady = $false
for ($i = 1; $i -le 30; $i++) {
    $pg = docker inspect --format "{{.State.Health.Status}}" campus-library-postgres 2>$null
    $rd = docker inspect --format "{{.State.Health.Status}}" campus-library-redis 2>$null
    if ($pg -eq "healthy" -and $rd -eq "healthy") { $containersReady = $true; break }
    Start-Sleep -Seconds 3
    Write-Host -NoNewline "."
}
Write-Host ""

if ($containersReady) {
    Write-Host "[成功] 中间件容器已就绪 (PostgreSQL 17 端口 15437, Redis 8 端口 16381)" -ForegroundColor Green
} else {
    Write-Host ""
    Write-Host "======================================================================" -ForegroundColor Red
    Write-Host " [中止] 依赖容器在 90 秒内未进入 healthy 状态，后端启动也会失败。" -ForegroundColor Red
    Write-Host "        排查命令: docker compose ps ; docker logs campus-library-postgres" -ForegroundColor Yellow
    Write-Host "======================================================================" -ForegroundColor Red
    Write-Host ""
    Write-Host "按任意键退出本窗口..." -ForegroundColor Gray
    try { $null = $Host.UI.RawUI.ReadKey("NoEcho,IncludeKeyDown") } catch { Start-Sleep -Seconds 5 }
    exit 1
}
Write-Host ""

# ----------------------------------------------------------------------
# 2. 独立窗口启动 Spring Boot 后端
# ----------------------------------------------------------------------
Write-Host "[2/4] 正在新窗口拉起 Spring Boot 3 后端服务..." -ForegroundColor Green
$backendArgs = "-NoExit -ExecutionPolicy Bypass -File `"$scriptsDir\start-backend.ps1`""
Start-Process powershell.exe -WorkingDirectory "$rootDir\backend" -ArgumentList $backendArgs
Write-Host "[成功] 后端服务已在新终端窗口启动。" -ForegroundColor Green
Write-Host ""

# ----------------------------------------------------------------------
# 3. 轮询等待后端健康检查就绪 (最多 60 秒)
#
# 顺序很关键：必须先确认后端就绪，再拉起前端。
# 旧顺序是"先起前端、后等后端"，于是后端一旦没起来（例如端口被别的项目占用），
# 前端照样在跑、界面只显示"无法连接服务器"，而真正的失败原因留在另一个窗口里 ——
# 用户看到的就是"一键启动之后什么都没有"。
# ----------------------------------------------------------------------
$healthUrl = "http://localhost:$backendPort/actuator/health"

# 预算给到 180 秒，而不是 60 秒。
# 实测踩过：后端窗口执行的是 `mvn spring-boot:run`，它会**先编译再启动**，
# 首次运行或改动过代码后，编译本身就要几十秒；60 秒预算会让启动器在
# "后端马上就绪"的前一刻判定失败并中止，用户只看到"没有前端"。
$healthBudgetSeconds = 180
Write-Host "[3/4] 正在等待后端服务启动并完成健康就绪 (最多 $healthBudgetSeconds 秒，Maven 首次编译较慢)..." -ForegroundColor Cyan
$backendReady = $false
for ($i = 1; $i -le $healthBudgetSeconds; $i++) {
    Start-Sleep -Seconds 1
    try {
        $resp = Invoke-RestMethod -Uri $healthUrl -TimeoutSec 2 -ErrorAction SilentlyContinue
        if ($resp.status -eq "UP") {
            $backendReady = $true
            break
        }
    } catch {}
    # 每 5 秒打一个点，避免长时间静默让人以为脚本卡死
    if ($i % 5 -eq 0) { Write-Host -NoNewline "." }
}
Write-Host ""

if (-not $backendReady) {
    Write-Host "======================================================================" -ForegroundColor Red
    Write-Host " [失败] 后端在 $healthBudgetSeconds 秒内未就绪，已中止启动前端。" -ForegroundColor Red
    Write-Host "----------------------------------------------------------------------" -ForegroundColor Red
    Write-Host " 前端产物里的接口地址是编译期常量，后端不可用时它只会显示「无法连接服务器」，" -ForegroundColor Yellow
    Write-Host " 因此这里不再继续拉起前端，避免留下一个「看起来在跑、实际用不了」的界面。" -ForegroundColor Yellow
    Write-Host "" -ForegroundColor Yellow
    Write-Host " 请查看弹出的【Campus Library - 后端服务】窗口里的报错，常见原因：" -ForegroundColor Yellow
    Write-Host "   1) 端口 $backendPort 被其它进程占用 -> 关掉占用进程，或设 SERVER_PORT 换端口后重跑；" -ForegroundColor Yellow
    Write-Host "   2) 数据库/Redis 容器未起 -> docker compose up -d" -ForegroundColor Yellow
    Write-Host "   3) JDK 版本不符 -> 需 JDK 21（脚本会在后端窗口里提示）" -ForegroundColor Yellow
    Write-Host "   4) 只是编译较慢 -> 等后端窗口出现 Started Application 后，" -ForegroundColor Yellow
    Write-Host "      单独执行 scripts\start-frontend.bat 拉起前端即可（不必重跑整个启动器）" -ForegroundColor Yellow
    Write-Host "======================================================================" -ForegroundColor Red
    Write-Host ""
    Write-Host "按任意键退出本窗口..." -ForegroundColor Gray
    try { $null = $Host.UI.RawUI.ReadKey("NoEcho,IncludeKeyDown") } catch { Start-Sleep -Seconds 5 }
    exit 1
}

Write-Host "[成功] 后端服务已健康就绪 (端口 $backendPort, Status: UP)！" -ForegroundColor Green
Write-Host "正在自动为您打开 Swagger 在线接口文档..." -ForegroundColor Green
try { Start-Process "http://localhost:$backendPort/swagger-ui.html" } catch {}
Write-Host ""

# ----------------------------------------------------------------------
# 4. 独立窗口启动 Flutter 前端客户端（后端确认就绪之后）
# ----------------------------------------------------------------------
Write-Host "[4/4] 正在新窗口拉起 Flutter 前端客户端..." -ForegroundColor Green
$frontendArgs = "-NoExit -ExecutionPolicy Bypass -File `"$scriptsDir\start-frontend.ps1`""
Start-Process powershell.exe -WorkingDirectory "$rootDir\frontend" -ArgumentList $frontendArgs
Write-Host "[成功] 前端客户端已在新终端窗口启动（接口地址指向 http://localhost:$backendPort/api/v1）。" -ForegroundColor Green
Write-Host ""

Write-Host ""
Write-Host "======================================================================" -ForegroundColor Cyan
Write-Host " 服务访问入口:" -ForegroundColor Yellow
Write-Host " - 后端服务:         http://localhost:$backendPort" -ForegroundColor White
Write-Host " - Swagger 在线文档:   http://localhost:$backendPort/swagger-ui.html" -ForegroundColor White
Write-Host " - OpenAPI JSON 规范:  http://localhost:$backendPort/v3/api-docs" -ForegroundColor White
Write-Host " - 健康检查端点:       $healthUrl" -ForegroundColor White
Write-Host " - PostgreSQL 17:    localhost:15437 (库 library_system / 用户 library)" -ForegroundColor White
Write-Host " - Redis 8 缓存:     localhost:16381" -ForegroundColor White
Write-Host " 凭据说明: 数据库与缓存口令统一存放在项目根目录 .env（已加入 .gitignore）" -ForegroundColor Gray
Write-Host "======================================================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "后端与前端已在独立窗口持续运行。如需全部停止，请执行 stop-all.bat。" -ForegroundColor Green
Write-Host "按任意键退出本窗口..." -ForegroundColor Gray
try {
    $null = $Host.UI.RawUI.ReadKey("NoEcho,IncludeKeyDown")
} catch {
    Start-Sleep -Seconds 3
}
