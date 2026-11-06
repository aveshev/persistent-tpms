import SwiftUI
import TPMSCore

/// The app's settings, like the Android app's AppSettings
struct AppSettingsView: View {
    @ObservedObject var monitor: Monitor
    @ObservedObject var settings: Settings
    @Environment(\.dismiss) private var dismiss
    @State private var importing = false
    @State private var importError: String?
    @State private var versionTaps = 0
    @State private var versionMessage: String?

    private static let tapsToToggle = 10
    private static let tapsCountedDown = 5

    var body: some View {
        NavigationStack {
            Form {
                Section("Display") {
                    LabeledContent("Theme") {
                        Picker("Theme", selection: $settings.theme) {
                            ForEach(Settings.Theme.allCases) { theme in
                                Image(systemName: theme.systemImage).accessibilityLabel(theme.name).tag(theme)
                            }
                        }
                        .pickerStyle(.segmented)
                        .fixedSize()
                    }
                    NavigationLink {
                        DimSettingsView(settings: settings)
                    } label: {
                        SettingsRow("Dim when idle", settings.dimDisplay ? "after \(settings.dimDisplaySeconds.asDimDelay)" : "Off")
                    }
                    NavigationLink {
                        MagnifierSettingsView(settings: settings)
                    } label: {
                        SettingsRow("Tap to magnify", settings.magnifier ? "for \(settings.magnifierSeconds) seconds" : "Off")
                    }
                    NavigationLink {
                        TextSizeView(settings: settings)
                    } label: {
                        SettingsRow("Sensor readings text size", settings.readoutTextScale.textSizeLabel)
                    }
                }
                Section("Readings") {
                    LabeledContent("Pressure in") {
                        Picker("Pressure in", selection: $settings.pressureUnit) {
                            ForEach(PressureUnit.allCases) { Text($0.symbol).tag($0) }
                        }
                        .pickerStyle(.segmented)
                        .fixedSize()
                    }
                    LabeledContent("Temperature in") {
                        Picker("Temperature in", selection: $settings.temperatureUnit) {
                            ForEach(TemperatureUnit.allCases) { Text($0.symbol).tag($0) }
                        }
                        .pickerStyle(.segmented)
                        .fixedSize()
                    }
                    NavigationLink {
                        TimeSinceUpdateView(settings: settings)
                    } label: {
                        SettingsRow("Time since last update", settings.showTimeSinceUpdate ? "On" : "Off")
                    }
                    Toggle(isOn: $settings.showBatteryVoltage) {
                        SettingsRow("Always show battery", "(even when not low)")
                    }
                }
                Section {
                    Toggle(isOn: Binding(get: { settings.alerts }, set: { monitor.setAlerts($0) })) {
                        SettingsRow("Alert notifications", "One for each tyre and alert. Turning it on asks for permission to notify")
                    }
                    NavigationLink {
                        AlertSoundSettingsView(monitor: monitor, settings: settings)
                    } label: {
                        SettingsRow("Alert sound", settings.alertSound.label)
                    }
                    Toggle(isOn: $settings.vibrateAlerts) {
                        SettingsRow("Vibrate", "Along with the alert sound, harder for a critical alert")
                    }
                } header: {
                    Text("Alerts")
                }
                Section("Background monitoring") {
                    NavigationLink {
                        PersistentModeView(monitor: monitor, settings: settings)
                    } label: {
                        SettingsRow("Persistent mode", "Persistent scanning")
                    }
                }
                Section {
                    Button("Import a database") { importing = true }
                    if let importError {
                        Text(importError).foregroundStyle(Theme.error)
                    }
                } header: {
                    Text("Import")
                } footer: {
                    Text("Replaces everything with a database exported by Persistent TPMS on Android (debug settings, export database). Pecham and Bekubee KY sensors need assigning again: they carry no ID, and Android tells them apart by their Bluetooth address, which iOS doesn't give.")
                }
                about
                Section("Misc") {
                    Toggle(isOn: Binding(get: { settings.demoMode }, set: { monitor.setDemoMode($0) })) {
                        SettingsRow("Demo mode", "Made-up vehicles and sensors, your own come back when it's turned off")
                    }
                    if settings.developer {
                        NavigationLink {
                            DebugSettingsView(monitor: monitor, settings: settings)
                        } label: {
                            SettingsRow("Debug", debugSummary)
                        }
                    }
                    NavigationLink {
                        PressureLossSettingsView(monitor: monitor, settings: settings)
                    } label: {
                        SettingsRow("Pressure loss", "Experimental\(settings.pressureLoss ? ", on" : "")")
                    }
                }
            }
            .fileImporter(isPresented: $importing, allowedContentTypes: [.data]) { result in
                do {
                    try monitor.importDatabase(from: result.get())
                    dismiss()
                } catch {
                    importError = "Couldn't import it: \(error.localizedDescription)"
                }
            }
            .navigationTitle("App settings")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .confirmationAction) { Button("Done") { dismiss() } } }
        }
    }

    private var debugSummary: String {
        guard settings.debugOptions else { return "Off" }
        let shown = [settings.showSensorId ? "Sensor ID" : nil, settings.showSensorFlags ? "Sensor flags" : nil]
            .compactMap { $0 }
            .joined(separator: ", ")
        return shown.isEmpty ? "On" : shown
    }

    private var about: some View {
        Section {
            // 10 taps in a row show the debug options, or hide them again, as Android does with its
            // own developer options
            Button(action: versionTapped) {
                // The full version, like Android's versionName: 0.9.0-dev+<sha>, see Version.base.xcconfig
                SettingsRow("Version", Bundle.main.object(forInfoDictionaryKey: "PersistentTPMSVersionName") as? String)
            }
            Link(destination: URL(string: "https://github.com/VincentMasselis/TPMS-advanced")!) {
                SettingsRow("Based on TPMS Advanced", "By Vincent Masselis, under the Apache License 2.0")
            }
            Link(destination: URL(string: "https://github.com/aveshev/persistent-tpms")!) {
                SettingsRow("Source code", "github.com/aveshev/persistent-tpms")
            }
            Link(destination: URL(string: "https://aveshev.com/persistent-tpms/privacy/")!) {
                SettingsRow("Privacy policy")
            }
            NavigationLink {
                LicensesView()
            } label: {
                SettingsRow("Open-source licenses")
            }
        } header: {
            Text("About")
        } footer: {
            if let versionMessage { Text(versionMessage) }
        }
    }

    private func versionTapped() {
        versionTaps += 1
        let left = Self.tapsToToggle - versionTaps
        if left <= 0 {
            settings.developer.toggle()
            versionTaps = 0
            versionMessage = settings.developer ? "You are now a developer" : "You are no longer a developer"
        } else if left <= Self.tapsCountedDown {
            versionMessage = "Tap \(left) more \(left == 1 ? "time" : "times") to \(settings.developer ? "stop being a developer" : "become a developer")"
        }
    }
}

extension Settings.AlertSound {
    var label: String {
        switch self {
        case .speech: "Speech"
        case .tones: "Tones"
        case .none: "Off"
        }
    }
}

/// Opened from the "Alert sound" item, choosing the sound once it's on
struct AlertSoundSettingsView: View {
    @ObservedObject var monitor: Monitor
    @ObservedObject var settings: Settings

    var body: some View {
        Form {
            Section {
                Toggle("Alert sound", isOn: Binding(
                    get: { settings.alertSound != .none },
                    set: { settings.alertSound = $0 ? settings.lastAlertSound : .none }
                ))
            } footer: {
                Text("Plays in addition to the red and critical alerts' notifications, and repeats while those alerts stay active: every 10 minutes for red, every 20 seconds at first for critical, then every 10 minutes.\n\nIn the background iOS only runs the app when a sensor is heard, so it may play late or not at all. Closing the app stops the repeats too, until a new alerting sensor update is received.")
            }
            if settings.alertSound != .none {
                Section {
                    option(.speech, "Says \"Tyre pressure\", \"Tyre hot\" or \"Sensor battery\" out loud")
                    option(.tones, "A tone pattern, faster and higher pitched for a critical alert")
                }
            }
        }
        .navigationTitle("Alert sound")
        .navigationBarTitleDisplayMode(.inline)
    }

    private func option(_ sound: Settings.AlertSound, _ supporting: String) -> some View {
        Button {
            settings.alertSound = sound
        } label: {
            HStack {
                SettingsRow(sound.label, supporting)
                Spacer()
                if settings.alertSound == sound { Image(systemName: "checkmark").foregroundStyle(.tint) }
            }
        }
    }
}

/// Listening once the app is closed. Android's scan conditions have no equivalent here: iOS gives apps
/// neither the classic Bluetooth connections nor beacons' scan windows to act on, and its background
/// scanning, filtered in the Bluetooth chip, is cheap enough to leave on.
struct PersistentModeView: View {
    @ObservedObject var monitor: Monitor
    @ObservedObject var settings: Settings

    private var isListening: Bool { settings.backgroundMonitoring && monitor.sensors.isEmpty == false }

    var body: some View {
        Form {
            Section {
                Text("When on, keeps monitoring your tyres in the background, while the phone is locked too. That way you don't have to open the app each ride.")
                    .listRowBackground(Color.clear)
                    .listRowInsets(EdgeInsets())
            }
            Section {
                Toggle("Persistent scanning", isOn: Binding(
                    get: { settings.backgroundMonitoring },
                    set: {
                        settings.backgroundMonitoring = $0
                        monitor.appIsActive(true)
                    }
                ))
            } header: {
                Text(settings.backgroundMonitoring == false ? "Current status: Off" : isListening ? "Current status: Listening" : "Current status: No sensor to listen to")
                    .textCase(nil)
            } footer: {
                Text("iOS only lets apps listen now and then in the background, so some readings are missed, more for Pecham sensors than for Wicarlink ones. Swiping the app away in the app switcher stops it until the app is opened again. After restarting the phone, it comes back once the phone is unlocked.\n\nTyres can still be monitored without it: the app scans whenever it is open.")
            }
        }
        .navigationTitle("Persistent mode")
        .navigationBarTitleDisplayMode(.inline)
    }
}

/// What the sensors report, to help investigate them, opened from the "Debug" item once 10 taps on
/// the version made it available. Android's scanning options have no iOS equivalent.
struct DebugSettingsView: View {
    @ObservedObject var monitor: Monitor
    @ObservedObject var settings: Settings
    @State private var exported: ExportedFile?
    @State private var exportError: String?

    private struct ExportedFile: Identifiable {
        let url: URL
        var id: URL { url }
    }

    var body: some View {
        Form {
            Section {
                Toggle("Debug", isOn: $settings.debugOptions)
            } footer: {
                Text("What the sensors and the phone report, to help investigate them. Turning Debug off hides all of it, and keeps what is selected here.")
            }
            Section {
                Toggle("Sensor ID", isOn: $settings.showSensorId)
                Toggle(isOn: $settings.showSensorFlags) {
                    SettingsRow("Sensor flags", "Bits 7 to 0 of the last packet's status bytes, the unset ones greyed out")
                }
            }
            .disabled(settings.debugOptions == false)
            Section("Data") {
                Button {
                    do {
                        exported = ExportedFile(url: try monitor.exportDatabase())
                    } catch {
                        exportError = "Couldn't export it: \(error.localizedDescription)"
                    }
                } label: {
                    SettingsRow("Export database", "Every stored reading, with the raw packet it came from")
                }
                if let exportError {
                    Text(exportError).foregroundStyle(Theme.error)
                }
            }
        }
        .navigationTitle("Debug")
        .navigationBarTitleDisplayMode(.inline)
        // With nothing selected there is nothing to show: same as turning the switch off
        .onDisappear {
            if settings.showSensorId == false, settings.showSensorFlags == false { settings.debugOptions = false }
        }
        .sheet(item: $exported) { ShareSheet(url: $0.url).ignoresSafeArea() }
    }
}

private struct ShareSheet: UIViewControllerRepresentable {
    let url: URL

    func makeUIViewController(context: Context) -> UIActivityViewController {
        UIActivityViewController(activityItems: [url], applicationActivities: nil)
    }

    func updateUIViewController(_ controller: UIActivityViewController, context: Context) {}
}

/// Only the original project's: this app uses no third-party library, unlike the Android one
struct LicensesView: View {
    var body: some View {
        Form {
            Section {
                Link(destination: URL(string: "https://www.apache.org/licenses/LICENSE-2.0")!) {
                    SettingsRow("TPMS Advanced", "By Vincent Masselis, Apache License 2.0")
                }
            } footer: {
                Text("The iOS app uses no third-party library.")
            }
        }
        .navigationTitle("Open-source licenses")
        .navigationBarTitleDisplayMode(.inline)
    }
}

/// The page opened from the "Time since last update" item
struct TimeSinceUpdateView: View {
    @ObservedObject var settings: Settings

    var body: some View {
        Form {
            Section {
                Text("Most TPMS sensors send updates at regular time intervals while riding. However, in some cases the updates can get lost, because of signal obstruction, low sensor battery or other issues. Knowing when exactly the last update arrived from each sensor can help you diagnose these issues.")
                    .listRowBackground(Color.clear)
                    .listRowInsets(EdgeInsets())
            }
            Section {
                NavigationLink {
                    SensorReadingsUpdateDetailsView()
                } label: {
                    SettingsRow("Sensor readings update details", "How often each sensor updates, and how to check a silent one")
                }
            }
            Section {
                Toggle("Time since last update", isOn: $settings.showTimeSinceUpdate)
            }
        }
        .navigationTitle("Time since last update")
        .navigationBarTitleDisplayMode(.inline)
    }
}

/// How often each sensor brand sends an update, and how to tell a silent sensor from a broken one.
/// Only the schedules and pressure steps measured on real sensors, see RawWicarlink.pressure.
struct SensorReadingsUpdateDetailsView: View {
    var body: some View {
        Form {
            Section {
                Text("Sensors from different manufacturers differ in how often they send an update, and in whether they can tell that the wheel is rolling.")
                    .listRowBackground(Color.clear)
                    .listRowInsets(EdgeInsets())
            }
            Section("Known schedules") {
                SettingsRow("WiCarLink", "Every 5.5 minutes, whether the vehicle moves or not, and whenever pressure changes significantly. Reads the pressure in steps of 0.1 bar (about 1.5 psi): a tyre between two steps can read one step lower when cold, and the next one up once warm")
                SettingsRow("Pecham", "Every minute while the wheel rotates. While stationary, only when the pressure is below ~22 psi, and in either case, whenever pressure changes significantly. Reads the pressure in steps of about 0.45 psi (3.1 kPa), shown rounded to 0.1 psi: a steady tyre can go back and forth by one step")
            }
            Section("When updates seem to be missing") {
                Text("If the app does not show the sensor updating as often as you think it should, it could be due to the following reasons:\n\n1. The sensor is not sending any updates at all. You can verify that by briefly taking it off the wheel: it should sense 0 pressure, which should force an update, and the app should show an alert. If it does not, either the sensor itself or its battery may be dead.\n\n2. The sensor is sending updates, but they are not getting to the phone. Changing the phone's position relative to the wheel may help, and so may replacing the sensor's battery with a fresh one. In the worst case, you may need a different sensor brand.\n\n3. The sensor reacts to pressure changes, but fails to detect wheel rotation (if it has rotation detection). Swapping it with a sensor that reports regularly helps tell this apart from #2: if it still doesn't send updates from where the other one does, it's likely faulty and should be replaced. You can use \"Manage sensors\" in the vehicle settings to quickly move them between the wheels.")
                    .listRowBackground(Color.clear)
                    .listRowInsets(EdgeInsets())
            }
        }
        .navigationTitle("Sensor readings update details")
        .navigationBarTitleDisplayMode(.inline)
    }
}

extension Double {
    /// "Default", or a percentage
    var textSizeLabel: String { self == 1 ? "Default" : "\(Int((self * 100).rounded()))%" }
}

extension Int {
    /// "30 seconds", "1 minute", "2 minutes", for a delay in seconds
    var asDimDelay: String {
        self < 60 ? "\(self) seconds" : self == 60 ? "1 minute" : "\(self / 60) minutes"
    }
}

/// The readouts' text size, with a sample, like the Android app's TextSize
struct TextSizeView: View {
    @ObservedObject var settings: Settings
    /// The same steps as Android's system font size
    private static let scales: [Double] = [0.85, 1, 1.15, 1.3, 1.5, 1.8, 2]

    var body: some View {
        let index = Self.scales.indices.min { abs(Self.scales[$0] - settings.readoutTextScale) < abs(Self.scales[$1] - settings.readoutTextScale) } ?? 1
        Form {
            Section {
                // A front left and a front right readout, as tall as the largest size takes so the
                // slider stays put while it's dragged
                HStack {
                    ForEach([Self.sample(240, 21), Self.sample(230, 23)], id: \.pressure) { sample in
                        TyreReadout(
                            tyre: Monitor.Tyre(latest: sample),
                            settings: settings,
                            alignment: .center,
                            date: Date(timeIntervalSince1970: sample.timestamp + 3 * 60)
                        )
                        .frame(maxWidth: .infinity)
                    }
                }
                .padding(.vertical, 8)
            }
            Section {
                HStack {
                    Text("A").font(.system(size: 14, weight: .semibold))
                    Slider(
                        value: Binding(get: { Double(index) }, set: { settings.readoutTextScale = Self.scales[Int($0.rounded())] }),
                        in: 0...Double(Self.scales.count - 1),
                        step: 1
                    )
                    Text("A").font(.system(size: 24, weight: .semibold))
                }
                Text(settings.readoutTextScale.textSizeLabel)
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                    .frame(maxWidth: .infinity)
            } footer: {
                Text("Applies only to sensor readouts, on top of the system font size. If the chosen font size does not fit on the screen, it will be reduced to fit.")
            }
        }
        .navigationTitle("Sensor readings text size")
        .navigationBarTitleDisplayMode(.inline)
    }

    private static func sample(_ kpa: Float, _ celsius: Float) -> SensorReading {
        SensorReading(
            timestamp: 0, rssi: -60, sensorId: 0, pressure: Pressure(kpa: kpa), temperature: Temperature(celsius: celsius),
            brand: .wicarlink, isAlarm: false, batteryVoltage: nil, batteryPercent: nil, location: nil, raw: Data()
        )
    }
}

struct MagnifierSettingsView: View {
    @ObservedObject var settings: Settings

    var body: some View {
        Form {
            Section {
                Toggle("Tap to magnify", isOn: $settings.magnifier)
            } footer: {
                Text("Tap near a tyre on the main screen to temporarily show its readings in a large font size, easy to read even on a rough road. A long press on a tyre opens its sensor and readings.")
            }
            if settings.magnifier {
                Section("Show for") {
                    Picker("Show for", selection: $settings.magnifierSeconds) {
                        ForEach([2, 3, 5, 7, 10], id: \.self) { Text("\($0) seconds").tag($0) }
                    }
                    .pickerStyle(.inline)
                    .labelsHidden()
                }
            }
        }
        .navigationTitle("Tap to magnify")
        .navigationBarTitleDisplayMode(.inline)
    }
}

struct DimSettingsView: View {
    @ObservedObject var settings: Settings

    var body: some View {
        Form {
            Section {
                Toggle("Dim when idle", isOn: $settings.dimDisplay)
            } footer: {
                Text("Lowers the screen's brightness on the main screen once nothing new was alerted or warned about for a while. A red or critical alert going on keeps it bright, a new alert or warning brings it back up. When dimmed, a first touch only brightens the screen, lift your finger and touch again to use the app.")
            }
            if settings.dimDisplay {
                Section("Dim after") {
                    Picker("Dim after", selection: $settings.dimDisplaySeconds) {
                        ForEach([15, 30, 60, 120, 300], id: \.self) { Text($0.asDimDelay).tag($0) }
                    }
                    .pickerStyle(.inline)
                    .labelsHidden()
                }
                Section("Brightness") {
                    LabeledContent("Divide brightness by") {
                        Picker("Divide brightness by", selection: $settings.dimDisplayDivisor) {
                            ForEach([50, 25, 10], id: \.self) { Text("\($0)×").tag($0) }
                        }
                        .pickerStyle(.segmented)
                        .fixedSize()
                    }
                }
                Section {
                    Toggle(isOn: $settings.pocketGuard) {
                        SettingsRow("Block touches in a pocket", "Turns the screen black and ignores touches")
                    }
                } header: {
                    Text("In a pocket")
                }
            }
        }
        .navigationTitle("Dim when idle")
        .navigationBarTitleDisplayMode(.inline)
    }
}

/// The page of the experimental pressure loss warning. It applies to every vehicle, each against its
/// own low pressure alert.
struct PressureLossSettingsView: View {
    @ObservedObject var monitor: Monitor
    @ObservedObject var settings: Settings

    private static let minDrops = [5, 7, 10, 14].map { Pressure(kpa: $0) }

    var body: some View {
        Form {
            Section {
                Text("Experimental. Shows \"Leak?\" under a tyre losing pressure while riding, and notifies about it: its pressure falling from its highest reading of the ride by at least the minimum drop, on two readings in a row, while the tyre isn't cooling down.\n\nRiding warms a tyre up, which only raises its pressure, so a fall is air getting out. A leak too slow to show within a ride is left to the low pressure alert, once the tyre is cold. Letting air out shows as a loss too.")
                    .listRowBackground(Color.clear)
                    .listRowInsets(EdgeInsets())
            }
            Section {
                Toggle("Pressure loss warning", isOn: $settings.pressureLoss)
            }
            Section("Minimum drop, in \(settings.pressureUnit.symbol)") {
                Picker("Minimum drop", selection: $settings.pressureLossMinDrop) {
                    ForEach(Self.minDrops, id: \.self) { Text($0.numberString(settings.pressureUnit)).tag($0) }
                }
                .pickerStyle(.segmented)
                .labelsHidden()
            }
        }
        .navigationTitle("Pressure loss")
        .navigationBarTitleDisplayMode(.inline)
        // The alerts are rebuilt with the new settings
        .onChange(of: settings.pressureLoss) { _, _ in monitor.reload() }
        .onChange(of: settings.pressureLossMinDrop) { _, _ in monitor.reload() }
    }
}
