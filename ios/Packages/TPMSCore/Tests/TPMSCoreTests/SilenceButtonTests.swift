import XCTest
@testable import TPMSCore

final class SilenceButtonTests: XCTestCase {

    private let until = Date(timeIntervalSince1970: 1_000)

    func testNothingToSayShowsNothing() {
        XCTAssertEqual(SilenceButton.of(highest: nil, silence: nil), .idle)
    }

    func testOffersToSilenceTheHighestLevel() {
        XCTAssertEqual(SilenceButton.of(highest: .red, silence: nil), .offer(isCritical: false))
        XCTAssertEqual(SilenceButton.of(highest: .crimson, silence: nil), .offer(isCritical: true))
    }

    func testACriticalAlertGetsThroughASilenceOfTheRedOnes() {
        XCTAssertEqual(SilenceButton.of(highest: .crimson, silence: (.red, until)), .offer(isCritical: true))
    }

    func testASilenceStaysShownWhileNothingGetsThroughIt() {
        XCTAssertEqual(SilenceButton.of(highest: nil, silence: (.red, until)), .silenced(isCritical: false, until: until))
        XCTAssertEqual(SilenceButton.of(highest: nil, silence: (.crimson, until)), .silenced(isCritical: true, until: until))
    }

    func testASilenceOfEverythingCoversACriticalAlert() {
        XCTAssertEqual(SilenceButton.of(highest: .crimson, silence: (.crimson, until)), .silenced(isCritical: true, until: until))
    }

    func testTheMinutesLeftAreRoundedUp() {
        XCTAssertEqual(SilenceButton.minutesLeft(until: until, now: until.addingTimeInterval(-600)), 10)
        XCTAssertEqual(SilenceButton.minutesLeft(until: until, now: until.addingTimeInterval(-599)), 10)
        XCTAssertEqual(SilenceButton.minutesLeft(until: until, now: until.addingTimeInterval(-60)), 1)
        XCTAssertEqual(SilenceButton.minutesLeft(until: until, now: until.addingTimeInterval(-1)), 1)
    }

    func testNoMinuteIsLeftOnceTheSilenceEnded() {
        XCTAssertEqual(SilenceButton.minutesLeft(until: until, now: until), 0)
        XCTAssertEqual(SilenceButton.minutesLeft(until: until, now: until.addingTimeInterval(30)), 0)
    }
}
