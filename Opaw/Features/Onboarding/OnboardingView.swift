import OpawKit
import SwiftData
import SwiftUI

/// First run, photo first: a portrait of the dog, then details prefilled from the photo
/// (breed, size, energy) that the person can change, then a reveal of the living avatar.
struct OnboardingView: View {
    enum Step: Int, CaseIterable {
        case intro, photo, name, breed, energy, birthday, reveal
    }

    @Environment(\.modelContext) private var modelContext
    @State private var step: Step = .intro
    @State private var forward = true
    @State private var draft = DogDraft()
    @State private var capture = CaptureResult()

    /// Steps that show the paw trail, in order.
    private let trailSteps: [Step] = [.photo, .name, .breed, .energy, .birthday]

    var body: some View {
        VStack(spacing: 0) {
            if let index = trailSteps.firstIndex(of: step) {
                header(index: index)
                    .transition(.opacity)
            }
            ZStack {
                page
                    .id(step)
                    .transition(pageTransition)
            }
            .frame(maxWidth: 560)
            .frame(maxWidth: .infinity, maxHeight: .infinity)
        }
        .opawBackground()
    }

    @ViewBuilder
    private var page: some View {
        switch step {
        case .intro:
            IntroStep { go(.photo) }
        case .photo:
            AvatarCaptureStep(draft: draft, eyebrow: "01  Portrait", initial: capture, onComplete: { result in
                apply(result)
                go(.name)
            })
        case .name:
            NameStep(draft: $draft, capture: capture) { go(.breed) }
        case .breed:
            BreedStep(draft: $draft, guess: capture.guess) { go(.energy) }
        case .energy:
            EnergyStep(draft: $draft, guessed: capture.guess != nil) { go(.birthday) }
        case .birthday:
            BirthdayStep(draft: $draft) { go(.reveal) }
        case .reveal:
            RevealStep(name: draft.trimmedName, capture: capture, onFinish: finish)
        }
    }

    private var pageTransition: AnyTransition {
        .asymmetric(
            insertion: .offset(x: forward ? 56 : -56).combined(with: .opacity),
            removal: .offset(x: forward ? -56 : 56).combined(with: .opacity)
        )
    }

    private func header(index: Int) -> some View {
        HStack {
            Button {
                if let previous = Step(rawValue: step.rawValue - 1) { go(previous) }
            } label: {
                Image(systemName: "chevron.left")
                    .font(.system(size: 16, weight: .semibold))
                    .foregroundStyle(Color.opawInk)
                    .frame(width: 44, height: 44)
                    .background(Color.opawSurface, in: Circle())
            }
            .buttonStyle(.pressable)
            .accessibilityLabel("Back")
            Spacer()
            PawTrail(current: index, total: trailSteps.count)
            Spacer()
            Color.clear.frame(width: 44, height: 44)
        }
        .padding(.horizontal, Space.m)
        .padding(.top, Space.s)
    }

    private func go(_ next: Step) {
        forward = next.rawValue > step.rawValue
        Haptics.soft()
        withAnimation(.spring(response: 0.55, dampingFraction: 0.88)) {
            step = next
        }
    }

    /// Prefills details from the photo, without overwriting anything the person already typed.
    private func apply(_ result: CaptureResult) {
        let isNewGuess = result.guess != capture.guess
        capture = result
        guard isNewGuess, let guess = result.guess else { return }
        draft.breed = guess.breed.name
        draft.size = guess.breed.size
        draft.energy = guess.breed.energy
    }

    private func finish() {
        draft.save(photo: capture.original, cutout: capture.cutout, in: modelContext)
    }
}

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

    var trimmedName: String { name.trimmingCharacters(in: .whitespaces) }
    var isValid: Bool { !trimmedName.isEmpty }
    /// The name, or "your dog" before one is typed.
    var displayName: String { isValid ? trimmedName : "your dog" }

    func makeDog() -> Dog {
        Dog(
            name: trimmedName,
            breed: breed.trimmingCharacters(in: .whitespaces),
            birthDate: hasBirthDate ? birthDate : nil,
            weightKg: weightKg,
            size: size,
            energy: energy
        )
    }

    /// Saves the dog with its photos and starter meal reminders, and makes it the active dog.
    @MainActor
    @discardableResult
    func save(photo: UIImage?, cutout: UIImage?, in context: ModelContext) -> Dog {
        let dog = makeDog()
        dog.photoData = photo?.jpegData(compressionQuality: 0.85)
        dog.cutoutData = cutout?.pngData()
        context.insert(dog)
        for hour in dog.mealHours {
            context.insert(Reminder(title: "\(dog.name)'s meal", kind: .meal, hour: hour, minute: 0, dog: dog))
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

// MARK: - Page scaffold

/// A question page: eyebrow, serif title, content, and a pinned primary button.
struct StepPage<Content: View>: View {
    let eyebrow: String
    let title: String
    var subtitle: String?
    let buttonTitle: String
    var isEnabled = true
    let action: () -> Void
    var secondaryTitle: String?
    var secondaryAction: (() -> Void)?
    @ViewBuilder let content: Content

    @State private var appeared = false

    var body: some View {
        VStack(spacing: 0) {
            ScrollView {
                VStack(alignment: .leading, spacing: Space.xl) {
                    VStack(alignment: .leading, spacing: 12) {
                        Eyebrow(text: eyebrow)
                        Text(title)
                            .font(.opawDisplay(36))
                            .foregroundStyle(Color.opawInk)
                            .fixedSize(horizontal: false, vertical: true)
                        if let subtitle {
                            Text(subtitle)
                                .font(.body)
                                .foregroundStyle(.secondary)
                                .fixedSize(horizontal: false, vertical: true)
                        }
                    }
                    content
                        .opacity(appeared ? 1 : 0)
                        .offset(y: appeared ? 0 : 12)
                }
                .padding(.horizontal, Space.l)
                .padding(.top, Space.l)
                .padding(.bottom, Space.xl)
                .frame(maxWidth: .infinity, alignment: .leading)
            }
            .scrollBounceBehavior(.basedOnSize)
            .scrollDismissesKeyboard(.interactively)

            VStack(spacing: 4) {
                Button(buttonTitle, action: action)
                    .buttonStyle(.opawPrimary)
                    .disabled(!isEnabled)
                if let secondaryTitle, let secondaryAction {
                    Button(secondaryTitle, action: secondaryAction)
                        .font(.subheadline.weight(.medium))
                        .foregroundStyle(.secondary)
                        .frame(minHeight: 44)
                }
            }
            .padding(.horizontal, Space.l)
            .padding(.bottom, Space.s)
            .animation(.easeInOut(duration: 0.2), value: isEnabled)
        }
        .onAppear {
            withAnimation(.easeOut(duration: 0.5).delay(0.15)) { appeared = true }
        }
    }
}

// MARK: - Intro

private struct IntroStep: View {
    let onBegin: () -> Void
    @State private var appeared = false

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            WalkingPaws()
                .frame(maxWidth: .infinity)
                .frame(height: 280)
                .padding(.top, Space.l)
            Spacer(minLength: Space.l)
            VStack(alignment: .leading, spacing: Space.m) {
                Text("opaw")
                    .font(.system(size: 15, weight: .bold, design: .rounded))
                    .tracking(3)
                    .foregroundStyle(Color.opawOrange)
                (Text("Every good dog\ndeserves a little ") + Text("magic.").italic().foregroundColor(.opawOrange))
                    .font(.opawDisplay(44))
                    .foregroundStyle(Color.opawInk)
                    .fixedSize(horizontal: false, vertical: true)
                Text("One photo becomes a living companion. Care, walks and memories, in one calm place.")
                    .font(.body)
                    .foregroundStyle(.secondary)
                    .fixedSize(horizontal: false, vertical: true)
            }
            .opacity(appeared ? 1 : 0)
            .offset(y: appeared ? 0 : 24)
            Spacer(minLength: Space.xl)
            Button(action: onBegin) {
                HStack(spacing: 8) {
                    Text("Let's begin")
                    Image(systemName: "arrow.right")
                }
            }
            .buttonStyle(.opawPrimary)
            .opacity(appeared ? 1 : 0)
            .padding(.bottom, Space.s)
        }
        .padding(.horizontal, Space.l)
        .onAppear {
            withAnimation(.spring(response: 0.9, dampingFraction: 0.85).delay(0.25)) { appeared = true }
        }
    }
}

// MARK: - Name

private struct NameStep: View {
    @Binding var draft: DogDraft
    let capture: CaptureResult
    let onNext: () -> Void
    @FocusState private var focused: Bool

    var body: some View {
        StepPage(
            eyebrow: "02  Name",
            title: "And who is this?",
            buttonTitle: "Continue",
            isEnabled: draft.isValid,
            action: onNext
        ) {
            VStack(alignment: .leading, spacing: Space.l) {
                if let thumbnail = capture.cutout ?? capture.original {
                    Image(uiImage: thumbnail)
                        .resizable()
                        .scaledToFit()
                        .frame(height: 140)
                        .frame(maxWidth: .infinity, alignment: .leading)
                }
                VStack(alignment: .leading, spacing: Space.s) {
                    TextField("Their name", text: $draft.name)
                        .font(.opawDisplay(40, weight: .regular))
                        .foregroundStyle(Color.opawInk)
                        .textInputAutocapitalization(.words)
                        .autocorrectionDisabled()
                        .submitLabel(.continue)
                        .focused($focused)
                        .onSubmit { if draft.isValid { onNext() } }
                    Capsule()
                        .fill(draft.isValid ? Color.opawOrange : Color.opawHairline)
                        .frame(height: 2)
                        .animation(.easeInOut(duration: 0.25), value: draft.isValid)
                }
                if draft.isValid {
                    Label("Nice to meet you, \(draft.trimmedName).", systemImage: "hand.wave.fill")
                        .font(.callout)
                        .foregroundStyle(.secondary)
                        .transition(.opacity.combined(with: .offset(y: 8)))
                }
            }
            .animation(.spring(response: 0.4, dampingFraction: 0.8), value: draft.isValid)
        }
        .task {
            try? await Task.sleep(for: .milliseconds(450))
            focused = true
        }
    }
}

// MARK: - Breed and size

private struct BreedStep: View {
    @Binding var draft: DogDraft
    let guess: BreedGuess?
    let onNext: () -> Void
    @FocusState private var breedFocused: Bool

    private var title: String {
        guess == nil ? "Tell us about \(draft.displayName)." : "We think \(draft.displayName) is a…"
    }

    private var suggestions: [BreedInfo] {
        guard breedFocused else { return [] }
        return BreedCatalog.suggestions(for: draft.breed).filter { $0.name != draft.breed }
    }

    var body: some View {
        StepPage(
            eyebrow: "03  Breed",
            title: title,
            subtitle: guess == nil ? "We couldn't tell the breed from the photo. Add it if you know it." : "Spotted from the photo. Change anything that's off.",
            buttonTitle: "Looks right",
            action: onNext
        ) {
            VStack(alignment: .leading, spacing: Space.xl) {
                VStack(alignment: .leading, spacing: Space.s) {
                    HStack(alignment: .firstTextBaseline) {
                        TextField("Breed or mix", text: $draft.breed)
                            .font(.opawDisplay(30, weight: .regular))
                            .foregroundStyle(Color.opawInk)
                            .textInputAutocapitalization(.words)
                            .focused($breedFocused)
                            .submitLabel(.done)
                        if guess != nil && !breedFocused {
                            Image(systemName: "sparkles")
                                .foregroundStyle(Color.opawOrange)
                                .accessibilityLabel("Detected from photo")
                        }
                    }
                    Capsule()
                        .fill(breedFocused ? Color.opawOrange : Color.opawHairline)
                        .frame(height: 2)
                    ForEach(suggestions) { breed in
                        Button {
                            draft.breed = breed.name
                            draft.size = breed.size
                            draft.energy = breed.energy
                            breedFocused = false
                            Haptics.select()
                        } label: {
                            HStack {
                                Text(breed.name)
                                Spacer()
                                Image(systemName: "arrow.up.left")
                                    .foregroundStyle(.tertiary)
                            }
                            .font(.body)
                            .foregroundStyle(Color.opawInk)
                            .frame(minHeight: 40)
                            .contentShape(Rectangle())
                        }
                        .buttonStyle(.plain)
                    }
                }
                .animation(.easeInOut(duration: 0.2), value: suggestions.map(\.name))

                VStack(alignment: .leading, spacing: Space.m) {
                    Eyebrow(text: "Size")
                    SizePicker(selection: $draft.size)
                    Text(draft.size.hint)
                        .font(.opawDisplay(18, weight: .regular))
                        .italic()
                        .foregroundStyle(.secondary)
                        .contentTransition(.opacity)
                        .animation(.easeInOut, value: draft.size)
                }

                HStack {
                    Text("Weight")
                        .font(.body)
                    Spacer()
                    TextField("Optional", value: $draft.weightKg, format: .number)
                        .keyboardType(.decimalPad)
                        .multilineTextAlignment(.trailing)
                        .frame(maxWidth: 120)
                    Text("kg")
                        .foregroundStyle(.secondary)
                }
                .padding(.horizontal, Space.m)
                .frame(minHeight: 56)
                .background(Color.opawSurface, in: RoundedRectangle(cornerRadius: Radius.control, style: .continuous))
            }
        }
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

// MARK: - Energy

private struct EnergyStep: View {
    @Binding var draft: DogDraft
    let guessed: Bool
    let onNext: () -> Void

    private var goal: Int {
        DogTraits(size: draft.size, energy: draft.energy, ageYears: draft.hasBirthDate ? Dog.age(from: draft.birthDate) : 3)
            .recommendedDailyActivityMinutes
    }

    var body: some View {
        StepPage(
            eyebrow: "04  Energy",
            title: "How does \(draft.displayName) like to spend the day?",
            subtitle: guessed ? "We picked what's typical for the breed. Every dog is different." : nil,
            buttonTitle: "Continue",
            action: onNext
        ) {
            VStack(alignment: .leading, spacing: Space.m) {
                ForEach(EnergyLevel.allCases) { level in
                    EnergyRow(level: level, isSelected: draft.energy == level) {
                        draft.energy = level
                        Haptics.select()
                    }
                }
                HStack(spacing: Space.s) {
                    Image(systemName: "target")
                        .foregroundStyle(Color.opawOrange)
                    Text("Daily activity goal: ")
                        .foregroundColor(.secondary)
                    + Text("\(goal) min").bold()
                }
                .font(.callout)
                .contentTransition(.numericText())
                .animation(.snappy, value: goal)
                .padding(.top, Space.s)
            }
        }
    }
}

private struct EnergyRow: View {
    let level: EnergyLevel
    let isSelected: Bool
    let onTap: () -> Void

    var body: some View {
        Button(action: onTap) {
            HStack(spacing: Space.m) {
                Image(systemName: level.symbol)
                    .font(.system(size: 20, weight: .semibold))
                    .foregroundStyle(isSelected ? Color.white : Color.opawInk)
                    .frame(width: 48, height: 48)
                    .background(isSelected ? Color.opawOrange : Color.opawCream, in: Circle())
                    .phaseAnimator([false, true]) { content, up in
                        content.offset(y: isSelected && up ? -3 : 0)
                    } animation: { _ in
                        .easeInOut(duration: level.bobDuration)
                    }
                VStack(alignment: .leading, spacing: 2) {
                    Text(level.phrase)
                        .font(.headline)
                        .foregroundStyle(Color.opawInk)
                    Text(level.detail)
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                }
                Spacer(minLength: 0)
                Image(systemName: isSelected ? "checkmark.circle.fill" : "circle")
                    .font(.title3)
                    .foregroundStyle(isSelected ? Color.opawOrange : Color.opawInk.opacity(0.15))
            }
            .padding(Space.m)
            .background(Color.opawSurface, in: RoundedRectangle(cornerRadius: Radius.card, style: .continuous))
            .overlay {
                RoundedRectangle(cornerRadius: Radius.card, style: .continuous)
                    .strokeBorder(isSelected ? Color.opawOrange : Color.clear, lineWidth: 1.5)
            }
            .contentShape(Rectangle())
        }
        .buttonStyle(.pressable)
        .animation(.spring(response: 0.35, dampingFraction: 0.75), value: isSelected)
        .accessibilityAddTraits(isSelected ? .isSelected : [])
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

// MARK: - Birthday

private struct BirthdayStep: View {
    @Binding var draft: DogDraft
    let onNext: () -> Void

    private var ageText: String {
        let parts = Calendar.current.dateComponents([.year, .month], from: draft.birthDate, to: Date())
        let years = parts.year ?? 0
        let months = parts.month ?? 0
        if years == 0 {
            return months <= 1 ? "a brand new puppy" : "\(months) months old"
        }
        return years == 1 ? "1 year old" : "\(years) years old"
    }

    var body: some View {
        StepPage(
            eyebrow: "05  Birthday",
            title: "When is \(draft.displayName)'s birthday?",
            subtitle: "A guess is fine. It helps us set goals that fit their age.",
            buttonTitle: "Continue",
            action: {
                draft.hasBirthDate = true
                onNext()
            },
            secondaryTitle: "Not sure, skip",
            secondaryAction: {
                draft.hasBirthDate = false
                onNext()
            }
        ) {
            VStack(alignment: .leading, spacing: Space.m) {
                DatePicker("Birthday", selection: $draft.birthDate, in: ...Date(), displayedComponents: .date)
                    .datePickerStyle(.wheel)
                    .labelsHidden()
                    .frame(maxWidth: .infinity)
                    .background(Color.opawSurface, in: RoundedRectangle(cornerRadius: Radius.card, style: .continuous))
                (Text("That makes \(draft.displayName) ") + Text(ageText).italic().foregroundColor(.opawOrange) + Text("."))
                    .font(.opawDisplay(22, weight: .regular))
                    .foregroundStyle(Color.opawInk)
                    .contentTransition(.opacity)
                    .animation(.easeInOut, value: ageText)
            }
        }
    }
}

// MARK: - Reveal

private struct RevealStep: View {
    let name: String
    let capture: CaptureResult
    let onFinish: () -> Void

    @State private var stage = 0

    var body: some View {
        ZStack {
            PawConfetti(isActive: stage >= 1)
                .allowsHitTesting(false)
            VStack(spacing: 0) {
                Spacer(minLength: Space.l)
                ZStack {
                    Circle()
                        .fill(RadialGradient(
                            colors: [Color.opawOrange.opacity(0.16), Color.opawOrange.opacity(0)],
                            center: .center,
                            startRadius: 8,
                            endRadius: 170
                        ))
                        .frame(width: 340, height: 340)
                        .scaleEffect(stage >= 1 ? 1 : 0.3)
                    Ellipse()
                        .fill(Color.black.opacity(0.07))
                        .frame(width: 170, height: 16)
                        .blur(radius: 6)
                        .offset(y: 132)
                        .opacity(stage >= 1 ? 1 : 0)
                    avatar
                        .frame(height: 270)
                        .scaleEffect(stage >= 1 ? 1 : 0.6)
                        .opacity(stage >= 1 ? 1 : 0)
                }
                .frame(height: 340)

                VStack(spacing: Space.s) {
                    Text("Welcome home,")
                        .font(.opawDisplay(22, weight: .regular))
                        .foregroundStyle(.secondary)
                    Text(name)
                        .font(.opawDisplay(56))
                        .foregroundStyle(Color.opawInk)
                        .lineLimit(1)
                        .minimumScaleFactor(0.5)
                    Text("Tap them anytime. They'll notice.")
                        .font(.callout)
                        .foregroundStyle(.secondary)
                }
                .multilineTextAlignment(.center)
                .opacity(stage >= 2 ? 1 : 0)
                .offset(y: stage >= 2 ? 0 : 16)
                .padding(.top, Space.l)

                Spacer(minLength: Space.l)

                Button(action: onFinish) {
                    HStack(spacing: 8) {
                        Text("Start our story")
                        Image(systemName: "arrow.right")
                    }
                }
                .buttonStyle(.opawPrimary)
                .opacity(stage >= 3 ? 1 : 0)
                .disabled(stage < 3)
                .padding(.bottom, Space.s)
            }
            .padding(.horizontal, Space.l)
        }
        .task {
            try? await Task.sleep(for: .milliseconds(250))
            withAnimation(.spring(response: 0.7, dampingFraction: 0.62)) { stage = 1 }
            Haptics.success()
            try? await Task.sleep(for: .milliseconds(500))
            withAnimation(.easeOut(duration: 0.6)) { stage = 2 }
            try? await Task.sleep(for: .milliseconds(500))
            withAnimation(.easeOut(duration: 0.5)) { stage = 3 }
        }
    }

    @ViewBuilder
    private var avatar: some View {
        if let cutout = capture.cutout {
            LivingPhotoView(image: cutout, mood: .happy)
        } else if let original = capture.original {
            Image(uiImage: original)
                .resizable()
                .scaledToFill()
                .frame(width: 230, height: 230)
                .clipShape(Circle())
        } else {
            Image(systemName: "dog.fill")
                .font(.system(size: 120))
                .foregroundStyle(Color.opawOrange)
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
