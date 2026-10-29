@echo off
setlocal
cd /d "%~dp0.."
call mvn verify
if errorlevel 1 exit /b %errorlevel%
if not exist dist mkdir dist
for %%M in (common-dto backend-server client-swing) do copy /y "%%M\target\%%M.jar" "dist\%%M.jar" >nul
exit /b 0
