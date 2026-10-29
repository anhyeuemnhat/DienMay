#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
mvn verify
mkdir -p dist
for module in common-dto backend-server client-swing; do cp "$module/target/$module.jar" "dist/$module.jar"; done
# Maven client manifest is intentionally minimal; launch using an explicit shared classpath.
echo 'Maven built. Client: java -cp "dist/common-dto.jar:dist/client-swing.jar" vn.dienmay.client.App'
