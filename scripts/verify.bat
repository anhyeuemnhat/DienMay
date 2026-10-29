@echo off
setlocal
cd /d "%~dp0.."
call ant clean verify %*
exit /b %errorlevel%
