import OpawKit
import PhotosUI
import SwiftData
import SwiftUI

/// First run: welcome, the dog's details, then a photo that becomes the avatar.
struct OnboardingView: View {
    enum Step {
        case welcome, details, photo
    }

    @State private var step: Step = .welcome
    @State private var draft = DogDraft()

    var body: some View {
        NavigationStack {
            Group {
                switch step {
                case .welcome:
                    WelcomeStep { withAnimation { step = .details } }
                case .details:
                    DogDetailsForm(draft: $draft, actionTitle: "Next") {
                        withAnimation { step = .photo }
                    }
                    .navigationTitle("About your dog")
                case .photo:
                    AvatarCaptureStep(draft: draft)
                }
            }
        }
    }
}

/// Editable fields for a dog before it's saved.
struct DogDraft {
    var name = ""
    var breed = ""
    var hasBirthDate = false
    var birthDate = Calendar.current.date(byAdding: .year, value: -2, to: Date()) ?? Date()
    var weightKg: Double?
    var size: DogSize = .medium
    var energy: EnergyLevel = .medium

    var isValid: Bool { !name.trimmingCharacters(in: .whitespaces).isEmpty }

    func makeDog() -> Dog {
        Dog(
            name: name.trimmingCharacters(in: .whitespaces),
            breed: breed.trimmingCharacters(in: .whitespaces),
            birthDate: hasBirthDate ? birthDate : nil,
            weightKg: weightKg,
            size: size,
            energy: energy
        )
    }
}

private struct WelcomeStep: View {
    let onContinue: () -> Void

    var body: some View {
        VStack(spacing: 24) {
            Spacer()
            Image(systemName: "pawprint.circle.fill")
                .font(.system(size: 96))
                .foregroundStyle(Color.opawOrange)
                .symbolEffect(.bounce, options: .repeating.speed(0.3))
            VStack(spacing: 8) {
                Text("Welcome to Opaw")
                    .font(.largeTitle.bold())
                Text("Turn your dog into a living avatar, keep track of their care, record walks and meet the pack nearby.")
                    .multilineTextAlignment(.center)
                    .foregroundStyle(.secondary)
            }
            .padding(.horizontal)
            Spacer()
            Button(action: onContinue) {
                Text("Get started")
                    .font(.headline)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 6)
            }
            .buttonStyle(.borderedProminent)
            .padding(.horizontal, 24)
            .padding(.bottom, 24)
        }
        .frame(maxWidth: 520)
        .frame(maxWidth: .infinity)
        .opawBackground()
    }
}

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

/// Picks a photo, cuts the dog out on device, and saves the dog.
struct AvatarCaptureStep: View {
    let draft: DogDraft
    var onSaved: () -> Void = {}

    @Environment(\.modelContext) private var modelContext
    @State private var pickerItem: PhotosPickerItem?
    @State private var showCamera = false
    @State private var original: UIImage?
    @State private var cutout: UIImage?
    @State private var isProcessing = false
    @State private var message: String?

    var body: some View {
        ScrollView {
            VStack(spacing: 20) {
                Text("Snap \(draft.name)")
                    .font(.largeTitle.bold())
                Text("A clear, well-lit photo of your whole dog works best. We'll lift them off the background to make their avatar.")
                    .multilineTextAlignment(.center)
                    .foregroundStyle(.secondary)

                ZStack {
                    RoundedRectangle(cornerRadius: 28, style: .continuous)
                        .fill(Color.opawOrange.opacity(0.12))
                    if let cutout {
                        LivingPhotoView(image: cutout, mood: .happy)
                            .padding(24)
                    } else if let original {
                        Image(uiImage: original)
                            .resizable()
                            .scaledToFit()
                            .clipShape(RoundedRectangle(cornerRadius: 20))
                            .padding(24)
                    } else {
                        Image(systemName: "dog.fill")
                            .font(.system(size: 80))
                            .foregroundStyle(Color.opawOrange.opacity(0.6))
                    }
                    if isProcessing {
                        ProgressView("Finding \(draft.name)…")
                            .padding()
                            .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 12))
                    }
                }
                .frame(height: 340)

                if let message {
                    Text(message)
                        .font(.callout)
                        .foregroundStyle(.secondary)
                        .multilineTextAlignment(.center)
                }

                HStack(spacing: 12) {
                    if UIImagePickerController.isSourceTypeAvailable(.camera) {
                        Button {
                            showCamera = true
                        } label: {
                            Label("Camera", systemImage: "camera.fill")
                                .frame(maxWidth: .infinity)
                        }
                        .buttonStyle(.bordered)
                    }
                    PhotosPicker(selection: $pickerItem, matching: .images) {
                        Label("Library", systemImage: "photo.on.rectangle")
                            .frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.bordered)
                }

                Button {
                    save()
                } label: {
                    Text(original == nil ? "Skip for now" : "Meet \(draft.name)")
                        .font(.headline)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 6)
                }
                .buttonStyle(.borderedProminent)
                .disabled(isProcessing)
            }
            .padding(24)
            .frame(maxWidth: 560)
            .frame(maxWidth: .infinity)
        }
        .opawBackground()
        .onChange(of: pickerItem) { _, item in
            guard let item else { return }
            Task {
                if let data = try? await item.loadTransferable(type: Data.self), let image = UIImage(data: data) {
                    await process(image)
                }
            }
        }
        .sheet(isPresented: $showCamera) {
            CameraPicker { image in
                Task { await process(image) }
            }
            .ignoresSafeArea()
        }
    }

    private func process(_ image: UIImage) async {
        let prepared = image.preparedForProcessing()
        original = prepared
        cutout = nil
        message = nil
        isProcessing = true
        defer { isProcessing = false }
        let hasDog = await CutoutService.containsDog(prepared)
        do {
            cutout = try await CutoutService.cutout(prepared)
            message = hasDog ? nil : "Hmm, we're not sure that's a dog, but we'll go with it!"
        } catch {
            message = "\(error.localizedDescription) We'll use the full photo for now."
        }
    }

    private func save() {
        let dog = draft.makeDog()
        dog.photoData = original?.jpegData(compressionQuality: 0.85)
        dog.cutoutData = cutout?.pngData()
        modelContext.insert(dog)
        // Sensible starter reminders for meals.
        for hour in dog.mealHours {
            let reminder = Reminder(title: "\(dog.name)'s meal", kind: .meal, hour: hour, minute: 0, dog: dog)
            modelContext.insert(reminder)
        }
        try? modelContext.save()
        OpawStore.activeDogID = dog.id
        Task {
            if await NotificationService.requestAuthorization() {
                let reminders = (try? modelContext.fetch(FetchDescriptor<Reminder>())) ?? []
                await NotificationService.reschedule(reminders)
            }
        }
        Haptics.success()
        onSaved()
    }
}
