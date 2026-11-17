import Foundation

/// What a tyre's new reading does to the notification of one of its classes, like the Android app's
/// AlertNotifier, see docs/alerts.md
public enum AlertAction: Equatable, Sendable {
    case none
    case cancel
    case post(AlertLevel, sound: Bool)
    /// Keeps the notification as it is until its hold ends, the reading would lower it to this level
    case hold(AlertLevel?)

    /// What the notification of a class shows
    public struct Shown: Equatable, Sendable {
        public let sensorId: Int
        public let level: AlertLevel

        public init(sensorId: Int, level: AlertLevel) {
            self.sensorId = sensorId
            self.level = level
        }
    }

    /// A value hovering on a boundary while riding, read about once a minute, doesn't flicker
    public static let hold: Double = 3 * 60

    /// A reading raising a notification, new or to a higher level, waits this long: the sensors send a
    /// few readings seconds apart when the pressure changes, and a single one raising it followed by
    /// one which doesn't is a value on a boundary
    public static let raiseDelay: Double = 5

    /// What tells the tyre may be leaking, "Leak?" on the main screen
    private static let leaks: Set<AlertClass> = [.pressureLoss, .sensorAlarm]

    /// A level is posted unless dismissed for a while, and holds the notification at it [isHeld]: a
    /// reading lowering it or clearing it meanwhile waits until the hold ends, a reading at that level
    /// or above starts it over. It sounds when it's new, higher than shown, or red or crimson again: a
    /// lower level updates silently. A leak isn't notified on its own while the tyre's pressure is red
    /// or crimson, it's told in the pressure's notification, see [TyreAlerts.foldsLeaks].
    public static func of(_ alerts: TyreAlerts, _ alertClass: AlertClass, shown: Shown?, isHeld: Bool, isSnoozed: (AlertLevel) -> Bool) -> AlertAction {
        let level = alerts.notifiable[alertClass]
        // At once rather than after the hold, the pressure's notification tells it now
        if leaks.contains(alertClass), alerts.foldsLeaks { return shown == nil ? .none : .cancel }
        guard let shown else {
            return level.flatMap { isSnoozed($0) ? nil : .post($0, sound: true) } ?? .none
        }
        // Another sensor was bound, whatever the previous one alerted for was likely seen to
        if shown.sensorId != alerts.latest?.sensorId {
            return level.flatMap { isSnoozed($0) ? nil : .post($0, sound: true) } ?? .cancel
        }
        if let level, level >= shown.level {
            return isSnoozed(level) ? .none : .post(level, sound: level > shown.level || level >= .red)
        }
        if isHeld { return .hold(level) }
        return level.flatMap { isSnoozed($0) ? nil : .post($0, sound: false) } ?? .cancel
    }

    /// What re-evaluating the latest reading under other thresholds does to a [shown] notification:
    /// lowered silently or cleared, the hold notwithstanding. Never raised nor posted, a threshold
    /// change doesn't alert about a reading already stored.
    public static func reevaluation(_ alerts: TyreAlerts, _ alertClass: AlertClass, shown: Shown, isSnoozed: (AlertLevel) -> Bool) -> AlertAction {
        // About another sensor than the latest reading's, left to the next reading
        if shown.sensorId != alerts.latest?.sensorId { return .none }
        if leaks.contains(alertClass), alerts.foldsLeaks { return .cancel }
        guard let level = alerts.notifiable[alertClass], isSnoozed(level) == false else { return .cancel }
        return level > shown.level ? .none : .post(level, sound: false)
    }

    /// Whether this, for a reading of [sensorId], raises the [shown] notification: new or higher
    public func raises(shown: Shown?, sensorId: Int) -> Bool {
        guard case let .post(level, _) = self else { return false }
        return shown == nil || shown?.sensorId != sensorId || level > (shown?.level ?? level)
    }
}

extension TyreAlerts {
    /// The pressure being red or crimson already says more than a leak's amber: another notification
    /// would only be one more sound and one more thing to dismiss
    public var foldsLeaks: Bool {
        notifiable[.pressure].map { $0 >= .red } ?? false
    }
}
