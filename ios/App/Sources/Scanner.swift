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
final class Scanner: NSObject, CBCentralManagerDelegate {

    /// Pecham and Bekubee KY (0x27A5), Wicarlink and Sysgration (0xFBB0), Bekubee TPMS (0xA827),
    /// Bekubee TC.TPMS (0xA828)
    static let services = ["27A5", "FBB0", "A827", "A828"].map { CBUUID(string: $0) }

    /// How long a beacon's report holds off the next one in the background. iOS suspends an app about
    /// 10 s after a report. Just over the 5 s a report needs confirming after, so the second one counts.
    private static let beaconRestartAfter: TimeInterval = 6
    /// With the app open, how soon after it started the scan starts over for a sensor's packet. A
    /// foreground scan left running caught a stand's Wicarlink 14 times in 15 in its first hour, then
    /// about 2 in 3 over the next hours, an S9 beside it catching them all (2026-11-17). One started
    /// over after each packet caught every burst of a ride on 2026-11-05. Capped, so a burst's copies
    /// don't each restart it.
    private static let foregroundRestartAfter: TimeInterval = 10
    /// With the app open and nothing heard, how long before the scan starts over all the same
    private static let foregroundRestartEvery: TimeInterval = 60
    /// Where the connections requested from beacons woken by connecting were kept, see [cancelLeftoverConnections]
    private static let connectionsKey = "beaconConnections"

    var onAdvertisement: ((_ packet: AdvertisingPacket, _ peripheral: UUID, _ rssi: Int) -> Void)?
    var onState: ((CBManagerState) -> Void)?
    /// A beacon was heard, loud enough. [needsConfirming]: a broadcast, which can be stray
    var onBeaconHeard: ((_ beacon: UUID, _ rssi: Int, _ needsConfirming: Bool) -> Void)?

    private var central: CBCentralManager?
    private var services = [CBUUID]()
    private var scanned: [CBUUID]?
    private var allowsDuplicates = true
    private var scannedDuplicates = true
    private var beacons = [RideBeacon]()
    private var isRestartPending = false
    private var scanStartedAt = Date.distantPast
    private var foregroundRestart: Timer?
    /// Connections restored by iOS, cancelled once Bluetooth is on
    private var restoredPeripherals = [CBPeripheral]()

    /// Starts over with these services. No services still keeps the central, for its beacons and for
    /// state restoration. [isActive]: the app is open. Every packet is only wanted then: iOS does
    /// report them all in the background too, which a beacon advertising all the time keeps the app
    /// awake with, so a background scan reports a device once and is restarted for the next report.
    func start(services: [CBUUID], isActive: Bool) {
        self.services = services
        allowsDuplicates = isActive
        if isActive {
            foregroundRestart = foregroundRestart ?? Timer.scheduledTimer(withTimeInterval: Self.foregroundRestartAfter, repeats: true) { [weak self] _ in
                guard let self, Date().timeIntervalSince(scanStartedAt) >= Self.foregroundRestartEvery else { return }
                restartInForeground()
            }
        } else {
            foregroundRestart?.invalidate()
            foregroundRestart = nil
        }
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
        self.beacons = beacons
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
        scanStartedAt = Date()
        scanned = services
        scannedDuplicates = allowsDuplicates
        central?.scanForPeripherals(withServices: services, options: [CBCentralManagerScanOptionAllowDuplicatesKey: allowsDuplicates])
    }

    /// Beacons advertising no service used to be woken by a connection request, which iOS keeps
    /// outstanding until the device shows up, the app relaunched or not: one left from such a build
    /// would go on taking the radio's time from the tyres' scan
    private func cancelLeftoverConnections() {
        guard let central, central.state == .poweredOn else { return }
        // iOS only hands them back when it relaunched the app for them: the stored ones cover the rest
        let stored = UserDefaults.standard.data(forKey: Self.connectionsKey)
            .flatMap { try? JSONSerialization.jsonObject(with: $0) as? [String: Any] }
            .map { $0.keys.compactMap(UUID.init(uuidString:)) } ?? []
        (restoredPeripherals + central.retrievePeripherals(withIdentifiers: stored)).forEach {
            rideLog("leftover beacon connection to \($0.identifier) cancelled")
            central.cancelPeripheralConnection($0)
        }
        restoredPeripherals = []
        UserDefaults.standard.removeObject(forKey: Self.connectionsKey)
    }

    func centralManager(_ central: CBCentralManager, willRestoreState dict: [String: Any]) {
        rideLog("restored, \(dict.keys.sorted())")
        // The scan goes on from centralManagerDidUpdateState
        restoredPeripherals = dict[CBCentralManagerRestoredStatePeripheralsKey] as? [CBPeripheral] ?? []
    }

    func centralManagerDidUpdateState(_ central: CBCentralManager) {
        onState?(central.state)
        if central.state == .poweredOn {
            applyServices()
            cancelLeftoverConnections()
        }
    }

    func centralManager(_ central: CBCentralManager, didDiscover peripheral: CBPeripheral, advertisementData: [String: Any], rssi: NSNumber) {
        let name = advertisementData[CBAdvertisementDataLocalNameKey] as? String
        let services = (advertisementData[CBAdvertisementDataServiceUUIDsKey] as? [CBUUID])?.map(\.uuidString) ?? []
        let beacon = beacons.first { $0.isSending(id: peripheral.identifier, name: name, services: services) }
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
        } else if beacon == nil, Date().timeIntervalSince(scanStartedAt) >= Self.foregroundRestartAfter {
            restartInForeground()
        }
    }

    private func restartInForeground() {
        guard let central, central.state == .poweredOn, scanned != nil, UIApplication.shared.applicationState == .active else { return }
        central.stopScan()
        scan()
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
}
