import Foundation

public extension Data {
    /// Upper case, like the Android app logs advertisements
    var hex: String { map { String(format: "%02X", $0) }.joined() }

    /// nil when [hex] isn't an even number of hexadecimal digits
    init?(hex: String) {
        guard hex.count % 2 == 0 else { return nil }
        var bytes = [UInt8]()
        bytes.reserveCapacity(hex.count / 2)
        var index = hex.startIndex
        while index < hex.endIndex {
            let next = hex.index(index, offsetBy: 2)
            guard let byte = UInt8(hex[index..<next], radix: 16) else { return nil }
            bytes.append(byte)
            index = next
        }
        self.init(bytes)
    }
}

public extension String {
    /// Java's `String.hashCode()`: the Android app gives the sensors identified by their Bluetooth
    /// address this hash of it as their ID, the iOS app the same hash of the peripheral's identifier
    var javaHashCode: Int32 {
        utf16.reduce(Int32(0)) { hash, unit in hash &* 31 &+ Int32(unit) }
    }
}
