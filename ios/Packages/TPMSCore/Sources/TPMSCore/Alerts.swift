import Foundation

// Ported from data/vehicle's AlertThresholds, TyreAlerts and PressureLoss, see docs/alerts.md

/// From the least to the most urgent, so levels compare by it
public enum AlertLevel: Int, Comparable, CaseIterable, Sendable {
    /// Doesn't need immediate action
    case amber
    /// Riding can go on with caution, to be addressed at the earliest possibility
    case red
    /// Must be fixed before riding on
    case crimson

    public static func < (lhs: AlertLevel, rhs: AlertLevel) -> Bool { lhs.rawValue < rhs.rawValue }
}

public enum AlertClass: String, CaseIterable, Sendable {
    case pressure, temperature, battery
    /// A tyre losing pressure while riding, see PressureLossTracker
    case pressureLoss
    /// Raised by the sensor itself (Sysgration, Wicarlink)
    case sensorAlarm
}

/// A tyre's thresholds. A value exactly on a boundary is at the more severe level.
public struct AlertThresholds: Hashable, Sendable {
    public var lowPressure: Pressure
    public var highPressure: Pressure
    public var highTemp: Temperature
    public var lowBatteryVoltage: Voltage
    public var lowBatteryPercent: Int = 10
    /// Off for a spare, which isn't driven on: red at worst, never crimson
    public var allowsCritical: Bool = true

    private static let amberLowPressure: Float = 1.03
    /// 25% below, where FMVSS 138 has cars' tyre pressure warning light turn on
    private static let crimsonLowPressure: Float = 0.75
    private static let amberHighPressure: Float = 0.97
    private static let crimsonHighPressure: Float = 1.2
    public static let amberTemperatureBelow: Float = 10
    public static let crimsonTemperatureAbove: Float = 20
    public static let batteryAmberMargin: Float = 0.1
    public static let batteryAmberMarginPercent = 10
    /// Tolerates the float rounding of thresholds typed in another unit
    private static let epsilon: Float = 0.01

    public func levels(_ reading: SensorReading, isLeaking: Bool) -> [AlertClass: AlertLevel] {
        var levels = [AlertClass: AlertLevel]()
        levels[.pressure] = pressureLevel(reading.pressure)
        levels[.temperature] = temperatureLevel(reading.temperature)
        levels[.battery] = reading.batteryVoltage.flatMap { batteryLevel($0, reading.temperature) }
            ?? reading.batteryPercent.flatMap(batteryLevel)
        if isLeaking { levels[.pressureLoss] = .amber }
        if reading.isAlarm { levels[.sensorAlarm] = .amber }
        return levels
    }

    /// The worse of the low and the high side, nil while in range
    public func pressureLevel(_ pressure: Pressure) -> AlertLevel? {
        let p = pressure.kpa, low = lowPressure.kpa, high = highPressure.kpa, e = Self.epsilon
        let lowSide: AlertLevel? = if p <= low * Self.crimsonLowPressure + e { .crimson }
            else if p <= low + e { .red }
            else if p <= low * Self.amberLowPressure + e { .amber }
            else { nil }
        let highSide: AlertLevel? = if p >= high * Self.crimsonHighPressure - e { .crimson }
            else if p >= high - e { .red }
            else if p >= high * Self.amberHighPressure - e { .amber }
            else { nil }
        return [lowSide, highSide].compactMap { $0 }.max().map(capped)
    }

    /// On the high side of the range, which tells which way a pressure alert goes
    public func isHighPressure(_ pressure: Pressure) -> Bool {
        pressure.kpa * 2 >= lowPressure.kpa + highPressure.kpa
    }

    public func temperatureLevel(_ temperature: Temperature) -> AlertLevel? {
        let t = temperature.celsius, hot = highTemp.celsius, e = Self.epsilon
        let level: AlertLevel? = if t >= hot + Self.crimsonTemperatureAbove - e { .crimson }
            else if t >= hot - e { .red }
            else if t >= hot - Self.amberTemperatureBelow - e { .amber }
            else { nil }
        return level.map(capped)
    }

    /// Against the low voltage alarm adjusted to the sensor's temperature, see Voltage.alarmAt
    public func batteryLevel(_ voltage: Voltage, _ temperature: Temperature) -> AlertLevel? {
        let alarm = lowBatteryVoltage.alarmAt(temperature)
        if voltage.isAtOrBelow(alarm) { return .red }
        if voltage.isAtOrBelow(Voltage(volts: alarm.volts + Self.batteryAmberMargin)) { return .amber }
        return nil
    }

    public func batteryLevel(_ percent: Int) -> AlertLevel? {
        if percent <= lowBatteryPercent { return .red }
        if percent <= lowBatteryPercent + Self.batteryAmberMarginPercent { return .amber }
        return nil
    }

    private func capped(_ level: AlertLevel) -> AlertLevel {
        level == .crimson && allowsCritical == false ? .red : level
    }
}

/// Follows a single tyre's readings, fed one by one to [next], for its alerts. [notifiable] is
/// [levels] except for a red battery, which takes readings at red 10 minutes apart, amber meanwhile.
public struct TyreAlerts: Hashable, Sendable {
    public private(set) var latest: SensorReading?
    public private(set) var levels: [AlertClass: AlertLevel] = [:]
    public private(set) var notifiable: [AlertClass: AlertLevel] = [:]
    public private(set) var isHighPressure = false
    private var batteryRedSince: Double?

    /// The voltage dips in the cold and while the sensor sends, recovering afterwards
    public static let batteryConfirmation: Double = 10 * 60

    public init() {}

    public func next(_ reading: SensorReading, _ thresholds: AlertThresholds, _ loss: PressureLoss?) -> TyreAlerts {
        // Another sensor was bound to this tyre, whatever was going on was about the previous one
        if let latest, reading.sensorId != latest.sensorId { return TyreAlerts().next(reading, thresholds, loss) }
        if let latest, reading.timestamp < latest.timestamp { return self }
        let levels = thresholds.levels(reading, isLeaking: loss != nil)
        let redSince = levels[.battery] == .red ? (batteryRedSince ?? reading.timestamp) : nil
        var next = self
        next.latest = reading
        next.levels = levels
        next.isHighPressure = thresholds.isHighPressure(reading.pressure)
        next.notifiable = levels.reduce(into: [:]) { result, entry in
            // A single low voltage can be a dip
            let held = entry.key == .battery && entry.value == .red && redSince.map { reading.timestamp - $0 < Self.batteryConfirmation } == true
            result[entry.key] = held ? .amber : entry.value
        }
        next.batteryRedSince = redSince
        return next
    }
}

/// A tyre which lost [drop] since its highest reading of the ride, [perHour] on average
public struct PressureLoss: Hashable, Sendable {
    public var perHour: Pressure
    public var drop: Pressure
    public var since: Double
    public var until: Double
}

/// Looks for a leak while riding: the pressure falling from its highest reading of the ride, the
/// tyre not cooling down. A ride starts after a gap of 10 minutes. See PressureLoss.kt.
public struct PressureLossTracker: Hashable, Sendable {
    public private(set) var loss: PressureLoss?
    private var peak: SensorReading?
    private var latest: SensorReading?
    private var confirmations = 0
    /// The least drop which warns, the settings' "Minimum drop"
    public let minDrop: Pressure

    public static let rideGap: Double = 10 * 60
    /// Below it, a sensor reads the open air: taken off the valve
    public static let offValve = Pressure(kpa: 10)
    public static let defaultMinDrop = Pressure(kpa: 7)
    private static let temperatureFall: Float = 1
    private static let requiredConfirmations = 2

    public init(minDrop: Pressure = defaultMinDrop) { self.minDrop = minDrop }

    public func next(_ reading: SensorReading) -> PressureLossTracker {
        if let latest, reading.timestamp <= latest.timestamp { return self }
        if reading.pressure < Self.offValve { return PressureLossTracker(minDrop: minDrop) }
        if let latest, reading.sensorId != latest.sensorId || reading.timestamp - latest.timestamp >= Self.rideGap {
            return PressureLossTracker(minDrop: minDrop).next(reading)
        }
        guard let peak, reading.pressure < peak.pressure else {
            var next = PressureLossTracker(minDrop: minDrop)
            next.peak = reading
            next.latest = reading
            return next
        }
        let drop = peak.pressure.kpa - reading.pressure.kpa
        let measured = PressureLoss(
            perHour: Pressure(kpa: drop / Float((reading.timestamp - peak.timestamp) / 3600)),
            drop: Pressure(kpa: drop), since: peak.timestamp, until: reading.timestamp
        )
        let confirmed = measured.drop >= minDrop && peak.temperature.celsius - reading.temperature.celsius <= Self.temperatureFall
        var next = self
        next.confirmations = confirmed ? confirmations + 1 : 0
        next.loss = next.confirmations >= Self.requiredConfirmations ? measured : loss
        next.latest = reading
        return next
    }
}
