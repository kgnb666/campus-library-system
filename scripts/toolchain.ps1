# ======================================================================
# 工具链探测助手 - Campus Library Borrowing System
#
# 供 scripts/ 下的启动与测试脚本 dot-source 复用:
#     . "$PSScriptRoot\toolchain.ps1"
#
# 解析优先级（逐层回退，第一层命中即返回）:
#   1. 显式环境变量      JAVA_HOME / MAVEN_HOME|M2_HOME / FLUTTER_ROOT
#   2. 系统 PATH 探测    Get-Command
#   3. 本机历史绝对路径  仅作最后兜底，不再是唯一来源
#
# 全部失败返回 $null，由调用方给出可读的中文提示。
# 原始实现把三套本机绝对路径写死，换一台机器（含答辩现场备用机）必然启动失败。
# ======================================================================

$script:FallbackJavaHome   = "D:\yp3\.tools\jdk21"
$script:FallbackMavenBin   = "D:\yp3\.tools\maven\bin"
$script:FallbackFlutterBin = "D:\flutter_sdk\flutter\bin"

<#
.SYNOPSIS  解析 JDK 安装目录（返回 JAVA_HOME 路径）
#>
function Resolve-JavaHome {
    # 1) 环境变量
    if ($env:JAVA_HOME -and (Test-Path (Join-Path $env:JAVA_HOME "bin\java.exe"))) {
        return $env:JAVA_HOME
    }

    # 2) PATH 中的 java（java.exe 位于 <JAVA_HOME>\bin\java.exe）
    $javaCmd = Get-Command java.exe -ErrorAction SilentlyContinue
    if ($javaCmd -and $javaCmd.Source) {
        $home = Split-Path (Split-Path $javaCmd.Source -Parent) -Parent
        if ($home -and (Test-Path (Join-Path $home "bin\java.exe"))) {
            return $home
        }
    }

    # 3) 本机兜底
    if (Test-Path (Join-Path $script:FallbackJavaHome "bin\java.exe")) {
        return $script:FallbackJavaHome
    }

    return $null
}

<#
.SYNOPSIS  解析 Maven 可执行文件路径（返回 mvn.cmd / mvn 的完整路径）
#>
function Resolve-MavenCmd {
    foreach ($candidate in @($env:MAVEN_HOME, $env:M2_HOME)) {
        if ($candidate) {
            $cmd = Join-Path $candidate "bin\mvn.cmd"
            if (Test-Path $cmd) { return $cmd }
        }
    }

    foreach ($name in @("mvn.cmd", "mvn")) {
        $found = Get-Command $name -ErrorAction SilentlyContinue
        if ($found -and $found.Source) { return $found.Source }
    }

    $fallback = Join-Path $script:FallbackMavenBin "mvn.cmd"
    if (Test-Path $fallback) { return $fallback }

    return $null
}

<#
.SYNOPSIS  解析 Flutter 可执行文件路径
#>
function Resolve-FlutterCmd {
    if ($env:FLUTTER_ROOT) {
        $cmd = Join-Path $env:FLUTTER_ROOT "bin\flutter.bat"
        if (Test-Path $cmd) { return $cmd }
    }

    foreach ($name in @("flutter.bat", "flutter")) {
        $found = Get-Command $name -ErrorAction SilentlyContinue
        if ($found -and $found.Source) { return $found.Source }
    }

    $fallback = Join-Path $script:FallbackFlutterBin "flutter.bat"
    if (Test-Path $fallback) { return $fallback }

    return $null
}
