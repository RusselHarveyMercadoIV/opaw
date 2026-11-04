import OpawKit
import SwiftData
import SwiftUI
import WidgetKit

/// Care: one-tap logs with today's counts, the streak, and today's log.
struct CareView: View {
    @Binding var selectedTab: AppTab

    @Query(sort: \Dog.createdAt) private var dogs: [Dog]
    @AppStorage(OpawDefaults.activeDogKey, store: OpawDefaults.shared) private var activeDogID = ""
    @Environment(\.modelContext) private var modelContext
    @State private var showPoop = false
    @State private var detailKind: CareKind?
    @State private var toast: CareToast?
    @State private var celebration: Badge?

    private var dog: Dog? {
        dogs.first { $0.id.uuidString == activeDogID } ?? dogs.first
    }

    /// Log types in the grid, in the design's order.
    static let gridKinds: [CareKind] = [.meal, .water, .pee, .poop, .meds, .bath, .brush, .nails, .weight, .vet, .play]

    var body: some View {
        NavigationStack {
            ZStack(alignment: .bottom) {
                if let dog {
                    TimelineView(.periodic(from: .now, by: 60)) { context in
                        content(dog: dog, now: context.date)
                    }
                }
                if let toast {
                    CareToastView(toast: toast)
                        .padding(.horizontal, Space.l)
                        .padding(.bottom, 96)
                        .transition(.move(edge: .bottom).combined(with: .opacity))
                        .onTapGesture { withAnimation { self.toast = nil } }
                }
                if showPoop, let dog {
                    PoopLogOverlay(dog: dog) { event in
                        if let event { log(event, dog: dog) }
                        withAnimation(.spring(response: 0.45, dampingFraction: 0.9)) { showPoop = false }
                    }
                    .transition(.opacity)
                    .zIndex(2)
                }
            }
            .opawBackground()
            .toolbar(.hidden, for: .navigationBar)
            .sheet(item: $detailKind) { kind in
                if let dog {
                    CareLogSheet(dog: dog, initialKind: kind) { didLog(kind, dog: dog, before: nil) }
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

    private func content(dog: Dog, now: Date) -> some View {
        ScrollView {
            VStack(spacing: Space.l) {
                ScreenHeader(title: "Care") {
                    HStack(spacing: 8) {
                        DogThumbnail(data: dog.cutoutData ?? dog.photoData, size: 36)
                            .overlay(Circle().stroke(Color.opawSurface, lineWidth: 3))
                        Text(dog.name)
                            .font(.opaw(15, .heavy))
                            .lineLimit(1)
                    }
                    .padding(5)
                    .padding(.trailing, 9)
                    .background(Color.opawSurface, in: Capsule())
                    .floatingShadow()
                } trailing: {
                    NavigationLink {
                        ScheduleView(dog: dog)
                    } label: {
                        RoundIcon(symbol: "bell.fill", tint: dog.medsStatus(now: now).overdue ? .opawAlert : .opawInk)
                    }
                    .buttonStyle(.pressable)
                    .accessibilityLabel("Schedule and reminders")
                }

                streakCard(dog: dog)
                    .padding(.horizontal, Space.l)

                grid(dog: dog, now: now)
                    .padding(.horizontal, Space.l)

                todayCard(dog: dog, now: now)
                    .padding(.horizontal, Space.l)
            }
            .padding(.bottom, 100)
        }
        .scrollIndicators(.hidden)
    }

    private func streakCard(dog: Dog) -> some View {
        let streak = dog.careStreak
        let counts = dog.lastSevenDayCounts
        return HStack(spacing: Space.m) {
            VStack(alignment: .leading, spacing: 2) {
                Text(streak == 1 ? "1-day streak" : "\(streak)-day streak")
                    .font(.opaw(22, .black))
                    .foregroundStyle(Color.opawInk)
                Text("Log 3 things a day to keep it going")
                    .font(.opaw(14, .medium))
                    .foregroundStyle(Color.opawMuted)
            }
            Spacer(minLength: 0)
            HStack(alignment: .bottom, spacing: 5) {
                ForEach(Array(counts.enumerated()), id: \.offset) { index, count in
                    RoundedRectangle(cornerRadius: 6, style: .continuous)
                        .fill(count >= 3 ? Color.opawInk : Color.opawChip)
                        .frame(width: 12, height: index == counts.count - 1 ? 30 : 22)
                }
            }
            .accessibilityHidden(true)
        }
        .card(padding: 20)
    }

    private func grid(dog: Dog, now: Date) -> some View {
        let medsLate = dog.medsStatus(now: now).overdue
        return LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 0), count: 4), spacing: 16) {
            ForEach(Self.gridKinds) { kind in
                let count = dog.count(kind, on: now)
                let alert = kind == .meds && medsLate
                Button {
                    tap(kind, dog: dog)
                } label: {
                    VStack(spacing: 6) {
                        ZStack(alignment: .topTrailing) {
                            Image(systemName: kind.symbol)
                                .font(.system(size: 24, weight: .semibold))
                                .foregroundStyle(alert ? Color.opawAlert : Color.opawInk)
                                .frame(width: 68, height: 68)
                                .background(Color.opawSurface, in: Circle())
                                .overlay {
                                    if alert { Circle().strokeBorder(Color.opawAlert, lineWidth: 2.5) }
                                }
                                .shadow(color: .black.opacity(0.06), radius: 12, y: 10)
                            if alert || count > 0 {
                                Text(alert ? "!" : "\(count)")
                                    .font(.opaw(12, .heavy))
                                    .foregroundStyle(.white)
                                    .frame(minWidth: 22, minHeight: 22)
                                    .padding(.horizontal, alert ? 0 : 2)
                                    .background(alert ? Color.opawAlert : Color(hex: 0x111111), in: Capsule())
                                    .overlay(Capsule().stroke(Color.opawCream, lineWidth: 2))
                                    .offset(x: 2, y: -2)
                                    .contentTransition(.numericText())
                            }
                        }
                        Text(kind.shortTitle)
                            .font(.opaw(13, .bold))
                            .foregroundStyle(Color.opawInk)
                            .lineLimit(1)
                    }
                    .frame(maxWidth: .infinity)
                    .contentShape(Rectangle())
                }
                .buttonStyle(.pressable)
                .contextMenu {
                    Button {
                        detailKind = kind
                    } label: {
                        Label("Log with details", systemImage: "square.and.pencil")
                    }
                }
                .accessibilityLabel("Log \(kind.title), \(count) today")
            }
            Button {
                detailKind = .meal
            } label: {
                VStack(spacing: 6) {
                    Image(systemName: "plus")
                        .font(.system(size: 24, weight: .semibold))
                        .foregroundStyle(Color.opawInk)
                        .frame(width: 68, height: 68)
                        .background(Color.opawSurface, in: Circle())
                        .shadow(color: .black.opacity(0.06), radius: 12, y: 10)
                    Text("More")
                        .font(.opaw(13, .bold))
                        .foregroundStyle(Color.opawInk)
                }
                .frame(maxWidth: .infinity)
            }
            .buttonStyle(.pressable)
            .accessibilityLabel("Log with details")
        }
    }

    private func todayCard(dog: Dog, now: Date) -> some View {
        let events = dog.events(on: now).sorted { $0.date > $1.date }
        return VStack(alignment: .leading, spacing: 0) {
            HStack {
                Text("Today's log")
                    .font(.opaw(17, .black))
                Spacer()
                NavigationLink {
                    HistoryView(dog: dog)
                } label: {
                    HStack(spacing: 4) {
                        Text("History")
                        Image(systemName: "chevron.right")
                    }
                    .font(.opaw(14, .bold))
                    .foregroundStyle(Color.opawMuted)
                }
            }
            .padding(.vertical, 12)
            if events.isEmpty {
                Text("Nothing yet. Tap a circle above to log.")
                    .font(.opaw(15, .semibold))
                    .foregroundStyle(Color.opawMuted)
                    .padding(.vertical, 10)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .overlay(alignment: .top) { Divider().overlay(Color.opawHairline) }
            }
            ForEach(events.prefix(8)) { event in
                CareEventRow(event: event)
                    .overlay(alignment: .top) { Rectangle().fill(Color.opawHairline).frame(height: 1) }
            }
        }
        .padding(.horizontal, 20)
        .padding(.vertical, 6)
        .background(Color.opawSurface, in: RoundedRectangle(cornerRadius: Radius.card, style: .continuous))
        .shadow(color: .black.opacity(0.05), radius: 15, y: 12)
    }

    // MARK: Logging

    private func tap(_ kind: CareKind, dog: Dog) {
        switch kind {
        case .poop:
            Haptics.tap()
            withAnimation(.spring(response: 0.45, dampingFraction: 0.85)) { showPoop = true }
        case .weight, .vet:
            detailKind = kind
        default:
            log(CareEvent(kind: kind, dog: dog), dog: dog)
        }
    }

    private func log(_ event: CareEvent, dog: Dog) {
        let before = dog.careReport().score
        modelContext.insert(event)
        try? modelContext.save()
        didLog(event.kind, dog: dog, before: before)
    }

    private func didLog(_ kind: CareKind, dog: Dog, before: Int?) {
        Haptics.success()
        WidgetCenter.shared.reloadTimelines(ofKind: OpawWidgetKind.quickLog)
        if let badge = BadgeService.refresh(dog).first {
            celebration = badge
        }
        try? modelContext.save()
        let after = dog.careReport().score
        let newToast = CareToast(kind: kind, image: dog.cutoutData ?? dog.photoData, before: before ?? after, after: after)
        withAnimation(.spring(response: 0.5, dampingFraction: 0.8)) { toast = newToast }
        Task {
            try? await Task.sleep(for: .seconds(3))
            if toast?.id == newToast.id {
                withAnimation(.easeInOut(duration: 0.35)) { toast = nil }
            }
        }
    }
}

extension CareKind {
    /// A short label that fits under a circle.
    var shortTitle: String {
        self == .vet ? "Vet" : title
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
        }
    }
}

// MARK: - Reaction toast

struct CareToast: Identifiable, Equatable {
    let id = UUID()
    let kind: CareKind
    let image: Data?
    let before: Int
    let after: Int
}

private struct CareToastView: View {
    let toast: CareToast

    var body: some View {
        HStack(spacing: 14) {
            Group {
                if let data = toast.image, let image = UIImage(data: data) {
                    Image(uiImage: image)
                        .resizable()
                        .scaledToFit()
                        .sticker(edge: 3)
                } else {
                    Image(systemName: "dog.fill")
                        .font(.system(size: 34))
                        .foregroundStyle(Color.opawInk)
                }
            }
            .frame(width: 64, height: 64)
            .phaseAnimator([false, true]) { view, up in
                view.offset(y: up ? -4 : 0)
            } animation: { _ in .easeInOut(duration: 0.4) }

            VStack(alignment: .leading, spacing: 3) {
                Text("\u{201C}\(toast.kind.reaction)\u{201D}")
                    .font(.opaw(16, .heavy))
                    .foregroundStyle(Color.opawInk)
                Text("\(toast.kind.title) logged · score \(toast.before) → \(toast.after)")
                    .font(.opaw(13, .semibold))
                    .foregroundStyle(Color.opawMuted)
            }
            Spacer(minLength: 0)
            if toast.after > toast.before {
                Text("+\(toast.after - toast.before)")
                    .font(.opaw(14, .heavy))
                    .foregroundStyle(Color.opawOnInk)
                    .padding(.horizontal, 10)
                    .padding(.vertical, 6)
                    .background(Color.opawInk, in: Capsule())
            }
        }
        .padding(14)
        .background(Color.opawSurface, in: RoundedRectangle(cornerRadius: 28, style: .continuous))
        .shadow(color: .black.opacity(0.14), radius: 20, y: 14)
        .accessibilityElement(children: .combine)
    }
}

// MARK: - Poop log

/// Poop logging over a dimmed screen, with the avatar asking for privacy.
private struct PoopLogOverlay: View {
    let dog: Dog
    let onDone: (CareEvent?) -> Void

    @State private var consistency: PoopConsistency = .normal
    @State private var color: PoopColor = .brown
    @State private var note = ""
    @State private var date = Date()
    @State private var appeared = false

    private var isConcerning: Bool { consistency.isConcerning || color.isConcerning }

    var body: some View {
        ZStack(alignment: .bottom) {
            Color(red: 0.08, green: 0.08, blue: 0.09).opacity(0.38)
                .ignoresSafeArea()
                .onTapGesture { onDone(nil) }

            VStack(spacing: 0) {
                HStack(alignment: .top, spacing: 0) {
                    avatar
                        .frame(width: 130, height: 150)
                    Text("a little privacy, please")
                        .font(.opaw(16, .heavy))
                        .foregroundStyle(Color(hex: 0x111111))
                        .padding(.horizontal, 16)
                        .padding(.vertical, 10)
                        .background(Color.white, in: UnevenRoundedRectangle(topLeadingRadius: 22, bottomLeadingRadius: 6, bottomTrailingRadius: 22, topTrailingRadius: 22))
                        .shadow(color: .black.opacity(0.12), radius: 12, y: 10)
                        .padding(.top, 8)
                    Spacer(minLength: 0)
                }
                .padding(.leading, 30)
                .offset(y: 28)
                .zIndex(1)

                logSheet
            }
            .offset(y: appeared ? 0 : 700)
        }
        .onAppear {
            withAnimation(.spring(response: 0.5, dampingFraction: 0.86)) { appeared = true }
        }
    }

    @ViewBuilder
    private var avatar: some View {
        if let data = dog.cutoutData ?? dog.photoData, let image = UIImage(data: data) {
            Image(uiImage: image)
                .resizable()
                .scaledToFit()
                .sticker()
                .phaseAnimator([false, true]) { view, up in
                    view.scaleEffect(up ? 1.03 : 1, anchor: .bottom)
                } animation: { _ in .easeInOut(duration: 1.3) }
        } else {
            Image(systemName: "dog.fill")
                .font(.system(size: 80))
                .foregroundStyle(.white)
        }
    }

    private var logSheet: some View {
        VStack(alignment: .leading, spacing: 18) {
            Capsule()
                .fill(Color.opawChip)
                .frame(width: 40, height: 5)
                .frame(maxWidth: .infinity)
            HStack {
                Text("Poop")
                    .font(.opaw(30, .black))
                Spacer()
                DatePicker("When", selection: $date, in: ...Date(), displayedComponents: [.hourAndMinute])
                    .labelsHidden()
                    .tint(Color.opawInk)
            }
            VStack(alignment: .leading, spacing: 10) {
                Eyebrow(text: "Consistency")
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 6) {
                        ForEach(PoopConsistency.pickerCases) { option in
                            ChoiceChip(title: option.title, isSelected: option == consistency) {
                                consistency = option
                                Haptics.select()
                            }
                        }
                    }
                }
            }
            VStack(alignment: .leading, spacing: 10) {
                Eyebrow(text: "Color")
                HStack {
                    ForEach(PoopColor.allCases) { option in
                        Button {
                            color = option
                            Haptics.select()
                        } label: {
                            VStack(spacing: 6) {
                                Circle()
                                    .fill(option.swatch)
                                    .frame(width: 44, height: 44)
                                    .overlay(Circle().stroke(Color.black.opacity(0.08), lineWidth: 1))
                                    .padding(option == color ? 3 : 0)
                                    .overlay {
                                        if option == color {
                                            Circle().stroke(Color.opawInk, lineWidth: 2.5)
                                        }
                                    }
                                    .frame(width: 52, height: 52)
                                Text(option.title)
                                    .font(.opaw(12, .semibold))
                                    .foregroundStyle(option == color ? Color.opawInk : Color.opawMuted)
                                    .lineLimit(1)
                                    .fixedSize()
                            }
                            .frame(maxWidth: .infinity)
                        }
                        .buttonStyle(.pressable)
                        .accessibilityLabel(option.title)
                        .accessibilityAddTraits(option == color ? .isSelected : [])
                    }
                }
            }
            if isConcerning {
                Label("Keep an eye on this. If it lasts more than a day, or you see black or red, call your vet.", systemImage: "exclamationmark.triangle.fill")
                    .font(.opaw(14, .semibold))
                    .foregroundStyle(Color.opawAlert)
                    .fixedSize(horizontal: false, vertical: true)
            }
            TextField("Add a note (optional)", text: $note)
                .font(.opaw(15, .medium))
                .padding(.horizontal, 18)
                .padding(.vertical, 14)
                .background(Color.opawChip, in: RoundedRectangle(cornerRadius: 20, style: .continuous))
            Button("Log poop") {
                let event = CareEvent(kind: .poop, date: date, note: note.trimmingCharacters(in: .whitespacesAndNewlines), dog: dog)
                event.poopConsistency = consistency
                event.poopColor = color
                onDone(event)
            }
            .buttonStyle(.opawPrimary)
        }
        .padding(.horizontal, 24)
        .padding(.top, 12)
        .padding(.bottom, 24)
        .background(
            Color.opawSurface,
            in: UnevenRoundedRectangle(topLeadingRadius: Radius.sheet, bottomLeadingRadius: 0, bottomTrailingRadius: 0, topTrailingRadius: Radius.sheet)
        )
        .animation(.easeInOut(duration: 0.2), value: isConcerning)
    }
}

extension PoopColor {
    var swatch: Color {
        switch self {
        case .brown: return Color(hex: 0x7A4A26)
        case .yellow: return Color(hex: 0xC9A45A)
        case .green: return Color(hex: 0x6F7A3A)
        case .black: return Color(hex: 0x2B2422)
        case .redTinge: return Color(hex: 0x9B3A2C)
        }
    }
}
