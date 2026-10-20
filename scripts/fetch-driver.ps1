$ErrorActionPreference = 'Stop'
Set-Location (Join-Path $PSScriptRoot '..')
New-Item -ItemType Directory -Force -Path lib | Out-Null
$url = 'https://repo.maven.apache.org/maven2/com/microsoft/sqlserver/mssql-jdbc/13.4.0.jre11/mssql-jdbc-13.4.0.jre11.jar'
$temp = 'lib/mssql-jdbc-13.4.0.jre11.jar.tmp'
Invoke-WebRequest -UseBasicParsing -Uri $url -OutFile $temp
& jar tf $temp | Out-Null
if ($LASTEXITCODE -ne 0) { throw 'Downloaded file is not a valid JAR.' }
Move-Item -Force $temp 'lib/mssql-jdbc-13.4.0.jre11.jar'
