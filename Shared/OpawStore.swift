import Foundation
import SwiftData
import OpawKit

/// Settings shared between the app and its extensions.
enum OpawDefaults {
    static let appGroupID = "group.app.opaw.shared"
    static let activeDogKey = "activeDogID"

    static var shared: UserDefaults {
        UserDefaults(suiteName: appGroupID) ?? .standard
    }
}

/// The shared SwiftData store, used by the app, the widgets and Siri.
///
/// It lives in the App Group container so the widget extension can read and write it.
@MainActor
enum OpawStore {
    static let appGroupID = OpawDefaults.appGroupID

    static let schema = Schema([Dog.self, CareEvent.self, Walk.self, Memory.self, Reminder.self])

    static let container: ModelContainer = makeContainer()

    /// The dog currently shown on Home and targeted by quick logs.
    static var activeDogID: UUID? {
        get { OpawDefaults.shared.string(forKey: OpawDefaults.activeDogKey).flatMap(UUID.init(uuidString:)) }
        set { OpawDefaults.shared.set(newValue?.uuidString ?? "", forKey: OpawDefaults.activeDogKey) }
    }

    private static func makeContainer() -> ModelContainer {
        let groupConfig = ModelConfiguration(
            "Opaw",
            schema: schema,
            groupContainer: .identifier(appGroupID)
        )
        if let container = try? ModelContainer(for: schema, configurations: [groupConfig]) {
            return container
        }
        // Fall back to the app's own container, for example when App Groups aren't provisioned yet.
        let localConfig = ModelConfiguration("Opaw", schema: schema)
        do {
            return try ModelContainer(for: schema, configurations: [localConfig])
        } catch {
            fatalError("Could not open the Opaw database: \(error)")
        }
    }

    /// The active dog, or the first dog if none is marked active.
    static func activeDog(in context: ModelContext) -> Dog? {
        let dogs = (try? context.fetch(FetchDescriptor<Dog>(sortBy: [SortDescriptor(\.createdAt)]))) ?? []
        if let id = activeDogID, let dog = dogs.first(where: { $0.id == id }) {
            return dog
        }
        return dogs.first
    }
}

enum CareLoggerError: LocalizedError {
    case noDog

    var errorDescription: String? {
        switch self {
        case .noDog: return "Add your dog in Opaw first."
        }
    }
}

@MainActor
enum CareLogger {
    /// Logs a care event for the active dog and returns the dog's name.
    @discardableResult
    static func log(_ kind: CareKind, date: Date = Date(), note: String = "", in context: ModelContext? = nil) throws -> String {
        let context = context ?? OpawStore.container.mainContext
        guard let dog = OpawStore.activeDog(in: context) else { throw CareLoggerError.noDog }
        let event = CareEvent(kind: kind, date: date, note: note, dog: dog)
        context.insert(event)
        try context.save()
        return dog.name
    }

    /// Today's count per kind for the active dog, used by the widget.
    static func todayCounts(in context: ModelContext? = nil) -> (dogName: String?, counts: [CareKind: Int]) {
        let context = context ?? OpawStore.container.mainContext
        guard let dog = OpawStore.activeDog(in: context) else { return (nil, [:]) }
        let start = Calendar.current.startOfDay(for: Date())
        var counts: [CareKind: Int] = [:]
        for event in dog.careEvents ?? [] where event.date >= start {
            counts[event.kind, default: 0] += 1
        }
        return (dog.name, counts)
    }
}
