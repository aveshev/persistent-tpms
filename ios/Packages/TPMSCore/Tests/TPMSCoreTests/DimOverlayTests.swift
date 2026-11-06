import Foundation
import XCTest
@testable import TPMSCore

final class DimOverlayTests: XCTestCase {

    func testAlphaDividesTheLightByTheDivisor() {
        for divisor in [2, 5, 10, 50] {
            let light = pow(1 - DimOverlay.alpha(divisor: divisor), 2.2)
            XCTAssertEqual(light, 1 / Double(divisor), accuracy: 0.0001)
        }
    }

    func testAlphaOfADivisorOfOneLeavesTheScreenAsItIs() {
        XCTAssertEqual(DimOverlay.alpha(divisor: 1), 0)
    }

    func testAlphaKnownValues() {
        XCTAssertEqual(DimOverlay.alpha(divisor: 2), 0.27, accuracy: 0.01)
        XCTAssertEqual(DimOverlay.alpha(divisor: 50), 0.83, accuracy: 0.01)
    }
}
