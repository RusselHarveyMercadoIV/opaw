import OpawKit
import SwiftData
import SwiftUI
import WidgetKit

/// The "Today" tab: the avatar, one-tap logging and today's progress.
struct HomeView: View {
    @Binding var selectedTab: AppTab

    @Query(sort: \Dog.createdAt) private var dogs: [Dog]
    @Environment(\.modelContext) private var modelContext
    @Environment(\.horizontalSizeClass) private var sizeClass
    @AppStorage(OpawDefaults.activeDogKey, store: OpawDefaults.shared) private var activeDogID = ""
    @State private var showLogSheet = false
    @State private var celebration: Badge?

    private var dog: Dog? {
        dogs.first { $0.id.uuidString == activeDogID } ?? dogs.first
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                if let dog {
                    TimelineView(.periodic(from: .now, by: 60)) { context in
                        let mood = MoodEngine.mood(for: BadgeService.careSnapshot(for: dog, now: context.date), now: context.date)
                        VStack(alignment: .leading, spacing: Space.l) {
                            greeting(dog: dog, now: context.date)
                            content(dog: dog, mood: mood)
                        }
                    }
                    .padding()
                    .frame(maxWidth: 1100)
                    .frame(maxWidth: .infinity)
                }
            }
            .opawBackground()
            .navigationTitle("Today")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                if dogs.count > 1 {
                    ToolbarItem(placement: .topBarLeading) {
                        Menu {
                            ForEach(dogs) { dog in
                                Button(dog.name) {
                                    activeDogID = dog.id.uuidString
                                    WidgetCenter.shared.reloadTimelines(ofKind: OpawWidgetKind.quickLog)
                                }
                            }
                        } label: {
                            Label("Switch dog", systemImage: "arrow.left.arrow.right.circle")
                        }
                    }
                }
                ToolbarItem(placement: .topBarTrailing) {
                    Button {
                        showLogSheet = true
                    } label: {
                        Label("Log", systemImage: "plus.circle.fill")
                    }
                }
            }
            .sheet(isPresented: $showLogSheet) {
                if let dog {
                    CareLogSheet(dog: dog) { awardBadges(for: dog) }
                }
            }
            .alert(item: $celebration) { badge in
                Alert(
                    title: Text("New badge: \(badge.title)"),
                    message: Text(badge.detail),
                    dismissButton: .default(Text("Woof!"))
                )
            }
        }
    }

    @ViewBuilder
    private func content(dog: Dog, mood: AvatarMood) -> some View {
        // Side by side on wide screens such as an unfolded iPhone Duo; stacked on a regular iPhone.
        if sizeClass == .regular {
            HStack(alignment: .top, spacing: 20) {
                avatarCard(dog: dog, mood: mood)
                    .frame(maxWidth: 460)
                VStack(spacing: 16) {
                    quickLogCard(dog: dog)
                    todayCard(dog: dog)
                    recentCard(dog: dog)
                }
            }
        } else {
            VStack(spacing: 16) {
                avatarCard(dog: dog, mood: mood)
                quickLogCard(dog: dog)
                todayCard(dog: dog)
                recentCard(dog: dog)
            }
        }
    }

    private func greeting(dog: Dog, now: Date) -> some View {
        let hour = Calendar.current.component(.hour, from: now)
        let part = hour < 12 ? "Good morning" : hour < 18 ? "Good afternoon" : "Good evening"
        return VStack(alignment: .leading, spacing: 6) {
            Eyebrow(text: now.formatted(.dateTime.weekday(.wide).month(.wide).day()))
            (Text("\(part),\n") + Text(dog.name).foregroundColor(.opawOrange) + Text("."))
                .font(.opawDisplay(34))
                .foregroundStyle(Color.opawInk)
                .fixedSize(horizontal: false, vertical: true)
        }
        .padding(.top, Space.s)
    }

    private func avatarCard(dog: Dog, mood: AvatarMood) -> some View {
        let streak = StreakCalculator.currentStreak(dates: (dog.careEvents ?? []).map(\.date))
        return VStack(spacing: 12) {
            NavigationLink {
                AvatarStudioView(dog: dog)
            } label: {
                ZStack(alignment: .bottom) {
                    Ellipse()
                        .fill(Color.black.opacity(0.06))
                        .frame(width: 180, height: 16)
                        .blur(radius: 6)
                    AvatarView(dog: dog, mood: mood)
                        .frame(height: 300)
                        .padding(.bottom, 6)
                }
                .frame(maxWidth: .infinity)
            }
            .buttonStyle(.plain)
            Text(mood.caption)
                .font(.opawDisplay(22, weight: .regular))
                .italic()
                .foregroundStyle(Color.opawInk)
            if streak > 0 {
                Label("\(streak)-day care streak", systemImage: "flame.fill")
                    .font(.subheadline.weight(.medium))
                    .foregroundStyle(Color.opawOrange)
            }
        }
        .card()
    }

    private func quickLogCard(dog: Dog) -> some View {
        VStack(alignment: .leading, spacing: Space.m) {
            Eyebrow(text: "Quick log")
            HStack(spacing: 0) {
                ForEach(CareKind.quickLog) { kind in
                    Button {
                        quickLog(kind, dog: dog)
                    } label: {
                        VStack(spacing: 6) {
                            CareGlyph(symbol: kind.symbol, size: 52)
                            Text(kind.title)
                                .font(.caption.weight(.medium))
                                .foregroundStyle(Color.opawInk)
                        }
                        .frame(maxWidth: .infinity, minHeight: 76)
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(.pressable)
                    .accessibilityLabel("Log \(kind.title)")
                }
            }
            Button {
                showLogSheet = true
            } label: {
                Label("More: bath, meds, vet, weight…", systemImage: "ellipsis.circle")
                    .font(.subheadline)
            }
        }
        .card()
    }

    private func todayCard(dog: Dog) -> some View {
        let snapshot = BadgeService.careSnapshot(for: dog)
        let goal = Double(max(1, dog.dailyGoalMinutes))
        let start = Calendar.current.startOfDay(for: Date())
        let today = (dog.careEvents ?? []).filter { $0.date >= start }
        func count(_ kind: CareKind) -> String { "\(today.filter { $0.kind == kind }.count)" }

        return VStack(alignment: .leading, spacing: 14) {
            Eyebrow(text: "Today")
            HStack(spacing: 18) {
                ZStack {
                    ProgressRing(progress: snapshot.activityMinutesToday / goal)
                    VStack(spacing: 0) {
                        Text("\(Int(snapshot.activityMinutesToday))")
                            .font(.opawNumber(26))
                        Text("of \(Int(goal)) min")
                            .font(.caption2)
                            .foregroundStyle(.secondary)
                    }
                }
                .frame(width: 96, height: 96)
                Grid(alignment: .leading, horizontalSpacing: 16, verticalSpacing: 10) {
                    GridRow {
                        StatTile(title: "Meals", value: count(.meal), symbol: CareKind.meal.symbol)
                        StatTile(title: "Water", value: count(.water), symbol: CareKind.water.symbol)
                    }
                    GridRow {
                        StatTile(title: "Pee", value: count(.pee), symbol: CareKind.pee.symbol)
                        StatTile(title: "Poop", value: count(.poop), symbol: CareKind.poop.symbol)
                    }
                }
            }
            Button {
                selectedTab = .walk
            } label: {
                Label("Start a walk", systemImage: "figure.walk")
            }
            .buttonStyle(.opawPrimary)
        }
        .card()
    }

    private func recentCard(dog: Dog) -> some View {
        let recent = (dog.careEvents ?? []).sorted { $0.date > $1.date }.prefix(6)
        return VStack(alignment: .leading, spacing: 10) {
            HStack {
                Eyebrow(text: "Recent")
                Spacer()
                NavigationLink("History") {
                    CareHistoryView(dog: dog)
                }
                .font(.subheadline)
            }
            if recent.isEmpty {
                Text("Nothing logged yet today. Tap a button above to start.")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
            } else {
                ForEach(Array(recent)) { event in
                    CareEventRow(event: event)
                }
            }
        }
        .card()
    }

    private func quickLog(_ kind: CareKind, dog: Dog) {
        modelContext.insert(CareEvent(kind: kind, dog: dog))
        try? modelContext.save()
        Haptics.success()
        awardBadges(for: dog)
        WidgetCenter.shared.reloadTimelines(ofKind: OpawWidgetKind.quickLog)
    }

    private func awardBadges(for dog: Dog) {
        if let first = BadgeService.refresh(dog).first {
            celebration = first
        }
        try? modelContext.save()
    }
}

struct CareEventRow: View {
    let event: CareEvent

    var body: some View {
        HStack(spacing: 12) {
            CareGlyph(symbol: event.kind.symbol)
            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                    .font(.subheadline.weight(.medium))
                if !event.note.isEmpty {
                    Text(event.note)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                        .lineLimit(1)
                }
            }
            Spacer()
            Text(event.date, style: .time)
                .font(.caption)
                .foregroundStyle(.secondary)
        }
    }

    private var title: String {
        var text = event.kind.title
        if let consistency = event.poopConsistency {
            text += " · \(consistency.title)"
        }
        if let weight = event.weightKg {
            text += " · \(weight.formatted(.number.precision(.fractionLength(1)))) kg"
        }
        return text
    }
}
