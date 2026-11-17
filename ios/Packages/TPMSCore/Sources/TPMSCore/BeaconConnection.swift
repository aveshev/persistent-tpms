import Foundation

/// A connection requested from a beacon woken by connecting, stored since iOS may end the app before
/// it completes. iOS only starts trying once [delay] ended, with the app suspended all along: when it
/// completes tells whether the beacon was away meanwhile.
public struct BeaconConnection: Codable, Equatable {
    /// How long after a connection ended the next one is requested. Left to iOS with
    /// `CBConnectPeripheralOptionStartDelayKey`, so a suspended app needs no timer. Locked and with the
    /// app suspended, an iPhone 13 Pro on iOS 18 connected within 5 s of 2 and 5 minute delays, by day
    /// and overnight. A trip shorter than that can go unnoticed, and a prompt can be late by up to it.
    public static let interval: Double = 120

    /// How long after its delay ended a connection counts as having been possible all along. It's
    /// about what iOS takes to connect to a beacon in range, while the app is suspended.
    public static let margin: Double = 120

    /// How many reads in a row can fail to tell the beacon near before it counts as having left. A
    /// beacon sitting still reads a few dB either side of [RideTriggers.minRssi] from one connection to
    /// the next: one weak read between two good ones is no trip.
    public static let toleratedMisses = 1

    /// What reading a connected beacon's signal said
    public enum Read: Equatable {
        case near(rssi: Int)
        case far
        /// No signal measured. iOS sometimes reports 0 dBm with no error, which no beacon reads.
        case unmeasured
    }

    /// What a completed connection says about the beacon
    public enum Outcome: Equatable {
        /// Probably in range since its last connection: no prompt
        case stillThere
        /// Heard nearby, after having been away or for the first time
        case heard(rssi: Int)
        /// Nothing to tell
        case nothing
    }

    public let requestedAt: Double
    public let delay: Double
    /// The beacon was detected nearby right before this was requested, or only missed [misses] times
    public let wasNear: Bool
    /// The reads in a row which couldn't tell it near, since it last was
    public let misses: Int

    public init(requestedAt: Double, delay: Double, wasNear: Bool, misses: Int = 0) {
        self.requestedAt = requestedAt
        self.delay = delay
        self.wasNear = wasNear
        self.misses = misses
    }

    /// Stored before [misses] existed
    public init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        requestedAt = try container.decode(Double.self, forKey: .requestedAt)
        delay = try container.decode(Double.self, forKey: .delay)
        wasNear = try container.decode(Bool.self, forKey: .wasNear)
        misses = try container.decodeIfPresent(Int.self, forKey: .misses) ?? 0
    }

    public var dueAt: Double { requestedAt + delay }

    public static func read(rssi: Int, failed: Bool) -> Read {
        failed || rssi == 0 || rssi == RideTriggers.noRssi ? .unmeasured : rssi >= RideTriggers.minRssi ? .near(rssi: rssi) : .far
    }

    /// What a connection completing [now] with [read] says, and how the next one is to be requested
    public func completed(with read: Read, at now: Double) -> (outcome: Outcome, wasNear: Bool, misses: Int) {
        // A trip shorter than the delay can't be told apart
        let wasThereAllAlong = wasNear && now - dueAt < Self.margin
        switch read {
        case let .near(rssi):
            return (wasThereAllAlong ? .stillThere : .heard(rssi: rssi), true, 0)
        case .far, .unmeasured:
            return wasThereAllAlong && misses < Self.toleratedMisses
                ? (.nothing, true, misses + 1)
                : (.nothing, false, 0)
        }
    }
}
