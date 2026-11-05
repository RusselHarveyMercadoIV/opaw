import Foundation
import SwiftData
import OpawKit

enum AvatarStatus: String, Codable {
    /// Only the on-device "living photo" is available.
    case livingPhoto
    /// Motion clips were requested and are being generated.
    case generating
    /// Motion clips are downloaded and ready.
    case ready
    case failed
}

@Model
final class Dog {
    var id: UUID = UUID()
    var name: String = ""
    var breed: String = ""
    var birthDate: Date?
    var weightKg: Double?
    var sizeRaw: String = DogSize.medium.rawValue
    var energyRaw: String = EnergyLevel.medium.rawValue
    /// Usual meal times as hours of the day, for the mood engine.
    var mealHours: [Int] = [8, 18]
    /// Usual meal times as minutes after midnight, such as 450 for 07:30. Empty on dogs saved before minutes were kept.
    var mealMinutesRaw: [Int] = []
    var dailyGoalMinutes: Int = 60
    @Attribute(.externalStorage) var photoData: Data?
    @Attribute(.externalStorage) var cutoutData: Data?
    var avatarStatusRaw: String = AvatarStatus.livingPhoto.rawValue
    var avatarJobID: String?
    var createdAt: Date = Date()
    var lastBadgeEarnedAt: Date?
    var earnedBadgesRaw: [String] = []
    /// The dog's family tree (parents, siblings), stored as JSON.
    var relativesData: Data?
    /// "dog", "cat" or "bunny".
    var species: String = "dog"
    /// "female", "male", or empty when not set.
    var sex: String = ""
    var isNeutered: Bool = false
    /// Care kinds shown on the Care screen, picked during onboarding. Empty means all of them.
    var trackedKindsRaw: [String] = []

    @Relationship(deleteRule: .cascade, inverse: \CareEvent.dog) var careEvents: [CareEvent]? = []
    @Relationship(deleteRule: .cascade, inverse: \Walk.dog) var walks: [Walk]? = []
    @Relationship(deleteRule: .cascade, inverse: \Memory.dog) var memories: [Memory]? = []
    @Relationship(deleteRule: .cascade, inverse: \Reminder.dog) var reminders: [Reminder]? = []

    init(name: String, breed: String = "", birthDate: Date? = nil, weightKg: Double? = nil, size: DogSize = .medium, energy: EnergyLevel = .medium) {
        self.id = UUID()
        self.name = name
        self.breed = breed
        self.birthDate = birthDate
        self.weightKg = weightKg
        self.sizeRaw = size.rawValue
        self.energyRaw = energy.rawValue
        self.createdAt = Date()
        self.dailyGoalMinutes = DogTraits(size: size, energy: energy, ageYears: Dog.age(from: birthDate)).recommendedDailyActivityMinutes
    }

    /// Meal times as minutes after midnight, sorted. Setting them also updates `mealHours`.
    var mealTimes: [Int] {
        get { mealMinutesRaw.isEmpty ? mealHours.sorted().map { $0 * 60 } : mealMinutesRaw.sorted() }
        set {
            mealMinutesRaw = newValue.sorted()
            mealHours = mealMinutesRaw.map { $0 / 60 }
        }
    }

    /// Whether a care kind (or the "walks" key) is tracked; an empty list tracks everything.
    func tracks(_ key: String) -> Bool {
        trackedKindsRaw.isEmpty || trackedKindsRaw.contains(key)
    }

    var size: DogSize {
        get { DogSize(rawValue: sizeRaw) ?? .medium }
        set { sizeRaw = newValue.rawValue }
    }

    var energy: EnergyLevel {
        get { EnergyLevel(rawValue: energyRaw) ?? .medium }
        set { energyRaw = newValue.rawValue }
    }

    var avatarStatus: AvatarStatus {
        get { AvatarStatus(rawValue: avatarStatusRaw) ?? .livingPhoto }
        set { avatarStatusRaw = newValue.rawValue }
    }

    var earnedBadges: Set<Badge> {
        get { Set(earnedBadgesRaw.compactMap(Badge.init(rawValue:))) }
        set { earnedBadgesRaw = newValue.map(\.rawValue).sorted() }
    }

    var relatives: [Relative] {
        get { relativesData.flatMap { try? JSONDecoder().decode([Relative].self, from: $0) } ?? [] }
        set { relativesData = try? JSONEncoder().encode(newValue) }
    }

    var ageYears: Double { Dog.age(from: birthDate) }

    var traits: DogTraits { DogTraits(size: size, energy: energy, ageYears: ageYears) }

    static func age(from birthDate: Date?) -> Double {
        guard let birthDate else { return 3 }
        return max(0, Date().timeIntervalSince(birthDate) / (365.25 * 86_400))
    }
}

@Model
final class CareEvent {
    var id: UUID = UUID()
    var kindRaw: String = CareKind.meal.rawValue
    var date: Date = Date()
    var note: String = ""
    var poopConsistencyRaw: String?
    var poopColorRaw: String?
    var weightKg: Double?
    /// Who logged it, such as "You" or a walker's name.
    var loggedBy: String = ""
    var dog: Dog?

    init(kind: CareKind, date: Date = Date(), note: String = "", dog: Dog?) {
        self.id = UUID()
        self.kindRaw = kind.rawValue
        self.date = date
        self.note = note
        self.loggedBy = OpawDefaults.shared.string(forKey: OpawDefaults.caretakerKey) ?? ""
        self.dog = dog
    }

    var kind: CareKind { CareKind(rawValue: kindRaw) ?? .meal }

    var poopConsistency: PoopConsistency? {
        get { poopConsistencyRaw.flatMap(PoopConsistency.init(rawValue:)) }
        set { poopConsistencyRaw = newValue?.rawValue }
    }

    var poopColor: PoopColor? {
        get { poopColorRaw.flatMap(PoopColor.init(rawValue:)) }
        set { poopColorRaw = newValue?.rawValue }
    }
}

/// A pee or poop dropped on the map during a walk.
struct WalkPin: Codable, Hashable, Identifiable {
    var id = UUID()
    var kind: String
    var latitude: Double
    var longitude: Double
    var date: Date
}

@Model
final class Walk {
    var id: UUID = UUID()
    var startedAt: Date = Date()
    var endedAt: Date = Date()
    var distanceMeters: Double = 0
    var movingSeconds: Double = 0
    @Attribute(.externalStorage) var routeData: Data?
    var pinsData: Data?
    var note: String = ""
    var sharedToPack: Bool = false
    /// "walk", "run" or "hike".
    var activityRaw: String = "walk"
    var isFavorite: Bool = false
    var dog: Dog?

    init(startedAt: Date, endedAt: Date, route: [GeoPoint], pins: [WalkPin], dog: Dog?) {
        self.id = UUID()
        self.startedAt = startedAt
        self.endedAt = endedAt
        self.dog = dog
        let stats = WalkCalculator.stats(for: route)
        self.distanceMeters = stats.distanceMeters
        self.movingSeconds = stats.movingSeconds
        self.routeData = try? JSONEncoder().encode(route)
        self.pinsData = try? JSONEncoder().encode(pins)
    }

    var route: [GeoPoint] {
        guard let routeData else { return [] }
        return (try? JSONDecoder().decode([GeoPoint].self, from: routeData)) ?? []
    }

    var pins: [WalkPin] {
        guard let pinsData else { return [] }
        return (try? JSONDecoder().decode([WalkPin].self, from: pinsData)) ?? []
    }

    var elapsedSeconds: Double { endedAt.timeIntervalSince(startedAt) }

    var stats: WalkStats {
        WalkStats(distanceMeters: distanceMeters, movingSeconds: movingSeconds, elapsedSeconds: elapsedSeconds)
    }
}

@Model
final class Memory {
    var id: UUID = UUID()
    var date: Date = Date()
    var caption: String = ""
    @Attribute(.externalStorage) var photoData: Data?
    var walkID: UUID?
    var isFavorite: Bool = false
    var dog: Dog?

    init(date: Date = Date(), caption: String, photoData: Data?, walkID: UUID? = nil, dog: Dog?) {
        self.id = UUID()
        self.date = date
        self.caption = caption
        self.photoData = photoData
        self.walkID = walkID
        self.dog = dog
    }
}

@Model
final class Reminder {
    var id: UUID = UUID()
    var title: String = ""
    var kindRaw: String = CareKind.meal.rawValue
    var hour: Int = 8
    var minute: Int = 0
    var isEnabled: Bool = true
    /// A short detail, such as "with food".
    var note: String = ""
    var repeatRaw: String = ReminderRepeat.daily.rawValue
    /// The first day of a repeating reminder that isn't daily.
    var startDate: Date = Date()
    var dog: Dog?

    init(title: String, kind: CareKind, hour: Int, minute: Int, repeats: ReminderRepeat = .daily, startDate: Date = Date(), note: String = "", dog: Dog?) {
        self.id = UUID()
        self.title = title
        self.kindRaw = kind.rawValue
        self.hour = hour
        self.minute = minute
        self.repeatRaw = repeats.rawValue
        self.startDate = startDate
        self.note = note
        self.dog = dog
    }

    var kind: CareKind { CareKind(rawValue: kindRaw) ?? .meal }

    var repeats: ReminderRepeat {
        get { ReminderRepeat(rawValue: repeatRaw) ?? .daily }
        set { repeatRaw = newValue.rawValue }
    }

    /// The next day on or after `day` this reminder falls on.
    func nextOccurrence(onOrAfter day: Date, calendar: Calendar = .current) -> Date {
        let start = calendar.startOfDay(for: startDate)
        let target = calendar.startOfDay(for: day)
        guard repeats != .daily, target > start else { return max(start, target) }
        switch repeats {
        case .daily:
            return target
        case .monthly:
            var months = calendar.dateComponents([.month], from: start, to: target).month ?? 0
            while true {
                let candidate = calendar.date(byAdding: .month, value: months, to: start) ?? target
                if candidate >= target { return candidate }
                months += 1
            }
        case .weekly, .everyThreeWeeks:
            let step = repeats == .weekly ? 7 : 21
            let days = calendar.dateComponents([.day], from: start, to: target).day ?? 0
            let periods = (days + step - 1) / step
            return calendar.date(byAdding: .day, value: periods * step, to: start) ?? target
        }
    }
}

enum ReminderRepeat: String, CaseIterable, Codable, Identifiable {
    case daily, weekly, everyThreeWeeks, monthly

    var id: String { rawValue }

    var title: String {
        switch self {
        case .daily: "Daily"
        case .weekly: "Weekly"
        case .everyThreeWeeks: "Every 3 weeks"
        case .monthly: "Monthly"
        }
    }
}


/// A member of a dog's family tree.
struct Relative: Codable, Identifiable, Hashable {
    enum Role: String, Codable, CaseIterable {
        case mom, dad, brother, sister

        var title: String {
            switch self {
            case .mom: "Mom"
            case .dad: "Dad"
            case .brother: "Brother"
            case .sister: "Sister"
            }
        }
    }

    var id = UUID()
    var name: String
    var role: Role
    /// Where they live, or "on the app".
    var place: String = ""
    var photoData: Data?
}
