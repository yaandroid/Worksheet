#!/usr/bin/env bash
# Rebuild web/public from the shared engine AND regenerate the self-contained install.sh
set -e
cd "$(dirname "$0")"
node build-web.js
tar -czf brahm-web.tar.gz public server.js
B64="$(base64 -w0 brahm-web.tar.gz)"
# header is everything in install.sh.tmpl up to __PAYLOAD__
cat install.sh.header > install.sh
echo "$B64" >> install.sh
chmod +x install.sh
rm -f brahm-web.tar.gz
echo "Built install.sh ($(du -h install.sh | cut -f1))"
