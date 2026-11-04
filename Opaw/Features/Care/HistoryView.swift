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
            VStack(spacing: 0) {
                HStack(spacing: 6) {
                    ForEach(Range.allCases) { option in
                        FilterPill(title: option.rawValue, isSelected: option == range) {
                            withAnimation(.snappy) { range = option }
                            Haptics.select()
                        }
                    }
                    Spacer()
                }
                .padding(.horizontal, 20)
                .padding(.top, 14)

                summaryCard
                    .padding(.horizontal, 20)
                    .padding(.top, 14)
                weightCard
                    .padding(.horizontal, 20)
                    .padding(.top, 12)
                poopCard
                    .padding(.horizontal, 20)
                    .padding(.top, 12)
            }
            .padding(.bottom, 120)
        }
        .scrollIndicators(.hidden)
        .safeAreaInset(edge: .top, spacing: 0) {
            ScreenHeader(title: "History") {
                Button { dismiss() } label: { RoundIcon(symbol: "chevron.left") }
                    .buttonStyle(.pressable)
                    .accessibilityLabel("Back")
            } trailing: {
                ShareLink(item: vetSummary) { RoundIcon(symbol: "square.and.arrow.up") }
                    .buttonStyle(.pressable)
                    .accessibilityLabel("Share summary")
            }
            .background(Color.opawCream)
        }
        .opawBackground()
        .overlay(alignment: .bottom) {
            ShareLink(item: vetSummary) {
                HStack(spacing: 10) {
                    Image(systemName: "doc.text")
                        .font(.system(size: 18, weight: .bold))
                    Text("Share vet summary")
                        .font(.opaw(17, .heavy))
                }
                .foregroundStyle(.white)
                .frame(maxWidth: .infinity)
                .frame(height: 62)
                .background(Color(hex: 0x111111), in: Capsule())
            }
            .buttonStyle(.pressable)
            .padding(.horizontal, 20)
            .padding(.bottom, 4)
        }
        .toolbar(.hidden, for: .navigationBar)
        .hidesFloatingTabBar()
    }

    // MARK: Summary

    private var summaryCard: some View {
        HStack(alignment: .top, spacing: 12) {
            Image(systemName: "sparkles")
                .font(.system(size: 18, weight: .bold))
                .foregroundStyle(.white)
                .padding(.top, 2)
            VStack(alignment: .leading, spacing: 4) {
                Text("This week, summarised on your phone")
                    .font(.opaw(13, .bold))
                    .foregroundStyle(Color(hex: 0xAAAAAA))
                Text(weeklySummary)
                    .font(.opaw(16, .bold))
                    .lineSpacing(5)
                    .foregroundStyle(.white)
                    .fixedSize(horizontal: false, vertical: true)
            }
            Spacer(minLength: 0)
        }
        .padding(.horizontal, 20)
        .padding(.vertical, 18)
        .background(Color(hex: 0x111111), in: RoundedRectangle(cornerRadius: 30, style: .continuous))
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
                    (Text(latest.kg.formatted(.number.precision(.fractionLength(1)))) + Text(" kg").font(.opaw(14, .black)).foregroundColor(Color.opawMuted))
                        .font(.opaw(22, .black))
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
                            .interpolationMethod(.linear)
                        PointMark(x: .value("Date", point.date), y: .value("kg", point.kg))
                            .symbol {
                                if index == points.count - 1 {
                                    Circle().fill(Color.opawInk).frame(width: 12, height: 12)
                                } else {
                                    Circle().fill(Color.opawSurface).frame(width: 7, height: 7)
                                        .overlay(Circle().stroke(Color.opawInk, lineWidth: 2.5))
                                }
                            }
                    }
                }
                .chartYScale(domain: .automatic(includesZero: false))
                .chartYAxis(.hidden)
                .chartXAxis {
                    AxisMarks(values: .automatic(desiredCount: 6)) { _ in
                        AxisValueLabel(format: range == .week ? .dateTime.weekday(.abbreviated) : .dateTime.month(.abbreviated))
                            .font(.opaw(12, .semibold))
                            .foregroundStyle(Color.opawFaint)
                    }
                }
                .frame(height: 110)
            }
        }
        .padding(.horizontal, 20)
        .padding(.top, 18)
        .padding(.bottom, 14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .whiteCard()
    }

    // MARK: Poop

    private enum PoopDay: Equatable { case empty, normal, other(PoopConsistency?) }

    /// One entry per day, oldest first.
    private func poopDays(count: Int) -> [PoopDay] {
        let calendar = Calendar.current
        let today = calendar.startOfDay(for: Date())
        return (0..<count).reversed().map { offset in
            let day = calendar.date(byAdding: .day, value: -offset, to: today) ?? today
            let poops = dog.events(on: day).filter { $0.kind == .poop }
            if poops.isEmpty { return .empty }
            if let off = poops.first(where: { ($0.poopConsistency ?? .normal) != .normal || ($0.poopColor?.isConcerning ?? false) }) {
                return .other(off.poopConsistency)
            }
            return .normal
        }
    }

    private var poopCard: some View {
        let days = poopDays(count: 14)
        let normal = days.filter { $0 == .normal }.count
        var others: [String: Int] = [:]
        for day in days {
            if case .other(let consistency) = day { others[(consistency?.title ?? "Off").lowercased(), default: 0] += 1 }
        }
        let caption = ([("\(normal) normal")] + others.sorted { $0.value > $1.value }.map { "\($0.value) \($0.key)" }).joined(separator: " · ")
        return VStack(alignment: .leading, spacing: 12) {
            HStack(alignment: .firstTextBaseline) {
                Text("Poop, last 14 days")
                    .font(.opaw(17, .black))
                Spacer()
                Text(caption)
                    .font(.opaw(13, .bold))
                    .foregroundStyle(Color.opawMuted)
            }
            HStack(spacing: 4) {
                ForEach(Array(days.enumerated()), id: \.offset) { _, day in
                    RoundedRectangle(cornerRadius: 7, style: .continuous)
                        .fill(day == .normal ? Color.opawInk : day == .empty ? Color(hex: 0xEEEEEF) : Color(hex: 0xD9D9DC))
                        .frame(height: 26)
                }
            }
            .accessibilityElement(children: .ignore)
            .accessibilityLabel(caption)
        }
        .padding(.horizontal, 20)
        .padding(.vertical, 16)
        .whiteCard()
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
