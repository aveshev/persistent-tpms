import Combine
import CoreBluetooth
import Foundation
import TPMSCore
import UIKit

/// The app's state: the vehicles, their sensors and tyres, the sensors around, and what the scan
/// receives, stored and alerted on. Everything runs on the main thread.
final class Monitor: ObservableObject {
    static let shared = Monitor()

    struct Tyre {
        var latest: SensorReading?
        var alerts = TyreAlerts()
        var loss = PressureLossTracker()
        /// The latest reading's pressure, before its sensor's calibration
        var latestRead: Pressure?
        /// The latest reading was corrected by its sensor's calibration
        var isCalibrated = false

        /// The worst level the latest reading shows, nil while every class is fine
        var worst: AlertLevel? { alerts.levels.values.max() }
    }

    /// A sensor heard lately, bound or not, for the assignment screen
    struct Nearby: Identifiable {
        var reading: SensorReading
        var lastSeen: Date
        var id: Int { reading.sensorId }
    }

    @Published private(set) var vehicles: [Vehicle] = []
    @Published private(set) var sensors: [BoundSensor] = []
    @Published private(set) var tyres: [TyreKey: Tyre] = [:]
    /// What the vehicle menu blinks for
    @Published private(set) var ongoing = OngoingAlerts()
    @Published private(set) var nearby: [Int: Nearby] = [:]
    @Published private(set) var bluetooth: CBManagerState = .unknown
    @Published private(set) var error: String?

    struct TyreKey: Hashable {
        let vehicleId: UUID
        let location: TyreLocation
    }

    /// Every sensor heard, bound or not, for assigning one by Bluetooth
    let heard = PassthroughSubject<SensorReading, Never>()

    let settings = Settings()
    let speaker = AlertSpeaker()
    private let scanner = Scanner()
    private let notifier = Notifier()
    private var database: Database?
    private var burst = BurstFilter()

    /// How long a sensor heard stays offered for assignment
    static let nearbyFor: TimeInterval = 10 * 60

    var current: Vehicle? { vehicles.first(where: \.isCurrent) ?? vehicles.first }

    private init() {}

    /// A red or critical tyre alert going on, of a vehicle. Android's stops with a reading lowering it
    /// or with it dismissed. Nothing dismisses one here and every tyre shows its latest reading,
    /// however old, so a sensor not heard lately doesn't count: one left flat, or away from the phone,
    /// would keep the screen bright for good.
    func isAlerting(_ vehicleId: UUID? = nil, now: Date = Date()) -> Bool {
        tyres.contains { key, tyre in
            (vehicleId == nil || key.vehicleId == vehicleId)
                && tyre.latest.map { now.timeIntervalSince1970 - $0.timestamp < OngoingAlerts.heldFor } == true
                && tyre.alerts.levels.contains { $0.key != .battery && $0.value >= .red }
        }
    }

    /// Opens the database and starts the scan. Called at launch, also when iOS relaunches the app
    /// in the background for a sensor it was scanning for.
    func start() {
        guard database == nil else { return }
        speaker.configure(settings: settings)
        if Demo.isOn {
            do {
                let database = try Database()
                let around = try Demo.seed(database)
                self.database = database
                reload()
                #if DEBUG
                ongoing = Demo.ongoingAlerts(vehicles)
                #endif
                nearby = Dictionary(uniqueKeysWithValues: around.map { ($0.sensorId, Nearby(reading: $0, lastSeen: Date(timeIntervalSince1970: $0.timestamp))) })
                bluetooth = .poweredOn
            } catch {
                self.error = "\(error)"
            }
            return
        }
        do {
            #if DEBUG
            // A database copied to Documents/import.db, by `xcrun devicectl device copy to`, is
            // imported at launch: a phone's data can be loaded without going through the screens
            let staged = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0].appendingPathComponent("import.db")
            if FileManager.default.fileExists(atPath: staged.path) {
                defer { try? FileManager.default.removeItem(at: staged) }
                try importDatabase(from: staged)
            }
            #endif
            if database == nil { try open() }
        } catch {
            self.error = "\(error)"
        }
        scanner.onState = { [weak self] in self?.bluetooth = $0 }
        scanner.onAdvertisement = { [weak self] in self?.received($0, from: $1, rssi: $2) }
        scanner.start()
    }

    /// The app went to the background or came back
    func appIsActive(_ isActive: Bool) {
        if Demo.isOn { return }
        if isActive || (settings.backgroundMonitoring && sensors.isEmpty == false) {
            scanner.start()
        } else {
            scanner.stop()
            // No reading could clear their alerts any more
            speaker.stopAll()
            ongoing = OngoingAlerts()
        }
    }

    /// Between made-up vehicles and the real ones. The Android app restarts for this, an iOS app can't.
    func setDemoMode(_ enabled: Bool) {
        settings.demoMode = enabled
        scanner.stop()
        speaker.stopAll()
        ongoing = OngoingAlerts()
        notifier.clearAll()
        database = nil
        nearby = [:]
        start()
    }

    private static func databaseURL() throws -> URL {
        try FileManager.default.url(for: .applicationSupportDirectory, in: .userDomainMask, appropriateFor: nil, create: true)
            .appendingPathComponent("car.db")
    }

    private func open() throws {
        let url = try Self.databaseURL()
        let database = try Database(path: url.path)
        // Written in the background too, while the phone is locked
        for suffix in ["", "-wal", "-shm"] {
            try? FileManager.default.setAttributes([.protectionKey: FileProtectionType.none], ofItemAtPath: url.path + suffix)
        }
        if try database.vehicles().isEmpty {
            var vehicle = Vehicle.new(name: "My car", kind: .car)
            vehicle.isCurrent = true
            try database.save(vehicle)
        }
        self.database = database
        reload()
    }

    /// Also after the pressure loss settings change, which the alerts are rebuilt with
    func reload() {
        guard let database else { return }
        do {
            vehicles = try database.vehicles()
            sensors = try database.sensors()
            // The alerts and leak tracking start over from the readings of the last two hours
            let since = Date().timeIntervalSince1970 - 2 * 3600
            var tyres = [TyreKey: Tyre]()
            for vehicle in vehicles {
                for location in vehicle.kind.locations {
                    let key = TyreKey(vehicleId: vehicle.id, location: location)
                    // Only the bound sensor's: a tyre without one shows nothing, and a sensor just
                    // bound doesn't show what the one before it read
                    let boundId = sensors.first { $0.vehicleId == vehicle.id && $0.location == location }?.id
                    let latest = try database.latestReading(vehicleId: vehicle.id, location: location).flatMap { $0.sensorId == boundId ? $0 : nil }
                    var tyre = Tyre(latest: latest.map(corrected), latestRead: latest?.pressure, isCalibrated: latest.map(isCalibrated) ?? false)
                    tyre.loss = PressureLossTracker(minDrop: settings.pressureLossMinDrop)
                    for reading in try database.readings(vehicleId: vehicle.id, location: location, after: since).map(corrected) where reading.sensorId == boundId {
                        tyre.loss = tyre.loss.next(reading)
                        tyre.alerts = tyre.alerts.next(reading, vehicle.thresholds(location), warnedLoss(tyre))
                    }
                    if let latest = tyre.latest, tyre.alerts.latest == nil {
                        tyre.alerts = tyre.alerts.next(latest, vehicle.thresholds(location), nil)
                    }
                    tyres[key] = tyre
                }
            }
            self.tyres = tyres
            ongoing = ongoing.reevaluated { tyres[TyreKey(vehicleId: $0.vehicleId, location: $0.location)]?.alerts.notifiable }
            error = nil
        } catch {
            self.error = "\(error)"
        }
    }

    private func received(_ packet: AdvertisingPacket, from peripheral: UUID, rssi: Int) {
        let now = Date()
        guard burst.isNew(peripheral: peripheral.uuidString, advertisement: packet.bytes, at: now.timeIntervalSince1970),
              // Pecham and Bekubee KY carry no ID: this iPhone's identifier for them stands for one
              let reading = packet.decode(timestamp: now.timeIntervalSince1970, rssi: rssi, addressId: Int(peripheral.uuidString.javaHashCode))
        else { return }
        nearby[reading.sensorId] = Nearby(reading: reading, lastSeen: now)
        nearby = nearby.filter { now.timeIntervalSince($0.value.lastSeen) < Self.nearbyFor }
        heard.send(reading)
        // An unassigned sensor's readings are never shown, stored or alerted on
        guard let sensor = sensors.first(where: { $0.id == reading.sensorId && $0.brand == reading.brand }),
              let vehicle = vehicles.first(where: { $0.id == sensor.vehicleId })
        else { return }
        record(reading, vehicle: vehicle, location: sensor.location)
    }

    /// The reading corrected by the sensor which sent it, so a moved sensor's past readings keep its
    /// calibration, like the Android app's PressureCalibrations
    private func corrected(_ reading: SensorReading) -> SensorReading {
        guard let calibration = sensors.first(where: { $0.id == reading.sensorId })?.calibration.applied else { return reading }
        var corrected = reading
        corrected.pressure = calibration.applyTo(reading.pressure)
        return corrected
    }

    private func isCalibrated(_ reading: SensorReading) -> Bool {
        sensors.first { $0.id == reading.sensorId }?.calibration.applied != nil
    }

    private func record(_ read: SensorReading, vehicle: Vehicle, location: TyreLocation, isStored: Bool = true) {
        // As it was read: the calibration applies when it's read back, it can still change
        if isStored { try? database?.insert(read, vehicleId: vehicle.id, location: location) }
        let reading = corrected(read)
        let key = TyreKey(vehicleId: vehicle.id, location: location)
        var tyre = tyres[key] ?? Tyre(loss: PressureLossTracker(minDrop: settings.pressureLossMinDrop))
        tyre.latest = reading
        tyre.latestRead = read.pressure
        tyre.isCalibrated = isCalibrated(read)
        tyre.loss = tyre.loss.next(reading)
        tyre.alerts = tyre.alerts.next(reading, vehicle.thresholds(location), warnedLoss(tyre))
        tyres[key] = tyre
        ongoing = ongoing.next(OngoingAlerts.Tyre(vehicleId: vehicle.id, location: location), levels: tyre.alerts.notifiable, at: reading.timestamp)
        speaker.update(tag: "\(vehicle.id)-\(location.code)", levels: tyre.alerts.notifiable)
        if settings.alerts {
            notifier.update(vehicle: vehicle, location: location, alerts: tyre.alerts, loss: warnedLoss(tyre), settings: settings)
        }
    }

    /// The pressure loss, only once its warning is turned on in the settings
    private func warnedLoss(_ tyre: Tyre) -> PressureLoss? {
        settings.pressureLoss ? tyre.loss.loss : nil
    }

    // MARK: Editing

    /// A kind without some of the tyres the vehicle had loses their sensors, their readings stay
    func save(_ vehicle: Vehicle) {
        perform { database in
            try database.save(vehicle)
            for sensor in try database.sensors() where sensor.vehicleId == vehicle.id && vehicle.kind.locations.contains(sensor.location) == false {
                try database.unbind(vehicleId: vehicle.id, location: sensor.location)
            }
        }
    }

    /// Saves a change to the vehicle right away, like the Android app's settings pages
    func update(_ vehicleId: UUID, _ change: (inout Vehicle) -> Void) {
        guard var vehicle = vehicles.first(where: { $0.id == vehicleId }) else { return }
        change(&vehicle)
        save(vehicle)
    }

    func add(name: String, kind: VehicleKind) {
        var vehicle = Vehicle.new(name: name, kind: kind)
        vehicle.isCurrent = true
        save(vehicle)
    }

    func setCurrent(_ vehicle: Vehicle) {
        perform { try $0.setCurrent(vehicle.id) }
    }

    func delete(_ vehicle: Vehicle) {
        perform { try $0.delete(vehicle.id) }
        vehicle.kind.locations.forEach { clearAlerts(vehicle, $0) }
    }

    /// The sensor's calibration, its readings past and future following it
    func setCalibration(_ calibration: SensorCalibration, sensorIds: [Int]) {
        perform { database in
            for id in sensorIds {
                try database.setCalibration(sensorId: id, isCalibrated: calibration.isEnabled, offsetKpa: calibration.calibration.offset.kpa, multiplier: calibration.calibration.multiplier)
            }
        }
    }

    /// A copy of the database to share, like the Android app's debug settings
    func exportDatabase() throws -> URL {
        let url = FileManager.default.temporaryDirectory.appendingPathComponent("car.db")
        try? FileManager.default.removeItem(at: url)
        try database?.export(to: url.path)
        return url
    }

    private func clearAlerts(_ vehicle: Vehicle, _ location: TyreLocation) {
        notifier.clear(vehicle: vehicle, location: location)
        speaker.stop(tag: "\(vehicle.id)-\(location.code)")
    }

    /// Binds the sensor [reading] came from to the tyre, the reading showing there at once
    func assign(_ reading: SensorReading, to vehicle: Vehicle, at location: TyreLocation) {
        perform { try $0.bind(BoundSensor(id: reading.sensorId, brand: reading.brand, vehicleId: vehicle.id, location: location)) }
        record(reading, vehicle: vehicle, location: location)
        appIsActive(UIApplication.shared.applicationState == .active)
    }

    func unassign(vehicle: Vehicle, location: TyreLocation) {
        perform { try $0.unbind(vehicleId: vehicle.id, location: location) }
        clearAlerts(vehicle, location)
    }

    /// Binds a code's [sensors], the shared ones among them with their [calibrations]
    func assign(_ sensors: [BoundSensor], calibrations: [SharedSensor] = []) {
        perform { database in
            for sensor in sensors { try database.bind(sensor) }
            for shared in calibrations {
                try database.setCalibration(sensorId: shared.id, isCalibrated: shared.isCalibrated, offsetKpa: shared.offsetKpa, multiplier: shared.multiplier)
            }
        }
        appIsActive(UIApplication.shared.applicationState == .active)
    }

    func unassignAll(_ vehicle: Vehicle) {
        perform { try $0.unbindAll(vehicleId: vehicle.id) }
        vehicle.kind.locations.forEach { notifier.clear(vehicle: vehicle, location: $0) }
    }

    /// Moves sensors around the vehicle, with their readings, see MoveChain
    func move(_ vehicle: Vehicle, _ moves: [MoveChain.Move]) {
        perform { try $0.move(vehicleId: vehicle.id, moves: moves) }
        moves.flatMap { [$0.from, $0.to] }.forEach { notifier.clear(vehicle: vehicle, location: $0) }
    }

    /// Moves the sensor at [location] to [target] of [toVehicle]. The sensor already there comes to
    /// [location] when [swap], it's unbound otherwise.
    func move(from vehicle: Vehicle, _ location: TyreLocation, to toVehicle: Vehicle, _ target: TyreLocation, swap: Bool) {
        perform { try $0.moveToVehicle(vehicleId: vehicle.id, location: location, toVehicleId: toVehicle.id, toLocation: target, swap: swap) }
        clearAlerts(vehicle, location)
        clearAlerts(toVehicle, target)
    }

    /// An unbound sensor advertising this location (Sysgration) heard lately, ready to be bound
    /// there, like LocatedTyreScannerUseCase.detectedSensor. Nil once a sensor is bound there.
    func detected(vehicle: Vehicle, location: TyreLocation, now: Date = Date()) -> SensorReading? {
        guard sensor(vehicle: vehicle, location: location) == nil else { return nil }
        return nearby.values
            .filter { nearby in
                now.timeIntervalSince(nearby.lastSeen) < Self.nearbyFor
                    && nearby.reading.location.map(location.accepts) == true
                    && sensors.contains { $0.id == nearby.reading.sensorId } == false
            }
            .max { $0.lastSeen < $1.lastSeen }?
            .reading
    }

    func sensor(vehicle: Vehicle, location: TyreLocation) -> BoundSensor? {
        sensors.first { $0.vehicleId == vehicle.id && $0.location == location }
    }

    func readings(vehicle: Vehicle, location: TyreLocation, limit: Int = 200) -> [SensorReading] {
        ((try? database?.readings(vehicleId: vehicle.id, location: location, limit: limit)) ?? []).map(corrected)
    }

    /// Replaces the data with a database exported by the Android app (its debug settings, "Export
    /// the database"), or by this one. Pecham and Bekubee KY sensors are assigned again: their IDs
    /// come from Android's Bluetooth addresses.
    func importDatabase(from url: URL) throws {
        let access = url.startAccessingSecurityScopedResource()
        defer { if access { url.stopAccessingSecurityScopedResource() } }
        let staged = FileManager.default.temporaryDirectory.appendingPathComponent("import.db")
        try? FileManager.default.removeItem(at: staged)
        try FileManager.default.copyItem(at: url, to: staged)
        // Checks it opens: its version, its tables
        try {
            let imported = try Database(path: staged.path)
            _ = try imported.vehicles()
            try imported.unbindSensorsIdentifiedByAddress()
        }()
        let target = try Self.databaseURL()
        database = nil
        for suffix in ["", "-wal", "-shm"] {
            try? FileManager.default.removeItem(atPath: target.path + suffix)
        }
        try FileManager.default.moveItem(at: staged, to: target)
        try open()
        nearby = [:]
    }

    func setAlerts(_ enabled: Bool) {
        if enabled {
            notifier.requestAuthorization { [weak self] granted in self?.settings.alerts = granted }
        } else {
            settings.alerts = false
            notifier.clearAll()
        }
    }

    /// A reading as if it had been heard, for the demo
    func simulate(_ reading: SensorReading) {
        nearby[reading.sensorId] = Nearby(reading: reading, lastSeen: Date())
        heard.send(reading)
    }

    #if DEBUG
    /// A reading of [location] as its sensor's, shown and alerted on like a real one. Made up, it has
    /// no bytes to store. A tyre without a sensor gets a Sysgration one advertising it, which is only
    /// detected there until assigned.
    func simulate(_ vehicle: Vehicle, _ location: TyreLocation, pressure: Pressure, temperature: Temperature, battery: Float?, isAlarm: Bool) {
        let sensor = sensor(vehicle: vehicle, location: location)
        let brand = sensor?.brand ?? .sysgration
        let reading = SensorReading(
            timestamp: Date().timeIntervalSince1970, rssi: -60, sensorId: sensor?.id ?? location.code * 256 + 256,
            pressure: pressure, temperature: temperature, brand: brand, isAlarm: isAlarm,
            batteryVoltage: brand.reportsPercent ? nil : battery.map(Voltage.init(volts:)),
            batteryPercent: brand.reportsPercent ? battery.map { Int($0) } : nil,
            location: sensor == nil ? location.simulatedAdvertised : nil, raw: Data()
        )
        if sensor != nil { record(reading, vehicle: vehicle, location: location, isStored: false) } else { simulate(reading) }
    }
    #endif

    private func perform(_ change: (Database) throws -> Void) {
        guard let database else { return }
        do {
            try change(database)
        } catch {
            self.error = "\(error)"
        }
        reload()
    }
}

#if DEBUG
private extension TyreLocation {
    /// What a Sysgration sensor at this tyre advertises, see accepts
    var simulatedAdvertised: SensorLocation? {
        [SensorLocation.frontLeft, .frontRight, .rearLeft, .rearRight].first(where: accepts)
    }
}
#endif
