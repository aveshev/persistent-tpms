import Foundation
import SQLite3

/// The vehicles, their sensors and readings, in the Android app's `car.db` schema (version 12, see
/// data/vehicle's 12.db): a database from one opens in the other. Readings keep the advertisement
/// itself and are decoded when read back, like on Android.
///
/// Used from the main thread only.
public final class Database {
    private var db: OpaquePointer?

    public static let version: Int32 = 12

    /// [path] nil opens an in-memory database, for the tests
    public init(path: String? = nil) throws {
        guard sqlite3_open_v2(path ?? ":memory:", &db, SQLITE_OPEN_READWRITE | SQLITE_OPEN_CREATE | SQLITE_OPEN_FULLMUTEX, nil) == SQLITE_OK else {
            throw DatabaseError(message: "Can't open \(path ?? "memory")")
        }
        try execute("PRAGMA foreign_keys = ON")
        try execute("PRAGMA journal_mode = WAL")
        let version = try scalarInt("PRAGMA user_version")
        // SQLDelight's schema snapshots have the tables but no version
        if version == 0, try scalarInt("SELECT COUNT(*) FROM sqlite_master WHERE name = 'Vehicle'") == 0 {
            try execute(Self.schema)
            try execute("PRAGMA user_version = \(Self.version)")
        } else if version == 0 {
            try execute("PRAGMA user_version = \(Self.version)")
        } else if version > Self.version {
            throw DatabaseError(message: "Can't downgrade database from version \(version) to \(Self.version)")
        }
    }

    deinit { sqlite3_close(db) }

    // MARK: Vehicles

    public func vehicles() throws -> [Vehicle] {
        try query("SELECT uuid, name, kind, isFavourite, lowPressure, highPressure, rearLowPressure, rearHighPressure, separateRearPressure, spareLowPressure, spareHighPressure, separateSparePressure, lowTemp, highTemp, lowBatteryVoltage, lowBatteryPercent FROM Vehicle WHERE isDeleting = 0 ORDER BY rowid") { row in
            Vehicle(
                id: row.uuid(0), name: row.text(1), kind: VehicleKind(rawValue: row.text(2)) ?? .car, isCurrent: row.int(3) == 1,
                lowPressure: Pressure(kpa: row.float(4)), highPressure: Pressure(kpa: row.float(5)),
                rearLowPressure: row.optionalFloat(6).map(Pressure.init(kpa:)), rearHighPressure: row.optionalFloat(7).map(Pressure.init(kpa:)),
                separateRearPressure: row.int(8) == 1,
                spareLowPressure: row.optionalFloat(9).map(Pressure.init(kpa:)), spareHighPressure: row.optionalFloat(10).map(Pressure.init(kpa:)),
                separateSparePressure: row.int(11) == 1,
                lowTemp: Temperature(celsius: row.float(12)), highTemp: Temperature(celsius: row.float(13)),
                lowBatteryVoltage: Voltage(volts: row.float(14)), lowBatteryPercent: row.int(15)
            )
        }
    }

    /// Inserts [vehicle] or updates it, by its ID. The triggers keep exactly one current vehicle.
    public func save(_ vehicle: Vehicle) throws {
        try run(
            """
            INSERT INTO Vehicle (uuid, name, kind, isFavourite, lowPressure, highPressure, rearLowPressure, rearHighPressure, separateRearPressure, spareLowPressure, spareHighPressure, separateSparePressure, lowTemp, highTemp, lowBatteryVoltage, lowBatteryPercent)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(uuid) DO UPDATE SET name = excluded.name, kind = excluded.kind, isFavourite = excluded.isFavourite,
                lowPressure = excluded.lowPressure, highPressure = excluded.highPressure, rearLowPressure = excluded.rearLowPressure,
                rearHighPressure = excluded.rearHighPressure, separateRearPressure = excluded.separateRearPressure,
                spareLowPressure = excluded.spareLowPressure, spareHighPressure = excluded.spareHighPressure,
                separateSparePressure = excluded.separateSparePressure, lowTemp = excluded.lowTemp, highTemp = excluded.highTemp,
                lowBatteryVoltage = excluded.lowBatteryVoltage, lowBatteryPercent = excluded.lowBatteryPercent
            """,
            vehicle.id, vehicle.name, vehicle.kind.rawValue, vehicle.isCurrent,
            vehicle.lowPressure.kpa, vehicle.highPressure.kpa, vehicle.rearLowPressure?.kpa, vehicle.rearHighPressure?.kpa,
            vehicle.separateRearPressure, vehicle.spareLowPressure?.kpa, vehicle.spareHighPressure?.kpa, vehicle.separateSparePressure,
            vehicle.lowTemp.celsius, vehicle.highTemp.celsius, vehicle.lowBatteryVoltage.volts, vehicle.lowBatteryPercent
        )
    }

    public func setCurrent(_ id: UUID) throws {
        try run("UPDATE Vehicle SET isFavourite = 1 WHERE uuid = ?", id)
    }

    /// The last vehicle is never deleted (the no_vehicle_delete trigger)
    public func delete(_ id: UUID) throws {
        try run("DELETE FROM Vehicle WHERE uuid = ?", id)
    }

    // MARK: Sensors

    public func sensors() throws -> [BoundSensor] {
        try query("SELECT id, brand, vehicleId, location, pressureCalibration, pressureOffset, pressureMultiplier FROM Sensor") { row in
            BoundSensor(id: row.int(0), brand: SensorBrand(rawValue: row.int(1)) ?? .pecham, vehicleId: row.uuid(2),
                        location: TyreLocation(code: row.int(3)) ?? .single,
                        calibration: SensorCalibration(
                            isEnabled: row.int(4) != 0,
                            calibration: PressureCalibration(offset: Pressure(kpa: Float(row.double(5))), multiplier: Float(row.double(6)))
                        ))
        }
    }

    /// Binds [sensor] to its tyre, replacing the sensor that was there. A sensor bound elsewhere
    /// moves, its readings staying where they were read.
    public func bind(_ sensor: BoundSensor) throws {
        try transaction {
            try run("DELETE FROM Sensor WHERE vehicleId = ? AND location = ?", sensor.vehicleId, sensor.location.code)
            try run("INSERT OR REPLACE INTO Sensor (id, location, vehicleId, brand) VALUES (?, ?, ?, ?)",
                    sensor.id, sensor.location.code, sensor.vehicleId, sensor.brand.rawValue)
        }
    }

    public func unbind(vehicleId: UUID, location: TyreLocation) throws {
        try run("DELETE FROM Sensor WHERE vehicleId = ? AND location = ?", vehicleId, location.code)
    }

    /// Unbinds every sensor of the vehicle. Their readings stay.
    public func unbindAll(vehicleId: UUID) throws {
        try run("DELETE FROM Sensor WHERE vehicleId = ?", vehicleId)
    }

    /// Gives a shared sensor its calibration, once bound
    public func setCalibration(sensorId: Int, isCalibrated: Bool, offsetKpa: Float, multiplier: Float) throws {
        try run("UPDATE Sensor SET pressureCalibration = ?, pressureOffset = ?, pressureMultiplier = ? WHERE id = ?",
                isCalibrated, offsetKpa, multiplier, sensorId)
    }

    /// A copy of everything for sharing, with what's still in the write-ahead log
    public func export(to path: String) throws {
        try run("VACUUM INTO ?", path)
    }

    /// Moves each sensor of the vehicle along with its readings and its calibration, see MoveChain
    public func move(vehicleId: UUID, moves: [MoveChain.Move]) throws {
        try transaction {
            try place(try moves.map { move in
                Placement(sensor: try row(vehicleId: vehicleId, location: move.from).orThrow("No sensor at \(move.from)"), vehicleId: vehicleId, location: move.to)
            })
        }
    }

    /// Moves the sensor at [location] of the vehicle [vehicleId] to [toLocation] of the vehicle
    /// [toVehicleId], along with its readings and its calibration. The sensor already there, if
    /// any, comes to [location] when [swap], with its own, and is unbound otherwise.
    public func moveToVehicle(vehicleId: UUID, location: TyreLocation, toVehicleId: UUID, toLocation: TyreLocation, swap: Bool) throws {
        try transaction {
            var placements = [Placement(sensor: try row(vehicleId: vehicleId, location: location).orThrow("No sensor at \(location)"), vehicleId: toVehicleId, location: toLocation)]
            if let there = try row(vehicleId: toVehicleId, location: toLocation) {
                if swap {
                    placements.append(Placement(sensor: there, vehicleId: vehicleId, location: location))
                } else {
                    // Replaced, its readings stay with the tyre like those of any sensor unbound
                    try unbind(vehicleId: toVehicleId, location: toLocation)
                }
            }
            try place(placements)
        }
    }

    /// A sensor's whole row, so it keeps its calibration
    private struct SensorRow {
        let id: Int
        let location: Int
        let vehicleId: UUID
        let brand: Int
        let isCalibrated: Int
        let offset: Double
        let multiplier: Double
    }

    private struct Placement {
        let sensor: SensorRow
        let vehicleId: UUID
        let location: TyreLocation
    }

    private func row(vehicleId: UUID, location: TyreLocation) throws -> SensorRow? {
        try query("SELECT id, location, vehicleId, brand, pressureCalibration, pressureOffset, pressureMultiplier FROM Sensor WHERE vehicleId = ? AND location = ?", vehicleId, location.code) { row in
            SensorRow(id: row.int(0), location: row.int(1), vehicleId: row.uuid(2), brand: row.int(3), isCalibrated: row.int(4), offset: row.double(5), multiplier: row.double(6))
        }.first
    }

    /// Within a transaction, puts each sensor where it goes, with its readings. A moved reading is
    /// parked under a negative timestamp until every sensor has left, as it may land on one still
    /// there. A reading still in the way, of a sensor which didn't move, keeps its place and the
    /// parked one is dropped. Like the Android app's SensorDatabase.
    private func place(_ placements: [Placement]) throws {
        // All freed first, a sensor can take a location another one is leaving
        for placement in placements {
            try run("DELETE FROM Sensor WHERE vehicleId = ? AND location = ?", placement.sensor.vehicleId, placement.sensor.location)
        }
        for placement in placements {
            let sensor = placement.sensor
            try run("INSERT INTO Sensor (id, location, vehicleId, brand, pressureCalibration, pressureOffset, pressureMultiplier) VALUES (?, ?, ?, ?, ?, ?, ?)",
                    sensor.id, placement.location.code, placement.vehicleId, sensor.brand, sensor.isCalibrated, sensor.offset, sensor.multiplier)
            try run("UPDATE Reading SET vehicleId = ?, location = ?, timestamp = -timestamp WHERE vehicleId = ? AND location = ? AND sensorId = ? AND timestamp > 0",
                    placement.vehicleId, placement.location.code, sensor.vehicleId, sensor.location, sensor.id)
        }
        for vehicleId in Set(placements.flatMap { [$0.sensor.vehicleId, $0.vehicleId] }) {
            try run("UPDATE OR IGNORE Reading SET timestamp = -timestamp WHERE vehicleId = ? AND timestamp < 0", vehicleId)
            try run("DELETE FROM Reading WHERE vehicleId = ? AND timestamp < 0", vehicleId)
        }
    }

    /// Unbinds the sensors identified by their Bluetooth address, which another phone's IDs for
    /// don't match (see SensorBrand.isIdentifiedByAddress): from an Android app's database. Their
    /// readings stay.
    public func unbindSensorsIdentifiedByAddress() throws {
        let brands = SensorBrand.allCases.filter(\.isIdentifiedByAddress).map { String($0.rawValue) }.joined(separator: ", ")
        try run("DELETE FROM Sensor WHERE brand IN (\(brands))")
    }

    // MARK: Readings

    /// A second reading at the same time for the same tyre is the same reading
    public func insert(_ reading: SensorReading, vehicleId: UUID, location: TyreLocation) throws {
        try run("INSERT OR IGNORE INTO Reading (vehicleId, location, timestamp, sensorId, rssi, raw) VALUES (?, ?, ?, ?, ?, ?)",
                vehicleId, location.code, reading.timestamp, reading.sensorId, reading.rssi, reading.raw)
    }

    /// The tyre's readings since [after], oldest first, decoded again from their advertisements
    public func readings(vehicleId: UUID, location: TyreLocation, after: Double = 0, limit: Int = 1000) throws -> [SensorReading] {
        try query("SELECT timestamp, sensorId, rssi, raw FROM (SELECT * FROM Reading WHERE vehicleId = ? AND location = ? AND timestamp > ? ORDER BY timestamp DESC LIMIT ?) ORDER BY timestamp ASC",
                  vehicleId, location.code, after, limit) { row in
            AdvertisingPacket(bytes: row.blob(3)).decode(timestamp: row.double(0), rssi: row.int(2), addressId: row.int(1))
        }.compactMap { $0 }
    }

    public func latestReading(vehicleId: UUID, location: TyreLocation) throws -> SensorReading? {
        try readings(vehicleId: vehicleId, location: location, limit: 1).last
    }

    // MARK: SQLite

    public struct DatabaseError: LocalizedError, CustomStringConvertible {
        public let message: String
        public var description: String { message }
        public var errorDescription: String? { message }
    }

    private func transaction(_ body: () throws -> Void) throws {
        try execute("BEGIN")
        do {
            try body()
            try execute("COMMIT")
        } catch {
            try? execute("ROLLBACK")
            throw error
        }
    }

    private func execute(_ sql: String) throws {
        guard sqlite3_exec(db, sql, nil, nil, nil) == SQLITE_OK else { throw lastError(sql) }
    }

    private func lastError(_ sql: String) -> DatabaseError {
        DatabaseError(message: "\(String(cString: sqlite3_errmsg(db))) in \(sql.prefix(80))")
    }

    private func scalarInt(_ sql: String) throws -> Int {
        try query(sql) { $0.int(0) }.first ?? 0
    }

    private func run(_ sql: String, _ values: Any?...) throws {
        let statement = try prepare(sql, values)
        defer { sqlite3_finalize(statement) }
        guard sqlite3_step(statement) == SQLITE_DONE else { throw lastError(sql) }
    }

    private func query<T>(_ sql: String, _ values: Any?..., map: (Row) -> T) throws -> [T] {
        let statement = try prepare(sql, values)
        defer { sqlite3_finalize(statement) }
        var results = [T]()
        while true {
            switch sqlite3_step(statement) {
            case SQLITE_ROW: results.append(map(Row(statement: statement)))
            case SQLITE_DONE: return results
            default: throw lastError(sql)
            }
        }
    }

    private static let transient = unsafeBitCast(-1, to: sqlite3_destructor_type.self)

    private func prepare(_ sql: String, _ values: [Any?]) throws -> OpaquePointer? {
        var statement: OpaquePointer?
        guard sqlite3_prepare_v2(db, sql, -1, &statement, nil) == SQLITE_OK else { throw lastError(sql) }
        for (offset, value) in values.enumerated() {
            let index = Int32(offset + 1)
            switch value {
            case nil: sqlite3_bind_null(statement, index)
            case let value as Bool: sqlite3_bind_int(statement, index, value ? 1 : 0)
            case let value as Int: sqlite3_bind_int64(statement, index, Int64(value))
            case let value as Int32: sqlite3_bind_int64(statement, index, Int64(value))
            case let value as Float: sqlite3_bind_double(statement, index, Double(value))
            case let value as Double: sqlite3_bind_double(statement, index, value)
            case let value as String: sqlite3_bind_text(statement, index, value, -1, Self.transient)
            case let value as UUID: bindBlob(statement, index, value.data)
            case let value as Data: bindBlob(statement, index, value)
            default: sqlite3_finalize(statement); throw DatabaseError(message: "Can't bind \(String(describing: value))")
            }
        }
        return statement
    }

    private func bindBlob(_ statement: OpaquePointer?, _ index: Int32, _ data: Data) {
        _ = data.withUnsafeBytes { sqlite3_bind_blob(statement, index, $0.baseAddress, Int32(data.count), Self.transient) }
    }

    struct Row {
        let statement: OpaquePointer?

        func int(_ column: Int32) -> Int { Int(sqlite3_column_int64(statement, column)) }
        func double(_ column: Int32) -> Double { sqlite3_column_double(statement, column) }
        func float(_ column: Int32) -> Float { Float(sqlite3_column_double(statement, column)) }
        func optionalFloat(_ column: Int32) -> Float? {
            sqlite3_column_type(statement, column) == SQLITE_NULL ? nil : float(column)
        }
        func text(_ column: Int32) -> String {
            sqlite3_column_text(statement, column).map { String(cString: $0) } ?? ""
        }
        func blob(_ column: Int32) -> Data {
            guard let bytes = sqlite3_column_blob(statement, column) else { return Data() }
            return Data(bytes: bytes, count: Int(sqlite3_column_bytes(statement, column)))
        }
        func uuid(_ column: Int32) -> UUID { UUID(data: blob(column)) ?? UUID() }
    }

    /// data/vehicle/src/main/sqldelight/12.db, the Android schema this one stays the same as
    static let schema = """
        CREATE TABLE Sensor(
            id INTEGER NOT NULL PRIMARY KEY,
            location INTEGER NOT NULL,
            vehicleId BLOB NOT NULL,
            brand INTEGER NOT NULL,
            pressureCalibration INTEGER NOT NULL DEFAULT 0,
            pressureOffset REAL NOT NULL DEFAULT 0,
            pressureMultiplier REAL NOT NULL DEFAULT 1,
            FOREIGN KEY(vehicleId) REFERENCES Vehicle(uuid) ON DELETE CASCADE,
            UNIQUE(location, vehicleId)
        );
        CREATE TABLE Vehicle(
            uuid BLOB NOT NULL PRIMARY KEY,
            name TEXT NOT NULL,
            isFavourite INTEGER NOT NULL,
            lowPressure REAL NOT NULL DEFAULT 100,
            highPressure REAL NOT NULL DEFAULT 344.75,
            lowTemp REAL NOT NULL DEFAULT 20,
            normalTemp REAL NOT NULL DEFAULT 45,
            highTemp REAL NOT NULL DEFAULT 90,
            kind TEXT NOT NULL DEFAULT "CAR",
            isDeleting INTEGER NOT NULL DEFAULT 0,
            rearLowPressure REAL,
            rearHighPressure REAL,
            separateRearPressure INTEGER NOT NULL DEFAULT 0,
            pressureCalibration INTEGER NOT NULL DEFAULT 0,
            pressureOffset REAL NOT NULL DEFAULT 0,
            pressureMultiplier REAL NOT NULL DEFAULT 1,
            lowBatteryVoltage REAL NOT NULL DEFAULT 2.6,
            lowBatteryPercent INTEGER NOT NULL DEFAULT 10,
            spareLowPressure REAL,
            spareHighPressure REAL,
            separateSparePressure INTEGER NOT NULL DEFAULT 0
        );
        CREATE TABLE Reading(
            vehicleId BLOB NOT NULL,
            location INTEGER NOT NULL,
            timestamp REAL NOT NULL,
            sensorId INTEGER NOT NULL,
            rssi INTEGER NOT NULL,
            raw BLOB NOT NULL,
            PRIMARY KEY (vehicleId, location, timestamp),
            FOREIGN KEY(vehicleId) REFERENCES Vehicle(uuid) ON DELETE CASCADE
        ) WITHOUT ROWID;
        CREATE TRIGGER new_favourite_insert AFTER INSERT ON Vehicle WHEN new.isFavourite = 1
        BEGIN
            UPDATE Vehicle SET isFavourite = 0 WHERE isFavourite = 1 AND uuid != new.uuid;
        END;
        CREATE TRIGGER new_favourite_update AFTER UPDATE OF isFavourite ON Vehicle WHEN new.isFavourite = 1
        BEGIN
            UPDATE Vehicle SET isFavourite = 0 WHERE isFavourite = 1 AND uuid != new.uuid;
        END;
        CREATE TRIGGER no_favourite_update AFTER UPDATE OF isFavourite ON Vehicle
            WHEN (SELECT COUNT(*) FROM Vehicle WHERE isFavourite = 1) = 0
        BEGIN
            UPDATE Vehicle SET isFavourite = 1 WHERE rowid IN (SELECT rowid FROM Vehicle LIMIT 1);
        END;
        CREATE TRIGGER no_vehicle_delete BEFORE DELETE ON Vehicle WHEN (SELECT COUNT(*) FROM Vehicle) = 1
        BEGIN
            SELECT RAISE(IGNORE);
        END;
        CREATE TRIGGER no_favourite_delete AFTER DELETE ON Vehicle WHEN old.isFavourite = 1
        BEGIN
            UPDATE Vehicle SET isFavourite = 1 WHERE rowid IN (SELECT rowid FROM Vehicle LIMIT 1);
        END;
        CREATE TRIGGER no_update_when_deleting BEFORE UPDATE ON Vehicle WHEN old.isDeleting = 1
        BEGIN
            SELECT RAISE(FAIL, 'Cannot update a vehicule which is currently deleting');
        END;
        """
}

extension Optional {
    fileprivate func orThrow(_ message: String) throws -> Wrapped {
        guard let self else { throw Database.DatabaseError(message: message) }
        return self
    }
}

extension UUID {
    /// Its 16 bytes, as the database stores it
    var data: Data { withUnsafeBytes(of: uuid) { Data($0) } }

    init?(data: Data) {
        guard data.count == 16 else { return nil }
        let b = [UInt8](data)
        self.init(uuid: (b[0], b[1], b[2], b[3], b[4], b[5], b[6], b[7], b[8], b[9], b[10], b[11], b[12], b[13], b[14], b[15]))
    }
}
