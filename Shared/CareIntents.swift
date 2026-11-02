import AppIntents
import WidgetKit
import OpawKit

// App Intents needs these as literals so Siri and Shortcuts can read them at build time.
extension CareKind: AppEnum {
    public static var typeDisplayRepresentation: TypeDisplayRepresentation = "Care type"

    public static var caseDisplayRepresentations: [CareKind: DisplayRepresentation] = [
        .meal: DisplayRepresentation(title: "Meal", image: .init(systemName: "fork.knife")),
        .water: DisplayRepresentation(title: "Water", image: .init(systemName: "drop.fill")),
        .pee: DisplayRepresentation(title: "Pee", image: .init(systemName: "drop.triangle")),
        .poop: DisplayRepresentation(title: "Poop", image: .init(systemName: "leaf.fill")),
        .bath: DisplayRepresentation(title: "Bath", image: .init(systemName: "bathtub.fill")),
        .brush: DisplayRepresentation(title: "Brush", image: .init(systemName: "comb.fill")),
        .nails: DisplayRepresentation(title: "Nails", image: .init(systemName: "scissors")),
        .meds: DisplayRepresentation(title: "Meds", image: .init(systemName: "pills.fill")),
        .vet: DisplayRepresentation(title: "Vet visit", image: .init(systemName: "cross.case.fill")),
        .weight: DisplayRepresentation(title: "Weight", image: .init(systemName: "scalemass.fill")),
        .play: DisplayRepresentation(title: "Play", image: .init(systemName: "tennisball.fill")),
    ]
}

/// Logs a care event. Used by the Home Screen widget buttons and by Siri.
struct LogCareIntent: AppIntent {
    static var title: LocalizedStringResource = "Log dog care"
    static var description = IntentDescription("Log a meal, potty break, bath and more for your dog.")

    @Parameter(title: "Type")
    var kind: CareKind

    init() {}

    init(kind: CareKind) {
        self.kind = kind
    }

    @MainActor
    func perform() async throws -> some IntentResult & ProvidesDialog {
        let name = try CareLogger.log(kind)
        WidgetCenter.shared.reloadAllTimelines()
        return .result(dialog: "Logged \(kind.title.lowercased()) for \(name).")
    }
}
