@echo off
echo ======================================================================
echo Starting Campus Library Infrastructure (PostgreSQL 17 + Redis 8)...
echo ======================================================================
cd /d "%~dp0\.."
docker compose up -d
docker compose ps
pause
