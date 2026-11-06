import SwiftUI
import TPMSCore
import UIKit

/// Darkens the screen once nothing new was alerted or warned about, nor touched, for a while, like
/// the Android app's DimWhenIdle. A red or critical alert going on keeps it bright, a
/// new alert or warning brings it back up. When dimmed, a first touch only brightens the screen.
/// It's black over the content, with the status bar and the home indicator hidden, rather than a lower
/// brightness: iOS keeps adjusting the brightness to the light, against a level set here, and an app can't
/// tell what it would have chosen when brightening. On an OLED screen, black saves as much.
/// While dimmed, covering the proximity sensor (a pocket) turns the screen off and ignores touches.
/// The phone doesn't lock itself while the main screen is shown, like the Android app's KeepScreenOn.
struct DimWhenIdle: ViewModifier {
    @ObservedObject var monitor: Monitor
    @ObservedObject var settings: Settings
    /// The main screen is shown, nothing over it
    let isShown: Bool
    @State private var lastActivity = Date()
    @Binding var isDimmed: Bool

    func body(content: Content) -> some View {
        let levels = currentLevels
        content
            .simultaneousGesture(DragGesture(minimumDistance: 0).onChanged { _ in lastActivity = Date() })
            .overlay {
                if isDimmed {
                    // Takes the first touch, which only brightens
                    Color.black.opacity(DimOverlay.alpha(divisor: settings.dimDisplayDivisor))
                        .contentShape(Rectangle())
                        .onTapGesture { wake() }
                        .ignoresSafeArea()
                }
            }
            .task(id: isShown && settings.dimDisplay) {
                guard isShown, settings.dimDisplay else { return wake() }
                lastActivity = Date()
                while Task.isCancelled == false {
                    if isDimmed == false, isAlerting == false, Date().timeIntervalSince(lastActivity) >= Double(settings.dimDisplaySeconds) {
                        dim()
                    }
                    try? await Task.sleep(for: .seconds(1))
                }
            }
            // A new warning or alert, or a worse one, of the vehicle shown
            .onChange(of: levels) { previous, levels in
                if levels.contains(where: { key, level in previous[key].map { level > $0 } ?? true }) { wake() }
            }
            .onChange(of: isAlerting) { _, isAlerting in if isAlerting { wake() } }
            .statusBarHidden(isDimmed)
            .persistentSystemOverlays(isDimmed ? .hidden : .automatic)
            .onChange(of: isShown, initial: true) { _, isShown in
                UIApplication.shared.isIdleTimerDisabled = isShown
            }
    }

    /// The current vehicle's tyres' levels, by tyre and class
    private var currentLevels: [String: AlertLevel] {
        guard let vehicle = monitor.current else { return [:] }
        return vehicle.kind.locations.reduce(into: [:]) { levels, location in
            monitor.tyres[Monitor.TyreKey(vehicleId: vehicle.id, location: location)]?.alerts.levels.forEach { alertClass, level in
                levels["\(location.code)-\(alertClass.rawValue)"] = level
            }
        }
    }

    private var isAlerting: Bool { monitor.isAlerting() }

    private func dim() {
        isDimmed = true
        UIDevice.current.isProximityMonitoringEnabled = settings.pocketGuard
    }

    private func wake() {
        lastActivity = Date()
        UIDevice.current.isProximityMonitoringEnabled = false
        guard isDimmed else { return }
        isDimmed = false
    }
}

/// Where the alerts' silence button shows, clear of the readings, see ClearOfReadouts. Touches go
/// through, to DimWhenIdle's overlay.
struct DimmedNotice: View {
    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: "sun.max").font(.title2)
            VStack {
                Text("Screen dimmed").font(.title3.weight(.semibold))
                Text("Tap anywhere to un-dim").font(.callout)
            }
            .lineLimit(1)
            .minimumScaleFactor(0.6)
        }
        .padding(.horizontal, 20)
        .padding(.vertical, 8)
        .background(.regularMaterial, in: Capsule())
        .allowsHitTesting(false)
    }
}
