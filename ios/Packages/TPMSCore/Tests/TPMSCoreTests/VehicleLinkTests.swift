import XCTest
@testable import TPMSCore

final class VehicleLinkTests: XCTestCase {
    private let id = UUID(uuidString: "0E8F2B6A-3C1D-4F5E-9A7B-1C2D3E4F5A6B")!

    private func vehicleId(_ link: String) -> UUID? {
        URL(string: link).flatMap(VehicleLink.vehicleId)
    }

    func testTheLinkIsAndroids() {
        XCTAssertEqual(VehicleLink.url(for: id).absoluteString, "tpmsadvanced://vehicle/0e8f2b6a-3c1d-4f5e-9a7b-1c2d3e4f5a6b")
    }

    func testALinkGivesItsVehicleBack() {
        XCTAssertEqual(VehicleLink.vehicleId(from: VehicleLink.url(for: id)), id)
    }

    func testCaseDoesNotMatter() {
        XCTAssertEqual(vehicleId("TPMSAdvanced://Vehicle/0E8F2B6A-3C1D-4F5E-9A7B-1C2D3E4F5A6B"), id)
    }

    func testAQueryIsIgnored() {
        XCTAssertEqual(vehicleId("tpmsadvanced://vehicle/0e8f2b6a-3c1d-4f5e-9a7b-1c2d3e4f5a6b?from=shortcut"), id)
    }

    // The bad links of the Android release smoke test, and a few more
    func testAnythingElseIsNoVehicle() {
        [
            "tpmsadvanced://vehicle/not-a-uuid",
            "tpmsadvanced://vehicle",
            "tpmsadvanced://vehicle/",
            "tpmsadvanced://vehicle/0e8f2b6a-3c1d-4f5e-9a7b-1c2d3e4f5a6b/settings",
            "tpmsadvanced://sensor/0e8f2b6a-3c1d-4f5e-9a7b-1c2d3e4f5a6b",
            "https://vehicle/0e8f2b6a-3c1d-4f5e-9a7b-1c2d3e4f5a6b",
            "tpmsadvanced:0e8f2b6a-3c1d-4f5e-9a7b-1c2d3e4f5a6b",
        ].forEach { XCTAssertNil(vehicleId($0), $0) }
    }

    private func vehicle(_ name: String, _ kind: VehicleKind = .car) -> Vehicle {
        Vehicle.new(name: name, kind: kind)
    }

    func testShortcutsKeepTheVehiclesKindAndGoByNameWithoutReadings() {
        let vehicles = [vehicle("NMax", .motorcycle), vehicle("My car"), vehicle("Trailer", .singleAxleTrailer)]
        XCTAssertEqual(
            VehicleShortcut.list(vehicles, limit: 4),
            [vehicles[1], vehicles[0], vehicles[2]].map { VehicleShortcut(id: $0.id, name: $0.name, kind: $0.kind) }
        )
    }

    func testTheLatestReadComesFirst() {
        let car = vehicle("car"), bike = vehicle("bike"), trailer = vehicle("trailer")
        XCTAssertEqual(
            VehicleShortcut.list([car, trailer, bike], latestReadings: [car.id: 100, bike.id: 200, trailer.id: 50], limit: 4).map(\.name),
            ["bike", "car", "trailer"]
        )
    }

    func testTheWorstAlertComesFirstThenTheLatestRead() {
        let car = vehicle("car"), bike = vehicle("bike"), trailer = vehicle("trailer"), van = vehicle("van")
        XCTAssertEqual(
            VehicleShortcut.list(
                [car, trailer, bike, van],
                alerting: [car.id: .red, bike.id: .red, van.id: .crimson],
                latestReadings: [car.id: 100, bike.id: 200, trailer.id: 300],
                limit: 4
            ).map(\.name),
            ["van", "bike", "car", "trailer"]
        )
    }

    func testAnAmberAlertDoesNotRankFirst() {
        let car = vehicle("car"), bike = vehicle("bike")
        XCTAssertEqual(
            VehicleShortcut.list([car, bike], alerting: [car.id: .amber], latestReadings: [bike.id: 200], limit: 4).map(\.name),
            ["bike", "car"]
        )
    }

    func testAVehicleWithoutANameHasNoShortcut() {
        let vehicles = [vehicle(""), vehicle("NMax"), vehicle("  \n"), vehicle(" My car ")]
        XCTAssertEqual(VehicleShortcut.list(vehicles, limit: 4).map(\.name), [" My car ", "NMax"])
    }

    func testTheLimitCountsTheNamedOnes() {
        let vehicles = [vehicle("a"), vehicle(" "), vehicle("b"), vehicle("c")]
        XCTAssertEqual(VehicleShortcut.list(vehicles, limit: 2).map(\.name), ["a", "b"])
        XCTAssertEqual(VehicleShortcut.list(vehicles, limit: 0), [])
        XCTAssertEqual(VehicleShortcut.list(vehicles, limit: -1), [])
    }
}
