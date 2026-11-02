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
}
