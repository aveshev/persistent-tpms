import Foundation

/// The alerts dismissed for a while, by sensor and class, like the Android app's AlertSnoozeUseCase.
/// Binding a replacement sensor escapes them: the cause has most likely been dealt with at the same
/// time. They only silence the notifications and the speech, and survive the condition clearing.
public struct AlertSnoozes: Codable, Equatable {
    private var until = [String: Double]()

    public init() {}

    /// Whether [level], and so every lower level, of [alertClass] is dismissed for [sensorId]
    public func isSnoozed(sensorId: Int, _ alertClass: AlertClass, _ level: AlertLevel, at now: Double) -> Bool {
        (until[Self.key(sensorId, alertClass, level)] ?? 0) > now
    }

    /// Dismisses [level] and the lower levels of [alertClass] for [duration], unless they already are
    /// for longer. A higher level still alerts.
    public mutating func snooze(sensorId: Int, _ alertClass: AlertClass, _ level: AlertLevel, for duration: Double, at now: Double) {
        // The ended ones would pile up with each sensor ever bound
        until = until.filter { $0.value > now }
        for lower in AlertLevel.allCases where lower <= level {
            let key = Self.key(sensorId, alertClass, lower)
            until[key] = max(until[key] ?? 0, now + duration)
        }
    }

    private static func key(_ sensorId: Int, _ alertClass: AlertClass, _ level: AlertLevel) -> String {
        "\(sensorId)/\(alertClass.rawValue)/\(level.rawValue)"
    }
}

extension AlertLevel {
    /// What a notification offers to dismiss it for, in seconds, the shortest first: swiping it away
    /// counts as that one
    public var snoozes: [Double] {
        switch self {
        case .amber: [24 * 3600, 7 * 24 * 3600]
        case .red, .crimson: [10 * 60, 24 * 3600]
        }
    }
}
