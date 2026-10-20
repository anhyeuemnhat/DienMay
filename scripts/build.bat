@echo off
setlocal
cd /d "%~dp0.."
if not exist build\classes mkdir build\classes
if not exist build\test-classes mkdir build\test-classes
if not exist dist\lib mkdir dist\lib
(for %%f in (Code\*.java) do @echo Code/%%~nxf
for %%f in (GUI\*.java) do @echo GUI/%%~nxf
for %%f in (ImplementationDB\*.java) do @echo ImplementationDB/%%~nxf
for %%f in (Service\*.java) do @echo Service/%%~nxf) > build\sources.txt
javac --release 17 -encoding UTF-8 -d build\classes @build\sources.txt
if errorlevel 1 exit /b 1
xcopy /e /i /y Images build\classes\Images >nul
(for %%f in (tests\*.java) do @echo tests/%%~nxf) > build\tests.txt
javac --release 17 -encoding UTF-8 -cp build\classes -d build\test-classes @build\tests.txt
if errorlevel 1 exit /b 1
java -ea -Djava.awt.headless=true -cp "build\classes;build\test-classes;lib\*" tests.UnitTests
if errorlevel 1 exit /b 1
(echo Manifest-Version: 1.0
 echo Main-Class: GUI.App
 echo Class-Path: lib/mssql-jdbc-13.4.0.jre11.jar
 echo.) > build\MANIFEST.MF
jar cfm dist\DienMay.jar build\MANIFEST.MF -C build\classes .
if errorlevel 1 exit /b 1
if exist lib\*.jar copy /y lib\*.jar dist\lib\ >nul
