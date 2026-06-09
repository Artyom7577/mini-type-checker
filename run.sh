#!/usr/bin/env bash
#
# run.sh — type-check one MiniType source file.
#   ./run.sh examples/ok_functions.mt           # type-check
#   ./run.sh examples/ok_functions.mt --tree     # also print the parse tree
#
set -euo pipefail
cd "$(dirname "$0")"
JAR="lib/antlr-4.13.2-complete.jar"
exec java -cp "out:$JAR" minitype.Main "$@"
