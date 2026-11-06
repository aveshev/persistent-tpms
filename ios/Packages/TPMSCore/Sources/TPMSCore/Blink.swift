import Foundation

/// A blinking element's two phases, shown then hidden, in ticks, like Blink.kt. Every blink counts
/// from the epoch, so every element switches together as long as it's redrawn on the same ticks, see
/// `lastTick(before:)`.
public struct Blink: Hashable, Sendable {
    public let first: Int
    public let second: Int

    public static let red = Blink(first: 3, second: 1)
    public static let critical = Blink(first: 1, second: 1)
    /// A crimson reading's alternation with "LOW!" or "HIGH!"
    public static let criticalLabel = Blink(first: 4, second: 4)
    /// An outline waiting for its tyre's sensor
    public static let waiting = Blink(first: 2, second: 2)

    /// In seconds, every phase is a multiple of it
    public static let tick: TimeInterval = 0.2
    /// A redraw comes just after its tick rather than on it, where rounding could count the one before
    private static let margin: TimeInterval = 0.01

    public func isFirstPhase(_ date: Date) -> Bool {
        Int((date.timeIntervalSince1970 / Self.tick).rounded(.down)) % (first + second) < first
    }

    /// Where a `TimelineView`'s periodic schedule starts from for its redraws to fall on every other
    /// blinking element's
    public static func lastTick(before date: Date) -> Date {
        Date(timeIntervalSince1970: ((date.timeIntervalSince1970 - margin) / tick).rounded(.down) * tick + margin)
    }
}

extension AlertLevel {
    /// As fast as a tyre blinks for it, nil for a level which doesn't blink
    public var blink: Blink? {
        switch self {
        case .amber: nil
        case .red: .red
        case .crimson: .critical
        }
    }
}
