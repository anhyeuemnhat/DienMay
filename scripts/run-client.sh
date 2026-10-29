#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
[ -f dist/client-swing.jar ] || { echo 'Build first: ant verify' >&2; exit 1; }
exec java -Dfile.encoding=UTF-8 -jar dist/client-swing.jar "$@"
