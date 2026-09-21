@echo off
rem 薄封装：实际逻辑与工具链探测统一维护在 run-backend-test.ps1
setlocal
cd /d "%~dp0"
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0run-backend-test.ps1"
endlocal
