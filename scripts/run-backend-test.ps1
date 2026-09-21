$ErrorActionPreference = "Continue"
try { $host.UI.RawUI.WindowTitle = "Campus Library - 后端测试" } catch {}

# 统一工具链探测：原实现把 JAVA_HOME 写死为 D:\jdk17\jdk-17.0.2，
# 与 README / Dockerfile 宣称的 JDK 21 自相矛盾，此处统一为 JDK 21。
. "$PSScriptRoot\toolchain.ps1"

if (Test-Path "$PSScriptRoot\backend") {
    $rootDir = $PSScriptRoot
} else {
    $rootDir = (Resolve-Path "$PSScriptRoot\..").Path
}

Write-Host "======================================================================" -ForegroundColor Cyan
Write-Host "   运行后端测试套件 (Spring Boot Test + Flyway + 并发/权限测试)" -ForegroundColor Yellow
Write-Host "======================================================================" -ForegroundColor Cyan

$javaHome = Resolve-JavaHome
if (-not $javaHome) {
    Write-Host "[错误] 未检测到可用的 JDK，请安装 JDK 21 并设置 JAVA_HOME。" -ForegroundColor Red
    Write-Host "按任意键关闭此窗口..." -ForegroundColor Yellow
    try { $null = $Host.UI.RawUI.ReadKey("NoEcho,IncludeKeyDown") } catch { Start-Sleep -Seconds 5 }
    exit 1
}

$mavenCmd = Resolve-MavenCmd
if (-not $mavenCmd) {
    $wrapper = Join-Path $rootDir "backend\mvnw.cmd"
    if (Test-Path $wrapper) {
        $mavenCmd = $wrapper
        Write-Host "[提示] 未检测到本机 Maven，改用仓库内 Maven Wrapper。" -ForegroundColor Yellow
    } else {
        Write-Host "[错误] 未检测到 Maven，请设置 MAVEN_HOME 或确认 backend\mvnw.cmd 存在。" -ForegroundColor Red
        Write-Host "按任意键关闭此窗口..." -ForegroundColor Yellow
        try { $null = $Host.UI.RawUI.ReadKey("NoEcho,IncludeKeyDown") } catch { Start-Sleep -Seconds 5 }
        exit 1
    }
}

$env:JAVA_HOME = $javaHome
$env:PATH = (Join-Path $javaHome "bin") + ";" + (Split-Path $mavenCmd -Parent) + ";" + $env:PATH

Write-Host "JDK:   $javaHome" -ForegroundColor Gray
Write-Host "Maven: $mavenCmd" -ForegroundColor Gray

# ------------------------------------------------------------------ 独立测试库
# 测试夹具会真实写入数据（集成测试建书、建用户、造借阅流水）。若与演示库共用
# 同一个库，演示数据会被逐步污染：历史上出现过 1937 条夹具书目混进书库、
# 库存计数漂移导致 books_check 约束报错。因此 application-test.yml 默认指向
# 独立的 library_system_test 库。
# PostgreSQL 的 JDBC 驱动无法自动建库（连不上不存在的库），这里在容器可用时补建。
$testDbName = if ($env:TEST_DB_NAME) { $env:TEST_DB_NAME } else { "library_system_test" }
$dbUser = if ($env:POSTGRES_USER) { $env:POSTGRES_USER }
          elseif ($env:DB_USER) { $env:DB_USER }
          else { "library" }
$pgContainer = if ($env:PG_CONTAINER) { $env:PG_CONTAINER } else { "campus-library-postgres" }

# 建库 SQL 与"给人照抄的命令行"。
# 命令行外层用单引号、SQL 内部的库名/角色名用双引号：直接写成
#   docker exec X psql ... -c "CREATE DATABASE "lib" OWNER "library""
# 嵌套双引号在 shell 里的归属是歧义的，照抄必错，所以这里拼成单引号形式
# （PowerShell 与 bash/zsh 都按此语义解析；cmd.exe 下需把内层双引号写成 \"）。
$createSql = 'CREATE DATABASE "' + $testDbName + '" OWNER "' + $dbUser + '"'
$manualCreateCmd = "docker exec $pgContainer psql -U $dbUser -d postgres -c '" + $createSql + "'"

$containerRunning = $false
if (Get-Command docker -ErrorAction SilentlyContinue) {
    try {
        $state = (& docker inspect --format "{{.State.Running}}" $pgContainer 2>&1 | Out-String).Trim()
        $containerRunning = ($state -eq "true")
    } catch {
        $containerRunning = $false
    }
}

if ($containerRunning) {
    $existsSql = "SELECT 1 FROM pg_database WHERE datname = '$testDbName'"
    $exists = (& docker exec $pgContainer psql -U $dbUser -d postgres -tAc $existsSql 2>&1 | Out-String).Trim()
    if ($exists -eq "1") {
        Write-Host "测试库: $testDbName (已存在，Flyway 会自动补齐到最新版本)" -ForegroundColor Gray
    } else {
        Write-Host "测试库: $testDbName (不存在，正在创建)" -ForegroundColor Gray
        $createOut = (& docker exec $pgContainer psql -U $dbUser -d postgres -c $createSql 2>&1 | Out-String).Trim()
        if ($LASTEXITCODE -ne 0) {
            Write-Host "[错误] 创建测试库失败: $createOut" -ForegroundColor Red
            Write-Host "请手动执行: $manualCreateCmd" -ForegroundColor Yellow
            Write-Host "按任意键关闭此窗口..." -ForegroundColor Yellow
            try { $null = $Host.UI.RawUI.ReadKey("NoEcho,IncludeKeyDown") } catch { Start-Sleep -Seconds 5 }
            exit 1
        }
        Write-Host "[信息] 测试库 $testDbName 创建完成。" -ForegroundColor Green
    }
} else {
    Write-Host "[警告] 未检测到运行中的容器 $pgContainer，跳过测试库自动创建。" -ForegroundColor Yellow
    Write-Host "        若测试因连不上 $testDbName 而失败，请先启动数据库：" -ForegroundColor Yellow
    Write-Host "            docker compose up -d postgres" -ForegroundColor Yellow
    Write-Host "        再手动建库：" -ForegroundColor Yellow
    Write-Host "            $manualCreateCmd" -ForegroundColor Yellow
}
Write-Host ""

Set-Location "$rootDir\backend"
& $mavenCmd test
$testExitCode = $LASTEXITCODE

Write-Host ""
if ($testExitCode -eq 0) {
    Write-Host "[成功] 后端测试全部通过。" -ForegroundColor Green
} else {
    Write-Host "[失败] 后端测试未通过，退出码: $testExitCode" -ForegroundColor Red
}
Write-Host "按任意键关闭此窗口..." -ForegroundColor Yellow
try { $null = $Host.UI.RawUI.ReadKey("NoEcho,IncludeKeyDown") } catch { Start-Sleep -Seconds 5 }
exit $testExitCode
