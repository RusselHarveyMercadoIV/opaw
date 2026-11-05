import OpawKit
import SwiftData
import SwiftUI

/// Challenges & badges (2f): this month's walking goal, the week-long logging challenge, and the badge collection.
struct ChallengesView: View {
    @Query(sort: \Dog.createdAt) private var dogs: [Dog]
    @AppStorage(OpawDefaults.activeDogKey, store: OpawDefaults.shared) private var activeDogID = ""

    @Environment(\.duoOpen) private var duoOpen

    private var dog: Dog? {
        dogs.first { $0.id.uuidString == activeDogID } ?? dogs.first
    }

    var body: some View {
        if duoOpen, let dog {
            duoBody(dog: dog)
        } else {
            phoneBody
        }
    }

    // MARK: Open display (6i)

    private func duoBody(dog: Dog) -> some View {
        let earned = dog.earnedBadges
        let newest = BadgeService.newestBadge(for: dog)
        let ordered = Badge.allCases.sorted { lhs, rhs in
            func rank(_ badge: Badge) -> Int { badge == newest ? 1 : (earned.contains(badge) ? 0 : 2) }
            return rank(lhs) < rank(rhs)
        }
        return DuoPanes {
            ScrollView {
                VStack(alignment: .leading, spacing: 0) {
                    PaneHeader(title: "Challenges")
                    monthCard(dog: dog, compact: true)
                        .padding(.top, 12)
                    streakCard(dog: dog, compact: true)
                        .padding(.top, 12)
                }
                .padding(.horizontal, 20)
                .padding(.bottom, 24)
            }
            .scrollIndicators(.hidden)
            .padding(.horizontal, -20)
        } right: {
            ScrollView {
                VStack(alignment: .leading, spacing: 0) {
                    PaneHeader(title: "Badges") {
                        Text("\(earned.count) of \(Badge.allCases.count)")
                            .font(.opaw(14, .bold))
                            .foregroundStyle(Color.opawMuted)
                    }
                    LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 0), count: 4), spacing: 20) {
                        ForEach(ordered) { badge in
                            BadgeCoin(badge: badge, earned: earned.contains(badge) || badge == newest, isNew: badge == newest)
                        }
                    }
                    .padding(.top, 14)
                }
                .padding(.horizontal, 20)
                .padding(.bottom, 24)
            }
            .scrollIndicators(.hidden)
            .padding(.horizontal, -20)
        }
        .opawBackground()
    }

    private var phoneBody: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                Text("Challenges")
                    .font(.opaw(36, .black))
                    .foregroundStyle(Color.opawInk)
                    .padding(.horizontal, 24)
                    .padding(.top, 12)
                if let dog {
                    monthCard(dog: dog)
                        .padding(.horizontal, 20)
                        .padding(.top, 16)
                    streakCard(dog: dog)
                        .padding(.horizontal, 20)
                        .padding(.top, 12)
                    badges(dog: dog)
                }
            }
            .padding(.top, 20)
            .padding(.bottom, 40)
        }
        .scrollIndicators(.hidden)
        .opawBackground()
        .presentationCornerRadius(40)
    }

    private func monthCard(dog: Dog, compact: Bool = false) -> some View {
        let calendar = Calendar.current
        let month = calendar.dateInterval(of: .month, for: Date()) ?? DateInterval(start: Date(), duration: 1)
        let goal = max(5, (dog.traits.recommendedWeeklyKm * 4.3 / 5).rounded() * 5)
        let done = dog.walkedKm(from: month.start, to: month.end)
        let daysLeft = max(0, calendar.dateComponents([.day], from: calendar.startOfDay(for: Date()), to: month.end).day ?? 0)
        return HStack(spacing: compact ? 18 : 20) {
            GoalRing(progress: done / goal, value: OpawFormat.longDistance(km: done).formatted(.number.precision(.fractionLength(1))), caption: "of \(Int(OpawFormat.longDistance(km: goal).rounded())) \(OpawFormat.distanceUnit)")
                .frame(width: 120, height: 120)
            VStack(alignment: .leading, spacing: 4) {
                Text(Date().formatted(.dateTime.month(.wide)))
                    .font(.opaw(13, .bold))
                    .foregroundStyle(Color.opawMuted)
                Text("Walk \(Int(goal)) km this month")
                    .font(.opaw(compact ? 20 : 22, .black))
                    .foregroundStyle(Color.opawInk)
                    .fixedSize(horizontal: false, vertical: true)
                Text(daysLeft == 1 ? "1 day left" : "\(daysLeft) days left")
                    .font(.opaw(compact ? 13 : 14, .semibold))
                    .foregroundStyle(compact ? Color.opawMuted : Color.opawSoftText)
            }
            Spacer(minLength: 0)
        }
        .padding(compact ? 20 : 22)
        .whiteCard(radius: compact ? 30 : 34, shadow: compact ? 0.05 : 0.06)
    }

    private func streakCard(dog: Dog, compact: Bool = false) -> some View {
        let streak = min(7, StreakCalculator.currentStreak(dates: (dog.careEvents ?? []).map(\.date)))
        return VStack(alignment: .leading, spacing: compact ? 10 : 12) {
            HStack(alignment: .firstTextBaseline) {
                Text("Log every day for a week")
                    .font(.opaw(compact ? 17 : 16, compact ? .black : .heavy))
                Spacer()
                Text("\(streak)/7")
                    .font(.opaw(compact ? 15 : 16, compact ? .heavy : .black))
            }
            .foregroundStyle(Color.opawInk)
            HStack(spacing: compact ? 5 : 6) {
                ForEach(0..<7, id: \.self) { index in
                    Capsule()
                        .fill(index < streak ? Color.opawInk : (compact ? Color.opawTrack : Color.opawHairline))
                        .frame(height: compact ? 8 : 10)
                }
            }
            Text("Reward: \(dog.name) earns the \(Badge.careStreak7.title) badge")
                .font(.opaw(13 + (compact ? 0 : 1), compact ? .semibold : .medium))
                .foregroundStyle(Color.opawMuted)
        }
        .padding(.horizontal, compact ? 20 : 22)
        .padding(.vertical, 18)
        .whiteCard(shadow: compact ? 0.05 : 0.06)
    }

    private func badges(dog: Dog) -> some View {
        let earned = dog.earnedBadges
        let newest = BadgeService.newestBadge(for: dog)
        // Earned first (newest at the front), then the rest.
        let ordered = Badge.allCases.sorted { lhs, rhs in
            func rank(_ badge: Badge) -> Int { badge == newest ? 0 : (earned.contains(badge) ? 1 : 2) }
            return rank(lhs) < rank(rhs)
        }
        return VStack(alignment: .leading, spacing: 0) {
            HStack(alignment: .firstTextBaseline) {
                Text("Badges")
                    .font(.opaw(21, .black))
                    .foregroundStyle(Color.opawInk)
                Spacer()
                Text("\(earned.count) of \(Badge.allCases.count)")
                    .font(.opaw(14, .bold))
                    .foregroundStyle(Color.opawMuted)
            }
            .padding(.horizontal, 24)
            .padding(.top, 22)
            LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 10), count: 3), spacing: 16) {
                ForEach(ordered) { badge in
                    BadgeMedal(badge: badge, earned: earned.contains(badge), isNew: badge == newest)
                }
            }
            .padding(.horizontal, 20)
            .padding(.top, 14)
        }
    }
}

/// A 94pt round badge: black when earned, red "NEW" when just earned, white while still to earn.
private struct BadgeMedal: View {
    let badge: Badge
    let earned: Bool
    let isNew: Bool

    var body: some View {
        VStack(spacing: 8) {
            Text(isNew ? "NEW" : badge.medal)
                .font(.opaw(26, .black))
                .foregroundStyle(earned || isNew ? Color.white : Color.opawInk.opacity(0.35))
                .lineLimit(1)
                .minimumScaleFactor(0.6)
                .frame(width: 94, height: 94)
                .background(isNew ? Color.opawAlert : (earned ? Color(hex: 0x111111) : Color.opawSurface), in: Circle())
                .shadow(color: isNew ? Color.opawAlert.opacity(0.35) : .black.opacity(earned ? 0.2 : 0.08), radius: 13, y: 12)
            Text(isNew ? badge.title : badge.medalLabel)
                .font(.opaw(14, .bold))
                .foregroundStyle(earned || isNew ? Color.opawInk : Color.opawMuted)
                .multilineTextAlignment(.center)
                .lineLimit(2)
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("\(badge.title). \(badge.detail). \(earned ? "Earned" : "Not earned yet")")
    }
}

/// A 78pt white coin with the badge's number (6i): a red NEW tag when just earned, gray with "?" while locked.
private struct BadgeCoin: View {
    let badge: Badge
    let earned: Bool
    let isNew: Bool

    var body: some View {
        VStack(spacing: 6) {
            Text(earned ? badge.medal : "?")
                .font(.opaw(24, .black))
                .foregroundStyle(earned ? Color.opawInk : Color(hex: 0xCCCCCC))
                .lineLimit(1)
                .minimumScaleFactor(0.6)
                .frame(width: 78, height: 78)
                .background(earned ? Color.opawSurface : Color(hex: 0xECECEE), in: Circle())
                .shadow(color: .black.opacity(earned ? 0.06 : 0), radius: 12, y: 10)
                .overlay(alignment: .topTrailing) {
                    if isNew {
                        Text("NEW")
                            .font(.opaw(10, .heavy))
                            .foregroundStyle(.white)
                            .padding(.horizontal, 7)
                            .padding(.vertical, 3)
                            .background(Color.opawAlert, in: Capsule())
                            .offset(x: 6, y: -4)
                    }
                }
            Text(earned ? badge.medalLabel : "Locked")
                .font(.opaw(13, .bold))
                .foregroundStyle(earned ? Color.opawInk : Color.opawFaint)
                .multilineTextAlignment(.center)
                .lineLimit(2)
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("\(badge.title). \(badge.detail). \(earned ? "Earned" : "Not earned yet")")
    }
}

extension Badge {
    /// The big text on the medal.
    var medal: String {
        switch self {
        case .firstLog, .firstWalk: return "1st"
        case .careStreak7, .walkStreak7: return "7"
        case .careStreak30: return "30"
        case .walks10, .memoryKeeper: return "10"
        case .walks100, .km100Total: return "100"
        case .km5Walk: return "5K"
        case .km10Walk: return "10K"
        case .km50Month: return "50"
        case .earlyBird: return "7am"
        case .squeakyClean, .packLeader: return "5"
        case .healthCheck: return "Vet"
        }
    }

    /// The caption under the medal.
    var medalLabel: String {
        switch self {
        case .firstLog: return "First log"
        case .firstWalk: return "First walk"
        case .careStreak7, .careStreak30: return "day streak"
        case .walkStreak7: return "walk streak"
        case .walks10, .walks100: return "walks"
        case .km5Walk, .km10Walk: return "in one walk"
        case .km50Month: return "km in a month"
        case .km100Total: return "km walked"
        case .earlyBird: return "Early bird"
        case .squeakyClean: return "baths"
        case .memoryKeeper: return "memories"
        case .healthCheck: return "Health check"
        case .packLeader: return "pack friends"
        }
    }
}
