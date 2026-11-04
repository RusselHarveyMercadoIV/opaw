import OpawKit
import SwiftData
import SwiftUI
import WidgetKit

/// Home: the pet as a big living sticker. Swipe between pets; the last page adds a new one.
struct HomeView: View {
    @Binding var selectedTab: AppTab

    @Query(sort: \Dog.createdAt) private var dogs: [Dog]
    @AppStorage(OpawDefaults.activeDogKey, store: OpawDefaults.shared) private var activeDogID = ""
    @State private var page = ""
    @State private var sheet: HomeSheet?

    private static let addPage = "add"

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                topBar
                TabView(selection: $page) {
                    ForEach(dogs) { dog in
                        PetPage(dog: dog, selectedTab: $selectedTab)
                            .tag(dog.id.uuidString)
                    }
                    AddPetPage { sheet = .addDog }
                        .tag(Self.addPage)
                }
                .tabViewStyle(.page(indexDisplayMode: .never))
                pageDots
                    .padding(.bottom, 92)
            }
            .opawBackground()
            .toolbar(.hidden, for: .navigationBar)
            .onAppear {
                if page.isEmpty { page = dogs.first { $0.id.uuidString == activeDogID }?.id.uuidString ?? dogs.first?.id.uuidString ?? "" }
            }
            .onChange(of: page) { _, newValue in
                guard newValue != Self.addPage, newValue != activeDogID else { return }
                activeDogID = newValue
                Haptics.select()
                WidgetCenter.shared.reloadTimelines(ofKind: OpawWidgetKind.quickLog)
            }
            .sheet(item: $sheet) { sheet in
                switch sheet {
                case .family: ProfileView()
                case .nearby: PackView()
                case .challenges: ChallengesView()
                case .addDog: AddDogSheet()
                }
            }
        }
    }

    private var topBar: some View {
        HStack(spacing: 10) {
            Button {
                sheet = .family
            } label: {
                HStack(spacing: 8) {
                    HStack(spacing: -12) {
                        ForEach(dogs.prefix(3)) { dog in
                            DogThumbnail(data: dog.cutoutData ?? dog.photoData, size: 36)
                                .overlay(Circle().stroke(Color.opawSurface, lineWidth: 3))
                        }
                    }
                    Text("Family")
                        .font(.opaw(15, .heavy))
                        .foregroundStyle(Color.opawInk)
                }
                .padding(5)
                .padding(.trailing, 9)
                .background(Color.opawSurface, in: Capsule())
                .floatingShadow()
            }
            .buttonStyle(.pressable)
            Spacer()
            Button { sheet = .nearby } label: { RoundIcon(symbol: "location.fill") }
                .buttonStyle(.pressable)
                .accessibilityLabel("Nearby pets")
            Button { sheet = .challenges } label: { RoundIcon(symbol: "trophy.fill") }
                .buttonStyle(.pressable)
                .accessibilityLabel("Challenges and badges")
        }
        .padding(.horizontal, Space.l)
        .padding(.top, 10)
    }

    private var pageDots: some View {
        HStack(spacing: 6) {
            ForEach(dogs.map(\.id.uuidString) + [Self.addPage], id: \.self) { id in
                Capsule()
                    .fill(id == page ? Color.opawInk : Color.opawInk.opacity(0.15))
                    .frame(width: id == page ? 18 : 6, height: 6)
            }
        }
        .animation(.spring(response: 0.35, dampingFraction: 0.8), value: page)
        .accessibilityHidden(true)
    }
}

enum HomeSheet: String, Identifiable {
    case family, nearby, challenges, addDog
    var id: String { rawValue }
}

// MARK: - Pet page

private struct PetPage: View {
    let dog: Dog
    @Binding var selectedTab: AppTab

    var body: some View {
        TimelineView(.periodic(from: .now, by: 60)) { context in
            let report = dog.careReport(now: context.date)
            let mood = MoodEngine.mood(for: BadgeService.careSnapshot(for: dog, now: context.date), now: context.date)
            ScrollView {
                VStack(spacing: Space.m) {
                    stage(report: report, mood: mood)
                    identity
                    Text("\u{201C}\(report.line)\u{201D}")
                        .font(.opaw(17, .bold))
                        .foregroundStyle(Color.opawInk.opacity(0.75))
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, Space.l)
                        .contentTransition(.opacity)
                    stats(now: context.date)
                        .padding(.horizontal, Space.l)
                }
                .padding(.top, Space.s)
                .padding(.bottom, Space.m)
            }
            .scrollIndicators(.hidden)
        }
    }

    private func stage(report: CareReport, mood: AvatarMood) -> some View {
        NavigationLink {
            AvatarStudioView(dog: dog)
        } label: {
            ZStack(alignment: .topTrailing) {
                ZStack(alignment: .bottom) {
                    Ellipse()
                        .fill(Color.black.opacity(0.07))
                        .frame(width: 190, height: 18)
                        .blur(radius: 7)
                    TierMotion(tier: report.tier) {
                        AvatarView(dog: dog, mood: mood)
                            .sticker()
                    }
                    .frame(height: 300)
                    .padding(.bottom, 8)
                }
                .frame(maxWidth: .infinity)

                VStack(alignment: .trailing, spacing: 8) {
                    Text(report.tier.sound)
                        .font(.opaw(16, .heavy))
                        .foregroundStyle(Color.opawInk)
                        .padding(.horizontal, 14)
                        .padding(.vertical, 9)
                        .background(Color.opawSurface, in: UnevenRoundedRectangle(topLeadingRadius: 20, bottomLeadingRadius: 6, bottomTrailingRadius: 20, topTrailingRadius: 20))
                        .floatingShadow()
                    HStack(spacing: 6) {
                        Circle()
                            .fill(report.tier.color)
                            .frame(width: 8, height: 8)
                        Text("\(report.score)")
                            .font(.opawNumber(15))
                            .contentTransition(.numericText())
                        Text(report.tier.title)
                            .font(.opaw(13, .bold))
                            .foregroundStyle(Color.opawMuted)
                    }
                    .padding(.horizontal, 12)
                    .padding(.vertical, 7)
                    .background(Color.opawSurface, in: Capsule())
                    .floatingShadow()
                }
                .padding(.trailing, Space.l)
                .padding(.top, 24)
            }
            .opacity(report.tier == .needsYou ? 0.8 : 1)
        }
        .buttonStyle(.plain)
        .accessibilityLabel("\(dog.name), \(report.tier.title), care score \(report.score). Open avatar studio.")
    }

    private var identity: some View {
        VStack(spacing: 10) {
            Text(dog.name)
                .font(.opawDisplay(44))
                .foregroundStyle(Color.opawInk)
                .lineLimit(1)
                .minimumScaleFactor(0.5)
            HStack(spacing: 6) {
                ForEach(chips, id: \.self) { chip in
                    Text(chip)
                        .font(.opaw(13, .bold))
                        .foregroundStyle(Color.opawInk)
                        .padding(.horizontal, 12)
                        .padding(.vertical, 7)
                        .background(Color.opawSurface, in: Capsule())
                        .floatingShadow()
                }
            }
        }
        .padding(.horizontal, Space.l)
    }

    private var chips: [String] {
        var result: [String] = []
        if !dog.breed.isEmpty { result.append(dog.breed) }
        if let age = dog.ageDescription { result.append(age) }
        if let weight = dog.weightKg { result.append("\(weight.formatted(.number.precision(.fractionLength(0...1)))) kg") }
        if result.isEmpty { result.append(dog.size.title) }
        return result
    }

    private func stats(now: Date) -> some View {
        let meals = dog.count(.meal, on: now)
        let mealGoal = max(1, dog.mealHours.count)
        let meds = dog.medsStatus(now: now)
        return HStack(spacing: 10) {
            HomeStat(value: "\(meals)/\(mealGoal)", label: "Meals")
            Button {
                selectedTab = .activity
            } label: {
                HomeStat(value: OpawFormat.distance(meters: dog.walkedKm(on: now) * 1000), label: "Walked")
            }
            .buttonStyle(.pressable)
            Button {
                selectedTab = .care
            } label: {
                HomeStat(
                    value: meds.next.map { $0.formatted(date: .omitted, time: .shortened) } ?? "None",
                    label: meds.overdue ? "Meds late" : "Meds due",
                    alert: meds.overdue
                )
            }
            .buttonStyle(.pressable)
        }
    }
}

private struct HomeStat: View {
    let value: String
    let label: String
    var alert = false

    var body: some View {
        VStack(spacing: 2) {
            Text(value)
                .font(.opawNumber(20))
                .foregroundStyle(alert ? Color.opawAlert : Color.opawInk)
                .lineLimit(1)
                .minimumScaleFactor(0.6)
            Text(label)
                .font(.opaw(13, .bold))
                .foregroundStyle(alert ? Color.opawAlert : Color.opawMuted)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 16)
        .background(Color.opawSurface, in: RoundedRectangle(cornerRadius: 24, style: .continuous))
        .overlay {
            if alert {
                RoundedRectangle(cornerRadius: 24, style: .continuous)
                    .strokeBorder(Color.opawAlert, lineWidth: 2)
            }
        }
        .shadow(color: .black.opacity(0.05), radius: 15, y: 12)
    }
}

/// Moves the avatar to match how it's doing: a bouncy bob, a slow breath, or a droop.
struct TierMotion<Content: View>: View {
    let tier: CareTier
    @ViewBuilder var content: Content
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        content
            .phaseAnimator([false, true]) { view, up in
                switch tier {
                case .thriving:
                    view
                        .offset(y: up && !reduceMotion ? -10 : 0)
                        .rotationEffect(.degrees(reduceMotion ? 0 : (up ? 1.5 : -1.5)), anchor: .bottom)
                case .okay:
                    view.scaleEffect(up && !reduceMotion ? 1.03 : 1, anchor: .bottom)
                case .needsYou:
                    view
                        .offset(y: up && !reduceMotion ? 12 : 8)
                        .rotationEffect(.degrees(up ? -1 : -3), anchor: .bottom)
                }
            } animation: { _ in
                switch tier {
                case .thriving: return .easeInOut(duration: 0.7)
                case .okay: return .easeInOut(duration: 1.6)
                case .needsYou: return .easeInOut(duration: 2)
                }
            }
    }
}

extension CareTier {
    var color: Color {
        switch self {
        case .thriving: return .opawGood
        case .okay: return .opawWarn
        case .needsYou: return .opawAlert
        }
    }
}

// MARK: - Add a pet

private struct AddPetPage: View {
    let onAdd: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: Space.m) {
            Spacer()
            Button(action: onAdd) {
                ZStack {
                    Circle()
                        .strokeBorder(Color.opawInk.opacity(0.25), style: StrokeStyle(lineWidth: 3, dash: [10, 10]))
                    Image(systemName: "plus")
                        .font(.system(size: 54, weight: .bold))
                        .foregroundStyle(Color.opawInk)
                }
                .frame(width: 240, height: 240)
                .frame(maxWidth: .infinity)
            }
            .buttonStyle(.pressable)
            .accessibilityLabel("Add a new pet")
            Spacer()
            Text("Add a new pet")
                .font(.opawDisplay(40))
                .foregroundStyle(Color.opawInk)
            Text("Snap a photo of another dog and they'll come to life too.")
                .font(.opaw(17, .semibold))
                .foregroundStyle(Color.opawMuted)
                .fixedSize(horizontal: false, vertical: true)
            Button(action: onAdd) {
                Label("Snap a photo", systemImage: "camera.fill")
            }
            .buttonStyle(.opawPrimary)
            .padding(.top, Space.s)
        }
        .padding(.horizontal, Space.l)
        .padding(.bottom, Space.m)
    }
}

/// A row in a log list, reused by Care and History.
struct CareEventRow: View {
    let event: CareEvent

    var body: some View {
        HStack(spacing: 12) {
            Text(event.date, style: .time)
                .font(.opaw(14, .bold))
                .foregroundStyle(Color.opawMuted)
                .frame(width: 52, alignment: .leading)
            Text(title)
                .font(.opaw(15, .semibold))
                .foregroundStyle(Color.opawInk)
            Spacer(minLength: 0)
        }
        .padding(.vertical, 10)
    }

    private var title: String {
        var text = event.kind.title
        if let consistency = event.poopConsistency {
            text += " · \(consistency.title.lowercased())"
        }
        if let color = event.poopColor, color != .brown {
            text += " · \(color.title.lowercased())"
        }
        if let weight = event.weightKg {
            text += " · \(weight.formatted(.number.precision(.fractionLength(1)))) kg"
        }
        if !event.note.isEmpty {
            text += " · \(event.note)"
        }
        return text
    }
}
