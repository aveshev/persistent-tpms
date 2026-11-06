import SwiftUI
import TPMSCore
import UIKit

/// The lines a tyre's readout shows, like TyreStat.kt's, so how tall it is is known before it's laid
/// out: 1.5 times each line's text size, see VehicleLayout
struct ReadoutLines {
    let showsTime: Bool
    let showsLeak: Bool
    let battery: String?
    let sensorId: String?
    let flags: [UInt8]

    /// Over the readings' size, like Android's 9 and 11 sp over its 16
    static let sensorIdScale: CGFloat = 9 / 16
    static let flagsScale: CGFloat = 11 / 16
    static let lineHeight: CGFloat = 1.5

    @MainActor init(tyre: Monitor.Tyre?, settings: Settings, isBasic: Bool) {
        let reading = tyre?.latest
        let levels = tyre?.alerts.levels ?? [:]
        // Basic, it's always there, like TyreStat's default
        showsTime = reading != nil && (isBasic || settings.showTimeSinceUpdate)
        showsLeak = isBasic == false && (levels[.pressureLoss] != nil || levels[.sensorAlarm] != nil)
        // A battery getting low shows whatever the setting
        battery = isBasic == false && (settings.showBatteryVoltage || levels[.battery] != nil)
            ? reading?.batteryVoltage?.string ?? reading?.batteryPercent.map { "\($0)%" }
            : nil
        sensorId = isBasic == false && settings.showsSensorId
            ? reading.map { $0.brand.isIdentifiedByAddress ? "No ID" : $0.sensorId.asSensorId($0.brand) }
            : nil
        flags = isBasic == false && settings.showsSensorFlags
            ? reading.flatMap { AdvertisingPacket(bytes: $0.raw).statusBytes.map(Array.init) } ?? []
            : []
    }

    init(showsTime: Bool = true, showsLeak: Bool = false, battery: String? = nil, sensorId: String? = nil, flags: [UInt8] = []) {
        self.showsTime = showsTime
        self.showsLeak = showsLeak
        self.battery = battery
        self.sensorId = sensorId
        self.flags = flags
    }

    /// The pressure, temperature and time since update
    static let basic = ReadoutLines()

    /// At a text size of 1
    var height: CGFloat {
        (CGFloat(2 + (showsTime ? 1 : 0) + (showsLeak ? 1 : 0) + (battery != nil ? 1 : 0))
            + (sensorId != nil ? Self.sensorIdScale : 0)
            + CGFloat(flags.count) * Self.flagsScale) * Self.lineHeight
    }

    /// The size of the last line's text, at a text size of 1
    fileprivate var lastLineScale: CGFloat {
        flags.isEmpty == false ? Self.flagsScale : sensorId != nil ? Self.sensorIdScale : 1
    }
}

/// What a location shows beside its tyre
enum ReadoutKind {
    case lines(ReadoutLines)
    case tapToAssign(isDetected: Bool)

    /// Android's 20 sp, over the readings' 16
    static let tapToAssignScale: CGFloat = 20 / 16
    static let badgeSpacing: CGFloat = 4
    static let badgeSize: CGFloat = 24

    fileprivate func height(_ size: CGFloat) -> CGFloat {
        switch self {
        case .lines(let lines): lines.height * size
        case .tapToAssign(let isDetected):
            Self.tapToAssignFont(size).lineHeight * 2 + (isDetected ? Self.badgeSpacing + Self.badgeSize : 0)
        }
    }

    /// How far under the readout's top its first line's capitals start
    fileprivate func capTop(_ size: CGFloat) -> CGFloat {
        switch self {
        case .lines:
            UIFont.systemFont(ofSize: size, weight: .semibold).let { $0.ascender - $0.capHeight + (size * ReadoutLines.lineHeight - $0.lineHeight) / 2 }
        case .tapToAssign:
            Self.tapToAssignFont(size).let { $0.ascender - $0.capHeight }
        }
    }

    /// How far over the readout's bottom its last line's baseline is
    fileprivate func lastBaseline(_ size: CGFloat) -> CGFloat {
        switch self {
        case .lines(let lines):
            UIFont.systemFont(ofSize: size * lines.lastLineScale, weight: .semibold)
                .let { -$0.descender + (size * lines.lastLineScale * ReadoutLines.lineHeight - $0.lineHeight) / 2 }
        case .tapToAssign(let isDetected):
            -Self.tapToAssignFont(size).descender + (isDetected ? Self.badgeSpacing + Self.badgeSize : 0)
        }
    }

    private static func tapToAssignFont(_ size: CGFloat) -> UIFont {
        UIFont.systemFont(ofSize: size * tapToAssignScale, weight: .semibold)
    }
}

extension UIFont {
    fileprivate func `let`<T>(_ block: (UIFont) -> T) -> T { block(self) }
}

/// Where everything of the main screen's vehicle goes, in its box, like the Android app's
/// VehicleSizing, Vehicle and FitsText. The largest text size from the settings' down is the first
/// whose readouts, outlines and frames don't overlap one another nor leave the box. The image is
/// sized from the readouts at that size. Unlike Android, it's worked out before anything is drawn,
/// from what's shown right now, and goes as low as it takes rather than stopping at half: iOS's
/// largest accessibility sizes go further than Android's.
struct VehicleLayout {
    /// The top bar's end corner, when it's beside the vehicle: only the spare's readout goes under it
    struct Corners {
        let start: CGFloat
        let end: CGFloat
        let height: CGFloat
        /// What's under the room at the screen's bottom, the home indicator
        var bottomInset: CGFloat = 0
    }

    /// What's laid out, whatever the room
    struct Content {
        let drawing: VehicleDrawing
        let locations: [TyreLocation]
        let isManaging: Bool
        /// Any location's outlined: the readouts leave room for the outlines
        let isOutlined: Bool
        /// The locations outlined
        let outlined: Set<TyreLocation>
        let kinds: [TyreLocation: ReadoutKind]
        let pressureUnit: PressureUnit
        let textSize: CGFloat

        @MainActor init(vehicle: Vehicle, monitor: Monitor, settings: Settings, baseSize: CGFloat, isManaging: Bool, isMoving: Bool, date: Date) {
            drawing = .of(vehicle.kind)
            locations = vehicle.kind.locations
            self.isManaging = isManaging
            outlined = Set(vehicle.kind.locations.filter { isMoving || isManaging || monitor.sensor(vehicle: vehicle, location: $0) == nil })
            isOutlined = outlined.isEmpty == false
            kinds = vehicle.kind.locations.reduce(into: [:]) { kinds, location in
                kinds[location] = monitor.sensor(vehicle: vehicle, location: location) == nil
                    ? .tapToAssign(isDetected: monitor.detected(vehicle: vehicle, location: location, now: date) != nil)
                    : .lines(ReadoutLines(tyre: monitor.tyres[Monitor.TyreKey(vehicleId: vehicle.id, location: location)], settings: settings, isBasic: isManaging))
            }
            pressureUnit = settings.pressureUnit
            textSize = baseSize * CGFloat(settings.readoutTextScale) * Demo.textScale
        }

        init(kind: VehicleKind, kinds: [TyreLocation: ReadoutKind], outlined: Set<TyreLocation>, isManaging: Bool, pressureUnit: PressureUnit, textSize: CGFloat) {
            drawing = .of(kind)
            locations = kind.locations
            self.isManaging = isManaging
            self.outlined = outlined
            isOutlined = outlined.isEmpty == false
            self.kinds = kinds
            self.pressureUnit = pressureUnit
            self.textSize = textSize
        }

        var sides: Int { locations.contains { $0.readoutSide == .leading } ? 2 : 1 }
        var hasSpare: Bool { locations.contains(.spare) }

        func slotWidth(_ size: CGFloat) -> CGFloat {
            VehicleLayout.widestReading(pressureUnit, isOutlined: isOutlined, size: size) + (isOutlined ? outlinePadding : 0)
        }

        /// The room a side's readouts take beside the image, without the image's overlap
        func readoutWidth(_ size: CGFloat) -> CGFloat {
            slotWidth(size) + readoutGap + (isOutlined ? outlineOutset : 0)
        }

        /// The room under the image for the spare's readout, when it's not beside the vehicle
        func spareRoom(_ size: CGFloat, corners: Corners?) -> CGFloat? {
            hasSpare && corners == nil ? spareReadoutGap + ReadoutLines.basic.height * size + outlineOutset : nil
        }

        /// As a share of the room's height, the image's, like VehicleSizing.imageHeight
        func imageHeight(_ room: CGSize, corners: Corners?, size: CGFloat, atLeast: CGFloat = minImageHeight, isAreaLimited: Bool = true) -> CGFloat {
            let between = room.width - (corners?.start ?? 0) - (corners?.end ?? 0)
            let sides = CGFloat(sides)
            let overlap = 1 - (1 - drawing.inset) / 2 * sides
            let perWidth = room.height * drawing.ratio
            var height = (between - readoutWidth(size) * sides - screenMargin * 2) / overlap / perWidth
            // From the middle of the room left to the box's end, the spare's readout goes under the bar
            if let corners, hasSpare {
                height = min(
                    height,
                    (room.width - corners.start - between / 2 - readoutWidth(size) - (spareBesideGap + slotWidth(size) + outlineOutset) - screenMargin) * 2 / overlap / perWidth
                )
            }
            if isAreaLimited {
                height = min(height, (maxImageArea * drawing.tallness * max(between, 0) / (drawing.ratio * room.height)).squareRoot())
            }
            height = max(height, atLeast * drawing.tallness)
            // Down to the spare's readout rather than to the image's bottom, see VehicleLayout's spareOverflow
            let most = spareRoom(size, corners: corners)
                .map { (room.height - verticalMargin * 2 - $0) / (room.height * drawing.spareBottom) }
                ?? (room.height - verticalMargin * 2) / room.height
            return min(height, most)
        }
    }

    let textSize: CGFloat
    let slotWidth: CGFloat
    let image: CGRect
    /// What its height would be without the maximum area, see isBesideBetter
    let unlimitedImageHeight: CGFloat
    /// Where what's over the vehicle centers on, see VehicleDrawing.Middle, nil for the screen's middle
    let middleY: CGFloat?
    let isSingleAxle: Bool
    let tyres: [TyreLocation: CGRect]
    let readouts: [TyreLocation: CGRect]
    /// Around a location's tyre and readout, of the locations outlined, and the spare when it's framed
    let outlines: [TyreLocation: CGRect]
    /// The spare's readout is beside the vehicle: framed, a line from its tyre going to it
    let spareFrame: CGRect?
    let spareLine: [CGPoint]
    private let fits: Bool
    /// Still overlapping at the smallest text it can go to
    var isMisfit: Bool { fits == false }

    init(_ content: Content, room: CGSize, corners: Corners?) {
        var size = content.textSize
        var layout = Self(content, room: room, corners: corners, size: size)
        while layout.fits == false, size > Self.minTextSize {
            size *= Self.textStep
            layout = Self(content, room: room, corners: corners, size: size)
        }
        self = layout
    }

    private init(_ content: Content, room: CGSize, corners: Corners?, size: CGFloat) {
        let drawing = content.drawing
        let slot = content.slotWidth(size)
        let readoutWidth = content.readoutWidth(size)
        let share = max(0, content.imageHeight(room, corners: corners, size: size))
        unlimitedImageHeight = room.height * max(0, content.imageHeight(room, corners: corners, size: size, atLeast: 0, isAreaLimited: false))
        let height = room.height * share, width = height * drawing.ratio
        let basic = ReadoutLines.basic.height * size
        // The image and its readouts are centered together, in what's between the corners
        let hasLeft = content.sides == 2
        let oneSided: CGFloat = (hasLeft ? readoutWidth : 0) - readoutWidth
        let cornersShift: CGFloat = (corners?.start ?? 0) - (corners?.end ?? 0)
        let offsetX: CGFloat = (oneSided + cornersShift) / 2
        let spareOverflow = content.spareRoom(size, corners: corners).map { max(0, $0 - height * (1 - drawing.spareBottom)) } ?? 0
        let centeredY = (room.height - height) / 2 - spareOverflow / 2
        // In landscape only the home indicator takes room, at the bottom: down to the screen's middle
        // as far as it stays above it
        let towardsMiddle = min((corners?.bottomInset ?? 0) / 2, max(0, room.height - verticalMargin - centeredY - height))
        let image = CGRect(x: (room.width - width) / 2 + offsetX, y: centeredY + towardsMiddle, width: width, height: height)
        let tyres: [TyreLocation: CGRect] = drawing.tyres.reduce(into: [:]) { rects, tyre in
            let long: CGFloat = drawing.tyreHeight * height
            let short: CGFloat = long * drawing.tyreRatio
            // The spare lies across the boot
            let tyreSize = tyre.key == .spare ? CGSize(width: long, height: short) : CGSize(width: short, height: long)
            rects[tyre.key] = CGRect(
                x: image.minX + tyre.value.x * width - tyreSize.width / 2,
                y: image.minY + tyre.value.y * height - tyreSize.height / 2,
                width: tyreSize.width,
                height: tyreSize.height
            )
        }
        // The rear ones keep above the line going to the spare's readout beside them
        let lineY = corners != nil && content.hasSpare ? image.minY + drawing.spareCenter * height : nil
        var readouts = [TyreLocation: CGRect]()
        var outlines = [TyreLocation: CGRect]()
        for location in content.locations where location != .spare {
            guard let side = location.readoutSide, let point = drawing.tyres[location], let tyre = tyres[location] else { continue }
            let reach = width * drawing.readoutSpan(location) / 2
            let x = side == .leading ? image.midX - reach - Self.readoutGap - slot : image.midX + reach + Self.readoutGap
            let readoutHeight = content.kinds[location]?.height(size) ?? basic
            let isOutlined = content.outlined.contains(location)
            let bottom = lineY.map { $0 - Self.spareLineClearance } ?? image.maxY
            let y = image.minY + point.y * height
            var center = y
            if isOutlined {
                // A rear tyre over the spare's: the outline grows up from the tyre's bottom
                let isOverSpare = content.hasSpare && point.y > 0.5
                let outlineHeight = min(max(tyre.height, basic), height)
                center = min(y, (isOverSpare ? tyre.maxY : bottom) - outlineHeight / 2)
                center = min(max(center, image.minY + outlineHeight / 2), image.maxY - outlineHeight / 2)
            }
            let top = max(min(center - readoutHeight / 2, bottom - readoutHeight), image.minY)
            let readout = CGRect(x: x, y: top, width: slot, height: readoutHeight)
            readouts[location] = readout
            if isOutlined {
                let outlineHeight = max(min(max(tyre.height, basic), height), readoutHeight)
                outlines[location] = tyre
                    .union(readout)
                    .union(CGRect(x: readout.minX, y: center - outlineHeight / 2, width: readout.width, height: outlineHeight))
                    .insetBy(dx: -Self.outlineOutset, dy: -Self.outlineOutset)
            }
        }
        var spareFrame: CGRect?
        var spareLine = [CGPoint]()
        var parts = [TyreLocation: [CGRect]]()
        if let spare = tyres[.spare] {
            let readoutHeight = content.kinds[.spare]?.height(size) ?? basic
            let isOutlined = content.outlined.contains(.spare)
            let rearLocation = TyreLocation.wheel(.rearRight)
            if let lineY, let rear = readouts[rearLocation], let rearTyre = tyres[rearLocation] {
                // Beside the rear right one, placed like its outline would be, in a frame titled as the spare
                let frameHeight: CGFloat = min(max(rearTyre.height, readoutHeight, basic), height)
                let center: CGFloat = max(min(rearTyre.midY, rearTyre.maxY - frameHeight / 2, image.maxY - frameHeight / 2), image.minY + frameHeight / 2)
                let readout = CGRect(x: rear.maxX + Self.spareBesideGap, y: center - readoutHeight / 2, width: slot, height: readoutHeight)
                let frame = CGRect(x: readout.minX, y: center - frameHeight / 2, width: slot, height: frameHeight)
                    .insetBy(dx: -Self.outlineOutset, dy: -Self.outlineOutset)
                readouts[.spare] = readout
                spareFrame = frame
                outlines[.spare] = frame
                spareLine = [CGPoint(x: spare.maxX, y: lineY), CGPoint(x: readout.midX, y: lineY), CGPoint(x: readout.midX, y: frame.maxY)]
                parts[.spare] = [
                    readout,
                    CGRect(x: frame.minX, y: frame.minY - Self.frameTitleInset(size), width: frame.width, height: frame.height + Self.frameTitleInset(size)),
                    CGRect(x: spare.maxX, y: lineY - Self.spareLineClearance, width: readout.maxX - spare.maxX, height: Self.spareLineClearance * 2),
                ]
            } else {
                // Under its tyre, on the vehicle's middle
                let readout = CGRect(x: image.midX - slot / 2, y: spare.maxY + Self.spareReadoutGap, width: slot, height: readoutHeight)
                readouts[.spare] = readout
                if isOutlined { outlines[.spare] = spare.union(readout).insetBy(dx: -Self.outlineOutset, dy: -Self.outlineOutset) }
            }
        }
        for location in content.locations where parts[location] == nil {
            parts[location] = [readouts[location], outlines[location]].compactMap { $0 }
        }
        let tolerance: CGFloat = 0.5
        let allowed = CGRect(origin: .zero, size: room).insetBy(dx: -tolerance, dy: -tolerance)
        let bar = corners.map { CGRect(x: room.width - $0.end, y: 0, width: $0.end, height: $0.height) }
        let all: [CGRect] = parts.values.flatMap { $0 }
        let pairs = parts.flatMap { location, rects in
            parts.filter { $0.key != location }.values.flatMap { others in
                rects.flatMap { rect in others.map { (rect, $0) } }
            }
        }
        fits = height > 0
            && all.allSatisfy { allowed.contains($0) && (bar?.intersects($0) ?? false) == false }
            && (bar?.intersects(image) ?? false) == false
            && pairs.allSatisfy { $0.0.insetBy(dx: tolerance, dy: tolerance).intersects($0.1) == false }
        textSize = size
        slotWidth = slot
        self.image = image
        self.tyres = tyres
        self.readouts = readouts
        self.outlines = outlines
        self.spareFrame = spareFrame
        self.spareLine = spareLine
        let axles: [CGFloat] = content.locations.filter { $0 != .spare }.compactMap { drawing.tyres[$0]?.y }
        let axlesMiddle: CGFloat = ((axles.min() ?? 0.5) + (axles.max() ?? 0.5)) / 2
        switch drawing.middle {
        case .betweenAxles: middleY = image.minY + axlesMiddle * height
        case .at(let share): middleY = image.minY + share * height
        case .screen: middleY = nil
        }
        isSingleAxle = drawing.isSingleAxle
    }

    /// The top bar goes beside the vehicle when that leaves it the tallest image, like Android's
    /// VehicleWithTopBar, once each has fit its text: too large for either, Android's would both be 0
    /// and stay over the vehicle. This keeps what fits first, then the largest text.
    static func isBesideBetter(_ beside: VehicleLayout, than over: VehicleLayout) -> Bool {
        if beside.fits != over.fits { return beside.fits }
        // Without the maximum area, or a vehicle as wide as it's tall would have the bar over it in
        // landscape: more area, but less height
        return beside.textSize != over.textSize ? beside.textSize > over.textSize : beside.unlimitedImageHeight > over.unlimitedImageHeight
    }

    /// A line in the room between two readouts one above the other, like FitsText's separators,
    /// midway between the upper one's last baseline and the capitals of the lower one
    func separators(_ content: Content) -> [(y: CGFloat, from: CGFloat, to: CGFloat)] {
        let sorted = readouts.sorted { $0.value.minY < $1.value.minY }
        return sorted.compactMap { upper in
            sorted
                .first {
                    $0.key != upper.key
                        && $0.value.minY >= upper.value.maxY - 1
                        && $0.value.minX < upper.value.maxX && upper.value.minX < $0.value.maxX
                }
                .flatMap { lower -> (y: CGFloat, from: CGFloat, to: CGFloat)? in
                    guard lower.value.minY - upper.value.maxY < textSize * ReadoutLines.lineHeight * Self.separatedLines,
                          let upperKind = content.kinds[upper.key], let lowerKind = content.kinds[lower.key]
                    else { return nil }
                    return (
                        y: (upper.value.maxY - upperKind.lastBaseline(textSize) + lower.value.minY + lowerKind.capTop(textSize)) / 2,
                        from: min(upper.value.minX, lower.value.minX),
                        to: max(upper.value.maxX, lower.value.maxX)
                    )
                }
        }
    }

    /// In the coordinates of what's [origin] is the box's
    func bounds(origin: CGPoint) -> VehicleBounds {
        let moved = { (rect: CGRect) in rect.offsetBy(dx: origin.x, dy: origin.y) }
        return VehicleBounds(
            readouts: readouts.mapValues(moved),
            frames: outlines.map { location, frame in
                moved(location == .spare && spareFrame != nil
                    ? CGRect(x: frame.minX, y: frame.minY - Self.frameTitleInset(textSize), width: frame.width, height: frame.height + Self.frameTitleInset(textSize))
                    : frame)
            },
            image: moved(image),
            middleY: middleY.map { $0 + origin.y },
            isSingleAxle: isSingleAxle
        )
    }

    /// How wide the widest line a reading can show is at this text size, like Android's
    /// rememberWidestReadoutWidth: each line's found once, then measured again at each size tried, as
    /// the system font's tracking changes with its size
    static func widestReading(_ unit: PressureUnit, isOutlined: Bool, size: CGFloat) -> CGFloat {
        let font = UIFont.systemFont(ofSize: size, weight: .semibold)
        let lines = max(
            ((widestPressure[unit] ?? "") as NSString).size(withAttributes: [.font: font]).width,
            (widestDetail as NSString).size(withAttributes: [.font: font]).width
        )
        return isOutlined
            ? max(lines, ("Tap to" as NSString).size(withAttributes: [.font: UIFont.systemFont(ofSize: size * ReadoutKind.tapToAssignScale, weight: .semibold)]).width)
            : lines
    }

    private static let referenceFont = UIFont.systemFont(ofSize: 17, weight: .semibold)

    private static func widest(_ lines: [String]) -> String {
        lines
            .flatMap { line in
                (0...9).map { digit in line.map { $0.isNumber ? String(digit) : String($0) }.joined() }
            }
            .max { ($0 as NSString).size(withAttributes: [.font: referenceFont]).width < ($1 as NSString).size(withAttributes: [.font: referenceFont]).width }
            ?? ""
    }

    private static let widestPressure: [PressureUnit: String] = [
        PressureUnit.kiloPascal: "1034 kPa*", .bar: "8.88 bar*", .psi: "88.8 psi*",
    ]
    .mapValues { widest([$0]) }

    private static let widestDetail = widest(["188°F", "188°C", "88 hours", "99+ days"])

    private static let textStep: CGFloat = 0.97
    private static let minTextSize: CGFloat = 6
    /// How close two readouts get with a separator between them, in lines
    private static let separatedLines: CGFloat = 2

    /// The "Spare" title, Android's labelMedium's 12 sp over the readings' 16, and half its 16 sp line
    /// height, which is drawn on the frame's top edge
    static let frameTitleScale: CGFloat = 12 / 16
    static func frameTitleInset(_ size: CGFloat) -> CGFloat { size / 2 }
    static let outlineOutset: CGFloat = 6
    /// Between an outline's outer edge and what's centered in it
    static let outlinePadding: CGFloat = 4
    static let minImageHeight: CGFloat = 0.45
}

// Vehicle.kt's
private let maxImageArea: CGFloat = 0.5
private let readoutGap = VehicleLayout.readoutGap
private let screenMargin: CGFloat = 4
private let outlineOutset = VehicleLayout.outlineOutset
private let outlinePadding = VehicleLayout.outlinePadding
private let verticalMargin = outlineOutset + screenMargin
private let spareReadoutGap = VehicleLayout.spareReadoutGap
private let spareBesideGap = VehicleLayout.spareBesideGap
private let minImageHeight = VehicleLayout.minImageHeight

extension VehicleLayout {
    fileprivate static let readoutGap: CGFloat = 8
    fileprivate static let spareReadoutGap: CGFloat = 4
    fileprivate static let spareBesideGap: CGFloat = 24
    fileprivate static let spareLineClearance: CGFloat = 6
}

/// Where the vehicle's readouts, frames, image and middle are, in VehicleBounds.space, for what's laid
/// out over them: see ClearOfReadouts
struct VehicleBounds: Equatable {
    /// The coordinate space these are in, set on what ClearOfReadouts is an overlay of
    static let space = "vehicleBounds"

    let readouts: [TyreLocation: CGRect]
    let frames: [CGRect]
    let image: CGRect
    /// Nil for the screen's middle
    let middleY: CGFloat?
    /// Nothing goes between its axles
    let isSingleAxle: Bool

    struct Key: PreferenceKey {
        static let defaultValue: VehicleBounds? = nil

        static func reduce(value: inout VehicleBounds?, nextValue: () -> VehicleBounds?) {
            value = nextValue() ?? value
        }
    }
}

/// Lays its only view out over the vehicle, as the Android app's ClearOfReadouts, covering no reading,
/// whichever fits first:
/// - between the axles, centered on its middle, unless it has a single axle: there's nothing between
///   its axles, it would cover the drawing. It then goes under the vehicle even with its text smaller.
/// - in the room under the vehicle, while the bar is over it. Extra lines on the rear's readouts can
///   leave less room there than above it, between the bar and the vehicle, where it then goes rather
///   than showing smaller.
/// - in the room past the readings' end, under the bar's end corner, while the bar is beside it, or
///   the widest part of it a spare's readout leaves, as close to the middle as it gets there
/// - between the axles still, raised to cover the front's lower lines rather than the rear's pressure
/// Its text gets smaller by itself as the room asks, through its minimumScaleFactor.
struct ClearOfReadouts: Layout {
    let bounds: VehicleBounds?
    /// The top bar: all across, or its end corner
    let bar: [CGRect]
    /// What the home indicator takes under the box while the bar is beside the vehicle: it aims for the
    /// screen's middle rather than the box's
    let bottomInset: CGFloat

    private static let clearance: CGFloat = 8

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout Void) -> CGSize {
        proposal.replacingUnspecifiedDimensions()
    }

    func placeSubviews(in box: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout Void) {
        guard let subview = subviews.first, let bounds else { return }
        let clearance = Self.clearance
        let readouts = Array(bounds.readouts.values)
        // The spare's readout beside the rear right one isn't part of the readings' width
        let vehicle = bounds.readouts
            .filter { $0.key != .spare }
            .values
            .reduce(bounds.image) { $0.union($1) }
        // The image isn't one: between the axles is over it, the rooms around it are out of it
        let obstacles = readouts + bounds.frames + bar
        let isClear = { (rect: CGRect) in obstacles.contains { $0.intersects(rect) } == false }
        let height = subview.sizeThatFits(ProposedViewSize(width: vehicle.width, height: nil)).height
        let middleY = bounds.middleY ?? box.height / 2
        let between = CGRect(x: vehicle.minX, y: middleY - height / 2, width: vehicle.width, height: height)
        let isBarOver = bar.contains { $0.width >= box.width - 1 }
        let beside = CGRect(
            x: vehicle.maxX + clearance,
            y: bar.max { $0.maxX < $1.maxX }?.maxY ?? 0,
            width: box.width - clearance - vehicle.maxX - clearance,
            height: 0
        )
        .withBottom(box.height - clearance)
        let besideObstacles = obstacles.filter { $0.intersects(beside) }
        let aimY = isBarOver == false && besideObstacles.isEmpty ? (box.height + bottomInset) / 2 : nil
        // Under the vehicle at full size, then above it. Otherwise the widest room beside it.
        let rooms: [CGRect] = isBarOver
            ? [
                CGRect(x: vehicle.minX, y: (readouts + bounds.frames).reduce(bounds.image) { $0.union($1) }.maxY + clearance, width: vehicle.width, height: 0)
                    .withBottom(box.height - clearance),
            ]
            .filter { bounds.isSingleAxle || height <= $0.height }
            + [
                CGRect(x: vehicle.minX, y: bar.map(\.maxY).max() ?? 0, width: vehicle.width, height: 0)
                    .withBottom(readouts.reduce(bounds.image) { $0.union($1) }.minY - clearance),
            ]
            : (besideObstacles.isEmpty
                ? [beside]
                : besideObstacles.flatMap { obstacle in
                    [
                        beside.withBottom(obstacle.minY - clearance),
                        beside.withTop(obstacle.maxY + clearance),
                        beside.withLeft(obstacle.maxX + clearance),
                    ]
                })
            .sorted { $0.width > $1.width }
        // The first room it fits in, at the largest size it fits there
        let inRoom = rooms
            .filter { $0.width > 0 && $0.height > 0 && isClear($0) }
            .lazy
            .compactMap { room -> (CGRect, ProposedViewSize)? in
                let proposal = ProposedViewSize(room.size)
                let size = subview.sizeThatFits(proposal)
                guard size.width <= room.width + 0.5, size.height <= room.height + 0.5 else { return nil }
                let y = aimY.map { min(max($0, room.minY + size.height / 2), room.maxY - size.height / 2) } ?? room.midY
                return (CGRect(x: room.minX, y: y - size.height / 2, width: room.width, height: size.height), proposal)
            }
            .first
        // Raised over the front's lower lines, its bottom off the rear's readouts
        let raised = readouts
            .filter { $0.minY > middleY }
            .map(\.minY)
            .min()
            .map { between.offsetBy(dx: 0, dy: min($0 - clearance - between.maxY, 0)) }
        let (placed, placedProposal) = bounds.isSingleAxle == false && isClear(between)
            ? (between, ProposedViewSize(width: vehicle.width, height: nil))
            : inRoom ?? (raised ?? between, ProposedViewSize(width: vehicle.width, height: nil))
        subview.place(
            at: CGPoint(x: box.minX + placed.midX, y: box.minY + placed.midY),
            anchor: .center,
            proposal: placedProposal
        )
    }
}

extension CGRect {
    fileprivate func withBottom(_ bottom: CGFloat) -> CGRect {
        CGRect(x: minX, y: minY, width: width, height: bottom - minY)
    }

    fileprivate func withTop(_ top: CGFloat) -> CGRect {
        CGRect(x: minX, y: top, width: width, height: maxY - top)
    }

    fileprivate func withLeft(_ left: CGFloat) -> CGRect {
        CGRect(x: left, y: minY, width: maxX - left, height: height)
    }
}
