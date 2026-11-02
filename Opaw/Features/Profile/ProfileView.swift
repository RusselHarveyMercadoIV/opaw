import OpawKit
import SwiftData
import SwiftUI
import WidgetKit

/// The dog's profile, badges, reminders and app settings.
struct ProfileView: View {
    @Query(sort: \Dog.createdAt) private var dogs: [Dog]
    @AppStorage(OpawDefaults.activeDogKey, store: OpawDefaults.shared) private var activeDogID = ""
    @State private var showAddDog = false

    private var dog: Dog? {
        dogs.first { $0.id.uuidString == activeDogID } ?? dogs.first
    }

    var body: some View {
        NavigationStack {
            List {
                if let dog {
                    Section {
                        HStack(spacing: 16) {
                            DogThumbnail(data: dog.cutoutData ?? dog.photoData, size: 72)
                            VStack(alignment: .leading, spacing: 4) {
                                Text(dog.name).font(.title2.bold())
                                Text([dog.breed, ageText(dog), dog.size.title].filter { !$0.isEmpty }.joined(separator: " · "))
                                    .font(.subheadline)
                                    .foregroundStyle(.secondary)
                            }
                        }
                        NavigationLink("Edit profile") { EditDogView(dog: dog) }
                        NavigationLink("Avatar") { AvatarStudioView(dog: dog) }
                        NavigationLink("Reminders") { RemindersView(dog: dog) }
                        NavigationLink("Care history") { CareHistoryView(dog: dog) }
                    }

                    Section("Badges") {
                        BadgeGrid(dog: dog)
                    }
                }

                Section("Your dogs") {
                    ForEach(dogs) { item in
                        Button {
                            activeDogID = item.id.uuidString
                            WidgetCenter.shared.reloadTimelines(ofKind: OpawWidgetKind.quickLog)
                        } label: {
                            HStack {
                                DogThumbnail(data: item.cutoutData ?? item.photoData, size: 36)
                                Text(item.name)
                                Spacer()
                                if item.id == dog?.id {
                                    Image(systemName: "checkmark").foregroundStyle(Color.opawOrange)
                                }
                            }
                        }
                        .foregroundStyle(.primary)
                    }
                    Button {
                        showAddDog = true
                    } label: {
                        Label("Add another dog", systemImage: "plus")
                    }
                }

                Section("About") {
                    LabeledContent("Version", value: Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "")
                    Text("Your care logs, walks and memories stay on your iPhone. Only what you choose to share with the Pack is uploaded.")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                    Link("Privacy policy", destination: URL(string: "https://github.com/RusselHarveyMercadoIV/opaw/blob/main/docs/PRIVACY.md")!)
                }
            }
            .navigationTitle("Profile")
            .sheet(isPresented: $showAddDog) {
                AddDogSheet()
            }
        }
    }

    private func ageText(_ dog: Dog) -> String {
        guard dog.birthDate != nil else { return "" }
        let years = dog.ageYears
        if years < 1 {
            return "\(max(1, Int(years * 12))) months"
        }
        return "\(Int(years)) years"
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
                            .fill(isEarned ? Color.opawOrange : Color.secondary.opacity(0.12))
                        if !isEarned {
                            ProgressRing(progress: badge.progress(stats), lineWidth: 4)
                        }
                        Image(systemName: badge.symbol)
                            .font(.title2)
                            .foregroundStyle(isEarned ? .white : .secondary)
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
            Section("Meal times") {
                ForEach(dog.mealHours.indices, id: \.self) { index in
                    Stepper("Meal \(index + 1): \(String(format: "%02d:00", dog.mealHours[index]))", value: $dog.mealHours[index], in: 0...23)
                }
                Button("Add meal time") { dog.mealHours.append(12) }
                if dog.mealHours.count > 1 {
                    Button("Remove last meal time", role: .destructive) { dog.mealHours.removeLast() }
                }
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

private struct AddDogSheet: View {
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
