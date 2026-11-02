import Foundation
import TPMSCore

/// The demo mode of the settings, or a launch with `-demo`, shows made-up vehicles from real advertisements, in an
/// in-memory database and without scanning, to look at every state of the screens (the Simulator
/// has no Bluetooth). `-screen tyre|vehicle|vehicles|settings|manage` opens that screen too, and
/// `-screen empty|assign|bluetooth|qr` the steps of assigning a sensor to a vehicle without any.
/// `-scanned <code>` stands for what the QR code scanner's camera would read.
enum Demo {
    static var isOn: Bool { ProcessInfo.processInfo.arguments.contains("-demo") || UserDefaults.standard.bool(forKey: Settings.demoModeKey) }

    static var screen: String? {
        let arguments = ProcessInfo.processInfo.arguments
        return arguments.firstIndex(of: "-screen").flatMap { arguments.indices.contains($0 + 1) ? arguments[$0 + 1] : nil }
    }

    static var scanned: String? {
        let arguments = ProcessInfo.processInfo.arguments
        return arguments.firstIndex(of: "-scanned").flatMap { arguments.indices.contains($0 + 1) ? arguments[$0 + 1] : nil }
    }

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
