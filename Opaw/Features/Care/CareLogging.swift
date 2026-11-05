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

/// Points for a walk: six for any walk, as in the design.
enum WalkPoints {
    static let perWalk = 6
}

/// The reaction shown after a log.
struct CareToast: Identifiable, Equatable {
    let id = UUID()
    let kind: CareKind
    let image: Data?
    let before: Int
    let after: Int
    /// The log this toast is about, so Undo can remove it.
    var eventID: UUID?
}

/// A log waiting for "Log another?" because it looks like a repeat.
struct PendingLog {
    let message: String
    let perform: () -> Void
}

enum CareRecorder {
    /// Saves a log, refreshes badges and widgets, and returns the reaction to show.
    @MainActor
    static func log(_ event: CareEvent, dog: Dog, in context: ModelContext) -> (toast: CareToast, badge: Badge?) {
        let before = dog.careReport().score
        context.insert(event)
        try? context.save()
        Haptics.success()
        WidgetCenter.shared.reloadTimelines(ofKind: OpawWidgetKind.quickLog)
        let badge = BadgeService.refresh(dog).first
        try? context.save()
        let after = dog.careReport().score
        return (CareToast(kind: event.kind, image: dog.cutoutData ?? dog.photoData, before: before, after: after, eventID: event.id), badge)
    }

    /// Removes a log, for Undo on the toast.
    @MainActor
    static func undo(eventID: UUID, in context: ModelContext) {
        let descriptor = FetchDescriptor<CareEvent>(predicate: #Predicate { $0.id == eventID })
        guard let event = try? context.fetch(descriptor).first else { return }
        context.delete(event)
        try? context.save()
        Haptics.soft()
        WidgetCenter.shared.reloadAllTimelines()
    }

    /// Removes a log from a list, after its context menu has closed so no row still reads it.
    @MainActor
    static func delete(eventID: UUID, in context: ModelContext) {
        Task { @MainActor in
            try? await Task.sleep(for: .milliseconds(350))
            withAnimation(.snappy) { undo(eventID: eventID, in: context) }
        }
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

/// The black pill that pops up after a log: the pet bobbing, its line, and the points earned.
struct CareToastView: View {
    let toast: CareToast
    var onUndo: (() -> Void)?

    var body: some View {
        HStack(spacing: 12) {
            ZStack {
                Circle().fill(Color.white)
                Group {
                    if let data = toast.image, let image = UIImage(data: data) {
                        Image(uiImage: image)
                            .resizable()
                            .scaledToFit()
                            .padding(4)
                    } else {
                        Image(systemName: "dog.fill")
                            .font(.system(size: 28))
                            .foregroundStyle(Color(hex: 0x111111))
                    }
                }
                .loopMotion(.bob, duration: 0.9)
            }
            .frame(width: 62, height: 62)
            .clipShape(Circle())

            VStack(alignment: .leading, spacing: 1) {
                Text("\u{201C}\(toast.kind.reaction)\u{201D}")
                    .font(.opaw(16, .heavy))
                    .foregroundStyle(.white)
                    .lineLimit(1)
                    .minimumScaleFactor(0.8)
                Text("\(toast.kind.title) logged · score \(toast.before) → \(toast.after)")
                    .font(.opaw(13, .medium))
                    .foregroundStyle(Color(hex: 0xAAAAAA))
                    .lineLimit(1)
                    .minimumScaleFactor(0.8)
            }
            Spacer(minLength: 0)
            Text("+\(toast.kind.points)")
                .font(.opaw(19, .black))
                .foregroundStyle(.white)
            if let onUndo {
                Button("Undo", action: onUndo)
                    .font(.opaw(14, .heavy))
                    .foregroundStyle(.white)
                    .padding(.horizontal, 12)
                    .padding(.vertical, 8)
                    .background(Color.white.opacity(0.16), in: Capsule())
                    .buttonStyle(.pressable)
                    .accessibilityLabel("Undo \(toast.kind.title) log")
            }
        }
        .padding(.leading, 8)
        .padding(.trailing, onUndo == nil ? 22 : 10)
        .padding(.vertical, 8)
        .background(Color(hex: 0x111111), in: Capsule())
        .shadow(color: .black.opacity(0.25), radius: 18, y: 18)
        .accessibilityElement(children: .contain)
    }
}

/// Hosts a toast: shows it, then hides it after a few seconds. Undo removes the log.
struct ToastSlot: ViewModifier {
    @Binding var toast: CareToast?
    var bottom: CGFloat = 32
    @Environment(\.modelContext) private var modelContext

    private func undoAction(for toast: CareToast) -> (() -> Void)? {
        guard let id = toast.eventID else { return nil }
        return {
            CareRecorder.undo(eventID: id, in: modelContext)
            withAnimation(.easeInOut(duration: 0.25)) { self.toast = nil }
        }
    }

    func body(content: Content) -> some View {
        content.overlay(alignment: .bottom) {
            if let toast {
                CareToastView(toast: toast, onUndo: undoAction(for: toast))
                    .padding(.horizontal, 18)
                    .padding(.bottom, bottom)
                    .transition(.move(edge: .bottom).combined(with: .opacity))
                    .onTapGesture { withAnimation { self.toast = nil } }
                    .task(id: toast.id) {
                        try? await Task.sleep(for: .seconds(5))
                        withAnimation(.easeInOut(duration: 0.35)) {
                            if self.toast?.id == toast.id { self.toast = nil }
                        }
                    }
            }
        }
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

extension View {
    func repeatLogConfirmation(_ pending: Binding<PendingLog?>) -> some View {
        modifier(RepeatLogConfirmation(pending: pending))
    }

    func careToast(_ toast: Binding<CareToast?>, bottom: CGFloat = 32) -> some View {
        modifier(ToastSlot(toast: toast, bottom: bottom))
    }
}
