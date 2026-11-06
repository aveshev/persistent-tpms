import AppIntents
import Combine
import CoreBluetooth
import SwiftUI
import TPMSCore
import UIKit

/// A trigger can't notify without the permission, which can be taken back from the iOS settings
struct NotificationsDeniedSection: View {
    var body: some View {
        Section {
            Text("Notifications are turned off for this app in the iOS settings.")
            if let url = URL(string: UIApplication.openNotificationSettingsURLString) {
                Link("Open the iOS settings", destination: url)
            }
        }
    }
}

/// The page opened from the "Paired Bluetooth device connected" item. iOS has no API for an app to
/// create an automation or pre-select its device: all this can do is tell how.
struct PairedDeviceView: View {
    @ObservedObject var monitor: Monitor
    @ObservedObject var settings: Settings
    @ObservedObject var ride: RidePrompts
    @State private var isDenied = false

    init(monitor: Monitor, settings: Settings) {
        self.monitor = monitor
        self.settings = settings
        ride = monitor.ride
    }

    var body: some View {
        Form {
            Section {
                Toggle("Notify when connected", isOn: Binding(get: { settings.rideShortcut }, set: { monitor.setRideShortcut($0); refreshDenied() }))
            }
            if isDenied { NotificationsDeniedSection() }
            if settings.rideShortcut {
                Section {
                    Text("""
                    In the Shortcuts app:
                    1. Open the **Automation** tab, then **New Automation**.
                    2. Choose **Bluetooth**, then pick the device.
                    3. Choose **Run Immediately**. Otherwise iOS asks you each time, and nothing runs until you answer.
                    4. Add the **Going for a ride** action.
                    """)
                    ShortcutsLink()
                        .shortcutsLinkStyle(.automaticOutline)
                        .frame(maxWidth: .infinity)
                        .listRowBackground(Color.clear)
                    if settings.debugOptions {
                        LabeledContent("Last triggered", value: ride.lastShortcut?.formatted(date: .abbreviated, time: .shortened) ?? "Not triggered yet")
                    }
                }
            }
        }
        .navigationTitle("Paired Bluetooth device")
        .navigationBarTitleDisplayMode(.inline)
        .onAppear(perform: refreshDenied)
    }

    private func refreshDenied() {
        monitor.isNotificationDenied { isDenied = $0 }
    }
}

extension RideBeacon {
    var displayName: String { label ?? name ?? "Unnamed device" }
    /// With the debug options on, a star tells one woken by connecting from one woken by its service
    func rowName(_ settings: Settings) -> String {
        settings.debugOptions && wake == .connection ? "★ \(displayName)" : displayName
    }
    /// The start of the identifier, to tell devices apart in a list: iOS shows no address
    var shortId: String { String(id.uuidString.prefix(8)) }
}

/// The page opened from the "Other BLE beacon nearby" item, like the Android app's Beacons, new beacons
/// being picked on another page. A removed beacon stays struck through with an undo button until the
/// page is left.
struct BeaconsView: View {
    @ObservedObject var monitor: Monitor
    @ObservedObject var settings: Settings
    @StateObject private var presence = BeaconPresenceScan()
    @State private var removed = Set<UUID>()
    @State private var renaming: RideBeacon?
    @State private var name = ""
    @State private var isAdding = false
    @State private var isDenied = false

    var body: some View {
        Form {
            Section {
                Text("Some newer cars and motorcycles feature always-on or on-while-riding BLE beacons that can be used to send the ride notification. These likely won't show up on your phone's Bluetooth pairing screen, but you can add them here.\n\nNOTE: AirTags and similar personal trackers use proprietary protocols, hide their names and randomize their addresses, so they aren't supported.")
                    .listRowBackground(Color.clear)
                    .listRowInsets(EdgeInsets())
            }
            Section {
                Toggle("Notify when near beacons", isOn: Binding(get: { settings.rideBeacon }, set: { monitor.setRideBeacon($0); refreshDenied() }))
            }
            if isDenied { NotificationsDeniedSection() }
            Section("Beacons") {
                if presence.isBluetoothOff {
                    SettingsRow("Bluetooth is off", "Turn it on to see which beacon is nearby")
                }
                if settings.rideBeaconDevices.isEmpty { Text("No beacon added yet") }
                // The last added first
                ForEach(settings.rideBeaconDevices.reversed()) { beacon in row(beacon) }
            }
            // Separated from the list above, so that it doesn't read as one more beacon
            Section {
                Button {
                    // Removed for good first: the page adding them may add one of them again
                    monitor.removeRideBeacons(removed)
                    removed = []
                    isAdding = true
                } label: {
                    HStack {
                        SettingsRow("Add a beacon", "Lists the Bluetooth devices around")
                        Spacer()
                        Image(systemName: "chevron.forward")
                            .font(.footnote.weight(.semibold))
                            .foregroundStyle(.tertiary)
                    }
                }
            }
        }
        .navigationTitle("Bluetooth beacons")
        .navigationBarTitleDisplayMode(.inline)
        .navigationDestination(isPresented: $isAdding) { BeaconPickerView(monitor: monitor, settings: settings) }
        .alert("Rename beacon", isPresented: Binding(get: { renaming != nil }, set: { if $0 == false { renaming = nil } })) {
            TextField(renaming?.name ?? renaming?.shortId ?? "", text: $name)
            Button("Save") {
                if let renaming { monitor.renameRideBeacon(renaming.id, name) }
            }
            Button("Cancel", role: .cancel) {}
        }
        // Scans only while the page is seen, not while the page adding a beacon is opened above it
        .onAppear {
            presence.start(monitor: monitor, settings: settings)
            refreshDenied()
        }
        .onDisappear {
            presence.stop()
            monitor.removeRideBeacons(removed)
            removed = []
            // Opening the page adding a beacon leaves this one too, while the user is about to add one
            if isAdding == false { monitor.disableRideBeaconIfNone() }
        }
    }

    private func row(_ beacon: RideBeacon) -> some View {
        let isRemoved = removed.contains(beacon.id)
        return HStack {
            VStack(alignment: .leading) {
                Text(beacon.rowName(settings)).strikethrough(isRemoved)
                details(beacon)
                    .font(.subheadline)
                    .strikethrough(isRemoved)
            }
            Spacer()
            if isRemoved {
                Button("Undo") { removed.remove(beacon.id) }.buttonStyle(.borderless)
            } else {
                Button {
                    name = beacon.label ?? beacon.name ?? ""
                    renaming = beacon
                } label: {
                    Image(systemName: "pencil")
                }
                .buttonStyle(.borderless)
                .accessibilityLabel("Rename \(beacon.displayName)")
                Button {
                    removed.insert(beacon.id)
                } label: {
                    Image(systemName: "trash")
                }
                .buttonStyle(.borderless)
                .accessibilityLabel("Remove \(beacon.displayName)")
            }
        }
    }

    /// Nothing about being nearby while Bluetooth is off: unknown then
    private func details(_ beacon: RideBeacon) -> Text {
        let id = Text("ID \(beacon.shortId)").foregroundColor(.secondary)
        guard presence.isBluetoothOff == false else { return id }
        let separator = Text(" · ").foregroundColor(.secondary)
        // Nearby: its signal, in the green of what's going well
        if let state = presence.nearby[beacon.id].map({ "\($0) dBm" }) ?? (presence.wasNearby.contains(beacon.id) ? "Was nearby" : nil) {
            return id + separator + Text(state).foregroundColor(.accentColor)
        }
        return id + separator + Text("Not nearby").foregroundColor(.secondary)
    }

    private func refreshDenied() {
        monitor.isNotificationDenied { isDenied = $0 }
    }
}

/// Tells which of the saved beacons are around, for the page listing them: a scan of its own for
/// everything advertising, at full speed. A beacon woken by connecting is only connected for an
/// instant, which BeaconPresence's window hides.
private final class BeaconPresenceScan: NSObject, ObservableObject, CBCentralManagerDelegate {
    @Published private(set) var nearby = [UUID: Int]()
    /// Still counted as nearby by the background scan, not heard by this page's: maybe from a single
    /// packet this one missed
    @Published private(set) var wasNearby = Set<UUID>()
    @Published private(set) var isBluetoothOff = false

    private var central: CBCentralManager?
    private var settings: Settings?
    private var presence = BeaconPresence()
    private var refresh: AnyCancellable?

    func start(monitor: Monitor, settings: Settings) {
        self.settings = settings
        central = CBCentralManager(delegate: self, queue: nil)
        refresh = Timer.publish(every: 1, on: .main, in: .common).autoconnect().sink { [weak self] now in
            guard let self else { return }
            presence.tick(at: now.timeIntervalSince1970)
            nearby = presence.nearby
            wasNearby = Set(settings.rideBeaconDevices.map(\.id).filter { id in
                monitor.beaconHeard(id).map { now.timeIntervalSince($0) < BeaconPresence.window } ?? false
            })
        }
    }

    func stop() {
        central?.stopScan()
        central = nil
        refresh = nil
    }

    func centralManagerDidUpdateState(_ central: CBCentralManager) {
        isBluetoothOff = central.state == .poweredOff
        if central.state == .poweredOn {
            central.scanForPeripherals(withServices: nil, options: [CBCentralManagerScanOptionAllowDuplicatesKey: true])
        }
    }

    func centralManager(_ central: CBCentralManager, didDiscover peripheral: CBPeripheral, advertisementData: [String: Any], rssi: NSNumber) {
        let name = advertisementData[CBAdvertisementDataLocalNameKey] as? String ?? peripheral.name
        let services = (advertisementData[CBAdvertisementDataServiceUUIDsKey] as? [CBUUID])?.map(\.uuidString) ?? []
        settings?.rideBeaconDevices
            .first { $0.isSending(id: peripheral.identifier, name: name, services: services) }
            .map { presence.heard($0.id, rssi: rssi.intValue, at: Date().timeIntervalSince1970) }
    }
}

/// Lists the devices advertising around, to add as beacons, like the Android app's page adding a
/// beacon. Unlike there a device may be unable to do the job: iOS tells a suspended app about nothing
/// but a service it was told to look for, or a connection it left pending.
private struct BeaconPickerView: View {
    @ObservedObject var monitor: Monitor
    @ObservedObject var settings: Settings
    @StateObject private var scan = BeaconScan()

    var body: some View {
        Form {
            Section {
                Text("Come close to your vehicle: the beacons with the strongest signal are shown at the top of the list.")
                    .listRowBackground(Color.clear)
                    .listRowInsets(EdgeInsets())
            }
            Section("Scanning") {
                if scan.isBluetoothOff {
                    SettingsRow("Bluetooth is off", "Turn it on to look for beacons")
                } else {
                    scanning
                }
            }
            if scan.isBluetoothOff == false, scan.isSearching == false { listed }
        }
        .navigationTitle("Add a beacon")
        .navigationBarTitleDisplayMode(.inline)
        .onAppear(perform: scan.start)
        .onDisappear(perform: scan.stop)
    }

    /// The same layout while searching and once listed, so that nothing jumps in between
    private var scanning: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                VStack(alignment: .leading) {
                    Text(scan.isSearching ? "Listing beacons in \(scan.remaining) s" : "Listening for new beacons…")
                    Text(scan.isSearching ? "\(scan.found) found so far" : scan.notShown == 0 ? "No new beacons" : scan.notShown == 1 ? "1 additional beacon found" : "\(scan.notShown) additional beacons found")
                        .font(.subheadline)
                        .foregroundStyle(scan.isSearching ? AnyShapeStyle(.secondary) : scan.notShown > 0 ? AnyShapeStyle(.tint) : AnyShapeStyle(.tertiary))
                }
                Spacer()
                if scan.notShown > 0 {
                    Button("Reload", action: scan.sort).buttonStyle(.borderless)
                }
            }
            if scan.isSearching {
                ProgressView(value: scan.progress).animation(.linear(duration: 1), value: scan.progress)
            } else {
                // Listens as long as the page is seen: never done
                ListeningBar()
            }
        }
    }

    private var listed: some View {
        Section {
            if scan.devices.isEmpty { Text("No beacon nearby") }
            ForEach(scan.devices) { device in
                let isAdded = settings.rideBeaconDevices.contains { $0.id == device.id }
                HStack {
                    SettingsRow(device.beacon.rowName(settings), device.details(isAdded: isAdded))
                        // Too weak or gone for now, may come back
                        .opacity(device.isCandidate ? 1 : 0.5)
                    Spacer()
                    if isAdded {
                        Button {
                            monitor.removeRideBeacons([device.id])
                        } label: {
                            Image(systemName: "xmark").foregroundStyle(Theme.error)
                        }
                        .buttonStyle(.borderless)
                        .accessibilityLabel("Remove \(device.beacon.displayName)")
                    } else {
                        Button {
                            monitor.addRideBeacon(device.beacon)
                        } label: {
                            Image(systemName: "plus")
                        }
                        .buttonStyle(.borderless)
                        .disabled(device.isCandidate == false)
                        .accessibilityLabel("Add \(device.beacon.displayName)")
                    }
                }
            }
        } header: {
            HStack {
                Text("Beacons found")
                Spacer()
                // Sorts again by the current signal, the new beacons included. Greyed out while already sorted.
                Button("\u{21C5}", action: scan.sort)
                    .font(.title3)
                    .disabled(scan.isSorted)
                    .accessibilityLabel("Sort by signal")
            }
        } footer: {
            Text("The ID is the one this iPhone gives the device. Unnamed devices that couldn't be recognised again aren't listed.")
        }
    }
}

/// A bar that never fills, which `ProgressView` doesn't animate on its own on iOS
private struct ListeningBar: View {
    @State private var isAtEnd = false

    var body: some View {
        GeometryReader { geometry in
            Capsule()
                .fill(.quaternary)
                .overlay(alignment: .leading) {
                    Capsule()
                        .fill(.tint)
                        .frame(width: geometry.size.width * 0.3)
                        .offset(x: isAtEnd ? geometry.size.width * 0.7 : 0)
                }
        }
        .frame(height: 4)
        .onAppear {
            withAnimation(.easeInOut(duration: 1).repeatForever()) { isAtEnd = true }
        }
    }
}

/// A foreground scan for everything advertising, on a central of its own: nothing to restore, and
/// the sensors' scan goes on beside it. Scans for as long as the page is seen.
private final class BeaconScan: NSObject, ObservableObject, CBCentralManagerDelegate {
    struct Device: Identifiable {
        var beacon: RideBeacon
        /// The median of its last packets: a single lucky packet doesn't count much
        var rssi: Int
        /// Loud enough to count as nearby
        var isStrong: Bool
        /// Not heard lately, gone or out of range
        var isQuiet: Bool
        /// The typical time between two of its packets as far as iOS reports them, nil until a few came
        var period: Double?
        var id: UUID { beacon.id }
        var isCandidate: Bool { isStrong && isQuiet == false }

        func details(isAdded: Bool) -> String {
            // By what each rounds to, so that 0.099 is "0.1 s" rather than "0.10 s"
            let seconds = period.map { "every \(String(format: $0 >= 0.095 ? "%.1f" : $0 >= 0.0095 ? "%.2f" : "%.3f", $0)) s" }
            let state = isQuiet ? "Not heard lately" : isStrong ? nil : "Weak signal"
            return ["ID \(beacon.shortId)", isAdded ? "Added" : nil].compactMap { $0 }.joined(separator: " · ")
                + "\n" + ["\(rssi) dBm", seconds, state].compactMap { $0 }.joined(separator: " · ")
        }
    }

    private struct Heard {
        var name: String?
        var services = [String]()
        var isConnectable = false
        var isSensor = false
        var rssis = [Int]()
        var timestamps = [Double]()
        var lastHeard = Date()
        var isStrong = false
        var median: Int { rssis.sorted()[rssis.count / 2] }
    }

    @Published private(set) var devices = [Device]()
    @Published private(set) var isSearching = true
    @Published private(set) var isBluetoothOff = false
    /// While searching: the seconds left, and how much of it is done
    @Published private(set) var remaining = Int(BeaconScan.search)
    @Published private(set) var progress = 0.0
    /// While searching: the candidates so far
    @Published private(set) var found = 0
    /// The devices which became candidates since the list was sorted
    @Published private(set) var notShown = 0
    @Published private(set) var isSorted = true

    private static let search: TimeInterval = 3
    private static let quietAfter: TimeInterval = 30
    private static let maxPackets = 11
    /// Enough for a few bursts of a few packets each
    private static let maxTimestamps = 50

    private var central: CBCentralManager?
    private var heard = [UUID: Heard]()
    /// The order they were found in, which sorting keeps between equal signals
    private var seen = [UUID]()
    private var list = BeaconList()
    private var refresh: AnyCancellable?

    func start() {
        central = CBCentralManager(delegate: self, queue: nil)
        let startedAt = Date()
        refresh = Timer.publish(every: 1, on: .main, in: .common).autoconnect().sink { [weak self] now in
            guard let self else { return }
            let elapsed = now.timeIntervalSince(startedAt)
            if isSearching {
                remaining = Int((Self.search - elapsed).rounded(.up))
                progress = min(elapsed / Self.search, 1)
            }
            publish()
            if isSearching, elapsed >= Self.search {
                isSearching = false
                sort()
            }
        }
    }

    func stop() {
        central?.stopScan()
        central = nil
        refresh = nil
    }

    /// Sorts again by the current signal, the new beacons included
    func sort() {
        list.sort()
        publish()
    }

    private func publish() {
        let now = Date()
        let all = seen.compactMap { id in
            heard[id].flatMap { heard -> Device? in
                let beacon = RideBeacon(id: id, name: heard.name, services: heard.services, isConnectable: heard.isConnectable)
                // A tyre sensor is no beacon, and neither is what couldn't be recognised later, or what
                // iOS tells an app in the background nothing about (iBeacons and AirTags among them)
                guard heard.isSensor == false, beacon.isRecognisable, beacon.wake != nil else { return nil }
                return Device(
                    beacon: beacon,
                    rssi: heard.median,
                    isStrong: heard.isStrong,
                    isQuiet: now.timeIntervalSince(heard.lastHeard) >= Self.quietAfter,
                    period: RideBeacon.broadcastPeriod(heard.timestamps)
                )
            }
        }
        list.update(all.map { BeaconList.Device(id: $0.id, rssi: $0.rssi, isCandidate: $0.isCandidate) })
        devices = list.order.compactMap { id in all.first { $0.id == id } }
        found = list.qualified.count
        notShown = list.notShown.count
        isSorted = list.isSorted
    }

    func centralManagerDidUpdateState(_ central: CBCentralManager) {
        isBluetoothOff = central.state == .poweredOff
        if central.state == .poweredOn {
            central.scanForPeripherals(withServices: nil, options: [CBCentralManagerScanOptionAllowDuplicatesKey: true])
        }
    }

    func centralManager(_ central: CBCentralManager, didDiscover peripheral: CBPeripheral, advertisementData: [String: Any], rssi: NSNumber) {
        // Without a signal, it would be listed as the loudest device around
        guard rssi.intValue != RideTriggers.noRssi else { return }
        if heard[peripheral.identifier] == nil { seen.append(peripheral.identifier) }
        var device = heard[peripheral.identifier] ?? Heard()
        // Some devices only send their name and services in some packets (the scan response)
        device.name = advertisementData[CBAdvertisementDataLocalNameKey] as? String ?? device.name ?? peripheral.name
        let services = (advertisementData[CBAdvertisementDataServiceUUIDsKey] as? [CBUUID])?.map(\.uuidString) ?? []
        device.services += services.filter { device.services.contains($0) == false }
        device.isSensor = device.services.contains { service in Scanner.services.contains { $0.uuidString == service } }
        device.isConnectable = device.isConnectable || advertisementData[CBAdvertisementDataIsConnectable] as? Bool == true
        device.rssis = (device.rssis + [rssi.intValue]).suffix(Self.maxPackets)
        device.lastHeard = Date()
        device.timestamps = (device.timestamps + [device.lastHeard.timeIntervalSince1970]).suffix(Self.maxTimestamps)
        device.isStrong = BeaconList.isStrong(rssi: device.median, wasStrong: device.isStrong)
        heard[peripheral.identifier] = device
    }
}
