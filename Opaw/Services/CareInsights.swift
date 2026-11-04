import Foundation
import OpawKit

/// Read-only views over a dog's care log, shared by Home, Care, Schedule and History.
extension Dog {
    func events(on day: Date, calendar: Calendar = .current) -> [CareEvent] {
        let start = calendar.startOfDay(for: day)
        let end = calendar.date(byAdding: .day, value: 1, to: start) ?? day
        return (careEvents ?? []).filter { $0.date >= start && $0.date < end }
    }

    func count(_ kind: CareKind, on day: Date = Date()) -> Int {
        events(on: day).filter { $0.kind == kind }.count
    }

    /// Kilometres walked on a day.
    func walkedKm(on day: Date = Date(), calendar: Calendar = .current) -> Double {
        let start = calendar.startOfDay(for: day)
        let end = calendar.date(byAdding: .day, value: 1, to: start) ?? day
        return (walks ?? []).filter { $0.startedAt >= start && $0.startedAt < end }
            .reduce(0) { $0 + $1.distanceMeters } / 1000
    }

    func walkedKm(from start: Date, to end: Date) -> Double {
        (walks ?? []).filter { $0.startedAt >= start && $0.startedAt < end }
            .reduce(0) { $0 + $1.distanceMeters } / 1000
    }

    var enabledReminders: [Reminder] {
        (reminders ?? []).filter(\.isEnabled).sorted { ($0.hour, $0.minute) < ($1.hour, $1.minute) }
    }

    /// The meds reminder that is late or comes next today, if any.
    func medsStatus(now: Date = Date()) -> (overdue: Bool, next: Date?) {
        let meds = enabledReminders.filter { $0.kind == .meds }
        let pending = meds.filter { !$0.isDone(on: now, for: self) }.map { $0.dueDate(on: now) }
        let overdue = pending.contains { now.timeIntervalSince($0) > 30 * 60 }
        return (overdue, pending.min())
    }

    @MainActor
    func careReport(now: Date = Date()) -> CareReport {
        CareScore.report(
            for: BadgeService.careSnapshot(for: self, now: now),
            medsOverdue: medsStatus(now: now).overdue,
            now: now
        )
    }

    /// Days in a row, ending today or yesterday, with at least three logs.
    var careStreak: Int {
        let calendar = Calendar.current
        let byDay = Dictionary(grouping: careEvents ?? []) { calendar.startOfDay(for: $0.date) }
        var day = calendar.startOfDay(for: Date())
        if (byDay[day]?.count ?? 0) < 3 {
            day = calendar.date(byAdding: .day, value: -1, to: day) ?? day
        }
        var streak = 0
        while (byDay[day]?.count ?? 0) >= 3 {
            streak += 1
            guard let previous = calendar.date(byAdding: .day, value: -1, to: day) else { break }
            day = previous
        }
        return streak
    }

    /// Log counts for the last seven days, oldest first.
    var lastSevenDayCounts: [Int] {
        let calendar = Calendar.current
        let today = calendar.startOfDay(for: Date())
        return (0..<7).reversed().map { offset in
            let day = calendar.date(byAdding: .day, value: -offset, to: today) ?? today
            return events(on: day).count
        }
    }

    var ageDescription: String? {
        guard let birthDate else { return nil }
        let parts = Calendar.current.dateComponents([.year, .month], from: birthDate, to: Date())
        let years = parts.year ?? 0
        if years == 0 {
            let months = max(1, parts.month ?? 1)
            return months == 1 ? "1 month old" : "\(months) months old"
        }
        return years == 1 ? "1 year old" : "\(years) years old"
    }
}

extension Reminder {
    func dueDate(on day: Date, calendar: Calendar = .current) -> Date {
        calendar.date(bySettingHour: hour, minute: minute, second: 0, of: day) ?? day
    }

    /// Done when a matching log falls between 90 minutes before and 6 hours after the due time.
    func isDone(on day: Date, for dog: Dog) -> Bool {
        let due = dueDate(on: day)
        return (dog.careEvents ?? []).contains {
            $0.kind == kind && $0.date >= due.addingTimeInterval(-90 * 60) && $0.date <= due.addingTimeInterval(6 * 3600)
        }
    }
}
