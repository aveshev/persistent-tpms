import SwiftUI
import TPMSCore
import UIKit

/// Lowers the screen's brightness once nothing new was alerted or warned about, nor touched, for a
/// while, like the Android app's DimWhenIdle. A red or critical alert going on keeps it bright, a
/// new alert or warning brings it back up. When dimmed, a first touch only brightens the screen.
/// At its lowest already, the screen is made darker over the content too, which wouldn't look dimmed
/// otherwise, though the status bar stays as it is.
/// While dimmed, covering the proximity sensor (a pocket) turns the screen off and ignores touches.
/// The phone doesn't lock itself while the main screen is shown, like the Android app's KeepScreenOn.
struct DimWhenIdle: ViewModifier {
    @ObservedObject var monitor: Monitor
    @ObservedObject var settings: Settings
    /// The main screen is shown, nothing over it
    let isShown: Bool
    @State private var lastActivity = Date()
    @Binding var isDimmed: Bool
    /// The brightness before dimming, given back when brightening
    @State private var brightness: CGFloat?
    /// How dark the screen is made over its content, dimmed at the lowest brightness already
    private static let darkenedAlpha = 0.3
    /// So low that dividing it can barely, if at all, be told apart on the screen
    private static let lowBrightness: CGFloat = 0.05

    func body(content: Content) -> some View {
        let levels = currentLevels
        content
            .simultaneousGesture(DragGesture(minimumDistance: 0).onChanged { _ in lastActivity = Date() })
            .overlay {
                if isDimmed {
                    // Takes the first touch, which only brightens
                    Color.black.opacity(isLow ? Self.darkenedAlpha : 0.001)
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
            .onChange(of: settings.dimDisplayDivisor) { _, _ in if isDimmed { brightness.map { screen?.brightness = $0 / CGFloat(settings.dimDisplayDivisor) } } }
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

    private var isLow: Bool { (brightness ?? 1) <= Self.lowBrightness }

    private var screen: UIScreen? {
        UIApplication.shared.connectedScenes.compactMap { ($0 as? UIWindowScene)?.screen }.first
    }

    private func dim() {
        guard let screen else { return }
        brightness = screen.brightness
        screen.brightness /= CGFloat(settings.dimDisplayDivisor)
        isDimmed = true
        UIDevice.current.isProximityMonitoringEnabled = settings.pocketGuard
    }

    private func wake() {
        lastActivity = Date()
        UIDevice.current.isProximityMonitoringEnabled = false
        guard isDimmed else { return }
        brightness.map { screen?.brightness = $0 }
        brightness = nil
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
