#!/usr/bin/env bash
# Provision MathWake as Device Owner so lock-task mode can block the power menu,
# notification shade, lock screen, Home/Overview and other apps during an alarm.
#
# Requirements:
#   - Android SDK platform-tools (adb) on your PC
#   - USB debugging enabled on the phone
#   - The phone must not already have a device owner / managed profile
#   - On most devices all accounts must be removed first (Google, Samsung, etc.)
#
# Usage:
#   ./scripts/setup-device-owner.sh
#   ADB=/path/to/adb ./scripts/setup-device-owner.sh

set -euo pipefail

ADB="${ADB:-adb}"
PACKAGE="com.aistudio.mathwake.qvxn"
COMPONENT="com.example.alarm.MathWakeDeviceAdminReceiver"

if ! command -v "$ADB" >/dev/null 2>&1; then
  echo "ERROR: adb not found. Install Android platform-tools and add it to PATH." >&2
  exit 1
fi

echo "Checking connected device..."
"$ADB" get-state >/dev/null

echo "Current device owner (if any):"
"$ADB" shell dpm list-owners || true

echo
echo "Setting MathWake as Device Owner..."
if ! "$ADB" shell dpm set-device-owner "$PACKAGE/$COMPONENT"; then
  cat >&2 <<'EOF'

Failed to set Device Owner.

Common reasons:
  - An account (Google/Samsung/etc.) still exists on the device.
  - The device already has a device owner or a work profile.
  - The device was provisioned already (a factory reset may be required).
  - The app is not installed or the component name is wrong.

Check with:
  adb shell dpm list-owners
  adb shell pm list packages | grep mathwake
EOF
  exit 1
fi

echo
echo "Done. Verify with:"
echo "  adb shell dpm list-owners"
