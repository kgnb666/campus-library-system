@echo off
echo ======================================================================
echo Running Frontend Widget Tests (Flutter + Riverpod)...
echo ======================================================================
set PATH=D:\flutter_sdk\flutter\bin;%PATH%
cd /d "%~dp0\..\frontend"
call flutter test
pause
