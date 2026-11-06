import Foundation

/// The order of the page choosing a beacon, like the Android app's page adding one: a device listed
/// once it could be a beacon, staying greyed out if it no longer can, in an order that only changes
/// when asked, so that rows don't move under the finger as signals change.
public struct BeaconList: Equatable {
    public struct Device: Equatable {
        public let id: UUID
        /// The median signal of its last packets, in dBm
        public let rssi: Int
        /// Could be a beacon right now: loud enough, and heard lately
        public let isCandidate: Bool

        public init(id: UUID, rssi: Int, isCandidate: Bool) {
            self.id = id
            self.rssi = rssi
            self.isCandidate = isCandidate
        }
    }

    /// How far under the threshold a strong device's signal falls before it becomes weak, in dB: one
    /// hovering around it would keep turning on and off otherwise
    private static let hysteresis = 5

    public static func isStrong(rssi: Int, wasStrong: Bool, minRssi: Int = RideTriggers.minRssi) -> Bool {
        rssi >= minRssi || (wasStrong && rssi >= minRssi - hysteresis)
    }

    /// What's listed, until the next [sort]
    public private(set) var order = [UUID]()
    /// Every device ever a candidate
    public private(set) var qualified = Set<UUID>()
    /// Whether sorting would change nothing. Stays false until the next [sort] once it was, rather
    /// than flickering as signals hover around each other's.
    public private(set) var isSorted = true
    private var latest = [Device]()

    public init() {}

    /// [devices] in the order they were found, which sorting keeps between equal ones
    public mutating func update(_ devices: [Device]) {
        latest = devices
        qualified.formUnion(devices.filter(\.isCandidate).map(\.id))
        if sorted() != order { isSorted = false }
    }

    /// The candidates first then the greyed out ones, each the loudest first, the new ones included
    public mutating func sort() {
        order = sorted()
        isSorted = true
    }

    /// The devices which became candidates since the last [sort]
    public var notShown: Set<UUID> {
        qualified.subtracting(order)
    }

    private func sorted() -> [UUID] {
        latest
            .filter { qualified.contains($0.id) }
            .enumerated()
            .sorted { a, b in
                (a.element.isCandidate ? 0 : 1, -a.element.rssi, a.offset) < (b.element.isCandidate ? 0 : 1, -b.element.rssi, b.offset)
            }
            .map(\.element.id)
    }
}
