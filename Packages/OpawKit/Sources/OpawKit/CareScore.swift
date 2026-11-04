import Foundation

/// How the avatar is doing overall, from 0 to 100.
public enum CareTier: String, Sendable, CaseIterable {
    case thriving   // 80–100
    case okay       // 50–79
    case needsYou   // 0–49

    public init(score: Int) {
        switch score {
        case 80...: self = .thriving
        case 50...: self = .okay
        default: self = .needsYou
        }
    }

    public var title: String {
        switch self {
        case .thriving: return "Thriving"
        case .okay: return "Okay"
        case .needsYou: return "Needs you"
        }
    }

    /// The little sound shown in a bubble above the avatar.
    public var sound: String {
        switch self {
        case .thriving: return "woof!"
        case .okay: return "hmm…"
        case .needsYou: return "whine"
        }
    }
}

/// Something the dog is waiting on.
public enum CareNeed: String, Sendable, CaseIterable {
    case meds, meal, water, potty, walk

    public var line: String {
        switch self {
        case .meds: return "My meds are late."
        case .meal: return "Is it dinner time yet?"
        case .water: return "The water bowl is looking empty."
        case .potty: return "I really need to go out."
        case .walk: return "Bit bored. Nobody's logged a walk today."
        }
    }

    var penalty: Int {
        switch self {
        case .meds: return 30
        case .meal: return 25
        case .water: return 12
        case .potty: return 12
        case .walk: return 20
        }
    }
}

public struct CareReport: Equatable, Sendable {
    public let score: Int
    public let needs: [CareNeed]

    public var tier: CareTier { CareTier(score: score) }

    /// What the avatar says, in its own voice.
    public var line: String {
        switch needs.count {
        case 0: return "Feeling great. Walk later?"
        case 1: return needs[0].line
        default: return "\(needs[0].line) \(needs[1].line)"
        }
    }
}

public enum CareScore {
    /// Scores today's care. Needs are listed most urgent first.
    ///
    /// - Parameter medsOverdue: whether a meds reminder passed more than 30 minutes ago without a log.
    public static func report(
        for snapshot: CareSnapshot,
        medsOverdue: Bool = false,
        now: Date = Date(),
        calendar: Calendar = .current
    ) -> CareReport {
        var needs: [CareNeed] = []
        func hoursSince(_ kind: CareKind) -> Double? {
            snapshot.lastEvent[kind].map { now.timeIntervalSince($0) / 3600 }
        }
        let hour = calendar.component(.hour, from: now)
        let awake = hour >= 7 && hour < 22

        if medsOverdue { needs.append(.meds) }
        if MoodEngine.isMealOverdue(snapshot: snapshot, now: now, calendar: calendar) { needs.append(.meal) }
        if awake, (hoursSince(.water) ?? .infinity) > 8 { needs.append(.water) }
        if awake, (hoursSince(.pee) ?? .infinity) > 7 { needs.append(.potty) }
        if hour >= 15 && snapshot.activityMinutesToday < snapshot.dailyActivityGoalMinutes / 2 { needs.append(.walk) }

        var score = 100 - needs.reduce(0) { $0 + $1.penalty }
        // Reward progress toward the activity goal a little, so a good walk shows.
        let goal = max(1, snapshot.dailyActivityGoalMinutes)
        let progress = min(1, snapshot.activityMinutesToday / goal)
        score = score - 8 + Int((progress * 8).rounded())
        return CareReport(score: min(100, max(0, score)), needs: needs)
    }
}
