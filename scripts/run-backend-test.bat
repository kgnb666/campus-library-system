@echo off
echo ======================================================================
echo Running Backend Tests (Spring Boot Test + Actuator + Flyway)...
echo ======================================================================
set JAVA_HOME=D:\jdk17\jdk-17.0.2
set PATH=D:\jdk17\jdk-17.0.2\bin;D:\yp3\.tools\maven\bin;%PATH%
cd /d "%~dp0\..\backend"
call mvn test
pause
