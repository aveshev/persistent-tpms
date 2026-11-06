import Foundation

/// Tells which beacons are around from what's heard of them, like the Android app's
/// BeaconPresenceUseCase. A beacon becomes nearby once [minPackets] of its packets from [minRssi] were
/// heard within [window], and stays so while one of them was: a beacon far away (the neighbour's)
/// doesn't count, nor does a single stray packet, nor one gone quiet.
public struct BeaconPresence {
    public static let window: Double = 30
    private static let minPackets = 2

    private struct Heard {
        /// The last loud ones, the last one first
        var packets: [(rssi: Int, at: Double)]
        var isNearby: Bool
    }

    private var heard = [UUID: Heard]()

    public init() {}

    /// The nearby beacons, with the signal (dBm) of their last loud packet
    public var nearby: [UUID: Int] {
        heard
            .filter { $0.value.isNearby }
            .compactMapValues { $0.packets.first?.rssi }
    }

    public mutating func heard(_ id: UUID, rssi: Int, at now: Double, minRssi: Int = RideTriggers.minRssi) {
        // Without a measured signal, how far it is isn't known
        guard rssi != RideTriggers.noRssi, rssi >= minRssi else { return }
        let packets = Array(([(rssi: rssi, at: now)] + (heard[id]?.packets ?? [])).prefix(Self.minPackets))
        heard[id] = Heard(packets: packets, isNearby: heard[id]?.isNearby == true || packets.count >= Self.minPackets)
    }

    /// Packets stop coming when a beacon goes away: the time passing is what tells it
    public mutating func tick(at now: Double) {
        heard = heard
            .mapValues { Heard(packets: $0.packets.filter { now - $0.at < Self.window }, isNearby: $0.isNearby) }
            .filter { $0.value.packets.isEmpty == false }
    }
}
