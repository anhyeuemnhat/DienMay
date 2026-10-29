#!/bin/sh
# Run real Swing windows with bounded execution; never force maximize through the window manager.
set -eu
cd "$(dirname "$0")/.."
for tool in ant java timeout xvfb-run openbox; do
  command -v "$tool" >/dev/null || { echo "Missing UI test tool: $tool" >&2; exit 1; }
done
ant compile-tests
for size in 1366x768 1920x1080; do
  timeout 120s xvfb-run -a -s "-screen 0 ${size}x24" sh -c '
    openbox --config-file /etc/xdg/openbox/rc.xml >/tmp/dienmay-openbox-$$.log 2>&1 &
    wm=$!
    trap "kill $wm 2>/dev/null || true" EXIT INT TERM
    sleep 1
    java -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8 \
      -cp "build/common:build/backend:build/client:build/tests" \
      vn.dienmay.tests.UiSmoke docs/evidence/ui
  ' > "docs/evidence/ui-${size}.log" 2>&1
done
