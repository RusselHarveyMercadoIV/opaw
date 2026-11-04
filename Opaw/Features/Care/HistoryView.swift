import Charts
import OpawKit
import SwiftUI

/// History: a plain-language summary, weight over time, the poop record, and a vet summary to share.
struct HistoryView: View {
    let dog: Dog

    enum Range: String, CaseIterable, Identifiable {
        case week = "Week"
        case sixMonths = "6 months"
        case year = "Year"

        var id: String { rawValue }

        var days: Int {
            switch self {
            case .week: return 7
            case .sixMonths: return 183
            case .year: return 365
            }
        }
    }

    @Environment(\.dismiss) private var dismiss
    @State private var range: Range = .sixMonths

    private var start: Date {
        Calendar.current.date(byAdding: .day, value: -range.days, to: Calendar.current.startOfDay(for: Date())) ?? Date()
    }

    var body: some View {
        ScrollView {
            VStack(spacing: 12) {
                ScreenHeader(title: "History") {
                    Button { dismiss() } label: { RoundIcon(symbol: "chevron.left") }
                        .buttonStyle(.pressable)
                        .accessibilityLabel("Back")
                } trailing: {
                    ShareLink(item: vetSummary) { RoundIcon(symbol: "square.and.arrow.up") }
                        .buttonStyle(.pressable)
                        .accessibilityLabel("Share summary")
                }

                HStack(spacing: 6) {
                    ForEach(Range.allCases) { option in
                        Button {
                            withAnimation(.snappy) { range = option }
                            Haptics.select()
                        } label: {
                            Text(option.rawValue)
                                .font(.opaw(14, .bold))
                                .foregroundStyle(option == range ? Color.opawOnInk : Color.opawInk)
                                .padding(.horizontal, 18)
                                .padding(.vertical, 10)
                                .background(option == range ? Color.opawInk : Color.opawSurface, in: Capsule())
                                .shadow(color: .black.opacity(option == range ? 0 : 0.05), radius: 8, y: 6)
                        }
                        .buttonStyle(.pressable)
                    }
                    Spacer()
                }
                .padding(.horizontal, Space.l)
                .padding(.top, 4)

                summaryCard
                    .padding(.horizontal, Space.l)
                weightCard
                    .padding(.horizontal, Space.l)
                poopCard
                    .padding(.horizontal, Space.l)
                logCard
                    .padding(.horizontal, Space.l)
            }
            .padding(.bottom, 180)
        }
        .scrollIndicators(.hidden)
        .opawBackground()
        .overlay(alignment: .bottom) {
            ShareLink(item: vetSummary) {
                Label("Share vet summary", systemImage: "doc.text")
            }
            .buttonStyle(.opawPrimary)
            .padding(.horizontal, Space.l)
            .padding(.bottom, 92)
        }
        .toolbar(.hidden, for: .navigationBar)
    }

    // MARK: Summary

    private var summaryCard: some View {
        HStack(alignment: .top, spacing: 12) {
            Image(systemName: "sparkles")
                .font(.system(size: 20, weight: .bold))
                .foregroundStyle(.white)
                .padding(.top, 2)
            VStack(alignment: .leading, spacing: 4) {
                Text("This week, summarised on your phone")
                    .font(.opaw(13, .bold))
                    .foregroundStyle(Color(hex: 0xAAAAAA))
                Text(weeklySummary)
                    .font(.opaw(16, .bold))
                    .foregroundStyle(.white)
                    .fixedSize(horizontal: false, vertical: true)
            }
            Spacer(minLength: 0)
        }
        .padding(20)
        .background(Color(hex: 0x111111), in: RoundedRectangle(cornerRadius: Radius.card, style: .continuous))
    }

    /// Plain sentences from this week's numbers. Computed on device; nothing leaves the phone.
    private var weeklySummary: String {
        let calendar = Calendar.current
        let today = calendar.startOfDay(for: Date())
        let weekAgo = calendar.date(byAdding: .day, value: -7, to: today) ?? today
        let twoWeeksAgo = calendar.date(byAdding: .day, value: -14, to: today) ?? today
        let tomorrow = calendar.date(byAdding: .day, value: 1, to: today) ?? today
        var sentences: [String] = []

        let thisWeek = dog.walkedKm(from: weekAgo, to: tomorrow)
        let lastWeek = dog.walkedKm(from: twoWeeksAgo, to: weekAgo)
        if thisWeek == 0 && lastWeek == 0 {
            sentences.append("No walks logged yet this week.")
        } else if lastWeek > 0 {
            let change = Int(((thisWeek - lastWeek) / lastWeek * 100).rounded())
            if abs(change) < 5 {
                sentences.append("\(dog.name) walked about as much as last week.")
            } else {
                sentences.append("\(dog.name) walked \(abs(change))% \(change > 0 ? "more" : "less") than last week.")
            }
        } else {
            sentences.append("\(dog.name) walked \(OpawFormat.distance(meters: thisWeek * 1000)) this week.")
        }

        let days = poopDays(count: 7)
        let logged = days.filter { $0 != .empty }.count
        if logged > 0 {
            let normal = days.filter { $0 == .normal }.count
            sentences.append("Poop was normal \(normal) of \(logged) day\(logged == 1 ? "" : "s").")
        }

        if let trend = weightTrend {
            sentences.append(trend)
        }
        return sentences.joined(separator: " ")
    }

    private var weightPoints: [(date: Date, kg: Double)] {
        (dog.careEvents ?? [])
            .compactMap { event in event.weightKg.map { (date: event.date, kg: $0) } }
            .sorted { $0.date < $1.date }
    }

    private var weightTrend: String? {
        let points = weightPoints
        guard let latest = points.last else { return nil }
        let monthAgo = Calendar.current.date(byAdding: .day, value: -30, to: latest.date) ?? latest.date
        guard let earlier = points.last(where: { $0.date <= monthAgo }) ?? (points.count > 1 ? points.first : nil) else {
            return "Weight is \(latest.kg.formatted(.number.precision(.fractionLength(1)))) kg."
        }
        let change = (latest.kg - earlier.kg) / max(0.1, earlier.kg)
        if abs(change) < 0.02 { return "Weight is steady." }
        return change > 0 ? "Weight is up a little." : "Weight is down a little."
    }

    // MARK: Weight

    private var weightCard: some View {
        let points = weightPoints.filter { $0.date >= start }
        return VStack(alignment: .leading, spacing: 10) {
            HStack(alignment: .firstTextBaseline) {
                Text("Weight")
                    .font(.opaw(17, .black))
                Spacer()
                if let latest = weightPoints.last {
                    (Text(latest.kg.formatted(.number.precision(.fractionLength(1)))) + Text(" kg").font(.opaw(14, .bold)).foregroundColor(Color.opawMuted))
                        .font(.opawNumber(22))
                }
            }
            if points.count < 2 {
                Text(points.isEmpty ? "Log a weight to start the chart." : "One more weigh-in and the line appears.")
                    .font(.opaw(14, .semibold))
                    .foregroundStyle(Color.opawMuted)
                    .frame(maxWidth: .infinity, minHeight: 80, alignment: .leading)
            } else {
                Chart {
                    ForEach(Array(points.enumerated()), id: \.offset) { index, point in
                        LineMark(x: .value("Date", point.date), y: .value("kg", point.kg))
                            .foregroundStyle(Color.opawInk)
                            .lineStyle(StrokeStyle(lineWidth: 3, lineCap: .round, lineJoin: .round))
                            .interpolationMethod(.catmullRom)
                        PointMark(x: .value("Date", point.date), y: .value("kg", point.kg))
                            .foregroundStyle(index == points.count - 1 ? Color.opawInk : Color.opawSurface)
                            .symbolSize(index == points.count - 1 ? 120 : 40)
                    }
                }
                .chartYScale(domain: .automatic(includesZero: false))
                .chartYAxis(.hidden)
                .chartXAxis {
                    AxisMarks(values: .automatic(desiredCount: 6)) { _ in
                        AxisValueLabel(format: range == .week ? .dateTime.weekday(.abbreviated) : .dateTime.month(.abbreviated))
                            .font(.opaw(12, .semibold))
                            .foregroundStyle(Color.opawMuted)
                    }
                }
                .frame(height: 110)
            }
        }
        .card(padding: 20)
    }

    // MARK: Poop

    private enum PoopDay { case empty, normal, watch }

    /// One entry per day, oldest first.
    private func poopDays(count: Int) -> [PoopDay] {
        let calendar = Calendar.current
        let today = calendar.startOfDay(for: Date())
        return (0..<count).reversed().map { offset in
            let day = calendar.date(byAdding: .day, value: -offset, to: today) ?? today
            let poops = dog.events(on: day).filter { $0.kind == .poop }
            if poops.isEmpty { return .empty }
            let concerning = poops.contains { ($0.poopConsistency?.isConcerning ?? false) || ($0.poopColor?.isConcerning ?? false) }
            return concerning ? .watch : .normal
        }
    }

    private var poopCard: some View {
        let days = poopDays(count: 14)
        let normal = days.filter { $0 == .normal }.count
        let watch = days.filter { $0 == .watch }.count
        return VStack(alignment: .leading, spacing: 12) {
            HStack(alignment: .firstTextBaseline) {
                Text("Poop, last 14 days")
                    .font(.opaw(17, .black))
                Spacer()
                Text(watch > 0 ? "\(normal) normal · \(watch) to watch" : "\(normal) normal")
                    .font(.opaw(13, .bold))
                    .foregroundStyle(watch > 0 ? Color.opawAlert : Color.opawMuted)
            }
            HStack(spacing: 4) {
                ForEach(Array(days.enumerated()), id: \.offset) { _, day in
                    RoundedRectangle(cornerRadius: 7, style: .continuous)
                        .fill(day == .normal ? Color.opawInk : day == .watch ? Color.opawAlert : Color.opawChip)
                        .frame(height: 26)
                }
            }
            .accessibilityElement(children: .ignore)
            .accessibilityLabel("\(normal) normal days and \(watch) days to watch in the last 14 days")
        }
        .card(padding: 20)
    }

    // MARK: Log

    private var logCard: some View {
        let events = (dog.careEvents ?? []).filter { $0.date >= start }.sorted { $0.date > $1.date }
        return VStack(alignment: .leading, spacing: 0) {
            Text("All logs")
                .font(.opaw(17, .black))
                .padding(.vertical, 12)
            if events.isEmpty {
                Text("Nothing logged in this range.")
                    .font(.opaw(15, .semibold))
                    .foregroundStyle(Color.opawMuted)
                    .padding(.bottom, 12)
            }
            ForEach(events.prefix(60)) { event in
                HStack(alignment: .firstTextBaseline) {
                    Text(event.date.formatted(.dateTime.month(.abbreviated).day()))
                        .font(.opaw(13, .bold))
                        .foregroundStyle(Color.opawMuted)
                        .frame(width: 52, alignment: .leading)
                    CareEventRow(event: event)
                }
                .overlay(alignment: .top) { Rectangle().fill(Color.opawHairline).frame(height: 1) }
            }
        }
        .padding(.horizontal, 20)
        .padding(.vertical, 4)
        .background(Color.opawSurface, in: RoundedRectangle(cornerRadius: Radius.card, style: .continuous))
        .shadow(color: .black.opacity(0.05), radius: 15, y: 12)
    }

    // MARK: Vet summary

    /// A plain-text summary a vet can read in a message or email.
    private var vetSummary: String {
        var lines: [String] = []
        lines.append("\(dog.name) — care summary from Opaw")
        var profile: [String] = []
        if !dog.breed.isEmpty { profile.append(dog.breed) }
        if let age = dog.ageDescription { profile.append(age) }
        if let weight = dog.weightKg { profile.append("\(weight.formatted(.number.precision(.fractionLength(1)))) kg") }
        if !profile.isEmpty { lines.append(profile.joined(separator: " · ")) }
        lines.append("")
        lines.append(weeklySummary)
        lines.append("")
        lines.append("Last 14 days:")
        let calendar = Calendar.current
        let since = calendar.date(byAdding: .day, value: -14, to: Date()) ?? Date()
        let recent = (dog.careEvents ?? []).filter { $0.date >= since }
        for kind in CareKind.allCases {
            let count = recent.filter { $0.kind == kind }.count
            if count > 0 { lines.append("• \(kind.title): \(count)") }
        }
        let flagged = recent.filter { ($0.poopConsistency?.isConcerning ?? false) || ($0.poopColor?.isConcerning ?? false) }
        for event in flagged.sorted(by: { $0.date < $1.date }) {
            var detail = [event.poopConsistency?.title, event.poopColor?.title].compactMap { $0 }.joined(separator: ", ")
            if !event.note.isEmpty { detail += " — \(event.note)" }
            lines.append("⚠︎ \(event.date.formatted(date: .abbreviated, time: .shortened)): poop \(detail)")
        }
        let walkKm = dog.walkedKm(from: since, to: Date().addingTimeInterval(60))
        lines.append("• Walked: \(OpawFormat.distance(meters: walkKm * 1000))")
        return lines.joined(separator: "\n")
    }
}
