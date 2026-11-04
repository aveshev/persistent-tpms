import CoreBluetooth
import SwiftUI
import TPMSCore

/// The current vehicle's tyres, laid out like the vehicle
struct MainView: View {
    @ObservedObject var monitor: Monitor
    @ObservedObject var settings: Settings
    @State private var selected: TyreLocation?
    @State private var sheet: Sheet?
    /// The tyre without a sensor tapped, to assign it one
    @State private var assigning: TyreLocation?
    @State private var magnified: TyreLocation?
    @State private var magnifiedAt = Date()
    @State private var isDimmed = false
    @Environment(\.scenePhase) private var scenePhase
    @ScaledMetric(relativeTo: .body) private var baseSize: CGFloat = TyreReadout.baseSize
    /// The bar's text, as large as a .headline
    @ScaledMetric(relativeTo: .headline) private var headlineSize: CGFloat = 17
    private var barTitleSize: CGFloat { headlineSize * Demo.textScale }

    enum Sheet: String, Identifiable {
        case vehicle, addVehicle, settings, manage, persistent, simulate
        var id: String { rawValue }
    }

    var body: some View {
        NavigationStack {
            Group {
                if let vehicle = monitor.current {
                    screen(vehicle)
                } else {
                    ProgressView()
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .background(Theme.background)
            // Shown for the settings' duration, a new tap shows it again for as long
            .task(id: magnifiedAt) {
                guard magnified != nil else { return }
                try? await Task.sleep(for: .seconds(settings.magnifierSeconds))
                if Task.isCancelled == false { withAnimation { magnified = nil } }
            }
            // Its own, which goes beside the vehicle when that makes its image larger
            .toolbar(.hidden, for: .navigationBar)
            .onAppear {
                #if DEBUG
                switch Demo.screen {
                case "tyre": selected = monitor.current?.kind.locations.last
                case "assign", "bluetooth", "qr": assigning = monitor.current?.kind.locations.first
                case "manage", "managecar": sheet = .manage
                case "vehicle": sheet = .vehicle
                case "vehicles": sheet = .addVehicle
                case "persistent": sheet = .persistent
                case "settings": sheet = .settings
                default: break
                }
                Demo.renderScreen(monitor: monitor, settings: settings)
                Demo.checkLayouts()
                #endif
            }
            .sheet(item: $selected) { location in
                if let vehicle = monitor.current {
                    TyreDetailView(vehicle: vehicle, location: location, monitor: monitor, settings: settings)
                }
            }
            .sheet(item: $assigning) { location in
                if let vehicle = monitor.current {
                    AssignSensorSheet(vehicle: vehicle, location: location, monitor: monitor, settings: settings)
                }
            }
            .sheet(item: $sheet) { sheet in
                switch sheet {
                case .vehicle: if let vehicle = monitor.current { VehicleSettingsView(vehicleId: vehicle.id, monitor: monitor, settings: settings) }
                case .addVehicle: AddVehicleView(monitor: monitor)
                case .persistent:
                    NavigationStack {
                        PersistentModeView(monitor: monitor, settings: settings)
                            .toolbar { ToolbarItem(placement: .confirmationAction) { Button("Done") { self.sheet = nil } } }
                    }
                case .simulate:
                    #if DEBUG
                    if let vehicle = monitor.current { SimulateReadingView(vehicle: vehicle, monitor: monitor, settings: settings) }
                    #else
                    EmptyView()
                    #endif
                case .settings: AppSettingsView(monitor: monitor, settings: settings)
                case .manage: if let vehicle = monitor.current { NavigationStack { ManageSensorsView(vehicleId: vehicle.id, monitor: monitor, settings: settings) } }
                }
            }
        }
        // Over the top bar too, whose taps would go through otherwise
        .modifier(DimWhenIdle(monitor: monitor, settings: settings, isShown: scenePhase == .active && selected == nil && sheet == nil && assigning == nil, isDimmed: $isDimmed))
    }

    /// A tyre's readout in a large font, easy to read on a rough road, like the Android app's
    /// magnifier: a card over the vehicle, its close button's ring counting down. A tap hides it.
    private func magnifier(_ vehicle: Vehicle, _ location: TyreLocation) -> some View {
        GeometryReader { screen in
            TimelineView(.periodic(from: .now, by: 0.1)) { context in
                let tyre = monitor.tyres[Monitor.TyreKey(vehicleId: vehicle.id, location: location)]
                let padding = 2 * (Self.magnifierScreenPadding + Self.magnifierCardPadding)
                let maxWidth = screen.size.width - padding, maxHeight = screen.size.height - padding
                let titleSize = magnifierTitleSize(vehicle, width: screen.size.width, height: maxHeight)
                let titleHeight = max(Self.magnifierCloseSize, titleSize * Self.magnifierTitleLineHeight)
                // At a size of 1000, for how wide it is per point of text size: the system font's tracking settles at large sizes
                let widest = VehicleLayout.widestReading(settings.pressureUnit, isOutlined: false, size: 1000) / 1000
                // As large as fits, its width the same from one location and reading to another
                let size = max(1, min(maxWidth / widest, (maxHeight - titleHeight) / ReadoutLines(tyre: tyre, settings: settings, isBasic: false).height))
                VStack(spacing: 0) {
                    MagnifierTitleRow(gap: Self.magnifierCloseGap) {
                        Text(location.name)
                            .font(.system(size: titleSize, weight: .semibold))
                            .lineLimit(1)
                            .fixedSize()
                        ZStack {
                            Circle().stroke(Theme.onBackground.opacity(0.15), lineWidth: 3)
                            Circle()
                                .trim(from: 0, to: max(0, 1 - context.date.timeIntervalSince(magnifiedAt) / Double(settings.magnifierSeconds)))
                                .stroke(Theme.primary, style: StrokeStyle(lineWidth: 3, lineCap: .round))
                                .rotationEffect(.degrees(-90))
                            Image(systemName: "xmark").font(.title3.weight(.semibold))
                        }
                        .frame(width: Self.magnifierCloseSize, height: Self.magnifierCloseSize)
                        .accessibilityLabel("Close")
                    }
                    TyreReadout(tyre: tyre, settings: settings, alignment: .center, date: context.date, textSize: size)
                }
                // As wide as its widest title or reading
                .frame(width: min(maxWidth, max(widest * size, magnifierWidestTitle(vehicle, size: titleSize) + Self.magnifierCloseSize + Self.magnifierCloseGap)))
                .padding(Self.magnifierCardPadding)
                .background(Color(.secondarySystemBackground), in: RoundedRectangle(cornerRadius: 28, style: .continuous))
                .padding(Self.magnifierScreenPadding)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .contentShape(Rectangle())
                .background(Color.black.opacity(Self.magnifierScrim).ignoresSafeArea())
                .onTapGesture { withAnimation { magnified = nil } }
            }
        }
        .transition(.opacity)
    }

    private static let magnifierCloseGap: CGFloat = 16
    private static let magnifierCloseSize: CGFloat = 44
    private static let magnifierCardPadding: CGFloat = 16
    private static let magnifierScreenPadding: CGFloat = 16
    private static let magnifierTitleMaxSize: CGFloat = 52
    private static let magnifierTitleLineHeight: CGFloat = 1.2
    /// The title's largest share of the height, leaving the rest to the reading
    private static let magnifierTitleMaxShare: CGFloat = 0.2
    private static let magnifierScrim = 0.32

    private func magnifierWidestTitle(_ vehicle: Vehicle, size: CGFloat) -> CGFloat {
        let font = UIFont.systemFont(ofSize: size, weight: .semibold)
        return vehicle.kind.locations
            .map { ($0.name as NSString).size(withAttributes: [.font: font]).width }
            .max() ?? 1
    }

    /// The vehicle's widest location sizes the title, which keeps its size from one location to another,
    /// fitting beside the close button
    private func magnifierTitleSize(_ vehicle: Vehicle, width: CGFloat, height: CGFloat) -> CGFloat {
        let widest = magnifierWidestTitle(vehicle, size: Self.magnifierTitleMaxSize)
        let room = width - 2 * (Self.magnifierScreenPadding + Self.magnifierCardPadding) - Self.magnifierCloseSize - Self.magnifierCloseGap
        return min(Self.magnifierTitleMaxSize, Self.magnifierTitleMaxSize * room / widest, height * Self.magnifierTitleMaxShare / Self.magnifierTitleLineHeight)
    }

    /// The top bar over the vehicle, or in the room's top corner beside it, whichever leaves it the
    /// largest image, like the Android app's VehicleWithTopBar. The readouts and silence button are
    /// laid out clear of it.
    private func screen(_ vehicle: Vehicle) -> some View {
        GeometryReader { geometry in
            let size = geometry.size
            let content = VehicleLayout.Content(vehicle: vehicle, monitor: monitor, settings: settings, baseSize: baseSize, isManaging: false, isMoving: false, date: Date())
            let barHeight = barHeight
            let barGaps = Self.besideTitleStart + Self.besideTitleEnd + Self.barPadding + barHeight
            let end = min(barTitleWidth, size.width * Self.barMaxEndShare - barGaps) + barGaps
            let corners = VehicleLayout.Corners(start: 0, end: end, height: barHeight)
            let over = CGSize(width: size.width, height: size.height - barHeight)
            let isBeside = VehicleLayout.isBesideBetter(
                VehicleLayout(content, room: size, corners: corners),
                than: VehicleLayout(content, room: over, corners: nil)
            )
            let vehicleView = VehicleView(
                vehicle: vehicle, monitor: monitor, settings: settings,
                onSensor: { assigning = $0 },
                onMagnify: {
                    magnified = $0
                    magnifiedAt = Date()
                },
                onDetails: { selected = $0 },
                corners: isBeside ? corners : nil
            )
            let bar = isBeside
                ? CGRect(x: size.width - end, y: 0, width: end, height: barHeight)
                : CGRect(x: 0, y: 0, width: size.width, height: barHeight)
            ZStack(alignment: .topTrailing) {
                VStack(spacing: 0) {
                    if isBeside == false {
                        ZStack {
                            vehicleMenu.padding(.horizontal, barHeight)
                            settingsMenu.frame(maxWidth: .infinity, alignment: .trailing)
                        }
                        .frame(height: barHeight)
                    }
                    // Why nothing comes in, under the bar's corner
                    status
                        .padding(.horizontal, 4)
                        .padding(.trailing, isBeside ? end : 0)
                    vehicleView
                }
                if isBeside {
                    HStack(spacing: Self.besideTitleEnd) {
                        vehicleMenu
                        settingsMenu
                    }
                    .padding(.leading, Self.besideTitleStart)
                    .padding(.trailing, Self.barPadding)
                    .frame(width: end, height: barHeight, alignment: .trailing)
                }
            }
            .coordinateSpace(.named(VehicleBounds.space))
            .overlayPreferenceValue(VehicleBounds.Key.self) { bounds in
                ClearOfReadouts(bounds: bounds, bar: [bar]) {
                    // Never both: an alert to silence keeps the screen bright
                    if isDimmed { DimmedNotice() } else { SilenceAlertsButton(speaker: monitor.speaker) }
                }
            }
            .overlay { magnified.map { magnifier(vehicle, $0) } }
        }
    }

    /// iOS's own bar's height, Android's is 64, taller as much as its title needs
    private var barHeight: CGFloat {
        max(Self.minBarHeight, barTitleSize * 1.3 + 8)
    }

    private static let minBarHeight: CGFloat = 44
    private static let barMaxEndShare: CGFloat = 0.5
    // VehicleWithTopBar.kt's
    private static let barPadding: CGFloat = 4
    private static let besideTitleStart: CGFloat = 16
    private static let besideTitleEnd: CGFloat = 8

    /// The vehicle's name and its chevron
    private var barTitleWidth: CGFloat {
        ((monitor.current?.name ?? "") as NSString)
            .size(withAttributes: [.font: UIFont.systemFont(ofSize: barTitleSize, weight: .semibold)]).width
            + 4 + barTitleSize
    }

    private var settingsMenu: some View {
        Menu {
            Button("Vehicle settings", systemImage: "car") { sheet = .vehicle }
            // Laid out with what it's set to: an iOS menu line can't also hold a switch
            Button { sheet = .persistent } label: {
                Label(settings.backgroundMonitoring ? "Persistent mode: on" : "Persistent mode: off", systemImage: settings.backgroundMonitoring ? "bell.fill" : "bell.slash")
            }
            Button("App settings", systemImage: "gear") { sheet = .settings }
            #if DEBUG
            if settings.developer, settings.debugOptions {
                Divider()
                Button("Simulate a reading", systemImage: "testtube.2") { sheet = .simulate }
            }
            #endif
        } label: {
            Image(systemName: "gearshape")
                .font(.system(size: barTitleSize * 20 / 17))
                .frame(width: barHeight, height: barHeight)
        }
        .accessibilityLabel("Settings")
    }

    /// The current vehicle's name, switching to another one, like CurrentVehicleDropdown
    private var vehicleMenu: some View {
        Menu {
            ForEach(monitor.vehicles) { vehicle in
                Button {
                    monitor.setCurrent(vehicle)
                } label: {
                    if vehicle.isCurrent {
                        Label(vehicle.name, systemImage: "checkmark")
                    } else if monitor.isAlerting(vehicle.id) {
                        Label(vehicle.name, systemImage: "exclamationmark.triangle.fill")
                    } else {
                        Text(vehicle.name)
                    }
                }
            }
            Divider()
            Button("Add a vehicle", systemImage: "plus") { sheet = .addVehicle }
        } label: {
            HStack(spacing: 4) {
                Text(monitor.current?.name ?? "Persistent TPMS").font(.system(size: barTitleSize, weight: .semibold)).lineLimit(1)
                Image(systemName: "chevron.down").font(.system(size: barTitleSize * 12 / 17, weight: .semibold))
            }
            .foregroundStyle(Theme.onBackground)
        }
    }

    /// Why nothing comes in, when nothing can
    @ViewBuilder private var status: some View {
        if let error = monitor.error {
            Banner(text: error, systemImage: "exclamationmark.triangle", color: .red)
        }
        switch monitor.bluetooth {
        case .poweredOff: Banner(text: "Bluetooth is off: the sensors can't be heard.", systemImage: "antenna.radiowaves.left.and.right.slash", color: .orange)
        case .unauthorized: Banner(text: "Bluetooth isn't allowed for Persistent TPMS. Allow it in Settings, Privacy & Security, Bluetooth.", systemImage: "lock", color: .orange)
        case .unsupported: Banner(text: "This device has no Bluetooth Low Energy.", systemImage: "xmark.octagon", color: .orange)
        default: EmptyView()
        }
    }
}

/// The magnifier's title and its close button, in the corner. The title is centered, unless that
/// reaches the close button: then against it, as far as the gap, like the Android app's.
private struct MagnifierTitleRow: Layout {
    let gap: CGFloat

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout Void) -> CGSize {
        let sizes = subviews.map { $0.sizeThatFits(.unspecified) }
        return CGSize(
            width: proposal.width ?? sizes.reduce(gap) { $0 + $1.width },
            height: sizes.map(\.height).max() ?? 0
        )
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout Void) {
        guard subviews.count == 2 else { return }
        let title = subviews[0].sizeThatFits(.unspecified)
        let close = subviews[1].sizeThatFits(.unspecified)
        subviews[0].place(
            at: CGPoint(x: bounds.minX + min((bounds.width - title.width) / 2, bounds.width - close.width - gap - title.width), y: bounds.midY),
            anchor: .leading,
            proposal: .unspecified
        )
        subviews[1].place(at: CGPoint(x: bounds.maxX, y: bounds.midY), anchor: .trailing, proposal: .unspecified)
    }
}

/// Silences the alerts' sound for a while, while a red or critical alert has yet to be said, like the
/// Android app's SilenceAlertsButton. Big enough to hit while riding.
private struct SilenceAlertsButton: View {
    @ObservedObject var speaker: AlertSpeaker

    var body: some View {
        Group {
            switch SilenceButton.of(highest: speaker.level, silence: speaker.silence) {
            case .idle:
                EmptyView()
            case let .offer(isCritical):
                Button { speaker.silenceNow() } label: {
                    Label("Silence \(isCritical ? "critical alerts" : "alerts") for 10 min", systemImage: "speaker.wave.2.fill")
                }
                .tint(Theme.error)
            case let .silenced(isCritical, until):
                // The speaker only drops an ended silence at its next reading. Nothing's left to
                // silence by then, what it dropped isn't kept, so the button just goes away.
                TimelineView(.periodic(from: .now, by: 1)) { context in
                    let minutesLeft = SilenceButton.minutesLeft(until: until, now: context.date)
                    if minutesLeft > 0 {
                        Button { speaker.unmute() } label: {
                            Label {
                                VStack(spacing: 2) {
                                    Text(isCritical ? "All alerts silenced" : "Alerts silenced")
                                    Text("\(minutesLeft) min left, tap to unmute")
                                        .font(.callout)
                                }
                            } icon: {
                                Image(systemName: "speaker.slash.fill")
                            }
                        }
                        .tint(Theme.onBackground.opacity(0.3))
                    }
                }
            }
        }
        .buttonStyle(.borderedProminent)
        .buttonBorderShape(.capsule)
        .font(.title3.weight(.semibold))
        .lineLimit(1...2)
        .minimumScaleFactor(0.6)
        .controlSize(.large)
    }
}

struct Banner: View {
    let text: String
    let systemImage: String
    let color: Color

    var body: some View {
        Label(text, systemImage: systemImage)
            .font(.callout)
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(12)
            .background(color.opacity(0.15), in: RoundedRectangle(cornerRadius: 12))
    }
}
