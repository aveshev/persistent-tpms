import Foundation
import TPMSCore
import SwiftUI

/// The demo mode of the settings, or a launch with `-demo`, shows made-up vehicles from real advertisements, in an
/// in-memory database and without scanning, to look at every state of the screens (the Simulator
/// has no Bluetooth). `-screen tyre|vehicle|vehicles|settings|manage` opens that screen too, and
/// `-screen empty|assign|bluetooth|qr` the steps of assigning a sensor to a vehicle without any.
/// `-scanned <code>` stands for what the QR code scanner's camera would read. `-kind <VehicleKind's raw
/// value>` shows that kind of vehicle, a reading on each tyre, or without sensors with `-unassigned`.
/// `-render <dir>` and `-layoutcheck`, for what's below.
enum Demo {
    /// On top of Dynamic Type, which Catalyst ignores: `-textscale <the system's scale at a size>` for -render
    static let textScale: CGFloat = argument("-textscale").flatMap(Double.init).map { CGFloat($0) } ?? 1

    static var isOn: Bool { ProcessInfo.processInfo.arguments.contains("-demo") || UserDefaults.standard.bool(forKey: Settings.demoModeKey) }

    static var screen: String? {
        let arguments = ProcessInfo.processInfo.arguments
        return arguments.firstIndex(of: "-screen").flatMap { arguments.indices.contains($0 + 1) ? arguments[$0 + 1] : nil }
    }

    static var scanned: String? {
        let arguments = ProcessInfo.processInfo.arguments
        return arguments.firstIndex(of: "-scanned").flatMap { arguments.indices.contains($0 + 1) ? arguments[$0 + 1] : nil }
    }

    private static func argument(_ name: String) -> String? {
        let arguments = ProcessInfo.processInfo.arguments
        return arguments.firstIndex(of: name).flatMap { arguments.indices.contains($0 + 1) ? arguments[$0 + 1] : nil }
    }

    #if DEBUG
    /// `-pocket`: dimmed, and saying the screen was just turned off in a pocket, for -render
    static var isPocketShown: Bool { ProcessInfo.processInfo.arguments.contains("-pocket") }

    /// Renders the main screen into the file `-render <path>`, in the room an iPhone's safe area leaves
    /// (`-landscape`, or portrait), then quits. Runs without a Simulator as Mac Catalyst, see README.md:
    /// it's the same SwiftUI and fonts, in a plain window. One a launch: `textScale` is read as views
    /// are evaluated, so it can't change while they're shown.
    static func renderScreen(monitor: Monitor, settings: Settings) {
        guard let path = argument("-render"), let window = UIApplication.shared.connectedScenes
            .compactMap({ $0 as? UIWindowScene })
            .first
            .flatMap({ scene -> UIWindow? in
                scene.sizeRestrictions?.minimumSize = CGSize(width: 1000, height: 1000)
                return scene.windows.first
            })
        else { return }
        let room = ProcessInfo.processInfo.arguments.contains("-landscape") ? CGSize(width: 750, height: 369) : CGSize(width: 390, height: 763)
        Task { @MainActor in
            try? await Task.sleep(for: .seconds(1))
            let host = UIHostingController(rootView: Group {
                if screen == "manage", let vehicle = monitor.current {
                    NavigationStack { ManageSensorsView(vehicleId: vehicle.id, monitor: monitor, settings: settings) }
                } else {
                    MainView(monitor: monitor, settings: settings)
                }
            }.tint(Theme.primary))
            host.view.frame = CGRect(origin: .zero, size: room)
            window.addSubview(host.view)
            try? await Task.sleep(for: .seconds(1.5))
            let image = UIGraphicsImageRenderer(bounds: host.view.bounds).image { _ in
                host.view.drawHierarchy(in: host.view.bounds, afterScreenUpdates: true)
            }
            try? image.pngData()?.write(to: URL(fileURLWithPath: path))
            exit(0)
        }
    }

    /// Every kind at every Dynamic Type size of the largest and smallest iPhones' rooms, each way up,
    /// with and without sensors, and with every line the settings add: what still overlaps at the
    /// smallest text it can go to, and the text and image sizes each ends up with
    static func checkLayouts() {
        guard ProcessInfo.processInfo.arguments.contains("-layoutcheck") else { return }
        // The safe areas' sizes: an SE, a 13 Pro, a 16 Pro Max
        let rooms: [(String, CGSize)] = [
            ("se portrait", CGSize(width: 375, height: 647)), ("se landscape", CGSize(width: 667, height: 375)),
            ("13pro portrait", CGSize(width: 390, height: 763)), ("13pro landscape", CGSize(width: 750, height: 369)),
            ("max portrait", CGSize(width: 440, height: 840)), ("max landscape", CGSize(width: 824, height: 419)),
        ]
        // UIFontMetrics' body scale at each end, xSmall and accessibility 5
        let sizes: [(String, CGFloat)] = [("xs", 14.0 / 17), ("large", 1), ("ax5", 53.0 / 17)]
        let all = ReadoutLines(showsLeak: true, battery: "3.0V", sensorId: "012345", flags: [0x12, 0x34])
        let states: [(String, (VehicleKind) -> VehicleLayout.Content)] = [
            ("assigned", { .init(kind: $0, kinds: Dictionary(uniqueKeysWithValues: $0.locations.map { ($0, ReadoutKind.lines(.basic)) }), outlined: [], isManaging: false, pressureUnit: .kiloPascal, textSize: TyreReadout.baseSize) }),
            ("unassigned", { .init(kind: $0, kinds: Dictionary(uniqueKeysWithValues: $0.locations.map { ($0, ReadoutKind.tapToAssign(isDetected: true)) }), outlined: Set($0.locations), isManaging: false, pressureUnit: .kiloPascal, textSize: TyreReadout.baseSize) }),
            ("managing", { .init(kind: $0, kinds: Dictionary(uniqueKeysWithValues: $0.locations.map { ($0, ReadoutKind.lines(.basic)) }), outlined: Set($0.locations), isManaging: true, pressureUnit: .kiloPascal, textSize: TyreReadout.baseSize) }),
            ("all lines", { .init(kind: $0, kinds: Dictionary(uniqueKeysWithValues: $0.locations.map { ($0, ReadoutKind.lines(all)) }), outlined: [], isManaging: false, pressureUnit: .kiloPascal, textSize: TyreReadout.baseSize) }),
        ]
        var misfits = 0
        for kind in VehicleKind.allCases {
            for (roomName, room) in rooms {
                for (sizeName, scale) in sizes {
                    for (stateName, state) in states {
                        let base = state(kind)
                        let content = VehicleLayout.Content(kind: kind, kinds: base.kinds, outlined: base.outlined, isManaging: base.isManaging, pressureUnit: .kiloPascal, textSize: TyreReadout.baseSize * scale)
                        let corners = VehicleLayout.Corners(start: 0, end: 150, height: 44 * max(1, scale * 0.8))
                        let over = VehicleLayout(content, room: CGSize(width: room.width, height: room.height - corners.height), corners: nil)
                        let beside = VehicleLayout(content, room: room, corners: corners)
                        let isBeside = VehicleLayout.isBesideBetter(beside, than: over)
                        let layout = isBeside ? beside : over
                        if layout.isMisfit { misfits += 1 }
                        print("LAYOUT \(kind.rawValue) \(roomName) \(sizeName) \(stateName) \(isBeside ? "beside" : "over"): text \(String(format: "%.1f", layout.textSize)) image \(Int(layout.image.height))\(layout.isMisfit ? " MISFIT" : "")")
                    }
                }
            }
        }
        print("LAYOUT misfits: \(misfits)")
        exit(0)
    }
    #endif

    private static var startsEmpty: Bool { ["empty", "assign", "bluetooth", "qr"].contains(screen) }

    /// A sensor at [kpa], as if heard, for assigning by Bluetooth
    static func reading(sensorId: Int, kpa: Float) -> SensorReading? {
        AdvertisingPacket(bytes: Data(hex: "0303a5270308425208ff101e140179da44")!)
            .decode(timestamp: Date().timeIntervalSince1970, rssi: -60, addressId: sensorId)
            .map { reading in
                var reading = reading
                reading.pressure = Pressure(kpa: kpa)
                return reading
            }
    }

    /// Fills [database], returning the readings of unassigned sensors heard around
    static func seed(_ database: Database) throws -> [SensorReading] {
        let now = Date().timeIntervalSince1970
        if let kind = argument("-kind").flatMap(VehicleKind.init(rawValue:)) {
            var vehicle = Vehicle.new(name: "NMax", kind: kind)
            vehicle.isCurrent = true
            try database.save(vehicle)
            if ProcessInfo.processInfo.arguments.contains("-unassigned") == false {
                for (index, location) in kind.locations.enumerated() {
                    guard let reading = AdvertisingPacket(bytes: Data(hex: "0303A5270308425208FF401E2301E261E6")!).decode(timestamp: now - 30, rssi: -70, addressId: index + 1) else { continue }
                    try database.bind(BoundSensor(id: reading.sensorId, brand: reading.brand, vehicleId: vehicle.id, location: location))
                    try database.insert(reading, vehicleId: vehicle.id, location: location)
                }
            }
            return []
        }
        var nmax = Vehicle.new(name: "NMax", kind: .motorcycle)
        var car = Vehicle.new(name: "Car", kind: .car)
        if screen == "managecar" { car.isCurrent = true } else { nmax.isCurrent = true }
        // The current one last: a vehicle saved as current takes it from the others
        try database.save(screen == "managecar" ? nmax : car)
        try database.save(screen == "managecar" ? car : nmax)
        if startsEmpty {
            var empty = Vehicle.new(name: "New car", kind: .car)
            empty.isCurrent = true
            try database.save(empty)
        }

        func record(_ hex: String, _ vehicle: Vehicle, _ location: TyreLocation, id: Int, ago: Double) throws {
            guard let reading = AdvertisingPacket(bytes: Data(hex: hex)!).decode(timestamp: now - ago, rssi: -70, addressId: id) else { return }
            try database.bind(BoundSensor(id: reading.sensorId, brand: reading.brand, vehicleId: vehicle.id, location: location))
            try database.insert(reading, vehicleId: vehicle.id, location: location)
        }
        // The NMax on 2026-10-31: its front Wicarlink, its rear Pecham warming up
        try record("0201060303B0FB12FFAC00C03652000DFA007010FF1100C52122", nmax, .axle(.front), id: 0, ago: 200)
        for (index, hex) in ["0303A5270308425208FF401E1F01D06FCA", "0303A5270308425208FF401E2201D961C6", "0303A5270308425208FF401E2301E261E6"].enumerated() {
            try record(hex, nmax, .axle(.rear), id: 1_734_484_831, ago: Double(300 - index * 120))
        }
        // The Android demo's: flat and cold at the front left, hot at the rear right
        try record("0201060303b0fb13ff010080eaca010000409c0000dc0500005a00", car, .wheel(.frontLeft), id: 0, ago: 30)
        try record("0303a5270308425208ff101e140179da44", car, .wheel(.frontRight), id: 4, ago: 4000)
        try record("0303a5270308425208ff101d2301b30a51", car, .wheel(.rearLeft), id: 6, ago: 90)
        try record("0201060303b0fb13ff010083eaca070000c04504001c2500005a00", car, .wheel(.rearRight), id: 0, ago: 10)

        return [
            "0201060303B0FB12FFAC00C38251020D9800DD10FF1100C42122",
            "0303a5270308425208ff101e1201c2f50d",
            "0201060303b0fb13ff010081eaca03000000710200d00700005a00",
        ].enumerated().compactMap { index, hex in
            AdvertisingPacket(bytes: Data(hex: hex)!).decode(timestamp: now - Double(index * 40), rssi: -50 - index * 12, addressId: 9)
        }
    }
}
