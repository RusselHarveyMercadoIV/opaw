import ActivityKit
import AppIntents
import SwiftUI
import WidgetKit
import OpawKit

@main
struct OpawWidgetsBundle: WidgetBundle {
    var body: some Widget {
        QuickLogWidget()
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
            if family == .accessoryRectangular {
                lockScreen
            } else {
                homeScreen
            }
        }
        .containerBackground(for: .widget) {
            family == .accessoryRectangular ? Color.clear : Color(red: 0.957, green: 0.957, blue: 0.961)
        }
    }

    /// Lock Screen: three one-tap logs.
    private var lockScreen: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(entry.dogName ?? "Opaw")
                .font(.system(size: 13, weight: .heavy, design: .rounded))
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
                    .font(.system(size: 16, weight: .black, design: .rounded))
                    .lineLimit(1)
                Spacer(minLength: 0)
            }
            .foregroundStyle(Color.opawWidgetInk)
            if entry.dogName == nil {
                Text("Open Opaw to add your dog.")
                    .font(.system(size: 13, weight: .semibold, design: .rounded))
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
                                    .font(.system(size: 11, weight: .heavy, design: .rounded).monospacedDigit())
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
        .supportedFamilies([.systemSmall, .systemMedium, .accessoryRectangular])
    }
}

// MARK: - Walk Live Activity

struct WalkLiveActivity: Widget {
    var body: some WidgetConfiguration {
        ActivityConfiguration(for: WalkActivityAttributes.self) { context in
            WalkActivityLockScreenView(context: context)
                .activityBackgroundTint(Color.white)
                .activitySystemActionForegroundColor(Color.opawWidgetInk)
        } dynamicIsland: { context in
            DynamicIsland {
                DynamicIslandExpandedRegion(.leading) {
                    Label(context.attributes.dogName, systemImage: "pawprint.fill")
                        .font(.system(size: 16, weight: .heavy, design: .rounded))
                        .foregroundStyle(.white)
                }
                DynamicIslandExpandedRegion(.trailing) {
                    WalkTimerText(state: context.state)
                        .font(.system(size: 20, weight: .heavy, design: .rounded).monospacedDigit())
                        .foregroundStyle(.white)
                }
                DynamicIslandExpandedRegion(.bottom) {
                    Text(OpawFormat.distance(meters: context.state.distanceMeters))
                        .font(.system(size: 28, weight: .black, design: .rounded).monospacedDigit())
                        .foregroundStyle(.white)
                }
            } compactLeading: {
                Image(systemName: "figure.walk")
                    .foregroundStyle(.white)
            } compactTrailing: {
                Text(OpawFormat.distance(meters: context.state.distanceMeters))
                    .font(.system(size: 12, weight: .heavy, design: .rounded).monospacedDigit())
                    .foregroundStyle(.white)
            } minimal: {
                Image(systemName: "pawprint.fill")
                    .foregroundStyle(.white)
            }
        }
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

struct WalkActivityLockScreenView: View {
    let context: ActivityViewContext<WalkActivityAttributes>

    var body: some View {
        HStack(spacing: 14) {
            Image(systemName: "pawprint.fill")
                .font(.system(size: 20, weight: .bold))
                .foregroundStyle(.white)
                .frame(width: 48, height: 48)
                .background(Color.opawWidgetInk, in: Circle())
            VStack(alignment: .leading, spacing: 2) {
                Text(context.isStale ? "Updating…" : context.state.isPaused ? "Walk paused" : "Walking with \(context.attributes.dogName)")
                    .font(.system(size: 16, weight: .heavy, design: .rounded))
                HStack(alignment: .firstTextBaseline, spacing: 14) {
                    Text(OpawFormat.distance(meters: context.state.distanceMeters))
                        .font(.system(size: 26, weight: .black, design: .rounded).monospacedDigit())
                    WalkTimerText(state: context.state)
                        .font(.system(size: 18, weight: .bold, design: .rounded).monospacedDigit())
                        .foregroundStyle(.secondary)
                }
            }
            .foregroundStyle(Color.opawWidgetInk)
            Spacer(minLength: 0)
        }
        .padding(16)
    }
}

extension Color {
    /// Opaw's ink, matching the app's AccentColor.
    static let opawWidgetAccent = Color(red: 0.067, green: 0.067, blue: 0.067)
    static let opawWidgetInk = Color(red: 0.067, green: 0.067, blue: 0.067)
}
