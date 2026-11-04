import ActivityKit
import AppIntents
import SwiftUI
import WidgetKit
import OpawKit

@main
struct OpawWidgetsBundle: WidgetBundle {
    var body: some Widget {
        QuickLogWidget()
        CareScoreWidget()
        WalkLiveActivity()
    }
}

// MARK: - Quick log widget

struct QuickLogEntry: TimelineEntry {
    let date: Date
    let dogName: String?
    let counts: [CareKind: Int]
}

struct QuickLogProvider: TimelineProvider {
    func placeholder(in context: Context) -> QuickLogEntry {
        QuickLogEntry(date: Date(), dogName: "Max", counts: [.meal: 1, .pee: 2])
    }

    func getSnapshot(in context: Context, completion: @escaping (QuickLogEntry) -> Void) {
        Task { @MainActor in
            completion(currentEntry())
        }
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<QuickLogEntry>) -> Void) {
        Task { @MainActor in
            let entry = currentEntry()
            // Refresh after midnight so today's counts reset.
            let tomorrow = Calendar.current.startOfDay(for: Date().addingTimeInterval(86_400))
            completion(Timeline(entries: [entry], policy: .after(tomorrow)))
        }
    }

    @MainActor
    private func currentEntry() -> QuickLogEntry {
        let today = CareLogger.todayCounts()
        return QuickLogEntry(date: Date(), dogName: today.dogName, counts: today.counts)
    }
}

struct QuickLogWidgetView: View {
    let entry: QuickLogEntry
    @Environment(\.widgetFamily) private var family

    var body: some View {
        Group {
            if family == .accessoryCircular {
                poopCircle
            } else if family == .accessoryRectangular {
                lockScreen
            } else {
                homeScreen
            }
        }
        .containerBackground(for: .widget) {
            family == .accessoryRectangular || family == .accessoryCircular ? Color.clear : Color(red: 0.957, green: 0.957, blue: 0.961)
        }
    }

    /// Lock Screen circle: log a poop with one tap.
    private var poopCircle: some View {
        Button(intent: LogCareIntent(kind: .poop)) {
            ZStack {
                AccessoryWidgetBackground()
                Image(systemName: CareKind.poop.symbol)
                    .font(.system(size: 22, weight: .bold))
            }
        }
        .buttonStyle(.plain)
        .accessibilityLabel("Log poop")
    }

    /// Lock Screen: three one-tap logs.
    private var lockScreen: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(entry.dogName ?? "Opaw")
                .font(.custom("Nunito-ExtraBold", size: 13))
                .lineLimit(1)
            HStack(spacing: 6) {
                ForEach([CareKind.meal, .pee, .poop]) { kind in
                    Button(intent: LogCareIntent(kind: kind)) {
                        Image(systemName: kind.symbol)
                            .font(.system(size: 15, weight: .bold))
                            .frame(maxWidth: .infinity, minHeight: 30)
                    }
                    .buttonStyle(.plain)
                    .background(.white.opacity(0.18), in: Capsule())
                    .accessibilityLabel("Log \(kind.title)")
                }
            }
        }
    }

    private var homeScreen: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Image(systemName: "pawprint.fill")
                Text(entry.dogName ?? "Opaw")
                    .font(.custom("Nunito-Black", size: 16))
                    .lineLimit(1)
                Spacer(minLength: 0)
            }
            .foregroundStyle(Color.opawWidgetInk)
            if entry.dogName == nil {
                Text("Open Opaw to add your dog.")
                    .font(.custom("Nunito-SemiBold", size: 13))
                    .foregroundStyle(.secondary)
            } else {
                let columns = family == .systemSmall ? 2 : 4
                LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 6), count: columns), spacing: 6) {
                    ForEach(CareKind.quickLog) { kind in
                        Button(intent: LogCareIntent(kind: kind)) {
                            VStack(spacing: 2) {
                                Image(systemName: kind.symbol)
                                    .font(.system(size: 17, weight: .semibold))
                                Text("\(entry.counts[kind] ?? 0)")
                                    .font(.custom("Nunito-ExtraBold", size: 11).monospacedDigit())
                                    .foregroundStyle(.secondary)
                            }
                            .foregroundStyle(Color.opawWidgetInk)
                            .frame(maxWidth: .infinity, minHeight: 44)
                            .background(.white, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
                        }
                        .buttonStyle(.plain)
                        .accessibilityLabel("Log \(kind.title)")
                    }
                }
            }
        }
    }
}

struct QuickLogWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: OpawWidgetKind.quickLog, provider: QuickLogProvider()) { entry in
            QuickLogWidgetView(entry: entry)
        }
        .configurationDisplayName("Quick log")
        .description("Log meals, water and potty breaks with one tap.")
        .supportedFamilies([.systemSmall, .systemMedium, .accessoryRectangular, .accessoryCircular])
    }
}

// MARK: - Care score (Lock Screen ring)

struct CareScoreEntry: TimelineEntry {
    let date: Date
    let dogName: String?
    let score: Int
}

struct CareScoreProvider: TimelineProvider {
    func placeholder(in context: Context) -> CareScoreEntry {
        CareScoreEntry(date: Date(), dogName: "Miso", score: 92)
    }

    func getSnapshot(in context: Context, completion: @escaping (CareScoreEntry) -> Void) {
        Task { @MainActor in completion(entry(at: Date())) }
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<CareScoreEntry>) -> Void) {
        Task { @MainActor in
            // The score drifts as time passes since the last meal or walk, so refresh every half hour.
            let now = Date()
            completion(Timeline(entries: [entry(at: now)], policy: .after(now.addingTimeInterval(30 * 60))))
        }
    }

    @MainActor
    private func entry(at date: Date) -> CareScoreEntry {
        let score = CareLogger.todayScore()
        return CareScoreEntry(date: date, dogName: score?.dogName, score: score?.score ?? 0)
    }
}

struct CareScoreWidgetView: View {
    let entry: CareScoreEntry

    var body: some View {
        ZStack {
            AccessoryWidgetBackground()
            Circle()
                .stroke(.white.opacity(0.25), lineWidth: 5)
                .padding(6)
            Circle()
                .trim(from: 0, to: Double(entry.score) / 100)
                .stroke(.white, style: StrokeStyle(lineWidth: 5, lineCap: .round))
                .rotationEffect(.degrees(-90))
                .padding(6)
            Text(entry.dogName == nil ? "–" : "\(entry.score)")
                .font(.custom("Nunito-Black", size: 18))
        }
        .widgetAccentable()
        .containerBackground(for: .widget) { Color.clear }
        .accessibilityLabel("\(entry.dogName ?? "Your pet")'s care score: \(entry.score)")
    }
}

struct CareScoreWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: OpawWidgetKind.careScore, provider: CareScoreProvider()) { entry in
            CareScoreWidgetView(entry: entry)
        }
        .configurationDisplayName("Care score")
        .description("How your pet is doing today, out of 100.")
        .supportedFamilies([.accessoryCircular])
    }
}

// MARK: - Walk Live Activity

struct WalkLiveActivity: Widget {
    var body: some WidgetConfiguration {
        ActivityConfiguration(for: WalkActivityAttributes.self) { context in
            WalkActivityLockScreenView(context: context)
                .activityBackgroundTint(Color(red: 0.067, green: 0.067, blue: 0.067))
                .activitySystemActionForegroundColor(.white)
        } dynamicIsland: { context in
            DynamicIsland {
                DynamicIslandExpandedRegion(.leading) {
                    PetStickerImage(size: 44)
                }
                DynamicIslandExpandedRegion(.trailing) {
                    WalkTimerText(state: context.state)
                        .font(.custom("Nunito-ExtraBold", size: 20).monospacedDigit())
                        .foregroundStyle(.white)
                        .multilineTextAlignment(.trailing)
                        .frame(maxWidth: 90, alignment: .trailing)
                }
                DynamicIslandExpandedRegion(.bottom) {
                    HStack(alignment: .firstTextBaseline) {
                        DistanceText(meters: context.state.distanceMeters, size: 30)
                        Spacer()
                        Text("\(context.state.peeCount) pee · \(context.state.poopCount) poop")
                            .font(.custom("Nunito-Medium", size: 13))
                            .foregroundStyle(Color(white: 0.67))
                    }
                }
            } compactLeading: {
                PetStickerImage(size: 26)
                    .clipShape(Circle())
            } compactTrailing: {
                WalkTimerText(state: context.state)
                    .font(.custom("Nunito-ExtraBold", size: 14).monospacedDigit())
                    .foregroundStyle(.white)
                    .frame(maxWidth: 52)
            } minimal: {
                PetStickerImage(size: 24)
                    .clipShape(Circle())
            }
        }
    }
}

/// The pet's sticker saved by the app when a walk starts, or a paw.
struct PetStickerImage: View {
    let size: CGFloat

    var body: some View {
        if let data = PetSticker.load(), let image = UIImage(data: data) {
            Image(uiImage: image)
                .resizable()
                .scaledToFit()
                .frame(width: size, height: size)
        } else {
            Image(systemName: "pawprint.fill")
                .font(.system(size: size * 0.45, weight: .bold))
                .foregroundStyle(.white)
                .frame(width: size, height: size)
        }
    }
}

/// "1.84 km" with the unit small and gray.
struct DistanceText: View {
    let meters: Double
    let size: CGFloat

    var body: some View {
        (Text((meters / 1000).formatted(.number.precision(.fractionLength(2))))
            + Text(" km").font(.custom("Nunito-Black", size: 15)).foregroundColor(Color(white: 0.67)))
            .font(.custom("Nunito-Black", size: size).monospacedDigit())
            .foregroundStyle(.white)
    }
}

struct WalkTimerText: View {
    let state: WalkActivityAttributes.ContentState

    var body: some View {
        if state.isPaused {
            Text(OpawFormat.duration(state.pausedElapsedSeconds))
        } else {
            Text(timerInterval: state.timerStart...Date.distantFuture, countsDown: false)
        }
    }
}

/// Lock Screen Live Activity (3g): a black card with the pet, the walk, distance and time.
struct WalkActivityLockScreenView: View {
    let context: ActivityViewContext<WalkActivityAttributes>

    var body: some View {
        VStack(spacing: 12) {
            HStack(spacing: 12) {
                PetStickerImage(size: 52)
                    .shadow(color: .white, radius: 0, x: 2)
                    .shadow(color: .white, radius: 0, x: -2)
                    .shadow(color: .white, radius: 0, y: 2)
                    .shadow(color: .white, radius: 0, y: -2)
                VStack(alignment: .leading, spacing: 0) {
                    Text(context.isStale ? "Updating…" : context.state.isPaused ? "Walk paused" : "Walking with \(context.attributes.dogName)")
                        .font(.custom("Nunito-ExtraBold", size: 16))
                        .foregroundStyle(.white)
                    Text("\(context.state.peeCount) pee · \(context.state.poopCount) poop")
                        .font(.custom("Nunito-Medium", size: 13))
                        .foregroundStyle(Color(white: 0.67))
                }
                Spacer(minLength: 0)
                Image(systemName: context.state.isPaused ? "play.fill" : "pause.fill")
                    .font(.system(size: 16, weight: .bold))
                    .foregroundStyle(.white)
                    .frame(width: 44, height: 44)
                    .background(Color(red: 0.165, green: 0.165, blue: 0.173), in: Circle())
            }
            HStack(alignment: .firstTextBaseline) {
                DistanceText(meters: context.state.distanceMeters, size: 34)
                Spacer()
                WalkTimerText(state: context.state)
                    .font(.custom("Nunito-ExtraBold", size: 20).monospacedDigit())
                    .foregroundStyle(.white)
                    .multilineTextAlignment(.trailing)
                    .frame(maxWidth: 110, alignment: .trailing)
            }
        }
        .padding(.horizontal, 18)
        .padding(.vertical, 16)
    }
}

extension Color {
    /// Opaw's ink, matching the app's AccentColor.
    static let opawWidgetAccent = Color(red: 0.067, green: 0.067, blue: 0.067)
    static let opawWidgetInk = Color(red: 0.067, green: 0.067, blue: 0.067)
}
