#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
exec java -cp 'dist/DienMay.jar:lib/*' GUI.App
