import Foundation
import XCTest
@testable import TPMSCore

/// The real packets of the Android decoders' tests (data/vehicle/src/test), decoded to the same values
final class DecoderTests: XCTestCase {

    private func decode(_ hex: String) -> SensorReading? {
        AdvertisingPacket(bytes: Data(hex: hex)!).decode(addressId: 1)
    }

    func testPecham() throws {
        let samples = [
            "0303A5270308425208FF101B0F026AB017",
            "0303A5270308425208FF801C12022B5593",
            "0303A5270308425208FF101D10022F4C32",
            "0303A5270308425208FF801B10020F940F",
            "0303A5270308425208FF801B1301F48D18",
            "0303A5270308425208FF401C1400921A7F",
            "0303A5270308425208FF241D1401D4BFEC",
            "0303a5270308425208ff28201a01032fec",
        ]
        let readings = samples.compactMap(decode)
        XCTAssertEqual(readings.count, samples.count)
        let first = try XCTUnwrap(readings.first)
        XCTAssertEqual(first.brand, .pecham)
        XCTAssertEqual(first.pressure.kpa, 326.13348, accuracy: 0.01)
        XCTAssertEqual(first.temperature.celsius, 15)
        XCTAssertEqual(try XCTUnwrap(first.batteryVoltage).volts, 2.7, accuracy: 0.001)
        XCTAssertFalse(first.isAlarm)
        XCTAssertEqual(readings[5].pressure.kpa, 0.6895, accuracy: 0.001)
    }

    func testPechamWithABadCRCIsntDecoded() {
        XCTAssertNil(decode("0303A5270308425208FF101B0F026AB018"))
    }

    func testWicarlink() throws {
        let reading = try XCTUnwrap(decode("0201060303B0FB12FFAC00D043520008DA001D10FF1100AF2206"))
        XCTAssertEqual(reading.brand, .wicarlink)
        XCTAssertEqual(reading.sensorId, 402095)
        XCTAssertFalse(reading.isAlarm)
        XCTAssertEqual(try XCTUnwrap(reading.batteryVoltage).volts, 3.3, accuracy: 0.005)
        XCTAssertEqual(reading.temperature.celsius, 27)
    }

    func testWicarlinkPressureIsItsStepOf0_1Bar() throws {
        let samples: [(String, Float)] = [
            ("0201060303B0FB12FFAC00C22D51000DC2001C10FF1100C52122", 140),
            ("0201060303B0FB12FFAC00C73056000DAE00FA10FF1100C52122", 150),
            ("0201060303B0FB12FFAC00C63355000D6400BA10FF1100C52122", 160),
            ("0201060303B0FB12FFAC00C35350010D9A00D710FF1100C42122", 260),
            ("0201060303B0FB12FFAC00C36353000D3F007C10FF1100C42122", 310),
            ("0201060303B0FB12FFAC00C47350010D3800E610FF1100C42122", 360),
            ("0201060303B0FB12FFAC00C48657020DB2006E10FF1100C42122", 420),
            ("0201060303B0FB12FFAC00C68F54000DB900EB10FF1100C42122", 450),
            ("0201060303B0FB12FFAC00C6A253020DE2008C10FF1100C42122", 510),
            ("0201060303B0FB12FFAC00C7BC52000D7900AC10FF1100C52122", 590),
        ]
        for (hex, kpa) in samples {
            XCTAssertEqual(try XCTUnwrap(decode(hex), hex).pressure.kpa, kpa, hex)
        }
    }

    func testWicarlinkPressureDropIsAnAlarm() throws {
        XCTAssertFalse(try XCTUnwrap(decode("0201060303B0FB12FFAC00C78952000D3800D510FF1100C42122")).isAlarm)
        XCTAssertTrue(try XCTUnwrap(decode("0201060303B0FB12FFAC00C67C53010D0400F010FF1100C42122")).isAlarm)
    }

    func testBekubeeTpmsWhateverItsCompanyId() throws {
        let mounted = try XCTUnwrap(decode("050854504D530CFF0200AF4500A7002D56A8B4030327A8"))
        XCTAssertEqual(mounted.brand, .bekubeeTpms)
        XCTAssertEqual(mounted.sensorId, 5647616)
        XCTAssertEqual(mounted.pressure.kpa, 67)
        XCTAssertEqual(mounted.temperature.celsius, 19)
        let unplugged = try XCTUnwrap(decode("050854504D530CFF0600B34A0064002FA0057D030327A8"))
        XCTAssertEqual(unplugged.sensorId, 10497792)
        XCTAssertEqual(unplugged.pressure.kpa, 0)
    }

    func testSysgration() throws {
        let reading = try XCTUnwrap(decode("0201060303B0FB" + "13FF0100" + "80EACA123456" + "E0930400" + "D0070000" + "5A" + "01"))
        XCTAssertEqual(reading.brand, .sysgration)
        XCTAssertEqual(reading.location, .frontLeft)
        XCTAssertEqual(reading.sensorId, 0x56341200)
        XCTAssertEqual(reading.pressure.kpa, 300)
        XCTAssertEqual(reading.temperature.celsius, 20)
        XCTAssertEqual(reading.batteryPercent, 90)
        XCTAssertTrue(reading.isAlarm)
    }

    func testStatusBytes() {
        func status(_ hex: String) -> [UInt8]? { AdvertisingPacket(bytes: Data(hex: hex)!).statusBytes }
        XCTAssertEqual(status("0303A5270308425208FF801B1B02A584EA"), [0x80])
        XCTAssertEqual(status("0303A52703084B5908FF801B1B02A584EA"), [0x80])
        XCTAssertEqual(status("0201060303B0FB12FFAC00C70053010D4F00DE10FF1100C42122"), [0x01])
        XCTAssertEqual(status("050854504D530CFF0600B34A0064002FA0057D030327A8"), [0x06])
        XCTAssertEqual(status("0201060303B0FB13FF010080EACA123456E0930400D0070000" + "5A01"), [0x01])
        XCTAssertNil(status("0201060303AAAA"))
    }

    /// What iOS reported for the NMax's sensors on 2026-10-31, laid out again into the bytes the
    /// S9 stored for the same broadcasts
    func testIOSAdvertisementsAreLaidOutLikeAndroidStoresThem() {
        let pecham = AdvertisingPacket(name: "BR", serviceUUIDs: ["27A5"], manufacturerData: Data(hex: "101E1D01CBF596")!)
        XCTAssertEqual(pecham.bytes.hex, "0303A5270308425208FF101E1D01CBF596")
        XCTAssertEqual(pecham.decode()?.brand, .pecham)

        let wicarlink = AdvertisingPacket(name: nil, serviceUUIDs: ["FBB0"], manufacturerData: Data(hex: "AC00C03652000DFA007010FF1100C52122")!)
        XCTAssertEqual(wicarlink.bytes.hex, "0201060303B0FB12FFAC00C03652000DFA007010FF1100C52122")
        XCTAssertEqual(wicarlink.decode()?.sensorId, 0x2221C5)

        let bekubee = AdvertisingPacket(name: "TPMS", serviceUUIDs: ["A827"], manufacturerData: Data(hex: "0200AF4500A7002D56A8B4")!)
        XCTAssertEqual(bekubee.bytes.hex, "050854504D530CFF0200AF4500A7002D56A8B4030327A8")
    }

    func testJavaHashCode() {
        // Java: "AA:BB:CC:DD:EE:01".hashCode()
        XCTAssertEqual("AA:BB:CC:DD:EE:01".javaHashCode, 512_212_955)
        XCTAssertEqual("".javaHashCode, 0)
    }
}
