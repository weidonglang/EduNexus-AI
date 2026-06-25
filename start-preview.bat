@echo off
cd /d "%~dp0"
if "%PREVIEW_PORT%"=="" set "PREVIEW_PORT=8091"
npx http-server . -p %PREVIEW_PORT%
