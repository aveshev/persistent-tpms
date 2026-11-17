import XCTest
@testable import TPMSCore

final class AlertSnoozesTests: XCTestCase {
    private let sensor = 21

    func testSnoozedUntilItEnds() {
        var snoozes = AlertSnoozes()
        XCTAssertFalse(snoozes.isSnoozed(sensorId: sensor, .pressure, .red, at: 1000))
        snoozes.snooze(sensorId: sensor, .pressure, .red, for: 600, at: 1000)
        XCTAssertTrue(snoozes.isSnoozed(sensorId: sensor, .pressure, .red, at: 1599))
        XCTAssertFalse(snoozes.isSnoozed(sensorId: sensor, .pressure, .red, at: 1600))
    }

    func testALowerLevelIsSnoozedTooButNotAHigherOne() {
        var snoozes = AlertSnoozes()
        snoozes.snooze(sensorId: sensor, .pressure, .red, for: 600, at: 1000)
        XCTAssertTrue(snoozes.isSnoozed(sensorId: sensor, .pressure, .amber, at: 1001))
        XCTAssertFalse(snoozes.isSnoozed(sensorId: sensor, .pressure, .crimson, at: 1001))
    }

    func testAShorterSnoozeDoesntCutALongerOne() {
        var snoozes = AlertSnoozes()
        snoozes.snooze(sensorId: sensor, .pressure, .amber, for: 86400, at: 1000)
        snoozes.snooze(sensorId: sensor, .pressure, .red, for: 600, at: 1000)
        XCTAssertFalse(snoozes.isSnoozed(sensorId: sensor, .pressure, .red, at: 2000))
        XCTAssertTrue(snoozes.isSnoozed(sensorId: sensor, .pressure, .amber, at: 2000))
    }

    func testAnotherSensorOrClassEscapesIt() {
        var snoozes = AlertSnoozes()
        snoozes.snooze(sensorId: sensor, .pressure, .red, for: 600, at: 1000)
        XCTAssertFalse(snoozes.isSnoozed(sensorId: 22, .pressure, .red, at: 1001))
        XCTAssertFalse(snoozes.isSnoozed(sensorId: sensor, .temperature, .red, at: 1001))
    }

    func testTheEndedOnesAreForgotten() {
        var snoozes = AlertSnoozes()
        snoozes.snooze(sensorId: 22, .pressure, .red, for: 600, at: 1000)
        var clean = AlertSnoozes()
        snoozes.snooze(sensorId: sensor, .pressure, .red, for: 600, at: 2000)
        clean.snooze(sensorId: sensor, .pressure, .red, for: 600, at: 2000)
        XCTAssertEqual(snoozes, clean)
    }

    func testSurvivesBeingStored() throws {
        var snoozes = AlertSnoozes()
        snoozes.snooze(sensorId: sensor, .pressure, .red, for: 600, at: 1000)
        XCTAssertEqual(try JSONDecoder().decode(AlertSnoozes.self, from: JSONEncoder().encode(snoozes)), snoozes)
    }

    func testTheShortestPeriodComesFirst() {
        XCTAssertEqual(AlertLevel.amber.snoozes, [86400, 604800])
        XCTAssertEqual(AlertLevel.red.snoozes, [600, 86400])
        XCTAssertEqual(AlertLevel.crimson.snoozes, [600, 86400])
    }
}
