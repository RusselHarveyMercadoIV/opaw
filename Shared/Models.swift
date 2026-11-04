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
    /// Usual meal times as hours of the day.
    var mealHours: [Int] = [8, 18]
    var dailyGoalMinutes: Int = 60
    @Attribute(.externalStorage) var photoData: Data?
    @Attribute(.externalStorage) var cutoutData: Data?
    var avatarStatusRaw: String = AvatarStatus.livingPhoto.rawValue
    var avatarJobID: String?
    var createdAt: Date = Date()
    var lastBadgeEarnedAt: Date?
    var earnedBadgesRaw: [String] = []

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
    var dog: Dog?

    init(kind: CareKind, date: Date = Date(), note: String = "", dog: Dog?) {
        self.id = UUID()
        self.kindRaw = kind.rawValue
        self.date = date
        self.note = note
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
    var dog: Dog?

    init(title: String, kind: CareKind, hour: Int, minute: Int, dog: Dog?) {
        self.id = UUID()
        self.title = title
        self.kindRaw = kind.rawValue
        self.hour = hour
        self.minute = minute
        self.dog = dog
    }

    var kind: CareKind { CareKind(rawValue: kindRaw) ?? .meal }
}
