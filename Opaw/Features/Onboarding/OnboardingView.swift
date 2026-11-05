import OpawKit
import SwiftData
import SwiftUI

/// Everything the photo step learned.
struct CaptureResult: Equatable {
    var original: UIImage?
    var cutout: UIImage?
    var guess: BreedGuess?
}

// MARK: - Draft

/// Editable fields for a dog before it's saved.
struct DogDraft {
    var name = ""
    var breed = ""
    var hasBirthDate = false
    var birthDate = Calendar.current.date(byAdding: .year, value: -2, to: Date()) ?? Date()
    var weightKg: Double?
    var size: DogSize = .medium
    var energy: EnergyLevel = .medium
    var species = "dog"
    var sex = ""
    var isNeutered = false
    /// Meal times as minutes after midnight, such as 450 for 07:30.
    var mealTimes: [Int] = [450, 1080]
    /// Care kinds to show in Care; empty means all.
    var trackedKinds: [String] = []

    var trimmedName: String { name.trimmingCharacters(in: .whitespaces) }
    var isValid: Bool { !trimmedName.isEmpty }
    /// The name, or "your dog" before one is typed.
    var displayName: String { isValid ? trimmedName : "your dog" }

    func makeDog() -> Dog {
        let dog = Dog(
            name: trimmedName,
            breed: breed.trimmingCharacters(in: .whitespaces),
            birthDate: hasBirthDate ? birthDate : nil,
            weightKg: weightKg,
            size: size,
            energy: energy
        )
        dog.species = species
        dog.sex = sex
        dog.isNeutered = isNeutered
        dog.trackedKindsRaw = trackedKinds
        if !mealTimes.isEmpty { dog.mealTimes = mealTimes }
        return dog
    }

    /// Saves the dog with its photos and starter meal reminders, and makes it the active dog.
    @MainActor
    @discardableResult
    func save(photo: UIImage?, cutout: UIImage?, in context: ModelContext) -> Dog {
        let dog = makeDog()
        dog.photoData = photo?.jpegData(compressionQuality: 0.85)
        dog.cutoutData = cutout?.pngData()
        context.insert(dog)
        let tracksMeals = trackedKinds.isEmpty || trackedKinds.contains(CareKind.meal.rawValue)
        if tracksMeals {
            for time in mealTimes.sorted() {
                context.insert(Reminder(title: "\(dog.name)'s meal", kind: .meal, hour: time / 60, minute: time % 60, dog: dog))
            }
        }
        try? context.save()
        OpawStore.activeDogID = dog.id
        Task {
            if await NotificationService.requestAuthorization() {
                let reminders = (try? context.fetch(FetchDescriptor<Reminder>())) ?? []
                await NotificationService.reschedule(reminders)
            }
        }
        Haptics.success()
        return dog
    }
}

/// Four dogs on a baseline, growing with size.
struct SizePicker: View {
    @Binding var selection: DogSize

    var body: some View {
        HStack(alignment: .bottom, spacing: 0) {
            ForEach(DogSize.allCases) { size in
                let isSelected = size == selection
                Button {
                    selection = size
                    Haptics.select()
                } label: {
                    VStack(spacing: Space.s) {
                        Image(systemName: "dog.fill")
                            .font(.system(size: size.glyphSize))
                            .foregroundStyle(isSelected ? Color.opawOrange : Color.opawInk.opacity(0.12))
                            .scaleEffect(isSelected ? 1.08 : 1, anchor: .bottom)
                        Text(size.title)
                            .font(.footnote.weight(isSelected ? .semibold : .regular))
                            .foregroundStyle(isSelected ? Color.opawInk : .secondary)
                    }
                    .frame(maxWidth: .infinity, minHeight: 120, alignment: .bottom)
                    .contentShape(Rectangle())
                }
                .buttonStyle(.pressable)
                .accessibilityAddTraits(isSelected ? .isSelected : [])
            }
        }
        .animation(.spring(response: 0.4, dampingFraction: 0.6), value: selection)
    }
}

extension DogSize {
    var glyphSize: CGFloat {
        switch self {
        case .small: return 30
        case .medium: return 44
        case .large: return 60
        case .giant: return 78
        }
    }

    var hint: String {
        switch self {
        case .small: return "Up to about 10 kg. Lap certified."
        case .medium: return "About 10 to 25 kg. Just right."
        case .large: return "About 25 to 45 kg. Big hugs."
        case .giant: return "Over 45 kg. Mostly heart."
        }
    }
}

extension EnergyLevel {
    var symbol: String {
        switch self {
        case .low: return "moon.zzz.fill"
        case .medium: return "figure.walk"
        case .high: return "hare.fill"
        }
    }

    var phrase: String {
        switch self {
        case .low: return "Couch connoisseur"
        case .medium: return "Happy wanderer"
        case .high: return "Zoomies on demand"
        }
    }

    var detail: String {
        switch self {
        case .low: return "Short strolls, long naps"
        case .medium: return "A good walk or two a day"
        case .high: return "Runs, fetch, and then more fetch"
        }
    }

    var bobDuration: Double {
        switch self {
        case .low: return 1.4
        case .medium: return 0.7
        case .high: return 0.3
        }
    }
}

// MARK: - Details form (used when adding another dog from Profile)

struct DogDetailsForm: View {
    @Binding var draft: DogDraft
    let actionTitle: String
    let onDone: () -> Void

    var body: some View {
        Form {
            Section("Basics") {
                TextField("Name", text: $draft.name)
                    .textContentType(.name)
                TextField("Breed (optional)", text: $draft.breed)
            }
            Section("Age and size") {
                Toggle("I know their birthday", isOn: $draft.hasBirthDate)
                if draft.hasBirthDate {
                    DatePicker("Birthday", selection: $draft.birthDate, in: ...Date(), displayedComponents: .date)
                }
                Picker("Size", selection: $draft.size) {
                    ForEach(DogSize.allCases) { Text($0.title).tag($0) }
                }
                Picker("Energy", selection: $draft.energy) {
                    ForEach(EnergyLevel.allCases) { Text($0.title).tag($0) }
                }
                HStack {
                    Text("Weight (kg)")
                    Spacer()
                    TextField("Optional", value: $draft.weightKg, format: .number)
                        .keyboardType(.decimalPad)
                        .multilineTextAlignment(.trailing)
                }
            }
            Section {
                Button(actionTitle, action: onDone)
                    .disabled(!draft.isValid)
                    .frame(maxWidth: .infinity)
            }
        }
    }
}
