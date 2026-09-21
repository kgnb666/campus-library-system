$ErrorActionPreference = "Continue"
try {
    $host.UI.RawUI.WindowTitle = "Campus Library - 后端服务 (Spring Boot 3)"
} catch {}

# 统一工具链探测（环境变量 → PATH → 本机兜底），避免把绝对路径写死导致换机即失败
. "$PSScriptRoot\toolchain.ps1"

Write-Host "======================================================================" -ForegroundColor Cyan
Write-Host "      正在启动 Campus Library 后端服务 (Spring Boot 3 + JDK 21)..." -ForegroundColor Yellow
Write-Host "======================================================================" -ForegroundColor Cyan

if (Test-Path "$PSScriptRoot\backend") {
    $rootDir = $PSScriptRoot
} else {
    $rootDir = (Resolve-Path "$PSScriptRoot\..").Path
}

$javaHome = Resolve-JavaHome
if (-not $javaHome) {
    Write-Host "[错误] 未检测到可用的 JDK。" -ForegroundColor Red
    Write-Host "       请安装 JDK 21 并设置 JAVA_HOME 环境变量后重试，例如:" -ForegroundColor Yellow
    Write-Host '       setx JAVA_HOME "C:\Program Files\Eclipse Adoptium\jdk-21"' -ForegroundColor Gray
    Write-Host "       按任意键关闭此窗口..." -ForegroundColor Yellow
    try { $null = $Host.UI.RawUI.ReadKey("NoEcho,IncludeKeyDown") } catch { Start-Sleep -Seconds 5 }
    exit 1
}

$mavenCmd = Resolve-MavenCmd
if (-not $mavenCmd) {
    # 本机没有 Maven 时回退到仓库内的 Maven Wrapper（已随仓库提供，无需额外安装）
    $wrapper = Join-Path $rootDir "backend\mvnw.cmd"
    if (Test-Path $wrapper) {
        $mavenCmd = $wrapper
        Write-Host "[提示] 未检测到本机 Maven，改用仓库内 Maven Wrapper: backend\mvnw.cmd" -ForegroundColor Yellow
    } else {
        Write-Host "[错误] 未检测到 Maven。" -ForegroundColor Red
        Write-Host "       请设置 MAVEN_HOME 环境变量，或确认仓库内存在 backend\mvnw.cmd。" -ForegroundColor Yellow
        Write-Host "       按任意键关闭此窗口..." -ForegroundColor Yellow
        try { $null = $Host.UI.RawUI.ReadKey("NoEcho,IncludeKeyDown") } catch { Start-Sleep -Seconds 5 }
        exit 1
    }
}

$env:JAVA_HOME = $javaHome
$env:PATH = (Join-Path $javaHome "bin") + ";" + (Split-Path $mavenCmd -Parent) + ";" + $env:PATH

Set-Location "$rootDir\backend"

# ----------------------------------------------------------------------
# 端口预检：直接给出可读结论，而不是让 Spring 抛一堆栈后再退出
#
# 实际踩过的坑：8080 被另一个项目占着时，后端窗口只留下 Spring 的
# "Port 8080 was already in use" 栈，用户很难意识到"后端其实没起来"。
# ----------------------------------------------------------------------
$backendPort = if ($env:SERVER_PORT) { [int]$env:SERVER_PORT } else { 8080 }
$occupiers = @(Get-NetTCPConnection -LocalPort $backendPort -State Listen -ErrorAction SilentlyContinue)
if ($occupiers.Count -gt 0) {
    $ownerPid = $occupiers[0].OwningProcess
    $proc = Get-CimInstance Win32_Process -Filter "ProcessId=$ownerPid" -ErrorAction SilentlyContinue
    $procName = if ($proc) { $proc.Name } else { "未知进程" }
    Write-Host "======================================================================" -ForegroundColor Red
    Write-Host " [错误] 端口 $backendPort 已被占用，后端无法启动。" -ForegroundColor Red
    Write-Host "        占用进程: PID=$ownerPid ($procName)" -ForegroundColor Yellow
    Write-Host "" -ForegroundColor Yellow
    Write-Host " 处理方式（二选一）：" -ForegroundColor Yellow
    Write-Host "   1) 先结束占用进程，再重跑本脚本；" -ForegroundColor Yellow
    Write-Host "   2) 换端口启动：`$env:SERVER_PORT=28080; .\scripts\start-all.ps1" -ForegroundColor Yellow
    Write-Host "      （一键启动器会自动把该端口传给前端，无需手工改前端配置）" -ForegroundColor Gray
    Write-Host "======================================================================" -ForegroundColor Red
    Write-Host ""
    Write-Host "按任意键关闭此窗口..." -ForegroundColor Gray
    try { $null = $Host.UI.RawUI.ReadKey("NoEcho,IncludeKeyDown") } catch { Start-Sleep -Seconds 5 }
    exit 1
}

Write-Host "工作目录: $(Get-Location)" -ForegroundColor Gray
Write-Host "JDK:      $javaHome" -ForegroundColor Gray
Write-Host "Maven:    $mavenCmd" -ForegroundColor Gray
Write-Host "端口:     $backendPort" -ForegroundColor Gray
Write-Host ""

try {
    & $mavenCmd spring-boot:run
} catch {
    Write-Host "后端运行异常: $_" -ForegroundColor Red
}

Write-Host ""
Write-Host "后端服务已停止。按任意键关闭此窗口..." -ForegroundColor Yellow
try {
    $null = $Host.UI.RawUI.ReadKey("NoEcho,IncludeKeyDown")
} catch {
    Start-Sleep -Seconds 5
}
