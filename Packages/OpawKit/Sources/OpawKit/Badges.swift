import Foundation

/// Aggregated stats the badge rules look at.
public struct BadgeStats: Sendable, Equatable {
    public var totalCareEvents: Int
    public var careStreakDays: Int
    public var walkCount: Int
    public var totalWalkKm: Double
    public var longestWalkKm: Double
    public var kmThisMonth: Double
    public var bathCount: Int
    public var memoryCount: Int
    public var earlyWalkCount: Int     // walks started before 7 am
    public var walkStreakDays: Int
    public var vetVisitCount: Int
    public var friendsCount: Int

    public init(
        totalCareEvents: Int = 0,
        careStreakDays: Int = 0,
        walkCount: Int = 0,
        totalWalkKm: Double = 0,
        longestWalkKm: Double = 0,
        kmThisMonth: Double = 0,
        bathCount: Int = 0,
        memoryCount: Int = 0,
        earlyWalkCount: Int = 0,
        walkStreakDays: Int = 0,
        vetVisitCount: Int = 0,
        friendsCount: Int = 0
    ) {
        self.totalCareEvents = totalCareEvents
        self.careStreakDays = careStreakDays
        self.walkCount = walkCount
        self.totalWalkKm = totalWalkKm
        self.longestWalkKm = longestWalkKm
        self.kmThisMonth = kmThisMonth
        self.bathCount = bathCount
        self.memoryCount = memoryCount
        self.earlyWalkCount = earlyWalkCount
        self.walkStreakDays = walkStreakDays
        self.vetVisitCount = vetVisitCount
        self.friendsCount = friendsCount
    }
}

public enum Badge: String, CaseIterable, Codable, Sendable, Identifiable {
    case firstLog
    case careStreak7
    case careStreak30
    case firstWalk
    case walks10
    case walks100
    case km5Walk
    case km10Walk
    case km50Month
    case km100Total
    case earlyBird
    case walkStreak7
    case squeakyClean
    case memoryKeeper
    case healthCheck
    case packLeader

    public var id: String { rawValue }

    public var title: String {
        switch self {
        case .firstLog: return "First Paw Print"
        case .careStreak7: return "Week of Love"
        case .careStreak30: return "Devoted Human"
        case .firstWalk: return "First Steps"
        case .walks10: return "Regular Rambler"
        case .walks100: return "Trail Legend"
        case .km5Walk: return "5K Pup"
        case .km10Walk: return "10K Hound"
        case .km50Month: return "50 km Month"
        case .km100Total: return "Century Paws"
        case .earlyBird: return "Early Bird"
        case .walkStreak7: return "Never Skip a Walk"
        case .squeakyClean: return "Squeaky Clean"
        case .memoryKeeper: return "Memory Keeper"
        case .healthCheck: return "Health Check"
        case .packLeader: return "Pack Leader"
        }
    }

    public var detail: String {
        switch self {
        case .firstLog: return "Log your first care event"
        case .careStreak7: return "Log care 7 days in a row"
        case .careStreak30: return "Log care 30 days in a row"
        case .firstWalk: return "Finish your first tracked walk"
        case .walks10: return "Finish 10 walks"
        case .walks100: return "Finish 100 walks"
        case .km5Walk: return "Walk 5 km in one go"
        case .km10Walk: return "Walk 10 km in one go"
        case .km50Month: return "Walk 50 km in a calendar month"
        case .km100Total: return "Walk 100 km in total"
        case .earlyBird: return "Start 5 walks before 7 am"
        case .walkStreak7: return "Walk 7 days in a row"
        case .squeakyClean: return "Log 5 baths"
        case .memoryKeeper: return "Save 10 memories"
        case .healthCheck: return "Log a vet visit"
        case .packLeader: return "Make 5 friends in the pack"
        }
    }

    public var symbol: String {
        switch self {
        case .firstLog: return "pawprint.fill"
        case .careStreak7, .careStreak30: return "flame.fill"
        case .firstWalk, .walks10, .walks100: return "figure.walk"
        case .km5Walk, .km10Walk: return "medal.fill"
        case .km50Month, .km100Total: return "trophy.fill"
        case .earlyBird: return "sunrise.fill"
        case .walkStreak7: return "calendar"
        case .squeakyClean: return "bubbles.and.sparkles.fill"
        case .memoryKeeper: return "photo.stack.fill"
        case .healthCheck: return "heart.text.square.fill"
        case .packLeader: return "person.3.fill"
        }
    }

    /// Progress toward the badge, from 0 to 1.
    public func progress(_ s: BadgeStats) -> Double {
        func ratio(_ value: Double, _ target: Double) -> Double { min(1, max(0, value / target)) }
        switch self {
        case .firstLog: return ratio(Double(s.totalCareEvents), 1)
        case .careStreak7: return ratio(Double(s.careStreakDays), 7)
        case .careStreak30: return ratio(Double(s.careStreakDays), 30)
        case .firstWalk: return ratio(Double(s.walkCount), 1)
        case .walks10: return ratio(Double(s.walkCount), 10)
        case .walks100: return ratio(Double(s.walkCount), 100)
        case .km5Walk: return ratio(s.longestWalkKm, 5)
        case .km10Walk: return ratio(s.longestWalkKm, 10)
        case .km50Month: return ratio(s.kmThisMonth, 50)
        case .km100Total: return ratio(s.totalWalkKm, 100)
        case .earlyBird: return ratio(Double(s.earlyWalkCount), 5)
        case .walkStreak7: return ratio(Double(s.walkStreakDays), 7)
        case .squeakyClean: return ratio(Double(s.bathCount), 5)
        case .memoryKeeper: return ratio(Double(s.memoryCount), 10)
        case .healthCheck: return ratio(Double(s.vetVisitCount), 1)
        case .packLeader: return ratio(Double(s.friendsCount), 5)
        }
    }
}

public enum BadgeEngine {
    /// All badges whose requirements are met by these stats.
    public static func earned(_ stats: BadgeStats) -> Set<Badge> {
        Set(Badge.allCases.filter { $0.progress(stats) >= 1 })
    }

    /// Badges newly earned compared to what the dog already has.
    public static func newlyEarned(_ stats: BadgeStats, alreadyEarned: Set<Badge>) -> [Badge] {
        Badge.allCases.filter { earned(stats).contains($0) && !alreadyEarned.contains($0) }
    }
}
