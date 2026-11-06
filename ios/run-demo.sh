#!/bin/bash
# Builds the app for the Simulator and screenshots it in its demo, see App/Sources/Demo.swift:
# ./run-demo.sh <simulator id> <output dir> [screen...]
set -e
cd "$(dirname "$0")"
export DEVELOPER_DIR=${DEVELOPER_DIR:-/Applications/Xcode.app/Contents/Developer}
device=$1
out=$2
shift 2
xcrun simctl boot "$device" 2>/dev/null || true
xcodebuild -project PersistentTPMS.xcodeproj -scheme PersistentTPMS -destination "id=$device" -configuration Debug -derivedDataPath build-sim build | grep -E "error:|BUILD"
xcrun simctl install "$device" build-sim/Build/Products/Debug-iphonesimulator/PersistentTPMS.app
mkdir -p "$out"
for screen in "${@:-main}"; do
    xcrun simctl launch --terminate-running-process "$device" com.aveshev.persistenttpms.lite -demo -screen "$screen" >/dev/null
    sleep 4
    xcrun simctl io "$device" screenshot "$out/$screen.png" >/dev/null
    sips -Z 900 "$out/$screen.png" >/dev/null
done
