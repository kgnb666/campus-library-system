<#
.SYNOPSIS
    Campus Library Borrowing System - 一键停止与资源清理

.DESCRIPTION
    修复要点（原实现的两个缺陷）:
      1. 原实现用 `Get-Process -Name java,dart | Where-Object MainWindowTitle -like "*Campus Library*"`
         匹配进程。但窗口标题设置在 PowerShell 宿主进程上（见 start-backend.ps1），
         java/dart 子进程并没有该标题 —— 结果一个进程都杀不掉，却始终打印"[成功] 所有服务已安全退出"。
      2. 现改为按【监听端口】定位进程，并【校验进程归属】后才停止。
         归属校验是必要的安全阀: 本机可能同时运行其它占用同一端口的项目
         （实测本机 8080 曾被另一个不相关项目占用），仅凭端口号杀进程会误伤无关应用。

.PARAMETER BackendPort
    后端监听端口，默认 8080。可用其它端口号验证脚本行为。
.PARAMETER KeepDocker
    保留中间件容器（PostgreSQL/Redis）不停止。
#>
param(
    [int]$BackendPort = 8080,
    [switch]$KeepDocker
)

$ErrorActionPreference = "Continue"

if (Test-Path "$PSScriptRoot\backend") {
    $rootDir = $PSScriptRoot
} else {
    $rootDir = (Resolve-Path "$PSScriptRoot\..").Path
}

# 本项目进程的识别特征: Spring Boot 主类 / 项目根路径 / 项目目录名
$projectPatterns = @(
    'com\.library\.Application',
    [regex]::Escape($rootDir),
    'campus-library'
)

# 前端进程的识别特征: 必须能定位到本项目，避免误杀其它项目的 Flutter 构建
$frontendPatterns = @(
    [regex]::Escape($rootDir),
    'campus_library_frontend'
)

function Test-MatchesAny {
    param([string]$Text, [string[]]$Patterns)
    if (-not $Text) { return $false }
    foreach ($p in $Patterns) {
        if ($Text -match $p) { return $true }
    }
    return $false
}

function Get-ShortCommandLine {
    param([string]$CommandLine)
    if (-not $CommandLine) { return "(无法读取命令行)" }
    $oneLine = $CommandLine -replace "\s+", " "
    if ($oneLine.Length -gt 100) { return $oneLine.Substring(0, 100) + "..." }
    return $oneLine
}

Write-Host "======================================================================" -ForegroundColor Cyan
Write-Host "           📚 校园图书借阅系统 - 一键停止与资源清理" -ForegroundColor Yellow
Write-Host "======================================================================" -ForegroundColor Cyan
Write-Host "项目根目录: $rootDir" -ForegroundColor Gray
Write-Host "后端端口:   $BackendPort" -ForegroundColor Gray
Write-Host ""

$stoppedSomething = $false

# ----------------------------------------------------------------------
# 1. 按端口停止后端（含归属校验）
# ----------------------------------------------------------------------
Write-Host "[1/3] 正在按端口 $BackendPort 停止后端进程..." -ForegroundColor Green
$listeners = @(Get-NetTCPConnection -LocalPort $BackendPort -State Listen -ErrorAction SilentlyContinue)
if ($listeners.Count -eq 0) {
    Write-Host "      [警告] 端口 $BackendPort 上没有任何监听进程，后端可能未在运行。" -ForegroundColor Yellow
} else {
    foreach ($conn in $listeners) {
        $ownerPid = $conn.OwningProcess
        $proc = Get-CimInstance Win32_Process -Filter "ProcessId=$ownerPid" -ErrorAction SilentlyContinue
        if (-not $proc) { continue }

        if (Test-MatchesAny $proc.CommandLine $projectPatterns) {
            Stop-Process -Id $ownerPid -Force -ErrorAction SilentlyContinue
            Write-Host "      [成功] 已停止本项目后端: PID=$ownerPid $($proc.Name)" -ForegroundColor Green
            $stoppedSomething = $true
        } else {
            Write-Host "      [警告] 端口 $BackendPort 被其它进程占用，且不属于本项目，已跳过以免误杀:" -ForegroundColor Yellow
            Write-Host "             PID=$ownerPid $($proc.Name)" -ForegroundColor Yellow
            Write-Host "             $(Get-ShortCommandLine $proc.CommandLine)" -ForegroundColor DarkGray
            Write-Host "             确认需要停止时请手工执行: Stop-Process -Id $ownerPid -Force" -ForegroundColor Gray
        }
    }
}

# ----------------------------------------------------------------------
# 2. 清理本项目的前端与残留进程
# ----------------------------------------------------------------------
Write-Host "[2/3] 正在清理本项目的前端与残留进程..." -ForegroundColor Green
$candidates = @(Get-CimInstance Win32_Process -ErrorAction SilentlyContinue | Where-Object {
    $_.Name -in @("dart.exe", "flutter_tester.exe", "flutter.bat", "java.exe")
})

$matched = @()
foreach ($proc in $candidates) {
    $isJava = ($proc.Name -eq "java.exe")
    $patterns = if ($isJava) { $projectPatterns } else { $frontendPatterns }
    if (Test-MatchesAny $proc.CommandLine $patterns) {
        $matched += $proc
    }
}

if ($matched.Count -eq 0) {
    Write-Host "      [警告] 未发现属于本项目的前端/残留进程。" -ForegroundColor Yellow
    Write-Host "             若前端窗口仍在运行，请在该终端窗口按 Ctrl+C，或用任务管理器结束 dart.exe。" -ForegroundColor Gray
} else {
    foreach ($proc in $matched) {
        Stop-Process -Id $proc.ProcessId -Force -ErrorAction SilentlyContinue
        Write-Host "      [成功] 已停止: PID=$($proc.ProcessId) $($proc.Name)" -ForegroundColor Green
        $stoppedSomething = $true
    }
}

# ----------------------------------------------------------------------
# 3. 关闭中间件容器
# ----------------------------------------------------------------------
if ($KeepDocker) {
    Write-Host "[3/3] 已按 -KeepDocker 要求保留中间件容器。" -ForegroundColor Gray
} else {
    Write-Host "[3/3] 正在关闭 Docker 中间件容器..." -ForegroundColor Green
    if (Get-Command docker -ErrorAction SilentlyContinue) {
        Set-Location $rootDir
        docker compose down
    } else {
        Write-Host "      [警告] 未检测到 docker 命令，跳过容器清理。" -ForegroundColor Yellow
    }
}

Write-Host ""
if ($stoppedSomething) {
    Write-Host "[成功] 已停止属于本项目的进程。" -ForegroundColor Green
} else {
    Write-Host "[警告] 未找到任何属于本项目的运行进程，未做任何停止操作。" -ForegroundColor Yellow
    Write-Host "       （注意: 端口占用者可能属于其它项目，脚本已刻意跳过以免误伤）" -ForegroundColor Gray
}
Write-Host ""
