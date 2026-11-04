import OpawKit
import SwiftData
import SwiftUI

/// Today (2b): the week strip, six tiles to log with one tap, the day's log with points, and the reaction toast.
struct TodayView: View {
    let dog: Dog

    @Environment(\.modelContext) private var modelContext
    @Environment(\.dismiss) private var dismiss
    @State private var day = Calendar.current.startOfDay(for: Date())
    @State private var highlight: CareKind?
    @State private var toast: CareToast?
    @State private var showPoop = false
    @State private var celebration: Badge?

    private var calendar: Calendar {
        var calendar = Calendar.current
        calendar.firstWeekday = 2
        return calendar
    }

    private var isToday: Bool { calendar.isDateInToday(day) }

    var body: some View {
        ZStack {
            TimelineView(.periodic(from: .now, by: 60)) { context in
                ScrollView {
                    VStack(spacing: 0) {
                        weekStrip(now: context.date)
                            .padding(.horizontal, 20)
                            .padding(.top, 18)
                        tiles(now: context.date)
                            .padding(.horizontal, 20)
                            .padding(.top, 16)
                        logCard
                            .padding(.horizontal, 20)
                            .padding(.top, 16)
                    }
                    .padding(.bottom, 120)
                }
                .scrollIndicators(.hidden)
                .safeAreaInset(edge: .top, spacing: 0) { header }
            }
            .opawBackground()
            .careToast($toast)

            if showPoop {
                PoopLogOverlay(dog: dog) { event in
                    if let event { record(event) }
                    withAnimation(.spring(response: 0.45, dampingFraction: 0.9)) { showPoop = false }
                }
                .transition(.opacity)
                .zIndex(2)
            }
        }
        .toolbar(.hidden, for: .navigationBar)
        .hidesFloatingTabBar()
        .alert(item: $celebration) { badge in
            Alert(title: Text("New badge: \(badge.title)"), message: Text(badge.detail), dismissButton: .default(Text("Woof!")))
        }
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
                    .frame(height: 64)
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
        let mealGoal = max(1, dog.mealHours.count)
        let medsNeeded = dog.reminders(on: day).filter { $0.kind == .meds }.count
        let medsTaken = dog.count(.meds, on: day)
        let medsAlert = isToday && medsNeeded > 0 && medsTaken < medsNeeded && dog.medsStatus(now: now).next.map { $0 <= now.addingTimeInterval(3 * 3600) } == true
        let bath = dog.daysSince([.bath], now: reference)
        let groom = dog.daysSince([.brush, .nails], now: reference)

        return LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 10), count: 3), spacing: 10) {
            tile(.meal, value: "\(meals)", suffix: "/\(mealGoal)", label: "Food")
            tile(.pee, value: "\(dog.count(.pee, on: day))", label: "Pee")
            tile(.poop, value: "\(dog.count(.poop, on: day))", label: "Poop")
            tile(.bath, value: bath.map { "\($0)d" } ?? "–", label: "Bath")
            tile(.meds, value: medsNeeded > 0 ? "\(medsTaken)/\(medsNeeded)" : "\(medsTaken)", label: "Meds", alert: medsAlert)
            tile(.brush, value: groom.map { "\($0)d" } ?? "–", label: "Groom")
        }
    }

    private func tile(_ kind: CareKind, value: String, suffix: String? = nil, label: String, alert: Bool = false) -> some View {
        let dark = highlight == kind && isToday
        return Button {
            tap(kind)
        } label: {
            VStack(alignment: .leading, spacing: 6) {
                HStack(alignment: .firstTextBaseline, spacing: 0) {
                    Text(value)
                        .font(.opaw(28, .black))
                        .contentTransition(.numericText())
                    if let suffix {
                        Text(suffix)
                            .font(.opaw(16, .black))
                            .foregroundStyle(dark ? Color.white.opacity(0.6) : Color.opawGhost)
                    }
                }
                .foregroundStyle(dark ? Color.white : (alert ? Color.opawAlert : Color.opawInk))
                .lineLimit(1)
                .minimumScaleFactor(0.6)
                Text(dark ? "\(label) ✓" : label)
                    .font(.opaw(15, .bold))
                    .foregroundStyle(dark ? Color.white : Color.opawInk)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.top, 16)
            .padding(.horizontal, 14)
            .padding(.bottom, 14)
            .background(dark ? Color(hex: 0x111111) : Color.opawSurface, in: RoundedRectangle(cornerRadius: 26, style: .continuous))
            .overlay {
                if alert && !dark {
                    RoundedRectangle(cornerRadius: 26, style: .continuous).strokeBorder(Color.opawAlert, lineWidth: 2)
                }
            }
            .shadow(color: .black.opacity(dark ? 0.2 : (alert ? 0 : 0.05)), radius: 13, y: dark ? 12 : 10)
        }
        .buttonStyle(.pressable)
        .disabled(!isToday)
        .accessibilityLabel("\(label): \(value)\(suffix ?? ""). Tap to log.")
    }

    // MARK: Log

    private struct Entry: Identifiable {
        let id: UUID
        let date: Date
        let text: String
        let points: Int
    }

    private var entries: [Entry] {
        let events = dog.events(on: day).map { Entry(id: $0.id, date: $0.date, text: $0.summary, points: $0.kind.points) }
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
                Text(isToday ? "Nothing logged yet. Tap a tile to log it." : "Nothing was logged this day.")
                    .font(.opaw(15, .semibold))
                    .foregroundStyle(Color.opawMuted)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.vertical, 13)
            }
            ForEach(Array(rows.enumerated()), id: \.element.id) { index, entry in
                HStack(spacing: 0) {
                    Text(entry.date.formatted(date: .omitted, time: .shortened))
                        .foregroundStyle(Color.opawFaint)
                        .frame(width: 56, alignment: .leading)
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
            }
        }
        .padding(.horizontal, 22)
        .padding(.vertical, 6)
        .whiteCard()
    }

    // MARK: Actions

    private func tap(_ kind: CareKind) {
        if kind == .poop {
            Haptics.tap()
            withAnimation(.spring(response: 0.45, dampingFraction: 0.85)) { showPoop = true }
        } else {
            record(CareEvent(kind: kind, dog: dog))
        }
    }

    private func record(_ event: CareEvent) {
        let result = CareRecorder.log(event, dog: dog, in: modelContext)
        withAnimation(.spring(response: 0.5, dampingFraction: 0.8)) {
            highlight = event.kind == .brush ? .brush : event.kind
            toast = result.toast
        }
        if let badge = result.badge { celebration = badge }
    }
}
