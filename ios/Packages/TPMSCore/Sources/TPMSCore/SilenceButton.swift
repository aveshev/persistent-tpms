import Foundation

/// What the button silencing the alerts' sound shows, like the Android app's SilenceAlertsUseCase,
/// see docs/alerts.md. It also ignores the alerts going on, which then no longer keep the screen
/// bright: with the alerts making no sound nor vibration, that's all it does, and only its label says
/// so ([isIgnoring]), silencing and ignoring being one.
public enum SilenceButton: Equatable, Sendable {
    /// No red or critical alert left to say, nor going on
    case idle
    /// Silences the alerts, all of them once [isCritical], told as ignoring them once [isIgnoring]
    case offer(isCritical: Bool, isIgnoring: Bool = false)
    /// The alerts are silent until [until], all of them once [isCritical], told as ignored once [isIgnoring]
    case silenced(isCritical: Bool, until: Date, isIgnoring: Bool = false)

    /// [highest] is the highest level yet to be said, [alerting] the worst alert going on but the
    /// ignored ones, which only a new alert offers again, [silence] the silence going on, [isSoundOff]
    /// the alerts making no sound nor vibration
    public static func of(
        highest spoken: AlertLevel?,
        alerting: AlertLevel? = nil,
        silence: (level: AlertLevel, until: Date)?,
        isSoundOff: Bool = false
    ) -> SilenceButton {
        let highest = [spoken, alerting].compactMap { $0 }.max()
        // A critical alert gets through a silence of the red ones, which can be silenced too
        if highest == .crimson, silence?.level != .crimson { return .offer(isCritical: true, isIgnoring: isSoundOff) }
        if let silence { return .silenced(isCritical: silence.level == .crimson, until: silence.until, isIgnoring: isSoundOff) }
        return highest == .red ? .offer(isCritical: false, isIgnoring: isSoundOff) : .idle
    }

    /// The minutes the button tells are left of a silence, rounded up: 0 only once it ended
    public static func minutesLeft(until: Date, now: Date) -> Int {
        Int((max(until.timeIntervalSince(now), 0) / 60).rounded(.up))
    }
}
