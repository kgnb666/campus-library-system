$ErrorActionPreference = "Continue"
try { $host.UI.RawUI.WindowTitle = "Campus Library - 前端测试" } catch {}

# 统一工具链探测（环境变量 → PATH → 本机兜底）
. "$PSScriptRoot\toolchain.ps1"

if (Test-Path "$PSScriptRoot\frontend") {
    $rootDir = $PSScriptRoot
} else {
    $rootDir = (Resolve-Path "$PSScriptRoot\..").Path
}

Write-Host "======================================================================" -ForegroundColor Cyan
Write-Host "   运行前端测试套件 (Flutter Widget / Riverpod 状态流转测试)" -ForegroundColor Yellow
Write-Host "======================================================================" -ForegroundColor Cyan

$flutterCmd = Resolve-FlutterCmd
if (-not $flutterCmd) {
    Write-Host "[错误] 未检测到 Flutter SDK，请安装并设置 FLUTTER_ROOT 环境变量。" -ForegroundColor Red
    Write-Host "按任意键关闭此窗口..." -ForegroundColor Yellow
    try { $null = $Host.UI.RawUI.ReadKey("NoEcho,IncludeKeyDown") } catch { Start-Sleep -Seconds 5 }
    exit 1
}

$javaHome = Resolve-JavaHome
if ($javaHome) {
    $env:JAVA_HOME = $javaHome
    $env:PATH = (Join-Path $javaHome "bin") + ";" + $env:PATH
}
$env:PATH = (Split-Path $flutterCmd -Parent) + ";" + $env:PATH

Write-Host "Flutter: $flutterCmd" -ForegroundColor Gray
Write-Host ""

Set-Location "$rootDir\frontend"
& $flutterCmd test
$testExitCode = $LASTEXITCODE

Write-Host ""
if ($testExitCode -eq 0) {
    Write-Host "[成功] 前端测试全部通过。" -ForegroundColor Green
} else {
    Write-Host "[失败] 前端测试未通过，退出码: $testExitCode" -ForegroundColor Red
}
Write-Host "按任意键关闭此窗口..." -ForegroundColor Yellow
try { $null = $Host.UI.RawUI.ReadKey("NoEcho,IncludeKeyDown") } catch { Start-Sleep -Seconds 5 }
exit $testExitCode
