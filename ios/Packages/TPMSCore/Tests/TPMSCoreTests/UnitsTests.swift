import XCTest
@testable import TPMSCore

final class UnitsTests: XCTestCase {
    func testTheRegionsCommonUnitWhateverTheLanguage() {
        let expected: [(String, String, PressureUnit)] = [
            ("US", "en", .psi), ("CA", "fr", .psi), ("GB", "en", .psi), ("BR", "pt", .psi), ("US", "ru", .psi),
            ("RU", "ru", .bar), ("RU", "en", .bar), ("UA", "uk", .bar), ("DE", "de", .bar), ("150", "en", .bar),
            ("JP", "ja", .kiloPascal),
        ]
        for (region, language, unit) in expected {
            XCTAssertEqual(PressureUnit.common(region: region, language: language), unit, "\(language)-\(region)")
        }
    }

    func testWithoutARegionRussianReadsBar() {
        XCTAssertEqual(PressureUnit.common(region: nil, language: "ru"), .bar)
        XCTAssertEqual(PressureUnit.common(region: nil, language: "en"), .psi)
        XCTAssertEqual(PressureUnit.common(region: "", language: "de"), .psi)
    }

    func testFahrenheitInTheUSOnly() {
        XCTAssertEqual(TemperatureUnit.common(region: "US"), .fahrenheit)
        XCTAssertEqual(TemperatureUnit.common(region: "PR"), .fahrenheit)
        for region in ["GB", "CA", "RU", "DE", "", nil] as [String?] {
            XCTAssertEqual(TemperatureUnit.common(region: region), .celsius, region ?? "nil")
        }
    }

    func testANewVehiclesPressuresReadPlainlyInTheUnit() {
        let car = Vehicle.new(name: "Car", kind: .car, unit: .psi)
        XCTAssertEqual(car.lowPressure.convert(.psi), 33, accuracy: 0.001)
        XCTAssertEqual(car.highPressure.convert(.psi), 46, accuracy: 0.001)
        let bike = Vehicle.new(name: "NMax", kind: .motorcycle, unit: .bar)
        XCTAssertEqual(bike.lowPressure.convert(.bar), 1.7, accuracy: 0.001)
        XCTAssertEqual(bike.highPressure.convert(.bar), 2.8, accuracy: 0.001)
        XCTAssertEqual(bike.rearLowPressure?.convert(.bar) ?? 0, 1.9, accuracy: 0.001)
        XCTAssertEqual(bike.rearHighPressure?.convert(.bar) ?? 0, 3.0, accuracy: 0.001)
        XCTAssertEqual(Pressure.psi(24).rounded(.kiloPascal).kpa, 165, accuracy: 0.001)
    }
}
