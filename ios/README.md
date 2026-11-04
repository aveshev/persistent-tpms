# Persistent TPMS for iOS

The iOS version of Persistent TPMS, starting with Lite. A native SwiftUI app: the Android app's
models are Android types (`Parcelable`, `ParcelUuid`, Metro, SQLDelight's Android driver), and
sharing its Kotlin through Kotlin Multiplatform would mean reworking every module of the Android
app first. The iOS app ports what it needs instead, and checks it against the same real packets
as the Android tests.

## Layout

- `Packages/TPMSCore`: everything without UI, a Swift package whose tests run on the Mac
  (`swift test`, no phone needed).
  - `AdvertisingPacket`: the decoders (Pecham, Bekubee KY, Wicarlink, Bekubee TPMS, Sysgration),
    ported from `data/vehicle`'s `AdvertisingPacket.kt` and `Raw*.kt`. `CRCTables.swift` is
    generated from `RawPecham.kt`'s and `RawBekubeeKy.kt`'s tables.
  - `Units`, `Vehicle`, `Sensor`: the models, ported from `data/vehicle` and `data/unit`.
  - `Alerts`: `AlertThresholds`, `TyreAlerts` and the pressure loss tracker, see
    [docs/alerts.md](../docs/alerts.md).
  - `Database`: SQLite in the Android app's `car.db` schema (version 12, `12.db`), so a database
    opens in either app.
  - `BurstFilter`: only the first copy of a sensor's burst goes through, like Android's.
- `App/Sources`: the app.
  - `Scanner`: CoreBluetooth, in the foreground and the background.
  - `Monitor`: the app's state, what's received, stored and alerted on.
  - `Notifier`: one notification per tyre and alert class.
  - The screens: `MainView` and `VehicleView` (the vehicle's drawing, its tyres and readouts, like
    Android's `Vehicle.kt` and `TyreStat.kt`), `DimWhenIdle`, `TyreDetailView`, `AssignSensorSheet`,
    `BluetoothAssignView`, `QrCodeScanView`, `ManageSensorsView`, `ShareSensorsView`, `AddVehicleView`,
    `VehicleSettingsViews`, `SettingsViews`, `SimulateReadingView`. `AlertSpeaker`: the alert sound's
    repeats, ported from Android's. `Theme`: Android's tyre colours, iOS's own for the rest.
  - `Demo`: made-up vehicles from real packets, the demo mode setting, and the Simulator's way in
    (`-demo`), which has no Bluetooth.

## What iOS changes

- **iOS doesn't give an advertisement's bytes**, only its name, service UUIDs and manufacturer
  data. `AdvertisingPacket(name:serviceUUIDs:manufacturerData:)` lays them out again in the
  order each sensor sends them. Pecham's and Wicarlink's CRCs cover more than their manufacturer
  data, so the order matters. The stored readings are then the same bytes as on Android:
  checked against the S9's readings of the same broadcasts on 2026-10-31.
- **No Bluetooth address on iOS.** Android identifies Pecham and Bekubee KY sensors by theirs
  (`address.hashCode()`), the iOS app by the identifier iOS gives the peripheral, hashed the same
  way. That identifier is this iPhone's own: these sensors are assigned again on each iPhone, and
  their IDs in a database from Android don't match.
- **Background listening.** iOS scans in the background only for the services it's told, now
  and then, and reports a peripheral once per scan: the scan starts over after each report. On
  the NMax, a locked iPhone under the seat caught 6 of 8 Wicarlink broadcasts and 6 of 32 Pecham
  ones (the S9 all of them), with the app open everything it could hear. iOS relaunches the app
  for its scan after the system ended it or crashed (state restoration), and after a restart once
  the phone is first unlocked, but not after the user swiped it away. See Apple's
  [TN3115](https://developer.apple.com/documentation/technotes/tn3115-bluetooth-state-restoration-app-relaunch-rules).
  Since iOS 26, only apps using AccessorySetupKit are relaunched after being swiped away, and this
  app doesn't use it: its scan would then only return accessories picked in the system's sheet.

## Lite on iOS

Android's Lite scans in the background only while a Bluetooth device it's told about is
connected or a beacon is near. iOS gives apps neither the classic Bluetooth connections (the
car's audio) nor a way to wake on them. Background scanning is cheap on iOS, filtered in the
Bluetooth chip by service UUID, so the iOS app simply listens in the background whenever a
sensor is assigned (Persistent mode, on by default).

### What it has

The settings and menus follow Android's, screen by screen:

- **Main screen**: the vehicle's drawing, each tyre coloured by its temperature, red and blinking
  while it alerts, an outline once its reading is 5 minutes old, its readout beside it ("LOW!"/"HIGH!"
  while critical, an asterisk on a calibrated pressure). Tap to magnify a tyre's readout, long press
  for its sensor and readings. "Tap to assign" outlines on the tyres without a sensor. The silence
  button while an alert sound plays.
- **Vehicle menu**: switching, a warning on the vehicles alerting, adding a vehicle. Its kind is
  chosen when adding it, like on Android.
- **Gear menu**: Vehicle settings, Persistent mode, App settings, and in debug builds with the debug
  options on, Simulate a reading (shown and alerted on, not stored).
- **Vehicle settings**: Pressure (front, rear, spare), Temperature and Battery, each on its own page
  with Android's legend, the values typed in or stepped. Manage sensors: move or swap sensors around
  the vehicle, tyre rotations included, move one to another vehicle, calibrate one or all of them,
  delete one. Share sensors as a QR code. Clear all sensors. Edit the name, delete the vehicle.
- **Assigning a sensor** like on Android: the Sysgration sensor detected at that wheel (its "S" badge),
  its QR code (a kit's, a Wicarlink cap's, or sensors shared by another phone), or by Bluetooth,
  following the pressure as it's put on the wheel, taken off and put back.
- **App settings**:
  - Display: theme, readings text size (on top of Dynamic Type), tap to magnify and for how long, dim
    when idle (the brightness divided, darkened over the content at the lowest brightness already, the
    proximity sensor turning the screen off in a pocket). Like on Android, the phone doesn't lock
    itself while the main screen is shown.
  - Readings: units, time since last update with its details, battery.
  - Alerts: notifications, alert sound (speech or tones), vibration. Pressure, temperature, battery
    (red once it stays so 10 minutes), the sensor's own alarm.
  - Background monitoring: Persistent mode.
  - Import a database, exported by Android or iOS.
  - About, with Android's 10 taps on the version for the Debug page: sensor ID, sensor flags, export
    database.
  - Misc: demo mode, pressure loss while riding (experimental, like on Android).

### Differences from Android

- **The alert sound is off by default** (speech on Android) until it's been tried on a phone. iOS
  runs the app in the background only when a sensor is heard, so it may play late or not at all there.
- **A red or critical alert stops holding the screen bright 30 minutes after its last reading.** Android
  only holds live readings, until dismissed, which iOS has no notification action for yet. Without a
  limit, an old reading kept in the database would stop the screen ever dimming.
- **Persistent mode is a menu line that opens its page**: an iOS menu line can't hold a switch.
- **Shared Pecham and Bekubee KY sensors are left out of the QR code**: their IDs are this phone's own,
  they're assigned by Bluetooth.

- **The main screen's layout follows Android's `VehicleSizing`, `FitsText` and `ClearOfReadouts`**, with
  these differences: its top bar is iOS's 44 pt, taller as its title grows, rather than 64 dp. The text fits
  before anything is drawn, from what's shown right now, so it grows back when an extra line goes (Android
  only shrinks until restarted), and goes as low as it takes rather than stopping at half. The top bar's
  placement compares what fits, then the text, then the image, once each has fit its text, and no readout
  or outline goes under the bar.

### Left out

- **Android Auto, shortcuts**: CarPlay needs Apple's approval for this category.
- **Scan conditions** (Bluetooth devices, beacons, Wi-Fi, stay active, suspend when idle), the monitoring
  button and the debug options about scanning: iOS gives an app nothing to act on, and its background
  scanning is cheap enough to leave on.
- **An app icon** and **localisation**.

## Building

Needs Xcode 16 and [XcodeGen](https://github.com/yonaskolb/XcodeGen) (`brew install xcodegen`).
With the Command Line Tools selected rather than Xcode, prefix the commands with
`DEVELOPER_DIR=/Applications/Xcode.app/Contents/Developer`.

```bash
cd ios
xcodegen generate
(cd Packages/TPMSCore && swift test)
xcodebuild -project PersistentTPMS.xcodeproj -scheme PersistentTPMS -destination 'generic/platform=iOS' -derivedDataPath build -allowProvisioningUpdates build
xcrun devicectl device install app --device <device id> build/Build/Products/Debug-iphoneos/PersistentTPMS.app
```

`xcodegen generate` again after adding or removing a file. `xcrun devicectl list devices` gives
the device's ID. A free Apple account's signing lasts 7 days, then the app needs installing again.

`./run-demo.sh <simulator id> <dir> main tyre empty assign bluetooth qr manage managecar vehicle
vehicles settings persistent` screenshots the demo in the Simulator, see `Demo.swift`.

The main screen's layout can be checked without a Simulator or a phone, as Mac Catalyst: generate a
copy of `project.yml` with `SUPPORTS_MACCATALYST: YES` under another name, build it for
`platform=macOS,variant=Mac Catalyst`, and run the app's binary with `-demo` and either `-layoutcheck`
(prints each vehicle kind's text and image sizes, `MISFIT` where something overlaps) or
`-kind CAR -textscale 3.1176 -render out.png` (`-landscape`, `-unassigned`, `-screen manage`). Catalyst ignores
Dynamic Type, so `-textscale` stands for it, 0.8235 for its smallest size and 3.1176 for its largest.

`python3 ios/compare_strings.py`, from the repository's root, lists the text that differs from the
Android app on `origin/develop`: non-breaking spaces, other wordings, what only one app has.
