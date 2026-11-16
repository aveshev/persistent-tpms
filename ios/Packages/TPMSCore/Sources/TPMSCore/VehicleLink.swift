import Foundation

/// A link opening the app on a vehicle, `tpmsadvanced://vehicle/<uuid>`: the Android app's, which
/// its launcher shortcuts and alert notifications carry.
public enum VehicleLink {
    public static let scheme = "tpmsadvanced"
    public static let host = "vehicle"

    /// With the UUID in lowercase, as Android writes it
    public static func url(for id: UUID) -> URL {
        URL(string: "\(scheme)://\(host)/\(id.uuidString.lowercased())")!
    }

    /// The vehicle of a link, nil for anything else: another scheme or host, no UUID, or more after it
    public static func vehicleId(from url: URL) -> UUID? {
        guard url.scheme?.lowercased() == scheme, url.host?.lowercased() == host, url.path.hasPrefix("/") else { return nil }
        return UUID(uuidString: String(url.path.dropFirst()))
    }
}

/// A vehicle's Home Screen quick action, like the Android app's launcher shortcut (ShortcutUseCase)
public struct VehicleShortcut: Equatable, Sendable {
    public let id: UUID
    public let name: String
    public let kind: VehicleKind

    public init(id: UUID, name: String, kind: VehicleKind) {
        self.id = id
        self.name = name
        self.kind = kind
    }

    /// [limit] at most, in the order the Android app ranks its shortcuts: the vehicles with a red or
    /// critical alert in [alerting] first, the worst first, then the latest read in [latestReadings]
    /// (seconds since 1970), then the others by name. One without a name is skipped, as on Android:
    /// it would show as an empty line.
    public static func list(
        _ vehicles: [Vehicle],
        alerting: [UUID: AlertLevel] = [:],
        latestReadings: [UUID: Double] = [:],
        limit: Int
    ) -> [VehicleShortcut] {
        func alert(_ vehicle: Vehicle) -> AlertLevel? { alerting[vehicle.id].flatMap { $0 >= .red ? $0 : nil } }
        return vehicles
            .filter { $0.name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty == false }
            .sorted { a, b in
                switch (alert(a), alert(b)) {
                case let (x?, y?) where x != y: return x > y
                case (.some, nil): return true
                case (nil, .some): return false
                default: break
                }
                switch (latestReadings[a.id], latestReadings[b.id]) {
                case let (x?, y?) where x != y: return x > y
                case (.some, nil): return true
                case (nil, .some): return false
                default: return a.name < b.name
                }
            }
            .prefix(max(limit, 0))
            .map { VehicleShortcut(id: $0.id, name: $0.name, kind: $0.kind) }
    }
}
