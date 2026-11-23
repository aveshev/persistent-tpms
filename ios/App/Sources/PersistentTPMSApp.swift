import Combine
import SwiftUI
import TPMSCore
import UIKit
import UserNotifications

@main
struct PersistentTPMSApp: App {
    @UIApplicationDelegateAdaptor private var delegate: AppDelegate
    @Environment(\.scenePhase) private var scenePhase

    var body: some Scene {
        WindowGroup {
            MainView(monitor: Monitor.shared, settings: Monitor.shared.settings)
                .tint(Theme.primary)
                .modifier(AppTheme(settings: Monitor.shared.settings))
                // The Android app's links, tpmsadvanced://vehicle/<uuid>
                .onOpenURL { VehicleLink.vehicleId(from: $0).map(Monitor.shared.open) }
        }
        .onChange(of: scenePhase) { _, phase in
            Monitor.shared.appIsActive(phase == .active)
        }
    }
}

final class AppDelegate: NSObject, UIApplicationDelegate, UNUserNotificationCenterDelegate {
    /// What iOS shows of them on the Home Screen, under the app's icon
    private static let maxQuickActions = 4
    private var quickActions: AnyCancellable?

    func application(_ application: UIApplication, didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil) -> Bool {
        // Before this returns, or the tap that launched the app isn't told
        UNUserNotificationCenter.current().delegate = self
        // Also when iOS relaunches the app in the background for a sensor, through Bluetooth state
        // restoration: the scan has to start again before this returns
        Monitor.shared.start(isRestored: launchOptions?[.bluetoothCentrals] != nil)
        // A quick action per vehicle, like the Android app's launcher shortcuts (ShortcutUseCase)
        // Ranked like them too: the alerting vehicles, then the latest read
        quickActions = Publishers.CombineLatest3(Monitor.shared.$vehicles, Monitor.shared.$ongoing, Monitor.shared.$tyres)
            .map { vehicles, ongoing, tyres in
                let now = Date()
                return VehicleShortcut.list(
                    vehicles,
                    alerting: Dictionary(uniqueKeysWithValues: vehicles.compactMap { vehicle in
                        ongoing.worst(of: vehicle.id, now: now).map { (vehicle.id, $0) }
                    }),
                    latestReadings: tyres.reduce(into: [:]) { latest, entry in
                        guard let timestamp = entry.value.latest?.timestamp else { return }
                        latest[entry.key.vehicleId] = max(latest[entry.key.vehicleId] ?? timestamp, timestamp)
                    },
                    limit: Self.maxQuickActions
                )
            }
            .removeDuplicates()
            .sink { shortcuts in
                application.shortcutItems = shortcuts.map {
                    UIApplicationShortcutItem(
                        type: VehicleLink.url(for: $0.id).absoluteString,
                        localizedTitle: $0.name,
                        localizedSubtitle: nil,
                        icon: UIApplicationShortcutIcon(systemImageName: $0.kind.quickActionSymbol)
                    )
                }
            }
        return true
    }

    /// Only for [SceneDelegate], which is told of the quick actions: SwiftUI's App isn't
    func application(_ application: UIApplication, configurationForConnecting connectingSceneSession: UISceneSession, options: UIScene.ConnectionOptions) -> UISceneConfiguration {
        let configuration = UISceneConfiguration(name: nil, sessionRole: connectingSceneSession.role)
        configuration.delegateClass = SceneDelegate.self
        return configuration
    }

    func applicationProtectedDataWillBecomeUnavailable(_ application: UIApplication) {
        rideLog("phone locked")
    }

    func applicationProtectedDataDidBecomeAvailable(_ application: UIApplication) {
        rideLog("phone unlocked")
    }

    /// Shown while the app is open too, like on Android: the user may not be looking at the tyre. A
    /// silent update only changes what's in the list.
    func userNotificationCenter(_ center: UNUserNotificationCenter, willPresent notification: UNNotification, withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void) {
        completionHandler(notification.request.content.sound == nil ? [.list] : [.banner, .list, .sound])
    }

    func userNotificationCenter(_ center: UNUserNotificationCenter, didReceive response: UNNotificationResponse, withCompletionHandler completionHandler: @escaping () -> Void) {
        Monitor.shared.answered(response)
        completionHandler()
    }
}

/// Opens a vehicle's quick action, whose type is its link: when it launches the app, and while it runs
final class SceneDelegate: NSObject, UIWindowSceneDelegate {
    func scene(_ scene: UIScene, willConnectTo session: UISceneSession, options connectionOptions: UIScene.ConnectionOptions) {
        connectionOptions.shortcutItem.map { _ = open($0) }
    }

    func windowScene(_ windowScene: UIWindowScene, performActionFor shortcutItem: UIApplicationShortcutItem, completionHandler: @escaping (Bool) -> Void) {
        completionHandler(open(shortcutItem))
    }

    private func open(_ shortcutItem: UIApplicationShortcutItem) -> Bool {
        guard let id = URL(string: shortcutItem.type).flatMap(VehicleLink.vehicleId) else { return false }
        Monitor.shared.open(vehicleId: id)
        return true
    }
}

private extension VehicleKind {
    /// The nearest SF Symbol, as Android's shortcuts take the nearest icon they have
    var quickActionSymbol: String {
        switch self {
        case .car, .carWithSpare: "car"
        case .singleAxleTrailer: "box.truck"
        case .motorcycle, .monowheel: "motorcycle"
        case .tadpoleThreeWheeler, .deltaThreeWheeler: "car.side"
        }
    }
}

/// The settings' theme: the phone's own, or always light or dark
private struct AppTheme: ViewModifier {
    @ObservedObject var settings: Settings

    func body(content: Content) -> some View {
        let scheme: ColorScheme? = switch settings.theme {
        case .system: nil
        case .light: .light
        case .dark: .dark
        }
        return content.preferredColorScheme(scheme)
    }
}
