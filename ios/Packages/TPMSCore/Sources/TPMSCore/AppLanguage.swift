import Foundation

/// The language the app's texts are shown in, chosen on the debug page. `system`, the default, follows the phone, and is what turning the debug options off goes back to.
public enum AppLanguage: String, CaseIterable, Identifiable, Sendable {
    case system
    case english
    case russian

    private static let key = "language"

    public var id: Self { self }

    /// Read every time rather than kept: a change applies to the next text, also in the background
    public static var current: AppLanguage {
        get { UserDefaults.standard.string(forKey: key).flatMap(AppLanguage.init(rawValue:)) ?? .system }
        set {
            UserDefaults.standard.set(newValue.rawValue, forKey: key)
            // What the system itself reads at launch: its permission prompts, and the app's language
            // in the Settings app, which also comes back here
            UserDefaults.standard.set(newValue.code.map { [$0] }, forKey: "AppleLanguages")
        }
    }

    private var code: String? {
        switch self {
        case .system: nil
        case .english: "en"
        case .russian: "ru"
        }
    }

    public var locale: Locale { code.map(Locale.init(identifier:)) ?? .autoupdatingCurrent }

    fileprivate var bundle: Bundle {
        code
            .flatMap { Bundle.main.path(forResource: $0, ofType: "lproj") }
            .flatMap(Bundle.init(path:))
            ?? .main
    }
}

/// A text in the chosen language, for what isn't a literal given to a SwiftUI view: SwiftUI looks
/// those up by itself, in the locale the app's root view sets. The same English keys, in
/// Localizable.strings.
public func tr(_ key: String.LocalizationValue) -> String {
    String(localized: key, bundle: AppLanguage.current.bundle)
}
