import Foundation
import TPMSCore
import UIKit
import UserNotifications

/// One notification per tyre and alert class, and what the speech says about it, like the Android
/// app's AlertNotifier, see docs/alerts.md and AlertAction.
///
/// A red or critical one which sounded while the phone was locked, likely in a pocket or a bag
/// during a ride, sounds again once it's unlocked, see docs/alerts.md ("Reminding on unlocking").
/// What the speech says goes on without the app, see SpeechBackup.
final class Notifier {
    private let center = UNUserNotificationCenter.current()
    private let defaults = UserDefaults.standard
    private static let key = "notifiedLevels"
    private static let snoozesKey = "alertSnoozes"
    private static let ridePromptId = "ridePrompt"
    private static let snoozeActionPrefix = "snooze."
    private static let sensorIdKey = "sensorId"
    private static let alertClassKey = "alertClass"
    private static let levelKey = "level"
    private static let speechTagKey = "speechTag"
    private static let unseenKey = "unseenAlerts"

    init() {
        center.setNotificationCategories(Self.categories)
        // Only sent while the app runs: suspended in the background between two sensors heard, it
        // reminds at the next reading instead, see update()
        NotificationCenter.default.addObserver(forName: UIApplication.protectedDataDidBecomeAvailableNotification, object: nil, queue: .main) { [weak self] _ in
            self?.remindIfUnlocked()
        }
        // Opened on the app, whose screen shows them
        NotificationCenter.default.addObserver(forName: UIApplication.didBecomeActiveNotification, object: nil, queue: .main) { [weak self] _ in
            self?.seen()
        }
    }

    /// The red and critical notifications which last sounded while the phone was locked
    private var unseen: Set<String> {
        get { Set(defaults.stringArray(forKey: Self.unseenKey) ?? []) }
        set { defaults.set(Array(newValue), forKey: Self.unseenKey) }
    }

    /// The latest content posted under each identifier, which may not be delivered yet, to repeat it.
    /// Kept in memory: after a relaunch, the delivered one is repeated.
    private var contents = [String: UNNotificationContent]()

    /// Whether the phone was locked when last checked, see [remindIfUnlocked]
    private var wasLocked = false

    /// Locked with a passcode: the protected data is only available while unlocked, and for a few
    /// seconds after locking. Without a passcode it's always there, and nothing reminds.
    private var isLocked: Bool { UIApplication.shared.isProtectedDataAvailable == false }

    /// The unseen ones still red or critical
    private var due: Set<String> {
        let notified = notified
        return unseen.filter { id in notified[id].flatMap(AlertLevel.init(rawValue:)).map { $0 >= .red } == true }
    }

    /// Unlocked since last checked: sounds the unseen notifications again, still red or critical
    private func remindIfUnlocked() {
        let isLocked = isLocked
        defer { wasLocked = isLocked }
        guard wasLocked, isLocked == false else { return }
        let due = due
        seen()
        // Opened on the app, whose screen shows them
        guard due.isEmpty == false, UIApplication.shared.applicationState != .active else { return }
        reminders(due) { [center] id, content in
            center.add(UNNotificationRequest(identifier: id, content: content, trigger: nil))
        }
    }

    /// The phone was unlocked with them shown, or the app opened on them
    private func seen() {
        unseen = []
    }

    /// The notifications [ids] as they show, sounding
    private func reminders(_ ids: Set<String>, _ then: @escaping (String, UNNotificationContent) -> Void) {
        let contents = contents
        center.getDeliveredNotifications { delivered in
            delivered
                .reduce(into: contents) { result, notification in
                    result[notification.request.identifier] = result[notification.request.identifier] ?? notification.request.content
                }
                .filter { ids.contains($0.key) }
                .forEach { id, content in
                    guard let content = content.mutableCopy() as? UNMutableNotificationContent else { return }
                    let level = (content.userInfo[Self.levelKey] as? Int).flatMap(AlertLevel.init(rawValue:))
                    content.sound = .default
                    // As it sounded when posted
                    content.interruptionLevel = level.map(Self.interruptionLevel) ?? .active
                    then(id, content)
                }
        }
    }

    /// A sounding notification's, a reminder's too. Red and crimson go through a Focus, Driving
    /// included: that's when they matter
    private static func interruptionLevel(_ level: AlertLevel) -> UNNotificationInterruptionLevel {
        level >= .red ? .timeSensitive : .active
    }

    private var snoozes: AlertSnoozes {
        get { defaults.data(forKey: Self.snoozesKey).flatMap { try? JSONDecoder().decode(AlertSnoozes.self, from: $0) } ?? AlertSnoozes() }
        set { defaults.set(try? JSONEncoder().encode(newValue), forKey: Self.snoozesKey) }
    }

    private var notified: [String: Int] {
        get { defaults.dictionary(forKey: Self.key) as? [String: Int] ?? [:] }
        set { defaults.set(newValue, forKey: Self.key) }
    }

    func requestAuthorization(_ completion: @escaping (Bool) -> Void) {
        center.requestAuthorization(options: [.alert, .sound, .badge]) { granted, _ in
            DispatchQueue.main.async { completion(granted) }
        }
    }

    /// Whether iOS lets the app notify, which can be turned off in its Settings at any time
    func isAuthorized(_ completion: @escaping (Bool) -> Void) {
        center.getNotificationSettings { settings in
            let isAuthorized = [.authorized, .provisional, .ephemeral].contains(settings.authorizationStatus)
            DispatchQueue.main.async { completion(isAuthorized) }
        }
    }

    /// A tyre's new reading, for a notification and the speech
    struct Update {
        let vehicle: Vehicle
        let location: TyreLocation
        let alerts: TyreAlerts
        let loss: PressureLoss?
        let settings: Settings
        /// Off, the speech goes on by the same rules with nothing posted
        let isPosted: Bool
    }

    /// The notifications held at their level, see [AlertAction.hold], kept in memory: after the app
    /// was ended, a notification keeps its level until its next reading. [posted] is the reading it
    /// shows, [pending] the latest which would lower it, applied when the hold ends. A suspended app
    /// runs no timer: then it's applied when something next wakes the app, or by that next reading.
    private struct Hold {
        let id = UUID()
        let until: Date
        let posted: Update
        let level: AlertLevel
        var pending: Update?
    }

    /// The notifications raised [AlertAction.raiseDelay] after their first reading, kept in memory.
    /// That's within the time iOS leaves a suspended app after a report.
    private struct Raise {
        let id = UUID()
        var update: Update
    }

    private var holds = [String: Hold]()
    private var raises = [String: Raise]()
    /// What would show with notifications turned off, for the speech
    private var unposted = [String: AlertAction.Shown]()
    weak var speaker: AlertSpeaker?

    func update(_ update: Update) {
        if update.isPosted { remindIfUnlocked() }
        shown(isPosted: update.isPosted) { shown in
            AlertClass.allCases.forEach { self.notify(update, $0, shown: shown) }
        }
    }

    /// The thresholds changed: the notification, its hold notwithstanding, and its speech come down to
    /// the latest reading's level at once, nothing being raised
    func reevaluate(_ update: Update) {
        shown(isPosted: update.isPosted) { shown in
            AlertClass.allCases.forEach { self.reevaluate(update, $0, shown: shown) }
        }
    }

    /// What the notifications show, by identifier
    private func shown(isPosted: Bool, _ then: @escaping ([String: AlertAction.Shown]) -> Void) {
        guard isPosted else { return then(unposted) }
        center.getDeliveredNotifications { delivered in
            let shown = delivered.reduce(into: [String: AlertAction.Shown]()) { result, notification in
                let info = notification.request.content.userInfo
                guard let sensorId = info[Self.sensorIdKey] as? Int,
                      let level = (info[Self.levelKey] as? Int).flatMap(AlertLevel.init(rawValue:))
                else { return }
                result[notification.request.identifier] = AlertAction.Shown(sensorId: sensorId, level: level)
            }
            DispatchQueue.main.async { then(shown) }
        }
    }

    private func notify(_ update: Update, _ alertClass: AlertClass, shown: [String: AlertAction.Shown], isSpoken: Bool = true, isRaiseDue: Bool = false) {
        guard let sensorId = update.alerts.latest?.sensorId else { return }
        let id = identifier(update.vehicle, update.location, alertClass)
        let current = shown[id]
        let isHeld = holds[id].map { $0.until > Date() } ?? false
        let action = AlertAction.of(update.alerts, alertClass, shown: current, isHeld: isHeld) { isSnoozed(sensorId, alertClass, $0) }
        if isRaiseDue == false {
            if action.raises(shown: current, sensorId: sensorId) {
                // The first raising reading's delay, a sensor reporting faster still alerts
                if raises[id] != nil {
                    raises[id]?.update = update
                } else {
                    let raise = Raise(update: update)
                    raises[id] = raise
                    DispatchQueue.main.asyncAfter(deadline: .now() + AlertAction.raiseDelay) { [weak self] in
                        guard let self, raises[id]?.id == raise.id, let due = raises.removeValue(forKey: id)?.update else { return }
                        self.shown(isPosted: due.isPosted) { self.notify(due, alertClass, shown: $0, isRaiseDue: true) }
                    }
                }
                return
            }
            // A reading which doesn't raise it came meanwhile: the raising one was a single one
            raises[id] = nil
        }
        switch action {
        case .none:
            // Tapped and over: the next one is a new alert, see below
            if update.alerts.notifiable[alertClass] == nil, notified[id] != nil { remove(id) }
        case .cancel:
            remove(id)
            holds[id] = nil
        case let .post(level, sound):
            // Tapping one removes it on iOS, unlike on Android: that's no new amber alert to sound for
            let isTapped = current == nil && level == .amber && notified[id] != nil
            post(update, alertClass, id: id, level: level, sound: sound && isTapped == false)
            startHold(id, alertClass, update, level)
        case .hold:
            guard var hold = holds[id] else { break }
            hold.pending = update
            holds[id] = hold
            // Still at its level, telling the latest reading too, which the main screen shows
            post(hold.posted, alertClass, id: id, level: hold.level, sound: false, now: update.alerts.latest)
        }
        // Unlike the notification, the speech follows the reading right away
        if isSpoken { speak(update, alertClass, action, current: current) }
    }

    private func startHold(_ id: String, _ alertClass: AlertClass, _ update: Update, _ level: AlertLevel) {
        let hold = Hold(until: Date().addingTimeInterval(AlertAction.hold), posted: update, level: level)
        holds[id] = hold
        DispatchQueue.main.asyncAfter(deadline: .now() + AlertAction.hold) { [weak self] in
            guard let self, holds[id]?.id == hold.id, let pending = holds.removeValue(forKey: id)?.pending else {
                if self?.holds[id]?.id == hold.id { self?.holds[id] = nil }
                return
            }
            // The latest reading which would have lowered it goes through now
            self.shown(isPosted: pending.isPosted) { self.notify(pending, alertClass, shown: $0, isSpoken: false) }
        }
    }

    private func speak(_ update: Update, _ alertClass: AlertClass, _ action: AlertAction, current: AlertAction.Shown?) {
        let tag = speechTag(update.vehicle, update.location, alertClass)
        let posted: (level: AlertLevel, sound: Bool)? = if case let .post(level, sound) = action { (level, sound) } else { nil }
        let held: AlertLevel? = if case let .hold(level) = action { level } else { nil }
        switch posted?.level ?? held {
        case .crimson?:
            if posted?.sound == true { speaker?.crimson(tag, alertClass, sounded: update.isPosted) }
        case .red?:
            // Coming down from crimson, said as red although its notification is held silently
            let isSounding = posted?.sound == true
            if isSounding || current?.level == .crimson { speaker?.red(tag, alertClass, sounded: isSounding && update.isPosted) }
        default:
            speaker?.stop(tag: tag)
        }
    }

    private func reevaluate(_ update: Update, _ alertClass: AlertClass, shown: [String: AlertAction.Shown]) {
        guard let sensorId = update.alerts.latest?.sensorId else { return }
        let id = identifier(update.vehicle, update.location, alertClass)
        guard let current = shown[id] else { return }
        let tag = speechTag(update.vehicle, update.location, alertClass)
        // About a reading already stored, which a threshold change doesn't alert about
        raises[id] = nil
        switch AlertAction.reevaluation(update.alerts, alertClass, shown: current, isSnoozed: { isSnoozed(sensorId, alertClass, $0) }) {
        case .cancel:
            remove(id)
            holds[id] = nil
            speaker?.stop(tag: tag)
        case let .post(level, _):
            post(update, alertClass, id: id, level: level, sound: false)
            // A reading held back is older than the one just re-evaluated
            holds[id] = nil
            if level < current.level {
                speaker?.stop(tag: tag)
                if level == .red { speaker?.red(tag, alertClass, sounded: false) }
            }
        case .none, .hold:
            break
        }
    }

    private func post(_ update: Update, _ alertClass: AlertClass, id: String, level: AlertLevel, sound: Bool, now: SensorReading? = nil) {
        guard let reading = update.alerts.latest else { return }
        var notified = notified
        notified[id] = level.rawValue
        self.notified = notified
        unposted[id] = AlertAction.Shown(sensorId: reading.sensorId, level: level)
        guard update.isPosted else { return }
        let content = UNMutableNotificationContent()
        content.title = title(update, alertClass, level)
        content.subtitle = update.vehicle.name
        content.body = "\(update.location.name): \(details(update, alertClass, reading, now: now))"
        content.sound = sound ? .default : nil
        // Passive updates what's there without a banner or lighting the screen, as Android's do
        content.interruptionLevel = sound == false ? .passive : Self.interruptionLevel(level)
        content.threadIdentifier = update.vehicle.id.uuidString
        content.categoryIdentifier = Self.category(level)
        content.userInfo = [
            Self.sensorIdKey: reading.sensorId,
            Self.alertClassKey: alertClass.rawValue,
            Self.levelKey: level.rawValue,
            Self.speechTagKey: speechTag(update.vehicle, update.location, alertClass),
        ]
        center.add(UNNotificationRequest(identifier: id, content: content, trigger: nil))
        contents[id] = content
        // A sound heard with the phone unlocked, a banner over the screen, was seen
        if sound {
            if level >= .red && isLocked { unseen.insert(id) } else { unseen.remove(id) }
        }
    }

    private func remove(_ id: String) {
        var notified = notified
        notified.removeValue(forKey: id)
        self.notified = notified
        unposted[id] = nil
        contents[id] = nil
        unseen.remove(id)
        center.removeDeliveredNotifications(withIdentifiers: [id])
    }

    private func isSnoozed(_ sensorId: Int, _ alertClass: AlertClass, _ level: AlertLevel) -> Bool {
        snoozes.isSnoozed(sensorId: sensorId, alertClass, level, at: Date().timeIntervalSince1970)
    }

    /// Dismisses the alert [response] is about, for what it picked or, swiped away, the level's shorter
    /// period. The tag to stop saying it, nil when [response] dismisses nothing. Like the Android app's,
    /// the next reading of a level no longer snoozed alerts again, its notification sounding.
    func snooze(_ response: UNNotificationResponse) -> String? {
        let request = response.notification.request
        let id = request.identifier
        let info = request.content.userInfo
        guard let sensorId = info[Self.sensorIdKey] as? Int,
              let alertClass = (info[Self.alertClassKey] as? String).flatMap(AlertClass.init(rawValue:)),
              let level = (info[Self.levelKey] as? Int).flatMap(AlertLevel.init(rawValue:)),
              let duration = response.actionIdentifier == UNNotificationDismissActionIdentifier
                ? level.snoozes.first
                : Self.snoozeDuration(response.actionIdentifier)
        else { return nil }
        var snoozes = snoozes
        snoozes.snooze(sensorId: sensorId, alertClass, level, for: duration, at: Date().timeIntervalSince1970)
        self.snoozes = snoozes
        remove(id)
        holds[id] = nil
        raises[id] = nil
        return info[Self.speechTagKey] as? String
    }

    private static func category(_ level: AlertLevel) -> String { "alert\(level.rawValue)" }

    /// Told when one is swiped away, and offering what to dismiss it for, on a long press. The speech's
    /// backup is told too: swiped away, it silences the speech, see Monitor.answered.
    private static var categories: Set<UNNotificationCategory> {
        Set(AlertLevel.allCases.map { level in
            UNNotificationCategory(
                identifier: category(level),
                actions: level.snoozes.map { UNNotificationAction(identifier: "\(snoozeActionPrefix)\($0)", title: "Dismiss \(label($0))") },
                intentIdentifiers: [],
                options: .customDismissAction
            )
        } + [UNNotificationCategory(identifier: SpeechBackup.category, actions: [], intentIdentifiers: [], options: .customDismissAction)])
    }

    private static func snoozeDuration(_ action: String) -> Double? {
        action.hasPrefix(snoozeActionPrefix) ? Double(action.dropFirst(snoozeActionPrefix.count)) : nil
    }

    private static func label(_ duration: Double) -> String {
        switch duration {
        case ..<86400: "\(Int(duration / 60)) min"
        case 86400: "1 day"
        default: "1 week"
        }
    }

    func clear(vehicle: Vehicle, location: TyreLocation) {
        AlertClass.allCases.map { identifier(vehicle, location, $0) }.forEach { id in
            remove(id)
            holds[id] = nil
            raises[id] = nil
        }
    }

    func clearAll() {
        center.removeAllDeliveredNotifications()
        notified = [:]
        unseen = []
        contents = [:]
        unposted = [:]
        holds = [:]
        raises = [:]
    }

    /// Replaces the one already there, if any. Silent, but time sensitive: a ride's start is when
    /// Driving Focus comes on, which would hold it back until the ride is over.
    func postRidePrompt() {
        let content = UNMutableNotificationContent()
        content.body = "Going for a ride? Open Persistent TPMS to monitor tyre pressure"
        content.interruptionLevel = .timeSensitive
        center.add(UNNotificationRequest(identifier: Self.ridePromptId, content: content, trigger: nil)) { error in
            rideLog("notification added, error: \(String(describing: error))")
        }
    }

    /// A red or critical alert's notification still shows, which the speech backup goes on for
    func isAlertShowing() async -> Bool {
        await center.deliveredNotifications().contains { notification in
            (notification.request.content.userInfo[Self.levelKey] as? Int).flatMap(AlertLevel.init(rawValue:)).map { $0 >= .red } == true
        }
    }

    func isRidePromptShowing() async -> Bool {
        await center.deliveredNotifications().contains { $0.request.identifier == Self.ridePromptId }
    }

    func clearRidePrompt() {
        center.removeDeliveredNotifications(withIdentifiers: [Self.ridePromptId])
    }

    /// Notifications were refused for this app, in the system's settings or the first time it asked
    func isDenied(_ completion: @escaping (Bool) -> Void) {
        center.getNotificationSettings { settings in
            DispatchQueue.main.async { completion(settings.authorizationStatus == .denied) }
        }
    }

    private func identifier(_ vehicle: Vehicle, _ location: TyreLocation, _ alertClass: AlertClass) -> String {
        "\(vehicle.id.uuidString)-\(location.code)-\(alertClass.rawValue)"
    }

    private func speechTag(_ vehicle: Vehicle, _ location: TyreLocation, _ alertClass: AlertClass) -> String {
        "\(vehicle.id)-\(location.code)-\(alertClass.rawValue)"
    }

    /// The condition alone, short enough not to be cut off, like the Android app's
    private func title(_ update: Update, _ alertClass: AlertClass, _ level: AlertLevel) -> String {
        let isHigh = update.alerts.isHighPressure
        return switch alertClass {
        case .pressure:
            switch level {
            case .amber: isHigh ? "Pressure getting high" : "Pressure getting low"
            case .red: isHigh ? "Pressure high" : "Pressure low"
            case .crimson: isHigh ? "Pressure critically high" : "Pressure critically low"
            }
        case .temperature:
            switch level {
            case .amber: "Tyre getting hot"
            case .red: "Tyre hot"
            case .crimson: "Tyre critically hot"
            }
        case .battery: level == .amber ? "Sensor battery getting low" : "Sensor battery low"
        case .pressureLoss: "Losing pressure"
        case .sensorAlarm: "May be leaking"
        }
    }

    /// The value [alertClass] is about, as [reading] has it
    private func value(_ alertClass: AlertClass, _ reading: SensorReading, _ settings: Settings) -> String {
        switch alertClass {
        case .temperature: reading.temperature.string(settings.temperatureUnit)
        case .battery: reading.batteryVoltage?.string ?? reading.batteryPercent.map { "\($0)%" } ?? ""
        case .pressure, .pressureLoss, .sensorAlarm: reading.pressure.string(settings.pressureUnit)
        }
    }

    /// [now] is a newer reading the main screen already shows: "21.0 psi (now 22.4 psi)"
    private func details(_ update: Update, _ alertClass: AlertClass, _ reading: SensorReading, now: SensorReading?) -> String {
        let settings = update.settings
        let unit = settings.pressureUnit
        let thresholds = update.vehicle.thresholds(update.location)
        let values = value(alertClass, reading, settings) + (now.map { " (now \(value(alertClass, $0, settings)))" } ?? "")
        switch alertClass {
        case .pressure:
            var text = "\(values), " + (update.alerts.isHighPressure ? "max \(thresholds.highPressure.string(unit))" : "min \(thresholds.lowPressure.string(unit))")
            // Not notified on their own then, see AlertAction
            if update.alerts.foldsLeaks {
                if let loss = update.loss { text += ", \(summary(loss, unit))" }
                if update.alerts.levels[.sensorAlarm] != nil { text += ", sensor alarm" }
            }
            return text
        case .temperature:
            return "\(values), hot from \(thresholds.highTemp.string(settings.temperatureUnit))"
        case .battery:
            return reading.batteryVoltage != nil
                ? "\(values), alarm at \(thresholds.lowBatteryVoltage.alarmAt(reading.temperature).string)"
                : "\(values), alarm at \(thresholds.lowBatteryPercent)%"
        case .pressureLoss:
            return update.loss.map { "\(summary($0, unit)), now \(reading.pressure.string(unit))" } ?? reading.pressure.string(unit)
        case .sensorAlarm:
            return "the sensor raised its own alarm, at \(reading.pressure.string(unit))"
        }
    }

    private func summary(_ loss: PressureLoss, _ unit: PressureUnit) -> String {
        "down \(loss.drop.string(unit)) in \(max(1, Int(((loss.until - loss.since) / 60).rounded()))) min"
    }
}
