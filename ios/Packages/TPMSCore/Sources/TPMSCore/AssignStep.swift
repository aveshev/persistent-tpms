/// Where assigning a sensor to a wheel by Bluetooth stands, like the Android app's AssignStep. The
/// user puts the sensor on the wheel, takes it off and puts it back, each forcing it to send an
/// update: the sensors whose pressure follows are kept from one step to the next, until a single
/// one is back on the wheel. Only the last steps confirm which sensor it is, so a loose pick in the
/// first ones can't assign the wrong sensor: the flow just keeps listening.
///
/// Readings are told apart by the last pressure heard from each sensor: the first one heard from a
/// sensor by its absolute pressure, the next ones by the change since.
public enum AssignStep: Equatable, Sendable {
    /// Is the sensor on the wheel right now?
    case ask
    /// Waiting for the sensor to be put on the wheel. [found] were heard on a wheel, [low] were
    /// first heard below onWheelKpa: on a tyre at a very low pressure maybe.
    case putOn(found: Set<Int> = [], low: Set<Int> = [])
    /// Waiting for the sensor to be taken off the wheel, one of [candidates] or any when nil
    case takeOff(candidates: Set<Int>?, off: Set<Int> = [])
    /// Waiting for one of [candidates] to be put back on the wheel
    case putBack(candidates: Set<Int>)
    /// [sensorId] came back on the wheel: it's the one
    case found(sensorId: Int)

    /// Sensors subtract the atmospheric pressure: one off a wheel reads close to 0, one on a wheel
    /// above this, unless the tyre is nearly flat
    public static let onWheelKpa: Float = 10
    /// A change of at least this much, put on or taken off a wheel. Altitude shifts both alike.
    public static let changeKpa: Float = 15

    /// This step after a reading of [sensorId] at [kpa], given the readings before it
    public func after(sensorId: Int, kpa: Float, readings: [Int: Float]) -> AssignStep {
        let previous = readings[sensorId]
        switch self {
        case .ask, .found:
            return self
        case let .putOn(found, low):
            if Self.isOn(previous, kpa) { return .putOn(found: found.union([sensorId]), low: low) }
            if previous == nil { return .putOn(found: found, low: low.union([sensorId])) }
            return self
        case let .takeOff(candidates, off):
            guard candidates?.contains(sensorId) ?? true, Self.isOff(previous, kpa) else { return self }
            return .takeOff(candidates: candidates, off: off.union([sensorId]))
        case let .putBack(candidates):
            return candidates.contains(sensorId) && Self.isOn(previous, kpa) ? .found(sensorId: sensorId) : self
        }
    }

    /// Whether this step waits for sensors
    public var isListening: Bool {
        switch self {
        case .putOn, .takeOff, .putBack: true
        case .ask, .found: false
        }
    }

    /// First heard above onWheelKpa, or rose by changeKpa since last heard
    private static func isOn(_ previous: Float?, _ kpa: Float) -> Bool {
        previous.map { kpa - $0 >= changeKpa } ?? (kpa > onWheelKpa)
    }

    /// Dropped by changeKpa since last heard, or first heard below onWheelKpa: a sensor taken off
    /// before it was ever heard on its wheel. Putting it back confirms it.
    private static func isOff(_ previous: Float?, _ kpa: Float) -> Bool {
        previous.map { $0 - kpa >= changeKpa } ?? (kpa < onWheelKpa)
    }
}
