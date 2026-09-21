@echo off
rem 薄封装：实际逻辑与工具链探测统一维护在 run-frontend-test.ps1
setlocal
cd /d "%~dp0"
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0run-frontend-test.ps1"
endlocal
