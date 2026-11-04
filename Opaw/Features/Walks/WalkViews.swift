import MapKit
import OpawKit
import SwiftData
import SwiftUI

/// The Walk tab: Activity (3f), a live walk (2c), and the summary after (3e).
struct WalkTabView: View {
    @Binding var selectedTab: AppTab

    @Environment(WalkRecorder.self) private var recorder
    @Query(sort: \Dog.createdAt) private var dogs: [Dog]
    @AppStorage(OpawDefaults.activeDogKey, store: OpawDefaults.shared) private var activeDogID = ""
    @AppStorage("walkActivity") private var activity = WalkActivity.walk.rawValue
    @State private var finished: IdentifiedWalk?

    private var dog: Dog? {
        dogs.first { $0.id.uuidString == activeDogID } ?? dogs.first
    }

    var body: some View {
        NavigationStack {
            Group {
                if recorder.state == .idle {
                    ActivityView(dog: dog, dogs: dogs, activity: $activity)
                } else {
                    LiveWalkView(dog: dog, activity: WalkActivity(rawValue: activity) ?? .walk) { result in
                        finished = IdentifiedWalk(walk: result)
                    }
                    .transition(.opacity)
                }
            }
            .animation(.easeInOut(duration: 0.3), value: recorder.state == .idle)
            .toolbar(.hidden, for: .navigationBar)
        }
        .fullScreenCover(item: $finished) { item in
            if let dog {
                WalkSummaryView(result: item.walk, dog: dog, activity: WalkActivity(rawValue: activity) ?? .walk)
            }
        }
    }
}

private struct IdentifiedWalk: Identifiable {
    let id = UUID()
    let walk: WalkRecorder.FinishedWalk
}

enum WalkActivity: String, CaseIterable, Identifiable {
    case walk, run, hike

    var id: String { rawValue }

    var title: String {
        switch self {
        case .walk: "Walk"
        case .run: "Run"
        case .hike: "Hike"
        }
    }
}

// MARK: - Activity (3f)

private struct ActivityView: View {
    let dog: Dog?
    let dogs: [Dog]
    @Binding var activity: String

    @Environment(WalkRecorder.self) private var recorder
    @AppStorage(OpawDefaults.activeDogKey, store: OpawDefaults.shared) private var activeDogID = ""
    @State private var showChallenges = false

    private var walks: [Walk] {
        (dog?.walks ?? []).sorted { $0.startedAt > $1.startedAt }
    }

    private var calendar: Calendar {
        var calendar = Calendar.current
        calendar.firstWeekday = 2
        return calendar
    }

    private var week: DateInterval {
        calendar.dateInterval(of: .weekOfYear, for: Date()) ?? DateInterval(start: Date(), duration: 7 * 86_400)
    }

    var body: some View {
        ZStack(alignment: .bottom) {
            ScrollView {
                VStack(spacing: 0) {
                    header
                    if let dog {
                        goalCard(dog: dog)
                            .padding(.horizontal, 20)
                            .padding(.top, 16)
                        barsCard(dog: dog)
                            .padding(.horizontal, 20)
                            .padding(.top, 12)
                        recordsCard
                            .padding(.horizontal, 20)
                            .padding(.top, 12)
                    }
                }
                .padding(.bottom, 200)
            }
            .scrollIndicators(.hidden)

            startControls
                .padding(.horizontal, 20)
                .padding(.bottom, 78)
        }
        .opawBackground()
        .sheet(isPresented: $showChallenges) { ChallengesView() }
        .onAppear { recorder.warmUp() }
        .onDisappear { recorder.coolDown() }
    }

    private var header: some View {
        HStack {
            Menu {
                ForEach(dogs) { item in
                    Button(item.name) { activeDogID = item.id.uuidString }
                }
            } label: {
                PetChip(dog: dog)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            Text("Activity")
                .font(.opaw(22, .black))
                .foregroundStyle(Color.opawInk)
            Button { showChallenges = true } label: {
                RoundIcon(symbol: "trophy")
            }
            .buttonStyle(.pressable)
            .frame(maxWidth: .infinity, alignment: .trailing)
            .accessibilityLabel("Challenges and badges")
        }
        .padding(.horizontal, 20)
        .padding(.top, 10)
    }

    private func goalCard(dog: Dog) -> some View {
        let goal = max(0.5, dog.traits.recommendedWeeklyKm)
        let done = dog.walkedKm(from: week.start, to: week.end)
        return HStack(spacing: 18) {
            GoalRing(progress: done / goal, value: done.formatted(.number.precision(.fractionLength(1))), caption: "of \(goal.formatted(.number.precision(.fractionLength(0...1)))) km")
                .frame(width: 118, height: 118)
            VStack(alignment: .leading, spacing: 4) {
                Text("Weekly goal")
                    .font(.opaw(20, .black))
                    .foregroundStyle(Color.opawInk)
                Text(goalText(dog: dog, goal: goal, done: done))
                    .font(.opaw(14, .medium))
                    .foregroundStyle(Color(hex: 0x888888))
                    .lineSpacing(3)
                    .fixedSize(horizontal: false, vertical: true)
            }
            Spacer(minLength: 0)
        }
        .padding(20)
        .whiteCard()
    }

    private func barsCard(dog: Dog) -> some View {
        let today = calendar.startOfDay(for: Date())
        let days: [(label: String, km: Double, isToday: Bool, isFuture: Bool)] = (0..<7).map { offset in
            let day = calendar.date(byAdding: .day, value: offset, to: week.start) ?? week.start
            return (day.formatted(.dateTime.weekday(.narrow)), dog.walkedKm(on: day), calendar.isDate(day, inSameDayAs: today), day > today)
        }
        let dayMax = max(0.5, days.map(\.km).max() ?? 0.5)
        return VStack(spacing: 8) {
            HStack(alignment: .bottom, spacing: 10) {
                ForEach(Array(days.enumerated()), id: \.offset) { _, day in
                    RoundedRectangle(cornerRadius: 8, style: .continuous)
                        .fill(day.isToday ? Color.opawInk : (day.isFuture || day.km == 0 ? Color.opawTrack : Color(hex: 0xCFCFD2)))
                        .frame(height: day.isFuture || day.km == 0 ? 6 : max(6, 100 * day.km / dayMax))
                        .frame(maxWidth: .infinity)
                }
            }
            .frame(height: 110, alignment: .bottom)
            HStack(spacing: 10) {
                ForEach(Array(days.enumerated()), id: \.offset) { _, day in
                    Text(day.label)
                        .font(.opaw(12, .bold))
                        .foregroundStyle(day.isToday ? Color.opawInk : Color.opawFaint)
                        .frame(maxWidth: .infinity)
                }
            }
        }
        .padding(.horizontal, 20)
        .padding(.top, 18)
        .padding(.bottom, 14)
        .whiteCard()
        .accessibilityHidden(true)
    }

    private func goalText(dog: Dog, goal: Double, done: Double) -> String {
        var who: [String] = []
        if let birth = dog.birthDate {
            let years = Calendar.current.dateComponents([.year], from: birth, to: Date()).year ?? 0
            if years > 0 { who.append("\(years)-year-old") }
        }
        who.append(dog.breed.isEmpty ? "\(dog.size.title.lowercased()) dog" : dog.breed)
        var text = "Set for a \(who.joined(separator: " "))"
        if let weight = dog.weightKg { text += " at \(weight.formatted(.number.precision(.fractionLength(0...1)))) kg" }
        text += "."
        let left = goal - done
        text += left > 0.05 ? " \(left.formatted(.number.precision(.fractionLength(1)))) km to go." : " Goal reached. Good dog!"
        return text
    }

    private var recordsCard: some View {
        let longest = walks.map(\.distanceMeters).max() ?? 0
        let fastest = walks.filter { $0.distanceMeters >= 1000 }.compactMap { $0.stats.paceMinutesPerKm }.min()
        return VStack(alignment: .leading, spacing: 0) {
            Text("Personal records")
                .font(.opaw(17, .black))
                .padding(.top, 12)
                .padding(.bottom, 6)
            recordRow("Longest walk", longest > 0 ? OpawFormat.distance(meters: longest) : "–")
            recordRow("Fastest km", fastest.map { OpawFormat.pace(minutesPerKm: $0) } ?? "–")
        }
        .padding(.horizontal, 20)
        .padding(.vertical, 6)
        .whiteCard()
    }

    private func recordRow(_ label: String, _ value: String) -> some View {
        HStack {
            Text(label).font(.opaw(15, .semibold))
            Spacer()
            Text(value).font(.opaw(15, .black))
        }
        .foregroundStyle(Color.opawInk)
        .padding(.vertical, 11)
        .overlay(alignment: .top) { Rectangle().fill(Color.opawHairline).frame(height: 1) }
    }

    private var startControls: some View {
        HStack(spacing: 10) {
            Button {
                if recorder.isAuthorized {
                    let sticker = (dog?.cutoutData ?? dog?.photoData).flatMap(UIImage.init(data:))
                    PetSticker.save(sticker?.thumbnailPNG(maxSide: 160))
                    recorder.start(dogName: dog?.name ?? "your dog")
                    Haptics.success()
                } else if recorder.isDenied, let url = URL(string: UIApplication.openSettingsURLString) {
                    UIApplication.shared.open(url)
                } else {
                    recorder.requestPermission()
                }
            } label: {
                Text(recorder.isAuthorized ? "Start \((WalkActivity(rawValue: activity) ?? .walk).title.lowercased())" : (recorder.isDenied ? "Turn on location in Settings" : "Allow location to start"))
                    .font(.opaw(17, .heavy))
                    .foregroundStyle(.white)
                    .lineLimit(1)
                    .minimumScaleFactor(0.8)
                    .frame(maxWidth: .infinity)
                    .frame(height: 62)
                    .background(Color(hex: 0x111111), in: Capsule())
            }
            .buttonStyle(.pressable)
            Menu {
                Picker("Activity", selection: $activity) {
                    ForEach(WalkActivity.allCases) { Text($0.title).tag($0.rawValue) }
                }
            } label: {
                Text("\((WalkActivity(rawValue: activity) ?? .walk).title) ▾")
                    .font(.opaw(15, .heavy))
                    .foregroundStyle(Color.opawInk)
                    .padding(.horizontal, 20)
                    .frame(height: 62)
                    .background(Color.opawSurface, in: Capsule())
                    .shadow(color: .black.opacity(0.07), radius: 12, y: 10)
            }
            .accessibilityLabel("Activity type")
        }
    }
}

/// A thick goal ring with the number in the middle (118pt, 13pt stroke on #eee).
struct GoalRing: View {
    let progress: Double
    let value: String
    let caption: String

    var body: some View {
        GeometryReader { proxy in
            let line = proxy.size.width * 13 / 120
            ZStack {
                Circle()
                    .stroke(Color.opawHairline, lineWidth: line)
                    .padding(line / 2 + proxy.size.width * 4 / 120)
                Circle()
                    .trim(from: 0, to: min(1, max(0, progress)))
                    .stroke(Color.opawInk, style: StrokeStyle(lineWidth: line, lineCap: .round))
                    .rotationEffect(.degrees(-90))
                    .padding(line / 2 + proxy.size.width * 4 / 120)
                    .animation(.spring(response: 0.8, dampingFraction: 0.8), value: progress)
                VStack(spacing: 0) {
                    Text(value)
                        .font(.opaw(26, .black))
                        .foregroundStyle(Color.opawInk)
                    Text(caption)
                        .font(.opaw(12, .semibold))
                        .foregroundStyle(Color.opawMuted)
                }
            }
        }
    }
}

extension Walk {
    /// "Morning walk", "Evening loop"… from when it started.
    var title: String { Walk.title(for: startedAt, activity: WalkActivity(rawValue: activityRaw) ?? .walk) }

    static func title(for date: Date, activity: WalkActivity = .walk) -> String {
        let hour = Calendar.current.component(.hour, from: date)
        switch activity {
        case .walk:
            switch hour {
            case 5..<11: return "Morning walk"
            case 11..<14: return "Midday stroll"
            case 14..<17: return "Afternoon walk"
            case 17..<21: return "Evening loop"
            default: return "Night stroll"
            }
        case .run, .hike:
            let part: String
            switch hour {
            case 5..<12: part = "Morning"
            case 12..<17: part = "Afternoon"
            case 17..<21: part = "Evening"
            default: part = "Night"
            }
            return "\(part) \(activity.title.lowercased())"
        }
    }

    /// Times the dog stopped for at least 15 seconds within a few metres: sniff stops.
    static func sniffStops(in points: [GeoPoint]) -> Int {
        guard var anchor = points.first else { return 0 }
        var stops = 0
        var counted = false
        for point in points.dropFirst() {
            let distance = Geo.distance(anchor, point)
            if distance > 8 {
                anchor = point
                counted = false
            } else if !counted && point.timestamp.timeIntervalSince(anchor.timestamp) >= 15 {
                stops += 1
                counted = true
            }
        }
        return stops
    }
}

// MARK: - Live walk (2c)

private struct LiveWalkView: View {
    let dog: Dog?
    let activity: WalkActivity
    let onFinish: (WalkRecorder.FinishedWalk) -> Void
    @Environment(WalkRecorder.self) private var recorder
    @State private var camera: MapCameraPosition = .userLocation(followsHeading: false, fallback: .automatic)
    @State private var confirmStop = false

    var body: some View {
        ZStack(alignment: .bottom) {
            Map(position: $camera) {
                MapPolyline(coordinates: recorder.points.map(\.coordinate))
                    .stroke(Color(hex: 0x111111), style: StrokeStyle(lineWidth: 7, lineCap: .round, lineJoin: .round))
                if let first = recorder.points.first {
                    Annotation("", coordinate: first.coordinate) { StartDot() }
                }
                ForEach(recorder.pins) { pin in
                    Annotation("", coordinate: CLLocationCoordinate2D(latitude: pin.latitude, longitude: pin.longitude)) {
                        PinBadge(kind: pin.kind)
                    }
                }
                if let here = recorder.lastLocation?.coordinate ?? recorder.points.last?.coordinate {
                    Annotation("", coordinate: here, anchor: .bottom) { petMarker }
                } else {
                    UserAnnotation()
                }
            }
            .mapStyle(.standard(pointsOfInterest: .excludingAll))
            .ignoresSafeArea()
            .safeAreaPadding(.bottom, 300)

            sheet
        }
        .overlay(alignment: .topLeading) {
            HStack(spacing: 8) {
                if recorder.state == .paused {
                    Circle().fill(Color.opawMuted).frame(width: 9, height: 9)
                } else {
                    PulseDot()
                }
                Text(recorder.state == .paused ? "Paused" : Walk.title(for: recorder.startedAt ?? Date(), activity: activity))
                    .font(.opaw(15, .heavy))
                    .foregroundStyle(Color(hex: 0x111111))
            }
            .padding(.horizontal, 18)
            .padding(.vertical, 12)
            .background(Color.white, in: Capsule())
            .shadow(color: .black.opacity(0.08), radius: 13, y: 10)
            .padding(.leading, 20)
            .padding(.top, 10)
        }
        .confirmationDialog("Finish this walk?", isPresented: $confirmStop, titleVisibility: .visible) {
            Button("Finish and save") {
                if let result = recorder.finish() { onFinish(result) }
            }
            Button("Discard walk", role: .destructive) { recorder.cancel() }
        }
    }

    /// The pet trotting along the route. Tap it to mark a pee or poop here.
    private var petMarker: some View {
        Menu {
            Button {
                recorder.dropPin(.pee)
                Haptics.tap()
            } label: { Label("Pee here", systemImage: CareKind.pee.symbol) }
            Button {
                recorder.dropPin(.poop)
                Haptics.tap()
            } label: { Label("Poop here", systemImage: CareKind.poop.symbol) }
        } label: {
            Group {
                if let data = dog?.cutoutData ?? dog?.photoData, let image = UIImage(data: data) {
                    Image(uiImage: image)
                        .resizable()
                        .scaledToFit()
                } else {
                    Image(systemName: "dog.fill")
                        .resizable()
                        .scaledToFit()
                        .foregroundStyle(Color(hex: 0x111111))
                }
            }
            .frame(width: 96, height: 96)
            .sticker(edge: 4, blur: 6, y: 10, opacity: 0.2)
            .loopMotion(.trot)
        }
        .accessibilityLabel("Mark a pee or poop here")
    }

    private var sheet: some View {
        TimelineView(.periodic(from: .now, by: 1)) { context in
            VStack(spacing: 0) {
                Text((recorder.stats.distanceMeters / 1000).formatted(.number.precision(.fractionLength(2))))
                    .font(.opaw(80, .black))
                    .tracking(-2.4)
                    .monospacedDigit()
                    .foregroundStyle(Color(hex: 0x111111))
                    .contentTransition(.numericText())
                    .frame(height: 72)
                Text("kilometres")
                    .font(.opaw(16, .semibold))
                    .foregroundStyle(Color(hex: 0x999999))
                    .padding(.top, 6)
                HStack(spacing: 0) {
                    StatColumn(value: OpawFormat.duration(recorder.elapsed(at: context.date)), label: "Time")
                    StatColumn(value: OpawFormat.pace(minutesPerKm: recorder.stats.paceMinutesPerKm), label: "Pace /km")
                    StatColumn(value: "\(Walk.sniffStops(in: recorder.points))", label: "Sniff stops")
                }
                .environment(\.colorScheme, .light)
                .padding(.top, 22)
                HStack(spacing: 12) {
                    Button {
                        if recorder.state == .paused { recorder.resume() } else { recorder.pause() }
                        Haptics.tap()
                    } label: {
                        Image(systemName: recorder.state == .paused ? "play.fill" : "pause.fill")
                            .font(.system(size: 20, weight: .bold))
                            .foregroundStyle(Color(hex: 0x111111))
                            .frame(width: 64, height: 64)
                            .background(Color(hex: 0xF1F1F2), in: Circle())
                    }
                    .buttonStyle(.pressable)
                    .accessibilityLabel(recorder.state == .paused ? "Resume" : "Pause")
                    Button { confirmStop = true } label: {
                        Text("Finish walk")
                            .font(.opaw(18, .bold))
                            .foregroundStyle(.white)
                            .frame(maxWidth: .infinity)
                            .frame(height: 64)
                            .background(Color(hex: 0x111111), in: Capsule())
                    }
                    .buttonStyle(.pressable)
                }
                .padding(.top, 26)
                Spacer(minLength: 0)
            }
            .padding(.horizontal, 24)
            .padding(.top, 28)
            .frame(height: 330)
            .frame(maxWidth: .infinity)
            .background(
                Color.white
                    .clipShape(UnevenRoundedRectangle(topLeadingRadius: 40, bottomLeadingRadius: 0, bottomTrailingRadius: 0, topTrailingRadius: 40))
                    .shadow(color: .black.opacity(0.06), radius: 15, y: -12)
                    .ignoresSafeArea(edges: .bottom)
            )
        }
    }
}

/// The white start circle with a thick black ring.
private struct StartDot: View {
    var body: some View {
        Circle()
            .fill(Color.white)
            .frame(width: 18, height: 18)
            .overlay(Circle().stroke(Color(hex: 0x111111), lineWidth: 4))
    }
}

/// A pee (white) or poop (black) marker on a map.
struct PinBadge: View {
    let kind: String

    var body: some View {
        let isPoop = kind == CareKind.poop.rawValue
        Image(systemName: isPoop ? CareKind.poop.symbol : CareKind.pee.symbol)
            .font(.system(size: 15, weight: .bold))
            .foregroundStyle(isPoop ? Color.white : Color(hex: 0x111111))
            .frame(width: 34, height: 34)
            .background(isPoop ? Color(hex: 0x111111) : Color.white, in: Circle())
            .shadow(color: .black.opacity(0.18), radius: 7, y: 6)
            .accessibilityLabel(isPoop ? "Poop" : "Pee")
    }
}

// MARK: - Summary (3e)

/// Shown after a walk ends. The walk is saved straight away; back closes, share shares.
private struct WalkSummaryView: View {
    let result: WalkRecorder.FinishedWalk
    let dog: Dog
    let activity: WalkActivity

    @Environment(\.modelContext) private var modelContext
    @Environment(\.dismiss) private var dismiss
    @State private var saved: Walk?
    @State private var isRecord = false

    var body: some View {
        WalkStoryView(
            points: result.points,
            pins: result.pins,
            title: Walk.title(for: result.startedAt, activity: activity),
            subtitle: WalkStoryView.subtitle(start: result.startedAt, dog: dog),
            stats: WalkCalculator.stats(for: result.points),
            isRecord: isRecord,
            dog: dog,
            extraKm: saved == nil ? WalkCalculator.stats(for: result.points).distanceMeters / 1000 : 0,
            onBack: { dismiss() }
        )
        .onAppear(perform: save)
    }

    private func save() {
        guard saved == nil else { return }
        let stats = WalkCalculator.stats(for: result.points)
        let monthStart = Calendar.current.dateInterval(of: .month, for: Date())?.start ?? Date()
        let best = (dog.walks ?? []).filter { $0.startedAt >= monthStart }.map(\.distanceMeters).max() ?? 0
        isRecord = stats.distanceMeters > 200 && stats.distanceMeters > best

        let walk = Walk(startedAt: result.startedAt, endedAt: result.endedAt, route: result.points, pins: result.pins, dog: dog)
        walk.activityRaw = activity.rawValue
        modelContext.insert(walk)
        // Pins dropped on the walk count as care logs too.
        for pin in result.pins {
            if let kind = CareKind(rawValue: pin.kind) {
                modelContext.insert(CareEvent(kind: kind, date: pin.date, note: "On a walk", dog: dog))
            }
        }
        BadgeService.refresh(dog)
        try? modelContext.save()
        saved = walk
        Haptics.success()
    }
}

/// A past walk, opened from Memories.
struct WalkDetailView: View {
    let walk: Walk
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        WalkStoryView(
            points: walk.route,
            pins: walk.pins,
            title: walk.title,
            subtitle: walk.dog.map { WalkStoryView.subtitle(start: walk.startedAt, dog: $0) } ?? walk.startedAt.formatted(date: .abbreviated, time: .shortened),
            stats: walk.stats,
            isRecord: false,
            dog: walk.dog,
            extraKm: 0,
            onBack: { dismiss() }
        )
        .toolbar(.hidden, for: .navigationBar)
        .hidesFloatingTabBar()
    }
}

/// The 3e layout: route map, back and share, then the sheet with the proud pet, stats and weekly progress.
private struct WalkStoryView: View {
    let points: [GeoPoint]
    let pins: [WalkPin]
    let title: String
    let subtitle: String
    let stats: WalkStats
    let isRecord: Bool
    let dog: Dog?
    let extraKm: Double
    let onBack: () -> Void

    static func subtitle(start: Date, dog: Dog) -> String {
        let day = Calendar.current.isDateInToday(start) ? "Today" : start.formatted(.dateTime.weekday(.abbreviated).day().month(.abbreviated))
        let walker = OpawDefaults.shared.string(forKey: OpawDefaults.caretakerKey) ?? ""
        let with = walker.isEmpty ? dog.name : "\(dog.name) and \(walker)"
        return "\(day) \(start.formatted(date: .omitted, time: .shortened)) · with \(with)"
    }

    private var peeCount: Int { pins.filter { $0.kind == CareKind.pee.rawValue }.count }
    private var poopCount: Int { pins.filter { $0.kind == CareKind.poop.rawValue }.count }

    var body: some View {
        ScrollView {
            VStack(spacing: 0) {
                RouteMap(points: points, pins: pins)
                    .frame(height: 400)
                    .overlay(alignment: .top) {
                        HStack {
                            Button(action: onBack) { RoundIcon(symbol: "chevron.left") }
                                .buttonStyle(.pressable)
                                .accessibilityLabel("Back")
                            Spacer()
                            ShareLink(item: shareText) { RoundIcon(symbol: "square.and.arrow.up") }
                                .buttonStyle(.pressable)
                                .accessibilityLabel("Share walk")
                        }
                        .padding(.horizontal, 14)
                        .padding(.top, 62)
                    }
                sheet
                    .padding(.top, -30)
            }
        }
        .scrollIndicators(.hidden)
        .background(Color.opawCream)
        .ignoresSafeArea(edges: .top)
    }

    private var sheet: some View {
        VStack(alignment: .leading, spacing: 0) {
            if isRecord {
                HStack(spacing: 6) {
                    Image(systemName: "sparkle")
                        .font(.system(size: 12, weight: .black))
                    Text("Longest walk this month")
                        .font(.opaw(13, .heavy))
                }
                .foregroundStyle(.white)
                .padding(.horizontal, 12)
                .padding(.vertical, 7)
                .background(Color(hex: 0x111111), in: Capsule())
                .padding(.bottom, 10)
            }
            Text(title)
                .font(.opaw(30, .black))
                .foregroundStyle(Color.opawInk)
            Text(subtitle)
                .font(.opaw(15, .medium))
                .foregroundStyle(Color.opawMuted)
                .padding(.trailing, 120)
            statsGrid
                .padding(.top, 16)
            if let dog { progress(dog: dog).padding(.top, 12) }
        }
        .padding(.horizontal, 20)
        .padding(.top, 22)
        .padding(.bottom, 40)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(
            Color.opawCream
                .clipShape(UnevenRoundedRectangle(topLeadingRadius: 36, bottomLeadingRadius: 0, bottomTrailingRadius: 0, topTrailingRadius: 36))
        )
        .overlay(alignment: .topTrailing) {
            if let data = dog?.cutoutData ?? dog?.photoData, let image = UIImage(data: data) {
                Image(uiImage: image)
                    .resizable()
                    .scaledToFit()
                    .frame(width: 110, height: 120)
                    .sticker(edge: 5, blur: 7, y: 10)
                    .loopMotion(.bob, duration: 1.2)
                    .padding(.trailing, 18)
                    .offset(y: -62)
            }
        }
    }

    private var statsGrid: some View {
        VStack(spacing: 0) {
            HStack(spacing: 0) {
                cell(OpawFormat.distance(meters: stats.distanceMeters), label: "Distance")
                Rectangle().fill(Color.opawHairline).frame(width: 1)
                cell(OpawFormat.duration(stats.elapsedSeconds), label: "Duration")
            }
            Rectangle().fill(Color.opawHairline).frame(height: 1)
            HStack(spacing: 0) {
                cell(OpawFormat.pace(minutesPerKm: stats.paceMinutesPerKm), label: "Pace /km")
                Rectangle().fill(Color.opawHairline).frame(width: 1)
                cell("\(peeCount) · \(poopCount)", label: "Pee · poop")
            }
        }
        .fixedSize(horizontal: false, vertical: true)
        .padding(.vertical, 6)
        .whiteCard()
    }

    /// A value such as "2.41 km" with the unit drawn small and gray.
    private func cell(_ value: String, label: String) -> some View {
        let parts = value.split(separator: " ", maxSplits: 1).map(String.init)
        let unit = parts.count == 2 && parts[1].allSatisfy(\.isLetter) ? parts[1] : nil
        return VStack(alignment: .leading, spacing: 0) {
            Group {
                if let unit {
                    Text(parts[0]) + Text(" \(unit)").font(.opaw(14, .black)).foregroundColor(Color.opawMuted)
                } else {
                    Text(value)
                }
            }
            .font(.opaw(26, .black))
            .foregroundStyle(Color.opawInk)
            .lineLimit(1)
            .minimumScaleFactor(0.6)
            Text(label)
                .font(.opaw(13, .semibold))
                .foregroundStyle(Color.opawMuted)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.horizontal, 20)
        .padding(.vertical, 12)
    }

    private func progress(dog: Dog) -> some View {
        var calendar = Calendar.current
        calendar.firstWeekday = 2
        let interval = calendar.dateInterval(of: .weekOfYear, for: Date()) ?? DateInterval(start: Date(), duration: 1)
        let total = dog.walkedKm(from: interval.start, to: interval.end) + extraKm
        let goal = max(0.5, dog.traits.recommendedWeeklyKm)
        return HStack(spacing: 10) {
            GeometryReader { proxy in
                ZStack(alignment: .leading) {
                    Capsule().fill(Color.opawTrack)
                    Capsule().fill(Color.opawInk)
                        .frame(width: proxy.size.width * min(1, total / goal))
                }
            }
            .frame(height: 10)
            Text("\(total.formatted(.number.precision(.fractionLength(1)))) / \(goal.formatted(.number.precision(.fractionLength(0...1)))) km this week")
                .font(.opaw(15, .bold))
                .foregroundStyle(Color.opawInk)
                .fixedSize()
        }
    }

    private var shareText: String {
        "\(title): \(OpawFormat.distance(meters: stats.distanceMeters)) in \(OpawFormat.duration(stats.elapsedSeconds)) with \(dog?.name ?? "my dog"). Logged with Opaw."
    }
}

/// A static map of a recorded route with its pee and poop pins.
struct RouteMap: View {
    let points: [GeoPoint]
    let pins: [WalkPin]

    var body: some View {
        Map(initialPosition: .automatic) {
            MapPolyline(coordinates: points.map(\.coordinate))
                .stroke(Color(hex: 0x111111), style: StrokeStyle(lineWidth: 7, lineCap: .round, lineJoin: .round))
            ForEach(pins) { pin in
                Annotation("", coordinate: CLLocationCoordinate2D(latitude: pin.latitude, longitude: pin.longitude)) {
                    PinBadge(kind: pin.kind)
                }
            }
            if let first = points.first {
                Annotation("", coordinate: first.coordinate) { StartDot() }
            }
        }
        .mapStyle(.standard(pointsOfInterest: .excludingAll))
        .allowsHitTesting(false)
    }
}

extension GeoPoint {
    var coordinate: CLLocationCoordinate2D {
        CLLocationCoordinate2D(latitude: latitude, longitude: longitude)
    }
}
