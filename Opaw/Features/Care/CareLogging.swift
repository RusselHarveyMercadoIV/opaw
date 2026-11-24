import OpawKit
import SwiftData
import SwiftUI
import WidgetKit

extension CareKind {
    /// A short label that fits under a circle.
    var shortTitle: String {
        self == .vet ? "Vet visit" : title
    }

    /// Care points earned for a log, shown as "+4" in the day's log.
    var points: Int {
        switch self {
        case .meal: return 4
        case .meds, .vet: return 5
        case .bath: return 3
        case .poop, .brush, .nails, .play, .custom: return 2
        case .water, .pee, .weight: return 1
        }
    }

    /// What the avatar says right after this is logged.
    var reaction: String {
        switch self {
        case .meal: return "Yum. Seconds?"
        case .water: return "Ahh, refreshing."
        case .pee: return "Much better."
        case .poop: return "Ahh, that's the spot."
        case .bath: return "Squeaky clean. Hmph."
        case .brush: return "So fluffy now."
        case .nails: return "No more click-clack."
        case .meds: return "Not my favourite, but thanks."
        case .vet: return "Brave dog award, please."
        case .weight: return "Don't tell me the number."
        case .play: return "Again! Again!"
        case .custom: return "Noted. Good human."
        }
    }
}

/// The time column in log lists. The design shows 24-hour times like "13:05"; on a phone set to
/// 12-hour time, "10:30 PM" needs more room, so the column widens to keep every time on one line.
enum ClockColumn {
    static let uses12Hour = DateFormatter.dateFormat(fromTemplate: "j", options: 0, locale: .current)?.contains("a") ?? false

    static func width(_ base: CGFloat) -> CGFloat {
        uses12Hour ? base + 22 : base
    }
}

/// Points for a walk: six for any walk, as in the design.
enum WalkPoints {
    static let perWalk = 6
}

/// A toast about a log (7b). Black toasts are the pet reacting to something you did; white ones are
/// the app telling you something. One at a time: a new toast replaces the one on screen.
struct CareToast: Identifiable, Equatable {
    enum Style: Equatable {
        /// "Ahh, that's the spot." Poop logged · score 90 → 92. Undo removes the log.
        case logged
        /// Pee log deleted · Score back to 89. Undo puts the log back.
        case deleted
        /// Poop log removed · Score back to 90, after Undo on a logged toast.
        case undone
    }

    let id = UUID()
    var style: Style = .logged
    let kind: CareKind
    /// The log's name, such as "Poop" or a custom track's name.
    var name: String
    let image: Data?
    let before: Int
    let after: Int
    /// The log this toast is about, so Undo can remove it.
    var eventID: UUID?
    /// A deleted log, so Undo can put it back.
    var restore: CareEventSnapshot?

    var seconds: Double { style == .undone ? 2 : 4 }

    static func == (lhs: CareToast, rhs: CareToast) -> Bool { lhs.id == rhs.id }
}

/// Everything needed to put a deleted log back.
struct CareEventSnapshot {
    let id: UUID
    let dogID: UUID?
    let kindRaw: String
    let date: Date
    let note: String
    let poopConsistencyRaw: String?
    let poopColorRaw: String?
    let weightKg: Double?
    let loggedBy: String
    let trackID: UUID?
    let trackName: String
    let amount: Double?

    init(_ event: CareEvent) {
        id = event.id
        dogID = event.dog?.id
        kindRaw = event.kindRaw
        date = event.date
        note = event.note
        poopConsistencyRaw = event.poopConsistencyRaw
        poopColorRaw = event.poopColorRaw
        weightKg = event.weightKg
        loggedBy = event.loggedBy
        trackID = event.trackID
        trackName = event.trackName
        amount = event.amount
    }

    func makeEvent(dog: Dog) -> CareEvent {
        let event = CareEvent(kind: CareKind(rawValue: kindRaw) ?? .meal, date: date, note: note, dog: dog)
        event.id = id
        event.poopConsistencyRaw = poopConsistencyRaw
        event.poopColorRaw = poopColorRaw
        event.weightKg = weightKg
        event.loggedBy = loggedBy
        event.trackID = trackID
        event.trackName = trackName
        event.amount = amount
        return event
    }
}

extension Notice {
    /// "30-day streak · Miso earned it · View", for a badge just earned.
    static func milestone(_ badge: Badge, dog: Dog, view: @escaping () -> Void) -> Notice {
        Notice(kind: .milestone, title: badge.title, detail: "\(dog.name) · \(badge.detail)", medal: badge.medal, actionTitle: "View", action: view)
    }
}

/// A log waiting for "Delete this log?" after it was held.
struct PendingDelete: Identifiable {
    let id: UUID
    let time: String
    let text: String
    let points: Int
}

/// A log waiting for "Log another?" because it looks like a repeat.
struct PendingLog {
    let message: String
    let perform: () -> Void
}

enum CareRecorder {
    /// Saves a log, refreshes badges and widgets, and returns the reaction to show. A new badge
    /// follows as a milestone toast; a failed save shows a failed toast with Retry instead.
    @MainActor
    static func log(_ event: CareEvent, dog: Dog, in context: ModelContext, toasts: ToastCenter?) -> CareToast? {
        let before = dog.careReport().score
        context.insert(event)
        do {
            try context.save()
        } catch {
            context.delete(event)
            let retry = CareEventSnapshot(event)
            toasts?.show(Notice(kind: .failed, title: "Couldn't save the \(event.displayTitle.lowercased()) log", detail: "Nothing was logged", actionTitle: "Retry") {
                let again = retry.makeEvent(dog: dog)
                again.id = UUID()
                toasts?.show(log(again, dog: dog, in: context, toasts: toasts))
            })
            return nil
        }
        Haptics.success()
        WidgetCenter.shared.reloadTimelines(ofKind: OpawWidgetKind.quickLog)
        let badges = BadgeService.refresh(dog)
        try? context.save()
        let after = dog.careReport().score
        let toast = CareToast(kind: event.kind, name: event.displayTitle, image: dog.cutoutData ?? dog.photoData, before: before, after: after, eventID: event.id)
        if let badge = badges.first, let toasts {
            // The reaction first, then the milestone replaces it.
            Task { @MainActor in
                try? await Task.sleep(for: .seconds(1.6))
                toasts.show(.milestone(badge, dog: dog) { toasts.showChallenges = true })
            }
        }
        return toast
    }

    /// Removes a log and returns the white toast that says so: `.undone` after Undo on a logged
    /// toast, `.deleted` (with Undo to put it back) after holding a log and confirming.
    @MainActor
    @discardableResult
    static func remove(eventID: UUID, as style: CareToast.Style, in context: ModelContext) -> CareToast? {
        let descriptor = FetchDescriptor<CareEvent>(predicate: #Predicate { $0.id == eventID })
        guard let event = try? context.fetch(descriptor).first, let dog = event.dog else { return nil }
        let before = dog.careReport().score
        let snapshot = CareEventSnapshot(event)
        let kind = event.kind
        let name = event.displayTitle
        context.delete(event)
        try? context.save()
        style == .deleted ? Haptics.rigid() : Haptics.soft()
        WidgetCenter.shared.reloadAllTimelines()
        return CareToast(style: style, kind: kind, name: name, image: dog.cutoutData ?? dog.photoData, before: before, after: dog.careReport().score, restore: style == .deleted ? snapshot : nil)
    }

    /// Puts a deleted log back, for Undo on the deleted toast.
    @MainActor
    static func restore(_ snapshot: CareEventSnapshot, in context: ModelContext) {
        guard let dogID = snapshot.dogID else { return }
        let descriptor = FetchDescriptor<Dog>(predicate: #Predicate { $0.id == dogID })
        guard let dog = try? context.fetch(descriptor).first else { return }
        context.insert(snapshot.makeEvent(dog: dog))
        try? context.save()
        Haptics.soft()
        WidgetCenter.shared.reloadAllTimelines()
    }

    /// A question to ask before logging when it looks like a repeat or more than planned, otherwise nil.
    @MainActor
    static func repeatWarning(kind: CareKind, trackID: UUID? = nil, title: String, dog: Dog, now: Date = Date()) -> String? {
        let today = dog.events(on: now).filter { $0.kind == kind && (kind != .custom || $0.trackID == trackID) && $0.date <= now }
        guard let last = today.max(by: { $0.date < $1.date }) else { return nil }
        let minutes = Int(now.timeIntervalSince(last.date) / 60)
        if minutes < 5 {
            return "\(title) was logged \(minutes == 0 ? "just now" : "\(minutes) min ago"). Log another?"
        }
        switch kind {
        case .meal:
            let planned = dog.scheduledMealHours.count
            if planned > 0, today.count >= planned {
                return "\(dog.name) has had \(today.count) of \(planned) meals today. Log another?"
            }
        case .meds:
            let doses = dog.reminders(on: now).filter { $0.kind == .meds }.count
            if doses > 0, today.count >= doses {
                return doses == 1 ? "Today's meds dose is already logged. Log another?" : "All \(doses) meds doses are logged today. Log another?"
            }
        case .bath, .brush, .nails, .vet:
            return "\(title) was already logged today at \(last.date.formatted(date: .omitted, time: .shortened)). Log another?"
        default:
            break
        }
        return nil
    }

    /// The display name for who logged something.
    static func name(_ loggedBy: String) -> String {
        loggedBy.isEmpty ? "You" : loggedBy
    }
}

/// The toast pill (7b): black with the pet bobbing inside a countdown ring after a log, white for
/// deletes and undos. Tap it or swipe it down to dismiss.
struct CareToastView: View {
    let toast: CareToast
    var onUndo: (() -> Void)?
    @State private var ringStart = Date()

    private var dark: Bool { toast.style == .logged }
    private var ink: Color { dark ? .white : Color(hex: 0x111111) }

    var body: some View {
        HStack(spacing: 12) {
            if dark {
                pet
            }
            VStack(alignment: .leading, spacing: 1) {
                Text(headline)
                    .font(.opaw(16, .heavy))
                    .foregroundStyle(ink)
                    .lineLimit(1)
                    .minimumScaleFactor(0.8)
                Text(detail)
                    .font(.opaw(13, .medium))
                    .foregroundStyle(dark ? Color(hex: 0xAAAAAA) : Color(hex: 0x666666))
                    .lineLimit(1)
                    .minimumScaleFactor(0.8)
            }
            Spacer(minLength: 0)
            if dark {
                Text("+\(toast.kind.points)")
                    .font(.opaw(19, .black))
                    .foregroundStyle(.white)
            }
            if let onUndo {
                Button(action: onUndo) {
                    HStack(spacing: 6) {
                        Icon(.undo, size: 16)
                        Text("Undo")
                            .font(.opaw(14, .heavy))
                    }
                    .foregroundStyle(ink)
                    .padding(.horizontal, 14)
                    .frame(height: 44)
                    .background(dark ? Color.white.opacity(0.16) : Color(hex: 0xF1F1F2), in: Capsule())
                }
                .buttonStyle(.pressable)
                .accessibilityHint(toast.style == .deleted ? "Puts this log back" : "Removes this \(toast.name.lowercased()) log")
            }
        }
        .padding(.leading, dark ? 8 : 22)
        .padding(.trailing, onUndo == nil ? 22 : 8)
        .padding(.vertical, 8)
        .frame(minHeight: dark ? 0 : 60)
        .background(dark ? Color(hex: 0x111111) : Color.white, in: Capsule())
        .shadow(color: .black.opacity(dark ? 0.25 : 0.12), radius: 18, y: 14)
        .accessibilityElement(children: .contain)
    }

    private var headline: String {
        switch toast.style {
        case .logged: return "\u{201C}\(toast.kind.reaction)\u{201D}"
        case .deleted: return "\(toast.name) log deleted"
        case .undone: return "\(toast.name) log removed"
        }
    }

    private var detail: String {
        switch toast.style {
        case .logged: return "\(toast.name) logged · score \(toast.before) → \(toast.after)"
        case .deleted, .undone: return "Score back to \(toast.after)"
        }
    }

    /// The pet in a white circle, with a ring that counts down until the toast goes.
    private var pet: some View {
        ZStack {
            Circle().fill(Color.white)
            Group {
                if let data = toast.image, let image = UIImage(data: data) {
                    Image(uiImage: image)
                        .resizable()
                        .scaledToFit()
                        .padding(6)
                } else {
                    Image(systemName: "dog.fill")
                        .font(.system(size: 26))
                        .foregroundStyle(Color(hex: 0x111111))
                }
            }
            .loopMotion(.bob, duration: 0.9)
            .clipShape(Circle())
            TimelineView(.animation) { context in
                let left = max(0, 1 - context.date.timeIntervalSince(ringStart) / toast.seconds)
                Circle()
                    .trim(from: 0, to: left)
                    .stroke(Color.white, style: StrokeStyle(lineWidth: 3, lineCap: .round))
                    .rotationEffect(.degrees(-90))
                    .padding(-4)
            }
        }
        .frame(width: 56, height: 56)
        .padding(3)
        .onAppear { ringStart = Date() }
    }
}

/// Asks "Log another?" for a pending repeat log.
struct RepeatLogConfirmation: ViewModifier {
    @Binding var pending: PendingLog?

    func body(content: Content) -> some View {
        content.confirmationDialog(
            "Log again?",
            isPresented: Binding(get: { pending != nil }, set: { if !$0 { pending = nil } }),
            titleVisibility: .visible,
            presenting: pending
        ) { pending in
            Button("Log another") { pending.perform() }
            Button("Cancel", role: .cancel) {}
        } message: { pending in
            Text(pending.message)
        }
    }
}

/// "Delete this log?" after a log is held (7a, 7h). Delete shows a white toast with Undo.
struct DeleteLogConfirmation: ViewModifier {
    @Binding var pending: PendingDelete?
    @Environment(\.modelContext) private var modelContext
    @Environment(ToastCenter.self) private var toasts

    func body(content: Content) -> some View {
        content.alert(
            "Delete this log?",
            isPresented: Binding(get: { pending != nil }, set: { if !$0 { pending = nil } }),
            presenting: pending
        ) { pending in
            Button("Delete", role: .destructive) {
                // Let the alert finish closing so no row still reads the log.
                Task { @MainActor in
                    try? await Task.sleep(for: .milliseconds(250))
                    let deleted = withAnimation(.snappy) { CareRecorder.remove(eventID: pending.id, as: .deleted, in: modelContext) }
                    withAnimation(.easeOut(duration: 0.32)) { toasts.show(deleted) }
                }
            }
            Button("Keep it", role: .cancel) {}
        } message: { pending in
            Text("\(pending.time)  \(pending.text)  +\(pending.points)\nYou can undo it right after.")
        }
    }
}

extension View {
    func deleteLogConfirmation(_ pending: Binding<PendingDelete?>) -> some View {
        modifier(DeleteLogConfirmation(pending: pending))
    }

    /// Hold a log row until the "Delete this log?" popup opens.
    func holdToDelete(_ entry: @autoclosure @escaping () -> PendingDelete?, into pending: Binding<PendingDelete?>) -> some View {
        onLongPressGesture(minimumDuration: 0.5) {
            guard let value = entry() else { return }
            Haptics.medium()
            pending.wrappedValue = value
        }
    }

    func repeatLogConfirmation(_ pending: Binding<PendingLog?>) -> some View {
        modifier(RepeatLogConfirmation(pending: pending))
    }

}
