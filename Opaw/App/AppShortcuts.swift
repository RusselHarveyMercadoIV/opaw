import AppIntents

/// Siri and Spotlight phrases, e.g. "Log poop in Opaw".
struct OpawShortcuts: AppShortcutsProvider {
    static var appShortcuts: [AppShortcut] {
        AppShortcut(
            intent: LogCareIntent(),
            phrases: [
                "Log \(\.$kind) in \(.applicationName)",
                "Log my dog's \(\.$kind) in \(.applicationName)",
                "Track \(\.$kind) with \(.applicationName)"
            ],
            shortTitle: "Log care",
            systemImageName: "pawprint.fill"
        )
    }
}
