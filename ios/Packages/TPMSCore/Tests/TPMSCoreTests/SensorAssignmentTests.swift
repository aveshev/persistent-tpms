import XCTest
@testable import TPMSCore

final class AssignStepTests: XCTestCase {

    private func play(_ start: AssignStep, _ readings: [(Int, Float)], heard: [Int: Float] = [:]) -> (AssignStep, [Int: Float]) {
        readings.reduce((start, heard)) { state, reading in
            (state.0.after(sensorId: reading.0, kpa: reading.1, readings: state.1), state.1.merging([reading.0: reading.1]) { $1 })
        }
    }

    func testOnlyTheSensorFollowingEveryStepIsFound() {
        // 1 is the user's, 2 sits on a wheel nearby, 3 is in a box
        let (putOn, heard) = play(.putOn(), [(1, 2), (1, 230), (2, 250), (3, 1)])
        XCTAssertEqual(putOn, .putOn(found: [1, 2], low: [1, 3]))
        guard case let .putOn(found, _) = putOn else { return XCTFail() }

        let (takeOff, heardOff) = play(.takeOff(candidates: found), [(2, 248), (1, 3), (3, 0)], heard: heard)
        XCTAssertEqual(takeOff, .takeOff(candidates: [1, 2], off: [1]))
        guard case let .takeOff(_, off) = takeOff else { return XCTFail() }

        let (putBack, _) = play(.putBack(candidates: off), [(2, 262), (1, 229)], heard: heardOff)
        XCTAssertEqual(putBack, .found(sensorId: 1))
    }

    func testAChangeBelowTheThresholdIsNotAMove() {
        XCTAssertEqual(play(.putOn(), [(1, 200), (2, 2), (2, 16)]).0, .putOn(found: [1], low: [2]))
        XCTAssertEqual(play(.putOn(), [(2, 2), (2, 17)]).0, .putOn(found: [2], low: [2]))
        XCTAssertEqual(play(.takeOff(candidates: nil), [(1, 200), (1, 186)], heard: [:]).0, .takeOff(candidates: nil))
    }

    func testASensorAlreadyOnTheWheelCanBeFirstHeardOffIt() {
        XCTAssertEqual(play(.takeOff(candidates: nil), [(1, 1)]).0, .takeOff(candidates: nil, off: [1]))
    }

    func testAskAndFoundIgnoreReadings() {
        XCTAssertEqual(AssignStep.ask.after(sensorId: 1, kpa: 200, readings: [:]), .ask)
        XCTAssertEqual(AssignStep.found(sensorId: 2).after(sensorId: 1, kpa: 200, readings: [:]), .found(sensorId: 2))
    }
}

final class MoveChainTests: XCTestCase {
    private let fl = TyreLocation.wheel(.frontLeft), fr = TyreLocation.wheel(.frontRight)
    private let rl = TyreLocation.wheel(.rearLeft), rr = TyreLocation.wheel(.rearRight)
    private var all: [TyreLocation] { [fl, fr, rl, rr] }

    func testAnEmptyLocationEndsTheMove() {
        XCTAssertEqual(MoveChain(from: fl).tap(fr, occupied: [fl], all: all), .done(MoveChain(from: fl).plus(fr)))
    }

    func testTheFirstOccupiedLocationAsks() {
        XCTAssertEqual(MoveChain(from: fl).tap(fr, occupied: [fl, fr], all: all), .askSwapOrChain(MoveChain(from: fl).plus(fr)))
    }

    func testTwoLocationsSwapWithoutAsking() {
        XCTAssertEqual(MoveChain(from: fl).tap(fr, occupied: [fl, fr], all: [fl, fr]), .done(MoveChain(from: fl).plus(fr)))
    }

    func testARotationClosesOnTheStart() {
        let occupied: Set<TyreLocation> = [fl, fr, rl, rr]
        let chain = MoveChain(from: fl).plus(rl)
        XCTAssertFalse(chain.canTap(fl, all))
        guard case let .proceed(next) = chain.tap(rr, occupied: occupied, all: all) else { return XCTFail() }
        XCTAssertTrue(next.canTap(fl, all))
        XCTAssertEqual(next.tap(fl, occupied: occupied, all: all), .done(next))
        XCTAssertEqual(next.moves(occupied: occupied), [.init(from: fl, to: rl), .init(from: rl, to: rr), .init(from: rr, to: fl)])
    }

    func testContinuingEndsWhenASingleOptionIsLeft() {
        let chain = MoveChain(from: fl).plus(fr)
        XCTAssertEqual(chain.continuing([fl, fr, rl]), .done(chain.plus(rl)))
    }

    func testASwapIsTwoMoves() {
        XCTAssertEqual(MoveChain(from: fl).plus(fr).moves(occupied: [fl, fr]), [.init(from: fl, to: fr), .init(from: fr, to: fl)])
        XCTAssertEqual(MoveChain(from: fl).plus(fr).moves(occupied: [fl]), [.init(from: fl, to: fr)])
    }
}

final class QrCodeTests: XCTestCase {

    /// The sensor's advertised ID for a printed one, see AdvertisingPacket's Sysgration
    private func id(_ b0: UInt32, _ b1: UInt32, _ b2: UInt32) -> Int {
        Int(Int32(bitPattern: b0 << 8 | b1 << 16 | b2 << 24))
    }

    func testAFourSensorKitIsPlacedByTheIdsFirstDigit() {
        XCTAssertEqual(QrCode("http://x/?a=2AABBC&1AABBD&4AABBE&3AABF0"), .sensors([
            QrCodeSensor(id: id(0x2A, 0xAB, 0xBC), wheel: .frontRight),
            QrCodeSensor(id: id(0x1A, 0xAB, 0xBD), wheel: .frontLeft),
            QrCodeSensor(id: id(0x4A, 0xAB, 0xBE), wheel: .rearRight),
            QrCodeSensor(id: id(0x3A, 0xAB, 0xF0), wheel: .rearLeft),
        ]))
    }

    func testOtherwiseByTheirRank() {
        XCTAssertEqual(QrCode("AAAAAA&BBBBBB"), .sensors([
            QrCodeSensor(id: id(0xAA, 0xAA, 0xAA), wheel: .frontLeft),
            QrCodeSensor(id: id(0xBB, 0xBB, 0xBB), wheel: .frontRight),
        ]))
    }

    func testDuplicates() {
        XCTAssertEqual(QrCode("1AAAAA&1BBBBB"), .duplicateWheelLocation([.frontLeft]))
        XCTAssertEqual(QrCode("AAAAAA&AAAAAA"), .duplicateId([id(0xAA, 0xAA, 0xAA)]))
    }

    func testAWicarlinkCapHoldsItsIdAlone() {
        XCTAssertEqual(QrCode(" 2221C5\n"), .wicarlink(0x2221C5))
        XCTAssertNil(QrCode("x2221C5"))
        XCTAssertNil(QrCode("https://example.com"))
    }

    func testAKitOnEachKind() throws {
        guard case let .sensors(sensors) = QrCode("1AAAAA&2AAAAA&3AAAAA&4AAAAA") else { return XCTFail() }
        XCTAssertEqual(sensors.locations(on: .carWithSpare)?.map(\.location), [.wheel(.frontLeft), .wheel(.frontRight), .wheel(.rearLeft), .wheel(.rearRight)])
        XCTAssertNil(sensors.locations(on: .motorcycle))
        // Fewer wheels than the kit has sensors
        XCTAssertNil(sensors.locations(on: .tadpoleThreeWheeler))
        guard case let .sensors(two) = QrCode("1AAAAA&3AAAAA") else { return XCTFail() }
        XCTAssertEqual(two.locations(on: .motorcycle)?.map(\.location), [.axle(.front), .axle(.rear)])
        XCTAssertEqual(two.locations(on: .singleAxleTrailer)?.map(\.location), [.side(.left), .side(.left)])
    }

    /// Written the way SharedSensors.kt writes it
    private func shared(version: String = "1", _ bytes: [UInt8]) -> String {
        let data = Data(bytes).base64EncodedString().replacingOccurrences(of: "+", with: "-").replacingOccurrences(of: "/", with: "_").replacingOccurrences(of: "=", with: "")
        return "tpmsadvanced://sensors?v=\(version)&d=\(data)"
    }

    func testASharedCodeReadsLikeAndroidWritesIt() {
        // Car, "Van", 2 sensors: Pecham at the front left, not calibrated. Sysgration with a negative
        // ID at the spare, calibrated by +1.5 kPa and 1.25.
        let code = shared([0, 3, 0x56, 0x61, 0x6E, 2,
                           0, 0, 1, 0, 0, 0, 0,
                           0xFF, 0xFF, 0xFF, 0x00, 8, 4, 0b11, 0x3F, 0xC0, 0, 0, 0x3F, 0xA0, 0, 0])
        XCTAssertEqual(QrCode(code), .shared(SharedSensors(kind: .car, name: "Van", sensors: [
            SharedSensor(id: 256, location: .wheel(.frontLeft), brand: .pecham, isCalibrated: false, offsetKpa: 0, multiplier: 1),
            SharedSensor(id: -256, location: .spare, brand: .sysgration, isCalibrated: true, offsetKpa: 1.5, multiplier: 1.25),
        ])))
    }

    func testAnUnreadableSharedCode() {
        XCTAssertEqual(QrCode(shared(version: "2", [0])), .sharedByNewerApp)
        XCTAssertEqual(QrCode("tpmsadvanced://sensors?d=AAAA"), .sharedUnreadable)
        // Truncated
        XCTAssertEqual(QrCode(shared([0, 3, 0x56])), .sharedUnreadable)
        // Trailing bytes
        XCTAssertEqual(QrCode(shared([0, 0, 1, 0, 0, 1, 0, 0, 0, 0, 9])), .sharedUnreadable)
        // Nothing shared
        XCTAssertEqual(QrCode(shared([0, 0, 0])), .sharedUnreadable)
    }

    private func QrCode(_ code: String) -> QrCodeResult? { QrCodeResult.parse(code) }
}

final class SensorMoveTests: XCTestCase {
    private let fl = TyreLocation.wheel(.frontLeft), fr = TyreLocation.wheel(.frontRight), rl = TyreLocation.wheel(.rearLeft)

    private func reading(_ sensorId: Int, at timestamp: Double) -> SensorReading {
        // A Pecham's ID comes from the database's own column
        AdvertisingPacket(bytes: Data(hex: "0303a5270308425208ff101e140179da44")!).decode(timestamp: timestamp, rssi: -60, addressId: sensorId)!
    }

    private func setUp2() throws -> (Database, Vehicle, Vehicle) {
        let database = try Database()
        let car = Vehicle.new(name: "Car", kind: .car), van = Vehicle.new(name: "Van", kind: .car)
        try database.save(car)
        try database.save(van)
        return (database, car, van)
    }

    private func bind(_ database: Database, _ id: Int, _ vehicle: Vehicle, _ location: TyreLocation) throws {
        try database.bind(BoundSensor(id: id, brand: .pecham, vehicleId: vehicle.id, location: location))
        try database.insert(reading(id, at: Double(id)), vehicleId: vehicle.id, location: location)
    }

    private func sensorIds(_ database: Database, _ vehicle: Vehicle) throws -> [TyreLocation: Int] {
        Dictionary(uniqueKeysWithValues: try database.sensors().filter { $0.vehicleId == vehicle.id }.map { ($0.location, $0.id) })
    }

    func testASwapTakesTheReadingsAlong() throws {
        let (database, car, _) = try setUp2()
        try bind(database, 1, car, fl)
        try bind(database, 2, car, fr)
        try database.move(vehicleId: car.id, moves: MoveChain(from: fl).plus(fr).moves(occupied: [fl, fr]))
        XCTAssertEqual(try sensorIds(database, car), [fl: 2, fr: 1])
        XCTAssertEqual(try database.latestReading(vehicleId: car.id, location: fl)?.sensorId, 2)
        XCTAssertEqual(try database.latestReading(vehicleId: car.id, location: fr)?.sensorId, 1)
        XCTAssertEqual(try database.readings(vehicleId: car.id, location: fr).map(\.timestamp), [1])
    }

    func testARotation() throws {
        let (database, car, _) = try setUp2()
        try bind(database, 1, car, fl)
        try bind(database, 2, car, fr)
        try bind(database, 3, car, rl)
        try database.move(vehicleId: car.id, moves: MoveChain(from: fl).plus(fr).plus(rl).moves(occupied: [fl, fr, rl]))
        XCTAssertEqual(try sensorIds(database, car), [fr: 1, rl: 2, fl: 3])
    }

    func testAReadingInTheWayKeepsItsPlace() throws {
        let (database, car, _) = try setUp2()
        try bind(database, 1, car, fl)
        // Another sensor read at the same time at the wheel it goes to, unbound since
        try database.insert(reading(9, at: 1), vehicleId: car.id, location: fr)
        try database.move(vehicleId: car.id, moves: [.init(from: fl, to: fr)])
        XCTAssertEqual(try database.readings(vehicleId: car.id, location: fr).map(\.sensorId), [9])
        XCTAssertEqual(try database.readings(vehicleId: car.id, location: fl).count, 0)
    }

    func testMovingToAnotherVehicle() throws {
        let (database, car, van) = try setUp2()
        try bind(database, 1, car, fl)
        try bind(database, 2, van, rl)
        try database.moveToVehicle(vehicleId: car.id, location: fl, toVehicleId: van.id, toLocation: rl, swap: true)
        XCTAssertEqual(try sensorIds(database, car), [fl: 2])
        XCTAssertEqual(try sensorIds(database, van), [rl: 1])
        XCTAssertEqual(try database.latestReading(vehicleId: van.id, location: rl)?.sensorId, 1)
    }

    func testReplacingOnAnotherVehicleUnbindsTheOneThere() throws {
        let (database, car, van) = try setUp2()
        try bind(database, 1, car, fl)
        try bind(database, 2, van, rl)
        try database.moveToVehicle(vehicleId: car.id, location: fl, toVehicleId: van.id, toLocation: rl, swap: false)
        XCTAssertEqual(try sensorIds(database, car), [:])
        XCTAssertEqual(try sensorIds(database, van), [rl: 1])
    }

    func testClearingAVehicle() throws {
        let (database, car, van) = try setUp2()
        try bind(database, 1, car, fl)
        try bind(database, 2, van, rl)
        try database.unbindAll(vehicleId: car.id)
        XCTAssertEqual(try sensorIds(database, car), [:])
        XCTAssertEqual(try sensorIds(database, van), [rl: 2])
    }
}

final class CalibrationTests: XCTestCase {
    private let offset = PressureCalibration(offset: Pressure(kpa: 10), multiplier: 1)

    func testZeroIsCorrectedTooButNeverGoesBelowZero() {
        XCTAssertEqual(offset.applyTo(Pressure(kpa: 0)), Pressure(kpa: 10))
        XCTAssertEqual(PressureCalibration(offset: Pressure(kpa: -50), multiplier: 1).applyTo(Pressure(kpa: 20)), Pressure(kpa: 0))
    }

    func testTheMultiplierComesBeforeTheOffset() {
        XCTAssertEqual(PressureCalibration(offset: Pressure(kpa: 10), multiplier: 2).applyTo(Pressure(kpa: 100)), Pressure(kpa: 210))
    }

    func testOffOrAdjustingNothingAppliesNothing() {
        XCTAssertNil(SensorCalibration(isEnabled: false, calibration: offset).applied)
        XCTAssertNil(SensorCalibration(isEnabled: true, calibration: PressureCalibration()).applied)
        XCTAssertEqual(SensorCalibration(isEnabled: true, calibration: offset).applied, offset)
    }

    func testTurningItOnWithoutAdjustingAnythingIsNoChange() {
        var draft = CalibrationDraft(saved: .none, others: [])
        draft.draft.isEnabled = true
        XCTAssertFalse(draft.isChanged)
        draft.draft.calibration = offset
        XCTAssertTrue(draft.isChanged)
        XCTAssertEqual(draft.toApply.applied, offset)
    }

    func testOffKeepsTheSavedValues() {
        let saved = SensorCalibration(isEnabled: true, calibration: offset)
        var draft = CalibrationDraft(saved: saved, others: [])
        draft.draft.isEnabled = false
        draft.draft.calibration.multiplier = 1.2
        XCTAssertEqual(draft.toApply, SensorCalibration(isEnabled: false, calibration: offset))
    }

    func testApplyingToAllAsksBeforeErasingAnotherCalibration() {
        let other = SensorCalibration(isEnabled: true, calibration: PressureCalibration(offset: Pressure(kpa: 5)))
        XCTAssertTrue(CalibrationDraft(saved: .none, others: [other]).applyingErases)
        XCTAssertTrue(CalibrationDraft(saved: .none, others: [other]).canApplyToAll)
        XCTAssertFalse(CalibrationDraft(saved: .none, others: [.none]).applyingErases)
        XCTAssertFalse(CalibrationDraft(saved: .none, others: [.none]).canApplyToAll)
        XCTAssertFalse(CalibrationDraft(saved: other, others: [other]).canApplyToAll)
    }
}

final class SharedSensorsUriTests: XCTestCase {
    func testAnotherPhoneReadsWhatThisOneShares() {
        let shared = SharedSensors(kind: .carWithSpare, name: "My car", sensors: [
            SharedSensor(id: 786_688, location: .wheel(.frontLeft), brand: .sysgration, isCalibrated: false, offsetKpa: 0, multiplier: 1),
            SharedSensor(id: -1_072_189_945, location: .wheel(.rearRight), brand: .wicarlink, isCalibrated: true, offsetKpa: -3.5, multiplier: 1.02),
        ])
        XCTAssertEqual(QrCodeResult.parse(shared.uri), .shared(shared))
    }

    func testTheValuesOfAnUncalibratedSensorAreLeftOut() {
        // 1 kind + 1 length + 1 name + 1 count + 4 id + 1 location + 1 brand + 1 flags: 11 bytes
        let shared = SharedSensors(kind: .car, name: "x", sensors: [
            SharedSensor(id: 1, location: .wheel(.frontLeft), brand: .wicarlink, isCalibrated: false, offsetKpa: 0, multiplier: 1),
        ])
        XCTAssertEqual(shared.uri, "tpmsadvanced://sensors?v=1&d=AAF4AQAAAAEAAgA")
    }
}
