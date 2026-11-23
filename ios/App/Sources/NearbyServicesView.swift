#if DEBUG
import CoreBluetooth
import SwiftUI
import TPMSCore

/// Which Bluetooth services the devices around advertise, to pick the ones whose broadcasts would
/// wake the app in the background often enough to notice the phone was unlocked, see docs/alerts.md
/// ("Reminding on unlocking"). iOS only scans for every device in the foreground, so this only
/// listens while it's open, or while the app is open at all once "Keep listening" is on: the main
/// screen stays on, dimmed, and turns off in a pocket without the app leaving the foreground. A
/// session is a stay on this page, at most 5 minutes, and a service's worth is how many sessions,
/// places, it was heard in: kept across launches until reset.
struct NearbyServicesView: View {
    @ObservedObject private var log = NearbyServicesLog.shared

    var body: some View {
        Form {
            Section {
                SettingsRow(
                    "Listening for \(Self.duration(log.current.duration))",
                    "\(log.current.devices) devices heard, \(log.current.withoutService) without a service. \(log.sessions.count) earlier sessions."
                )
            } footer: {
                Text("Only while the app is open and the phone unlocked: iOS only lets an app hear every device in the foreground. Open it for a minute in each place: home, work, a car park, a street, a petrol station. Devices without a service, such as Apple's, can't wake the app in the background.")
            }
            Section {
                Toggle(isOn: $log.isKept) {
                    SettingsRow("Keep listening", "Also on the main screen, which stays on: put the phone in a pocket with the app open, a session every 5 minutes")
                }
            }
            Section("Services") {
                ForEach(log.rows, id: \.uuid) { row in
                    VStack(alignment: .leading, spacing: 2) {
                        HStack {
                            Text(row.uuid).font(.body.monospaced())
                            Spacer()
                            Text("\(row.sessions) of \(log.sessions.count + 1)").foregroundStyle(.secondary)
                        }
                        Text([row.name, "\(row.devices) devices now", row.isOverflow ? "overflow only" : nil, row.isSensor ? "sensor" : nil].compactMap { $0 }.joined(separator: ", "))
                            .font(.footnote)
                            .foregroundStyle(.secondary)
                    }
                }
            }
            Section {
                ShareLink(item: log.export()) { Text("Export") }
                Button("Reset", role: .destructive) { log.reset() }
            } footer: {
                Text("Export gives every session as JSON. \"Overflow only\": an iPhone app's service, which iOS hides in a part of the broadcast a background scan can't match.")
            }
        }
        .navigationTitle("Nearby services")
        .navigationBarTitleDisplayMode(.inline)
        .onAppear { log.isOpen = true }
        .onDisappear { log.isOpen = false }
    }

    private static func duration(_ seconds: TimeInterval) -> String {
        seconds < 60 ? "\(Int(seconds)) s" : "\(Int(seconds / 60)) min"
    }
}

/// One stay on the page: the devices heard by service. Devices are iOS's identifiers, which change
/// when a device changes its address, every 15 minutes or so for phones: counts are rough.
struct NearbyServicesSession: Codable {
    var start = Date()
    var end = Date()
    /// Service UUID to the devices advertising it
    var services = [String: Set<UUID>]()
    /// Service UUID to the devices advertising it only in the overflow area
    var overflow = [String: Set<UUID>]()
    var all = Set<UUID>()
    var withoutServices = Set<UUID>()

    var duration: TimeInterval { end.timeIntervalSince(start) }
    var devices: Int { all.count }
    var withoutService: Int { withoutServices.count }
}

final class NearbyServicesLog: NSObject, ObservableObject, CBCentralManagerDelegate {
    static let shared = NearbyServicesLog()

    struct Row {
        let uuid: String
        let name: String?
        let sessions: Int
        let devices: Int
        let isOverflow: Bool
        let isSensor: Bool
    }

    private static let key = "nearbyServiceSessions"
    private static let keptKey = "nearbyServicesKept"
    /// Listening on, in a pocket, a session per place walked through rather than one for the walk
    private static let sessionLength: TimeInterval = 5 * 60

    @Published private(set) var sessions: [NearbyServicesSession] = {
        UserDefaults.standard.data(forKey: key).flatMap { try? JSONDecoder().decode([NearbyServicesSession].self, from: $0) } ?? []
    }()
    @Published private(set) var current = NearbyServicesSession()

    /// Listens whenever the app is open, not only on its page
    @Published var isKept = UserDefaults.standard.bool(forKey: keptKey) {
        didSet {
            UserDefaults.standard.set(isKept, forKey: Self.keptKey)
            update()
        }
    }
    /// Its page is shown
    var isOpen = false { didSet { update() } }

    private var central: CBCentralManager?
    private var timer: Timer?

    private override init() {
        super.init()
        // The scan stops in the background: a session per stay in the foreground
        [UIApplication.didBecomeActiveNotification, UIApplication.willResignActiveNotification].forEach {
            NotificationCenter.default.addObserver(forName: $0, object: nil, queue: .main) { [weak self] _ in self?.update() }
        }
        update()
    }

    private func update() {
        if (isKept || isOpen) && UIApplication.shared.applicationState == .active { start() } else { stop() }
    }

    private func start() {
        guard central == nil else { return }
        current = NearbyServicesSession()
        // No restore identifier: this scan isn't meant to go on in the background
        central = CBCentralManager(delegate: self, queue: nil)
        // The duration shown follows
        timer = Timer.scheduledTimer(withTimeInterval: 1, repeats: true) { [weak self] _ in self?.tick() }
    }

    private func tick() {
        current.end = Date()
        guard current.duration >= Self.sessionLength else { return }
        if current.devices > 0 {
            sessions.append(current)
            save()
        }
        current = NearbyServicesSession()
    }

    private func stop() {
        guard let central else { return }
        central.stopScan()
        self.central = nil
        timer?.invalidate()
        timer = nil
        current.end = Date()
        if current.devices > 0 {
            sessions.append(current)
            save()
        }
        current = NearbyServicesSession()
    }

    func reset() {
        sessions = []
        save()
        current = NearbyServicesSession(start: current.start)
    }

    /// Every session, the current one last, as a JSON file to share
    func export() -> URL {
        let url = FileManager.default.temporaryDirectory.appendingPathComponent("nearby-services.json")
        let encoder = JSONEncoder()
        encoder.outputFormatting = [.prettyPrinted, .sortedKeys]
        encoder.dateEncodingStrategy = .iso8601
        try? encoder.encode(sessions + [current]).write(to: url)
        return url
    }

    /// The services heard, most sessions first, then most devices now
    var rows: [Row] {
        // Typed step by step: as one expression, it takes the CI's compiler too long to type-check
        let everySession: [NearbyServicesSession] = sessions + [current]
        let sensors = Set<String>(Scanner.services.map(\.uuidString))
        let uuids = Set<String>(everySession.flatMap { (session: NearbyServicesSession) -> [String] in
            Array(session.services.keys) + Array(session.overflow.keys)
        })
        let rows: [Row] = uuids.map { (uuid: String) -> Row in
            let heardIn: Int = everySession.filter { $0.services[uuid] != nil || $0.overflow[uuid] != nil }.count
            let devicesNow: Int = (current.services[uuid]?.count ?? 0) + (current.overflow[uuid]?.count ?? 0)
            return Row(
                uuid: uuid,
                name: Self.names[uuid],
                sessions: heardIn,
                devices: devicesNow,
                isOverflow: everySession.allSatisfy { $0.services[uuid] == nil },
                isSensor: sensors.contains(uuid)
            )
        }
        return rows.sorted { (a: Row, b: Row) -> Bool in
            if a.sessions != b.sessions { return a.sessions > b.sessions }
            if a.devices != b.devices { return a.devices > b.devices }
            return a.uuid < b.uuid
        }
    }

    private func save() {
        UserDefaults.standard.set(try? JSONEncoder().encode(sessions), forKey: Self.key)
    }

    func centralManagerDidUpdateState(_ central: CBCentralManager) {
        // Every device, each reported once: only which services it has matters here
        if central.state == .poweredOn { central.scanForPeripherals(withServices: nil) }
    }

    func centralManager(_ central: CBCentralManager, didDiscover peripheral: CBPeripheral, advertisementData: [String: Any], rssi: NSNumber) {
        let id = peripheral.identifier
        let services = (advertisementData[CBAdvertisementDataServiceUUIDsKey] as? [CBUUID] ?? [])
            + (advertisementData[CBAdvertisementDataSolicitedServiceUUIDsKey] as? [CBUUID] ?? [])
            + (advertisementData[CBAdvertisementDataServiceDataKey] as? [CBUUID: Data] ?? [:]).keys
        let overflow = advertisementData[CBAdvertisementDataOverflowServiceUUIDsKey] as? [CBUUID] ?? []
        current.all.insert(id)
        if services.isEmpty, overflow.isEmpty { current.withoutServices.insert(id) }
        services.forEach { current.services[$0.uuidString, default: []].insert(id) }
        overflow.forEach { current.overflow[$0.uuidString, default: []].insert(id) }
        current.end = Date()
    }

    /// The services likely heard, by their assigned 16-bit UUID. Others are looked up afterwards.
    private static let names: [String: String] = [
        "FE2C": "Google Fast Pair",
        "FEAA": "Google Eddystone, Find Hub",
        "FEF3": "Google",
        "FE9F": "Google",
        "FD5A": "Samsung SmartTag",
        "FD59": "Samsung SmartTag",
        "FEED": "Tile",
        "FEEC": "Tile",
        "FE95": "Xiaomi",
        "FCD2": "BTHome",
        "FD6F": "Exposure Notification",
        "180F": "Battery",
        "180A": "Device Information",
        "180D": "Heart Rate",
        "1812": "HID",
        "27A5": "Pecham, Bekubee KY",
        "FBB0": "Wicarlink, Sysgration",
        "A827": "Bekubee TPMS",
        "A828": "Bekubee TC.TPMS",
    ]
}
#endif
