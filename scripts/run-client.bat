@echo off
setlocal
cd /d "%~dp0.."
if not exist "dist\client-swing.jar" (echo Build first: ant verify & exit /b 1)
java -Dfile.encoding=UTF-8 -jar dist\client-swing.jar %*
exit /b %errorlevel%
