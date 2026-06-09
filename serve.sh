#!/usr/bin/env bash
#
# serve.sh — build (if needed) and launch the web visualizer.
#   ./serve.sh           # serve on http://localhost:8000
#   ./serve.sh 9000      # serve on a different port
#
set -euo pipefail
cd "$(dirname "$0")"
JAR="lib/antlr-4.13.2-complete.jar"

# Build if the classes are missing.
if [ ! -d out ] || [ ! -f out/minitype/WebServer.class ]; then
  ./build.sh
fi

PORT="${1:-8000}"
echo "Starting MiniType visualizer on http://localhost:$PORT  (Ctrl+C to stop)"
exec java -cp "out:$JAR" minitype.WebServer "$PORT"
