#!/usr/bin/env bash
# Remove every package in the given list for the phone's main user (no root, reversible).
ADB="${ADB:-$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe}"
grep -vE '^\s*(#|$)' "$1" | tr -d '\r' | while read -r pkg; do
  printf '%-45s ' "$pkg"
  out=$("$ADB" shell pm uninstall -k --user 0 "$pkg" < /dev/null)
  # some Nothing apps refuse uninstall; disabling them has the same effect
  case "$out" in Failure*) out="$out -> $("$ADB" shell pm disable-user --user 0 "$pkg" < /dev/null)";; esac
  echo "$out"
done
