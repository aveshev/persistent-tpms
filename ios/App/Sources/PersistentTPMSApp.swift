import SwiftUI
import UIKit

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

final class AppDelegate: NSObject, UIApplicationDelegate {
    func application(_ application: UIApplication, didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil) -> Bool {
        // Also when iOS relaunches the app in the background for a sensor, through Bluetooth state
        // restoration: the scan has to start again before this returns
        Monitor.shared.start()
        return true
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
