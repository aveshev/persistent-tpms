import Foundation

/// A sensor of a Sysgration kit's QR code, at the wheel its kit puts it
public struct QrCodeSensor: Hashable, Sendable {
    public let id: Int
    public let wheel: SensorLocation
}

/// A sensor another phone shared, with its calibration since it's the sensor which reads too high
/// or too low
public struct SharedSensor: Hashable, Sendable {
    public let id: Int
    public let location: TyreLocation
    public let brand: SensorBrand
    public let isCalibrated: Bool
    public let offsetKpa: Float
    public let multiplier: Float

    public init(id: Int, location: TyreLocation, brand: SensorBrand, isCalibrated: Bool, offsetKpa: Float, multiplier: Float) {
        self.id = id
        self.location = location
        self.brand = brand
        self.isCalibrated = isCalibrated
        self.offsetKpa = offsetKpa
        self.multiplier = multiplier
    }

    /// The calibration is on and changes the readings
    public var adjusts: Bool { isCalibrated && (offsetKpa != 0 || multiplier != 1) }
}

/// A vehicle's sensors, shown by one phone as a QR code for another one to assign them all at once,
/// like the Android app's SharedSensors. Its format is the same on both.
public struct SharedSensors: Hashable, Sendable {
    public let kind: VehicleKind
    public let name: String
    public let sensors: [SharedSensor]

    public init(kind: VehicleKind, name: String, sensors: [SharedSensor]) {
        self.kind = kind
        self.name = name
        self.sensors = sensors
    }

    /// The code's content, like Android's SharedSensors.uri: a URI so a QR code reader shows what it is
    public var uri: String { QrCodeResult.uri(of: self) }
}

/// What a scanned QR code holds, like the Android app's QrCodeResult
public enum QrCodeResult: Hashable, Sendable {
    /// A Sysgration kit's 2 or 4 sensors
    case sensors([QrCodeSensor])
    /// Different sensors of a kit's code on these wheels
    case duplicateWheelLocation([SensorLocation])
    case duplicateId([Int])
    /// A Wicarlink code holds a single sensor, assigned to the wheel the scan was started from
    case wicarlink(Int)
    /// Another phone's sensors, for the vehicle the scan was started from
    case shared(SharedSensors)
    /// Sensors shared by a newer version of the app, in a format this one can't read
    case sharedByNewerApp
    /// Sensors shared by another phone, but the code is damaged
    case sharedUnreadable

    private static let sharedPrefix = "tpmsadvanced://sensors?"

    // A code is its index in these, the same as Android's: only ever append to them
    private static let kinds: [VehicleKind] = [.car, .singleAxleTrailer, .motorcycle, .tadpoleThreeWheeler, .deltaThreeWheeler, .carWithSpare, .monowheel]
    private static let brands: [SensorBrand] = [.pecham, .bekubeeKy, .wicarlink, .bekubeeTpms, .sysgration, .bekubeeTcTpms]
    private static let locations: [TyreLocation] = [
        .wheel(.frontLeft), .wheel(.frontRight), .wheel(.rearLeft), .wheel(.rearRight),
        .axle(.front), .axle(.rear), .side(.left), .side(.right), .spare, .single,
    ]

    /// What [code] holds, nil for a code that isn't a TPMS one. The kits' formats look for their
    /// IDs anywhere in a code.
    public static func parse(_ code: String) -> QrCodeResult? {
        if code.hasPrefix(sharedPrefix) { return parseShared(code) }
        if let ids = firstMatch(of: "([0-9a-fA-F]{6})&([0-9a-fA-F]{6})&([0-9a-fA-F]{6})&([0-9a-fA-F]{6})", in: code)
            ?? firstMatch(of: "([0-9a-fA-F]{6})&([0-9a-fA-F]{6})", in: code) {
            return sysgration(ids)
        }
        // Printed the way the cap reads, the ID's most significant byte first
        return firstMatch(of: "^([0-9a-fA-F]{6})$", in: code.trimmingCharacters(in: .whitespacesAndNewlines))
            .flatMap { Int($0[0], radix: 16) }
            .map(QrCodeResult.wicarlink)
    }

    private static func firstMatch(of pattern: String, in code: String) -> [String]? {
        let regex = try! NSRegularExpression(pattern: pattern)
        guard let match = regex.firstMatch(in: code, range: NSRange(code.startIndex..., in: code)) else { return nil }
        return (1..<match.numberOfRanges).compactMap { Range(match.range(at: $0), in: code).map { String(code[$0]) } }
    }

    /// Each sensor at the wheel its ID starts with, or its rank
    private static func sysgration(_ ids: [String]) -> QrCodeResult {
        let sensors = ids.enumerated().map { index, hex in
            let wheel: SensorLocation = switch hex.first {
            case "1": .frontLeft
            case "2": .frontRight
            case "3": .rearLeft
            case "4": .rearRight
            default: SensorLocation.allCases[index]
            }
            // Little endian after a zero byte, as the sensor advertises it
            let value = UInt32(hex, radix: 16)!
            let id = Int(Int32(bitPattern: (value >> 16 & 0xFF) << 8 | (value >> 8 & 0xFF) << 16 | (value & 0xFF) << 24))
            return QrCodeSensor(id: id, wheel: wheel)
        }
        let wheels = duplicates(sensors.map(\.wheel))
        if wheels.isEmpty == false { return .duplicateWheelLocation(wheels) }
        let same = duplicates(sensors.map(\.id))
        return same.isEmpty ? .sensors(sensors) : .duplicateId(same)
    }

    private static func duplicates<T: Hashable>(_ values: [T]) -> [T] {
        var seen = Set<T>(), repeated = [T]()
        for value in values where seen.insert(value).inserted == false && repeated.contains(value) == false {
            repeated.append(value)
        }
        return repeated
    }

    /// Big endian, like Java's DataOutputStream. Most sensors aren't calibrated: their values are
    /// left out.
    fileprivate static func uri(of shared: SharedSensors) -> String {
        var bytes = [UInt8]()
        func append(_ value: UInt32) { (0..<4).reversed().forEach { bytes.append(UInt8(truncatingIfNeeded: value >> ($0 * 8))) } }
        bytes.append(UInt8(kinds.firstIndex(of: shared.kind) ?? 0))
        // Its bytes fit the length's single byte
        let name = Array(String(shared.name.prefix(40)).utf8)
        bytes.append(UInt8(name.count))
        bytes += name
        bytes.append(UInt8(shared.sensors.count))
        for sensor in shared.sensors {
            append(UInt32(truncatingIfNeeded: sensor.id))
            bytes.append(UInt8(locations.firstIndex(of: sensor.location) ?? 0))
            bytes.append(UInt8(brands.firstIndex(of: sensor.brand) ?? 0))
            let hasValues = PressureCalibration(offset: Pressure(kpa: sensor.offsetKpa), multiplier: sensor.multiplier).adjusts
            bytes.append((sensor.isCalibrated ? 0b01 : 0) | (hasValues ? 0b10 : 0))
            if hasValues {
                append(sensor.offsetKpa.bitPattern)
                append(sensor.multiplier.bitPattern)
            }
        }
        let data = Data(bytes).base64EncodedString()
            .replacingOccurrences(of: "+", with: "-")
            .replacingOccurrences(of: "/", with: "_")
            .replacingOccurrences(of: "=", with: "")
        return "\(sharedPrefix)v=1&d=\(data)"
    }

    private static func parseShared(_ code: String) -> QrCodeResult {
        let parameters = code.dropFirst(sharedPrefix.count).split(separator: "&", omittingEmptySubsequences: false).reduce(into: [String: String]()) { all, part in
            let pair = part.split(separator: "=", maxSplits: 1, omittingEmptySubsequences: false)
            all[String(pair[0])] = pair.count > 1 ? String(pair[1]) : ""
        }
        guard let version = parameters["v"] else { return .sharedUnreadable }
        guard version == "1" else { return .sharedByNewerApp }
        guard let data = parameters["d"], let bytes = base64URL(data) else { return .sharedUnreadable }
        return decodeShared(bytes).map(QrCodeResult.shared) ?? .sharedUnreadable
    }

    private static func base64URL(_ text: String) -> Data? {
        let base64 = text.replacingOccurrences(of: "-", with: "+").replacingOccurrences(of: "_", with: "/")
        return Data(base64Encoded: base64 + String(repeating: "=", count: (4 - base64.count % 4) % 4))
    }

    /// Big endian, like Java's DataOutputStream. Nil for a damaged code.
    private static func decodeShared(_ data: Data) -> SharedSensors? {
        var reader = Reader(bytes: [UInt8](data))
        guard let kind = reader.byte().flatMap({ kinds[safe: $0] }),
              let nameLength = reader.byte(),
              let name = reader.bytes(nameLength).flatMap({ String(bytes: $0, encoding: .utf8) }),
              let count = reader.byte()
        else { return nil }
        var sensors = [SharedSensor]()
        for _ in 0..<count {
            guard let id = reader.int32(),
                  let location = reader.byte().flatMap({ locations[safe: $0] }),
                  let brand = reader.byte().flatMap({ brands[safe: $0] }),
                  let flags = reader.byte()
            else { return nil }
            var offset: Float = 0, multiplier: Float = 1
            if flags & 0b10 != 0 {
                guard let readOffset = reader.float(), let readMultiplier = reader.float() else { return nil }
                offset = readOffset
                multiplier = readMultiplier
            }
            sensors.append(SharedSensor(id: Int(id), location: location, brand: brand, isCalibrated: flags & 0b01 != 0, offsetKpa: offset, multiplier: multiplier))
        }
        guard reader.isAtEnd, sensors.isEmpty == false,
              Set(sensors.map(\.location)).count == sensors.count,
              Set(sensors.map(\.id)).count == sensors.count
        else { return nil }
        return SharedSensors(kind: kind, name: name, sensors: sensors)
    }

    private struct Reader {
        let bytes: [UInt8]
        var position = 0

        var isAtEnd: Bool { position == bytes.count }

        mutating func bytes(_ count: Int) -> [UInt8]? {
            guard position + count <= bytes.count else { return nil }
            defer { position += count }
            return Array(bytes[position..<position + count])
        }

        mutating func byte() -> Int? { bytes(1).map { Int($0[0]) } }

        private mutating func uint32() -> UInt32? {
            bytes(4).map { $0.reduce(0) { $0 << 8 | UInt32($1) } }
        }

        mutating func int32() -> Int32? { uint32().map { Int32(bitPattern: $0) } }

        mutating func float() -> Float? { uint32().map { Float(bitPattern: $0) } }
    }
}

extension Array {
    fileprivate subscript(safe index: Int) -> Element? { indices.contains(index) ? self[index] : nil }
}

extension Array where Element == QrCodeSensor {
    /// The code's sensors on a vehicle of this kind, each at the wheel its own kit puts it, or nil
    /// when the vehicle has fewer wheels than the code has sensors: the user assigns them via
    /// Bluetooth then. A car with a spare keeps the spare's, the kits don't have one.
    public func locations(on kind: VehicleKind) -> [(sensor: QrCodeSensor, location: TyreLocation)]? {
        guard kind.locations.count >= count else { return nil }
        return map { sensor in
            let wheel = sensor.wheel
            let location: TyreLocation = switch kind {
            case .car, .carWithSpare: .wheel(wheel)
            case .singleAxleTrailer: .side(wheel.side)
            case .motorcycle: .axle(wheel.axle)
            case .tadpoleThreeWheeler: wheel.axle == .front ? .wheel(wheel) : .axle(.rear)
            case .deltaThreeWheeler: wheel.axle == .front ? .axle(.front) : .wheel(wheel)
            // A code has 2 sensors at least, more than its single wheel
            case .monowheel: .single
            }
            return (sensor, location)
        }
    }
}
