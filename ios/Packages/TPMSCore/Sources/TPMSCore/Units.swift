import Foundation

// Ported from data/vehicle's Pressure, Temperature and Voltage, and data/unit's units

public enum PressureUnit: String, CaseIterable, Identifiable, Sendable {
    case kiloPascal = "KILO_PASCAL", bar = "BAR", psi = "PSI"

    public var id: String { rawValue }

    /// As written after a value, e.g. "kPa" in "250 kPa"
    public var symbol: String {
        switch self {
        case .kiloPascal: "kPa"
        case .bar: "bar"
        case .psi: "psi"
        }
    }
}

public enum TemperatureUnit: String, CaseIterable, Identifiable, Sendable {
    case celsius = "CELSIUS", fahrenheit = "FAHRENHEIT"

    public var id: String { rawValue }

    public var symbol: String { self == .celsius ? "°C" : "°F" }
}

public struct Pressure: Comparable, Hashable, Sendable {
    public var kpa: Float

    public init(kpa: Float) { self.kpa = kpa }

    public static func bar(_ value: Float) -> Pressure { Pressure(kpa: value * 100) }
    public static func psi(_ value: Float) -> Pressure { Pressure(kpa: value * 6.895) }

    public init(_ value: Float, _ unit: PressureUnit) {
        switch unit {
        case .kiloPascal: kpa = value
        case .bar: kpa = value * 100
        case .psi: kpa = value * 6.895
        }
    }

    public func convert(_ unit: PressureUnit) -> Float {
        switch unit {
        case .kiloPascal: kpa
        case .bar: kpa / 100
        case .psi: kpa / 6.895
        }
    }

    /// One decimal less from 10 bar and 100 psi, like the Android app
    public func string(_ unit: PressureUnit) -> String {
        switch unit {
        case .kiloPascal: String(format: "%.0f kPa", kpa)
        case .bar: String(format: convert(.bar) >= 9.995 ? "%.1f bar" : "%.2f bar", convert(.bar))
        case .psi: String(format: convert(.psi) >= 99.95 ? "%.0f psi" : "%.1f psi", convert(.psi))
        }
    }

    /// The value alone, without trailing zeros: what a text field starts with
    public func numberString(_ unit: PressureUnit) -> String {
        let text = switch unit {
        case .kiloPascal: String(format: "%.0f", kpa)
        case .bar: String(format: "%.2f", convert(.bar))
        case .psi: String(format: "%.1f", convert(.psi))
        }
        guard text.contains(".") else { return text }
        return String(text.reversed().drop { $0 == "0" }.drop { $0 == "." }.reversed())
    }

    public static func < (lhs: Pressure, rhs: Pressure) -> Bool { lhs.kpa < rhs.kpa }
}

public struct Temperature: Comparable, Hashable, Sendable {
    public var celsius: Float

    public init(celsius: Float) { self.celsius = celsius }

    public init(_ value: Float, _ unit: TemperatureUnit) {
        celsius = unit == .celsius ? value : (value - 32) * 5 / 9
    }

    public func convert(_ unit: TemperatureUnit) -> Float {
        unit == .celsius ? celsius : celsius * 9 / 5 + 32
    }

    public func string(_ unit: TemperatureUnit) -> String {
        String(format: "%.0f%@", convert(unit), unit.symbol)
    }

    public func numberString(_ unit: TemperatureUnit) -> String { String(format: "%.0f", convert(unit)) }

    public static func < (lhs: Temperature, rhs: Temperature) -> Bool { lhs.celsius < rhs.celsius }
}

/// A sensor's battery voltage, most sensors report it in steps of 0.1 V: comparisons go through
/// [isAtOrBelow], which tolerates the rounding of those steps
public struct Voltage: Comparable, Hashable, Sendable {
    public var volts: Float

    public init(volts: Float) { self.volts = volts }

    public var string: String { String(format: "%.1f V", volts) }

    public func isAtOrBelow(_ other: Voltage) -> Bool { volts <= other.volts + 0.001 }

    /// The temperature the low voltage alarm is set for, see [alarmAt]
    public static let alarmReferenceTemperature = Temperature(celsius: 20)
    /// How much a coin cell sags per degree below the reference, under a sensor's load
    public static let coldSagPerDegree: Float = 0.005
    /// The cold can't take the alarm lower than this below it: the sensor would go silent first
    public static let brownoutMargin: Float = 0.1

    /// Where this low voltage alarm, set for a battery at 20°C, stands for a sensor reading at
    /// [temperature]: a coin cell sags in the cold without its charge changing (see Voltage.kt)
    public func alarmAt(_ temperature: Temperature) -> Voltage {
        Voltage(volts: volts - min(max(Self.alarmReferenceTemperature.celsius - temperature.celsius, 0) * Self.coldSagPerDegree, Self.brownoutMargin))
    }

    public static func < (lhs: Voltage, rhs: Voltage) -> Bool { lhs.volts < rhs.volts }
}
