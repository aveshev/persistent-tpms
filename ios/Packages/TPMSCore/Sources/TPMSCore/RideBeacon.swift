import Foundation

/// A Bluetooth device chosen to trigger the ride notification, as far as iOS tells: no address, an
/// identifier of this iPhone's own instead, and only what it advertises.
public struct RideBeacon: Codable, Equatable, Identifiable {
    /// How a device can wake a suspended app. In the background iOS only reports a peripheral whose
    /// service UUID it was told to scan for, or one it was told to connect to.
    public enum Wake: Equatable {
        /// Its advertised service UUID added to the background scan, the first choice: every report
        /// carries a signal strength, and it takes nothing away from the app that owns the device
        case service
        /// A connection left pending by its identifier, for a device advertising no service
        case connection
    }

    public let id: UUID
    public let name: String?
    public let services: [String]
    public let isConnectable: Bool
    /// The user's name for it, shown instead of the advertised one
    public var label: String?

    public init(id: UUID, name: String?, services: [String], isConnectable: Bool, label: String? = nil) {
        self.id = id
        self.name = name
        self.services = services
        self.isConnectable = isConnectable
        self.label = label
    }

    /// Nil for a device which can't: nothing but a name or manufacturer data, not connectable
    public var wake: Wake? {
        services.isEmpty == false ? .service : isConnectable ? .connection : nil
    }

    /// Whether it can be told again once its identifier changed, which is what a private address
    /// rotating does and can't be seen from here: by its name or its own service. Such a device would
    /// work on the day it's picked, then go quiet with no explanation.
    public var isRecognisable: Bool {
        name != nil || services.contains(where: Self.isOwnService)
    }

    /// Whether an advertisement comes from this beacon. Its identifier first. A device rotating its
    /// private address gets a new identifier each time: its name or its own service still tell it.
    public func isSending(id: UUID, name: String?, services: [String]) -> Bool {
        id == self.id
            || (self.name != nil && name == self.name)
            || services.contains { Self.isOwnService($0) && self.services.contains($0) }
    }

    /// The typical time between two of a device's packets, from the times (seconds) they were
    /// received, nil until a few were heard. Like the Android app's.
    public static func broadcastPeriod(_ timestamps: [Double]) -> Double? {
        // The same packet received twice, or on another advertising channel
        let pairs = zip(timestamps, timestamps.dropFirst()).filter { $0.1 > $0.0 }
        // Some devices send bursts of packets a few ms apart then go quiet: what matters is the time
        // between two bursts' starts. Never quiet for that long: always sending this fast.
        let ends = pairs.filter { $0.1 - $0.0 >= sameBurst }.map(\.1)
        let starts = ends.isEmpty ? [] : (timestamps.first.map { [$0] } ?? []) + ends
        let gaps = ends.isEmpty
            ? pairs.map { $0.1 - $0.0 }
            : zip(starts, starts.dropFirst()).map { $0.1 - $0.0 }
        // Missed packets make some gaps a multiple of the period: the median ignores them
        return gaps.count >= minGaps ? gaps.sorted()[gaps.count / 2] : nil
    }

    /// Packets closer than that belong to the same burst
    private static let sameBurst = 0.05
    private static let minGaps = 3

    /// A 16-bit UUID stands for a whole kind of device (0x180F, any battery) when a 128-bit one is the
    /// device maker's
    private static func isOwnService(_ uuid: String) -> Bool {
        uuid.count > 4
    }
}
