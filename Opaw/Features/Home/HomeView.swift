import OpawKit
import SwiftData
import SwiftUI
import WidgetKit

/// Home (2a): the pet as a big living sticker. Swipe between pets; the last page adds a new one (2h).
struct HomeView: View {
    @Binding var selectedTab: AppTab

    @Query(sort: \Dog.createdAt) private var dogs: [Dog]
    @AppStorage(OpawDefaults.activeDogKey, store: OpawDefaults.shared) private var activeDogID = ""
    @State private var page = ""
    @State private var sheet: HomeSheet?
    @State private var showPaywall = false
    @Environment(PremiumStore.self) private var premium
    @State private var path = NavigationPath()

    private static let addPage = "add"

    private var currentDog: Dog? {
        dogs.first { $0.id.uuidString == page } ?? dogs.first { $0.id.uuidString == activeDogID } ?? dogs.first
    }

    var body: some View {
        NavigationStack(path: $path) {
            ZStack(alignment: .top) {
                TabView(selection: $page) {
                    ForEach(dogs) { dog in
                        PetPage(dog: dog, dots: dots) { path.append(HomeRoute.today(dog.id)) }
                            .tag(dog.id.uuidString)
                    }
                    AddPetPage(dots: dots, locked: !premium.isPremium && !dogs.isEmpty) { sheet = premium.isPremium || dogs.isEmpty ? .addDog : .petLimit }
                        .tag(Self.addPage)
                }
                .tabViewStyle(.page(indexDisplayMode: .never))
                .ignoresSafeArea(edges: .bottom)

                topBar
            }
            .opawBackground()
            .toolbar(.hidden, for: .navigationBar)
            .navigationDestination(for: HomeRoute.self) { route in
                switch route {
                case .today(let id):
                    if let dog = dogs.first(where: { $0.id == id }) { TodayView(dog: dog) }
                case .family(let id):
                    if let dog = dogs.first(where: { $0.id == id }) {
                        FamilyTreeView(dog: dog) { selected in
                            page = selected.id.uuidString
                        }
                    }
                case .studio(let id):
                    if let dog = dogs.first(where: { $0.id == id }) { AvatarStudioView(dog: dog) }
                }
            }
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
                case .nearby: PackView()
                case .addDog: AddDogSheet()
                case .petLimit: PetLimitSheet(dog: currentDog ?? dogs.first) { showPaywall = true }
                }
            }
            .premiumPaywall(isPresented: $showPaywall)
        }
    }

    // MARK: Top bar

    private var topBar: some View {
        HStack(spacing: 10) {
            Button {
                if let dog = currentDog { path.append(HomeRoute.family(dog.id)) }
            } label: {
                familyChip
            }
            .buttonStyle(.pressable)
            .accessibilityLabel("Family")
            Spacer()
            if page != Self.addPage, let dog = currentDog {
                Button { sheet = .nearby } label: { TopCircle { Icon(.heart, size: 22) } }
                    .buttonStyle(.pressable)
                    .accessibilityLabel("Friends nearby")
                ShareAvatarButton(dog: dog)
            }
        }
        .padding(.horizontal, 20)
        .padding(.top, 10)
    }

    private var familyChip: some View {
        let faces: [Data?] = {
            var result = dogs.prefix(2).map { $0.cutoutData ?? $0.photoData }
            if result.count < 2, let relative = currentDog?.relatives.first { result.append(relative.photoData) }
            while result.count < 2 { result.append(nil) }
            return result
        }()
        return HStack(spacing: 10) {
            HStack(spacing: -16) {
                ForEach(Array(faces.enumerated()), id: \.offset) { index, data in
                    MiniFace(data: data, size: 42, fill: index == 0 ? Color.opawChip : Color.opawTrack)
                }
            }
            Text("Family")
                .font(.opaw(15, .heavy))
                .foregroundStyle(Color.opawInk)
            Icon(.chevronRight, size: 16)
                .foregroundStyle(Color.opawInk)
        }
        .padding(.leading, 8)
        .padding(.trailing, 18)
        .frame(height: 58)
        .background(Color.opawSurface, in: Capsule())
        .floatingShadow()
    }

    /// Page dots: one per pet, then a dashed circle for "add".
    private var dots: AnyView {
        let ids = dogs.map(\.id.uuidString)
        let current = page
        return AnyView(
            HStack(spacing: 6) {
                ForEach(ids, id: \.self) { id in
                    Capsule()
                        .fill(id == current ? Color.opawInk : Color(hex: 0xCCCCCC))
                        .frame(width: id == current ? 22 : 8, height: 8)
                }
                if current == Self.addPage {
                    Capsule().fill(Color.opawInk).frame(width: 22, height: 8)
                } else {
                    Button {
                        withAnimation { page = Self.addPage }
                    } label: {
                        Circle()
                            .strokeBorder(Color.opawGhost, style: StrokeStyle(lineWidth: 2, dash: [3, 2.5]))
                            .frame(width: 16, height: 16)
                            .padding(.leading, 2)
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel("Add a pet")
                }
            }
            .animation(.spring(response: 0.35, dampingFraction: 0.8), value: current)
        )
    }
}

enum HomeSheet: String, Identifiable {
    case nearby, addDog, petLimit
    var id: String { rawValue }
}

enum HomeRoute: Hashable {
    case today(UUID)
    case family(UUID)
    case studio(UUID)
}

/// A 58pt white circle used in the Home top bar.
private struct TopCircle<Content: View>: View {
    @ViewBuilder var content: Content

    var body: some View {
        content
            .foregroundStyle(Color.opawInk)
            .frame(width: 58, height: 58)
            .background(Color.opawSurface, in: Circle())
            .floatingShadow()
    }
}

/// Shares the pet's sticker image.
private struct ShareAvatarButton: View {
    let dog: Dog

    var body: some View {
        Group {
            if let data = dog.cutoutData ?? dog.photoData, let image = UIImage(data: data) {
                let picture = Image(uiImage: image)
                ShareLink(item: picture, preview: SharePreview(dog.name, image: picture)) { label }
            } else {
                ShareLink(item: "Meet \(dog.name) on Opaw.") { label }
            }
        }
        .buttonStyle(.pressable)
        .accessibilityLabel("Share \(dog.name)")
    }

    private var label: some View {
        TopCircle {
            Icon(.share, size: 22)
        }
    }
}

/// A small round face with a white rim.
struct MiniFace: View {
    let data: Data?
    var size: CGFloat = 42
    var rim: CGFloat = 3
    var fill: Color = .opawChip
    var initial: String?

    var body: some View {
        ZStack {
            Circle().fill(fill)
            if let data, let image = UIImage(data: data) {
                Image(uiImage: image)
                    .resizable()
                    .scaledToFill()
            } else if let initial {
                Text(initial)
                    .font(.opaw(size * 0.4, .black))
                    .foregroundStyle(Color.opawMuted)
            }
        }
        .frame(width: size - rim * 2, height: size - rim * 2)
        .clipShape(Circle())
        .padding(rim)
        .background(Color.opawSurface, in: Circle())
    }
}

// MARK: - Pet page

private struct PetPage: View {
    let dog: Dog
    let dots: AnyView
    let openToday: () -> Void

    var body: some View {
        TimelineView(.periodic(from: .now, by: 60)) { context in
            let report = dog.careReport(now: context.date)
            let mood = MoodEngine.mood(for: BadgeService.careSnapshot(for: dog, now: context.date), now: context.date)
            ScrollView {
                VStack(spacing: 0) {
                    stage(report: report, mood: mood)
                    VStack(spacing: 10) {
                        Text(dog.name)
                            .font(.opaw(50, .black))
                            .tracking(-0.5)
                            .foregroundStyle(Color.opawInk)
                            .lineLimit(1)
                            .minimumScaleFactor(0.5)
                        dots
                        chipRow
                        Text("\u{201C}\(report.line)\u{201D}")
                            .font(.opaw(17, .medium))
                            .foregroundStyle(Color.opawSoftText)
                            .lineSpacing(4)
                            .multilineTextAlignment(.center)
                            .contentTransition(.opacity)
                    }
                    .padding(.horizontal, 36)
                    .padding(.top, 14)
                    stats(now: context.date)
                        .padding(.horizontal, 20)
                        .padding(.top, 18)
                }
                .padding(.top, 92)
                .padding(.bottom, 120)
            }
            .scrollIndicators(.hidden)
        }
    }

    private func stage(report: CareReport, mood: AvatarMood) -> some View {
        NavigationLink(value: HomeRoute.studio(dog.id)) {
            ZStack(alignment: .topTrailing) {
                AvatarView(dog: dog, mood: mood)
                    .sticker(edge: 7, blur: 13, y: 22)
                    .loopMotion(report.tier.motion)
                    .opacity(report.tier == .needsYou ? 0.6 : 1)
                    .padding(.horizontal, 78)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                OutlinedText(text: report.tier.sound, size: 28)
                    .loopMotion(.tagPop)
                    .padding(.trailing, 52)
                    .padding(.top, 90)
                    .allowsHitTesting(false)
            }
            .frame(height: 290)
        }
        .buttonStyle(.plain)
        .accessibilityLabel("\(dog.name), \(report.tier.title). Open avatar studio.")
    }

    private var chipRow: some View {
        HStack(spacing: 8) {
            ForEach(chips, id: \.self) { chip in
                Text(chip)
                    .font(.opaw(14, .heavy))
                    .foregroundStyle(Color.opawInk)
                    .lineLimit(1)
                    .padding(.horizontal, 14)
                    .padding(.vertical, 7)
                    .background(Color.opawSurface, in: Capsule())
                    .shadow(color: .black.opacity(0.06), radius: 8, y: 6)
            }
        }
    }

    private var chips: [String] {
        var result: [String] = []
        if !dog.breed.isEmpty { result.append(dog.breed) }
        if let age = dog.ageDescription { result.append(age) }
        if let weight = dog.weightKg { result.append(OpawFormat.weight(kg: weight)) }
        if result.isEmpty { result.append(dog.size.title) }
        return result
    }

    private func stats(now: Date) -> some View {
        let meals = dog.count(.meal, on: now)
        let mealGoal = max(1, dog.mealHours.count)
        let meds = dog.medsStatus(now: now)
        return Button(action: openToday) {
            HStack(spacing: 0) {
                StatColumn(value: "\(meals)/\(mealGoal)", label: "Meals")
                StatColumn(value: OpawFormat.distance(meters: dog.walkedKm(on: now) * 1000), label: "Walked")
                StatColumn(
                    value: meds.next.map { $0.formatted(date: .omitted, time: .shortened) } ?? "None",
                    label: "Meds due",
                    valueColor: meds.next == nil ? .opawInk : .opawAlert
                )
            }
            .padding(.vertical, 18)
            .padding(.horizontal, 12)
            .whiteCard()
        }
        .buttonStyle(.pressable)
        .accessibilityHint("Opens today's log")
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

    /// How the avatar moves: a bouncy bob, a slow breath, or a droop.
    var motion: LoopMotion {
        switch self {
        case .thriving: return .bob
        case .okay: return .breathe
        case .needsYou: return .droop
        }
    }
}

// MARK: - Add a pet (2h)

private struct AddPetPage: View {
    let dots: AnyView
    var locked = false
    let onAdd: () -> Void

    var body: some View {
        ScrollView {
            VStack(spacing: 0) {
                ZStack(alignment: .top) {
                    GhostCycle()
                        .frame(width: 190, height: 210)
                        .padding(.top, 70)
                    Button(action: onAdd) {
                        Icon(.plus, size: 34, weight: 2.8)
                            .foregroundStyle(.white)
                            .frame(width: 88, height: 88)
                            .background(Color(hex: 0x111111), in: Circle())
                            .shadow(color: .black.opacity(0.25), radius: 18, y: 18)
                            .overlay(alignment: .topTrailing) {
                                if locked {
                                    Icon(.lock, size: 16)
                                        .foregroundStyle(.white)
                                        .frame(width: 34, height: 34)
                                        .background(Color(hex: 0x111111), in: Circle())
                                        .overlay(Circle().strokeBorder(Color.opawCream, lineWidth: 3))
                                        .offset(x: 6, y: -6)
                                }
                            }
                    }
                    .buttonStyle(.pressable)
                    .padding(.top, 150)
                    .accessibilityLabel("Add a new pet")
                }
                .frame(maxWidth: .infinity)
                .frame(height: 340, alignment: .top)

                VStack(spacing: 10) {
                    Text("Add a new pet")
                        .font(.opaw(40, .black))
                        .foregroundStyle(Color.opawInk)
                    dots
                    Text("Snap a photo of another dog, cat or bunny and they'll come to life too.")
                        .font(.opaw(17, .medium))
                        .foregroundStyle(Color.opawSoftText)
                        .lineSpacing(4)
                        .multilineTextAlignment(.center)
                        .fixedSize(horizontal: false, vertical: true)
                }
                .padding(.horizontal, 40)
                .padding(.top, 14)
            }
            .padding(.top, 72)
            .padding(.bottom, 120)
        }
        .scrollIndicators(.hidden)
    }
}

/// Three faint silhouettes (dog, cat, bunny) taking turns to appear, every 9 seconds.
private struct GhostCycle: View {
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        TimelineView(.animation(paused: reduceMotion)) { context in
            let time = context.date.timeIntervalSinceReferenceDate
            ZStack {
                ForEach(0..<3, id: \.self) { index in
                    let state = Self.state(at: time - Double(index) * 3)
                    GhostShape(kind: index)
                        .fill(Color(hex: 0x111111).opacity(0.09))
                        .opacity(reduceMotion ? (index == 0 ? 1 : 0) : state.opacity)
                        .scaleEffect(reduceMotion ? 1 : state.scale)
                }
            }
        }
        .accessibilityHidden(true)
    }

    /// The design's ghost-cycle keyframes: fade in by 6%, hold to 28%, fade out by 34%.
    static func state(at time: Double) -> (opacity: Double, scale: Double) {
        let t = (time.truncatingRemainder(dividingBy: 9) + 9).truncatingRemainder(dividingBy: 9) / 9
        switch t {
        case ..<0.06:
            let p = t / 0.06
            return (p, 0.94 + 0.06 * p)
        case ..<0.28:
            return (1, 1)
        case ..<0.34:
            let p = (t - 0.28) / 0.06
            return (1 - p, 1 + 0.03 * p)
        default:
            return (0, 1.03)
        }
    }
}

/// Silhouettes drawn in a 200×220 box: 0 dog, 1 cat, 2 bunny.
private struct GhostShape: Shape {
    let kind: Int

    func path(in rect: CGRect) -> Path {
        var path = Path()
        func ellipse(_ cx: CGFloat, _ cy: CGFloat, _ rx: CGFloat, _ ry: CGFloat, rotate degrees: CGFloat = 0) {
            let oval = Path(ellipseIn: CGRect(x: -rx, y: -ry, width: rx * 2, height: ry * 2))
            let transform = CGAffineTransform(rotationAngle: degrees * .pi / 180).concatenating(CGAffineTransform(translationX: cx, y: cy))
            path.addPath(oval, transform: transform)
        }
        func pill(_ x: CGFloat, _ y: CGFloat, _ w: CGFloat, _ h: CGFloat) {
            path.addRoundedRect(in: CGRect(x: x, y: y, width: w, height: h), cornerSize: CGSize(width: w / 2, height: w / 2))
        }
        switch kind {
        case 0:
            ellipse(100, 165, 56, 50)
            ellipse(100, 78, 40, 40)
            ellipse(64, 70, 13, 30, rotate: 22)
            ellipse(136, 70, 13, 30, rotate: -22)
            ellipse(100, 100, 20, 15)
            pill(70, 180, 20, 36)
            pill(110, 180, 20, 36)
        case 1:
            ellipse(100, 160, 46, 56)
            ellipse(100, 82, 36, 36)
            path.addLines([CGPoint(x: 68, y: 66), CGPoint(x: 74, y: 28), CGPoint(x: 96, y: 52)])
            path.closeSubpath()
            path.addLines([CGPoint(x: 132, y: 66), CGPoint(x: 126, y: 28), CGPoint(x: 104, y: 52)])
            path.closeSubpath()
            var tail = Path()
            tail.move(to: CGPoint(x: 144, y: 196))
            tail.addCurve(to: CGPoint(x: 164, y: 134), control1: CGPoint(x: 174, y: 190), control2: CGPoint(x: 180, y: 156))
            path.addPath(tail.strokedPath(StrokeStyle(lineWidth: 14, lineCap: .round)))
            pill(76, 184, 18, 32)
            pill(106, 184, 18, 32)
        default:
            ellipse(100, 168, 50, 46)
            ellipse(100, 104, 32, 32)
            ellipse(84, 48, 11, 38, rotate: -8)
            ellipse(116, 48, 11, 38, rotate: 8)
            ellipse(150, 186, 14, 14)
        }
        let scale = min(rect.width / 200, rect.height / 220)
        return path.applying(CGAffineTransform(scaleX: scale, y: scale).concatenating(CGAffineTransform(translationX: rect.minX, y: rect.minY)))
    }
}

/// A row in a log list, reused by History.
struct CareEventRow: View {
    let event: CareEvent

    var body: some View {
        HStack(spacing: 0) {
            Text(event.date.formatted(date: .omitted, time: .shortened))
                .font(.opaw(15, .semibold))
                .foregroundStyle(Color.opawFaint)
                .frame(width: 56, alignment: .leading)
            Text(event.summary)
                .font(.opaw(15, .semibold))
                .foregroundStyle(Color.opawInk)
            Spacer(minLength: 0)
        }
        .padding(.vertical, 13)
    }
}

extension CareEvent {
    /// "Poop · normal", "Weight · 9.8 kg", "Breakfast · 120 g".
    var summary: String {
        var text = kind.title
        if let consistency = poopConsistency {
            text += " · \(consistency.title.lowercased())"
        }
        if let color = poopColor, color != .brown {
            text += " · \(color.title.lowercased())"
        }
        if let weight = weightKg {
            text += " · \(OpawFormat.weight(kg: weight))"
        }
        if !note.isEmpty {
            text += " · \(note)"
        }
        return text
    }
}
