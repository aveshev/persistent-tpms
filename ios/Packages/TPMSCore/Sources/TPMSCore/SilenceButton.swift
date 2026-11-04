/// What the button silencing the alerts' sound shows, like the Android app's SilenceAlertsUseCase,
/// see docs/alerts.md
public enum SilenceButton: Equatable, Sendable {
    /// No red or critical alert left to say
    case idle
    /// Silences the alerts, all of them once [isCritical]
    case offer(isCritical: Bool)
    /// The alerts are silent for a while, all of them once [isCritical]
    case silenced(isCritical: Bool)

    /// [highest] is the highest level yet to be said, [silenced] the level of the silence going on
    public static func of(highest: AlertLevel?, silenced: AlertLevel?) -> SilenceButton {
        // A critical alert gets through a silence of the red ones, which can be silenced too
        if highest == .crimson, silenced != .crimson { return .offer(isCritical: true) }
        if let silenced { return .silenced(isCritical: silenced == .crimson) }
        return highest == .red ? .offer(isCritical: false) : .idle
    }
}
