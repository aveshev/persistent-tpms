import CoreBluetooth
import TPMSCore
import UIKit

/// Listens to the tyre sensors and the ride notification's beacons, in the foreground and in the
/// background.
///
/// In the background iOS only scans for the services it's told, and reports a peripheral once per
/// scan: the scan starts over after each report there, so the next broadcast is reported too. iOS
/// relaunches the app in the background for the scan it was running when the system ended the app,
/// through state restoration, but not after the user swiped it away or after a reboot.
final class Scanner: NSObject, CBCentralManagerDelegate, CBPeripheralDelegate {

    /// Pecham and Bekubee KY (0x27A5), Wicarlink and Sysgration (0xFBB0), Bekubee TPMS (0xA827),
    /// TC.TPMS (0xA828)
    static let services = ["27A5", "FBB0", "A827", "A828"].map { CBUUID(string: $0) }

    /// How long a beacon's report holds off the next one in the background. iOS suspends an app about
    /// 10 s after a report. Just over the 5 s a report needs confirming after, so the second one counts.
    private static let beaconRestartAfter: TimeInterval = 6
    private static let connectionsKey = "beaconConnections"

    var onAdvertisement: ((_ packet: AdvertisingPacket, _ peripheral: UUID, _ rssi: Int) -> Void)?
    var onState: ((CBManagerState) -> Void)?
    /// A beacon was heard, loud enough. [needsConfirming]: a broadcast, which can be stray
    var onBeaconHeard: ((_ beacon: UUID, _ rssi: Int, _ needsConfirming: Bool) -> Void)?
    /// A beacon woken by connecting was probably in range since it was last detected
    var onBeaconStillThere: ((_ beacon: UUID) -> Void)?

    private var central: CBCentralManager?
    private var services = [CBUUID]()
    private var scanned: [CBUUID]?
    private var allowsDuplicates = true
    private var scannedDuplicates = true
    private var beacons = [RideBeacon]()
    /// Only for the beacons woken by a connection. Each connects, says how far it is and is let go of
    /// at once, the next connection left to iOS after a delay: a device held connected stops
    /// advertising, and can't be connected to by its maker's app.
    private var peripherals = [UUID: CBPeripheral]()
    /// What to request once a connection let go of ended, by the signal read before
    private var next = [UUID: (wasNear: Bool, misses: Int)]()
    private var isRestartPending = false

    /// The connections requested, by beacon: iOS may end the app before they complete
    private var connections: [String: BeaconConnection] {
        get { UserDefaults.standard.data(forKey: Self.connectionsKey).flatMap { try? JSONDecoder().decode([String: BeaconConnection].self, from: $0) } ?? [:] }
        set { UserDefaults.standard.set(try? JSONEncoder().encode(newValue), forKey: Self.connectionsKey) }
    }

    /// Starts over with these services. No services still keeps the central, for its beacons and for
    /// state restoration. [isActive]: the app is open. Every packet is only wanted then: iOS does
    /// report them all in the background too, which a beacon advertising all the time keeps the app
    /// awake with, so a background scan reports a device once and is restarted for the next report.
    func start(services: [CBUUID], isActive: Bool) {
        self.services = services
        allowsDuplicates = isActive
        if let central {
            guard central.state == .poweredOn else { return }
            applyServices()
        } else {
            central = CBCentralManager(delegate: self, queue: nil, options: [CBCentralManagerOptionRestoreIdentifierKey: "scanner"])
        }
    }

    func stop() {
        services = []
        scanned = nil
        central?.stopScan()
        setBeacons([])
    }

    func setBeacons(_ beacons: [RideBeacon]) {
        guard beacons != self.beacons else { return }
        self.beacons = beacons
        applyBeacons()
    }

    private func applyServices() {
        guard let central else { return }
        guard services.isEmpty == false else {
            scanned = nil
            central.stopScan()
            return
        }
        guard central.isScanning == false || scanned != services || scannedDuplicates != allowsDuplicates else { return }
        scan()
    }

    private func scan() {
        scanned = services
        scannedDuplicates = allowsDuplicates
        central?.scanForPeripherals(withServices: services, options: [CBCentralManagerScanOptionAllowDuplicatesKey: allowsDuplicates])
    }

    /// Lets go of the beacons no longer wanted, requests the connections of the ones wanted
    private func applyBeacons() {
        guard let central, central.state == .poweredOn else { return }
        let wanted = beacons.filter { $0.wake == .connection }.map(\.id)
        for (id, peripheral) in peripherals where wanted.contains(id) == false {
            central.cancelPeripheralConnection(peripheral)
            peripherals[id] = nil
            next[id] = nil
            connections[id.uuidString] = nil
        }
        central
            .retrievePeripherals(withIdentifiers: wanted.filter { peripherals[$0] == nil })
            .forEach { peripherals[$0.identifier] = $0 }
        for id in wanted {
            guard let peripheral = peripherals[id] else {
                rideLog("beacon \(id) unknown to iOS")
                continue
            }
            peripheral.delegate = self
            switch peripheral.state {
            case .disconnected:
                if let connection = connections[id.uuidString] {
                    // Its request is gone, by the app being swiped away or Bluetooth turning off: what's
                    // left of its delay, still counted from when it was requested
                    let left = connection.dueAt - Date().timeIntervalSince1970
                    rideLog("beacon connection requested again, \(Int(left)) s left of its delay")
                    connect(peripheral, after: left)
                } else {
                    request(peripheral, after: 0, wasNear: false)
                }
            case .connected:
                // Restored by iOS with the app relaunched, before it could read the signal
                peripheral.readRSSI()
            default:
                break
            }
        }
    }

    func centralManager(_ central: CBCentralManager, willRestoreState dict: [String: Any]) {
        rideLog("restored, \(dict.keys.sorted())")
        // The scan goes on from centralManagerDidUpdateState. Its connections go on by themselves, as
        // long as something holds on to them.
        (dict[CBCentralManagerRestoredStatePeripheralsKey] as? [CBPeripheral])?.forEach { peripherals[$0.identifier] = $0 }
    }

    func centralManagerDidUpdateState(_ central: CBCentralManager) {
        onState?(central.state)
        if central.state == .poweredOn {
            applyServices()
            applyBeacons()
        }
    }

    func centralManager(_ central: CBCentralManager, didDiscover peripheral: CBPeripheral, advertisementData: [String: Any], rssi: NSNumber) {
        let name = advertisementData[CBAdvertisementDataLocalNameKey] as? String
        let services = (advertisementData[CBAdvertisementDataServiceUUIDsKey] as? [CBUUID])?.map(\.uuidString) ?? []
        let beacon = beacons.first { $0.wake == .service && $0.isSending(id: peripheral.identifier, name: name, services: services) }
        if let beacon {
            onBeaconHeard?(beacon.id, rssi.intValue, true)
        } else {
            let packet = AdvertisingPacket(
                name: name,
                serviceUUIDs: services,
                manufacturerData: advertisementData[CBAdvertisementDataManufacturerDataKey] as? Data ?? Data()
            )
            onAdvertisement?(packet, peripheral.identifier, rssi.intValue)
        }
        if UIApplication.shared.applicationState != .active, self.services.isEmpty == false {
            if beacon != nil {
                restartLater()
            } else {
                central.stopScan()
                scan()
            }
        }
    }

    /// A beacon advertises all the time, unlike a sensor: restarting the scan at once would wake the
    /// app several times a second for as long as it's nearby, when knowing it's still there is enough.
    /// Within the time iOS leaves the app after a report, or this only runs once something next wakes it.
    private func restartLater() {
        guard isRestartPending == false else { return }
        isRestartPending = true
        DispatchQueue.main.asyncAfter(deadline: .now() + Self.beaconRestartAfter) { [weak self] in
            guard let self else { return }
            isRestartPending = false
            guard let central, central.state == .poweredOn, scanned != nil, UIApplication.shared.applicationState != .active else { return }
            central.stopScan()
            scan()
        }
    }

    // MARK: The beacons' connections

    func centralManager(_ central: CBCentralManager, didConnect peripheral: CBPeripheral) {
        guard peripheral === peripherals[peripheral.identifier] else { return }
        let due = connections[peripheral.identifier.uuidString]?.dueAt
        rideLog("beacon connected, \(due.map { "\(Int(Date().timeIntervalSince1970 - $0)) s after its delay ended" } ?? "nothing requested")")
        // Connecting says it's in range, which can be a long way: its signal says how far
        peripheral.delegate = self
        peripheral.readRSSI()
    }

    func peripheral(_ peripheral: CBPeripheral, didReadRSSI RSSI: NSNumber, error: Error?) {
        let id = peripheral.identifier
        guard peripheral === peripherals[id] else { return }
        let read = BeaconConnection.read(rssi: RSSI.intValue, failed: error != nil)
        let result = connections[id.uuidString]
            .map { $0.completed(with: read, at: Date().timeIntervalSince1970) }
            // Restored connected with nothing stored: as for a first connection
            ?? BeaconConnection(requestedAt: 0, delay: 0, wasNear: false).completed(with: read, at: Date().timeIntervalSince1970)
        rideLog("beacon at \(RSSI) dBm, error \(String(describing: error)): \(result)")
        switch result.outcome {
        case .stillThere: onBeaconStillThere?(id)
        case let .heard(rssi): onBeaconHeard?(id, rssi, false)
        case .nothing: break
        }
        next[id] = (result.wasNear, result.misses)
        central?.cancelPeripheralConnection(peripheral)
    }

    func centralManager(_ central: CBCentralManager, didDisconnectPeripheral peripheral: CBPeripheral, error: Error?) {
        let id = peripheral.identifier
        guard peripheral === peripherals[id] else { return }
        rideLog("beacon disconnected, \(String(describing: error))")
        // Without one the beacon ended it first, before its signal could be read
        let (wasNear, misses) = next.removeValue(forKey: id) ?? (false, 0)
        request(peripheral, after: BeaconConnection.interval, wasNear: wasNear, misses: misses)
    }

    func centralManager(_ central: CBCentralManager, didFailToConnect peripheral: CBPeripheral, error: Error?) {
        guard peripheral === peripherals[peripheral.identifier] else { return }
        rideLog("beacon connection failed, \(String(describing: error))")
        request(peripheral, after: BeaconConnection.interval, wasNear: false)
    }

    private func request(_ peripheral: CBPeripheral, after delay: Double, wasNear: Bool, misses: Int = 0) {
        rideLog("beacon connection requested in \(Int(delay)) s")
        connections[peripheral.identifier.uuidString] = BeaconConnection(requestedAt: Date().timeIntervalSince1970, delay: delay, wasNear: wasNear, misses: misses)
        connect(peripheral, after: delay)
    }

    private func connect(_ peripheral: CBPeripheral, after delay: Double) {
        central?.connect(peripheral, options: delay >= 1 ? [CBConnectPeripheralOptionStartDelayKey: Int(delay)] : nil)
    }
}
