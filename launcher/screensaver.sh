#!/usr/bin/env bash
# Select the widgets-only screensaver, or restore the TV's earlier selection with --undo.
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")"
ADB=${ADB:-adb}
adb_() { "$ADB" ${ADB_SERIAL:+-s "$ADB_SERIAL"} "$@"; }
sh_() { adb_ shell "$@" </dev/null; }
installed() { sh_ pm path "$1" 2>/dev/null | grep -q '^package:'; }
source ../scripts/screensaver.sh

mode=enable
dry=0
for arg in "$@"; do
  case $arg in
    --undo) mode=undo ;;
    --dry-run) dry=1 ;;
    *) echo "usage: $0 [--undo] [--dry-run]" >&2; exit 2 ;;
  esac
done
[[ $(adb_ get-state 2>/dev/null || true) == device ]] || { echo "error: no TV connected" >&2; exit 1; }
if [[ $dry == 1 ]]; then
  echo "[dry-run] screensaver: $mode (settings and packages unchanged)"
  exit 0
fi
if [[ $mode == undo ]]; then
  restore_widget_screensaver
  echo "screensaver: earlier selection restored"
else
  if ! enable_widget_screensaver; then
    restore_widget_screensaver
    exit 1
  fi
  echo "screensaver: Hyrule Home widgets (press the remote to dismiss)"
fi
