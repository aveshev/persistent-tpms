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
  - `OngoingAlerts`: the red and critical alerts going on, what the vehicle menu blinks for, like
    Android's `AlertSpeaker.ongoing`. `Blink`: the blinking elements' phases, like `Blink.kt`.
- `App/Sources`: the app.
  - `Scanner`: CoreBluetooth, in the foreground and the background.
  - `Monitor`: the app's state, what's received, stored and alerted on.
  - `Notifier`: one notification per tyre and alert class, and the ride notification.
  - `RidePrompts`, `RideNotificationViews`, `GoingForARideIntent`: the ride notification's triggers,
    settings and Shortcuts action. `Packages/TPMSCore`'s `RideTriggers` and `RideBeacon` hold their
    rules, tested on the Mac.
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
connected or a beacon is near. Background scanning is cheap on iOS, filtered in the Bluetooth chip
by service UUID, so the iOS app simply listens in the background whenever a sensor is assigned
(Persistent mode, on by default). But it has no higher scan mode to switch on, so what starts a
ride there is a notification instead.

### Ride notification

"Going for a ride? Open Persistent TPMS to monitor tire pressure": opening the app gets the full scan
rate for as long as it's open and the phone unlocked. Silent, at the normal level, so **Driving Focus
may hold it back**: a time-sensitive level isn't used for it, and this is a free developer account.
Off until its switch on the Persistent mode page is turned on, which asks for the permission to notify
and, the first time, turns on the sensor trigger. Each trigger is optional, under "Notify when ...".
Only the sensor one needs Persistent scanning, and is greyed out without it. Nothing while the app is
open, which also removes it.

There's only ever one, and no fixed limit on how often. A trigger firing while it's showing leaves it
as it is and keeps it current for another hour. A trigger firing with none showing, or with one left
stale by an hour without any firing, posts a new one: the old one is removed first, so it comes with a
banner at the top of the list. iOS can't expire a notification and a suspended app has no timer, so an
unopened one is only removed when something next wakes the app, which can be much later than the hour.

`RideTriggers` compares stored times when a report arrives, a suspended app running no timer. A
trigger fires when heard after 30 minutes away from the vehicle, and only a signal from -85 dBm counts as hearing
it, like Android's beacons. So a bike out of reach of the house reads as absent, and a stop shorter
than that doesn't prompt again.

| Trigger | Wakes the app with | Needs | Ends after a swipe away |
|---|---|---|---|
| Sensor | The scan already running | Persistent scanning, no setup | Yes |
| Beacon, by service | Its service UUID added to the scan | A device advertising one | Yes |
| Beacon, by connection | A pending connection by its identifier | A connectable device | Yes |
| Shortcuts | An automation running the "Going for a ride" App Intent | Setting it up in Shortcuts | No |

- **Sensor.** Heard after a long gap, each vehicle on its own. A single report is enough: most sensors
  only send once the wheel turns (a parked Wicarlink every 5.5 minutes, which a walk past rarely
  catches), so this comes as the ride starts rather than before it.
- **Beacon, picked in a foreground scan with no filter.** Tyre sensors and devices with neither name
  nor 128-bit service aren't listed: nearly all rotate their address and couldn't be recognised after,
  and iOS doesn't show which do, so one with a fixed address is left out too. Each row shows the start
  of its identifier, to tell devices apart. The page works like Android's (`BeaconList`): it keeps
  scanning while seen, and its order only changes when reloading or sorting. Those with only a name or
  manufacturer data, not connectable, aren't listed either, as iOS reports nothing about them in the
  background (iBeacons and AirTags too). With the debug options on, a star marks those woken by
  connecting. A service comes first: each report has a signal strength
  and nothing is taken from the beacon. A report needs confirming by a second one, 5 seconds to 5
  minutes later, like Android's two packets, the window wider for the background's slower reports.
- **Several beacons, like Android's page.** Listed with their signal, "Was nearby" or "Not nearby"
  (`BeaconPresence`), renamed, and removed with an undo until the page is left, which turns the trigger
  off with none left. They count as one for the notification: two on a vehicle make a single one, and
  one heard all day hides the others coming back, so a beacon at home can hide one on the bike.
- **Beacon, by connection.** On connecting the signal is read, then the connection is let go of at
  once: held, a device typically stops advertising and its maker's app can't connect. The next one is
  left to iOS 2 minutes later, by `CBConnectPeripheralOptionStartDelayKey`, so a suspended app needs no
  timer. Connecting again at once instead happens several times a second while the beacon is near. The
  request's time and delay are stored (`BeaconConnection`), as iOS may end the app. A connection
  completing within 2 minutes after its delay ended means the beacon was probably in range all along:
  no prompt. Later, it was away. Being away counts towards the time away from the vehicle, and only a
  gap of that length prompts, as for the other triggers. So a trip shorter than 2 minutes can go
  unnoticed, and a prompt can be late by up to 2 minutes. A signal below -85 dBm, or none measured (iOS
  sometimes reports 0 dBm), counts as away from the second read in a row on: a beacon sitting still
  reads a few dB either side of the threshold. A device asking for security may show a pairing request.

  Tested on an iPhone 13 Pro, iOS 18, locked, the app in the background, with a Mac as the beacon: 2 and
  5 minute delays connected within 5 s, by day and overnight, plugged in or not (one run of 2 minutes
  on Nov 2–3 came 42 to 122 minutes late each time, with another build, unexplained). Back after 5
  minutes to 4 hours away, the pending connection completed within 2 s. A pending connection survives
  reinstalling the app, iOS relaunching it to complete it. After a swipe away, nothing came for 5.5
  minutes with the beacon on. Not tested: iOS ending the app to free memory while a connection is
  pending, a real beacon, a moving phone.
- **Beacons with a rotating private address.** iOS gives only an identifier specific to this iPhone,
  which changes with the address. A beacon by service is still recognised by its name or its own 128-bit
  service, not by a 16-bit one, which stands for a whole kind of device. A beacon by connection isn't:
  its identifier is all there is, so it stays "Not nearby" once the address changed.
- **Shortcuts.** Only an automation sees classic Bluetooth devices (a car's audio, many headsets),
  which apps can't list. iOS has no API for an app to create one or pre-select its device, so the page
  has a `ShortcutsLink`, a guide calling out Run Immediately, and with the debug options on "Last
  triggered", recorded when the action runs since the app can't see the automation. The action also
  restarts the scan.

iBeacon region monitoring is left out: it needs "Always" location access.

### What it has

The settings and menus follow Android's, screen by screen:

- **Main screen**: the vehicle's drawing, each tyre coloured by its temperature, red and blinking
  while it alerts, an outline once its reading is 5 minutes old, its readout beside it ("LOW!"/"HIGH!"
  while critical, an asterisk on a calibrated pressure). Tap to magnify a tyre's readout, long press
  for its sensor and readings. "Tap to assign" outlines on the tyres without a sensor. The silence
  button while an alert sound plays.
- **Vehicle menu**: switching, adding a vehicle, its kind chosen when adding it, like on Android. A
  red disc blinks behind its arrow while another vehicle has a red or critical alert, and the list's
  alerting names blink red, see [docs/alerts.md](../docs/alerts.md). A popover rather than a `Menu`,
  whose lines can't blink. Nothing dismisses an alert here, so one stops counting 30 minutes after
  its sensor's last reading.
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
    when idle (black over the content, the status bar and home indicator hidden, the brightness left to iOS, the
    proximity sensor turning the screen off in a pocket). Like on Android, the phone doesn't lock
    itself while the main screen is shown.
  - Readings: units, time since last update with its details, battery.
  - Alerts: notifications, alert sound (speech or tones), vibration. Pressure, temperature, battery
    (red once it stays so 10 minutes), the sensor's own alarm.
  - Background monitoring: Persistent mode, with the ride notification.
  - Import a database, exported by Android or iOS.
  - About, with Android's 10 taps on the version for the Debug page: sensor ID, sensor flags, export
    database.
  - Misc: demo mode, pressure loss while riding (experimental, like on Android).

### Differences from Android

- **The alert sound is off by default** (speech on Android) until it's been tried on a phone. iOS
  runs the app in the background only when a sensor is heard, so it may play late or not at all there.
- **A red or critical alert stops holding the screen bright 30 minutes after its last reading.** Android
  only holds live readings, until dismissed. Without a
  limit, an old reading kept in the database would stop the screen ever dimming.
- **Alert notifications follow Android's rules (`AlertAction`, `AlertSnoozes`, docs/alerts.md)**: the 5 s
  wait before raising, the 3 min hold, sounding, folding a leak into a red pressure, snoozes, re-evaluating
  on a threshold change, and the wording. What iOS changes:
  - The "Dismiss" actions take a long press. Clearing one (swiped left, Clear) counts as its shorter
    period, as a swipe away does on Android: seen on an iPhone 13 Pro, iOS 18, iOS relaunching the app
    to tell it. Clear All in Notification Center leaves the ones above its line, the recent ones.
  - Tapping a notification removes it, which Android's doesn't: it comes back with the next reading,
    silently for amber. Red and crimson sound at every reading anyway.
  - Which notifications show is only known at a reading: a sensor gone quiet, or an app swiped away,
    brings nothing back.
  - A suspended app runs no timer: a hold ending applies its reading when something next wakes the app.
  - No channel per level for the user to set up, and a notification's time is when it was posted, not
    the reading's: iOS has neither. Speech waits a fixed 2 s for a notification's sound, which iOS doesn't
    report the end of.
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
  button and the debug options about scanning: a background app has no higher scan rate to switch on, and
  its background scanning is cheap enough to leave on. Bluetooth devices and beacons trigger the ride
  notification instead.
- **An app icon** and **localisation**.

## Building

Needs Xcode 16 and [XcodeGen](https://github.com/yonaskolb/XcodeGen) (`brew install xcodegen`).
With the Command Line Tools selected rather than Xcode, prefix the commands with
`DEVELOPER_DIR=/Applications/Xcode.app/Contents/Developer`.

```bash
./gradlew writeIosVersion
cd ios
xcodegen generate
(cd Packages/TPMSCore && swift test)
xcodebuild -project PersistentTPMS.xcodeproj -scheme PersistentTPMS -destination 'generic/platform=iOS' -derivedDataPath build -allowProvisioningUpdates build
xcrun devicectl device install app --device <device id> build/Build/Products/Debug-iphoneos/PersistentTPMS.app
```

`./gradlew writeIosVersion`, from the repository's root (`JAVA_HOME` set to Android Studio's JBR,
`"/Applications/Android Studio.app/Contents/jbr/Contents/Home"`), writes the Android app's version
to `Version.xcconfig`, not committed, see `Version.base.xcconfig`: `CFBundleShortVersionString` is the
plain version (`0.9.0`), `CFBundleVersion` the Android `versionCode`, and the app settings show the
`versionName` (`0.9.0-dev+<sha>`). Write it again after each commit: the build warns when it was
written for another commit or is missing, and a Release build fails. The version itself is bumped
with the Android app's, in `gradle/libs.versions.toml`, see [PUBLISHING.md](../PUBLISHING.md).
`xcodegen generate` again after adding or removing a file. `xcrun devicectl list devices` gives
the device's ID. A free Apple account's signing lasts 7 days, then the app needs installing again.

`./run-demo.sh <simulator id> <dir> main tyre empty assign bluetooth qr manage managecar vehicle
vehicles settings persistent` screenshots the demo in the Simulator, see `Demo.swift`.

The main screen's layout can be checked without a Simulator or a phone, as Mac Catalyst: generate a
copy of `project.yml` with `SUPPORTS_MACCATALYST: YES` under another name, build it for
`platform=macOS,variant=Mac Catalyst`, and run the app's binary with `-demo` and either `-layoutcheck`
(prints each vehicle kind's text and image sizes, `MISFIT` where something overlaps) or
`-kind CAR -textscale 3.1176 -render out.png` (`-landscape`, `-unassigned`, `-screen manage`,
`-otheralert red|crimson`). Catalyst ignores
Dynamic Type, so `-textscale` stands for it, 0.8235 for its smallest size and 3.1176 for its largest.

`python3 ios/compare_strings.py`, from the repository's root, lists the text that differs from the
Android app on `origin/develop`: non-breaking spaces, other wordings, what only one app has.
