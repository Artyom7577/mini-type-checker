#!/usr/bin/env bash
#
# test.sh — run the checker on every example and verify the outcome:
#   examples/ok_*.mt   must type-check  (exit 0)
#   examples/err_*.mt  must be rejected (exit 1, with type errors)
#
cd "$(dirname "$0")"
JAR="lib/antlr-4.13.2-complete.jar"

pass=0; fail=0
for f in examples/*.mt; do
  base="$(basename "$f")"
  out="$(java -cp "out:$JAR" minitype.Main "$f" 2>&1)"; code=$?
  if [[ "$base" == ok_* ]]; then expect=0; else expect=1; fi
  if [ "$code" -eq "$expect" ]; then
    printf 'PASS  %-26s (exit %d)\n' "$base" "$code"
    pass=$((pass+1))
  else
    printf 'FAIL  %-26s (exit %d, expected %d)\n' "$base" "$code" "$expect"
    echo "$out" | sed 's/^/      | /'
    fail=$((fail+1))
  fi
done
echo "------------------------------------------------------------"
echo "$pass passed, $fail failed"
[ "$fail" -eq 0 ]
