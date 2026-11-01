#!/bin/sh
# Android's folder picker and storage service on the emulator: some phones' pickers show "No items"
# (the storage service hides everything when it cannot see /storage/emulated). Opens the picker at
# the top of storage, optionally right after a shell command, to find what brings the fault on.
#   sh tools/phone/picker_check.sh NAME [SHELL COMMAND]   writes diag/NAME.txt and diag/NAME-picker.png
name="$1"
shift
mkdir -p diag
f="diag/$name.txt"
since=$(adb shell date "+%m-%d\ %H:%M:%S.000" | tr -d '\r')
{
  echo "== $name"
  adb shell getprop ro.build.fingerprint
  adb shell getprop ro.build.version.security_patch
  adb shell pm list packages --show-versioncode | grep -iE "providers.media|mediaprovider|documentsui|externalstorage"
  if [ $# -gt 0 ]; then
    echo "-- shell command first: $*"
    adb shell "$@" 2>&1 | head -20
  fi
} > "$f" 2>&1
adb shell am start -a android.intent.action.OPEN_DOCUMENT_TREE \
  --eu android.provider.extra.INITIAL_URI "content://com.android.externalstorage.documents/document/primary%3A"
sleep 10
adb exec-out screencap -p > "diag/$name-picker.png"
{
  echo "-- storage service log since $since:"
  adb shell "logcat -d -T '$since' -s ExternalStorage FileSystemProvider" | grep -v "^[[:space:]]*at " | head -20
  echo "-- times a folder was hidden: $(adb shell "logcat -d -T '$since'" | grep -c 'is hidden')"
} >> "$f" 2>&1
adb shell input keyevent KEYCODE_BACK
sleep 2
exit 0
