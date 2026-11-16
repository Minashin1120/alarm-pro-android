#!/usr/bin/env bash
# Grant WRITE_SECURE_SETTINGS to MathWake over ADB.
#
# MathWake only needs its accessibility service while an alarm is ringing. Android gives apps no
# public way to enable/disable an accessibility service, so without this permission the service
# has to stay enabled all day. Payment / banking apps (and Play Integrity's App Access Risk)
# then treat MathWake as a risky side-loaded app and may refuse to work.
#
# With this permission MathWake enables the service when the alarm starts and removes it again
# when the alarm is dismissed. The permission survives app updates and reboots.
#
# Requirements:
#   - Android SDK platform-tools (adb) on your PC
#   - USB debugging enabled on the phone
#   - On some Xiaomi/OnePlus devices also enable
#     "USB debugging (Security settings)" or "Disable permission monitoring".
#
# Usage:
#   ./scripts/grant-accessibility-control.sh
#   ADB=/path/to/adb ./scripts/grant-accessibility-control.sh

set -euo pipefail

ADB="${ADB:-adb}"
PACKAGE="com.aistudio.mathwake.qvxn"
PERMISSION="android.permission.WRITE_SECURE_SETTINGS"

if ! command -v "$ADB" >/dev/null 2>&1; then
  echo "ERROR: adb not found. Install Android platform-tools and add it to PATH." >&2
  exit 1
fi

echo "Checking connected device..."
"$ADB" get-state >/dev/null

# Android 13+ blocks accessibility services of side-loaded apps unless
# ACCESS_RESTRICTED_SETTINGS is allowed. Older releases do not have this app-op.
echo "Allowing restricted settings for $PACKAGE (Android 13+)..."
"$ADB" shell cmd appops set "$PACKAGE" ACCESS_RESTRICTED_SETTINGS allow || true

echo "Granting $PERMISSION to $PACKAGE..."
if ! "$ADB" shell pm grant "$PACKAGE" "$PERMISSION"; then
  cat >&2 <<'EOF'

Failed to grant WRITE_SECURE_SETTINGS.

Common reasons:
  - The app is not installed.
  - Developer options / USB debugging is off.
  - On Xiaomi/OnePlus/realme, enable "USB debugging (Security settings)" or
    "Disable permission monitoring" in Developer options, then reconnect and retry.

Check the package:
  adb shell pm list packages | grep mathwake
EOF
  exit 1
fi

echo
echo "Done. MathWake will now keep the accessibility service off while idle."
echo "If the service is currently enabled, it disables itself within a second."
echo "Verify with:"
echo "  adb shell dumpsys package $PACKAGE | grep WRITE_SECURE_SETTINGS"
