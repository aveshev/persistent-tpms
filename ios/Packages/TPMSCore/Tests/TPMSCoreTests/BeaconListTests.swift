import XCTest
@testable import TPMSCore

final class BeaconListTests: XCTestCase {
    private let bike = UUID()
    private let car = UUID()
    private let far = UUID()

    private func device(_ id: UUID, _ rssi: Int, isCandidate: Bool = true) -> BeaconList.Device {
        BeaconList.Device(id: id, rssi: rssi, isCandidate: isCandidate)
    }

    func testSortsTheLoudestFirst() {
        var list = BeaconList()
        list.update([device(bike, -80), device(car, -60)])
        list.sort()
        XCTAssertEqual(list.order, [car, bike])
        XCTAssertTrue(list.isSorted)
    }

    func testAWeakDeviceIsNotListed() {
        var list = BeaconList()
        list.update([device(bike, -60), device(far, -95, isCandidate: false)])
        list.sort()
        XCTAssertEqual(list.order, [bike])
    }

    func testTheOrderStaysUntilSorted() {
        var list = BeaconList()
        list.update([device(bike, -60), device(car, -70)])
        list.sort()
        list.update([device(bike, -80), device(car, -70)])
        XCTAssertEqual(list.order, [bike, car])
        XCTAssertFalse(list.isSorted)
        list.sort()
        XCTAssertEqual(list.order, [car, bike])
        XCTAssertTrue(list.isSorted)
    }

    func testStaysUnsortedOnceItWas() {
        var list = BeaconList()
        list.update([device(bike, -60), device(car, -70)])
        list.sort()
        list.update([device(bike, -80), device(car, -70)])
        // Back to the order listed: no flickering until sorted again
        list.update([device(bike, -60), device(car, -70)])
        XCTAssertFalse(list.isSorted)
    }

    func testANewDeviceWaitsForTheNextSort() {
        var list = BeaconList()
        list.update([device(bike, -60)])
        list.sort()
        list.update([device(bike, -60), device(car, -50)])
        XCTAssertEqual(list.order, [bike])
        XCTAssertEqual(list.notShown, [car])
        list.sort()
        XCTAssertEqual(list.order, [car, bike])
        XCTAssertEqual(list.notShown, [])
    }

    func testADeviceNoLongerACandidateStaysGreyedOutBelowTheOthers() {
        var list = BeaconList()
        list.update([device(bike, -60), device(car, -70)])
        list.sort()
        list.update([device(bike, -60, isCandidate: false), device(car, -70)])
        list.sort()
        XCTAssertEqual(list.order, [car, bike])
    }

    func testEqualSignalsKeepTheOrderFound() {
        var list = BeaconList()
        list.update([device(bike, -60), device(car, -60), device(far, -60)])
        list.sort()
        XCTAssertEqual(list.order, [bike, car, far])
    }

    func testAStrongDeviceOnlyBecomesWeakAFewDbUnderTheThreshold() {
        XCTAssertTrue(BeaconList.isStrong(rssi: -85, wasStrong: false))
        XCTAssertFalse(BeaconList.isStrong(rssi: -86, wasStrong: false))
        XCTAssertTrue(BeaconList.isStrong(rssi: -90, wasStrong: true))
        XCTAssertFalse(BeaconList.isStrong(rssi: -91, wasStrong: true))
    }
}
