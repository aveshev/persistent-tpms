import Foundation
import TPMSCore

/// The app's settings, in UserDefaults
final class Settings: ObservableObject {
    private let defaults = UserDefaults.standard

    @Published var pressureUnit: PressureUnit {
        didSet { defaults.set(pressureUnit.rawValue, forKey: "pressureUnit") }
    }

    @Published var temperatureUnit: TemperatureUnit {
        didSet { defaults.set(temperatureUnit.rawValue, forKey: "temperatureUnit") }
    }

    /// Notifications for the tyres' alerts, which take the user's permission: off until asked for
    @Published var alerts: Bool {
        didSet { defaults.set(alerts, forKey: "alerts") }
    }

    /// Keeps listening to the sensors once the app is in the background
    @Published var backgroundMonitoring: Bool {
        didSet { defaults.set(backgroundMonitoring, forKey: "backgroundMonitoring") }
    }

    // MARK: Ride notification, off until turned on like the alerts' notifications

    /// The switch over the triggers below, which keep their state while it's off
    @Published var rideNotification: Bool {
        didSet { defaults.set(rideNotification, forKey: "rideNotification") }
    }

    /// Never touched yet: turning it on for the first time also turns on what needs no setup
    var isRideNotificationNew: Bool { defaults.object(forKey: "rideNotification") == nil }

    /// Invites to open the app when a bound sensor is heard after a long gap
    @Published var rideSensors: Bool {
        didSet { defaults.set(rideSensors, forKey: "rideSensors") }
    }

    /// Invites to open the app when one of [rideBeaconDevices] comes back nearby
    @Published var rideBeacon: Bool {
        didSet { defaults.set(rideBeacon, forKey: "rideBeacon") }
    }

    @Published var rideBeaconDevices: [RideBeacon] {
        didSet { defaults.set(try? JSONEncoder().encode(rideBeaconDevices), forKey: "rideBeaconDevices") }
    }

    /// The Shortcuts action invites to open the app. It's recorded as run either way.
    @Published var rideShortcut: Bool {
        didSet { defaults.set(rideShortcut, forKey: "rideShortcut") }
    }

    /// How long away from the vehicle before being heard again counts as back near it, shorter on the debug page to try it
    @Published var rideAbsentFor: Double {
        didSet { defaults.set(rideAbsentFor, forKey: "rideAbsentFor") }
    }

    /// How long after the last trigger an unopened notification counts as stale, shorter on the debug
    /// page to try it
    @Published var rideStaleAfter: Double {
        didSet { defaults.set(rideStaleAfter, forKey: "rideStaleAfter") }
    }

    enum Theme: String, CaseIterable, Identifiable {
        case system, light, dark
        var id: String { rawValue }
        var name: String { rawValue.capitalized }
        var systemImage: String {
            switch self {
            case .system: "circle.lefthalf.filled"
            case .light: "sun.max"
            case .dark: "moon"
            }
        }
    }

    // MARK: Display, like the Android app's AppPreferences

    @Published var theme: Theme {
        didSet { defaults.set(theme.rawValue, forKey: "theme") }
    }

    /// The readouts' text size, on top of the system's text size: 1 follows it
    @Published var readoutTextScale: Double {
        didSet { defaults.set(readoutTextScale, forKey: "readoutTextScale") }
    }

    /// Tapping a tyre shows its readout magnified for [magnifierSeconds], to read it on a shaking phone
    @Published var magnifier: Bool {
        didSet { defaults.set(magnifier, forKey: "magnifier") }
    }

    @Published var magnifierSeconds: Int {
        didSet { defaults.set(magnifierSeconds, forKey: "magnifierSeconds") }
    }

    /// Dims the main screen once nothing new was alerted or warned about, nor touched, for
    /// [dimDisplaySeconds], its brightness divided by [dimDisplayDivisor]
    @Published var dimDisplay: Bool {
        didSet { defaults.set(dimDisplay, forKey: "dimDisplay") }
    }

    @Published var dimDisplaySeconds: Int {
        didSet { defaults.set(dimDisplaySeconds, forKey: "dimDisplaySeconds") }
    }

    @Published var dimDisplayDivisor: Int {
        didSet { defaults.set(dimDisplayDivisor, forKey: "dimDisplayDivisor") }
    }

    /// While dimmed, covering the proximity sensor (a pocket) turns the screen off and ignores touches
    @Published var pocketGuard: Bool {
        didSet { defaults.set(pocketGuard, forKey: "pocketGuard") }
    }

    // MARK: Readings

    @Published var showTimeSinceUpdate: Bool {
        didSet { defaults.set(showTimeSinceUpdate, forKey: "showTimeSinceUpdate") }
    }

    /// A battery getting low shows whatever this is set to
    @Published var showBatteryVoltage: Bool {
        didSet { defaults.set(showBatteryVoltage, forKey: "showBatteryVoltage") }
    }

    @Published var showSensorId: Bool {
        didSet { defaults.set(showSensorId, forKey: "showSensorId") }
    }

    @Published var showSensorFlags: Bool {
        didSet { defaults.set(showSensorFlags, forKey: "showSensorFlags") }
    }

    /// Whether the debug options can be reached: 10 taps on the version, like the Android app
    @Published var developer: Bool {
        didSet { defaults.set(developer, forKey: "developer") }
    }

    /// What's selected on the debug page only shows while this is on, and it's kept while it's off
    @Published var debugOptions: Bool {
        didSet { defaults.set(debugOptions, forKey: "debugOptions") }
    }

    var showsSensorId: Bool { developer && debugOptions && showSensorId }
    var showsSensorFlags: Bool { developer && debugOptions && showSensorFlags }

    /// Made-up vehicles and sensors, in a database of their own
    @Published var demoMode: Bool {
        didSet { defaults.set(demoMode, forKey: Self.demoModeKey) }
    }

    static let demoModeKey = "demoMode"

    // MARK: Alerts

    enum AlertSound: String, CaseIterable, Identifiable {
        case speech, tones, none
        var id: String { rawValue }
    }

    /// How the red and crimson tyre alerts sound, on top of their notifications
    @Published var alertSound: AlertSound {
        didSet {
            defaults.set(alertSound.rawValue, forKey: "alertSound")
            if alertSound != .none { lastAlertSound = alertSound }
        }
    }

    /// The one its switch turns back on
    @Published var lastAlertSound: AlertSound {
        didSet { defaults.set(lastAlertSound.rawValue, forKey: "lastAlertSound") }
    }

    /// Vibrates along with the alerts' sound, in the same loops. Works with the sound off too.
    @Published var vibrateAlerts: Bool {
        didSet { defaults.set(vibrateAlerts, forKey: "vibrateAlerts") }
    }

    /// Warns about a tyre losing at least [pressureLossMinDrop] while riding. Experimental, so off
    /// by default.
    @Published var pressureLoss: Bool {
        didSet { defaults.set(pressureLoss, forKey: "pressureLoss") }
    }

    @Published var pressureLossMinDrop: Pressure {
        didSet { defaults.set(pressureLossMinDrop.kpa, forKey: "pressureLossMinDropKpa") }
    }

    init() {
        // Like the Android app's UnitPreferences
        let region = Locale.current.region?.identifier
        pressureUnit = defaults.string(forKey: "pressureUnit").flatMap(PressureUnit.init(rawValue:))
            ?? (["US", "GB", "CA", "AU"].contains(region) ? .psi : .bar)
        temperatureUnit = defaults.string(forKey: "temperatureUnit").flatMap(TemperatureUnit.init(rawValue:))
            ?? (region == "US" ? .fahrenheit : .celsius)
        alerts = defaults.bool(forKey: "alerts")
        backgroundMonitoring = defaults.object(forKey: "backgroundMonitoring") as? Bool ?? true
        rideSensors = defaults.bool(forKey: "rideSensors")
        rideBeacon = defaults.bool(forKey: "rideBeacon")
        // A single device was kept before
        rideBeaconDevices = defaults.data(forKey: "rideBeaconDevices").flatMap { try? JSONDecoder().decode([RideBeacon].self, from: $0) }
            ?? defaults.data(forKey: "rideBeaconDevice").flatMap { try? JSONDecoder().decode(RideBeacon.self, from: $0) }.map { [$0] }
            ?? []
        rideShortcut = defaults.bool(forKey: "rideShortcut")
        // Before this switch, turning a trigger on was enough
        let isAnyTriggerOn = ["rideSensors", "rideBeacon", "rideShortcut"].contains { UserDefaults.standard.bool(forKey: $0) }
        rideNotification = UserDefaults.standard.object(forKey: "rideNotification") as? Bool ?? isAnyTriggerOn
        rideAbsentFor = defaults.object(forKey: "rideAbsentFor") as? Double ?? RideTriggers.defaultAbsentFor
        rideStaleAfter = defaults.object(forKey: "rideStaleAfter") as? Double ?? RideTriggers.defaultStaleAfter
        theme = defaults.string(forKey: "theme").flatMap(Theme.init(rawValue:)) ?? .system
        readoutTextScale = defaults.object(forKey: "readoutTextScale") as? Double ?? 1
        magnifier = defaults.object(forKey: "magnifier") as? Bool ?? true
        magnifierSeconds = defaults.object(forKey: "magnifierSeconds") as? Int ?? 5
        dimDisplay = defaults.object(forKey: "dimDisplay") as? Bool ?? true
        dimDisplaySeconds = defaults.object(forKey: "dimDisplaySeconds") as? Int ?? 60
        dimDisplayDivisor = defaults.object(forKey: "dimDisplayDivisor") as? Int ?? 50
        pocketGuard = defaults.object(forKey: "pocketGuard") as? Bool ?? true
        showTimeSinceUpdate = defaults.object(forKey: "showTimeSinceUpdate") as? Bool ?? true
        showBatteryVoltage = defaults.bool(forKey: "showBatteryVoltage")
        showSensorId = defaults.bool(forKey: "showSensorId")
        showSensorFlags = defaults.bool(forKey: "showSensorFlags")
        developer = defaults.bool(forKey: "developer")
        debugOptions = defaults.object(forKey: "debugOptions") as? Bool ?? defaults.bool(forKey: "showSensorId")
        demoMode = defaults.bool(forKey: Self.demoModeKey)
        // Off until turned on, unlike on Android: a phone shouldn't start talking on its own
        alertSound = defaults.string(forKey: "alertSound").flatMap(AlertSound.init(rawValue:)) ?? .none
        lastAlertSound = defaults.string(forKey: "lastAlertSound").flatMap(AlertSound.init(rawValue:)) ?? .speech
        vibrateAlerts = defaults.bool(forKey: "vibrateAlerts")
        pressureLoss = defaults.bool(forKey: "pressureLoss")
        pressureLossMinDrop = Pressure(kpa: defaults.object(forKey: "pressureLossMinDropKpa") as? Float ?? PressureLossTracker.defaultMinDrop.kpa)
    }
}
