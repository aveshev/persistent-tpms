import Foundation

/// A sensor's advertisement, as broadcast: what the database stores (`Reading.raw`), decoded the
/// same way as the Android app does (AdvertisingPacket.kt), whose real packets the tests use.
///
/// iOS doesn't give an advertisement's bytes, only what it found in them: [init(name:serviceUUIDs:
/// manufacturerData:)] lays them out again, in the order each sensor sends them. Pecham's and
/// Wicarlink's CRCs cover more than their manufacturer data, so the order matters, and the stored
/// readings are the same bytes as on Android.
public struct AdvertisingPacket {
    public let bytes: Data

    /// The AD structures, by type
    private let structures: [UInt8: Data]

    public init(bytes: Data) {
        self.bytes = bytes
        var structures = [UInt8: Data]()
        let array = [UInt8](bytes)
        var index = 0
        while index < array.count, array[index] != 0 {
            let length = Int(array[index])
            if index + length >= array.count { break }
            let type = array[index + 1]
            if structures[type] == nil { structures[type] = Data(array[(index + 2)..<(index + 1 + length)]) }
            index += 1 + length
        }
        self.structures = structures
    }

    /// From what iOS reports. [serviceUUIDs] are 16-bit UUIDs as CoreBluetooth prints them
    /// ("27A5"), [manufacturerData] starts with its company ID.
    public init(name: String?, serviceUUIDs: [String], manufacturerData: Data) {
        let uuids = serviceUUIDs.compactMap { UInt16($0, radix: 16) }
        let mfg = Self.structure(0xFF, manufacturerData)
        let uuidList = Self.structure(0x03, Data(uuids.flatMap { [UInt8($0 & 0xFF), UInt8($0 >> 8)] }))
        let shortName = name.map { Self.structure(0x08, Data($0.utf8)) } ?? Data()
        let flags = Self.structure(0x01, Data([0x06]))
        let bytes: Data = if uuids.contains(0x27A5) {
            // Pecham and Bekubee KY: "0303A527" "03084252" "08FF..."
            uuidList + shortName + mfg
        } else if uuids.contains(0xA827) || uuids.contains(0xA828) {
            // Bekubee TPMS: "050854504D53" "0CFF..." "030327A8", Bekubee TC.TPMS the same with
            // "080854432E54504D53" and "030328A8"
            shortName + mfg + uuidList
        } else {
            // Wicarlink and Sysgration: "020106" "0303B0FB" "12FF..."
            flags + uuidList + mfg
        }
        self.init(bytes: bytes)
    }

    private static func structure(_ type: UInt8, _ data: Data) -> Data {
        Data([UInt8(data.count + 1), type]) + data
    }

    public var name: String? {
        (structures[0x09] ?? structures[0x08]).flatMap { String(data: $0, encoding: .utf8) }
    }

    public var flags: UInt8? { structures[0x01]?.first }

    /// The company ID, little endian, then the data
    public var manufacturer: (companyId: Int, data: Data)? {
        guard let data = structures[0xFF], data.count >= 2 else { return nil }
        let array = [UInt8](data)
        return (Int(array[0]) | Int(array[1]) << 8, Data(array[2...]))
    }

    public var serviceUUIDs16: [Int] {
        [structures[0x02], structures[0x03]].compactMap { $0 }.flatMap { data -> [Int] in
            let array = [UInt8](data)
            return stride(from: 0, to: array.count - 1, by: 2).map { Int(array[$0]) | Int(array[$0 + 1]) << 8 }
        }
    }

    /// nil when this isn't the advertisement of a known sensor, or it doesn't pass its checks.
    /// [sensorId] is the one Pecham and Bekubee KY get from their address, see [SensorBrand].
    public func decode(timestamp: Double = 0, rssi: Int = 0, addressId: Int = 0) -> SensorReading? {
        let a = [UInt8](bytes)
        func reading(_ brand: SensorBrand, _ sensorId: Int, _ pressure: Pressure, _ temperature: Temperature,
                     alarm: Bool = false, volts: Float? = nil, percent: Int? = nil, location: SensorLocation? = nil) -> SensorReading {
            SensorReading(timestamp: timestamp, rssi: rssi, sensorId: sensorId, pressure: pressure, temperature: temperature,
                          brand: brand, isAlarm: alarm, batteryVoltage: volts.map(Voltage.init(volts:)),
                          batteryPercent: percent, location: location, raw: bytes)
        }

        // Pecham and Bekubee KY: status, decivolts, °C, pressure then CRC, from the advertisement's
        // byte 10. The manufacturer data's first 2 bytes aren't a company ID.
        if let brand = (name == "BR" ? SensorBrand.pecham : name == "KY" ? .bekubeeKy : nil),
           a.count >= 17, Self.isValidPechamCRC(a, brand: brand) {
            let d = Array(a[10..<17])
            return reading(brand, addressId,
                           // 145 at 0 psi for Pecham, 146 for Bekubee KY, like RawPecham.kt and RawBekubeeKy.kt
                           .psi(Float((Int(d[3]) << 8 | Int(d[4])) - (brand == .pecham ? 145 : 146)) / 10),
                           Temperature(celsius: Float(Int8(bitPattern: d[2]))),
                           volts: Float(Int8(bitPattern: d[1])) / 10)
        }

        // Wicarlink
        if flags == 0x06, serviceUUIDs16.contains(0xFBB0), a.count >= 26, Self.isValidWicarlinkCRC(a) {
            let id = Int(a[23]) | Int(a[24]) << 8 | Int(a[25]) << 16
            let units = Int(a[12]) + (a[17] == 1 ? 256 : 0)
            // The sensor reads steps of 0.1 bar, sent rounded to units of 3.144 kPa
            let kpa = (Float(units) * 3.144 / 10).rounded() * 10
            return reading(.wicarlink, id, Pressure(kpa: kpa), Temperature(celsius: Float(Int(a[13]) - 55)),
                           // 0x01 "leakage", sent at once on a pressure drop. 0x02, "inflating", isn't an alarm.
                           alarm: a[14] == 0x01, volts: Float(a[11]) * 0.01 + 1.22)
        }

        // Bekubee TPMS, whatever its company ID, which changes with its state
        if name == "TPMS", serviceUUIDs16.contains(0xA827), let m = manufacturer?.data, m.count >= 9 {
            let d = [UInt8](m)
            return reading(.bekubeeTpms, Int(d[4]) | Int(d[5]) << 8 | Int(d[6]) << 16,
                           Pressure(kpa: Float((Int(d[2]) << 8 | Int(d[3])) - 100)),
                           Temperature(celsius: Float(Int(d[1]) - 50)),
                           volts: Float(d[0]) * 0.01 + 1.22)
        }

        // Bekubee TC.TPMS (AOOCCI), see RawBekubeeTcTpms.kt. The manufacturer data's first 2 bytes
        // are a status byte and the battery in decivolts, then come °C, the pressure (absolute, in
        // 0.1 psi, 146 at 0 psi) and the sensor's address, whose last 3 bytes are its ID
        if name == "TC.TPMS", serviceUUIDs16.contains(0xA828), let m = manufacturer, m.data.count >= 9 {
            let d = [UInt8](m.data)
            return reading(.bekubeeTcTpms, Int(d[6]) << 16 | Int(d[7]) << 8 | Int(d[8]),
                           .psi(Float((Int(d[1]) << 8 | Int(d[2])) - 146) / 10),
                           Temperature(celsius: Float(Int8(bitPattern: d[0]))),
                           volts: Float(m.companyId >> 8 & 0xFF) / 10)
        }

        // Sysgration: location, EA CA, ID, pressure, temperature, battery and alarm
        if let m = manufacturer?.data, m.count >= 16 {
            let d = [UInt8](m)
            guard d[1] == 0xEA, d[2] == 0xCA, let location = SensorLocation(rawValue: d[0]) else { return nil }
            func int32(_ from: Int) -> Int32 {
                Int32(bitPattern: UInt32(d[from]) | UInt32(d[from + 1]) << 8 | UInt32(d[from + 2]) << 16 | UInt32(d[from + 3]) << 24)
            }
            // Little endian after a zero byte, like RawSysgration.kt: a multiple of 256
            let id = Int(Int32(bitPattern: UInt32(d[3]) << 8 | UInt32(d[4]) << 16 | UInt32(d[5]) << 24))
            return reading(.sysgration, id,
                           Pressure(kpa: Float(int32(6)) / 1000),
                           Temperature(celsius: Float(int32(10)) / 100),
                           alarm: d[15] == 0x01,
                           // A percentage, anything above 100 isn't one
                           percent: Int(d[14]) <= 100 ? Int(d[14]) : nil,
                           location: location)
        }
        return nil
    }

    /// Shown for debugging: what is known or suspected to be a status byte, see AdvertisingPacket.kt
    public var statusBytes: [UInt8]? {
        let a = [UInt8](bytes)
        if name == "BR" || name == "KY" { return a.count > 10 ? [a[10]] : nil }
        if name == "TPMS" { return manufacturer.map { [UInt8($0.companyId & 0xFF)] } }
        // 0x01 with pressure, 0x02 without
        if name == "TC.TPMS" { return manufacturer.map { [UInt8($0.companyId & 0xFF)] } }
        if let m = manufacturer?.data, m.count > 15, m[m.startIndex + 1] == 0xEA, m[m.startIndex + 2] == 0xCA {
            return [m[m.startIndex + 15]]
        }
        if serviceUUIDs16.contains(0xFBB0) { return a.count > 14 ? [a[14]] : nil }
        return nil
    }

    // MARK: CRCs, see RawPecham.kt and RawWicarlink.kt

    static func isValidPechamCRC(_ a: [UInt8], brand: SensorBrand) -> Bool {
        let crc = pechamCRC(Array(a[0..<15]))
        let (hi, lo) = brand == .pecham
            ? (CRCTables.pechamCRCHi, CRCTables.pechamCRCLo)
            : (CRCTables.bekubeeKyCRCHi, CRCTables.bekubeeKyCRCLo)
        return hi[Int(crc >> 8)] == a[15] && lo[Int(crc & 0xFF)] == a[16]
    }

    /// The 2 CRC bytes Pecham puts after the first 15 bytes of its advertisement
    static func pechamCRCBytes(_ first15: [UInt8], brand: SensorBrand = .pecham) -> [UInt8] {
        let crc = pechamCRC(first15)
        let (hi, lo) = brand == .pecham
            ? (CRCTables.pechamCRCHi, CRCTables.pechamCRCLo)
            : (CRCTables.bekubeeKyCRCHi, CRCTables.bekubeeKyCRCLo)
        return [hi[Int(crc >> 8)], lo[Int(crc & 0xFF)]]
    }

    /// CRC-16, polynomial 0x8005, from 0xFFFF, most significant bit first
    private static func pechamCRC(_ data: [UInt8]) -> UInt16 {
        var crc: UInt16 = 0xFFFF
        for byte in data.prefix(15) {
            for bit in 0..<8 {
                let dataBit = (byte >> (7 - bit)) & 1 == 1
                let msb = crc & 0x8000 != 0
                crc <<= 1
                if dataBit != msb { crc ^= 0x8005 }
            }
        }
        return crc
    }

    static func isValidWicarlinkCRC(_ a: [UInt8]) -> Bool {
        let expected = (Int(a[16]) << 8 | Int(a[18])) - Int(Int8(bitPattern: a[17]))
        return Int(wicarlinkCRC(a)) == expected
    }

    /// CRC-16/XMODEM over bytes 9 to 15 and 19 to 25
    static func wicarlinkCRC(_ a: [UInt8]) -> UInt16 {
        var crc: UInt16 = 0
        for byte in a[9..<16] + a[19..<26] {
            crc ^= UInt16(byte) << 8
            for _ in 0..<8 { crc = crc & 0x8000 != 0 ? (crc << 1) ^ 0x1021 : crc << 1 }
        }
        return crc
    }
}
