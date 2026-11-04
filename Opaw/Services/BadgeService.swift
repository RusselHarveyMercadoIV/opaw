import Foundation
import OpawKit
import SwiftData

/// Turns a dog's history into badge stats, and awards new badges.
@MainActor
enum BadgeService {
    static func stats(for dog: Dog, friendsCount: Int = 0, now: Date = Date(), calendar: Calendar = .current) -> BadgeStats {
        let events = dog.careEvents ?? []
        let walks = dog.walks ?? []
        let monthStart = calendar.dateInterval(of: .month, for: now)?.start ?? now
        return BadgeStats(
            totalCareEvents: events.count,
            careStreakDays: StreakCalculator.currentStreak(dates: events.map(\.date), now: now, calendar: calendar),
            walkCount: walks.count,
            totalWalkKm: walks.reduce(0) { $0 + $1.distanceMeters } / 1000,
            longestWalkKm: (walks.map(\.distanceMeters).max() ?? 0) / 1000,
            kmThisMonth: walks.filter { $0.startedAt >= monthStart }.reduce(0) { $0 + $1.distanceMeters } / 1000,
            bathCount: events.filter { $0.kind == .bath }.count,
            memoryCount: (dog.memories ?? []).count,
            earlyWalkCount: walks.filter { calendar.component(.hour, from: $0.startedAt) < 7 }.count,
            walkStreakDays: StreakCalculator.currentStreak(dates: walks.map(\.startedAt), now: now, calendar: calendar),
            vetVisitCount: events.filter { $0.kind == .vet }.count,
            friendsCount: friendsCount
        )
    }

    private static func newestKey(_ dog: Dog) -> String { "newestBadge." + dog.id.uuidString }

    /// The badge earned most recently, while it's still new (three days).
    static func newestBadge(for dog: Dog, now: Date = Date()) -> Badge? {
        guard let earnedAt = dog.lastBadgeEarnedAt, now.timeIntervalSince(earnedAt) < 3 * 86_400 else { return nil }
        return OpawDefaults.shared.string(forKey: newestKey(dog)).flatMap(Badge.init(rawValue:))
    }

    /// Awards any newly earned badges and returns them, so the UI can celebrate.
    @discardableResult
    static func refresh(_ dog: Dog, friendsCount: Int = 0) -> [Badge] {
        let new = BadgeEngine.newlyEarned(stats(for: dog, friendsCount: friendsCount), alreadyEarned: dog.earnedBadges)
        guard !new.isEmpty else { return [] }
        dog.earnedBadges = dog.earnedBadges.union(new)
        dog.lastBadgeEarnedAt = Date()
        if let newest = new.first {
            OpawDefaults.shared.set(newest.rawValue, forKey: newestKey(dog))
        }
        return new
    }

    /// Snapshot used by the mood engine.
    static func careSnapshot(for dog: Dog, now: Date = Date()) -> CareSnapshot {
        dog.careSnapshot(now: now)
    }
}
