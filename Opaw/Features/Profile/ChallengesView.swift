import OpawKit
import SwiftData
import SwiftUI

/// Challenges for this month and the badge collection.
struct ChallengesView: View {
    @Query(sort: \Dog.createdAt) private var dogs: [Dog]
    @AppStorage(OpawDefaults.activeDogKey, store: OpawDefaults.shared) private var activeDogID = ""
    @Environment(\.dismiss) private var dismiss

    private var dog: Dog? {
        dogs.first { $0.id.uuidString == activeDogID } ?? dogs.first
    }

    var body: some View {
        ScrollView {
            VStack(spacing: 14) {
                ScreenHeader(title: "Challenges") {
                    Button { dismiss() } label: { RoundIcon(symbol: "xmark") }
                        .buttonStyle(.pressable)
                        .accessibilityLabel("Close")
                } trailing: {
                    EmptyView()
                }
                if let dog {
                    monthCard(dog: dog)
                        .padding(.horizontal, Space.l)
                        .padding(.top, 6)
                    streakCard(dog: dog)
                        .padding(.horizontal, Space.l)
                    badgesCard(dog: dog)
                        .padding(.horizontal, Space.l)
                }
            }
            .padding(.bottom, Space.xl)
        }
        .scrollIndicators(.hidden)
        .opawBackground()
    }

    private func monthCard(dog: Dog) -> some View {
        let calendar = Calendar.current
        let month = calendar.dateInterval(of: .month, for: Date()) ?? DateInterval(start: Date(), duration: 1)
        let goal = max(5, (dog.traits.recommendedWeeklyKm * 4.3 / 5).rounded() * 5)
        let done = dog.walkedKm(from: month.start, to: month.end)
        let daysLeft = max(0, calendar.dateComponents([.day], from: calendar.startOfDay(for: Date()), to: month.end).day ?? 0)
        return HStack(spacing: 18) {
            ZStack {
                ProgressRing(progress: done / goal, lineWidth: 12)
                VStack(spacing: 0) {
                    Text(done.formatted(.number.precision(.fractionLength(1))))
                        .font(.opawNumber(24))
                    Text("of \(Int(goal)) km")
                        .font(.opaw(12, .bold))
                        .foregroundStyle(Color.opawMuted)
                }
            }
            .frame(width: 104, height: 104)
            VStack(alignment: .leading, spacing: 4) {
                Text(Date().formatted(.dateTime.month(.wide)))
                    .font(.opaw(14, .bold))
                    .foregroundStyle(Color.opawMuted)
                Text("Walk \(Int(goal)) km this month")
                    .font(.opaw(20, .black))
                    .fixedSize(horizontal: false, vertical: true)
                Text(daysLeft == 1 ? "1 day left" : "\(daysLeft) days left")
                    .font(.opaw(14, .semibold))
                    .foregroundStyle(Color.opawMuted)
            }
            Spacer(minLength: 0)
        }
        .card(padding: 20)
    }

    private func streakCard(dog: Dog) -> some View {
        let streak = min(7, StreakCalculator.currentStreak(dates: (dog.careEvents ?? []).map(\.date)))
        return VStack(alignment: .leading, spacing: 12) {
            HStack {
                Text("Log every day for a week")
                    .font(.opaw(17, .black))
                Spacer()
                Text("\(streak)/7")
                    .font(.opawNumber(17))
            }
            HStack(spacing: 6) {
                ForEach(0..<7, id: \.self) { index in
                    Capsule()
                        .fill(index < streak ? Color.opawInk : Color.opawChip)
                        .frame(height: 10)
                }
            }
            Text("Reward: the \(Badge.careStreak7.title) badge.")
                .font(.opaw(14, .semibold))
                .foregroundStyle(Color.opawMuted)
        }
        .card(padding: 20)
    }

    private func badgesCard(dog: Dog) -> some View {
        let earned = dog.earnedBadges
        return VStack(alignment: .leading, spacing: 14) {
            HStack(alignment: .firstTextBaseline) {
                Text("Badges")
                    .font(.opaw(17, .black))
                Spacer()
                Text("\(earned.count) of \(Badge.allCases.count)")
                    .font(.opaw(13, .bold))
                    .foregroundStyle(Color.opawMuted)
            }
            BadgeGrid(dog: dog)
        }
        .card(padding: 20)
    }
}
