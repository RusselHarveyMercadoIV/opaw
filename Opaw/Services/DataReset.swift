import SwiftData
import UserNotifications
import WidgetKit

/// Settings > Reset data: erases everything Opaw keeps on this device so setup starts again.
///
/// Purchases stay with the App Store, and a Pack account stays on its server; the
/// one-time offer is not shown a second time.
@MainActor
enum DataReset {
    static func eraseAll(in context: ModelContext, recorder: WalkRecorder) {
        if recorder.state != .idle { recorder.cancel() }

        let dogs = (try? context.fetch(FetchDescriptor<Dog>())) ?? []
        for dog in dogs {
            AvatarClipStore.deleteAll(dogID: dog.id)
            context.delete(dog)
        }
        // Anything left without a pet, such as logs saved by a widget.
        for event in (try? context.fetch(FetchDescriptor<CareEvent>())) ?? [] { context.delete(event) }
        for walk in (try? context.fetch(FetchDescriptor<Walk>())) ?? [] { context.delete(walk) }
        for memory in (try? context.fetch(FetchDescriptor<Memory>())) ?? [] { context.delete(memory) }
        for reminder in (try? context.fetch(FetchDescriptor<Reminder>())) ?? [] { context.delete(reminder) }
        try? context.save()

        let defaults = OpawDefaults.shared
        let offerShown = defaults.bool(forKey: "premiumOfferShown")
        defaults.removePersistentDomain(forName: OpawDefaults.appGroupID)
        if offerShown { defaults.set(true, forKey: "premiumOfferShown") }
        UserDefaults.standard.removeObject(forKey: "walkActivity")

        let notifications = UNUserNotificationCenter.current()
        notifications.removeAllPendingNotificationRequests()
        notifications.removeAllDeliveredNotifications()
        WidgetCenter.shared.reloadAllTimelines()
    }
}
