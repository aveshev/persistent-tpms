import AppIntents

/// Runs from an automation of the Shortcuts app, the only thing that can see a classic Bluetooth
/// device (a car's audio, many headsets) connecting. The Shortcuts app also launches the app for it
/// after being swiped away, when Bluetooth no longer can, so this restarts the scan too.
struct GoingForARideIntent: AppIntent {
    static let title: LocalizedStringResource = "Going for a ride"
    static let description = IntentDescription("Sends a silent notification inviting you to open Persistent TPMS, and starts listening to the sensors again.")
    static let openAppWhenRun = false

    @MainActor
    func perform() async throws -> some IntentResult {
        await Monitor.shared.goingForARide()
        return .result()
    }
}

/// Lists the action in the Shortcuts app without importing anything
struct PersistentTPMSShortcuts: AppShortcutsProvider {
    static var appShortcuts: [AppShortcut] {
        AppShortcut(
            intent: GoingForARideIntent(),
            phrases: ["Going for a ride with \(.applicationName)"],
            shortTitle: "Going for a ride",
            systemImageName: "bell"
        )
    }
}
