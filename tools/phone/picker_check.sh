#!/bin/sh
# Android's folder picker and storage service on the emulator: some phones' pickers show "No items"
# (the storage service hides everything when it cannot see /storage/emulated). Run on the fresh
# phone and again after the tests, to see whether the phone itself or the tests cause it.
#   sh tools/phone/picker_check.sh NAME     writes diag/NAME.txt and diag/NAME-picker.png
name="$1"
mkdir -p diag
f="diag/$name.txt"
{
  echo "== $name"
  adb shell getprop ro.build.fingerprint
  adb shell getprop ro.build.version.security_patch
  adb shell dumpsys package com.android.externalstorage | grep -m2 -E "versionName|lastUpdateTime"
  adb shell pm list packages --show-versioncode | grep -iE "providers.media|mediaprovider|documentsui"
  adb shell ls -ld /storage /storage/emulated /storage/emulated/0 /storage/emulated/0/Android/data
  echo "-- storage service, top of storage:"
  adb shell content query --uri "content://com.android.externalstorage.documents/document/primary%3A/children" 2>&1 | head -20
  echo "-- storage service, Pictures:"
  adb shell content query --uri "content://com.android.externalstorage.documents/document/primary%3APictures/children" 2>&1 | head -20
} > "$f" 2>&1
adb logcat -c
adb shell am start -a android.intent.action.OPEN_DOCUMENT_TREE \
  --eu android.provider.extra.INITIAL_URI "content://com.android.externalstorage.documents/document/primary%3A"
sleep 10
adb exec-out screencap -p > "diag/$name-picker.png"
{
  echo "-- storage service log:"
  adb logcat -d -s ExternalStorage FileSystemProvider | grep -v "^[[:space:]]*at " | head -20
} >> "$f" 2>&1
adb shell input keyevent KEYCODE_BACK
sleep 2
exit 0
