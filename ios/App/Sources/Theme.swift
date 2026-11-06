import SwiftUI
import TPMSCore
import UIKit

/// The Android app's green and blue for the tyres (core/ui's Color.kt), iOS's own colours for the
/// rest: the system background, label and alert colours
enum Theme {
    static let primary = Color(dynamic(light: 0x0E6E14, dark: 0x81DB74))
    static let tertiary = Color(dynamic(light: 0x386569, dark: 0xA0CFD3))
    static let error = Color(.systemRed)
    static let background = Color(.systemBackground)
    static let onBackground = Color(.label)
    static let orange = Color(.systemOrange)

    private static let primaryUI = dynamic(light: 0x0E6E14, dark: 0x81DB74)
    private static let tertiaryUI = dynamic(light: 0x386569, dark: 0xA0CFD3)
    private static let errorUI = UIColor.systemRed

    /// A tyre's fill at [temperature], like TyreIconStateFlow: blue while cold, turning green as it
    /// warms up, green while fine, turning red up to the hot temperature
    static func tyre(_ temperature: Temperature, low: Temperature, high: Temperature) -> Color {
        let t = CGFloat(temperature.celsius), lo = CGFloat(low.celsius), hi = CGFloat(high.celsius)
        let half = max(0, (hi - lo) / 2)
        let fineStart = lo + min(10, half), fineEnd = hi - min(10, half)
        return switch t {
        case ..<fineStart: Color(mix(tertiaryUI, primaryUI, fineStart > lo ? (t - lo) / (fineStart - lo) : 1))
        case ...fineEnd: primary
        default: Color(mix(primaryUI, errorUI, hi > fineEnd ? (t - fineEnd) / (hi - fineEnd) : 1))
        }
    }

    private static func rgb(_ hex: UInt32) -> UIColor {
        UIColor(red: CGFloat(hex >> 16 & 0xFF) / 255, green: CGFloat(hex >> 8 & 0xFF) / 255, blue: CGFloat(hex & 0xFF) / 255, alpha: 1)
    }

    private static func dynamic(light: UInt32, dark: UInt32) -> UIColor {
        UIColor { $0.userInterfaceStyle == .dark ? rgb(dark) : rgb(light) }
    }

    private static func mix(_ a: UIColor, _ b: UIColor, _ fraction: CGFloat) -> UIColor {
        UIColor { traits in
            var (r1, g1, b1, a1, r2, g2, b2, a2) = (CGFloat(0), CGFloat(0), CGFloat(0), CGFloat(0), CGFloat(0), CGFloat(0), CGFloat(0), CGFloat(0))
            a.resolvedColor(with: traits).getRed(&r1, green: &g1, blue: &b1, alpha: &a1)
            b.resolvedColor(with: traits).getRed(&r2, green: &g2, blue: &b2, alpha: &a2)
            let f = min(max(fraction, 0), 1)
            return UIColor(red: r1 + (r2 - r1) * f, green: g1 + (g2 - g1) * f, blue: b1 + (b2 - b1) * f, alpha: 1)
        }
    }
}

extension AlertLevel {
    var color: Color {
        switch self {
        case .amber: Theme.orange
        case .red, .crimson: Theme.error
        }
    }
}

extension TyreLocation {
    /// "front left wheel", "spare wheel", a monowheel's only "wheel", like Android's appendLoc
    var phrase: String {
        self == .single ? "wheel" : shortPhrase + " wheel"
    }

    /// "front left", without saying it's a wheel
    var shortPhrase: String { name.lowercased() }
}

extension Int {
    /// The sensor's ID as the Android app shows it, see TyreStat.kt's asSensorId
    func asSensorId(_ brand: SensorBrand) -> String {
        let value = UInt32(truncatingIfNeeded: self)
        return if value >> 24 != 0 {
            String(format: "0x%08X", value)
        } else if brand == .wicarlink {
            String(format: "%06X", value)
        } else {
            String(format: "%02X%02X%02X", value & 0xFF, value >> 8 & 0xFF, value >> 16 & 0xFF)
        }
    }
}
