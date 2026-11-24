import OpawKit
import SwiftData
import SwiftUI
import WidgetKit

/// Day summary (2b): the week strip, six read-only tiles, and the day's log with points. Logging is on
/// Care; hold a log here to delete it.
struct TodayView: View {
    let dog: Dog
    /// On the open display Home shows Today in its right pane (6a), with these buttons beside the title.
    private let embeddedActions: AnyView?

    init(dog: Dog) {
        self.dog = dog
        embeddedActions = nil
    }

    init<Actions: View>(dog: Dog, embedded: Bool, @ViewBuilder actions: () -> Actions) {
        self.dog = dog
        embeddedActions = embedded ? AnyView(actions()) : nil
    }

    private var embedded: Bool { embeddedActions != nil }

    @Environment(\.dismiss) private var dismiss
    @State private var day = Calendar.current.startOfDay(for: Date())
    @State private var pendingDelete: PendingDelete?
    @Environment(ToastCenter.self) private var toasts

    private var calendar: Calendar {
        var calendar = Calendar.current
        calendar.firstWeekday = 2
        return calendar
    }

    private var isToday: Bool { calendar.isDateInToday(day) }

    var body: some View {
        if embedded {
            content
        } else {
            content
                .toolbar(.hidden, for: .navigationBar)
                .hidesFloatingTabBar()
        }
    }

    private var content: some View {
        TimelineView(.periodic(from: .now, by: 60)) { context in
            ScrollView {
                VStack(spacing: 0) {
                    weekStrip(now: context.date)
                        .padding(.horizontal, 20)
                        .padding(.top, embedded ? 14 : 18)
                    if toasts.isOffline {
                        offlineChip
                            .padding(.horizontal, 20)
                            .padding(.top, 14)
                            .transition(.offset(y: 24).combined(with: .opacity))
                    }
                    tiles(now: context.date)
                        .padding(.horizontal, 20)
                        .padding(.top, embedded ? 14 : 16)
                    logHeader
                        .padding(.horizontal, 26)
                        .padding(.top, 22)
                    logCard
                        .padding(.horizontal, 20)
                        .padding(.top, 10)
                }
                .padding(.bottom, embedded ? 24 : 40)
            }
            .scrollIndicators(.hidden)
            .refreshable { await refresh() }
            .animation(.easeOut(duration: 0.32), value: toasts.isOffline)
            .padding(.horizontal, embedded ? -20 : 0)
            .safeAreaInset(edge: .top, spacing: 0) {
                if embedded { embeddedHeader } else { header }
            }
        }
        .background { if !embedded { Color.opawCream.ignoresSafeArea() } }
        .deleteLogConfirmation($pendingDelete)
    }

    private var header: some View {
        HStack {
            Button { dismiss() } label: { RoundIcon(symbol: "chevron.left") }
                .buttonStyle(.pressable)
                .accessibilityLabel("Back")
            Spacer()
            Text(isToday ? "Today" : day.formatted(.dateTime.weekday(.wide)))
                .font(.opaw(24, .black))
                .foregroundStyle(Color.opawInk)
            Spacer()
            NavigationLink {
                HistoryView(dog: dog)
            } label: {
                RoundIcon(symbol: "calendar")
            }
            .buttonStyle(.pressable)
            .accessibilityLabel("History")
        }
        .padding(.horizontal, 20)
        .padding(.top, 10)
        .background(Color.opawCream)
    }

    /// "Today" on the left, then History, share and Settings (6a).
    private var embeddedHeader: some View {
        HStack(spacing: 10) {
            Text(isToday ? "Today" : day.formatted(.dateTime.weekday(.wide)))
                .font(.opaw(26, .black))
                .foregroundStyle(Color.opawInk)
            Spacer()
            NavigationLink {
                HistoryView(dog: dog)
            } label: {
                RoundIcon(symbol: "calendar")
            }
            .buttonStyle(.pressable)
            .accessibilityLabel("History")
            embeddedActions
        }
        .padding(.top, 10)
        .background(Color.opawCream)
    }

    // MARK: Week

    private func weekStrip(now: Date) -> some View {
        let today = calendar.startOfDay(for: now)
        let start = calendar.dateInterval(of: .weekOfYear, for: today)?.start ?? today
        let days = (0..<7).compactMap { calendar.date(byAdding: .day, value: $0, to: start) }
        return HStack(spacing: 4) {
            ForEach(days, id: \.self) { date in
                let selected = calendar.isDate(date, inSameDayAs: day)
                let future = date > today
                Button {
                    Haptics.select()
                    withAnimation(.spring(response: 0.35, dampingFraction: 0.85)) { day = date }
                } label: {
                    VStack(spacing: 0) {
                        Text(String(date.formatted(.dateTime.weekday(.narrow))))
                            .font(.opaw(12, .semibold))
                        Text(date.formatted(.dateTime.day()))
                            .font(.opaw(17, .heavy))
                            .foregroundStyle(selected ? Color.white : (future ? Color.opawGhost : Color.opawInk))
                    }
                    .foregroundStyle(selected ? Color.white : (future ? Color.opawGhost : Color.opawMuted))
                    .frame(maxWidth: .infinity)
                    .frame(height: embedded ? 56 : 64)
                    .background(selected ? Color(hex: 0x111111) : Color.clear, in: Capsule())
                }
                .buttonStyle(.pressable)
                .disabled(future)
                .accessibilityLabel(date.formatted(date: .complete, time: .omitted))
            }
        }
    }

    // MARK: Tiles

    private func tiles(now: Date) -> some View {
        let reference = isToday ? now : (calendar.date(byAdding: .day, value: 1, to: day)?.addingTimeInterval(-1) ?? day)
        let meals = dog.count(.meal, on: day)
        let mealGoal = max(1, dog.scheduledMealHours.count)
        let medsNeeded = dog.reminders(on: day).filter { $0.kind == .meds }.count
        let medsTaken = dog.count(.meds, on: day)
        let due = isToday ? dog.dueKinds(now: now) : []
        let bath = dog.daysSince([.bath], now: reference)
        let groom = dog.daysSince([.brush, .nails], now: reference)

        return LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 10), count: 3), spacing: 10) {
            tile(.meal, value: "\(meals)", suffix: "/\(mealGoal)", label: "Food", alert: due.contains(.meal))
            tile(.pee, value: "\(dog.count(.pee, on: day))", label: "Pee", alert: due.contains(.pee))
            tile(.poop, value: "\(dog.count(.poop, on: day))", label: "Poop", alert: due.contains(.poop))
            tile(.bath, value: bath.map { "\($0)d" } ?? "–", label: "Bath", alert: due.contains(.bath))
            tile(.meds, value: medsNeeded > 0 ? "\(medsTaken)/\(medsNeeded)" : "\(medsTaken)", label: "Meds", alert: due.contains(.meds))
            tile(.brush, value: groom.map { "\($0)d" } ?? "–", label: "Groom", alert: !due.isDisjoint(with: [.brush, .nails]))
        }
    }

    private func tile(_ kind: CareKind, value: String, suffix: String? = nil, label: String, alert: Bool = false) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack(alignment: .firstTextBaseline, spacing: 0) {
                Text(value)
                    .font(.opaw(28, .black))
                    .countTransition(value)
                if let suffix {
                    Text(suffix)
                        .font(.opaw(16, .black))
                        .foregroundStyle(Color.opawMuted)
                }
            }
            .foregroundStyle(alert ? Color.opawAlert : Color.opawInk)
            .lineLimit(1)
            .minimumScaleFactor(0.6)
            Text(label)
                .font(.opaw(15, .bold))
                .foregroundStyle(Color.opawInk)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.top, 16)
        .padding(.horizontal, 14)
        .padding(.bottom, 14)
        .background(Color.opawSurface, in: RoundedRectangle(cornerRadius: 26, style: .continuous))
        .overlay {
            if alert {
                RoundedRectangle(cornerRadius: 26, style: .continuous).strokeBorder(Color.opawAlert, lineWidth: 2)
            }
        }
        .shadow(color: .black.opacity(alert ? 0 : 0.05), radius: 13, y: 10)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("\(label): \(value)\(suffix ?? "")")
    }

    // MARK: Log

    private struct Entry: Identifiable {
        let id: UUID
        let date: Date
        let text: String
        let points: Int
        var isCareLog = false
    }

    private var entries: [Entry] {
        let events = dog.events(on: day).map { Entry(id: $0.id, date: $0.date, text: $0.summary, points: $0.kind.points, isCareLog: true) }
        let end = calendar.date(byAdding: .day, value: 1, to: day) ?? day
        let walks = (dog.walks ?? []).filter { $0.startedAt >= day && $0.startedAt < end }.map {
            Entry(id: $0.id, date: $0.startedAt, text: "\($0.title) · \(OpawFormat.distance(meters: $0.distanceMeters))", points: WalkPoints.perWalk)
        }
        return (events + walks).sorted { $0.date < $1.date }
    }

    private var logCard: some View {
        let rows = entries
        return VStack(spacing: 0) {
            if rows.isEmpty {
                VStack(spacing: 4) {
                    Text(isToday ? "Nothing logged yet" : "Nothing logged this day")
                        .font(.opaw(17, .black))
                        .foregroundStyle(Color.opawInk)
                    if isToday {
                        Text("Log on Care when \(dog.name) eats, pees or poops and it shows up here.")
                            .font(.opaw(14, .medium))
                            .foregroundStyle(Color.opawMuted)
                            .multilineTextAlignment(.center)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                }
                .frame(maxWidth: .infinity)
                .padding(.vertical, 22)
            }
            ForEach(Array(rows.enumerated()), id: \.element.id) { index, entry in
                HStack(spacing: 0) {
                    Text(entry.date.formatted(date: .omitted, time: .shortened))
                        .foregroundStyle(Color.opawFaint)
                        .lineLimit(1)
                        .minimumScaleFactor(0.8)
                        .frame(width: ClockColumn.width(56), alignment: .leading)
                    Text(entry.text)
                        .foregroundStyle(Color.opawInk)
                        .lineLimit(1)
                    Spacer(minLength: 8)
                    Text("+\(entry.points)")
                        .foregroundStyle(Color.opawFaint)
                }
                .font(.opaw(15, .semibold))
                .padding(.vertical, 13)
                .overlay(alignment: .bottom) {
                    if index < rows.count - 1 { Rectangle().fill(Color.opawHairline).frame(height: 1) }
                }
                .contentShape(Rectangle())
                .holdToDelete(
                    entry.isCareLog ? PendingDelete(id: entry.id, time: entry.date.formatted(date: .omitted, time: .shortened), text: entry.text, points: entry.points) : nil,
                    into: $pendingDelete
                )
            }
        }
        .padding(.horizontal, 22)
        .padding(.vertical, 6)
        .whiteCard()
    }

    /// Shown while the phone is offline (7f). Logging keeps working; everything stays on this phone.
    private var offlineChip: some View {
        HStack(spacing: 10) {
            Icon(.cloudOff, size: 18)
            Text("Offline")
                .font(.opaw(14, .bold))
            Text("Logs stay on this phone")
                .font(.opaw(14, .medium))
                .foregroundStyle(Color.opawMuted)
            Spacer(minLength: 0)
        }
        .foregroundStyle(Color.opawInk)
        .padding(.horizontal, 16)
        .frame(height: 44)
        .background(Color.opawSurface, in: Capsule())
        .shadow(color: .black.opacity(0.05), radius: 8, y: 6)
        .accessibilityElement(children: .combine)
    }

    /// Pull to refresh: re-reads badges and the widgets, with a soft haptic as it lets go.
    private func refresh() async {
        Haptics.soft()
        BadgeService.refresh(dog)
        WidgetCenter.shared.reloadAllTimelines()
        try? await Task.sleep(for: .milliseconds(600))
    }

    private var logHeader: some View {
        HStack(alignment: .firstTextBaseline) {
            Text("History")
                .font(.opaw(17, .black))
                .foregroundStyle(Color.opawInk)
            Spacer()
            if !entries.isEmpty {
                Text("Hold a log to delete it")
                    .font(.opaw(13, .semibold))
                    .foregroundStyle(Color.opawMuted)
            }
        }
    }
}
