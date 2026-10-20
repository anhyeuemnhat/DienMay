@echo off
cd /d "%~dp0.."
java -cp "dist\DienMay.jar;lib\*" GUI.App
