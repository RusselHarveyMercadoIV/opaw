import MapKit
import OpawKit
import SwiftData
import SwiftUI

/// The Walk tab: start a walk, follow it live, and browse past walks.
struct WalkTabView: View {
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
                    WalkHomeView(dog: dog)
                } else {
                    LiveWalkView { result in
                        finished = IdentifiedWalk(walk: result)
                    }
                }
            }
            .navigationTitle(recorder.state == .idle ? "Walks" : "Walking \(recorder.dogName)")
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

private struct WalkHomeView: View {
    let dog: Dog?
    @Environment(WalkRecorder.self) private var recorder
    @State private var camera: MapCameraPosition = .userLocation(fallback: .automatic)

    private var walks: [Walk] {
        (dog?.walks ?? []).sorted { $0.startedAt > $1.startedAt }
    }

    private var weekKm: Double {
        let start = Calendar.current.dateInterval(of: .weekOfYear, for: Date())?.start ?? Date()
        return walks.filter { $0.startedAt >= start }.reduce(0) { $0 + $1.distanceMeters } / 1000
    }

    var body: some View {
        List {
            Section {
                Map(position: $camera) {
                    UserAnnotation()
                }
                .frame(height: 220)
                .clipShape(RoundedRectangle(cornerRadius: 16))
                .listRowInsets(EdgeInsets())

                if recorder.isAuthorized {
                    Button {
                        recorder.start(dogName: dog?.name ?? "your dog")
                        Haptics.success()
                    } label: {
                        Label("Start walk", systemImage: "play.fill")
                            .font(.headline)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 6)
                    }
                    .buttonStyle(.borderedProminent)
                    .listRowBackground(Color.clear)
                } else if recorder.isDenied {
                    VStack(alignment: .leading, spacing: 8) {
                        Text("Location is turned off for Opaw, so walks can't be recorded. You can turn it on in Settings.")
                        Button("Open Settings") {
                            if let url = URL(string: UIApplication.openSettingsURLString) {
                                UIApplication.shared.open(url)
                            }
                        }
                        .buttonStyle(.borderedProminent)
                    }
                } else {
                    VStack(alignment: .leading, spacing: 8) {
                        Text("Opaw needs your location to record walks.")
                        Button("Allow location") { recorder.requestPermission() }
                            .buttonStyle(.borderedProminent)
                    }
                }
            }

            if let dog {
                Section("This week") {
                    HStack {
                        StatTile(title: "Distance", value: String(format: "%.1f km", weekKm), symbol: "point.topleft.down.to.point.bottomright.curvepath")
                        StatTile(title: "Goal", value: String(format: "%.1f km", dog.traits.recommendedWeeklyKm), symbol: "target")
                    }
                    ProgressView(value: min(1, weekKm / max(0.1, dog.traits.recommendedWeeklyKm)))
                        .tint(.opawOrange)
                }
            }

            Section("History") {
                if walks.isEmpty {
                    Text("Your walks will show up here.")
                        .foregroundStyle(.secondary)
                }
                ForEach(walks) { walk in
                    NavigationLink {
                        WalkDetailView(walk: walk)
                    } label: {
                        WalkRow(walk: walk)
                    }
                }
            }
        }
        .onAppear { recorder.warmUp() }
        .onDisappear { recorder.coolDown() }
    }
}

struct WalkRow: View {
    let walk: Walk

    var body: some View {
        HStack {
            Image(systemName: "figure.walk.circle.fill")
                .font(.title)
                .foregroundStyle(Color.opawOrange)
            VStack(alignment: .leading) {
                Text(walk.startedAt.formatted(.dateTime.weekday(.abbreviated).month().day().hour().minute()))
                    .font(.subheadline.weight(.medium))
                Text("\(OpawFormat.distance(meters: walk.distanceMeters)) · \(OpawFormat.duration(walk.movingSeconds))")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
            Spacer()
            if walk.sharedToPack {
                Image(systemName: "person.3.fill")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
        }
    }
}

/// Live view while recording.
private struct LiveWalkView: View {
    let onFinish: (WalkRecorder.FinishedWalk) -> Void
    @Environment(WalkRecorder.self) private var recorder
    @State private var camera: MapCameraPosition = .userLocation(followsHeading: false, fallback: .automatic)
    @State private var confirmStop = false

    var body: some View {
        VStack(spacing: 0) {
            Map(position: $camera) {
                UserAnnotation()
                MapPolyline(coordinates: recorder.points.map(\.coordinate))
                    .stroke(Color.opawOrange, lineWidth: 5)
                ForEach(recorder.pins) { pin in
                    Annotation(pin.kind == CareKind.poop.rawValue ? "Poop" : "Pee", coordinate: CLLocationCoordinate2D(latitude: pin.latitude, longitude: pin.longitude)) {
                        Text(pin.kind == CareKind.poop.rawValue ? "💩" : "💛")
                    }
                }
            }
            .mapControls {
                MapUserLocationButton()
            }

            VStack(spacing: 16) {
                TimelineView(.periodic(from: .now, by: 1)) { context in
                    HStack {
                        StatTile(title: "Distance", value: OpawFormat.distance(meters: recorder.stats.distanceMeters))
                        StatTile(title: "Time", value: OpawFormat.duration(recorder.elapsed(at: context.date)))
                        StatTile(title: "Pace", value: OpawFormat.pace(minutesPerKm: recorder.stats.paceMinutesPerKm))
                    }
                }
                HStack(spacing: 12) {
                    Button {
                        recorder.dropPin(.pee)
                        Haptics.tap()
                    } label: {
                        Label("Pee", systemImage: "drop.triangle")
                            .frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.bordered)
                    Button {
                        recorder.dropPin(.poop)
                        Haptics.tap()
                    } label: {
                        Label("Poop", systemImage: "leaf.fill")
                            .frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.bordered)
                }
                HStack(spacing: 12) {
                    if recorder.state == .paused {
                        Button {
                            recorder.resume()
                        } label: {
                            Label("Resume", systemImage: "play.fill").frame(maxWidth: .infinity)
                        }
                        .buttonStyle(.borderedProminent)
                    } else {
                        Button {
                            recorder.pause()
                        } label: {
                            Label("Pause", systemImage: "pause.fill").frame(maxWidth: .infinity)
                        }
                        .buttonStyle(.bordered)
                    }
                    Button(role: .destructive) {
                        confirmStop = true
                    } label: {
                        Label("Finish", systemImage: "stop.fill").frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.borderedProminent)
                    .tint(.red)
                }
            }
            .padding()
            .background(.regularMaterial)
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
}

/// Shown after a walk ends: save it, log potty breaks, share it with the pack.
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

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    RouteMap(points: result.points, pins: result.pins)
                        .frame(height: 220)
                        .listRowInsets(EdgeInsets())
                    HStack {
                        StatTile(title: "Distance", value: OpawFormat.distance(meters: stats.distanceMeters))
                        StatTile(title: "Moving", value: OpawFormat.duration(stats.movingSeconds))
                        StatTile(title: "Pace", value: OpawFormat.pace(minutesPerKm: stats.paceMinutesPerKm))
                    }
                }
                Section {
                    TextField("How was the walk?", text: $note, axis: .vertical)
                    if pack.isSignedIn {
                        Toggle("Share with the pack", isOn: $shareToPack)
                    }
                } footer: {
                    if shareToPack {
                        Text("The start and end of your route are hidden so your home stays private.")
                    }
                }
                if let error {
                    Text(error).foregroundStyle(.red)
                }
            }
            .navigationTitle("Nice walk!")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save") { Task { await save() } }
                        .disabled(isSaving)
                }
            }
            .interactiveDismissDisabled()
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

struct WalkDetailView: View {
    let walk: Walk

    var body: some View {
        List {
            Section {
                RouteMap(points: walk.route, pins: walk.pins)
                    .frame(height: 300)
                    .listRowInsets(EdgeInsets())
            }
            Section("Stats") {
                LabeledContent("Distance", value: OpawFormat.distance(meters: walk.distanceMeters))
                LabeledContent("Moving time", value: OpawFormat.duration(walk.movingSeconds))
                LabeledContent("Total time", value: OpawFormat.duration(walk.elapsedSeconds))
                LabeledContent("Pace", value: OpawFormat.pace(minutesPerKm: walk.stats.paceMinutesPerKm))
                LabeledContent("Potty breaks", value: "\(walk.pins.count)")
            }
            if !walk.note.isEmpty {
                Section("Note") { Text(walk.note) }
            }
        }
        .navigationTitle(walk.startedAt.formatted(date: .abbreviated, time: .shortened))
    }
}

/// A static map of a recorded route.
struct RouteMap: View {
    let points: [GeoPoint]
    let pins: [WalkPin]

    var body: some View {
        Map(initialPosition: .automatic) {
            MapPolyline(coordinates: points.map(\.coordinate))
                .stroke(Color.opawOrange, lineWidth: 5)
            ForEach(pins) { pin in
                Annotation("", coordinate: CLLocationCoordinate2D(latitude: pin.latitude, longitude: pin.longitude)) {
                    Text(pin.kind == CareKind.poop.rawValue ? "💩" : "💛")
                }
            }
            if let first = points.first {
                Marker("Start", systemImage: "flag.fill", coordinate: first.coordinate)
                    .tint(.green)
            }
        }
        .allowsHitTesting(false)
    }
}

extension GeoPoint {
    var coordinate: CLLocationCoordinate2D {
        CLLocationCoordinate2D(latitude: latitude, longitude: longitude)
    }
}
