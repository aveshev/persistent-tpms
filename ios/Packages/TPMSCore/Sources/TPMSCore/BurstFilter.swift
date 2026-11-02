import Foundation

/// A sensor sends the same advertisement several times in a short time: only the first copy goes
/// through, like Android's `distinctWithin(BluetoothLeScanner.BURST)`. The signal strength isn't
/// compared, it changes from one copy to the next.
public struct BurstFilter {
    public static let window: Double = 50

    /// When each peripheral's advertisement was last let through, only those within the window
    private var passed = [String: Double]()

    public init() {}

    /// Whether this advertisement of [peripheral], received at [time] (seconds), is a new one
    public mutating func isNew(peripheral: String, advertisement: Data, at time: Double) -> Bool {
        passed = passed.filter { time - $0.value < Self.window }
        let key = peripheral + advertisement.hex
        guard passed[key] == nil else { return false }
        passed[key] = time
        return true
    }
}
