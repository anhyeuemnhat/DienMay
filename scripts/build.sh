#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
mkdir -p build/classes build/test-classes dist/lib
find Code GUI ImplementationDB Service -name '*.java' -print > build/sources.txt
javac --release 17 -encoding UTF-8 -d build/classes @build/sources.txt
cp -R Images build/classes/
find tests -name '*.java' -print > build/tests.txt
javac --release 17 -encoding UTF-8 -cp build/classes -d build/test-classes @build/tests.txt
java -ea -Djava.awt.headless=true -cp 'build/classes:build/test-classes:lib/*' tests.UnitTests
printf 'Manifest-Version: 1.0\nMain-Class: GUI.App\nClass-Path: lib/mssql-jdbc-13.4.0.jre11.jar\n\n' > build/MANIFEST.MF
jar cfm dist/DienMay.jar build/MANIFEST.MF -C build/classes .
for file in lib/*.jar; do [ ! -f "$file" ] || cp "$file" dist/lib/; done
