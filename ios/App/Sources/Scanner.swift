import CoreBluetooth
import TPMSCore
import UIKit

/// Listens to the tyre sensors, in the foreground and in the background.
///
/// In the background iOS only scans for the services it's told, and reports a peripheral once per
/// scan: the scan starts over after each report there, so the next broadcast is reported too. iOS
/// relaunches the app in the background for the scan it was running when the system ended the app,
/// through state restoration, but not after the user swiped it away or after a reboot.
final class Scanner: NSObject, CBCentralManagerDelegate {

    /// Pecham and Bekubee KY (0x27A5), Wicarlink and Sysgration (0xFBB0), Bekubee TPMS (0xA827),
    /// Bekubee TC.TPMS (0xA828)
    static let services = ["27A5", "FBB0", "A827", "A828"].map { CBUUID(string: $0) }

    var onAdvertisement: ((_ packet: AdvertisingPacket, _ peripheral: UUID, _ rssi: Int) -> Void)?
    var onState: ((CBManagerState) -> Void)?

    private var central: CBCentralManager?
    private var shouldScan = false

    func start() {
        shouldScan = true
        if let central {
            if central.state == .poweredOn, central.isScanning == false { scan() }
        } else {
            central = CBCentralManager(delegate: self, queue: nil, options: [CBCentralManagerOptionRestoreIdentifierKey: "scanner"])
        }
    }

    func stop() {
        shouldScan = false
        central?.stopScan()
    }

    private func scan() {
        central?.scanForPeripherals(withServices: Self.services, options: [CBCentralManagerScanOptionAllowDuplicatesKey: true])
    }

    func centralManager(_ central: CBCentralManager, willRestoreState dict: [String: Any]) {
        // The scan goes on from centralManagerDidUpdateState
    }

    func centralManagerDidUpdateState(_ central: CBCentralManager) {
        onState?(central.state)
        if central.state == .poweredOn, shouldScan { scan() }
    }

    func centralManager(_ central: CBCentralManager, didDiscover peripheral: CBPeripheral, advertisementData: [String: Any], rssi: NSNumber) {
        let packet = AdvertisingPacket(
            name: advertisementData[CBAdvertisementDataLocalNameKey] as? String,
            serviceUUIDs: (advertisementData[CBAdvertisementDataServiceUUIDsKey] as? [CBUUID])?.map(\.uuidString) ?? [],
            manufacturerData: advertisementData[CBAdvertisementDataManufacturerDataKey] as? Data ?? Data()
        )
        onAdvertisement?(packet, peripheral.identifier, rssi.intValue)
        if UIApplication.shared.applicationState != .active, shouldScan {
            central.stopScan()
            scan()
        }
    }
}
