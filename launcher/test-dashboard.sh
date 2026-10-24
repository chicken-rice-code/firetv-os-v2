#!/usr/bin/env bash
# Pure Java regression tests; never connects to ADB or changes a device.
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")"
JAVAC=${JAVA_HOME:+$JAVA_HOME/bin/}javac
JAVA=${JAVA_HOME:+$JAVA_HOME/bin/}java
SDK=${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}
[[ -f $SDK/platforms/android-25/android.jar ]] || { echo "Set ANDROID_HOME to the Android SDK" >&2; exit 1; }
mkdir -p build/tests
"$JAVAC" --release 8 -classpath "$SDK/platforms/android-25/android.jar" -d build/tests \
  src/tv/dumbtv/home/DashboardLogic.java src/tv/dumbtv/home/Weather.java tests/DashboardLogicTest.java
"$JAVA" -cp build/tests tv.dumbtv.home.DashboardLogicTest
