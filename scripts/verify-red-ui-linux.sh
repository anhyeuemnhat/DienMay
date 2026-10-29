#!/bin/sh
# Real Swing/JAR regression at both requested screen sizes. Requires Xvfb and Openbox.
set -eu
cd "$(dirname "$0")/.."
for tool in ant java timeout xvfb-run openbox; do
  command -v "$tool" >/dev/null || { echo "Missing test tool: $tool" >&2; exit 1; }
done
mkdir -p docs/red-ui
ant clean verify > docs/red-ui/ant-clean-verify.log 2>&1
for size in 1366x768 1920x1080; do
  for suite in UiSmoke RedUiChecks PackagedAppSmoke; do
    case "$suite" in
      UiSmoke) arguments="docs/red-ui/screenshots"; log="ui-${size}.log" ;;
      RedUiChecks) arguments="docs/red-ui/checks"; log="checks-${size}.log" ;;
      PackagedAppSmoke) arguments="dist/client-swing.jar docs/red-ui/standalone"; log="jar-${size}.log" ;;
    esac
    timeout 180s xvfb-run -a -s "-screen 0 ${size}x24" sh -c '
      openbox --config-file /etc/xdg/openbox/rc.xml > /tmp/dienmay-red-ui-wm-$$.log 2>&1 &
      wm=$!
      trap "kill $wm 2>/dev/null || true" EXIT INT TERM
      sleep 1
      java -Dawt.useSystemAAFontSettings=on -Dswing.aatext=true \
        -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8 \
        -cp "dist/common-dto.jar:dist/backend-server.jar:dist/client-swing.jar:build/tests" \
        "vn.dienmay.tests.$1" $2
    ' sh "$suite" "$arguments" > "docs/red-ui/$log" 2>&1
  done
done
echo "PASS: offline regression, both UI resolutions, JAR resources and standalone JAR startup."
echo "Scope: synthetic HTTP fixtures, not live SQL Server acceptance."
