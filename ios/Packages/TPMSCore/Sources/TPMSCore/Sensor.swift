import Foundation

/// The sensors the app decodes. The raw value is what the database stores, the same as Android's
/// `SensorBrand.code`: it never changes, a new brand takes the next one.
public enum SensorBrand: Int, CaseIterable, Sendable {
    case pecham = 1, bekubeeKy = 2, wicarlink = 3, bekubeeTpms = 4, sysgration = 5

    public var name: String {
        switch self {
        case .pecham: "Pecham"
        case .bekubeeKy: "Bekubee KY"
        case .wicarlink: "Wicarlink"
        case .bekubeeTpms: "Bekubee TPMS"
        case .sysgration: "Sysgration"
        }
    }

    /// Sysgration reports its battery as a percentage, the others as a voltage
    public var reportsPercent: Bool { self == .sysgration }

    /// Pecham's and Bekubee KY's advertisements carry no ID: Android identifies them by their
    /// Bluetooth address, which iOS doesn't give. iOS gives each phone its own identifier for them
    /// instead, so they're bound again on each iPhone.
    public var isIdentifiedByAddress: Bool { self == .pecham || self == .bekubeeKy }
}

/// Where a Sysgration sensor says it is, from its own standpoint
public enum SensorLocation: UInt8, CaseIterable, Sendable {
    case frontLeft = 0x80, frontRight = 0x81, rearLeft = 0x82, rearRight = 0x83

    /// Android's ordinal, which the database's location column is built from
    var ordinal: Int { Int(rawValue - 0x80) }

    public var axle: Axle { self == .frontLeft || self == .frontRight ? .front : .rear }
    public var side: Side { self == .frontLeft || self == .rearLeft ? .left : .right }

    public enum Axle: Int, Sendable { case front = 0, rear = 1 }
    public enum Side: Int, Sendable { case left = 0, right = 1 }
}

/// A decoded advertisement: what Android's `Tyre.SensorInput` is
public struct SensorReading: Hashable, Sendable {
    public var timestamp: Double
    public var rssi: Int
    public var sensorId: Int
    public var pressure: Pressure
    public var temperature: Temperature
    public var brand: SensorBrand
    /// An alarm raised by the sensor itself, only Sysgration and Wicarlink send one
    public var isAlarm: Bool
    public var batteryVoltage: Voltage?
    public var batteryPercent: Int?
    /// Where a Sysgration sensor says it is
    public var location: SensorLocation?
    /// The whole advertisement, as Android's database stores it
    public var raw: Data

    public init(
        timestamp: Double, rssi: Int, sensorId: Int, pressure: Pressure, temperature: Temperature, brand: SensorBrand,
        isAlarm: Bool, batteryVoltage: Voltage?, batteryPercent: Int?, location: SensorLocation?, raw: Data
    ) {
        self.timestamp = timestamp
        self.rssi = rssi
        self.sensorId = sensorId
        self.pressure = pressure
        self.temperature = temperature
        self.brand = brand
        self.isAlarm = isAlarm
        self.batteryVoltage = batteryVoltage
        self.batteryPercent = batteryPercent
        self.location = location
        self.raw = raw
    }
}
