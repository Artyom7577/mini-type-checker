#!/usr/bin/env bash
#
# build.sh — generate the parser with ANTLR4, then compile all Java sources.
# Idempotent: downloads the ANTLR jar once into lib/ if it is not present.
#
set -euo pipefail
cd "$(dirname "$0")"
ROOT="$(pwd)"

JAR="$ROOT/lib/antlr-4.13.2-complete.jar"
URL="https://www.antlr.org/download/antlr-4.13.2-complete.jar"

if [ ! -f "$JAR" ]; then
  echo ">> ANTLR jar not found — downloading ..."
  mkdir -p "$ROOT/lib"
  curl -fL --retry 2 -o "$JAR" "$URL"
fi

echo ">> Generating parser from src/minitype/MiniType.g4 ..."
rm -rf "$ROOT/gen"
mkdir -p "$ROOT/gen/minitype"
( cd src/minitype && java -jar "$JAR" -visitor -no-listener -package minitype -o "$ROOT/gen/minitype" MiniType.g4 )

echo ">> Compiling Java sources ..."
rm -rf "$ROOT/out"
mkdir -p "$ROOT/out"
javac -cp "$JAR" -d "$ROOT/out" \
  "$ROOT"/gen/minitype/*.java \
  "$ROOT"/src/minitype/types/*.java \
  "$ROOT"/src/minitype/*.java

echo ">> Build OK.  Run:  ./run.sh examples/ok_functions.mt"
