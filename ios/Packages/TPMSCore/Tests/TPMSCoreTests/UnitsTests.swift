import XCTest
@testable import TPMSCore

final class UnitsTests: XCTestCase {

    override func tearDown() {
        AppLanguage.current = .system
    }

    func testWritesPressureUnitsInEnglish() {
        AppLanguage.current = .english
        XCTAssertEqual(Pressure.bar(2.3).string(.bar), "2.30 bar")
        XCTAssertEqual(Pressure(kpa: 230).string(.kiloPascal), "230 kPa")
        XCTAssertEqual(Pressure.psi(33).string(.psi), "33.0 psi")
    }

    func testWritesBarAndKpaInRussianAndPsiAsItIs() {
        AppLanguage.current = .russian
        XCTAssertEqual(Pressure.bar(2.3).string(.bar), "2,30 бар")
        XCTAssertEqual(Pressure(kpa: 230).string(.kiloPascal), "230 кПа")
        XCTAssertEqual(Pressure.psi(33).string(.psi), "33,0 psi")
    }

    func testWritesEnglishNumbersWithAPeriod() {
        AppLanguage.current = .english
        XCTAssertEqual(Pressure.bar(2.5).numberString(.bar), "2.5")
        XCTAssertEqual(Voltage(volts: 2.7).string, "2.7 V")
    }

    func testWritesRussianNumbersWithACommaAndTrimsTheirZeros() {
        AppLanguage.current = .russian
        XCTAssertEqual(Pressure.bar(2.5).numberString(.bar), "2,5")
        XCTAssertEqual(Pressure.bar(2).numberString(.bar), "2")
        XCTAssertEqual(Voltage(volts: 2.7).string, "2,7 V")
    }
}
