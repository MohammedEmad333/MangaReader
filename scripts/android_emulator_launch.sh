#!/usr/bin/env bash
set -euo pipefail

adb wait-for-device
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am force-stop com.mangareader.app || true
adb shell monkey -p com.mangareader.app -c android.intent.category.LAUNCHER 1
sleep 15
pid="$(adb shell pidof com.mangareader.app | tr -d '\r')"
if [[ -z "$pid" ]]; then
  echo "::error::Yomu process was not running after launcher smoke test."
  adb logcat -d -t 500
  exit 1
fi
echo "Yomu process launched successfully (PID $pid)."
python3 scripts/android_updates_smoke.py
