import Network
import SwiftData
import SwiftUI

// MARK: - Toasts (7b)
//
// One toast at a time, across the app: a new toast replaces the one on screen. Black toasts are the
// pet reacting to something you did (a log, a milestone); white ones are the app telling you
// something. They sit 12 points above the bottom buttons, and a tap or a swipe down dismisses them.

/// Something the app tells you: family logs, meds due, offline, a failed save, a milestone.
struct Notice: Identifiable {
    enum Kind {
        /// Someone else in the household logged something. 4 s.
        case family
        /// Meds are due. Stays until you log it or swipe it away; the only toast that uses red.
        case due
        /// You went offline. 4 s, then it shrinks to the Offline chip.
        case offline
        /// A save failed. Stays until retried or dismissed.
        case failed
        /// A streak or badge. 6 s.
        case milestone
    }

    let id = UUID()
    let kind: Kind
    let title: String
    let detail: String
    /// A photo for family toasts.
    var image: Data?
    /// The big number or medal on a milestone toast.
    var medal: String?
    var actionTitle: String?
    var action: (() -> Void)?

    /// How long it stays, or nil when it stays until dealt with.
    var seconds: Double? {
        switch kind {
        case .family, .offline: return 4
        case .milestone: return 6
        case .due, .failed: return nil
        }
    }
}

@MainActor
@Observable
final class ToastCenter {
    enum Item {
        case care(CareToast)
        case notice(Notice)

        var id: UUID {
            switch self {
            case .care(let toast): return toast.id
            case .notice(let notice): return notice.id
            }
        }

        var seconds: Double? {
            switch self {
            case .care(let toast): return toast.seconds
            case .notice(let notice): return notice.seconds
            }
        }
    }

    private(set) var item: Item?
    /// True while the phone has no network; Today shows the Offline chip.
    private(set) var isOffline = false
    /// Set by a milestone's View button; the tab view opens Challenges.
    var showChallenges = false

    private let monitor = NWPathMonitor()
    /// Reminders whose due toast was already shown, so a swiped-away one doesn't come back.
    private var shownDue: Set<String> = []

    init() {
        monitor.pathUpdateHandler = { [weak self] path in
            let offline = path.status != .satisfied
            Task { @MainActor in self?.setOffline(offline) }
        }
        monitor.start(queue: DispatchQueue(label: "app.opaw.network"))
    }

    func show(_ toast: CareToast?) {
        guard let toast else { return }
        item = .care(toast)
    }

    func show(_ notice: Notice) {
        switch notice.kind {
        case .due: Haptics.warning()
        case .failed: Haptics.error()
        case .milestone: Haptics.milestone()
        case .family, .offline: break
        }
        item = .notice(notice)
    }

    func dismiss(_ id: UUID? = nil) {
        guard id == nil || item?.id == id else { return }
        item = nil
    }

    /// "Meds are due · Apoquel · 18:00", once per dose.
    func showDue(_ reminder: Reminder, on day: Date, log: @escaping () -> Void) {
        let key = "\(reminder.id.uuidString)-\(Calendar.current.startOfDay(for: day).timeIntervalSince1970)"
        guard !shownDue.contains(key) else { return }
        shownDue.insert(key)
        let time = reminder.dueDate(on: day).formatted(date: .omitted, time: .shortened)
        let name = reminder.title.isEmpty ? reminder.kind.title : reminder.title
        show(Notice(kind: .due, title: "\(reminder.kind == .meds ? "Meds are" : "\(reminder.kind.title) is") due", detail: "\(name) · \(time)", actionTitle: "Log", action: log))
    }

    private func setOffline(_ offline: Bool) {
        guard offline != isOffline else { return }
        isOffline = offline
        if offline {
            show(Notice(kind: .offline, title: "You're offline", detail: "Logging keeps working"))
        } else if case .notice(let notice)? = item, notice.kind == .offline {
            item = nil
        }
    }
}

// MARK: - Host

extension View {
    /// Shows the app's toasts over this view, `bottom` points above its bottom edge.
    func toastHost(bottom: CGFloat) -> some View {
        modifier(ToastHost(bottom: bottom))
    }
}

private struct ToastHost: ViewModifier {
    let bottom: CGFloat
    @Environment(ToastCenter.self) private var center
    @Environment(\.modelContext) private var modelContext
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var drag: CGFloat = 0

    /// Things that arrive rise 24 points over about a third of a second; things that leave go faster.
    private var enter: Animation { reduceMotion ? .easeInOut(duration: 0.15) : .easeOut(duration: 0.32) }
    private var exit: Animation { reduceMotion ? .easeInOut(duration: 0.15) : .easeIn(duration: 0.2) }

    func body(content: Content) -> some View {
        content.overlay(alignment: .bottom) {
            ZStack {
                if let item = center.item {
                    toast(item)
                        .id(item.id)
                        .padding(.horizontal, 18)
                        .padding(.bottom, bottom)
                        .offset(y: max(0, drag))
                        .transition(reduceMotion ? .opacity : .asymmetric(
                            insertion: .offset(y: 24).combined(with: .opacity),
                            removal: .offset(y: 10).combined(with: .opacity)
                        ))
                        .onTapGesture { close(item.id) }
                        .gesture(
                            DragGesture(minimumDistance: 8)
                                .onChanged { drag = $0.translation.height }
                                .onEnded { value in
                                    if value.translation.height > 30 { close(item.id) }
                                    withAnimation(.snappy) { drag = 0 }
                                }
                        )
                        .task(id: item.id) {
                            guard let seconds = item.seconds else { return }
                            try? await Task.sleep(for: .seconds(seconds))
                            close(item.id)
                        }
                }
            }
            .animation(enter, value: center.item?.id)
        }
    }

    private func close(_ id: UUID) {
        withAnimation(exit) { center.dismiss(id) }
    }

    @ViewBuilder
    private func toast(_ item: ToastCenter.Item) -> some View {
        switch item {
        case .care(let toast):
            CareToastView(toast: toast, onUndo: undoAction(for: toast))
        case .notice(let notice):
            NoticeView(notice: notice) {
                close(notice.id)
                notice.action?()
            }
        }
    }

    private func undoAction(for toast: CareToast) -> (() -> Void)? {
        switch toast.style {
        case .logged:
            guard let id = toast.eventID else { return nil }
            return {
                let undone = CareRecorder.remove(eventID: id, as: .undone, in: modelContext)
                withAnimation(enter) { center.show(undone) }
            }
        case .deleted:
            guard let snapshot = toast.restore else { return nil }
            return {
                CareRecorder.restore(snapshot, in: modelContext)
                close(toast.id)
            }
        case .undone:
            return nil
        }
    }
}

/// A notice pill: an icon in a circle, two lines, and an optional action.
struct NoticeView: View {
    let notice: Notice
    let onAction: () -> Void

    private var dark: Bool { notice.kind == .milestone }

    var body: some View {
        HStack(spacing: 12) {
            icon
            VStack(alignment: .leading, spacing: 1) {
                Text(notice.title)
                    .font(.opaw(16, .heavy))
                    .foregroundStyle(dark ? Color.white : Color(hex: 0x111111))
                    .lineLimit(1)
                    .minimumScaleFactor(0.8)
                Text(notice.detail)
                    .font(.opaw(13, .medium))
                    .foregroundStyle(dark ? Color(hex: 0xAAAAAA) : Color(hex: 0x666666))
                    .lineLimit(1)
                    .minimumScaleFactor(0.8)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            if let title = notice.actionTitle, notice.action != nil {
                Button(title, action: onAction)
                    .font(.opaw(15, .heavy))
                    .foregroundStyle(.white)
                    .padding(.horizontal, 16)
                    .frame(height: 46)
                    .background(dark ? Color.white.opacity(0.16) : Color(hex: 0x111111), in: Capsule())
                    .buttonStyle(.pressable)
            }
        }
        .padding(8)
        .padding(.trailing, notice.actionTitle == nil ? 14 : 2)
        .frame(minHeight: 78)
        .background(dark ? Color(hex: 0x111111) : Color.white, in: Capsule())
        .shadow(color: .black.opacity(dark ? 0.25 : 0.14), radius: 18, y: 18)
        .accessibilityElement(children: .contain)
    }

    @ViewBuilder
    private var icon: some View {
        Group {
            switch notice.kind {
            case .family:
                if let data = notice.image, let image = UIImage(data: data) {
                    Image(uiImage: image).resizable().scaledToFill()
                } else {
                    Icon(.users, size: 24)
                }
            case .due: Icon(.meds, size: 24, weight: 2.4)
            case .offline: Icon(.cloudOff, size: 24)
            case .failed: Icon(.alert, size: 24)
            case .milestone:
                Text(notice.medal ?? "★")
                    .font(.opaw(22, .black))
                    .lineLimit(1)
                    .minimumScaleFactor(0.5)
                    .padding(6)
            }
        }
        .foregroundStyle(notice.kind == .due ? Color.white : Color(hex: 0x111111))
        .frame(width: 62, height: 62)
        .background(background, in: Circle())
        .clipShape(Circle())
        .accessibilityHidden(true)
    }

    private var background: Color {
        switch notice.kind {
        case .due: return .opawAlert
        case .family: return Color(hex: 0xE6E6E8)
        case .milestone: return .white
        default: return Color(hex: 0xF4F4F5)
        }
    }
}

// MARK: - Watchers

/// Shows a family toast when someone else's log arrives, and a due toast when a meds dose is late.
struct ToastWatcher: View {
    @Query(sort: \CareEvent.date, order: .reverse) private var events: [CareEvent]
    @Query(sort: \Dog.createdAt) private var dogs: [Dog]
    @Environment(ToastCenter.self) private var center
    @Environment(\.modelContext) private var modelContext
    @Environment(\.scenePhase) private var scenePhase
    @State private var seen: Set<UUID>?

    var body: some View {
        TimelineView(.periodic(from: .now, by: 60)) { context in
            Color.clear
                .onChange(of: context.date) { checkDue(now: context.date) }
        }
        .allowsHitTesting(false)
        .accessibilityHidden(true)
        .onAppear {
            seen = Set(events.prefix(50).map(\.id))
            checkDue(now: Date())
        }
        .onChange(of: events.first?.id) { checkFamily() }
        .onChange(of: scenePhase) { if scenePhase == .active { checkDue(now: Date()) } }
    }

    /// A log made by another member of the household in the last couple of minutes.
    private func checkFamily() {
        guard var known = seen else { return }
        let me = OpawDefaults.shared.string(forKey: OpawDefaults.caretakerKey) ?? ""
        for event in events.prefix(10) where !known.contains(event.id) {
            known.insert(event.id)
            guard !event.loggedBy.isEmpty, event.loggedBy != me, Date().timeIntervalSince(event.date) < 120 else { continue }
            let detail = event.summary.split(separator: " · ").dropFirst().joined(separator: " · ")
            center.show(Notice(
                kind: .family,
                title: "\(event.loggedBy) logged \(event.kind == .custom ? event.displayTitle.lowercased() : "a \(event.kind.title.lowercased())")",
                detail: detail.isEmpty ? "just now" : "\(detail) · just now",
                image: event.dog?.cutoutData ?? event.dog?.photoData
            ))
        }
        seen = known
    }

    /// The first late meds dose today, for any pet.
    private func checkDue(now: Date) {
        for dog in dogs {
            let done = dog.doneReminderIDs(on: now)
            guard let late = dog.reminders(on: now)
                .filter({ $0.kind == .meds && !done.contains($0.id) && $0.dueDate(on: now) <= now })
                .min(by: { $0.dueDate(on: now) < $1.dueDate(on: now) }) else { continue }
            center.showDue(late, on: now) {
                let event = CareEvent(kind: .meds, note: late.title, dog: dog)
                center.show(CareRecorder.log(event, dog: dog, in: modelContext, toasts: center))
            }
            return
        }
    }
}

// MARK: - Loading and motion (7d, 7f)

/// A grey placeholder bar that shimmers while something loads. Still with Reduce Motion.
struct SkeletonBar: View {
    var width: CGFloat?
    var height: CGFloat = 18
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var phase: CGFloat = -1

    var body: some View {
        RoundedRectangle(cornerRadius: 8, style: .continuous)
            .fill(Color(hex: 0xECECEE))
            .overlay {
                if !reduceMotion {
                    GeometryReader { proxy in
                        LinearGradient(colors: [.clear, Color(hex: 0xF7F7F8), .clear], startPoint: .leading, endPoint: .trailing)
                            .frame(width: proxy.size.width)
                            .offset(x: phase * proxy.size.width)
                    }
                    .clipShape(RoundedRectangle(cornerRadius: 8, style: .continuous))
                }
            }
            .frame(width: width, height: height)
            .onAppear {
                withAnimation(.linear(duration: 1.4).repeatForever(autoreverses: false)) { phase = 1 }
            }
            .accessibilityHidden(true)
    }
}

/// Pop (7d): counts, checks and badges overshoot once in 450 ms when their value changes.
struct PopOnChange<Value: Equatable>: ViewModifier {
    let value: Value
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    func body(content: Content) -> some View {
        if reduceMotion {
            content
        } else {
            content.keyframeAnimator(initialValue: 1.0, trigger: value) { view, scale in
                view.scaleEffect(scale, anchor: UnitPoint(x: 0.5, y: 0.8))
            } keyframes: { _ in
                SpringKeyframe(1.22, duration: 0.15, spring: .snappy)
                SpringKeyframe(1.0, duration: 0.3, spring: .bouncy)
            }
        }
    }
}

extension View {
    func popOnChange<Value: Equatable>(_ value: Value) -> some View {
        modifier(PopOnChange(value: value))
    }

    /// Count (7d): numbers roll to their new value over 600 ms, ease-out.
    func countTransition<Value: Equatable>(_ value: Value) -> some View {
        contentTransition(.numericText()).animation(.easeOut(duration: 0.6), value: value)
    }
}
