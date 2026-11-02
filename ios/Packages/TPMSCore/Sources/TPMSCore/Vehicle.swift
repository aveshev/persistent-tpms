import Foundation

/// A tyre's place on a vehicle, from the vehicle's standpoint. The raw value is what the database
/// stores, the same as Android's location adapter: axles from 0, sides from 10, wheels from 20, the
/// spare 30, a monowheel's single wheel 40.
public enum TyreLocation: Hashable, Identifiable, Sendable {
    case axle(SensorLocation.Axle)
    case side(SensorLocation.Side)
    case wheel(SensorLocation)
    case spare
    case single

    public var id: Int { code }

    public var code: Int {
        switch self {
        case let .axle(axle): axle.rawValue
        case let .side(side): 10 + side.rawValue
        case let .wheel(wheel): 20 + wheel.ordinal
        case .spare: 30
        case .single: 40
        }
    }

    public init?(code: Int) {
        switch code {
        case 0...1: self = .axle(SensorLocation.Axle(rawValue: code)!)
        case 10...11: self = .side(SensorLocation.Side(rawValue: code - 10)!)
        case 20...23: self = .wheel(SensorLocation.allCases[code - 20])
        case 30: self = .spare
        case 40: self = .single
        default: return nil
        }
    }

    /// The spare is kept at the rear's pressure, like a full-size spare
    public var axle: SensorLocation.Axle? {
        switch self {
        case let .axle(axle): axle
        case let .wheel(wheel): wheel.axle
        case .spare: .rear
        case .side, .single: nil
        }
    }

    public var name: String {
        switch self {
        case .axle(.front): "Front"
        case .axle(.rear): "Rear"
        case .side(.left): "Left"
        case .side(.right): "Right"
        case .wheel(.frontLeft): "Front left"
        case .wheel(.frontRight): "Front right"
        case .wheel(.rearLeft): "Rear left"
        case .wheel(.rearRight): "Rear right"
        case .spare: "Spare"
        case .single: "Wheel"
        }
    }

    /// Whether a Sysgration sensor advertising [sensorLocation] can be at this tyre. They only
    /// advertise the four wheels of a car, none says it's a spare.
    public func accepts(_ sensorLocation: SensorLocation) -> Bool {
        switch self {
        case let .wheel(wheel): wheel == sensorLocation
        case let .axle(axle): sensorLocation.axle == axle
        case let .side(side): sensorLocation.side == side
        case .spare: false
        // Whichever wheel it was made for, it's the only one
        case .single: true
        }
    }
}

public enum VehicleKind: String, CaseIterable, Identifiable, Sendable {
    case car = "CAR"
    case singleAxleTrailer = "SINGLE_AXLE_TRAILER"
    case motorcycle = "MOTORCYCLE"
    case tadpoleThreeWheeler = "TADPOLE_THREE_WHEELER"
    case deltaThreeWheeler = "DELTA_THREE_WHEELER"
    case carWithSpare = "CAR_WITH_SPARE"
    case monowheel = "MONOWHEEL"

    public var id: String { rawValue }

    /// In the order they're offered when adding a vehicle, like CurrentVehicleDropdown.kt's
    public static let addingOrder: [VehicleKind] = [
        .car, .carWithSpare, .motorcycle, .tadpoleThreeWheeler, .deltaThreeWheeler, .singleAxleTrailer, .monowheel,
    ]

    public var name: String {
        switch self {
        case .car: "Car"
        case .singleAxleTrailer: "Single axle trailer"
        case .motorcycle: "Motorcycle"
        case .tadpoleThreeWheeler: "Tadpole three-wheeler"
        case .deltaThreeWheeler: "Delta three-wheeler"
        case .carWithSpare: "Car with a spare tyre"
        case .monowheel: "Monowheel"
        }
    }

    /// Rows of tyres, front first, as the main screen lays them out
    public var rows: [[TyreLocation]] {
        switch self {
        case .car: [[.wheel(.frontLeft), .wheel(.frontRight)], [.wheel(.rearLeft), .wheel(.rearRight)]]
        case .carWithSpare: [[.wheel(.frontLeft), .wheel(.frontRight)], [.wheel(.rearLeft), .wheel(.rearRight)], [.spare]]
        case .singleAxleTrailer: [[.side(.left), .side(.right)]]
        case .motorcycle: [[.axle(.front)], [.axle(.rear)]]
        case .tadpoleThreeWheeler: [[.wheel(.frontLeft), .wheel(.frontRight)], [.axle(.rear)]]
        case .deltaThreeWheeler: [[.axle(.front)], [.wheel(.rearLeft), .wheel(.rearRight)]]
        case .monowheel: [[.single]]
        }
    }

    public var locations: [TyreLocation] { rows.flatMap { $0 } }

    /// From a typical recommended cold pressure to a typical sidewall maximum, see Vehicle.kt
    public var defaultPressures: ClosedRange<Pressure> {
        switch self {
        case .car, .carWithSpare: .bar(2.3)...(.bar(3.2))
        case .singleAxleTrailer: .bar(2.8)...(.bar(4.5))
        case .motorcycle: .bar(2.5)...(.bar(3))
        case .tadpoleThreeWheeler: .bar(1.8)...(.bar(2.5))
        case .deltaThreeWheeler: .bar(2.5)...(.bar(3))
        case .monowheel: .bar(2.8)...(.bar(3.5))
        }
    }

    public var defaultRearPressures: ClosedRange<Pressure>? {
        switch self {
        case .motorcycle: .bar(2.9)...(.bar(3.4))
        case .tadpoleThreeWheeler: .bar(2.4)...(.bar(3))
        case .deltaThreeWheeler: .bar(1.9)...(.bar(2.8))
        default: nil
        }
    }

    public var hasFrontRearAxles: Bool { locations.contains { $0.axle != nil } }
}

public struct Vehicle: Identifiable, Hashable, Sendable {
    public var id: UUID
    public var name: String
    public var kind: VehicleKind
    public var isCurrent: Bool
    public var lowPressure: Pressure
    public var highPressure: Pressure
    public var rearLowPressure: Pressure?
    public var rearHighPressure: Pressure?
    public var separateRearPressure: Bool
    public var spareLowPressure: Pressure?
    public var spareHighPressure: Pressure?
    public var separateSparePressure: Bool
    public var lowTemp: Temperature
    public var highTemp: Temperature
    public var lowBatteryVoltage: Voltage
    public var lowBatteryPercent: Int

    /// A new vehicle starts from its kind's pressures, see VehicleKind.defaultPressures, and the
    /// temperatures Vehicle.sq's insert sets
    public static func new(name: String, kind: VehicleKind) -> Vehicle {
        let rear = kind.defaultRearPressures
        return Vehicle(
            id: UUID(), name: name, kind: kind, isCurrent: false,
            lowPressure: kind.defaultPressures.lowerBound, highPressure: kind.defaultPressures.upperBound,
            rearLowPressure: rear?.lowerBound, rearHighPressure: rear?.upperBound, separateRearPressure: rear != nil,
            spareLowPressure: nil, spareHighPressure: nil, separateSparePressure: false,
            lowTemp: Temperature(celsius: 7), highTemp: Temperature(celsius: 65),
            lowBatteryVoltage: Voltage(volts: 2.6), lowBatteryPercent: 10
        )
    }

    /// The spare's and the rear's own range while they have one, the front's otherwise
    public func pressureRange(_ location: TyreLocation) -> ClosedRange<Pressure> {
        if location == .spare, separateSparePressure, let low = spareLowPressure, let high = spareHighPressure, low <= high {
            return low...high
        }
        if location.axle == .rear, separateRearPressure, let low = rearLowPressure, let high = rearHighPressure, low <= high {
            return low...high
        }
        return min(lowPressure, highPressure)...max(lowPressure, highPressure)
    }

    public func thresholds(_ location: TyreLocation) -> AlertThresholds {
        let range = pressureRange(location)
        return AlertThresholds(
            lowPressure: range.lowerBound, highPressure: range.upperBound, highTemp: highTemp,
            lowBatteryVoltage: lowBatteryVoltage, lowBatteryPercent: lowBatteryPercent,
            // Nothing critical about a spare that isn't driven on
            allowsCritical: location != .spare
        )
    }
}

/// A sensor bound to a vehicle's tyre
public struct BoundSensor: Hashable, Sendable {
    public var id: Int
    public var brand: SensorBrand
    public var vehicleId: UUID
    public var location: TyreLocation
    public var calibration: SensorCalibration

    public init(id: Int, brand: SensorBrand, vehicleId: UUID, location: TyreLocation, calibration: SensorCalibration = .none) {
        self.id = id
        self.brand = brand
        self.vehicleId = vehicleId
        self.location = location
        self.calibration = calibration
    }
}

/// Corrects the pressure read from a sensor which is off by a few units, like the Android app's
/// PressureCalibration: the read pressure is multiplied by [multiplier] then shifted by [offset]
public struct PressureCalibration: Hashable, Sendable {
    public var offset: Pressure
    public var multiplier: Float

    public init(offset: Pressure = Pressure(kpa: 0), multiplier: Float = 1) {
        self.offset = offset
        self.multiplier = multiplier
    }

    /// False when [applyTo] leaves every pressure as it is. With a margin: the values go through
    /// float unit conversions and −/+ steps.
    public var adjusts: Bool { abs(offset.kpa) >= 0.01 || abs(multiplier - 1) >= 0.0001 }

    /// A pressure of 0, which is how a flat tyre is reported, is left as is so an offset never hides
    /// it. A correction below 0 is clamped to 0 for the same reason.
    public func applyTo(_ pressure: Pressure) -> Pressure {
        pressure.kpa > 0 ? Pressure(kpa: max(0, pressure.kpa * multiplier + offset.kpa)) : pressure
    }
}

/// A sensor's calibration: its values are kept while it's off
public struct SensorCalibration: Hashable, Sendable {
    public var isEnabled: Bool
    public var calibration: PressureCalibration

    public init(isEnabled: Bool, calibration: PressureCalibration) {
        self.isEnabled = isEnabled
        self.calibration = calibration
    }

    public static let none = SensorCalibration(isEnabled: false, calibration: PressureCalibration())

    /// What corrects the readings, nil when nothing does
    public var applied: PressureCalibration? { isEnabled && calibration.adjusts ? calibration : nil }
}

/// The page editing a sensor's calibration: a draft until it's applied, like the Android app's
/// SensorCalibrationViewModel
public struct CalibrationDraft: Hashable, Sendable {
    public let saved: SensorCalibration
    public var draft: SensorCalibration
    /// The vehicle's other sensors
    public let others: [SensorCalibration]

    public init(saved: SensorCalibration, others: [SensorCalibration]) {
        self.saved = saved
        draft = saved
        self.others = others
    }

    /// What applying writes. A calibration off keeps the saved values: the ones edited while off
    /// apply to nothing, they're dropped. On but adjusting nothing, it's the same as off.
    public var toApply: SensorCalibration {
        if draft.applied != nil { return draft }
        var off = saved
        off.isEnabled = false
        return off
    }

    /// Applying to this sensor would write something: an edit undone, or a calibration turned on
    /// without adjusting anything, isn't one
    public var isChanged: Bool { toApply != saved }

    /// Applying to all changes something: this sensor's edits, or another sensor reading differently
    public var canApplyToAll: Bool {
        others.isEmpty == false && (isChanged || others.contains { $0.applied != toApply.applied })
    }

    /// Applying to all would lose another sensor's calibration, the user is asked first
    public var applyingErases: Bool {
        others.contains { $0.applied != nil && $0.applied != toApply.applied }
    }
}
