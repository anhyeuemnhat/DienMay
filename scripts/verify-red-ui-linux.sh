#!/bin/sh
# Compatibility entrypoint; current evidence is never mixed with historical red-ui reports.
set -eu
cd "$(dirname "$0")/.."
mkdir -p docs/evidence
ant clean verify > docs/evidence/ant-clean-verify.log 2>&1
sh scripts/ui-smoke-linux.sh
sh scripts/ui-modal-linux.sh
for size in 1366x768 1920x1080; do
  timeout 45s xvfb-run -a -s "-screen 0 ${size}x24" sh -c '
    openbox >/tmp/dienmay-package-wm-$$.log 2>&1 &
    wm=$!
    trap "kill $wm 2>/dev/null || true" EXIT INT TERM
    sleep 1
    java -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8 \
      -cp "dist/common-dto.jar:dist/backend-server.jar:dist/client-swing.jar:build/tests" \
      vn.dienmay.tests.PackagedAppSmoke dist/client-swing.jar docs/evidence/packaged
  ' > "docs/evidence/packaged-${size}.log" 2>&1
done
echo "PASS: offline tests, Swing fixture checks and packaged login startup. NOT SQL Server acceptance."
