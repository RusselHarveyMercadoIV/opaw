import AppIntents
import WidgetKit
import OpawKit

/// Mirror of `CareKind` for Siri and Shortcuts.
///
/// App Intents reads enum cases at build time and only supports enums declared in the same module,
/// so this can't be a conformance on `CareKind` itself (which lives in OpawKit).
enum CareKindOption: String, AppEnum {
    case meal
    case water
    case pee
    case poop
    case bath
    case brush
    case nails
    case meds
    case vet
    case weight
    case play

    static var typeDisplayRepresentation: TypeDisplayRepresentation = "Care type"

    static var caseDisplayRepresentations: [CareKindOption: DisplayRepresentation] = [
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

    init(_ kind: CareKind) {
        self = CareKindOption(rawValue: kind.rawValue) ?? .meal
    }

    var kind: CareKind {
        CareKind(rawValue: rawValue) ?? .meal
    }
}

/// Logs a care event. Used by the Home Screen widget buttons and by Siri.
struct LogCareIntent: AppIntent {
    static var title: LocalizedStringResource = "Log dog care"
    static var description = IntentDescription("Log a meal, potty break, bath and more for your dog.")

    @Parameter(title: "Type")
    var kind: CareKindOption

    static var parameterSummary: some ParameterSummary {
        Summary("Log \(\.$kind) for my dog")
    }

    init() {}

    init(kind: CareKind) {
        self.kind = CareKindOption(kind)
    }

    @MainActor
    func perform() async throws -> some IntentResult & ProvidesDialog {
        let name = try CareLogger.log(kind.kind)
        WidgetCenter.shared.reloadTimelines(ofKind: OpawWidgetKind.quickLog)
        return .result(dialog: "Logged \(kind.kind.title.lowercased()) for \(name).")
    }
}
