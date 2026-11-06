import Foundation

/// What the button silencing the alerts' sound shows, like the Android app's SilenceAlertsUseCase,
/// see docs/alerts.md
public enum SilenceButton: Equatable, Sendable {
    /// No red or critical alert left to say
    case idle
    /// Silences the alerts, all of them once [isCritical]
    case offer(isCritical: Bool)
    /// The alerts are silent until [until], all of them once [isCritical]
    case silenced(isCritical: Bool, until: Date)

    /// [highest] is the highest level yet to be said, [silence] the silence going on
    public static func of(highest: AlertLevel?, silence: (level: AlertLevel, until: Date)?) -> SilenceButton {
        // A critical alert gets through a silence of the red ones, which can be silenced too
        if highest == .crimson, silence?.level != .crimson { return .offer(isCritical: true) }
        if let silence { return .silenced(isCritical: silence.level == .crimson, until: silence.until) }
        return highest == .red ? .offer(isCritical: false) : .idle
    }

    /// The minutes the button tells are left of a silence, rounded up: 0 only once it ended
    public static func minutesLeft(until: Date, now: Date) -> Int {
        Int((max(until.timeIntervalSince(now), 0) / 60).rounded(.up))
    }
}
