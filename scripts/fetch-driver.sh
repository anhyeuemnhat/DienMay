#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
mkdir -p lib
url='https://repo.maven.apache.org/maven2/com/microsoft/sqlserver/mssql-jdbc/13.4.0.jre11/mssql-jdbc-13.4.0.jre11.jar'
curl --fail --location --proto '=https' --tlsv1.2 "$url" -o lib/mssql-jdbc-13.4.0.jre11.jar.tmp
jar tf lib/mssql-jdbc-13.4.0.jre11.jar.tmp >/dev/null
mv lib/mssql-jdbc-13.4.0.jre11.jar.tmp lib/mssql-jdbc-13.4.0.jre11.jar
