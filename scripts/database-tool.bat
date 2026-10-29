@echo off
setlocal
cd /d "%~dp0.."
if not exist "dist\backend-server.jar" (echo Build first: ant verify & exit /b 1)
java -Dfile.encoding=UTF-8 -cp "dist\common-dto.jar;dist\backend-server.jar;backend-server\lib\*" vn.dienmay.server.DatabaseTool %*
exit /b %errorlevel%
