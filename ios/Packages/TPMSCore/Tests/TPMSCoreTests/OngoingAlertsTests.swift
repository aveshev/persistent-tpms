import XCTest
@testable import TPMSCore

final class OngoingAlertsTests: XCTestCase {
    private let shown = UUID()
    private let other = UUID()
    private let now = Date(timeIntervalSince1970: 1_000_000)

    private func tyre(_ vehicleId: UUID, _ location: TyreLocation = .wheel(.frontLeft)) -> OngoingAlerts.Tyre {
        OngoingAlerts.Tyre(vehicleId: vehicleId, location: location)
    }

    private func alerts(_ vehicleId: UUID, _ levels: [AlertClass: AlertLevel], _ location: TyreLocation = .wheel(.frontLeft)) -> OngoingAlerts {
        OngoingAlerts().next(tyre(vehicleId, location), levels: levels, at: now.timeIntervalSince1970)
    }

    func testOnlyAnotherVehicleAlertingCounts() {
        XCTAssertNil(alerts(shown, [.pressure: .crimson]).worst(otherThan: shown, now: now))
        XCTAssertEqual(alerts(other, [.pressure: .crimson]).worst(otherThan: shown, now: now), .crimson)
    }

    func testAmberDoesNotCount() {
        XCTAssertNil(alerts(other, [.pressure: .amber, .pressureLoss: .amber, .sensorAlarm: .amber]).worst(otherThan: shown, now: now))
    }

    func testEveryClassAtRedCountsTheBatteryIncluded() {
        XCTAssertEqual(alerts(other, [.battery: .red]).worst(otherThan: shown, now: now), .red)
        XCTAssertEqual(alerts(other, [.temperature: .red]).worst(otherThan: shown, now: now), .red)
    }

    func testCrimsonWinsAcrossClassesTyresAndVehicles() {
        XCTAssertEqual(alerts(other, [.battery: .red, .pressure: .crimson]).worst(otherThan: shown, now: now), .crimson)
        let third = UUID()
        let ongoing = alerts(other, [.pressure: .red])
            .next(tyre(other, .wheel(.rearLeft)), levels: [.temperature: .crimson], at: now.timeIntervalSince1970)
            .next(tyre(third), levels: [.pressure: .red], at: now.timeIntervalSince1970)
        XCTAssertEqual(ongoing.worst(otherThan: shown, now: now), .crimson)
        XCTAssertEqual(ongoing.worst(of: third, now: now), .red)
    }

    func testAReadingLoweringOrClearingItEndsIt() {
        let ongoing = alerts(other, [.pressure: .crimson])
        let timestamp = now.timeIntervalSince1970
        XCTAssertEqual(ongoing.next(tyre(other), levels: [.pressure: .red], at: timestamp).worst(otherThan: shown, now: now), .red)
        XCTAssertNil(ongoing.next(tyre(other), levels: [.pressure: .amber], at: timestamp).worst(otherThan: shown, now: now))
        XCTAssertNil(ongoing.next(tyre(other), levels: [:], at: timestamp).worst(otherThan: shown, now: now))
    }

    func testASensorNotHeardStopsCountingAfterTheHoldUntilHeardAgain() {
        let ongoing = alerts(other, [.pressure: .red])
        let held = now.addingTimeInterval(OngoingAlerts.heldFor - 1)
        let over = now.addingTimeInterval(OngoingAlerts.heldFor)
        XCTAssertEqual(ongoing.worst(otherThan: shown, now: held), .red)
        XCTAssertNil(ongoing.worst(otherThan: shown, now: over))
        XCTAssertEqual(ongoing.next(tyre(other), levels: [.pressure: .red], at: over.timeIntervalSince1970).worst(otherThan: shown, now: over), .red)
    }

    func testReevaluatingLowersOrEndsButNeverRaisesOrStarts() {
        let ongoing = alerts(other, [.pressure: .red])
        XCTAssertNil(ongoing.reevaluated { _ in [.pressure: .amber] }.worst(otherThan: shown, now: now))
        // A tyre left without a sensor, or a vehicle deleted
        XCTAssertNil(ongoing.reevaluated { _ in nil }.worst(otherThan: shown, now: now))
        XCTAssertEqual(ongoing.reevaluated { _ in [.pressure: .crimson] }.worst(otherThan: shown, now: now), .red)
        XCTAssertEqual(alerts(other, [.pressure: .crimson]).reevaluated { _ in [.pressure: .red] }.worst(otherThan: shown, now: now), .red)
        XCTAssertNil(OngoingAlerts().reevaluated { _ in [.pressure: .crimson] }.worst(otherThan: shown, now: now))
    }
}

final class BlinkTests: XCTestCase {
    /// The phases over a cycle, at each redraw of a schedule started anywhere
    private func phases(_ blink: Blink, from start: TimeInterval) -> [Bool] {
        let first = Blink.lastTick(before: Date(timeIntervalSince1970: start)).timeIntervalSince1970
        return (0..<(blink.first + blink.second)).map {
            blink.isFirstPhase(Date(timeIntervalSince1970: first + Double($0) * Blink.tick))
        }
    }

    func testEachLevelBlinksLikeItsTyres() {
        XCTAssertNil(AlertLevel.amber.blink)
        XCTAssertEqual(AlertLevel.red.blink, .red)
        XCTAssertEqual(AlertLevel.crimson.blink, .critical)
    }

    func testAPhaseLastsAsLongOnEveryRedraw() {
        // Around today's date too, where a Double's rounding matters most
        for base in [0.0, 1_793_000_000.0, 1_793_000_000.123, 1_793_000_000.2, 1_793_000_000.79999] {
            for (blink, on) in [(Blink.red, 3), (.critical, 1), (.criticalLabel, 4), (.waiting, 2)] {
                XCTAssertEqual(phases(blink, from: base + 1).filter { $0 }.count, on, "\(blink) from \(base)")
            }
        }
    }

    func testRedrawsStartedApartFallOnTheSameTicks() {
        let date = Date(timeIntervalSince1970: 1_793_000_000.5)
        let one = Blink.lastTick(before: date).timeIntervalSince1970
        let other = Blink.lastTick(before: date.addingTimeInterval(7.13)).timeIntervalSince1970
        XCTAssertEqual(((other - one) / Blink.tick).rounded(), (other - one) / Blink.tick, accuracy: 0.001)
    }

    func testLastTickIsNotAfterTheDate() {
        for offset in stride(from: 0.0, to: 0.4, by: 0.003) {
            let date = Date(timeIntervalSince1970: 1_793_000_000 + offset)
            XCTAssertLessThanOrEqual(Blink.lastTick(before: date), date)
            XCTAssertLessThan(date.timeIntervalSince(Blink.lastTick(before: date)), Blink.tick + 0.001)
        }
    }

    func testBlinksCountFromTheEpochSoAShorterCycleStartsWithALongerOne() {
        let start = Date(timeIntervalSince1970: 1_793_000_000.01)
        // 1_793_000_000 s is 8_965_000_000 ticks, a multiple of the 4 and the 2 ticks cycles
        XCTAssertTrue(Blink.red.isFirstPhase(start))
        XCTAssertTrue(Blink.critical.isFirstPhase(start))
    }
}
