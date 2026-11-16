import Foundation
import TPMSCore
import UIKit

/// The "Going for a ride?" notification's triggers, their times kept across launches: iOS may have
/// ended the app between two reports. Everything runs on the main thread.
final class RidePrompts: ObservableObject {
    @Published private(set) var triggers: RideTriggers {
        didSet { (try? JSONEncoder().encode(triggers)).map { defaults.set($0, forKey: Self.triggersKey) } }
    }

    /// The app can't see whether the automation exists, only when it last ran
    @Published private(set) var lastShortcut: Date? {
        didSet { defaults.set(lastShortcut, forKey: Self.lastShortcutKey) }
    }

    private let settings: Settings
    private let notifier: Notifier
    private let defaults = UserDefaults.standard
    private static let triggersKey = "rideTriggers"
    private static let lastShortcutKey = "rideLastShortcut"

    init(settings: Settings, notifier: Notifier) {
        self.settings = settings
        self.notifier = notifier
        triggers = defaults.data(forKey: Self.triggersKey).flatMap { try? JSONDecoder().decode(RideTriggers.self, from: $0) } ?? RideTriggers()
        lastShortcut = defaults.object(forKey: Self.lastShortcutKey) as? Date
    }

    /// A bound sensor of [vehicle] was heard. Each vehicle has its own absence: a neighbouring car's
    /// sensor heard all day doesn't hide being back near the bike.
    func sensorHeard(vehicle: UUID, rssi: Int) {
        guard settings.rideNotification, settings.rideSensors else { return }
        let isBack = triggers.heard(RideTriggers.sensors(of: vehicle), rssi: rssi, at: now, absentFor: settings.rideAbsentFor)
        if isBack || UIApplication.shared.applicationState != .active { rideLog("sensor of \(vehicle) at \(rssi) dBm, back near the vehicle: \(isBack)") }
        if isBack { Task { @MainActor in await prompt() } }
    }

    /// Whichever beacon: all of them count as one, so that two on a vehicle make a single notification
    func beaconHeard(rssi: Int, needsConfirming: Bool) {
        removeIfStale()
        guard settings.rideNotification, settings.rideBeacon else { return }
        let isBack = triggers.heard(RideTriggers.beacon, rssi: rssi, needsConfirming: needsConfirming, at: now, absentFor: settings.rideAbsentFor)
        if isBack || UIApplication.shared.applicationState != .active { rideLog("beacon at \(rssi) dBm, back near the vehicle: \(isBack)") }
        if isBack { Task { @MainActor in await prompt() } }
    }

    /// A beacon woken by connecting was probably in range since it was last detected: no prompt
    func beaconStillThere() {
        rideLog("beacon still there")
        removeIfStale()
        triggers.stillThere(RideTriggers.beacon, at: now)
    }

    /// The Shortcuts action ran. It can't tell being back near the vehicle, so a device reconnecting is only held back
    /// by the notification still showing. Returns once done, the app being suspended after it.
    @MainActor
    func shortcutRan() async {
        rideLog("shortcut ran")
        lastShortcut = Date()
        if settings.rideNotification, settings.rideShortcut { await prompt() }
    }

    /// While the app is open it has nothing to invite to: only the time heard is kept
    @MainActor
    private func prompt() async {
        guard UIApplication.shared.applicationState != .active, Demo.isOn == false else {
            rideLog("no prompt, app open or demo")
            return
        }
        let isShowing = await notifier.isRidePromptShowing()
        let posts = triggers.fired(at: now, isShowing: isShowing, staleAfter: settings.rideStaleAfter)
        rideLog("fired, showing: \(isShowing), posts: \(posts)")
        guard posts else { return }
        // Removed first: only a new notification makes a banner and goes up the list for sure
        notifier.clearRidePrompt()
        notifier.postRidePrompt()
    }

    /// iOS can't expire a notification and a suspended app has no timer: this is called on whatever
    /// wakes the app, so an unopened notification comes down then
    func removeIfStale() {
        guard triggers.expired(at: now, staleAfter: settings.rideStaleAfter) else { return }
        rideLog("stale, removed")
        notifier.clearRidePrompt()
    }

    /// The notification did its job, or is pointless now
    func appOpened() {
        notifier.clearRidePrompt()
    }

    func forgetSensors() {
        triggers.forgetSensors()
    }

    func forgetBeacon() {
        triggers.forget(RideTriggers.beacon)
    }

    private var now: Double { Date().timeIntervalSince1970 }
}

private let launchedAt = Date()

/// Debug builds keep what the triggers do with the app's state, in Documents/ride.log, which
/// `xcrun devicectl device copy from` gets from the phone: whether a trigger fired suspended or after
/// a relaunch can't be seen from the screen. Every sensor packet goes there too, before any filter,
/// with when the app is opened and left and the phone locked and unlocked: what a locked or open phone
/// catches is compared from it.
func rideLog(_ message: @autoclosure () -> String) {
    #if DEBUG
    let state = switch UIApplication.shared.applicationState {
    case .active: "active"
    case .inactive: "inactive"
    case .background: "background"
    @unknown default: "unknown"
    }
    let line = "\(Date().ISO8601Format()) \(state) up \(Int(Date().timeIntervalSince(launchedAt)))s: \(message())\n"
    let url = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0].appendingPathComponent("ride.log")
    if let handle = try? FileHandle(forWritingTo: url) {
        _ = try? handle.seekToEnd()
        handle.write(Data(line.utf8))
        try? handle.close()
    } else {
        try? Data(line.utf8).write(to: url, options: .noFileProtection)
    }
    #endif
}
