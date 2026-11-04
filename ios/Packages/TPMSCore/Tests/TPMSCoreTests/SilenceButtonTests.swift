import XCTest
@testable import TPMSCore

final class SilenceButtonTests: XCTestCase {

    func testNothingToSayShowsNothing() {
        XCTAssertEqual(SilenceButton.of(highest: nil, silenced: nil), .idle)
    }

    func testOffersToSilenceTheHighestLevel() {
        XCTAssertEqual(SilenceButton.of(highest: .red, silenced: nil), .offer(isCritical: false))
        XCTAssertEqual(SilenceButton.of(highest: .crimson, silenced: nil), .offer(isCritical: true))
    }

    func testACriticalAlertGetsThroughASilenceOfTheRedOnes() {
        XCTAssertEqual(SilenceButton.of(highest: .crimson, silenced: .red), .offer(isCritical: true))
    }

    func testASilenceStaysShownWhileNothingGetsThroughIt() {
        XCTAssertEqual(SilenceButton.of(highest: nil, silenced: .red), .silenced(isCritical: false))
        XCTAssertEqual(SilenceButton.of(highest: nil, silenced: .crimson), .silenced(isCritical: true))
    }

    func testASilenceOfEverythingCoversACriticalAlert() {
        XCTAssertEqual(SilenceButton.of(highest: .crimson, silenced: .crimson), .silenced(isCritical: true))
    }
}
