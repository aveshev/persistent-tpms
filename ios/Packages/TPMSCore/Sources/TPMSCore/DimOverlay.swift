import Foundation

/// The screen is dimmed by black over its content, leaving the brightness to iOS. iOS blends in the display's
/// gamma, so dividing the light by a number takes less black than that number suggests.
public enum DimOverlay {
    private static let gamma = 2.2

    /// How opaque the black is to divide the light the content gives by [divisor]
    public static func alpha(divisor: Int) -> Double {
        1 - pow(1 / Double(max(divisor, 1)), 1 / gamma)
    }
}
