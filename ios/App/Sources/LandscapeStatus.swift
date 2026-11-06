import CoreTelephony
import Network
import SwiftUI

/// iOS hides its status bar on an iPhone in landscape, whatever the app asks for: what matters of it
/// on a ride, the time, the network and the battery. Drawn over the screen's top left corner, it takes
/// no room from the layout: on one line if it fits in `room`, otherwise on two, and otherwise not at all.
struct LandscapeStatus: View {
    private static let margin: CGFloat = 8

    /// How wide the box's top left corner is, a bar's height tall, up to what's in there
    let room: CGFloat

    @StateObject private var state = DeviceStatus()

    /// `room`, for what's laid out
    static func room(in box: CGFloat, of bounds: VehicleBounds?, barHeight: CGFloat) -> CGFloat {
        guard let bounds else { return box }
        return (Array(bounds.readouts.values) + bounds.frames + [bounds.image])
            .filter { $0.minY < barHeight }
            .map(\.minX)
            .min() ?? box
    }

    var body: some View {
        ViewThatFits(in: .horizontal) {
            HStack(spacing: 6) {
                time
                network
            }
            VStack(alignment: .leading, spacing: 1) {
                time
                network
            }
            Color.clear.frame(width: 0, height: 0)
        }
        .font(.system(size: 13, weight: .semibold))
        .foregroundStyle(Theme.onBackground)
        .lineLimit(1)
        .frame(width: max(0, room - Self.margin * 2), alignment: .leading)
        .padding(.leading, Self.margin)
    }

    private var time: some View {
        TimelineView(.everyMinute) { context in
            Text(context.date, format: .dateTime.hour().minute())
        }
    }

    private var network: some View {
        HStack(spacing: 6) {
            if state.isOnWifi {
                Image(systemName: "wifi").accessibilityLabel("Wi-Fi")
            } else if let radio = state.radio {
                Text(radio)
            }
            BatteryIcon(level: state.batteryLevel, isCharging: state.isCharging)
        }
    }
}

/// Like the one of iOS's status bar, which draws its level: SF Symbols' only comes full with a bolt
private struct BatteryIcon: View {
    private static let lowLevel: Float = 0.2

    let level: Float
    let isCharging: Bool

    var body: some View {
        HStack(spacing: 1) {
            RoundedRectangle(cornerRadius: 3)
                .stroke(Theme.onBackground.opacity(0.5), lineWidth: 1)
                .overlay(alignment: .leading) {
                    GeometryReader { geometry in
                        RoundedRectangle(cornerRadius: 1.5)
                            .fill(isCharging ? Color.green : level < Self.lowLevel ? Color.red : Theme.onBackground)
                            .frame(width: max(0, (geometry.size.width - 3) * CGFloat(level)))
                            .padding(1.5)
                    }
                }
                .overlay {
                    if isCharging {
                        Image(systemName: "bolt.fill")
                            .font(.system(size: 9, weight: .bold))
                            .foregroundStyle(Theme.onBackground)
                            .shadow(color: .black, radius: 1)
                    }
                }
                .frame(width: 24, height: 12)
            RoundedRectangle(cornerRadius: 1)
                .fill(Theme.onBackground.opacity(0.5))
                .frame(width: 1.5, height: 4)
        }
        .accessibilityElement()
        .accessibilityLabel("Battery \(Int(level * 100))%\(isCharging ? ", charging" : "")")
    }
}

/// What LandscapeStatus shows, updated as it changes
@MainActor
private final class DeviceStatus: ObservableObject {
    @Published private(set) var isOnWifi = false
    @Published private(set) var radio: String?
    @Published private(set) var batteryLevel: Float = 0
    @Published private(set) var isCharging = false

    private let path = NWPathMonitor()
    private let telephony = CTTelephonyNetworkInfo()
    private var observers = [NSObjectProtocol]()

    init() {
        // Left on: the app's other uses of the battery's state would otherwise stop
        UIDevice.current.isBatteryMonitoringEnabled = true
        path.pathUpdateHandler = { [weak self] path in
            let isOnWifi = path.status == .satisfied && path.usesInterfaceType(.wifi)
            Task { @MainActor in self?.isOnWifi = isOnWifi }
        }
        path.start(queue: .global(qos: .utility))
        observers = [
            UIDevice.batteryLevelDidChangeNotification,
            UIDevice.batteryStateDidChangeNotification,
            .CTServiceRadioAccessTechnologyDidChange,
        ].map { name in
            NotificationCenter.default.addObserver(forName: name, object: nil, queue: .main) { [weak self] _ in
                Task { @MainActor in self?.update() }
            }
        }
        update()
    }

    deinit {
        path.cancel()
        observers.forEach(NotificationCenter.default.removeObserver)
    }

    private func update() {
        batteryLevel = max(0, UIDevice.current.batteryLevel)
        isCharging = [.charging, .full].contains(UIDevice.current.batteryState)
        // The SIM in use for data, there may be two
        radio = telephony.dataServiceIdentifier
            .flatMap { telephony.serviceCurrentRadioAccessTechnology?[$0] }
            .flatMap(Self.generation)
    }

    private static func generation(_ technology: String) -> String? {
        switch technology {
        case CTRadioAccessTechnologyNR, CTRadioAccessTechnologyNRNSA: "5G"
        case CTRadioAccessTechnologyLTE: "4G"
        case CTRadioAccessTechnologyWCDMA, CTRadioAccessTechnologyHSDPA, CTRadioAccessTechnologyHSUPA,
             CTRadioAccessTechnologyCDMAEVDORev0, CTRadioAccessTechnologyCDMAEVDORevA,
             CTRadioAccessTechnologyCDMAEVDORevB, CTRadioAccessTechnologyeHRPD: "3G"
        case CTRadioAccessTechnologyGPRS, CTRadioAccessTechnologyEdge, CTRadioAccessTechnologyCDMA1x: "2G"
        default: nil
        }
    }
}
