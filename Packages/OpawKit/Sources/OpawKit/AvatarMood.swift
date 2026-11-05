import Foundation

/// The avatar's mood, derived from how the dog has been cared for.
public enum AvatarMood: String, CaseIterable, Codable, Sendable {
    case happy
    case hungry
    case thirsty
    case restless   // needs a walk
    case sleepy
    case fresh      // just had a bath
    case proud      // just earned a badge or finished a big walk

    public var caption: String {
        switch self {
        case .happy: return "Feeling great"
        case .hungry: return "Is it dinner time yet?"
        case .thirsty: return "Could use some water"
        case .restless: return "Let's go for a walk!"
        case .sleepy: return "Zzz… cozy time"
        case .fresh: return "Squeaky clean"
        case .proud: return "Look what we did!"
        }
    }

    /// The motion clip that best fits this mood. Matches the clip names produced by the avatar worker.
    public var clipName: String {
        switch self {
        case .happy: return "wag"
        case .hungry: return "hungry"
        case .thirsty: return "hungry"
        case .restless: return "excited"
        case .sleepy: return "sleepy"
        case .fresh: return "shake"
        case .proud: return "celebrate"
        }
    }
}

/// A snapshot of recent care, used to compute the mood.
public struct CareSnapshot: Sendable {
    public var lastEvent: [CareKind: Date]
    public var activityMinutesToday: Double
    public var dailyActivityGoalMinutes: Double
    public var lastBadgeEarnedAt: Date?
    /// Usual meal times as hours of the day, e.g. [8, 18].
    public var mealHours: [Int]

    public init(
        lastEvent: [CareKind: Date] = [:],
        activityMinutesToday: Double = 0,
        dailyActivityGoalMinutes: Double = 60,
        lastBadgeEarnedAt: Date? = nil,
        mealHours: [Int] = [8, 18]
    ) {
        self.lastEvent = lastEvent
        self.activityMinutesToday = activityMinutesToday
        self.dailyActivityGoalMinutes = dailyActivityGoalMinutes
        self.lastBadgeEarnedAt = lastBadgeEarnedAt
        self.mealHours = mealHours
    }
}

public enum MoodEngine {
    /// Rules are checked in priority order; the first match wins.
    public static func mood(for snapshot: CareSnapshot, now: Date = Date(), calendar: Calendar = .current) -> AvatarMood {
        func hoursSince(_ kind: CareKind) -> Double? {
            snapshot.lastEvent[kind].map { now.timeIntervalSince($0) / 3600 }
        }

        if let badge = snapshot.lastBadgeEarnedAt, now.timeIntervalSince(badge) < 3600 {
            return .proud
        }
        if let bath = hoursSince(.bath), bath < 3 {
            return .fresh
        }
        let hour = calendar.component(.hour, from: now)
        if hour >= 22 || hour < 6 {
            return .sleepy
        }
        if isMealOverdue(snapshot: snapshot, now: now, calendar: calendar) {
            return .hungry
        }
        if let water = hoursSince(.water), water > 8 {
            return .thirsty
        }
        // After 4 pm, nudge for a walk if under half the daily goal.
        if hour >= 16 && snapshot.activityMinutesToday < snapshot.dailyActivityGoalMinutes / 2 {
            return .restless
        }
        return .happy
    }

    /// A meal is overdue when a scheduled meal hour passed more than an hour ago and nothing was logged since.
    static func isMealOverdue(snapshot: CareSnapshot, now: Date, calendar: Calendar) -> Bool {
        let startOfDay = calendar.startOfDay(for: now)
        let passedMeals = snapshot.mealHours
            .compactMap { calendar.date(byAdding: .hour, value: $0, to: startOfDay) }
            .filter { now.timeIntervalSince($0) > 3600 }
        guard let latestDue = passedMeals.max() else { return false }
        guard let lastMeal = snapshot.lastEvent[.meal] else { return true }
        return lastMeal < latestDue.addingTimeInterval(-2 * 3600)
    }
}
