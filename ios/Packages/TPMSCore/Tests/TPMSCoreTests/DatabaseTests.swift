import Foundation
import XCTest
@testable import TPMSCore

final class DatabaseTests: XCTestCase {

    private var database: Database!

    override func setUpWithError() throws {
        database = try Database()
    }

    func testAVehicleRoundTrips() throws {
        var nmax = Vehicle.new(name: "NMax", kind: .motorcycle)
        nmax.isCurrent = true
        try database.save(nmax)
        XCTAssertEqual(try database.vehicles(), [nmax])

        nmax.name = "Yamaha"
        nmax.highTemp = Temperature(celsius: 80)
        try database.save(nmax)
        XCTAssertEqual(try database.vehicles(), [nmax])
    }

    func testOneVehicleIsAlwaysCurrentAndTheLastIsntDeleted() throws {
        var first = Vehicle.new(name: "First", kind: .car)
        first.isCurrent = true
        let second = Vehicle.new(name: "Second", kind: .car)
        try database.save(first)
        try database.save(second)
        try database.setCurrent(second.id)
        XCTAssertEqual(try database.vehicles().filter(\.isCurrent).map(\.id), [second.id])

        try database.delete(second.id)
        XCTAssertEqual(try database.vehicles().filter(\.isCurrent).map(\.id), [first.id])
        try database.delete(first.id)
        XCTAssertEqual(try database.vehicles().map(\.id), [first.id])
    }

    func testAReadingIsDecodedFromItsAdvertisement() throws {
        var nmax = Vehicle.new(name: "NMax", kind: .motorcycle)
        nmax.isCurrent = true
        try database.save(nmax)
        let rear = TyreLocation.axle(.rear)
        try database.bind(BoundSensor(id: 42, brand: .pecham, vehicleId: nmax.id, location: rear))
        XCTAssertEqual(try database.sensors(), [BoundSensor(id: 42, brand: .pecham, vehicleId: nmax.id, location: rear)])

        let packet = AdvertisingPacket(bytes: Data(hex: "0303A5270308425208FF401E1F01D06FCA")!)
        let reading = try XCTUnwrap(packet.decode(timestamp: 1000, rssi: -60, addressId: 42))
        try database.insert(reading, vehicleId: nmax.id, location: rear)
        // The same tyre at the same time is the same reading
        try database.insert(reading, vehicleId: nmax.id, location: rear)
        XCTAssertEqual(try database.readings(vehicleId: nmax.id, location: rear), [reading])
        XCTAssertEqual(try database.latestReading(vehicleId: nmax.id, location: rear), reading)
    }

    func testBindingMovesASensorAndReplacesTheTyresPreviousOne() throws {
        var car = Vehicle.new(name: "Car", kind: .car)
        car.isCurrent = true
        try database.save(car)
        try database.bind(BoundSensor(id: 1, brand: .wicarlink, vehicleId: car.id, location: .wheel(.frontLeft)))
        try database.bind(BoundSensor(id: 1, brand: .wicarlink, vehicleId: car.id, location: .wheel(.rearLeft)))
        try database.bind(BoundSensor(id: 2, brand: .wicarlink, vehicleId: car.id, location: .wheel(.rearLeft)))
        XCTAssertEqual(try database.sensors().map(\.id), [2])
    }

    func testAnImportKeepsOnlyTheSensorsWithAnId() throws {
        var car = Vehicle.new(name: "Car", kind: .car)
        car.isCurrent = true
        try database.save(car)
        try database.bind(BoundSensor(id: 1, brand: .pecham, vehicleId: car.id, location: .wheel(.frontLeft)))
        try database.bind(BoundSensor(id: 2, brand: .bekubeeKy, vehicleId: car.id, location: .wheel(.frontRight)))
        try database.bind(BoundSensor(id: 3, brand: .wicarlink, vehicleId: car.id, location: .wheel(.rearLeft)))
        try database.unbindSensorsIdentifiedByAddress()
        XCTAssertEqual(try database.sensors().map(\.id), [3])
    }

    func testTheAndroidSchemaSnapshotOpens() throws {
        // A database made by the Android app, its version being the same, opens as is
        let url = URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent().appendingPathComponent("../../../../../data/vehicle/src/main/sqldelight/12.db")
            .standardizedFileURL
        let copy = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString + ".db")
        try FileManager.default.copyItem(at: url, to: copy)
        let android = try Database(path: copy.path)
        // The snapshot has the vehicle every Android install starts with
        XCTAssertEqual(try android.vehicles().map(\.name), ["My car"])
    }
}

final class AlertTests: XCTestCase {

    private let thresholds = AlertThresholds(
        lowPressure: .bar(2), highPressure: .bar(3), highTemp: Temperature(celsius: 90), lowBatteryVoltage: Voltage(volts: 2.6)
    )

    private func reading(kpa: Float, celsius: Float = 20, volts: Float = 3, at timestamp: Double = 0) -> SensorReading {
        SensorReading(timestamp: timestamp, rssi: -60, sensorId: 1, pressure: Pressure(kpa: kpa), temperature: Temperature(celsius: celsius),
                      brand: .pecham, isAlarm: false, batteryVoltage: Voltage(volts: volts), batteryPercent: nil, location: nil, raw: Data())
    }

    func testPressureLevels() {
        XCTAssertNil(thresholds.pressureLevel(Pressure(kpa: 250)))
        XCTAssertEqual(thresholds.pressureLevel(Pressure(kpa: 205)), .amber)
        XCTAssertEqual(thresholds.pressureLevel(Pressure(kpa: 200)), .red)
        XCTAssertEqual(thresholds.pressureLevel(Pressure(kpa: 150)), .crimson)
        XCTAssertEqual(thresholds.pressureLevel(Pressure(kpa: 300)), .red)
        XCTAssertEqual(thresholds.pressureLevel(Pressure(kpa: 360)), .crimson)
        var spare = thresholds
        spare.allowsCritical = false
        XCTAssertEqual(spare.pressureLevel(Pressure(kpa: 150)), .red)
    }

    func testTemperatureLevels() {
        XCTAssertNil(thresholds.temperatureLevel(Temperature(celsius: 79)))
        XCTAssertEqual(thresholds.temperatureLevel(Temperature(celsius: 80)), .amber)
        XCTAssertEqual(thresholds.temperatureLevel(Temperature(celsius: 90)), .red)
        XCTAssertEqual(thresholds.temperatureLevel(Temperature(celsius: 110)), .crimson)
    }

    func testTheBatteryAlarmComesDownInTheCold() {
        XCTAssertEqual(thresholds.batteryLevel(Voltage(volts: 2.6), Temperature(celsius: 20)), .red)
        XCTAssertEqual(thresholds.batteryLevel(Voltage(volts: 2.6), Temperature(celsius: 0)), .amber)
        XCTAssertNil(thresholds.batteryLevel(Voltage(volts: 2.8), Temperature(celsius: 20)))
    }

    func testARedBatteryNotifiesOnceItStaysRed() {
        let first = TyreAlerts().next(reading(kpa: 250, volts: 2.5, at: 0), thresholds, nil)
        XCTAssertEqual(first.levels[.battery], .red)
        XCTAssertEqual(first.notifiable[.battery], .amber)
        let later = first.next(reading(kpa: 250, volts: 2.5, at: 601), thresholds, nil)
        XCTAssertEqual(later.notifiable[.battery], .red)
    }

    func testALeakWhileRiding() {
        var tracker = PressureLossTracker()
        for (index, kpa) in [250, 252, 244, 243].enumerated() {
            tracker = tracker.next(reading(kpa: Float(kpa), celsius: 30, at: Double(index * 60)))
        }
        XCTAssertEqual(tracker.loss?.drop.kpa, 9)
        // Cooling down isn't a leak
        var cooling = PressureLossTracker()
        for (index, (kpa, celsius)) in [(250, 40), (242, 30), (241, 29)].enumerated() {
            cooling = cooling.next(reading(kpa: Float(kpa), celsius: Float(celsius), at: Double(index * 60)))
        }
        XCTAssertNil(cooling.loss)
    }

    private func track(_ readings: [(minute: Double, kpa: Float)]) -> PressureLoss? {
        readings.reduce(PressureLossTracker()) { $0.next(reading(kpa: $1.kpa, celsius: 30, at: $1.minute * 60)) }.loss
    }

    func testASingleStepOfACoarseSensorIsNotALeak() {
        // An older Wicarlink sensor, in steps of 10 kPa
        XCTAssertNil(track([(0, 220), (2, 230), (4, 240), (6, 230), (8, 230)]))
        XCTAssertEqual(try XCTUnwrap(track([(0, 220), (2, 230), (4, 240), (6, 230), (8, 220), (10, 220)])).drop.kpa, 20, accuracy: 0.01)
    }

    func testADropReadInOneGoIsASingleStep() {
        XCTAssertNil(track([(0, 214), (2, 206), (4, 206)]))
    }

    func testASensorsStepCarriesOverFromItsEarlierRides() {
        let later: [(minute: Double, kpa: Float)] = [(60, 240), (62, 220), (64, 220)]
        XCTAssertNil(track(later))
        XCTAssertNotNil(track([(0, 220), (2, 230)] + later))
    }

    func testANewerWicarlinkSensorsRideIsNotALeak() {
        // Gold's front left on 2026-11-27, in units of 3.144 kPa which 0.1 bar steps turned into a
        // 10 kPa drop at 09:39: (minute, units, celsius)
        let ride: [(Double, Int, Float)] = [
            (0, 55, 32), (0.6, 55, 33), (2.6, 55, 33), (4.6, 55, 34), (8.7, 55, 36), (10.75, 57, 34),
            (22.9, 56, 33), (24.9, 56, 32), (26.9, 56, 32), (28.9, 56, 31), (30, 56, 30), (32, 55, 30),
            (34, 55, 31), (36, 56, 31),
        ]
        let tracker = ride.reduce(PressureLossTracker()) {
            $0.next(reading(kpa: Float($1.1) * 3.144, celsius: $1.2, at: $1.0 * 60))
        }
        XCTAssertNil(tracker.loss)
    }
}
