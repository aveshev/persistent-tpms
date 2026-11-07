import XCTest
@testable import TPMSCore

final class BeaconConnectionTests: XCTestCase {
    private let connection = BeaconConnection(requestedAt: 1000, delay: 120, wasNear: true)

    func testCompletingSoonAfterTheDelayWasThereAllAlong() {
        XCTAssertEqual(connection.completed(with: .near(rssi: -70), at: 1120).outcome, .stillThere)
        XCTAssertEqual(connection.completed(with: .near(rssi: -70), at: 1120 + BeaconConnection.margin - 1).outcome, .stillThere)
    }

    func testCompletingWellAfterTheDelayCameBack() {
        XCTAssertEqual(connection.completed(with: .near(rssi: -70), at: 1120 + BeaconConnection.margin).outcome, .heard(rssi: -70))
    }

    func testAfterAWeakSignalOrAFailureItCameCloser() {
        let weak = BeaconConnection(requestedAt: 1000, delay: 120, wasNear: false)
        XCTAssertEqual(weak.completed(with: .near(rssi: -70), at: 1121).outcome, .heard(rssi: -70))
    }

    func testANearReadStartsCountingOver() {
        let result = BeaconConnection(requestedAt: 1000, delay: 120, wasNear: true, misses: 1).completed(with: .near(rssi: -70), at: 1121)
        XCTAssertEqual(result.wasNear, true)
        XCTAssertEqual(result.misses, 0)
    }

    func testOneWeakReadBetweenTwoGoodOnesIsNoTrip() {
        let weak = connection.completed(with: .far, at: 1121)
        XCTAssertEqual(weak.outcome, .nothing)
        XCTAssertEqual(weak.wasNear, true)
        let next = BeaconConnection(requestedAt: 1121, delay: 120, wasNear: weak.wasNear, misses: weak.misses)
        XCTAssertEqual(next.completed(with: .near(rssi: -76), at: 1242).outcome, .stillThere)
    }

    func testTwoWeakReadsInARowLeft() {
        let first = connection.completed(with: .far, at: 1121)
        let second = BeaconConnection(requestedAt: 1121, delay: 120, wasNear: first.wasNear, misses: first.misses).completed(with: .far, at: 1242)
        XCTAssertEqual(second.wasNear, false)
        let third = BeaconConnection(requestedAt: 1242, delay: 120, wasNear: second.wasNear, misses: second.misses)
        XCTAssertEqual(third.completed(with: .near(rssi: -70), at: 1363).outcome, .heard(rssi: -70))
    }

    func testAWeakReadAfterBeingAwayLeavesItAway() {
        let result = connection.completed(with: .far, at: 1120 + BeaconConnection.margin)
        XCTAssertEqual(result.outcome, .nothing)
        XCTAssertEqual(result.wasNear, false)
    }

    func testAnUnmeasuredSignalCountsAsAMiss() {
        XCTAssertEqual(connection.completed(with: .unmeasured, at: 1121).misses, 1)
    }

    func testReadingTheSignal() {
        XCTAssertEqual(BeaconConnection.read(rssi: -70, failed: false), .near(rssi: -70))
        XCTAssertEqual(BeaconConnection.read(rssi: RideTriggers.minRssi, failed: false), .near(rssi: RideTriggers.minRssi))
        XCTAssertEqual(BeaconConnection.read(rssi: RideTriggers.minRssi - 1, failed: false), .far)
        XCTAssertEqual(BeaconConnection.read(rssi: -70, failed: true), .unmeasured)
        XCTAssertEqual(BeaconConnection.read(rssi: 0, failed: false), .unmeasured)
        XCTAssertEqual(BeaconConnection.read(rssi: RideTriggers.noRssi, failed: false), .unmeasured)
    }

    func testSurvivesBeingStored() throws {
        let stored = try JSONDecoder().decode(BeaconConnection.self, from: JSONEncoder().encode(BeaconConnection(requestedAt: 1000, delay: 120, wasNear: true, misses: 1)))
        XCTAssertEqual(stored, BeaconConnection(requestedAt: 1000, delay: 120, wasNear: true, misses: 1))
    }

    func testReadsWhatWasStoredBeforeMisses() throws {
        let stored = try JSONDecoder().decode(BeaconConnection.self, from: Data(#"{"requestedAt":1000,"delay":1800,"wasNear":true}"#.utf8))
        XCTAssertEqual(stored, BeaconConnection(requestedAt: 1000, delay: 1800, wasNear: true))
    }
}
