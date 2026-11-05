import Foundation
import TPMSCore
import UserNotifications

/// One notification per tyre and alert class, like the Android app's: posted when its level gets
/// worse, replaced silently when it improves, removed once the class is fine again. The levels it
/// notified about are kept, so a relaunch doesn't notify again about the same alert.
final class Notifier {
    private let center = UNUserNotificationCenter.current()
    private let defaults = UserDefaults.standard
    private static let key = "notifiedLevels"

    private var notified: [String: Int] {
        get { defaults.dictionary(forKey: Self.key) as? [String: Int] ?? [:] }
        set { defaults.set(newValue, forKey: Self.key) }
    }

    func requestAuthorization(_ completion: @escaping (Bool) -> Void) {
        center.requestAuthorization(options: [.alert, .sound, .badge]) { granted, _ in
            DispatchQueue.main.async { completion(granted) }
        }
    }

    func update(vehicle: Vehicle, location: TyreLocation, alerts: TyreAlerts, loss: PressureLoss?, settings: Settings) {
        guard let reading = alerts.latest else { return }
        let thresholds = vehicle.thresholds(location)
        var notified = notified
        for alertClass in AlertClass.allCases {
            let id = identifier(vehicle, location, alertClass)
            guard let level = alerts.notifiable[alertClass] else {
                if notified.removeValue(forKey: id) != nil {
                    center.removeDeliveredNotifications(withIdentifiers: [id])
                }
                continue
            }
            let previous = notified[id].flatMap(AlertLevel.init(rawValue:))
            guard previous != level else { continue }
            notified[id] = level.rawValue
            let content = UNMutableNotificationContent()
            content.title = "\(vehicle.name), \(location.name.lowercased()): \(title(alertClass, level, alerts.isHighPressure))"
            content.body = body(alertClass, reading, thresholds, loss, settings)
            // Only a worse level makes a sound, an improving one updates the notification silently
            content.sound = previous.map { level > $0 } ?? true ? .default : nil
            content.interruptionLevel = level == .crimson ? .timeSensitive : .active
            content.threadIdentifier = vehicle.id.uuidString
            center.add(UNNotificationRequest(identifier: id, content: content, trigger: nil))
        }
        self.notified = notified
    }

    func clear(vehicle: Vehicle, location: TyreLocation) {
        let ids = AlertClass.allCases.map { identifier(vehicle, location, $0) }
        center.removeDeliveredNotifications(withIdentifiers: ids)
        var notified = notified
        ids.forEach { notified.removeValue(forKey: $0) }
        self.notified = notified
    }

    func clearAll() {
        center.removeAllDeliveredNotifications()
        notified = [:]
    }

    private func identifier(_ vehicle: Vehicle, _ location: TyreLocation, _ alertClass: AlertClass) -> String {
        "\(vehicle.id.uuidString)-\(location.code)-\(alertClass.rawValue)"
    }

    private func title(_ alertClass: AlertClass, _ level: AlertLevel, _ isHigh: Bool) -> String {
        let isCritical = level == .crimson
        return switch alertClass {
        case .pressure:
            switch (isCritical, isHigh) {
            case (true, true): tr("critical pressure high")
            case (true, false): tr("critical pressure low")
            case (false, true): tr("pressure high")
            case (false, false): tr("pressure low")
            }
        case .temperature: isCritical ? tr("critical temperature high") : tr("temperature high")
        case .battery: level == .amber ? tr("battery getting low") : tr("battery low")
        case .pressureLoss: tr("losing pressure")
        case .sensorAlarm: tr("the sensor reports a leak")
        }
    }

    private func body(_ alertClass: AlertClass, _ reading: SensorReading, _ thresholds: AlertThresholds, _ loss: PressureLoss?, _ settings: Settings) -> String {
        let unit = settings.pressureUnit
        return switch alertClass {
        case .pressure:
            tr("\(reading.pressure.string(unit)), set from \(thresholds.lowPressure.string(unit)) to \(thresholds.highPressure.string(unit))")
        case .temperature:
            tr("\(reading.temperature.string(settings.temperatureUnit)), hot from \(thresholds.highTemp.string(settings.temperatureUnit))")
        case .battery:
            reading.batteryVoltage.map { tr("\($0.string), the alarm being at \(thresholds.lowBatteryVoltage.string)") }
                ?? reading.batteryPercent.map { tr("\($0) %, the alarm being at \(thresholds.lowBatteryPercent) %") } ?? ""
        case .pressureLoss:
            loss.map { tr("\($0.drop.string(unit)) lost since its highest reading of the ride, now \(reading.pressure.string(unit))") } ?? ""
        case .sensorAlarm:
            tr("Read \(reading.pressure.string(unit)). A fall in temperature can raise it too.")
        }
    }
}
