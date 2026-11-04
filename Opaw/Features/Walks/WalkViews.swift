import MapKit
import OpawKit
import SwiftData
import SwiftUI

/// The Walk tab: the weekly goal and records, a live walk, and the summary after.
struct WalkTabView: View {
    @Binding var selectedTab: AppTab

    @Environment(WalkRecorder.self) private var recorder
    @Query(sort: \Dog.createdAt) private var dogs: [Dog]
    @AppStorage(OpawDefaults.activeDogKey, store: OpawDefaults.shared) private var activeDogID = ""
    @State private var finished: IdentifiedWalk?

    private var dog: Dog? {
        dogs.first { $0.id.uuidString == activeDogID } ?? dogs.first
    }

    var body: some View {
        NavigationStack {
            Group {
                if recorder.state == .idle {
                    ActivityView(dog: dog)
                } else {
                    LiveWalkView(dog: dog) { result in
                        finished = IdentifiedWalk(walk: result)
                    }
                    .transition(.opacity)
                }
            }
            .animation(.easeInOut(duration: 0.3), value: recorder.state == .idle)
            .toolbar(.hidden, for: .navigationBar)
        }
        .sheet(item: $finished) { item in
            if let dog {
                WalkSummarySheet(result: item.walk, dog: dog)
            }
        }
    }
}

private struct IdentifiedWalk: Identifiable {
    let id = UUID()
    let walk: WalkRecorder.FinishedWalk
}

// MARK: - Activity

private struct ActivityView: View {
    let dog: Dog?
    @Environment(WalkRecorder.self) private var recorder

    private var walks: [Walk] {
        (dog?.walks ?? []).sorted { $0.startedAt > $1.startedAt }
    }

    private var week: DateInterval {
        Calendar.current.dateInterval(of: .weekOfYear, for: Date()) ?? DateInterval(start: Date(), duration: 7 * 86_400)
    }

    var body: some View {
        ZStack(alignment: .bottom) {
            ScrollView {
                VStack(spacing: 14) {
                    ScreenHeader(title: "Activity") {
                        if let dog {
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
                        }
                    } trailing: {
                        EmptyView()
                    }
                    if let dog {
                        goalCard(dog: dog)
                            .padding(.horizontal, Space.l)
                            .padding(.top, 6)
                        recordsCard
                            .padding(.horizontal, Space.l)
                        walksCard
                            .padding(.horizontal, Space.l)
                    }
                }
                .padding(.bottom, 180)
            }
            .scrollIndicators(.hidden)

            startControls
                .padding(.horizontal, Space.l)
                .padding(.bottom, 92)
        }
        .opawBackground()
        .onAppear { recorder.warmUp() }
        .onDisappear { recorder.coolDown() }
    }

    private func goalCard(dog: Dog) -> some View {
        let goal = max(0.5, dog.traits.recommendedWeeklyKm)
        let done = dog.walkedKm(from: week.start, to: week.end)
        let calendar = Calendar.current
        let days: [(label: String, km: Double, isToday: Bool)] = (0..<7).map { offset in
            let day = calendar.date(byAdding: .day, value: offset, to: week.start) ?? week.start
            let label = day.formatted(.dateTime.weekday(.narrow))
            return (label, dog.walkedKm(on: day), calendar.isDateInToday(day))
        }
        let dayMax = max(0.5, days.map(\.km).max() ?? 0.5)
        return VStack(alignment: .leading, spacing: 18) {
            HStack(spacing: 18) {
                ZStack {
                    ProgressRing(progress: done / goal, lineWidth: 12)
                    VStack(spacing: 0) {
                        Text(done.formatted(.number.precision(.fractionLength(1))))
                            .font(.opawNumber(26))
                        Text("of \(goal.formatted(.number.precision(.fractionLength(0...1)))) km")
                            .font(.opaw(12, .bold))
                            .foregroundStyle(Color.opawMuted)
                    }
                }
                .frame(width: 108, height: 108)
                VStack(alignment: .leading, spacing: 4) {
                    Text("Weekly goal")
                        .font(.opaw(20, .black))
                    Text(goalText(dog: dog, goal: goal, done: done))
                        .font(.opaw(14, .semibold))
                        .foregroundStyle(Color.opawMuted)
                        .fixedSize(horizontal: false, vertical: true)
                }
            }
            HStack(alignment: .bottom, spacing: 8) {
                ForEach(Array(days.enumerated()), id: \.offset) { _, day in
                    VStack(spacing: 6) {
                        RoundedRectangle(cornerRadius: 7, style: .continuous)
                            .fill(day.km > 0 ? Color.opawInk : Color.opawChip)
                            .frame(height: max(8, 64 * day.km / dayMax))
                        Text(day.label)
                            .font(.opaw(12, .bold))
                            .foregroundStyle(day.isToday ? Color.opawInk : Color.opawMuted)
                    }
                    .frame(maxWidth: .infinity)
                }
            }
            .frame(height: 86, alignment: .bottom)
            .accessibilityHidden(true)
        }
        .card(padding: 20)
    }

    private func goalText(dog: Dog, goal: Double, done: Double) -> String {
        var who: [String] = []
        if let age = dog.ageDescription { who.append(age.replacingOccurrences(of: " old", with: "-old").replacingOccurrences(of: " ", with: "-")) }
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
        let monthStart = Calendar.current.dateInterval(of: .month, for: Date())?.start ?? Date()
        let month = walks.filter { $0.startedAt >= monthStart }.reduce(0) { $0 + $1.distanceMeters }
        return VStack(alignment: .leading, spacing: 12) {
            Text("Personal records")
                .font(.opaw(17, .black))
            HStack(spacing: 10) {
                RecordTile(value: longest > 0 ? OpawFormat.distance(meters: longest) : "—", label: "Longest walk")
                RecordTile(value: fastest.map { OpawFormat.pace(minutesPerKm: $0) } ?? "—", label: "Fastest km")
                RecordTile(value: OpawFormat.distance(meters: month), label: "This month")
            }
        }
        .card(padding: 20)
    }

    private var walksCard: some View {
        VStack(alignment: .leading, spacing: 0) {
            Text("Recent walks")
                .font(.opaw(17, .black))
                .padding(.vertical, 12)
            if walks.isEmpty {
                Text("Your walks will show up here.")
                    .font(.opaw(15, .semibold))
                    .foregroundStyle(Color.opawMuted)
                    .padding(.bottom, 14)
            }
            ForEach(walks.prefix(10)) { walk in
                NavigationLink {
                    WalkDetailView(walk: walk)
                } label: {
                    WalkRow(walk: walk)
                }
                .buttonStyle(.plain)
                .overlay(alignment: .top) { Rectangle().fill(Color.opawHairline).frame(height: 1) }
            }
        }
        .padding(.horizontal, 20)
        .padding(.vertical, 4)
        .background(Color.opawSurface, in: RoundedRectangle(cornerRadius: Radius.card, style: .continuous))
        .shadow(color: .black.opacity(0.05), radius: 15, y: 12)
    }

    @ViewBuilder
    private var startControls: some View {
        if recorder.isAuthorized {
            Button {
                recorder.start(dogName: dog?.name ?? "your dog")
                Haptics.success()
            } label: {
                Label("Start walk", systemImage: "play.fill")
            }
            .buttonStyle(.opawPrimary)
        } else if recorder.isDenied {
            Button {
                if let url = URL(string: UIApplication.openSettingsURLString) {
                    UIApplication.shared.open(url)
                }
            } label: {
                Label("Turn on location in Settings", systemImage: "location.slash")
            }
            .buttonStyle(.opawPrimary)
        } else {
            Button {
                recorder.requestPermission()
            } label: {
                Label("Allow location to record walks", systemImage: "location.fill")
            }
            .buttonStyle(.opawPrimary)
        }
    }
}

private struct RecordTile: View {
    let value: String
    let label: String

    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(value)
                .font(.opawNumber(18))
                .lineLimit(1)
                .minimumScaleFactor(0.6)
            Text(label)
                .font(.opaw(12, .bold))
                .foregroundStyle(Color.opawMuted)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(14)
        .background(Color.opawChip, in: RoundedRectangle(cornerRadius: 20, style: .continuous))
    }
}

struct WalkRow: View {
    let walk: Walk

    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: "figure.walk")
                .font(.system(size: 16, weight: .bold))
                .foregroundStyle(Color.opawInk)
                .frame(width: 40, height: 40)
                .background(Color.opawChip, in: Circle())
            VStack(alignment: .leading, spacing: 1) {
                Text(walk.title)
                    .font(.opaw(15, .heavy))
                    .foregroundStyle(Color.opawInk)
                Text("\(walk.startedAt.formatted(.dateTime.weekday(.abbreviated).day().month(.abbreviated))) · \(OpawFormat.distance(meters: walk.distanceMeters)) · \(OpawFormat.duration(walk.movingSeconds))")
                    .font(.opaw(13, .medium))
                    .foregroundStyle(Color.opawMuted)
            }
            Spacer(minLength: 0)
            if walk.sharedToPack {
                Image(systemName: "person.2.fill")
                    .font(.caption)
                    .foregroundStyle(Color.opawMuted)
            }
            Image(systemName: "chevron.right")
                .font(.system(size: 12, weight: .bold))
                .foregroundStyle(Color.opawMuted)
        }
        .padding(.vertical, 10)
        .contentShape(Rectangle())
    }
}

extension Walk {
    /// "Morning walk", "Evening loop"… from when it started.
    var title: String { Walk.title(for: startedAt) }

    static func title(for date: Date) -> String {
        switch Calendar.current.component(.hour, from: date) {
        case 5..<11: return "Morning walk"
        case 11..<14: return "Midday stroll"
        case 14..<17: return "Afternoon walk"
        case 17..<21: return "Evening loop"
        default: return "Night stroll"
        }
    }
}

// MARK: - Live walk

private struct LiveWalkView: View {
    let dog: Dog?
    let onFinish: (WalkRecorder.FinishedWalk) -> Void
    @Environment(WalkRecorder.self) private var recorder
    @State private var camera: MapCameraPosition = .userLocation(followsHeading: false, fallback: .automatic)
    @State private var confirmStop = false

    private var peeCount: Int { recorder.pins.filter { $0.kind == CareKind.pee.rawValue }.count }
    private var poopCount: Int { recorder.pins.filter { $0.kind == CareKind.poop.rawValue }.count }

    var body: some View {
        ZStack(alignment: .bottom) {
            Map(position: $camera) {
                UserAnnotation()
                MapPolyline(coordinates: recorder.points.map(\.coordinate))
                    .stroke(Color(hex: 0x111111), style: StrokeStyle(lineWidth: 7, lineCap: .round, lineJoin: .round))
                ForEach(recorder.pins) { pin in
                    Annotation("", coordinate: CLLocationCoordinate2D(latitude: pin.latitude, longitude: pin.longitude)) {
                        PinBadge(kind: pin.kind)
                    }
                }
            }
            .mapStyle(.standard(pointsOfInterest: .excludingAll))
            .ignoresSafeArea()

            VStack(spacing: 0) {
                if let data = dog?.cutoutData ?? dog?.photoData, let image = UIImage(data: data) {
                    Image(uiImage: image)
                        .resizable()
                        .scaledToFit()
                        .frame(height: 110)
                        .sticker(edge: 4)
                        .phaseAnimator([0, 1, 2, 3]) { view, step in
                            view
                                .offset(y: step == 1 || step == 3 ? -5 : 0)
                                .rotationEffect(.degrees(step == 1 ? 3 : step == 3 ? -3 : 0), anchor: .bottom)
                        } animation: { _ in .easeInOut(duration: 0.18) }
                        .offset(y: 24)
                        .zIndex(1)
                }
                statsCard
            }
            .padding(.horizontal, 14)
            .padding(.bottom, 8)
        }
        .confirmationDialog("Finish this walk?", isPresented: $confirmStop, titleVisibility: .visible) {
            Button("Finish and save") {
                if let result = recorder.finish() {
                    onFinish(result)
                }
            }
            Button("Discard walk", role: .destructive) {
                recorder.cancel()
            }
        }
    }

    private var statsCard: some View {
        VStack(alignment: .leading, spacing: 16) {
            HStack(alignment: .firstTextBaseline) {
                Text(recorder.state == .paused ? "Paused" : Walk.title(for: recorder.startedAt ?? Date()))
                    .font(.opaw(16, .heavy))
                    .foregroundStyle(recorder.state == .paused ? Color.opawAlert : Color.opawMuted)
                Spacer()
            }
            TimelineView(.periodic(from: .now, by: 1)) { context in
                VStack(alignment: .leading, spacing: 14) {
                    HStack(alignment: .firstTextBaseline, spacing: 8) {
                        Text((recorder.stats.distanceMeters / 1000).formatted(.number.precision(.fractionLength(2))))
                            .font(.system(size: 64, weight: .black, design: .rounded).monospacedDigit())
                            .contentTransition(.numericText())
                        Text("kilometres")
                            .font(.opaw(17, .bold))
                            .foregroundStyle(Color.opawMuted)
                    }
                    HStack {
                        LiveStat(value: OpawFormat.duration(recorder.elapsed(at: context.date)), label: "Time")
                        LiveStat(value: OpawFormat.pace(minutesPerKm: recorder.stats.paceMinutesPerKm), label: "Pace /km")
                        LiveStat(value: "\(peeCount) · \(poopCount)", label: "Pee · poop")
                    }
                }
            }
            HStack(spacing: 10) {
                Button {
                    recorder.dropPin(.pee)
                    Haptics.tap()
                } label: {
                    RoundIcon(symbol: CareKind.pee.symbol, size: 56)
                }
                .buttonStyle(.pressable)
                .accessibilityLabel("Mark a pee here")
                Button {
                    recorder.dropPin(.poop)
                    Haptics.tap()
                } label: {
                    RoundIcon(symbol: CareKind.poop.symbol, size: 56)
                }
                .buttonStyle(.pressable)
                .accessibilityLabel("Mark a poop here")
                Button {
                    if recorder.state == .paused { recorder.resume() } else { recorder.pause() }
                    Haptics.tap()
                } label: {
                    RoundIcon(symbol: recorder.state == .paused ? "play.fill" : "pause.fill", size: 56)
                }
                .buttonStyle(.pressable)
                .accessibilityLabel(recorder.state == .paused ? "Resume" : "Pause")
                Button("Finish walk") { confirmStop = true }
                    .buttonStyle(.opawPrimary)
            }
        }
        .padding(20)
        .background(Color.opawSurface, in: RoundedRectangle(cornerRadius: 36, style: .continuous))
        .shadow(color: .black.opacity(0.12), radius: 20, y: 10)
    }
}

private struct LiveStat: View {
    let value: String
    let label: String

    var body: some View {
        VStack(alignment: .leading, spacing: 1) {
            Text(value)
                .font(.opawNumber(20))
                .lineLimit(1)
                .minimumScaleFactor(0.7)
            Text(label)
                .font(.opaw(12, .bold))
                .foregroundStyle(Color.opawMuted)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
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

// MARK: - Summary

/// Shown after a walk ends: the route with pins, stats, then save (and optionally share).
private struct WalkSummarySheet: View {
    let result: WalkRecorder.FinishedWalk
    let dog: Dog

    @Environment(\.modelContext) private var modelContext
    @Environment(\.dismiss) private var dismiss
    @Environment(PackService.self) private var pack
    @State private var note = ""
    @State private var shareToPack = false
    @State private var isSaving = false
    @State private var error: String?

    private var stats: WalkStats { WalkCalculator.stats(for: result.points) }
    private var peeCount: Int { result.pins.filter { $0.kind == CareKind.pee.rawValue }.count }
    private var poopCount: Int { result.pins.filter { $0.kind == CareKind.poop.rawValue }.count }

    private var isMonthRecord: Bool {
        let monthStart = Calendar.current.dateInterval(of: .month, for: Date())?.start ?? Date()
        let best = (dog.walks ?? []).filter { $0.startedAt >= monthStart }.map(\.distanceMeters).max() ?? 0
        return stats.distanceMeters > 200 && stats.distanceMeters > best
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                RouteMap(points: result.points, pins: result.pins)
                    .frame(height: 340)

                VStack(alignment: .leading, spacing: 18) {
                    if isMonthRecord {
                        Label("Longest walk this month", systemImage: "trophy.fill")
                            .font(.opaw(14, .heavy))
                            .foregroundStyle(Color.opawOnInk)
                            .padding(.horizontal, 14)
                            .padding(.vertical, 8)
                            .background(Color.opawInk, in: Capsule())
                    }
                    VStack(alignment: .leading, spacing: 4) {
                        Text(Walk.title(for: result.startedAt))
                            .font(.opawDisplay(32))
                        Text("Today \(result.startedAt.formatted(date: .omitted, time: .shortened)) · with \(dog.name)")
                            .font(.opaw(15, .semibold))
                            .foregroundStyle(Color.opawMuted)
                    }
                    LazyVGrid(columns: [GridItem(.flexible(), spacing: 10), GridItem(.flexible(), spacing: 10)], spacing: 10) {
                        RecordTile(value: OpawFormat.distance(meters: stats.distanceMeters), label: "Distance")
                        RecordTile(value: OpawFormat.duration(stats.elapsedSeconds), label: "Duration")
                        RecordTile(value: OpawFormat.pace(minutesPerKm: stats.paceMinutesPerKm), label: "Pace /km")
                        RecordTile(value: "\(peeCount) · \(poopCount)", label: "Pee · poop")
                    }
                    weekProgress
                    TextField("How was the walk?", text: $note, axis: .vertical)
                        .font(.opaw(15, .medium))
                        .padding(.horizontal, 18)
                        .padding(.vertical, 14)
                        .background(Color.opawChip, in: RoundedRectangle(cornerRadius: 20, style: .continuous))
                    if pack.isSignedIn {
                        Toggle(isOn: $shareToPack) {
                            VStack(alignment: .leading, spacing: 2) {
                                Text("Share with the pack")
                                    .font(.opaw(15, .heavy))
                                Text("The start and end of your route are hidden so your home stays private.")
                                    .font(.opaw(12, .medium))
                                    .foregroundStyle(Color.opawMuted)
                            }
                        }
                        .tint(Color.opawInk)
                    }
                    if let error {
                        Text(error)
                            .font(.opaw(14, .semibold))
                            .foregroundStyle(Color.opawAlert)
                    }
                    Button(isSaving ? "Saving…" : "Save walk") { Task { await save() } }
                        .buttonStyle(.opawPrimary)
                        .disabled(isSaving)
                }
                .padding(Space.l)
                .padding(.top, 4)
                .background(
                    Color.opawSurface,
                    in: UnevenRoundedRectangle(topLeadingRadius: Radius.sheet, bottomLeadingRadius: 0, bottomTrailingRadius: 0, topTrailingRadius: Radius.sheet)
                )
                .offset(y: -32)
            }
        }
        .background(Color.opawSurface.ignoresSafeArea())
        .ignoresSafeArea(edges: .top)
        .interactiveDismissDisabled()
    }

    private var weekProgress: some View {
        let interval = Calendar.current.dateInterval(of: .weekOfYear, for: Date()) ?? DateInterval(start: Date(), duration: 1)
        let before = dog.walkedKm(from: interval.start, to: interval.end)
        let total = before + stats.distanceMeters / 1000
        let goal = max(0.5, dog.traits.recommendedWeeklyKm)
        return VStack(alignment: .leading, spacing: 8) {
            Text("\(total.formatted(.number.precision(.fractionLength(1)))) / \(goal.formatted(.number.precision(.fractionLength(0...1)))) km this week")
                .font(.opaw(15, .heavy))
            GeometryReader { proxy in
                ZStack(alignment: .leading) {
                    Capsule().fill(Color.opawChip)
                    Capsule().fill(Color.opawInk)
                        .frame(width: proxy.size.width * min(1, total / goal))
                }
            }
            .frame(height: 10)
        }
    }

    private func save() async {
        isSaving = true
        defer { isSaving = false }
        let walk = Walk(startedAt: result.startedAt, endedAt: result.endedAt, route: result.points, pins: result.pins, dog: dog)
        walk.note = note
        modelContext.insert(walk)
        // Pins dropped on the walk count as care logs too.
        for pin in result.pins {
            if let kind = CareKind(rawValue: pin.kind) {
                modelContext.insert(CareEvent(kind: kind, date: pin.date, note: "On a walk", dog: dog))
            }
        }
        BadgeService.refresh(dog)
        try? modelContext.save()
        if shareToPack {
            do {
                try await pack.shareWalk(walk, dog: dog, caption: note)
                try? modelContext.save()
            } catch {
                self.error = "Saved, but sharing failed: \(error.localizedDescription)"
                return
            }
        }
        Haptics.success()
        dismiss()
    }
}

// MARK: - Detail

struct WalkDetailView: View {
    let walk: Walk
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                ZStack(alignment: .topLeading) {
                    RouteMap(points: walk.route, pins: walk.pins)
                        .frame(height: 380)
                    Button { dismiss() } label: { RoundIcon(symbol: "chevron.left") }
                        .buttonStyle(.pressable)
                        .padding(.leading, 14)
                        .padding(.top, 62)
                        .accessibilityLabel("Back")
                }
                VStack(alignment: .leading, spacing: 18) {
                    VStack(alignment: .leading, spacing: 4) {
                        Text(walk.title)
                            .font(.opawDisplay(32))
                        Text(walk.startedAt.formatted(date: .abbreviated, time: .shortened))
                            .font(.opaw(15, .semibold))
                            .foregroundStyle(Color.opawMuted)
                    }
                    LazyVGrid(columns: [GridItem(.flexible(), spacing: 10), GridItem(.flexible(), spacing: 10)], spacing: 10) {
                        RecordTile(value: OpawFormat.distance(meters: walk.distanceMeters), label: "Distance")
                        RecordTile(value: OpawFormat.duration(walk.elapsedSeconds), label: "Duration")
                        RecordTile(value: OpawFormat.pace(minutesPerKm: walk.stats.paceMinutesPerKm), label: "Pace /km")
                        RecordTile(
                            value: "\(walk.pins.filter { $0.kind == CareKind.pee.rawValue }.count) · \(walk.pins.filter { $0.kind == CareKind.poop.rawValue }.count)",
                            label: "Pee · poop"
                        )
                    }
                    if !walk.note.isEmpty {
                        Text(walk.note)
                            .font(.opaw(16, .semibold))
                    }
                }
                .padding(Space.l)
                .padding(.bottom, 100)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(
                    Color.opawSurface,
                    in: UnevenRoundedRectangle(topLeadingRadius: Radius.sheet, bottomLeadingRadius: 0, bottomTrailingRadius: 0, topTrailingRadius: Radius.sheet)
                )
                .offset(y: -32)
            }
        }
        .background(Color.opawSurface.ignoresSafeArea())
        .ignoresSafeArea(edges: .top)
        .toolbar(.hidden, for: .navigationBar)
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
                Annotation("", coordinate: first.coordinate) {
                    Circle()
                        .fill(Color.white)
                        .frame(width: 18, height: 18)
                        .overlay(Circle().stroke(Color(hex: 0x111111), lineWidth: 4))
                }
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
