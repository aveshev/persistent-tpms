import XCTest
@testable import TPMSCore

final class AlertActionTests: XCTestCase {
    private let thresholds = AlertThresholds(
        lowPressure: Pressure(kpa: 200),
        highPressure: Pressure(kpa: 300),
        highTemp: Temperature(celsius: 90),
        lowBatteryVoltage: Voltage(volts: 2.6)
    )

    private func alerts(kpa: Float, sensorId: Int = 1, isAlarm: Bool = false) -> TyreAlerts {
        TyreAlerts().next(
            SensorReading(timestamp: 1000, rssi: -60, sensorId: sensorId, pressure: Pressure(kpa: kpa), temperature: Temperature(celsius: 20),
                          brand: .pecham, isAlarm: isAlarm, batteryVoltage: Voltage(volts: 3), batteryPercent: nil, location: nil, raw: Data()),
            thresholds,
            nil
        )
    }

    private func action(kpa: Float, shown: AlertLevel?, sensorId: Int = 1, isHeld: Bool = false, snoozed: AlertLevel? = nil) -> AlertAction {
        AlertAction.of(
            alerts(kpa: kpa, sensorId: sensorId),
            .pressure,
            shown: shown.map { AlertAction.Shown(sensorId: 1, level: $0) },
            isHeld: isHeld,
            isSnoozed: { level in snoozed.map { level <= $0 } ?? false }
        )
    }

    private let amber: Float = 205
    private let red: Float = 190
    private let crimson: Float = 100
    private let fine: Float = 250

    func testNothingToSayIsNothing() {
        XCTAssertEqual(action(kpa: fine, shown: nil), AlertAction.none)
    }

    func testANewAlertSounds() {
        XCTAssertEqual(action(kpa: amber, shown: nil), .post(.amber, sound: true))
        XCTAssertEqual(action(kpa: red, shown: nil), .post(.red, sound: true))
    }

    func testAHigherLevelSounds() {
        XCTAssertEqual(action(kpa: crimson, shown: .amber), .post(.crimson, sound: true))
    }

    func testRedAndCrimsonSoundAgainAmberDoesnt() {
        XCTAssertEqual(action(kpa: red, shown: .red), .post(.red, sound: true))
        XCTAssertEqual(action(kpa: crimson, shown: .crimson), .post(.crimson, sound: true))
        XCTAssertEqual(action(kpa: amber, shown: .amber), .post(.amber, sound: false))
    }

    func testLoweringWaitsForTheHold() {
        XCTAssertEqual(action(kpa: amber, shown: .red, isHeld: true), .hold(.amber))
        XCTAssertEqual(action(kpa: fine, shown: .red, isHeld: true), .hold(nil))
    }

    func testAtItsLevelDuringTheHoldPostsAgain() {
        XCTAssertEqual(action(kpa: red, shown: .red, isHeld: true), .post(.red, sound: true))
    }

    func testLoweringOnceTheHoldEndedIsSilent() {
        XCTAssertEqual(action(kpa: amber, shown: .red), .post(.amber, sound: false))
        XCTAssertEqual(action(kpa: fine, shown: .red), .cancel)
    }

    func testASnoozedLevelStaysQuiet() {
        XCTAssertEqual(action(kpa: red, shown: nil, snoozed: .red), AlertAction.none)
        XCTAssertEqual(action(kpa: amber, shown: nil, snoozed: .red), AlertAction.none)
        XCTAssertEqual(action(kpa: red, shown: .red, snoozed: .red), AlertAction.none)
    }

    func testAHigherLevelThanSnoozedGetsThrough() {
        XCTAssertEqual(action(kpa: crimson, shown: nil, snoozed: .red), .post(.crimson, sound: true))
    }

    func testAnotherSensorStartsOver() {
        XCTAssertEqual(action(kpa: amber, shown: .red, sensorId: 2, isHeld: true), .post(.amber, sound: true))
        XCTAssertEqual(action(kpa: fine, shown: .red, sensorId: 2, isHeld: true), .cancel)
    }

    func testALeakIsToldInARedPressuresNotification() {
        let folded = alerts(kpa: red, isAlarm: true)
        XCTAssertTrue(folded.foldsLeaks)
        XCTAssertEqual(AlertAction.of(folded, .sensorAlarm, shown: nil, isHeld: false, isSnoozed: { _ in false }), AlertAction.none)
        XCTAssertEqual(
            AlertAction.of(folded, .sensorAlarm, shown: .init(sensorId: 1, level: .amber), isHeld: true, isSnoozed: { _ in false }),
            .cancel
        )
    }

    func testALeakIsNotifiedWhileThePressureIsFine() {
        let alone = alerts(kpa: fine, isAlarm: true)
        XCTAssertFalse(alone.foldsLeaks)
        XCTAssertEqual(AlertAction.of(alone, .sensorAlarm, shown: nil, isHeld: false, isSnoozed: { _ in false }), .post(.amber, sound: true))
    }

    func testOnlyNewOrHigherRaises() {
        let shown = AlertAction.Shown(sensorId: 1, level: .red)
        XCTAssertTrue(AlertAction.post(.red, sound: true).raises(shown: nil, sensorId: 1))
        XCTAssertTrue(AlertAction.post(.crimson, sound: true).raises(shown: shown, sensorId: 1))
        XCTAssertTrue(AlertAction.post(.red, sound: true).raises(shown: shown, sensorId: 2))
        XCTAssertFalse(AlertAction.post(.red, sound: true).raises(shown: shown, sensorId: 1))
        XCTAssertFalse(AlertAction.hold(nil).raises(shown: shown, sensorId: 1))
    }

    func testAThresholdChangeLowersOrClearsButNeverRaises() {
        let shown = AlertAction.Shown(sensorId: 1, level: .red)
        XCTAssertEqual(AlertAction.reevaluation(alerts(kpa: amber), .pressure, shown: shown, isSnoozed: { _ in false }), .post(.amber, sound: false))
        XCTAssertEqual(AlertAction.reevaluation(alerts(kpa: fine), .pressure, shown: shown, isSnoozed: { _ in false }), .cancel)
        XCTAssertEqual(AlertAction.reevaluation(alerts(kpa: crimson), .pressure, shown: shown, isSnoozed: { _ in false }), AlertAction.none)
        XCTAssertEqual(AlertAction.reevaluation(alerts(kpa: red), .pressure, shown: shown, isSnoozed: { _ in false }), .post(.red, sound: false))
    }

    func testAThresholdChangeLeavesAnotherSensorsNotificationToTheNextReading() {
        let shown = AlertAction.Shown(sensorId: 2, level: .red)
        XCTAssertEqual(AlertAction.reevaluation(alerts(kpa: fine), .pressure, shown: shown, isSnoozed: { _ in false }), AlertAction.none)
    }
}
