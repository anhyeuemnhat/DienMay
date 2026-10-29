#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
[ -f dist/backend-server.jar ] || { echo 'Build first: ant verify' >&2; exit 1; }
exec java -Dfile.encoding=UTF-8 -cp 'dist/common-dto.jar:dist/backend-server.jar:backend-server/lib/*' vn.dienmay.server.ServerMain "$@"
