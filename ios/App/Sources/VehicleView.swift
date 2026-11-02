import SwiftUI
import TPMSCore

/// Where a vehicle's tyres are on its top view drawing, as fractions of the image, measured on the
/// same drawings as the Android app's Vehicle.kt
struct VehicleDrawing {
    let image: String
    /// Width over height
    let ratio: CGFloat
    /// A tyre's height, as a fraction of the image's
    let tyreHeight: CGFloat
    /// A tyre's width over its height
    let tyreRatio: CGFloat
    /// How far the tyres or body reach across the image, as a fraction of its width: the readouts
    /// sit against them
    let inset: CGFloat
    let tyres: [TyreLocation: CGPoint]
    var middle = Middle.betweenAxles

    /// Where what's over the vehicle centers on when it fits there, like Vehicle.kt's center
    enum Middle {
        case betweenAxles
        /// As a fraction of the image's height: above a monowheel's wheel rather than over it
        case at(CGFloat)
        /// The screen's vertical middle: a trailer's axle isn't in the image's
        case screen
    }

    /// How far the readout of [location] sits from the middle: a car's front tyres are a little
    /// further in than its rear ones
    func readoutSpan(_ location: TyreLocation) -> CGFloat {
        image == "schema_car_top_view" && [.wheel(.frontLeft), .wheel(.frontRight)].contains(location) ? 0.91 : inset
    }

    /// Where the spare is, and its bottom edge, as fractions of the image's height
    var spareCenter: CGFloat { tyres[.spare]?.y ?? 1 }
    var spareBottom: CGFloat { spareCenter + tyreHeight * tyreRatio / 2 }

    private static let ratio: CGFloat = 625 / 1386
    private static let tyreHeight: CGFloat = 0.165
    private static let tyreRatio: CGFloat = 15 / 40

    // The car with a spare's drawing is cropped, its positions are measured on the full canvas
    private static let spareCropTop: CGFloat = 88 / 1386
    private static let spareCropHeight: CGFloat = 1212 / 1386
    private static func spareCarY(_ y: CGFloat) -> CGFloat { (y - spareCropTop) / spareCropHeight }

    private static func pair(track: CGFloat, y: CGFloat, _ left: TyreLocation, _ right: TyreLocation) -> [TyreLocation: CGPoint] {
        [left: CGPoint(x: (1 - track) / 2, y: y), right: CGPoint(x: (1 + track) / 2, y: y)]
    }

    /// How far a pair of tyres reaches, their track plus a tyre's width
    private static func outer(_ track: CGFloat, _ tyreHeight: CGFloat, _ ratio: CGFloat) -> CGFloat {
        track + tyreHeight * tyreRatio / ratio
    }

    static func of(_ kind: VehicleKind) -> VehicleDrawing {
        switch kind {
        case .car:
            VehicleDrawing(
                image: "schema_car_top_view", ratio: ratio, tyreHeight: tyreHeight, tyreRatio: tyreRatio, inset: 0.92,
                tyres: pair(track: 0.74, y: 0.217, .wheel(.frontLeft), .wheel(.frontRight))
                    .merging(pair(track: 0.74, y: 0.783, .wheel(.rearLeft), .wheel(.rearRight))) { $1 }
            )
        case .carWithSpare:
            {
                let ratio: CGFloat = 625 / 1212, height = tyreHeight / spareCropHeight
                return VehicleDrawing(
                    image: "schema_car_with_spare_top_view", ratio: ratio, tyreHeight: height, tyreRatio: tyreRatio,
                    inset: outer(0.748, height, ratio),
                    tyres: pair(track: 0.748, y: spareCarY(0.241), .wheel(.frontLeft), .wheel(.frontRight))
                        .merging(pair(track: 0.748, y: spareCarY(0.761), .wheel(.rearLeft), .wheel(.rearRight))) { $1 }
                        .merging([.spare: CGPoint(x: 0.5, y: spareCarY(0.905))]) { $1 }
                )
            }()
        case .singleAxleTrailer:
            VehicleDrawing(
                image: "schema_single_axle_trailer_top_view", ratio: ratio, tyreHeight: tyreHeight, tyreRatio: tyreRatio,
                inset: outer(0.86, tyreHeight, ratio),
                tyres: pair(track: 0.86, y: 0.686, .side(.left), .side(.right)),
                middle: .screen
            )
        case .motorcycle:
            VehicleDrawing(
                image: "schema_motorcycle_top_view", ratio: ratio, tyreHeight: tyreHeight, tyreRatio: tyreRatio, inset: 1,
                tyres: [.axle(.front): CGPoint(x: 0.5, y: 0.0865), .axle(.rear): CGPoint(x: 0.5, y: 0.805)]
            )
        case .tadpoleThreeWheeler:
            VehicleDrawing(
                image: "schema_tadpole_three_wheeler_top_view", ratio: ratio, tyreHeight: tyreHeight, tyreRatio: tyreRatio, inset: 0.818,
                tyres: pair(track: 0.58, y: 0.1005, .wheel(.frontLeft), .wheel(.frontRight))
                    .merging([.axle(.rear): CGPoint(x: 0.5, y: 0.845)]) { $1 }
            )
        case .deltaThreeWheeler:
            VehicleDrawing(
                image: "schema_delta_three_wheeler_top_view", ratio: ratio, tyreHeight: tyreHeight, tyreRatio: tyreRatio, inset: 0.886,
                tyres: [.axle(.front): CGPoint(x: 0.5, y: 0.0865)]
                    .merging(pair(track: 0.713, y: 0.835, .wheel(.rearLeft), .wheel(.rearRight))) { $1 }
            )
        case .monowheel:
            VehicleDrawing(
                image: "schema_monowheel_top_view", ratio: ratio, tyreHeight: 0.377, tyreRatio: 11.2 / 52, inset: 1,
                tyres: [.single: CGPoint(x: 0.5, y: 0.505)],
                middle: .at(0.2)
            )
        }
    }
}

extension TyreLocation {
    /// Which side of the vehicle a tyre's readout is on: a left wheel's on the left, the spare's
    /// under it, the others' on the right
    var readoutSide: HorizontalAlignment? {
        switch self {
        case .wheel(.frontLeft), .wheel(.rearLeft), .side(.left): .leading
        case .spare: nil
        default: .trailing
        }
    }
}

/// A sensor on its way to another tyre: the [waiting] location's outline blinks, those it [passed]
/// are greyed out, tapping a location it [canTap] sends it there
struct TyreMove {
    var waiting: TyreLocation?
    var passed: Set<TyreLocation> = []
    let canTap: (TyreLocation) -> Bool
    let onTap: (TyreLocation) -> Void
}

/// The vehicle's top view, its tyres coloured by their temperature and alerts, each with its
/// readout beside it, like the Android app's main screen. A tyre without a sensor is outlined with
/// "Tap to assign". Tapping a tyre with one or its readout magnifies it, or opens its details when
/// the magnifier is off or nothing was read; a long press opens them. While [isManaging], every
/// location is outlined with only its basic readout, and tapping one manages its sensor.
struct VehicleView: View {
    let vehicle: Vehicle
    @ObservedObject var monitor: Monitor
    @ObservedObject var settings: Settings
    var isManaging = false
    var move: TyreMove?
    /// Arrows over the vehicle, for the sensors moving
    var arrows: [MoveChain.Move] = []
    /// A location without a sensor was tapped, or any while [isManaging]
    let onSensor: (TyreLocation) -> Void
    var onMagnify: (TyreLocation) -> Void = { _ in }
    var onDetails: (TyreLocation) -> Void = { _ in }
    /// The top bar's corners, when it's beside the vehicle
    var corners: VehicleLayout.Corners?
    @ScaledMetric(relativeTo: .body) private var baseSize: CGFloat = TyreReadout.baseSize

    private static let outlineRadius: CGFloat = 12
    /// A location a moving sensor went through
    private static let passedOpacity = 0.38

    var body: some View {
        let blinks = move != nil || (isManaging == false && vehicle.kind.locations.contains { tyre($0)?.alerts.levels.values.contains { $0 >= .red } == true })
        // Fast enough for the blinking while something blinks, otherwise for the time since update
        TimelineView(.periodic(from: .now, by: blinks ? 0.2 : 15)) { context in
            GeometryReader { geometry in
                let content = VehicleLayout.Content(vehicle: vehicle, monitor: monitor, settings: settings, baseSize: baseSize, isManaging: isManaging, isMoving: move != nil, date: context.date)
                let layout = VehicleLayout(content, room: geometry.size, corners: corners)
                ZStack(alignment: .topLeading) {
                    Image(content.drawing.image)
                        .renderingMode(.template)
                        .resizable()
                        .foregroundStyle(Theme.onBackground)
                        .frame(width: layout.image.width, height: layout.image.height)
                        .position(x: layout.image.midX, y: layout.image.midY)
                    if isManaging == false {
                        Canvas { context, _ in
                            for separator in layout.separators(content) {
                                var line = Path()
                                line.move(to: CGPoint(x: separator.from, y: separator.y))
                                line.addLine(to: CGPoint(x: separator.to, y: separator.y))
                                context.stroke(line, with: .color(Color(.separator)), lineWidth: 1)
                            }
                        }
                        .allowsHitTesting(false)
                    }
                    ForEach(vehicle.kind.locations) { location in
                        if let tyreRect = layout.tyres[location], let readout = layout.readouts[location] {
                            let long = max(tyreRect.width, tyreRect.height)
                            tappable(location) {
                                TyrePill(tyre: tyre(location), vehicle: vehicle, date: context.date, blinks: isManaging == false)
                                    .frame(width: min(tyreRect.width, tyreRect.height), height: long)
                                    // The spare lies across the boot
                                    .rotationEffect(location == .spare ? .degrees(90) : .zero)
                                    .frame(width: long + 16, height: long + 16)
                            }
                            .position(x: tyreRect.midX, y: tyreRect.midY)
                            tappable(location) { readoutView(location, content: content, size: layout.textSize, slot: readout.size, date: context.date) }
                                .position(x: readout.midX, y: readout.midY)
                        }
                    }
                    if layout.spareLine.isEmpty == false {
                        Path { $0.addLines(layout.spareLine) }
                            .stroke(Theme.onBackground, lineWidth: Self.frameWidth)
                            .allowsHitTesting(false)
                    }
                    ForEach(vehicle.kind.locations) { location in
                        if let outline = layout.outlines[location] {
                            outlineView(location, in: outline, isFramed: location == .spare && layout.spareFrame != nil, textSize: layout.textSize, date: context.date)
                        }
                    }
                    MoveArrows(moves: arrows, tyres: layout.tyres, outlines: layout.outlines)
                }
                .preference(key: VehicleBounds.Key.self, value: layout.bounds(origin: geometry.frame(in: .named(VehicleBounds.space)).origin))
            }
        }
    }

    /// In the slot: against the tyre, or centered with what's outlined, off the outline's outer edge
    @ViewBuilder private func readoutView(_ location: TyreLocation, content: VehicleLayout.Content, size: CGFloat, slot: CGSize, date: Date) -> some View {
        let outer = isOutlined(location) && location != .spare ? VehicleLayout.outlinePadding : 0
        Group {
            if case .tapToAssign(let isDetected) = content.kinds[location] {
                TapToAssign(isDetected: isDetected, size: size)
                    .frame(width: slot.width - outer)
            } else {
                TyreReadout(
                    tyre: tyre(location),
                    settings: settings,
                    // Against the tyre, on whichever side of it the readout is. Centred in its outline on the sensors' page.
                    alignment: isManaging ? .center : location.readoutSide.map { $0 == .leading ? .trailing : .leading } ?? .center,
                    date: date,
                    textSize: size,
                    isBasic: isManaging
                )
                .frame(width: slot.width - outer, alignment: isManaging || location.readoutSide == nil ? .center : location.readoutSide == .leading ? .trailing : .leading)
            }
        }
        .padding(location.readoutSide == .leading ? .leading : .trailing, outer)
        .frame(width: slot.width, height: slot.height)
    }

    private func tyre(_ location: TyreLocation) -> Monitor.Tyre? {
        monitor.tyres[Monitor.TyreKey(vehicleId: vehicle.id, location: location)]
    }

    /// Whether tapping the location manages or assigns its sensor, or is up to a move
    private func isOutlined(_ location: TyreLocation) -> Bool {
        move != nil || isManaging || monitor.sensor(vehicle: vehicle, location: location) == nil
    }

    private static let frameWidth: CGFloat = 1

    private func outlineColor(_ location: TyreLocation, date: Date) -> Color? {
        if move?.waiting == location { return Blink.waiting.isFirstPhase(date) ? Theme.primary : .clear }
        if move?.passed.contains(location) == true { return Theme.onBackground.opacity(Self.passedOpacity) }
        return isManaging || monitor.sensor(vehicle: vehicle, location: location) == nil ? Theme.primary : nil
    }

    /// Covers the location's tyre and readout, tapping it anywhere, like the Android app's TyreTapArea
    @ViewBuilder private func outlineView(_ location: TyreLocation, in rect: CGRect, isFramed: Bool, textSize: CGFloat, date: Date) -> some View {
        let color = outlineColor(location, date: date)
        if color != nil || isFramed {
            RoundedRectangle(cornerRadius: Self.outlineRadius, style: .continuous)
                .strokeBorder(color ?? Theme.onBackground, lineWidth: color == nil ? Self.frameWidth : 2)
                .frame(width: rect.width, height: rect.height)
                .position(x: rect.midX, y: rect.midY)
                .allowsHitTesting(false)
        }
        if isFramed {
            // On the frame's top edge, cutting it
            Text("Spare")
                .font(.system(size: textSize * VehicleLayout.frameTitleScale, weight: .medium))
                .foregroundStyle(color ?? Theme.onBackground)
                .padding(.horizontal, 4)
                .background(Theme.background)
                .frame(width: rect.width - 24, alignment: .leading)
                .position(x: rect.midX, y: rect.minY)
                .allowsHitTesting(false)
        }
        if isOutlined(location) {
            Button {
                if let move {
                    if move.canTap(location) { move.onTap(location) }
                } else {
                    onSensor(location)
                }
            } label: {
                Color.clear
                    .contentShape(RoundedRectangle(cornerRadius: Self.outlineRadius, style: .continuous))
            }
            .buttonStyle(.plain)
            .frame(width: rect.width, height: rect.height)
            .position(x: rect.midX, y: rect.midY)
            .accessibilityLabel(location.name)
        }
    }

    private func tappable(_ location: TyreLocation, @ViewBuilder _ content: () -> some View) -> some View {
        Button {
            if settings.magnifier, tyre(location)?.latest != nil { onMagnify(location) } else { onDetails(location) }
        } label: {
            content().contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .contextMenu {
            Button("Sensor and readings", systemImage: "info.circle") { onDetails(location) }
        }
    }
}

/// An arrow from each move's tyre to the next, out of their outlines. A swap's two arrows are drawn
/// side by side.
private struct MoveArrows: View {
    let moves: [MoveChain.Move]
    let tyres: [TyreLocation: CGRect]
    let outlines: [TyreLocation: CGRect]

    private static let lineWidth: CGFloat = 3
    private static let head: CGFloat = 14
    /// Off the tyres, when they're too close to leave their outlines
    private static let inset: CGFloat = 28
    private static let spacing: CGFloat = 6
    /// Between an arrow's ends and the outlines it leaves and reaches
    private static let gap: CGFloat = 4

    var body: some View {
        Canvas { context, _ in
            for move in moves {
                guard let from = tyres[move.from], let to = tyres[move.to] else { continue }
                let start = CGPoint(x: from.midX, y: from.midY), end = CGPoint(x: to.midX, y: to.midY)
                let dx = end.x - start.x, dy = end.y - start.y
                let length = (dx * dx + dy * dy).squareRoot()
                guard length > 0 else { continue }
                let direction = CGVector(dx: dx / length, dy: dy / length)
                let normal = CGVector(dx: -direction.dy, dy: direction.dx)
                // Side by side with the swap's other arrow, rather than over it
                let shift = moves.contains(.init(from: move.to, to: move.from)) ? Self.spacing : 0
                let tail = CGPoint(x: start.x + normal.dx * shift, y: start.y + normal.dy * shift)
                let tip = CGPoint(x: end.x + normal.dx * shift, y: end.y + normal.dy * shift)
                var fromInset = (outlines[move.from]?.exit(from: tail, direction) ?? 0) + Self.gap
                var toInset = (outlines[move.to]?.exit(from: tip, CGVector(dx: -direction.dx, dy: -direction.dy)) ?? 0) + Self.gap
                if length - fromInset - toInset <= Self.head * 2 {
                    fromInset = min(Self.inset, length / 3)
                    toInset = fromInset
                }
                let arrowTail = CGPoint(x: tail.x + direction.dx * fromInset, y: tail.y + direction.dy * fromInset)
                let arrowTip = CGPoint(x: tip.x - direction.dx * toInset, y: tip.y - direction.dy * toInset)
                let base = CGPoint(x: arrowTip.x - direction.dx * Self.head, y: arrowTip.y - direction.dy * Self.head)
                var line = Path()
                line.move(to: arrowTail)
                line.addLine(to: CGPoint(x: arrowTip.x - direction.dx * Self.head / 2, y: arrowTip.y - direction.dy * Self.head / 2))
                context.stroke(line, with: .color(Theme.primary), style: StrokeStyle(lineWidth: Self.lineWidth, lineCap: .round))
                var arrowHead = Path()
                arrowHead.move(to: arrowTip)
                arrowHead.addLine(to: CGPoint(x: base.x + normal.dx * Self.head / 2, y: base.y + normal.dy * Self.head / 2))
                arrowHead.addLine(to: CGPoint(x: base.x - normal.dx * Self.head / 2, y: base.y - normal.dy * Self.head / 2))
                arrowHead.closeSubpath()
                context.fill(arrowHead, with: .color(Theme.primary))
            }
        }
        .allowsHitTesting(false)
    }
}

extension CGRect {
    /// How far from [point], inside this rectangle, its edge is in that [direction]
    fileprivate func exit(from point: CGPoint, _ direction: CGVector) -> CGFloat {
        [
            direction.dx > 0 ? (maxX - point.x) / direction.dx : nil,
            direction.dx < 0 ? (minX - point.x) / direction.dx : nil,
            direction.dy > 0 ? (maxY - point.y) / direction.dy : nil,
            direction.dy < 0 ? (minY - point.y) / direction.dy : nil,
        ].compactMap { $0 }.min().map { Swift.max($0, 0) } ?? 0
    }
}

/// What a tyre without a sensor shows, in place of its readout. The "S" badge is a Sysgration sensor
/// advertising this location, see AssignSensorSheet.
private struct TapToAssign: View {
    let isDetected: Bool
    let size: CGFloat

    var body: some View {
        VStack(spacing: ReadoutKind.badgeSpacing) {
            Text("Tap to\nassign")
                .font(.system(size: size * ReadoutKind.tapToAssignScale, weight: .semibold))
                .multilineTextAlignment(.center)
                .lineLimit(2)
                .minimumScaleFactor(0.5)
                .foregroundStyle(Theme.primary)
            if isDetected { SysgrationBadge() }
        }
    }
}

/// A Sysgration sensor detected at a location
struct SysgrationBadge: View {
    var body: some View {
        Text("S")
            .font(.system(size: 15, weight: .black))
            .foregroundStyle(Theme.background)
            .frame(width: 24, height: 24)
            .background(Theme.primary, in: Circle())
    }
}

/// A tyre: filled with its temperature's colour, red and blinking while it alerts, an outline while
/// nothing was read in the last 5 minutes, like TyreIconStateFlow
struct TyrePill: View {
    let tyre: Monitor.Tyre?
    let vehicle: Vehicle
    let date: Date
    /// Still on the sensors' page, where they're only checked
    var blinks = true

    var body: some View {
        GeometryReader { geometry in
            let shape = RoundedRectangle(cornerRadius: min(geometry.size.width, geometry.size.height) * 0.2, style: .continuous)
            if let reading = tyre?.latest, date.timeIntervalSince1970 - reading.timestamp < 5 * 60 {
                // A battery alert doesn't change the tyre, its readout says it
                let level = tyre?.alerts.levels.filter { $0.key != .battery }.values.max()
                let alerts = level.map { $0 >= .red } ?? false
                let isHidden = blinks && alerts && (level == .crimson ? Blink.critical : Blink.red).isFirstPhase(date) == false
                shape
                    .fill(alerts ? Theme.error : Theme.tyre(reading.temperature, low: vehicle.lowTemp, high: vehicle.highTemp))
                    .opacity(isHidden ? 0 : 1)
            } else {
                shape.strokeBorder(Theme.onBackground, lineWidth: 2)
            }
        }
    }
}

/// A tyre's latest reading, like TyreStat.kt: its pressure, temperature and how long ago it was
/// read, then what's wrong, each in its alert's colour, and the lines the settings add
struct TyreReadout: View {
    let tyre: Monitor.Tyre?
    @ObservedObject var settings: Settings
    let alignment: HorizontalAlignment
    let date: Date
    /// In place of the settings' text size: as the vehicle's readouts fit, or the magnifier's
    var textSize: CGFloat?
    /// Only the pressure, temperature and time since update, whatever the settings and alerts, and
    /// still: on the sensors' page they're only checked
    var isBasic = false
    /// The readings' size, following the system's text size like Android's 16 sp
    @ScaledMetric(relativeTo: .body) private var baseSize: CGFloat = TyreReadout.baseSize

    /// Android's 16 sp
    static let baseSize: CGFloat = 16

    var body: some View {
        let reading = tyre?.latest
        let levels = tyre?.alerts.levels ?? [:]
        let size: CGFloat = textSize ?? CGFloat(settings.readoutTextScale) * baseSize
        let lines = ReadoutLines(tyre: tyre, settings: settings, isBasic: isBasic)
        VStack(alignment: alignment, spacing: 0) {
            line(
                // Explained on the sensor's calibration page
                reading.map { $0.pressure.string(settings.pressureUnit) + (tyre?.isCalibrated == true ? "*" : "") } ?? "-.--",
                levels[.pressure],
                size: size,
                criticalLabel: tyre?.alerts.isHighPressure == true ? "HIGH!" : "LOW!"
            )
            line(reading?.temperature.string(settings.temperatureUnit) ?? "-.-", levels[.temperature], size: size, criticalLabel: "HIGH!")
            if let reading, lines.showsTime {
                line(Self.elapsed(reading.timestamp, now: date), nil, size: size)
            }
            if lines.showsLeak {
                line("Leak?", .amber, size: size)
            }
            if let battery = lines.battery {
                line(battery, levels[.battery], size: size)
            }
            if let sensorId = lines.sensorId {
                Text(sensorId)
                    .font(.system(size: size * ReadoutLines.sensorIdScale))
                    .lineLimit(1)
                    .minimumScaleFactor(0.5)
                    .foregroundStyle(Theme.onBackground)
                    .frame(height: size * ReadoutLines.sensorIdScale * ReadoutLines.lineHeight)
            }
            // A line per status byte, bit 7 first, the bits which aren't set greyed out
            ForEach(Array(lines.flags.enumerated()), id: \.offset) { _, flag in
                flagLine(flag, size: size * ReadoutLines.flagsScale)
                    .lineLimit(1)
                    .minimumScaleFactor(0.5)
                    .frame(height: size * ReadoutLines.flagsScale * ReadoutLines.lineHeight)
            }
        }
    }

    private func flagLine(_ flag: UInt8, size: CGFloat) -> Text {
        (0..<8).reversed().reduce(Text("")) { text, bit in
            let isSet = flag >> bit & 1 == 1
            return text + Text("\(bit)")
                .fontWeight(isSet ? .bold : .regular)
                .foregroundColor(isSet ? Theme.onBackground : Theme.onBackground.opacity(0.3))
        }
        .font(.system(size: size, design: .monospaced))
    }

    private func line(_ text: String, _ level: AlertLevel?, size: CGFloat, criticalLabel: String = "") -> some View {
        let isLabel = isBasic == false && level == .crimson && criticalLabel.isEmpty == false && Blink.criticalLabel.isFirstPhase(date) == false
        let isHidden = isBasic == false && level == .red && Blink.red.isFirstPhase(date) == false
        return Text(isLabel ? criticalLabel : text)
            .font(.system(size: size, weight: .semibold))
            .lineLimit(1)
            .minimumScaleFactor(0.5)
            .foregroundStyle(level?.color ?? Theme.onBackground)
            .opacity(isHidden ? 0 : 1)
            // Android's line height, so the readout's height is known before it's laid out
            .frame(height: size * ReadoutLines.lineHeight)
    }

    /// TyreStat.kt's elapsedLabel: minutes under 2 hours, hours under 2 days, then days
    static func elapsed(_ timestamp: Double, now: Date) -> String {
        let minutes = Int(max(0, now.timeIntervalSince1970 - timestamp) / 60)
        let hours = minutes / 60
        return switch true {
        case minutes == 0: "<1 min"
        case minutes < 120: "\(minutes) min"
        case hours < 48: "\(hours) hours"
        case hours / 24 > 99: "99+ days"
        default: "\(hours / 24) days"
        }
    }
}
