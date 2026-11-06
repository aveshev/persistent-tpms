import XCTest
@testable import TPMSCore

final class RideTriggersTests: XCTestCase {
    private let source = "source"
    private let absent = RideTriggers.defaultAbsentFor
    private let stale = RideTriggers.defaultStaleAfter

    func testFirstReportIsBackNearTheVehicle() {
        var triggers = RideTriggers()
        XCTAssertTrue(triggers.heard(source, rssi: -60, at: 1000))
    }

    func testStayingNearbyIsNotComingBack() {
        var triggers = RideTriggers()
        _ = triggers.heard(source, rssi: -60, at: 1000)
        // Each report starts the absence over: reports closer than it never add up to one
        for minutes in 1...10 {
            XCTAssertFalse(triggers.heard(source, rssi: -60, at: 1000 + Double(minutes) * 20 * 60))
        }
    }

    func testHeardAgainAfterTheAbsenceIsComingBack() {
        var triggers = RideTriggers()
        _ = triggers.heard(source, rssi: -60, at: 1000)
        XCTAssertTrue(triggers.heard(source, rssi: -60, at: 1000 + absent))
    }

    func testWeakSignalIsNotHeard() {
        var triggers = RideTriggers()
        _ = triggers.heard(source, rssi: -60, at: 1000)
        // From the house: keeps the bike absent rather than present
        XCTAssertFalse(triggers.heard(source, rssi: -86, at: 1000 + absent))
        XCTAssertEqual(triggers.lastHeard[source], 1000)
        XCTAssertTrue(triggers.heard(source, rssi: -85, at: 1000 + absent + 1))
    }

    func testUnmeasuredSignalIsNotHeard() {
        var triggers = RideTriggers()
        XCTAssertFalse(triggers.heard(source, rssi: RideTriggers.noRssi, at: 1000))
        XCTAssertNil(triggers.lastHeard[source])
    }

    func testConfirmingTakesASecondReport() {
        var triggers = RideTriggers()
        XCTAssertFalse(triggers.heard(source, rssi: -60, needsConfirming: true, at: 1000))
        // A copy of the same packet
        XCTAssertFalse(triggers.heard(source, rssi: -60, needsConfirming: true, at: 1002))
        XCTAssertTrue(triggers.heard(source, rssi: -60, needsConfirming: true, at: 1010))
        // Once only
        XCTAssertFalse(triggers.heard(source, rssi: -60, needsConfirming: true, at: 1020))
    }

    func testWalkingPastTwiceFarApartStartsOver() {
        var triggers = RideTriggers()
        XCTAssertFalse(triggers.heard(source, rssi: -60, needsConfirming: true, at: 1000))
        XCTAssertFalse(triggers.heard(source, rssi: -60, needsConfirming: true, at: 1000 + 6 * 60))
        XCTAssertTrue(triggers.heard(source, rssi: -60, needsConfirming: true, at: 1000 + 6 * 60 + 10))
    }

    func testAConnectionEndingCountsAsHeardUntilThen() {
        var triggers = RideTriggers()
        _ = triggers.heard(source, rssi: -60, at: 1000)
        // Connected for 2 hours, then gone for less than the absence
        triggers.stillThere(source, at: 1000 + 2 * 3600)
        XCTAssertFalse(triggers.heard(source, rssi: -60, at: 1000 + 2 * 3600 + 60))
    }

    func testNothingShowingAlwaysPosts() {
        var triggers = RideTriggers()
        XCTAssertTrue(triggers.fired(at: 1000, isShowing: false))
        // Dismissed, or the app opened
        XCTAssertTrue(triggers.fired(at: 1001, isShowing: false))
    }

    func testACurrentNotificationStaysAsItIs() {
        var triggers = RideTriggers()
        _ = triggers.fired(at: 1000, isShowing: false)
        XCTAssertFalse(triggers.fired(at: 1000 + stale - 1, isShowing: true))
    }

    func testEachFiringKeepsTheNotificationCurrent() {
        var triggers = RideTriggers()
        _ = triggers.fired(at: 1000, isShowing: false)
        _ = triggers.fired(at: 1000 + stale - 1, isShowing: true)
        XCTAssertFalse(triggers.fired(at: 1000 + 2 * stale - 2, isShowing: true))
    }

    func testAStaleNotificationIsReplaced() {
        var triggers = RideTriggers()
        _ = triggers.fired(at: 1000, isShowing: false)
        XCTAssertTrue(triggers.fired(at: 1000 + stale, isShowing: true))
    }

    func testANotificationOfUnknownAgeIsReplaced() {
        var triggers = RideTriggers()
        XCTAssertTrue(triggers.fired(at: 1000, isShowing: true))
    }

    func testExpiresOnceAfterNothingFired() {
        var triggers = RideTriggers()
        XCTAssertFalse(triggers.expired(at: 1000))
        _ = triggers.fired(at: 1000, isShowing: false)
        XCTAssertFalse(triggers.expired(at: 1000 + stale - 1))
        XCTAssertTrue(triggers.expired(at: 1000 + stale))
        XCTAssertFalse(triggers.expired(at: 1000 + 2 * stale))
    }

    func testAFiringPutsOffExpiring() {
        var triggers = RideTriggers()
        _ = triggers.fired(at: 1000, isShowing: false)
        _ = triggers.fired(at: 1000 + stale - 1, isShowing: true)
        XCTAssertFalse(triggers.expired(at: 1000 + stale))
    }

    func testForgottenTriggerStartsOver() {
        var triggers = RideTriggers()
        let vehicle = UUID()
        _ = triggers.heard(RideTriggers.sensors(of: vehicle), rssi: -60, at: 1000)
        _ = triggers.heard(RideTriggers.beacon, rssi: -60, at: 1000)
        triggers.forgetSensors()
        XCTAssertTrue(triggers.heard(RideTriggers.sensors(of: vehicle), rssi: -60, at: 1001))
        XCTAssertFalse(triggers.heard(RideTriggers.beacon, rssi: -60, at: 1001))
    }

    func testSurvivesBeingStored() throws {
        var triggers = RideTriggers()
        _ = triggers.heard(source, rssi: -60, needsConfirming: true, at: 1000)
        _ = triggers.fired(at: 1000, isShowing: false)
        XCTAssertEqual(try JSONDecoder().decode(RideTriggers.self, from: JSONEncoder().encode(triggers)), triggers)
    }
}
