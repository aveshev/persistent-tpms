import XCTest
@testable import TPMSCore

final class RideBeaconTests: XCTestCase {
    private let id = UUID()
    private let custom = "6E400001-B5A3-F393-E0A9-E50E24DCCA9E"

    func testAServiceComesBeforeAConnection() {
        XCTAssertEqual(RideBeacon(id: id, name: nil, services: ["180F"], isConnectable: true).wake, .service)
        XCTAssertEqual(RideBeacon(id: id, name: nil, services: ["180F"], isConnectable: false).wake, .service)
    }

    func testConnectableWithoutServiceWakesByConnecting() {
        XCTAssertEqual(RideBeacon(id: id, name: "Bike", services: [], isConnectable: true).wake, .connection)
    }

    func testNameOrManufacturerDataOnlyCantWake() {
        XCTAssertNil(RideBeacon(id: id, name: "Bike", services: [], isConnectable: false).wake)
    }

    func testRecognisedByItsIdentifier() {
        let beacon = RideBeacon(id: id, name: nil, services: ["180F"], isConnectable: false)
        XCTAssertTrue(beacon.isSending(id: id, name: nil, services: ["180F"]))
    }

    func testRotatedAddressIsRecognisedByNameOrOwnService() {
        let named = RideBeacon(id: id, name: "Bike", services: ["180F"], isConnectable: false)
        XCTAssertTrue(named.isSending(id: UUID(), name: "Bike", services: ["180F"]))
        let custom = RideBeacon(id: id, name: nil, services: [custom], isConnectable: false)
        XCTAssertTrue(custom.isSending(id: UUID(), name: nil, services: [self.custom]))
    }

    func testAnotherDeviceOfTheSameKindIsNotTheBeacon() {
        let beacon = RideBeacon(id: id, name: "Bike", services: ["180F"], isConnectable: false)
        XCTAssertFalse(beacon.isSending(id: UUID(), name: nil, services: ["180F"]))
        XCTAssertFalse(beacon.isSending(id: UUID(), name: "Other", services: ["180F"]))
    }

    func testBroadcastPeriodNeedsAFewPackets() {
        XCTAssertNil(RideBeacon.broadcastPeriod([]))
        XCTAssertNil(RideBeacon.broadcastPeriod([0, 1, 2]))
        XCTAssertEqual(RideBeacon.broadcastPeriod([0, 1, 2, 3]), 1)
    }

    func testBroadcastPeriodOfBurstsIsBetweenTheirStarts() throws {
        let timestamps = [0.0, 4, 8, 12].flatMap { burst in (0..<5).map { burst + Double($0) * 0.01 } }
        XCTAssertEqual(try XCTUnwrap(RideBeacon.broadcastPeriod(timestamps)), 4, accuracy: 0.001)
    }

    func testBroadcastPeriodOfADeviceAlwaysSendingFast() throws {
        let timestamps = (0..<10).map { Double($0) * 0.02 }
        XCTAssertEqual(try XCTUnwrap(RideBeacon.broadcastPeriod(timestamps)), 0.02, accuracy: 0.001)
    }

    func testBroadcastPeriodIgnoresMissedPackets() {
        XCTAssertEqual(RideBeacon.broadcastPeriod([0, 1, 2, 3, 6, 7, 8]), 1)
    }

    func testRecognisableByItsNameOrOwnService() {
        XCTAssertTrue(RideBeacon(id: id, name: "Bike", services: [], isConnectable: true).isRecognisable)
        XCTAssertTrue(RideBeacon(id: id, name: nil, services: ["180F", "7B1E5C2A-9D34-4F60-8A11-3C0D5E7F9A21"], isConnectable: false).isRecognisable)
    }

    func testNotRecognisableByAShortServiceAlone() {
        XCTAssertFalse(RideBeacon(id: id, name: nil, services: ["180F"], isConnectable: true).isRecognisable)
        XCTAssertFalse(RideBeacon(id: id, name: nil, services: [], isConnectable: true).isRecognisable)
    }

    func testAnUnnamedBeaconDoesntMatchAnUnnamedDevice() {
        let beacon = RideBeacon(id: id, name: nil, services: ["180F"], isConnectable: false)
        XCTAssertFalse(beacon.isSending(id: UUID(), name: nil, services: ["180F"]))
    }
}
