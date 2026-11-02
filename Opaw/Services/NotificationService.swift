import Foundation
import UserNotifications

/// Schedules care reminders as repeating local notifications.
@MainActor
enum NotificationService {
    private static let prefix = "opaw.reminder."

    @discardableResult
    static func requestAuthorization() async -> Bool {
        (try? await UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound, .badge])) ?? false
    }

    /// Replaces all scheduled reminders with the enabled ones for every dog.
    static func reschedule(_ reminders: [Reminder]) async {
        struct Item {
            let id: String
            let title: String
            let body: String
            let hour: Int
            let minute: Int
        }
        let items = reminders.filter(\.isEnabled).map { reminder in
            Item(
                id: prefix + reminder.id.uuidString,
                title: reminder.title,
                body: "\(reminder.kind.emoji) Time for \(reminder.dog?.name ?? "your dog")'s \(reminder.kind.title.lowercased()).",
                hour: reminder.hour,
                minute: reminder.minute
            )
        }

        let center = UNUserNotificationCenter.current()
        let pending = await center.pendingNotificationRequests()
        let old = pending.map(\.identifier).filter { $0.hasPrefix(prefix) }
        center.removePendingNotificationRequests(withIdentifiers: old)

        for item in items {
            let content = UNMutableNotificationContent()
            content.title = item.title
            content.body = item.body
            content.sound = .default
            var components = DateComponents()
            components.hour = item.hour
            components.minute = item.minute
            let trigger = UNCalendarNotificationTrigger(dateMatching: components, repeats: true)
            try? await center.add(UNNotificationRequest(identifier: item.id, content: content, trigger: trigger))
        }
    }
}
