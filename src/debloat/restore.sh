#!/usr/bin/env bash
# Bring back every package in the given list that still has a system copy on the phone.
ADB="${ADB:-$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe}"
grep -vE '^\s*(#|$)' "$1" | tr -d '\r' | while read -r pkg; do
  printf '%-45s ' "$pkg"
  "$ADB" shell cmd package install-existing "$pkg" < /dev/null
  "$ADB" shell pm enable --user 0 "$pkg" > /dev/null 2>&1 < /dev/null
done
