import Foundation

/// The red and critical alerts going on, what the vehicle menu blinks for, like Android's
/// AlertSpeaker.ongoing, see docs/alerts.md. Follows what the speech says, but isn't silenced or
/// turned off with it. An alert starts from a reading just heard, never from a stored one, and ends
/// with a reading lowering it or with the scan.
///
/// Android's also ends with its notification dismissed. Nothing dismisses one here, so a sensor not
/// heard for `heldFor` doesn't count any more: one left flat, or away from the phone, would otherwise
/// blink for as long as the app runs.
public struct OngoingAlerts: Hashable, Sendable {
    public struct Tyre: Hashable, Sendable {
        public let vehicleId: UUID
        public let location: TyreLocation

        public init(vehicleId: UUID, location: TyreLocation) {
            self.vehicleId = vehicleId
            self.location = location
        }
    }

    private struct Alert: Hashable, Sendable {
        let level: AlertLevel
        let heardAt: Double
    }

    private var alerts: [Tyre: Alert] = [:]

    /// How long an alert keeps counting after its sensor's last reading
    public static let heldFor: Double = 30 * 60

    public init() {}

    /// After a reading of [tyre] just heard, from its `TyreAlerts.notifiable`: a single low battery
    /// voltage can be a dip, it only counts once confirmed
    public func next(_ tyre: Tyre, levels: [AlertClass: AlertLevel], at timestamp: Double) -> OngoingAlerts {
        var next = self
        next.alerts[tyre] = levels.values.max().flatMap { $0 >= .red ? Alert(level: $0, heardAt: timestamp) : nil }
        return next
    }

    /// The thresholds or the sensors changed: every alert comes down to its tyre's [levels] at once, and
    /// ends without them. Never raised, nor started: that takes a new reading.
    public func reevaluated(_ levels: (Tyre) -> [AlertClass: AlertLevel]?) -> OngoingAlerts {
        var next = self
        next.alerts = alerts.reduce(into: [:]) { result, entry in
            guard let worst = levels(entry.key)?.values.max(), worst >= .red else { return }
            result[entry.key] = Alert(level: min(worst, entry.value.level), heardAt: entry.value.heardAt)
        }
        return next
    }

    /// The worst alert of the [vehicleId] vehicle, nil without any
    public func worst(of vehicleId: UUID, now: Date) -> AlertLevel? {
        worst(now: now) { $0 == vehicleId }
    }

    /// The worst alert of the vehicles other than [vehicleId], nil without any
    public func worst(otherThan vehicleId: UUID?, now: Date) -> AlertLevel? {
        worst(now: now) { $0 != vehicleId }
    }

    private func worst(now: Date, where isIncluded: (UUID) -> Bool) -> AlertLevel? {
        alerts
            .filter { isIncluded($0.key.vehicleId) && now.timeIntervalSince1970 - $0.value.heardAt < Self.heldFor }
            .map(\.value.level)
            .max()
    }
}
