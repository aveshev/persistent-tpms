import SwiftUI
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
        }
        .onChange(of: scenePhase) { _, phase in
            Monitor.shared.appIsActive(phase == .active)
        }
    }
}

final class AppDelegate: NSObject, UIApplicationDelegate, UNUserNotificationCenterDelegate {
    func application(_ application: UIApplication, didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil) -> Bool {
        // Before this returns, or the tap that launched the app isn't told
        UNUserNotificationCenter.current().delegate = self
        // Also when iOS relaunches the app in the background for a sensor, through Bluetooth state
        // restoration: the scan has to start again before this returns
        Monitor.shared.start()
        return true
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
