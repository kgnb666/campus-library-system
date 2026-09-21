$ErrorActionPreference = "Continue"
try {
    $host.UI.RawUI.WindowTitle = "Campus Library - 前端客户端 (Flutter)"
} catch {}

# 统一工具链探测（环境变量 → PATH → 本机兜底）
. "$PSScriptRoot\toolchain.ps1"

Write-Host "======================================================================" -ForegroundColor Cyan
Write-Host "        正在启动 Campus Library 前端客户端 (Flutter Web/Chrome)..." -ForegroundColor Yellow
Write-Host "======================================================================" -ForegroundColor Cyan

if (Test-Path "$PSScriptRoot\frontend") {
    $rootDir = $PSScriptRoot
} else {
    $rootDir = (Resolve-Path "$PSScriptRoot\..").Path
}

$flutterCmd = Resolve-FlutterCmd
if (-not $flutterCmd) {
    Write-Host "[错误] 未检测到 Flutter SDK。" -ForegroundColor Red
    Write-Host "       请安装 Flutter 并设置 FLUTTER_ROOT 环境变量后重试，例如:" -ForegroundColor Yellow
    Write-Host '       setx FLUTTER_ROOT "C:\src\flutter"' -ForegroundColor Gray
    Write-Host "       按任意键关闭此窗口..." -ForegroundColor Yellow
    try { $null = $Host.UI.RawUI.ReadKey("NoEcho,IncludeKeyDown") } catch { Start-Sleep -Seconds 5 }
    exit 1
}

# Flutter 工具链自身需要 JDK（Web 构建路径下亦会调用）；探测到即注入，未探测到不阻断
$javaHome = Resolve-JavaHome
if ($javaHome) {
    $env:JAVA_HOME = $javaHome
    $env:PATH = (Join-Path $javaHome "bin") + ";" + $env:PATH
}

$env:PATH = (Split-Path $flutterCmd -Parent) + ";" + $env:PATH

Set-Location "$rootDir\frontend"
Write-Host "工作目录: $(Get-Location)" -ForegroundColor Gray
Write-Host "Flutter:  $flutterCmd" -ForegroundColor Gray
if ($javaHome) { Write-Host "JDK:      $javaHome" -ForegroundColor Gray }

# ----------------------------------------------------------------------
# 把后端端口显式传给前端（必须与 start-backend.ps1 的取值一致）
#
# 前端是 Flutter Web，接口地址是**编译期常量**：不传 --dart-define 的话，
# 产物里用的是 EnvConfig 的 dev 默认值 http://localhost:8080/api/v1。
# 一旦后端因端口冲突改用 SERVER_PORT=28080，前端仍会固执地指向 8080，
# 表现为界面「无法连接服务器」而后端日志一切正常 —— 排查成本极高。
# ----------------------------------------------------------------------
$backendPort = if ($env:SERVER_PORT) { [int]$env:SERVER_PORT } else { 8080 }
$apiBaseUrl = "http://localhost:$backendPort/api/v1"
Write-Host "后端接口: $apiBaseUrl  (如需更改请用 SERVER_PORT 环境变量)" -ForegroundColor Gray
Write-Host ""

Write-Host "正在拉取 Flutter 依赖包..." -ForegroundColor Green
& $flutterCmd pub get

Write-Host ""
Write-Host "正在以 Chrome 浏览器启动 Flutter 读者端应用..." -ForegroundColor Green
& $flutterCmd run -d chrome --dart-define=API_BASE_URL=$apiBaseUrl

Write-Host ""
Write-Host "前端已退出。按任意键关闭此窗口..." -ForegroundColor Yellow
try {
    $null = $Host.UI.RawUI.ReadKey("NoEcho,IncludeKeyDown")
} catch {
    Start-Sleep -Seconds 5
}
