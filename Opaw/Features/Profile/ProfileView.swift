import OpawKit
import SwiftData
import SwiftUI
import WidgetKit

/// Settings (5e): the pet, the Premium banner for free users, then household, settings and Premium features.
struct ProfileView: View {
    @Query(sort: \Dog.createdAt) private var dogs: [Dog]
    @AppStorage(OpawDefaults.activeDogKey, store: OpawDefaults.shared) private var activeDogID = ""
    @Environment(PremiumStore.self) private var premium
    @Environment(\.dismiss) private var dismiss
    @Environment(\.openURL) private var openURL
    @State private var showPaywall = false
    @State private var showWidgetHelp = false
    @State private var showBackupNote = false
    @State private var showNearby = false
    @State private var confirmReset = false
    @Environment(\.modelContext) private var modelContext
    @Environment(WalkRecorder.self) private var recorder
    @Environment(DataReset.self) private var reset
    @State private var pane: SettingsPane = .subscription
    @Environment(\.duoOpen) private var duoOpen

    /// What the right pane shows on the open display (6o).
    enum SettingsPane { case subscription, household, tracking, units, history }

    private var dog: Dog? {
        dogs.first { $0.id.uuidString == activeDogID } ?? dogs.first
    }

    var body: some View {
        Group {
            if duoOpen {
                NavigationStack { duoBody }
            } else {
                phoneBody
            }
        }
        .premiumPaywall(isPresented: $showPaywall)
        .adaptiveSheet(isPresented: $showNearby, wide: duoOpen) { PackView() }
        .alert("Add an Opaw widget", isPresented: $showWidgetHelp) {
            Button("OK", role: .cancel) {}
        } message: {
            Text("Touch and hold your Home Screen or Lock Screen, tap Edit, then Add Widget, and pick Opaw.")
        }
        .alert("iCloud backup", isPresented: $showBackupNote) {
            Button("OK", role: .cancel) {}
        } message: {
            Text("Backup to iCloud is coming soon. For now, everything stays on this iPhone.")
        }
        .confirmationDialog("Reset all data?", isPresented: $confirmReset, titleVisibility: .visible) {
            Button("Erase everything", role: .destructive) {
                Haptics.success()
                dismiss()
                reset.start(in: modelContext, recorder: recorder)
            }
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("This deletes your pets, logs, walks, memories and reminders on this iPhone, then starts setup again. It can't be undone. Your subscription isn't affected.")
        }
    }

    /// The red "Reset data" row, last in Settings.
    private var resetRow: some View {
        Button { confirmReset = true } label: {
            HStack(spacing: 14) {
                Icon(.refresh, size: duoOpen ? 16 : 17)
                    .foregroundStyle(Color.opawAlert)
                    .frame(width: duoOpen ? 34 : 36, height: duoOpen ? 34 : 36)
                    .background(Color.opawAlert.opacity(0.1), in: Circle())
                Text("Reset data")
                    .font(.opaw(duoOpen ? 15 : 16, .bold))
                    .foregroundStyle(Color.opawAlert)
                Spacer(minLength: 0)
            }
            .padding(.vertical, duoOpen ? 11 : 13)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityHint("Erases everything on this iPhone and starts setup again")
    }

    // MARK: Open display (6o)

    private var duoBody: some View {
        DuoPanes(rail: false, leading: 30) {
            ScrollView {
                VStack(alignment: .leading, spacing: 0) {
                    HStack(spacing: 16) {
                        Button { dismiss() } label: { RoundIcon(symbol: "chevron.left") }
                            .buttonStyle(.pressable)
                            .accessibilityLabel("Back")
                        Text("Settings")
                            .font(.opaw(26, .black))
                            .foregroundStyle(Color.opawInk)
                    }
                    .frame(minHeight: 54)
                    .padding(.top, 10)
                    if let dog {
                        NavigationLink {
                            EditDogView(dog: dog)
                        } label: {
                            HStack(spacing: 14) {
                                MiniFace(data: dog.cutoutData ?? dog.photoData, size: 56, rim: 4, fill: Color.opawTrack)
                                    .shadow(color: .black.opacity(0.06), radius: 10, y: 8)
                                VStack(alignment: .leading, spacing: 0) {
                                    Text(dog.name)
                                        .font(.opaw(20, .black))
                                        .foregroundStyle(Color.opawInk)
                                    Text(subtitle(dog, includePlan: false))
                                        .font(.opaw(14, .medium))
                                        .foregroundStyle(Color.opawMuted)
                                        .lineLimit(1)
                                }
                                Spacer(minLength: 0)
                            }
                        }
                        .buttonStyle(.pressable)
                        .padding(.top, 14)
                    }
                    paneCard {
                        paneRow(.sparkle, "Subscription", value: subscriptionValue, target: .subscription, divider: false)
                        paneRow(.users, "Household", target: .household, divider: false)
                        if dog != nil {
                            paneRow(.paw, "Care tracking", target: .tracking, divider: pane != .household)
                        }
                        paneAction(.bell, "Notifications", divider: pane != (dog != nil ? .tracking : .household)) {
                            if let url = URL(string: UIApplication.openNotificationSettingsURLString) { openURL(url) }
                        }
                        paneRow(.ruler, "Units", target: .units, divider: true)
                        paneAction(.help, "Help", divider: pane != .units) {
                            openURL(URL(string: "https://github.com/RusselHarveyMercadoIV/opaw/issues")!)
                        }
                    }
                    .padding(.top, 14)
                    paneCard {
                        paneAction(.heart, "Friends nearby", divider: false) { showNearby = true }
                        paneAction(.grid, "Home Screen widgets", divider: true, locked: !premium.isPremium) {
                            if premium.isPremium { showWidgetHelp = true } else { showPaywall = true }
                        }
                        paneAction(.cloud, "iCloud backup", divider: true, locked: !premium.isPremium) {
                            if premium.isPremium { showBackupNote = true } else { showPaywall = true }
                        }
                        if premium.isPremium {
                            paneRow(.chart, "Full history", target: .history, divider: true)
                        } else {
                            paneAction(.chart, "Full history", divider: true, locked: true) { showPaywall = true }
                        }
                    }
                    .padding(.top, 12)
                    paneCard { resetRow }
                        .padding(.top, 12)
                }
                .padding(.leading, 20)
                .padding(.trailing, 36)
                .padding(.bottom, 24)
            }
            .scrollIndicators(.hidden)
            .padding(.horizontal, -20)
        } right: {
            switch pane {
            case .subscription: SubscriptionView(inPane: true)
            case .household: HouseholdView(petName: dog?.name ?? "your pet", inPane: true)
            case .tracking:
                if let dog { CareTrackingView(dog: dog, inPane: true).id(dog.id) }
            case .units: UnitsView(inPane: true)
            case .history:
                if let dog { HistoryView(dog: dog, inPane: true) }
            }
        }
        .opawBackground()
        .toolbar(.hidden, for: .navigationBar)
    }

    private var subscriptionValue: String {
        guard let status = premium.status else { return "Free" }
        if status.isTrial, let end = status.expirationDate {
            let calendar = Calendar.current
            let days = max(0, calendar.dateComponents([.day], from: calendar.startOfDay(for: Date()), to: calendar.startOfDay(for: end)).day ?? 0)
            return "Trial · \(days) \(days == 1 ? "day" : "days")"
        }
        return status.plan.title
    }

    private func paneCard<Content: View>(@ViewBuilder _ content: () -> Content) -> some View {
        VStack(spacing: 0) { content() }
            .padding(.horizontal, 20)
            .padding(.vertical, 6)
            .whiteCard()
    }

    /// A row that shows its page in the right pane; the open one is highlighted (6o).
    private func paneRow(_ icon: DesignIcon, _ title: String, value: String? = nil, target: SettingsPane, divider: Bool) -> some View {
        let selected = pane == target
        return Button {
            Haptics.select()
            withAnimation(.snappy) { pane = target }
        } label: {
            paneRowLabel(icon, title, selected: selected, divider: divider && !selected) {
                if let value {
                    Text(value)
                        .font(.opaw(13, .bold))
                        .foregroundStyle(Color(hex: 0x666666))
                } else {
                    Icon(.chevronRight, size: 16, weight: 2.6).foregroundStyle(Color.opawGhost)
                }
            }
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(selected ? .isSelected : [])
    }

    private func paneAction(_ icon: DesignIcon, _ title: String, divider: Bool, locked: Bool = false, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            paneRowLabel(icon, title, selected: false, divider: divider) {
                if locked {
                    PremiumTag()
                } else {
                    Icon(.chevronRight, size: 16, weight: 2.6).foregroundStyle(Color.opawGhost)
                }
            }
        }
        .buttonStyle(.plain)
    }

    private func paneRowLabel<Trailing: View>(_ icon: DesignIcon, _ title: String, selected: Bool, divider: Bool, @ViewBuilder trailing: () -> Trailing) -> some View {
        HStack(spacing: 14) {
            Icon(icon, size: 16, cutoutColor: selected ? Color(hex: 0x111111) : Color.opawChip)
                .foregroundStyle(selected ? Color.white : Color.opawInk)
                .frame(width: 34, height: 34)
                .background(selected ? Color(hex: 0x111111) : Color.opawChip, in: Circle())
            Text(title)
                .font(.opaw(15, .bold))
                .foregroundStyle(Color.opawInk)
            Spacer(minLength: 8)
            trailing()
        }
        .padding(.vertical, 11)
        .padding(.horizontal, 12)
        .background(selected ? Color.opawChip : Color.clear, in: RoundedRectangle(cornerRadius: 18, style: .continuous))
        .contentShape(Rectangle())
        .overlay(alignment: .top) {
            if divider { Rectangle().fill(Color.opawHairline).frame(height: 1).padding(.horizontal, 12) }
        }
        .padding(.horizontal, -12)
    }

    // MARK: Phone (5e)

    private var phoneBody: some View {
        NavigationStack {
            VStack(spacing: 0) {
                ScreenHeader(title: "Settings") {
                    Button { dismiss() } label: { RoundIcon(symbol: "chevron.left") }
                        .buttonStyle(.pressable)
                        .accessibilityLabel("Back")
                } trailing: {
                    Color.clear.frame(width: 54, height: 54)
                }
                ScrollView {
                    VStack(spacing: 12) {
                        if let dog { petHeader(dog) }
                        if !premium.isPremium { banner }
                        card {
                            if let dog {
                                linkRow(.paw, "Care tracking", first: true) { CareTrackingView(dog: dog) }
                            }
                            linkRow(.users, "Household", first: dog == nil) { HouseholdView(petName: dog?.name ?? "your pet") }
                            actionRow(.bell, "Notifications") {
                                if let url = URL(string: UIApplication.openNotificationSettingsURLString) { openURL(url) }
                            }
                            linkRow(.ruler, "Units") { UnitsView() }
                            actionRow(.heart, "Friends nearby") { showNearby = true }
                        }
                        card {
                            premiumRow(.grid, "Home Screen widgets", first: true) { showWidgetHelp = true }
                            premiumRow(.cloud, "iCloud backup") { showBackupNote = true }
                            if premium.isPremium, let dog {
                                linkRow(.chart, "Full history") { HistoryView(dog: dog) }
                            } else {
                                premiumRow(.chart, "Full history") {}
                            }
                        }
                        card {
                            linkRow(.sparkle, "Subscription", value: planName, first: true) { SubscriptionView() }
                            actionRow(.help, "Help") {
                                openURL(URL(string: "https://github.com/RusselHarveyMercadoIV/opaw/issues")!)
                            }
                        }
                        card { resetRow }
                    }
                    .padding(.horizontal, 20)
                    .padding(.top, 18)
                    .padding(.bottom, 40)
                }
                .scrollIndicators(.hidden)
            }
            .opawBackground()
            .toolbar(.hidden, for: .navigationBar)
        }
    }

    private var planName: String {
        guard let status = premium.status else { return "Free" }
        return status.isTrial ? "Trial" : status.plan.title
    }

    // MARK: Pieces

    private func petHeader(_ dog: Dog) -> some View {
        NavigationLink {
            EditDogView(dog: dog)
        } label: {
            HStack(spacing: 14) {
                MiniFace(data: dog.cutoutData ?? dog.photoData, size: 64, rim: 4, fill: Color.opawTrack)
                    .shadow(color: .black.opacity(0.06), radius: 10, y: 8)
                VStack(alignment: .leading, spacing: 0) {
                    Text(dog.name)
                        .font(.opaw(22, .black))
                        .foregroundStyle(Color.opawInk)
                    Text(subtitle(dog, includePlan: true))
                        .font(.opaw(14, .medium))
                        .foregroundStyle(Color.opawMuted)
                        .lineLimit(1)
                }
                Spacer(minLength: 0)
            }
        }
        .buttonStyle(.pressable)
        .padding(.bottom, 6)
    }

    private func subtitle(_ dog: Dog, includePlan: Bool) -> String {
        var parts: [String] = []
        if !dog.breed.isEmpty { parts.append(dog.breed) }
        if dog.birthDate != nil {
            let years = Int(dog.ageYears)
            parts.append(years < 1 ? "\(max(1, Int(dog.ageYears * 12))) mo" : (years == 1 ? "1 yr" : "\(years) yrs"))
        }
        if includePlan {
            if let status = premium.status {
                parts.append(status.isTrial ? "Premium trial" : "Premium")
            } else {
                parts.append("Free plan")
            }
        }
        return parts.joined(separator: " · ")
    }

    private var banner: some View {
        Button { showPaywall = true } label: {
            HStack(spacing: 10) {
                Icon(.sparkle, size: 18, weight: 2.4)
                Text("Try Opaw Premium")
                    .font(.opaw(15, .heavy))
                Spacer(minLength: 0)
                Text("7 days free")
                    .font(.opaw(13, .bold))
                    .foregroundStyle(Color(hex: 0xAAAAAA))
                Icon(.chevronRight, size: 16, weight: 2.6)
            }
            .foregroundStyle(.white)
            .padding(.leading, 18)
            .padding(.trailing, 16)
            .frame(height: 50)
            .background(Color(hex: 0x111111), in: Capsule())
        }
        .buttonStyle(.pressable)
        .padding(.bottom, 0)
    }

    private func card<Content: View>(@ViewBuilder _ content: () -> Content) -> some View {
        VStack(spacing: 0) { content() }
            .padding(.horizontal, 20)
            .padding(.vertical, 4)
            .whiteCard()
    }

    private func rowLabel<Trailing: View>(_ icon: DesignIcon, _ title: String, first: Bool, @ViewBuilder trailing: () -> Trailing) -> some View {
        HStack(spacing: 14) {
            RowIcon(icon: icon)
            Text(title)
                .font(.opaw(16, .bold))
                .foregroundStyle(Color.opawInk)
            Spacer(minLength: 8)
            trailing()
        }
        .padding(.vertical, 13)
        .contentShape(Rectangle())
        .overlay(alignment: .top) {
            if !first { Rectangle().fill(Color.opawHairline).frame(height: 1) }
        }
    }

    private var chevron: some View {
        Icon(.chevronRight, size: 16, weight: 2.6).foregroundStyle(Color.opawGhost)
    }

    private func linkRow<Destination: View>(_ icon: DesignIcon, _ title: String, value: String? = nil, first: Bool = false, @ViewBuilder destination: @escaping () -> Destination) -> some View {
        NavigationLink {
            destination()
        } label: {
            rowLabel(icon, title, first: first) {
                if let value {
                    Text(value)
                        .font(.opaw(14, .semibold))
                        .foregroundStyle(Color.opawMuted)
                }
                chevron
            }
        }
        .buttonStyle(.plain)
    }

    private func actionRow(_ icon: DesignIcon, _ title: String, first: Bool = false, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            rowLabel(icon, title, first: first) { chevron }
        }
        .buttonStyle(.plain)
    }

    /// A row free users see locked; tapping it opens the paywall until Premium is on.
    private func premiumRow(_ icon: DesignIcon, _ title: String, first: Bool = false, action: @escaping () -> Void) -> some View {
        Button {
            if premium.isPremium { action() } else { showPaywall = true }
        } label: {
            rowLabel(icon, title, first: first) {
                if premium.isPremium { chevron } else { PremiumTag() }
            }
        }
        .buttonStyle(.plain)
    }
}

/// Profile > Units: kilometres and kilograms, or miles and pounds.
struct UnitsView: View {
    var inPane = false
    @AppStorage(OpawFormat.imperialKey, store: OpawDefaults.shared) private var imperial = false
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        VStack(spacing: 0) {
            if inPane {
                PaneHeader(title: "Units")
            } else {
                ScreenHeader(title: "Units") {
                    Button { dismiss() } label: { RoundIcon(symbol: "chevron.left") }
                        .buttonStyle(.pressable)
                        .accessibilityLabel("Back")
                } trailing: {
                    Color.clear.frame(width: 54, height: 54)
                }
            }
            VStack(spacing: 0) {
                option("Metric", detail: "Kilometres and kilograms", selected: !imperial, first: true) { imperial = false }
                option("Imperial", detail: "Miles and pounds", selected: imperial) { imperial = true }
            }
            .padding(.horizontal, 20)
            .padding(.vertical, 4)
            .whiteCard()
            .padding(.horizontal, inPane ? 0 : 20)
            .padding(.top, inPane ? 12 : 18)
            Spacer()
        }
        .opawBackground()
        .toolbar(.hidden, for: .navigationBar)
        .onChange(of: imperial) { _, _ in
            WidgetCenter.shared.reloadAllTimelines()
        }
    }

    private func option(_ title: String, detail: String, selected: Bool, first: Bool = false, action: @escaping () -> Void) -> some View {
        Button {
            Haptics.select()
            action()
        } label: {
            HStack {
                VStack(alignment: .leading, spacing: 0) {
                    Text(title)
                        .font(.opaw(16, .heavy))
                        .foregroundStyle(Color.opawInk)
                    Text(detail)
                        .font(.opaw(13, .medium))
                        .foregroundStyle(Color.opawMuted)
                }
                Spacer()
                ZStack {
                    if selected {
                        Circle().fill(Color(hex: 0x111111))
                        Icon(.check, size: 12).foregroundStyle(.white)
                    } else {
                        Circle().strokeBorder(Color(hex: 0xD4D4D8), lineWidth: 2)
                    }
                }
                .frame(width: 22, height: 22)
            }
            .padding(.vertical, 14)
            .contentShape(Rectangle())
            .overlay(alignment: .top) {
                if !first { Rectangle().fill(Color.opawHairline).frame(height: 1) }
            }
        }
        .buttonStyle(.plain)
    }
}

struct BadgeGrid: View {
    let dog: Dog

    var body: some View {
        let stats = BadgeService.stats(for: dog)
        let earned = dog.earnedBadges
        LazyVGrid(columns: [GridItem(.adaptive(minimum: 96), spacing: 12)], spacing: 16) {
            ForEach(Badge.allCases) { badge in
                let isEarned = earned.contains(badge)
                VStack(spacing: 6) {
                    ZStack {
                        Circle()
                            .fill(isEarned ? Color.opawInk : Color.opawChip)
                        if !isEarned {
                            ProgressRing(progress: badge.progress(stats), lineWidth: 4)
                        }
                        Image(systemName: badge.symbol)
                            .font(.title2)
                            .foregroundStyle(isEarned ? Color.opawOnInk : Color.opawMuted)
                    }
                    .frame(width: 60, height: 60)
                    Text(badge.title)
                        .font(.caption.weight(.semibold))
                        .multilineTextAlignment(.center)
                        .lineLimit(2)
                    Text(badge.detail)
                        .font(.caption2)
                        .foregroundStyle(.secondary)
                        .multilineTextAlignment(.center)
                        .lineLimit(2)
                }
                .accessibilityElement(children: .combine)
                .accessibilityValue(isEarned ? "Earned" : "\(Int(badge.progress(stats) * 100)) percent")
            }
        }
        .padding(.vertical, 8)
    }
}

struct EditDogView: View {
    @Bindable var dog: Dog
    @Environment(\.modelContext) private var modelContext
    @Environment(\.dismiss) private var dismiss
    @State private var confirmDelete = false

    var body: some View {
        Form {
            Section("Basics") {
                TextField("Name", text: $dog.name)
                TextField("Breed", text: $dog.breed)
                DatePicker(
                    "Birthday",
                    selection: Binding(get: { dog.birthDate ?? Date() }, set: { dog.birthDate = $0 }),
                    in: ...Date(),
                    displayedComponents: .date
                )
                Picker("Size", selection: Binding(get: { dog.size }, set: { dog.size = $0 })) {
                    ForEach(DogSize.allCases) { Text($0.title).tag($0) }
                }
                Picker("Energy", selection: Binding(get: { dog.energy }, set: { dog.energy = $0 })) {
                    ForEach(EnergyLevel.allCases) { Text($0.title).tag($0) }
                }
            }
            Section {
                Stepper("Daily activity: \(dog.dailyGoalMinutes) min", value: $dog.dailyGoalMinutes, in: 10...240, step: 5)
                Button("Use recommended (\(dog.traits.recommendedDailyActivityMinutes) min)") {
                    dog.dailyGoalMinutes = dog.traits.recommendedDailyActivityMinutes
                }
            } header: {
                Text("Goals")
            } footer: {
                Text("A starting point based on size, age and energy. Your vet knows best.")
            }
            Section {
                NavigationLink("Care tracking and meal times") { CareTrackingView(dog: dog) }
            }
            Section {
                Button("Delete \(dog.name)", role: .destructive) { confirmDelete = true }
            }
        }
        .navigationTitle("Edit \(dog.name)")
        .onDisappear { try? modelContext.save() }
        .confirmationDialog("Delete \(dog.name) and all their logs, walks and memories?", isPresented: $confirmDelete, titleVisibility: .visible) {
            Button("Delete", role: .destructive) {
                AvatarClipStore.deleteAll(dogID: dog.id)
                modelContext.delete(dog)
                try? modelContext.save()
                OpawStore.activeDogID = nil
                dismiss()
            }
        }
    }
}

struct AddDogSheet: View {
    @Environment(\.dismiss) private var dismiss
    @State private var draft = DogDraft()
    @State private var showPhoto = false

    var body: some View {
        NavigationStack {
            DogDetailsForm(draft: $draft, actionTitle: "Next") {
                showPhoto = true
            }
            .navigationTitle("New dog")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
            }
            .navigationDestination(isPresented: $showPhoto) {
                AvatarCaptureStep(draft: draft, onSaved: { dismiss() })
            }
        }
    }
}
