import OpawKit
import SwiftData
import SwiftUI
import WidgetKit

/// Care (3a): the streak, one-tap log circles, and who's been caring today.
struct CareView: View {
    @Binding var selectedTab: AppTab

    @Query(sort: \Dog.createdAt) private var dogs: [Dog]
    @AppStorage(OpawDefaults.activeDogKey, store: OpawDefaults.shared) private var activeDogID = ""
    @Environment(\.modelContext) private var modelContext
    @State private var showPoop = false
    @State private var detailKind: CareKind?
    @State private var showCustom = false
    @State private var toast: CareToast?
    @State private var celebration: Badge?

    private var dog: Dog? {
        dogs.first { $0.id.uuidString == activeDogID } ?? dogs.first
    }

    /// Log types in the grid, in the design's order. "Custom" follows.
    static let gridKinds: [CareKind] = [.meal, .water, .pee, .poop, .meds, .bath, .brush, .nails, .weight, .vet]

    var body: some View {
        NavigationStack {
            ZStack {
                if let dog {
                    TimelineView(.periodic(from: .now, by: 60)) { context in
                        content(dog: dog, now: context.date)
                    }
                }
                if showPoop, let dog {
                    PoopLogOverlay(dog: dog) { event in
                        if let event { record(event, dog: dog) }
                        withAnimation(.spring(response: 0.45, dampingFraction: 0.9)) { showPoop = false }
                    }
                    .transition(.opacity)
                    .zIndex(2)
                }
            }
            .opawBackground()
            .careToast($toast, bottom: 110)
            .toolbar(.hidden, for: .navigationBar)
            .sheet(item: $detailKind) { kind in
                if let dog {
                    CareLogSheet(dog: dog, initialKind: kind) { }
                }
            }
            .sheet(isPresented: $showCustom) {
                if let dog {
                    CareLogSheet(dog: dog, initialKind: .play) { }
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
            VStack(spacing: 0) {
                HStack {
                    Menu {
                        ForEach(dogs) { item in
                            Button(item.name) {
                                activeDogID = item.id.uuidString
                                WidgetCenter.shared.reloadTimelines(ofKind: OpawWidgetKind.quickLog)
                            }
                        }
                    } label: {
                        PetChip(dog: dog)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    Text("Care")
                        .font(.opaw(22, .black))
                        .foregroundStyle(Color.opawInk)
                    NavigationLink {
                        ScheduleView(dog: dog)
                    } label: {
                        RoundIcon(symbol: "bell.fill")
                    }
                    .buttonStyle(.pressable)
                    .frame(maxWidth: .infinity, alignment: .trailing)
                    .accessibilityLabel("Schedule and reminders")
                }
                .padding(.horizontal, 20)
                .padding(.top, 10)

                streakCard(dog: dog)
                    .padding(.horizontal, 20)
                    .padding(.top, 16)

                grid(dog: dog, now: now)
                    .padding(.horizontal, 20)
                    .padding(.top, 22)

                caringCard(dog: dog, now: now)
                    .padding(.horizontal, 20)
                    .padding(.top, 22)
            }
            .padding(.bottom, 120)
        }
        .scrollIndicators(.hidden)
    }

    private func streakCard(dog: Dog) -> some View {
        let streak = dog.careStreak
        let counts = dog.lastSevenDayCounts
        // Today sits fifth, as in the design: four days before it and two still to come.
        let bars: [Int?] = Array(counts.suffix(5)).map { Optional($0) } + [nil, nil]
        return NavigationLink {
            HistoryView(dog: dog)
        } label: {
            HStack(spacing: 16) {
                VStack(alignment: .leading, spacing: 2) {
                    Text(streak == 1 ? "1-day streak" : "\(streak)-day streak")
                        .font(.opaw(22, .black))
                        .foregroundStyle(Color.opawInk)
                    Text("Log 3 things a day to keep it going")
                        .font(.opaw(14, .medium))
                        .foregroundStyle(Color.opawMuted)
                        .fixedSize(horizontal: false, vertical: true)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                HStack(alignment: .center, spacing: 5) {
                    ForEach(Array(bars.enumerated()), id: \.offset) { index, count in
                        RoundedRectangle(cornerRadius: 6, style: .continuous)
                            .fill((count ?? 0) >= 3 ? Color.opawInk : Color.opawTrack)
                            .frame(width: 12, height: index == 4 ? 30 : 22)
                    }
                }
                .accessibilityHidden(true)
            }
            .padding(.horizontal, 20)
            .padding(.vertical, 16)
            .whiteCard()
        }
        .buttonStyle(.pressable)
    }

    private func grid(dog: Dog, now: Date) -> some View {
        let due = dog.dueKinds(now: now)
        return LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 0), count: 4), spacing: 16) {
            ForEach(Self.gridKinds) { kind in
                let count = dog.count(kind, on: now)
                let alert = due.contains(kind)
                Button {
                    tap(kind, dog: dog)
                } label: {
                    circle(symbol: kind.symbol, label: kind.shortTitle, badge: alert ? "!" : (count > 0 ? "\(count)" : nil), alert: alert)
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
                showCustom = true
            } label: {
                circle(symbol: "plus", label: "Custom", badge: nil, alert: false)
            }
            .buttonStyle(.pressable)
            .accessibilityLabel("Custom log")
        }
    }

    private func circle(symbol: String, label: String, badge: String?, alert: Bool) -> some View {
        VStack(spacing: 6) {
            SymbolIcon(symbol: symbol, size: 26)
                .foregroundStyle(alert ? Color.opawAlert : Color.opawInk)
                .frame(width: 68, height: 68)
                .background(Color.opawSurface, in: Circle())
                .overlay {
                    if alert { Circle().strokeBorder(Color.opawAlert, lineWidth: 2.5) }
                }
                .shadow(color: .black.opacity(0.06), radius: 12, y: 10)
                .overlay(alignment: .topTrailing) {
                    if let badge {
                        Text(badge)
                            .font(.opaw(12, .heavy))
                            .foregroundStyle(.white)
                            .frame(minWidth: 22, minHeight: 22)
                            .background(alert ? Color.opawAlert : Color(hex: 0x111111), in: Capsule())
                            .padding(2)
                            .background(Color.opawCream, in: Capsule())
                            .offset(x: 4, y: -4)
                            .contentTransition(.numericText())
                    }
                }
            Text(label)
                .font(.opaw(13, .bold))
                .foregroundStyle(Color.opawInk)
                .lineLimit(1)
        }
        .frame(maxWidth: .infinity)
        .contentShape(Rectangle())
    }

    private func caringCard(dog: Dog, now: Date) -> some View {
        let events = dog.events(on: now).sorted { $0.date > $1.date }
        let people = events.map { CareRecorder.name($0.loggedBy) }.reduce(into: [String]()) { if !$0.contains($1) { $0.append($1) } }
        return VStack(spacing: 0) {
            HStack {
                Text("Who's caring today")
                    .font(.opaw(17, .black))
                    .foregroundStyle(Color.opawInk)
                Spacer()
                HStack(spacing: -12) {
                    ForEach(people.prefix(3), id: \.self) { name in
                        MiniFace(data: nil, size: 32, rim: 3, fill: Color.opawTrack, initial: String(name.prefix(1)))
                    }
                }
            }
            .padding(.top, 12)
            .padding(.bottom, 8)
            if events.isEmpty {
                Text("Nothing yet. Tap a circle above to log.")
                    .font(.opaw(15, .semibold))
                    .foregroundStyle(Color.opawMuted)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.vertical, 10)
                    .overlay(alignment: .top) { Rectangle().fill(Color.opawHairline).frame(height: 1) }
            }
            ForEach(events.prefix(6)) { event in
                HStack(spacing: 0) {
                    Text(event.date.formatted(date: .omitted, time: .shortened))
                        .foregroundStyle(Color.opawFaint)
                        .frame(width: 52, alignment: .leading)
                    Text(caringLine(event))
                        .foregroundStyle(Color.opawInk)
                        .lineLimit(1)
                    Spacer(minLength: 0)
                }
                .font(.opaw(15, .semibold))
                .padding(.vertical, 10)
                .overlay(alignment: .top) { Rectangle().fill(Color.opawHairline).frame(height: 1) }
            }
        }
        .padding(.horizontal, 20)
        .padding(.vertical, 6)
        .whiteCard()
    }

    private func caringLine(_ event: CareEvent) -> String {
        let who = CareRecorder.name(event.loggedBy)
        let detail = event.summary.split(separator: " · ").dropFirst().joined(separator: " · ")
        let what: String
        switch event.kind {
        case .meal: what = "a meal"
        case .water: what = "water"
        case .pee: what = "a pee"
        case .poop: what = "a poop"
        case .meds: what = "meds"
        case .bath: what = "a bath"
        case .brush: what = "a brush"
        case .nails: what = "a nail trim"
        case .weight: what = "a weigh-in"
        case .vet: what = "a vet visit"
        case .play: what = "play time"
        }
        return detail.isEmpty ? "\(who) logged \(what)" : "\(who) logged \(what) · \(detail)"
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
            record(CareEvent(kind: kind, dog: dog), dog: dog)
        }
    }

    private func record(_ event: CareEvent, dog: Dog) {
        let result = CareRecorder.log(event, dog: dog, in: modelContext)
        withAnimation(.spring(response: 0.5, dampingFraction: 0.8)) { toast = result.toast }
        if let badge = result.badge { celebration = badge }
    }
}

// MARK: - Poop log (3b)

/// Poop logging over a dimmed screen, with the avatar asking for privacy.
struct PoopLogOverlay: View {
    let dog: Dog
    let onDone: (CareEvent?) -> Void

    @State private var consistency: PoopConsistency = .normal
    @State private var color: PoopColor = .brown
    @State private var note = ""
    @State private var date = Date()
    @State private var editTime = false
    @State private var appeared = false

    var body: some View {
        ZStack(alignment: .bottom) {
            Color(red: 20 / 255, green: 20 / 255, blue: 22 / 255).opacity(0.38)
                .ignoresSafeArea()
                .onTapGesture { onDone(nil) }

            ZStack(alignment: .topLeading) {
                avatar
                    .frame(width: 130, height: 150)
                    .padding(.leading, 40)
                    .padding(.top, 14)
                Text("a little privacy, please")
                    .font(.opaw(16, .heavy))
                    .foregroundStyle(Color(hex: 0x111111))
                    .padding(.horizontal, 16)
                    .padding(.vertical, 10)
                    .background(Color.white, in: UnevenRoundedRectangle(topLeadingRadius: 22, bottomLeadingRadius: 6, bottomTrailingRadius: 22, topTrailingRadius: 22))
                    .shadow(color: .black.opacity(0.12), radius: 12, y: 10)
                    .padding(.leading, 170)
                    .padding(.top, 20)
                logSheet
                    .padding(.top, 120)
            }
            .offset(y: appeared ? 0 : 800)
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
                .sticker(edge: 5, blur: 7, y: 10)
                .loopMotion(.breathe, duration: 2.6)
        } else {
            Image(systemName: "dog.fill")
                .font(.system(size: 80))
                .foregroundStyle(.white)
        }
    }

    private var timeLabel: String {
        let time = date.formatted(date: .omitted, time: .shortened)
        return abs(date.timeIntervalSinceNow) < 120 ? "Now · \(time)" : time
    }

    private var logSheet: some View {
        VStack(alignment: .leading, spacing: 18) {
            Capsule()
                .fill(Color(hex: 0xDDDDDD))
                .frame(width: 40, height: 5)
                .frame(maxWidth: .infinity)
            HStack {
                Text("Poop")
                    .font(.opaw(30, .black))
                    .foregroundStyle(Color(hex: 0x111111))
                Spacer()
                Button {
                    withAnimation(.easeInOut(duration: 0.2)) { editTime.toggle() }
                } label: {
                    Text(timeLabel)
                        .font(.opaw(14, .bold))
                        .foregroundStyle(Color(hex: 0x111111))
                        .padding(.horizontal, 14)
                        .padding(.vertical, 9)
                        .background(Color(hex: 0xF1F1F2), in: Capsule())
                }
                .buttonStyle(.pressable)
                .accessibilityLabel("Time: \(timeLabel). Change")
            }
            if editTime {
                DatePicker("When", selection: $date, in: ...Date(), displayedComponents: [.date, .hourAndMinute])
                    .datePickerStyle(.compact)
                    .font(.opaw(15, .bold))
                    .tint(Color(hex: 0x111111))
            }
            VStack(alignment: .leading, spacing: 10) {
                label("Consistency")
                FlowChips(items: PoopConsistency.pickerCases, selection: $consistency)
            }
            VStack(alignment: .leading, spacing: 10) {
                label("Color")
                HStack(spacing: 0) {
                    ForEach(PoopColor.allCases) { option in
                        if option != PoopColor.allCases.first { Spacer(minLength: 0) }
                        swatch(option)
                    }
                }
            }
            TextField("", text: $note, prompt: Text("Add a note (optional)").foregroundStyle(Color(hex: 0xAAAAAA)))
                .font(.opaw(15, .medium))
                .foregroundStyle(Color(hex: 0x111111))
                .padding(.horizontal, 18)
                .padding(.vertical, 14)
                .background(Color(hex: 0xF6F6F7), in: RoundedRectangle(cornerRadius: 20, style: .continuous))
            Button {
                let event = CareEvent(kind: .poop, date: date, note: note.trimmingCharacters(in: .whitespacesAndNewlines), dog: dog)
                event.poopConsistency = consistency
                event.poopColor = color
                onDone(event)
            } label: {
                Text("Log poop")
                    .font(.opaw(17, .heavy))
                    .foregroundStyle(.white)
                    .frame(maxWidth: .infinity)
                    .frame(height: 62)
                    .background(Color(hex: 0x111111), in: Capsule())
            }
            .buttonStyle(.pressable)
        }
        .padding(.horizontal, 24)
        .padding(.top, 12)
        .padding(.bottom, 24)
        .background(
            Color.white
                .clipShape(UnevenRoundedRectangle(topLeadingRadius: 40, bottomLeadingRadius: 0, bottomTrailingRadius: 0, topTrailingRadius: 40))
                .ignoresSafeArea(edges: .bottom)
        )
    }

    private func label(_ text: String) -> some View {
        Text(text)
            .font(.opaw(14, .bold))
            .foregroundStyle(Color(hex: 0x999999))
    }

    private func swatch(_ option: PoopColor) -> some View {
        let selected = option == color
        return Button {
            color = option
            Haptics.select()
        } label: {
            VStack(spacing: 6) {
                Circle()
                    .fill(option.swatch)
                    .overlay(Circle().strokeBorder(Color.black.opacity(selected ? 0 : 0.08), lineWidth: 1))
                    .frame(width: 44, height: 44)
                    .padding(5.5)
                    .overlay {
                        if selected { Circle().strokeBorder(Color(hex: 0x111111), lineWidth: 2.5) }
                    }
                    .frame(width: 44, height: 44)
                Text(option.title)
                    .font(.opaw(12, .semibold))
                    .foregroundStyle(selected ? Color(hex: 0x111111) : Color(hex: 0x999999))
                    .lineLimit(1)
                    .fixedSize()
            }
        }
        .buttonStyle(.pressable)
        .accessibilityLabel(option.title)
        .accessibilityAddTraits(selected ? .isSelected : [])
    }
}

/// Wrapping chips: black when selected, light gray otherwise.
private struct FlowChips: View {
    let items: [PoopConsistency]
    @Binding var selection: PoopConsistency

    var body: some View {
        WrapLayout(spacing: 6) {
            ForEach(items) { option in
                let selected = option == selection
                Button {
                    selection = option
                    Haptics.select()
                } label: {
                    Text(option.title)
                        .font(.opaw(14, .bold))
                        .foregroundStyle(selected ? Color.white : Color(hex: 0x555555))
                        .padding(.horizontal, 14)
                        .padding(.vertical, 10)
                        .background(selected ? Color(hex: 0x111111) : Color(hex: 0xF1F1F2), in: Capsule())
                }
                .buttonStyle(.pressable)
                .accessibilityAddTraits(selected ? .isSelected : [])
            }
        }
    }
}

/// Lays children out in rows, wrapping when a row is full.
struct WrapLayout: Layout {
    var spacing: CGFloat = 6

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let width = proposal.width ?? .infinity
        var x: CGFloat = 0, y: CGFloat = 0, rowHeight: CGFloat = 0, maxX: CGFloat = 0
        for view in subviews {
            let size = view.sizeThatFits(.unspecified)
            if x > 0 && x + size.width > width {
                x = 0
                y += rowHeight + spacing
                rowHeight = 0
            }
            x += size.width + spacing
            maxX = max(maxX, x - spacing)
            rowHeight = max(rowHeight, size.height)
        }
        return CGSize(width: proposal.width ?? maxX, height: y + rowHeight)
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        var x = bounds.minX, y = bounds.minY, rowHeight: CGFloat = 0
        for view in subviews {
            let size = view.sizeThatFits(.unspecified)
            if x > bounds.minX && x + size.width > bounds.maxX {
                x = bounds.minX
                y += rowHeight + spacing
                rowHeight = 0
            }
            view.place(at: CGPoint(x: x, y: y), proposal: ProposedViewSize(size))
            x += size.width + spacing
            rowHeight = max(rowHeight, size.height)
        }
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
