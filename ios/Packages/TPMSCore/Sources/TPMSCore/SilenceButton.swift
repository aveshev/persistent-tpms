import Foundation

/// What the button silencing the alerts' sound shows, like the Android app's SilenceAlertsUseCase,
/// see docs/alerts.md. It also ignores the alerts going on for that while, which then no longer keep
/// the screen bright: with nothing to say, the sound and vibration off or the speech over, that's all
/// it does, and it says so ([isIgnoring]).
public enum SilenceButton: Equatable, Sendable {
    /// No red or critical alert left to say, nor going on
    case idle
    /// Silences the alerts, all of them once [isCritical], or only ignores them once [isIgnoring]
    case offer(isCritical: Bool, isIgnoring: Bool = false)
    /// The alerts are silent until [until], all of them once [isCritical], ignored once [isIgnoring]
    case silenced(isCritical: Bool, until: Date, isIgnoring: Bool = false)

    /// [highest] is the highest level yet to be said, [alerting] the worst alert going on, [silence]
    /// the silence going on, [isSoundOff] the alerts making no sound nor vibration
    public static func of(
        highest spoken: AlertLevel?,
        alerting: AlertLevel? = nil,
        silence: (level: AlertLevel, until: Date)?,
        isSoundOff: Bool = false
    ) -> SilenceButton {
        let highest = [spoken, alerting].compactMap { $0 }.max()
        // Only ignored when there's no speech to silence: none of the alerts at the level offered
        let isIgnoring = isSoundOff || (spoken ?? .amber) < (highest ?? .amber)
        // A critical alert gets through a silence of the red ones, which can be silenced too
        if highest == .crimson, silence?.level != .crimson { return .offer(isCritical: true, isIgnoring: isIgnoring) }
        // Silent alerts have nothing left to tell once they're over
        if let silence, isSoundOff == false || highest != nil {
            return .silenced(isCritical: silence.level == .crimson, until: silence.until, isIgnoring: isSoundOff)
        }
        return highest == .red ? .offer(isCritical: false, isIgnoring: isIgnoring) : .idle
    }

    /// The minutes the button tells are left of a silence, rounded up: 0 only once it ended
    public static func minutesLeft(until: Date, now: Date) -> Int {
        Int((max(until.timeIntervalSince(now), 0) / 60).rounded(.up))
    }
}
