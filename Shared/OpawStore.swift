import Foundation
import SwiftData
import OpawKit

enum OpawWidgetKind {
    static let quickLog = "QuickLogWidget"
    static let careScore = "CareScoreWidget"
}

/// A small copy of the active pet's sticker, shared with the widgets and Live Activity.
enum PetSticker {
    private static var url: URL? {
        FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: OpawDefaults.appGroupID)?
            .appendingPathComponent("pet-sticker.png")
    }

    static func save(_ data: Data?) {
        guard let url else { return }
        if let data { try? data.write(to: url, options: .atomic) } else { try? FileManager.default.removeItem(at: url) }
    }

    static func load() -> Data? {
        url.flatMap { try? Data(contentsOf: $0) }
    }
}

/// Version 1 of the on-device database. Add a new version and a migration stage
/// to `OpawMigrationPlan` whenever a model changes after release.
enum OpawSchemaV1: VersionedSchema {
    static var versionIdentifier = Schema.Version(1, 0, 0)

    static var models: [any PersistentModel.Type] {
        [Dog.self, CareEvent.self, Walk.self, Memory.self, Reminder.self]
    }
}

enum OpawMigrationPlan: SchemaMigrationPlan {
    static var schemas: [any VersionedSchema.Type] { [OpawSchemaV1.self] }
    static var stages: [MigrationStage] { [] }
}

/// Settings shared between the app and its extensions.
enum OpawDefaults {
    static let appGroupID = "group.app.opaw.shared"
    static let activeDogKey = "activeDogID"
    /// The name shown when this phone logs care, such as "Sam".
    static let caretakerKey = "caretakerName"

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

    static let schema = Schema(versionedSchema: OpawSchemaV1.self)

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
        if let container = try? ModelContainer(for: schema, migrationPlan: OpawMigrationPlan.self, configurations: [groupConfig]) {
            return container
        }
        // Fall back to the app's own container, for example when App Groups aren't provisioned yet.
        let localConfig = ModelConfiguration("Opaw", schema: schema)
        do {
            return try ModelContainer(for: schema, migrationPlan: OpawMigrationPlan.self, configurations: [localConfig])
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

    /// The active dog's care score, for the Lock Screen widget.
    static func todayScore(in context: ModelContext? = nil) -> (dogName: String, score: Int)? {
        let context = context ?? OpawStore.container.mainContext
        guard let dog = OpawStore.activeDog(in: context) else { return nil }
        return (dog.name, CareScore.report(for: dog.careSnapshot()).score)
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

extension Dog {
    /// What the mood engine and care score read: last log per kind, today's activity, meal times.
    func careSnapshot(now: Date = Date()) -> CareSnapshot {
        var last: [CareKind: Date] = [:]
        for event in careEvents ?? [] {
            if let existing = last[event.kind], existing >= event.date { continue }
            last[event.kind] = event.date
        }
        let startOfDay = Calendar.current.startOfDay(for: now)
        let walkMinutes = (walks ?? [])
            .filter { $0.startedAt >= startOfDay }
            .reduce(0) { $0 + $1.movingSeconds / 60 }
        let playMinutes = Double((careEvents ?? []).filter { $0.kind == .play && $0.date >= startOfDay }.count * 15)
        return CareSnapshot(
            lastEvent: last,
            activityMinutesToday: walkMinutes + playMinutes,
            dailyActivityGoalMinutes: Double(dailyGoalMinutes),
            lastBadgeEarnedAt: lastBadgeEarnedAt,
            mealHours: mealHours
        )
    }
}
