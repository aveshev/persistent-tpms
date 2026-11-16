import XCTest
@testable import TPMSCore

final class BeaconPresenceTests: XCTestCase {
    private let beacon = UUID()

    func testASinglePacketIsNotNearby() {
        var presence = BeaconPresence()
        presence.heard(beacon, rssi: -60, at: 0)
        XCTAssertEqual(presence.nearby, [:])
    }

    func testTwoPacketsAreNearbyWithTheLastSignal() {
        var presence = BeaconPresence()
        presence.heard(beacon, rssi: -60, at: 0)
        presence.heard(beacon, rssi: -70, at: 1)
        XCTAssertEqual(presence.nearby, [beacon: -70])
    }

    func testAWeakPacketDoesntCountNorChangeTheSignal() {
        var presence = BeaconPresence()
        presence.heard(beacon, rssi: -90, at: 0)
        presence.heard(beacon, rssi: -90, at: 1)
        XCTAssertEqual(presence.nearby, [:])
        presence.heard(beacon, rssi: -60, at: 2)
        presence.heard(beacon, rssi: -61, at: 3)
        presence.heard(beacon, rssi: -90, at: 4)
        XCTAssertEqual(presence.nearby, [beacon: -61])
    }

    func testAnUnmeasuredSignalDoesntCount() {
        var presence = BeaconPresence()
        presence.heard(beacon, rssi: RideTriggers.noRssi, at: 0)
        presence.heard(beacon, rssi: RideTriggers.noRssi, at: 1)
        XCTAssertEqual(presence.nearby, [:])
    }

    func testOnceNearbyASinglePacketWithinTheWindowKeepsItSo() {
        var presence = BeaconPresence()
        presence.heard(beacon, rssi: -60, at: 0)
        presence.heard(beacon, rssi: -60, at: 1)
        presence.tick(at: 20)
        presence.heard(beacon, rssi: -65, at: 25)
        presence.tick(at: 40)
        XCTAssertEqual(presence.nearby, [beacon: -65])
    }

    func testGoneQuietForTheWindowIsNotNearbyAndStartsOver() {
        var presence = BeaconPresence()
        presence.heard(beacon, rssi: -60, at: 0)
        presence.heard(beacon, rssi: -60, at: 1)
        presence.tick(at: 31)
        XCTAssertEqual(presence.nearby, [:])
        presence.heard(beacon, rssi: -60, at: 32)
        XCTAssertEqual(presence.nearby, [:])
    }

    func testBeaconsAreToldApart() {
        var presence = BeaconPresence()
        let other = UUID()
        presence.heard(beacon, rssi: -60, at: 0)
        presence.heard(other, rssi: -60, at: 1)
        XCTAssertEqual(presence.nearby, [:])
    }
}
