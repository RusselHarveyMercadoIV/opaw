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
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Image(systemName: "pawprint.fill")
                    .foregroundStyle(Color.opawWidgetAccent)
                Text(entry.dogName ?? "Opaw")
                    .font(.headline)
                    .lineLimit(1)
                Spacer(minLength: 0)
            }
            if entry.dogName == nil {
                Text("Open Opaw to add your dog.")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            } else {
                let columns = family == .systemSmall ? 2 : 4
                LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 6), count: columns), spacing: 6) {
                    ForEach(CareKind.quickLog) { kind in
                        Button(intent: LogCareIntent(kind: kind)) {
                            VStack(spacing: 2) {
                                Image(systemName: kind.symbol)
                                    .font(.title3)
                                Text("\(entry.counts[kind] ?? 0)")
                                    .font(.caption2.monospacedDigit())
                                    .foregroundStyle(.secondary)
                            }
                            .frame(maxWidth: .infinity, minHeight: 40)
                        }
                        .buttonStyle(.bordered)
                        .tint(Color.opawWidgetAccent)
                        .accessibilityLabel("Log \(kind.title)")
                    }
                }
            }
        }
        .containerBackground(for: .widget) {
            Color(.systemBackground)
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
        .supportedFamilies([.systemSmall, .systemMedium])
    }
}

// MARK: - Walk Live Activity

struct WalkLiveActivity: Widget {
    var body: some WidgetConfiguration {
        ActivityConfiguration(for: WalkActivityAttributes.self) { context in
            WalkActivityLockScreenView(context: context)
                .activityBackgroundTint(Color.black.opacity(0.75))
                .activitySystemActionForegroundColor(Color.opawWidgetAccent)
        } dynamicIsland: { context in
            DynamicIsland {
                DynamicIslandExpandedRegion(.leading) {
                    Label(context.attributes.dogName, systemImage: "pawprint.fill")
                        .font(.headline)
                        .foregroundStyle(Color.opawWidgetAccent)
                }
                DynamicIslandExpandedRegion(.trailing) {
                    WalkTimerText(state: context.state)
                        .font(.title3.monospacedDigit())
                }
                DynamicIslandExpandedRegion(.bottom) {
                    Text(OpawFormat.distance(meters: context.state.distanceMeters))
                        .font(.title2.bold().monospacedDigit())
                }
            } compactLeading: {
                Image(systemName: "figure.walk")
                    .foregroundStyle(Color.opawWidgetAccent)
            } compactTrailing: {
                Text(OpawFormat.distance(meters: context.state.distanceMeters))
                    .font(.caption.monospacedDigit())
            } minimal: {
                Image(systemName: "pawprint.fill")
                    .foregroundStyle(Color.opawWidgetAccent)
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
        HStack(spacing: 16) {
            Image(systemName: "pawprint.circle.fill")
                .font(.system(size: 40))
                .foregroundStyle(Color.opawWidgetAccent)
            VStack(alignment: .leading, spacing: 4) {
                Text(context.isStale ? "Updating…" : context.state.isPaused ? "Walk paused" : "Walking with \(context.attributes.dogName)")
                    .font(.headline)
                    .foregroundStyle(.white)
                HStack(spacing: 16) {
                    Text(OpawFormat.distance(meters: context.state.distanceMeters))
                    WalkTimerText(state: context.state)
                }
                .font(.title3.monospacedDigit())
                .foregroundStyle(.white)
            }
            Spacer(minLength: 0)
        }
        .padding()
    }
}

extension Color {
    /// Opaw's persimmon accent, matching the app's AccentColor.
    static let opawWidgetAccent = Color(red: 0.894, green: 0.341, blue: 0.180)
}
