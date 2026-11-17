import Foundation

/// Decides when something heard counts as being back near the vehicle, for the "Going for a ride?"
/// notification. A suspended app runs no timer, it only gets a few seconds each time iOS reports
/// something: all this does is compare the times kept here when the next report comes in.
public struct RideTriggers: Codable, Equatable {
    /// How long a trigger stays quiet before being heard again counts as back near the vehicle
    public static let defaultAbsentFor: Double = 30 * 60

    /// How long after the last trigger fired a notification left unopened stops being current: the
    /// next ride gets a banner of its own rather than yesterday's notification
    public static let defaultStaleAfter: Double = 60 * 60

    /// The signal (dBm) from which something counts as nearby, like Android's beacons. A sensor under
    /// a seat with the phone on the rider read -76 dBm at its median on a ride, some of its packets
    /// falling below that: a later one lets it through.
    public static let minRssi = -85

    /// What CoreBluetooth reports when it couldn't measure the signal
    public static let noRssi = 127

    /// A second report this long after the first one rules out two copies of the same packet
    private static let confirmedAfter: Double = 5
    /// A second report later than this starts over, the first one was somebody walking past
    private static let confirmedWithin: Double = 5 * 60

    public static let beacon = "beacon"
    public static func sensors(of vehicle: UUID) -> String { vehicle.uuidString }

    /// When each trigger was last heard nearby
    public private(set) var lastHeard = [String: Double]()
    /// The first report of a trigger after its absence, waiting for a second one
    private var firstHeard = [String: Double]()
    private var lastFired: Double?

    public init() {}

    /// Whether hearing [source] now counts as back near the vehicle: it had never been heard, or not for
    /// [absentFor]. Only a signal from [minRssi] counts as hearing it at all, so a bike parked out of
    /// reach of the house reads as absent, and going to it as coming back.
    ///
    /// With [needsConfirming] it takes a second report: a single one can be a stray packet of a
    /// device always advertising, or someone walking past it.
    public mutating func heard(_ source: String, rssi: Int, needsConfirming: Bool = false, at now: Double, absentFor: Double = defaultAbsentFor) -> Bool {
        guard rssi != Self.noRssi, rssi >= Self.minRssi else { return false }
        let wasAbsent = lastHeard[source].map { now - $0 >= absentFor } ?? true
        lastHeard[source] = now
        guard needsConfirming else {
            firstHeard[source] = nil
            return wasAbsent
        }
        if wasAbsent {
            firstHeard[source] = now
            return false
        }
        guard let first = firstHeard[source] else { return false }
        if now - first > Self.confirmedWithin {
            firstHeard[source] = now
            return false
        }
        guard now - first >= Self.confirmedAfter else { return false }
        firstHeard[source] = nil
        return true
    }

    /// [source] was known to be there until now, its connection coming as soon as it was allowed to
    public mutating func stillThere(_ source: String, at now: Double) {
        lastHeard[source] = now
    }

    /// A trigger fired, whichever one. Whether a new notification should go out: not while one
    /// [isShowing] is still current, so two triggers firing for one return, or one coming back and
    /// forth, make a single notification. Each firing keeps it current for another [staleAfter].
    public mutating func fired(at now: Double, isShowing: Bool, staleAfter: Double = defaultStaleAfter) -> Bool {
        let isCurrent = isShowing && lastFired.map { now - $0 < staleAfter } ?? false
        lastFired = now
        return isCurrent == false
    }

    /// Whether the notification should come down, for nothing having fired for [staleAfter]. Says so
    /// once, a suspended app having only its next report to remove it from.
    public mutating func expired(at now: Double, staleAfter: Double = defaultStaleAfter) -> Bool {
        guard let lastFired, now - lastFired >= staleAfter else { return false }
        self.lastFired = nil
        return true
    }

    /// A trigger turned off and on again starts over, its first report counting as back near the vehicle
    public mutating func forget(_ source: String) {
        lastHeard[source] = nil
        firstHeard[source] = nil
    }

    public mutating func forgetSensors() {
        for source in lastHeard.keys where source != Self.beacon { forget(source) }
    }
}
