import CoreLocation
import OpawKit
import PhotosUI
import SwiftData
import SwiftUI
import UserNotifications

/// First run (4a to 4g): welcome, photo first, details with the detected breed, the avatar reveal,
/// what to track, the household and permissions. Then the paywall (5a), the one-time offer (5c) once,
/// and home.
struct OnboardingView: View {
    enum Step: Int, Comparable {
        case welcome, camera, details, reveal, track, household, permissions

        static func < (lhs: Step, rhs: Step) -> Bool { lhs.rawValue < rhs.rawValue }
    }

    @Environment(\.modelContext) private var modelContext
    @Environment(PremiumStore.self) private var premium
    @AppStorage("premiumOfferShown", store: OpawDefaults.shared) private var offerShown = false
    @State private var step: Step = .welcome
    @State private var forward = true
    @State private var draft = DogDraft(trackedKinds: OnboardingView.defaultTracked)
    @State private var capture = CaptureResult()
    @State private var isProcessing = false
    @State private var foundDog = false
    @State private var showPaywall = false
    @State private var showOffer = false
    @Environment(\.duoOpen) private var duoOpen

    static let defaultTracked = [CareKind.meal, .water, .pee, .poop, .weight].map(\.rawValue) + [TrackOption.walkKey]

    var body: some View {
        ZStack {
            page
                // Steps without a two-pane design keep the phone layout, centered.
                .frame(maxWidth: duoOpen && ![.camera, .details, .reveal].contains(step) ? 480 : .infinity)
                .id(step)
                .transition(.asymmetric(
                    insertion: .offset(x: forward ? 56 : -56).combined(with: .opacity),
                    removal: .offset(x: forward ? -56 : 56).combined(with: .opacity)
                ))
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .opawBackground()
        .fullScreenCover(isPresented: $showPaywall, onDismiss: afterPaywall) {
            PaywallView(petName: draft.displayName, petImage: capture.cutout) { purchased in
                if purchased { offerShown = true }
                showPaywall = false
            }
        }
        .fullScreenCover(isPresented: $showOffer, onDismiss: finish) {
            OneTimeOfferView(petImage: capture.cutout) { _ in showOffer = false }
                .presentationBackground(.clear)
        }
    }

    @ViewBuilder
    private var page: some View {
        switch step {
        case .welcome:
            WelcomeStep { go(.camera) }
        case .camera:
            CameraStep(onBack: { go(.welcome) }, onPhoto: use)
        case .details:
            DetailsStep(draft: $draft, capture: capture, isProcessing: isProcessing, foundDog: foundDog, onBack: { go(.camera) }) { go(.reveal) }
        case .reveal:
            RevealStep(name: draft.displayName, capture: capture, isProcessing: isProcessing, onBack: { go(.details) }, onRetake: { go(.camera) }) { go(.track) }
        case .track:
            TrackStep(draft: $draft, onBack: { go(.reveal) }) { go(.household) }
        case .household:
            HouseholdStep(name: draft.displayName, onBack: { go(.track) }) { go(.permissions) }
        case .permissions:
            PermissionsStep(name: draft.displayName, image: capture.cutout ?? capture.original, onBack: { go(.household) }) {
                if premium.isPremium { finish() } else { showPaywall = true }
            }
        }
    }

    private func go(_ next: Step) {
        forward = next > step
        Haptics.soft()
        withAnimation(.spring(response: 0.5, dampingFraction: 0.88)) { step = next }
    }

    /// Takes the new photo, moves on to the details, and builds the avatar in the background.
    private func use(_ image: UIImage) {
        let prepared = image.preparedForProcessing()
        capture = CaptureResult(original: prepared)
        isProcessing = true
        go(.details)
        Task {
            async let hasDog = CutoutService.containsDog(prepared)
            async let guess = CutoutService.guessBreed(prepared)
            let cutout = try? await CutoutService.cutout(prepared)
            let found = await hasDog
            let breedGuess = await guess
            guard capture.original === prepared else { return }
            withAnimation(.spring(response: 0.6, dampingFraction: 0.8)) {
                capture = CaptureResult(original: prepared, cutout: cutout, guess: breedGuess)
                foundDog = found || breedGuess != nil
                isProcessing = false
            }
            if let breedGuess, draft.breed.isEmpty {
                draft.breed = breedGuess.breed.name
                draft.size = breedGuess.breed.size
                draft.energy = breedGuess.breed.energy
            }
            Haptics.success()
        }
    }

    private func afterPaywall() {
        if premium.isPremium || offerShown {
            finish()
        } else {
            offerShown = true
            showOffer = true
        }
    }

    private func finish() {
        draft.save(photo: capture.original, cutout: capture.cutout, in: modelContext)
    }
}

// MARK: - Shared layout

/// Back button, six progress bars and "n of 6".
private struct StepHeader: View {
    let index: Int
    var inset: CGFloat = 20
    let onBack: () -> Void

    var body: some View {
        HStack(spacing: 16) {
            Button(action: onBack) { RoundIcon(symbol: "chevron.left") }
                .buttonStyle(.pressable)
                .accessibilityLabel("Back")
            HStack(spacing: 5) {
                ForEach(1...6, id: \.self) { bar in
                    Capsule()
                        .fill(bar <= index ? Color(hex: 0x111111) : Color(hex: 0xDCDCDF))
                        .frame(height: 6)
                }
            }
            .animation(.easeInOut(duration: 0.3), value: index)
            Text("\(index) of 6")
                .font(.opaw(13, .bold))
                .foregroundStyle(Color.opawMuted)
                .frame(width: 44, alignment: .trailing)
        }
        .padding(.horizontal, inset)
        .padding(.top, 10)
        .accessibilityElement(children: .contain)
        .accessibilityValue("Step \(index) of 6")
    }
}

/// A step's big title and gray subtitle.
private struct StepTitle: View {
    let title: String
    let subtitle: String

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(title)
                .font(.opaw(30, .black))
                .foregroundStyle(Color.opawInk)
                .fixedSize(horizontal: false, vertical: true)
            Text(subtitle)
                .font(.opaw(16, .medium))
                .foregroundStyle(Color.opawMuted)
                .lineSpacing(4)
                .fixedSize(horizontal: false, vertical: true)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.horizontal, 24)
        .padding(.top, 26)
    }
}

/// The primary button pinned to the bottom, with an optional text button under it.
private struct BottomActions: View {
    let title: String
    var isEnabled = true
    var secondary: String?
    var secondaryColor: Color = .opawInk
    let action: () -> Void
    var secondaryAction: (() -> Void)?

    var body: some View {
        VStack(spacing: 14) {
            Button(title, action: action)
                .buttonStyle(BlackPillButtonStyle())
                .disabled(!isEnabled)
            if let secondary, let secondaryAction {
                Button(secondary, action: secondaryAction)
                    .font(.opaw(15, .bold))
                    .foregroundStyle(secondaryColor)
            }
        }
        .padding(.horizontal, 20)
        .padding(.bottom, 8)
    }
}

// MARK: - 4a Welcome

private struct WelcomeStep: View {
    let onStart: () -> Void
    @Environment(PremiumStore.self) private var premium
    @State private var restoreNote: String?

    var body: some View {
        VStack(spacing: 0) {
            ZStack(alignment: .topLeading) {
                GhostCycle()
                    .frame(width: 230, height: 260)
                    .loopMotion(.bob, duration: 3.2)
                    .frame(maxWidth: .infinity)
                    .padding(.top, 40)
                SpeechBubble(text: "hi! take my picture?")
                    .frame(maxWidth: .infinity, alignment: .trailing)
                    .padding(.trailing, 12)
            }
            .frame(height: 330, alignment: .top)
            .padding(.top, 56)

            Spacer(minLength: 16)

            VStack(alignment: .leading, spacing: 10) {
                Text("Your pet, alive on your phone")
                    .font(.opaw(40, .black))
                    .foregroundStyle(Color.opawInk)
                    .lineSpacing(-2)
                    .fixedSize(horizontal: false, vertical: true)
                Text("Snap one photo and we turn it into an avatar that reacts to every walk, meal and cuddle you log.")
                    .font(.opaw(16, .medium))
                    .foregroundStyle(Color.opawMuted)
                    .lineSpacing(4)
                    .fixedSize(horizontal: false, vertical: true)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.horizontal, 24)

            Spacer(minLength: 24)

            BottomActions(title: "Get started", secondary: "I already have an account", action: onStart) {
                Task {
                    await premium.restore()
                    restoreNote = premium.isPremium
                        ? "Your Opaw Premium is back on. Your pets live on each iPhone, so add your pet to get going."
                        : "Opaw keeps your pets on this iPhone, so there's nothing to sign in to. Tap Get started to add your pet."
                    premium.errorMessage = nil
                }
            }
        }
        .alert("Welcome back", isPresented: Binding(get: { restoreNote != nil }, set: { if !$0 { restoreNote = nil } })) {
            Button("OK", role: .cancel) {}
        } message: {
            Text(restoreNote ?? "")
        }
    }
}

// MARK: - 4b Camera

private struct CameraStep: View {
    let onBack: () -> Void
    let onPhoto: (UIImage) -> Void

    @State private var camera = PetCamera()
    @State private var pick: PhotosPickerItem?
    @State private var showLibrary = false
    @State private var isCapturing = false
    @Environment(\.duoOpen) private var duoOpen

    var body: some View {
        Group {
            if duoOpen {
                duoBody
            } else {
                phoneBody
            }
        }
        .task { await camera.start() }
        .onDisappear { camera.stop() }
        .photosPicker(isPresented: $showLibrary, selection: $pick, matching: .images)
        .onChange(of: pick) { _, item in
            guard let item else { return }
            Task {
                if let data = try? await item.loadTransferable(type: Data.self), let image = UIImage(data: data) {
                    onPhoto(image)
                }
            }
        }
    }

    private var preview: some View {
        Group {
            if camera.isAvailable {
                CameraPreview(session: camera.session)
            } else {
                Color(hex: 0xE6E6E8)
            }
        }
    }

    // MARK: Open display (6l)

    private var duoBody: some View {
        DuoPanes(rail: false, bleedLeft: true) {
            preview
                .clipped()
                .ignoresSafeArea(edges: .vertical)
                .overlay {
                    Brackets()
                        .padding(.horizontal, 40)
                        .padding(.top, 40)
                        .padding(.bottom, 37)
                }
        } right: {
            VStack(alignment: .leading, spacing: 0) {
                StepHeader(index: 1, inset: 0, onBack: onBack)
                VStack(alignment: .leading, spacing: 8) {
                    Text("Start with a photo of your pet")
                        .font(.opaw(30, .black))
                        .foregroundStyle(Color.opawInk)
                        .fixedSize(horizontal: false, vertical: true)
                    Text("We use it to build the avatar and work out the species and breed.")
                        .font(.opaw(16, .medium))
                        .foregroundStyle(Color.opawMuted)
                        .lineSpacing(5)
                        .fixedSize(horizontal: false, vertical: true)
                }
                .padding(.top, 26)
                WrapLayout(spacing: 6) {
                    ForEach(["Whole body", "Good light", "One pet only"], id: \.self) { tip in
                        HStack(spacing: 6) {
                            Icon(.check, size: 12, weight: 3.4)
                            Text(tip)
                                .font(.opaw(13, .bold))
                        }
                        .foregroundStyle(Color.opawInk)
                        .padding(.leading, 10)
                        .padding(.trailing, 12)
                        .padding(.vertical, 8)
                        .background(Color.opawSurface, in: Capsule())
                        .shadow(color: .black.opacity(0.05), radius: 8, y: 6)
                    }
                }
                .padding(.top, 18)
                Spacer(minLength: 16)
                HStack {
                    libraryButton
                    Spacer()
                    shutterButton(ring: Color.opawCream)
                    Spacer()
                    Button { camera.flip() } label: {
                        Icon(.refresh, size: 22)
                            .foregroundStyle(Color(hex: 0x111111))
                            .frame(width: 54, height: 54)
                            .background(Color.white, in: Circle())
                            .floatingShadow()
                    }
                    .buttonStyle(.pressable)
                    .opacity(camera.isAvailable ? 1 : 0.4)
                    .disabled(!camera.isAvailable)
                    .accessibilityLabel("Flip camera")
                }
                .padding(.horizontal, 30)
                .padding(.bottom, 10)
            }
        }
    }

    private var phoneBody: some View {
        ZStack(alignment: .top) {
            preview
                .ignoresSafeArea()

            VStack(spacing: 0) {
                StepHeader(index: 1, onBack: onBack)
                ZStack {
                    Brackets()
                        .padding(.horizontal, 36)
                        .frame(height: 400)
                        .padding(.top, 52)
                        .frame(maxHeight: .infinity, alignment: .top)
                    hint
                        .frame(maxHeight: .infinity, alignment: .bottom)
                        .padding(.bottom, 18)
                }
                controls
            }
        }
    }

    private var hint: some View {
        HStack(spacing: 8) {
            Icon(.sparkle, size: 16, weight: 2.4)
            Text(camera.isAvailable ? "Fit your whole pet in the frame" : "Pick a photo of your whole pet")
                .font(.opaw(15, .heavy))
        }
        .foregroundStyle(Color(hex: 0x111111))
        .padding(.horizontal, 16)
        .padding(.vertical, 10)
        .background(Color.white, in: Capsule())
        .shadow(color: .black.opacity(0.1), radius: 13, y: 10)
    }

    private var libraryButton: some View {
            Button { showLibrary = true } label: {
                RoundedRectangle(cornerRadius: 16, style: .continuous)
                    .fill(Color(hex: 0xE6E6E8))
                    .overlay {
                        Image(systemName: "photo.on.rectangle")
                            .font(.system(size: 18, weight: .semibold))
                            .foregroundStyle(Color(hex: 0x999999))
                    }
                    .frame(width: 54, height: 54)
                    .padding(3)
                    .background(Color.white, in: RoundedRectangle(cornerRadius: 19, style: .continuous))
                    .overlay(RoundedRectangle(cornerRadius: 19, style: .continuous).strokeBorder(Color(hex: 0xE6E6E8), lineWidth: 1))
            }
            .buttonStyle(.pressable)
            .accessibilityLabel("Choose from library")
    }

    private func shutterButton(ring: Color) -> some View {
            Button {
                if camera.isAvailable {
                    guard !isCapturing else { return }
                    isCapturing = true
                    Haptics.tap()
                    Task {
                        if let image = await camera.capture() { onPhoto(image) }
                        isCapturing = false
                    }
                } else {
                    showLibrary = true
                }
            } label: {
                Circle()
                    .fill(Color(hex: 0x111111))
                    .frame(width: 84, height: 84)
                    .padding(5)
                    .background(Circle().fill(ring))
                    .padding(4)
                    .background(Circle().fill(Color(hex: 0x111111)))
                    .scaleEffect(isCapturing ? 0.92 : 1)
            }
            .buttonStyle(.pressable)
            .accessibilityLabel("Take photo")
    }

    private var controls: some View {
        HStack {
            libraryButton
            Spacer()
            shutterButton(ring: .white)
            Spacer()
            Button { camera.flip() } label: {
                Icon(.refresh, size: 22)
                    .foregroundStyle(Color(hex: 0x111111))
                    .frame(width: 54, height: 54)
                    .background(Color(hex: 0xF1F1F2), in: Circle())
            }
            .buttonStyle(.pressable)
            .opacity(camera.isAvailable ? 1 : 0.4)
            .disabled(!camera.isAvailable)
            .accessibilityLabel("Flip camera")
        }
        .padding(.horizontal, 40)
        .padding(.bottom, 20)
        .frame(height: 170)
        .background(
            UnevenRoundedRectangle(topLeadingRadius: 36, topTrailingRadius: 36, style: .continuous)
                .fill(Color.white)
                .ignoresSafeArea(edges: .bottom)
        )
    }
}

/// White viewfinder corners.
private struct Brackets: View {
    var body: some View {
        GeometryReader { proxy in
            let size = proxy.size
            Path { path in
                let arm: CGFloat = 44, r: CGFloat = 30
                // Top left
                path.move(to: CGPoint(x: 0, y: arm))
                path.addLine(to: CGPoint(x: 0, y: r))
                path.addQuadCurve(to: CGPoint(x: r, y: 0), control: .zero)
                path.addLine(to: CGPoint(x: arm, y: 0))
                // Top right
                path.move(to: CGPoint(x: size.width - arm, y: 0))
                path.addLine(to: CGPoint(x: size.width - r, y: 0))
                path.addQuadCurve(to: CGPoint(x: size.width, y: r), control: CGPoint(x: size.width, y: 0))
                path.addLine(to: CGPoint(x: size.width, y: arm))
                // Bottom left
                path.move(to: CGPoint(x: 0, y: size.height - arm))
                path.addLine(to: CGPoint(x: 0, y: size.height - r))
                path.addQuadCurve(to: CGPoint(x: r, y: size.height), control: CGPoint(x: 0, y: size.height))
                path.addLine(to: CGPoint(x: arm, y: size.height))
                // Bottom right
                path.move(to: CGPoint(x: size.width - arm, y: size.height))
                path.addLine(to: CGPoint(x: size.width - r, y: size.height))
                path.addQuadCurve(to: CGPoint(x: size.width, y: size.height - r), control: CGPoint(x: size.width, y: size.height))
                path.addLine(to: CGPoint(x: size.width, y: size.height - arm))
            }
            .stroke(Color.white, style: StrokeStyle(lineWidth: 5, lineCap: .butt))
        }
        .allowsHitTesting(false)
    }
}

// MARK: - 4c Details

private struct DetailsStep: View {
    enum Field: String, Identifiable {
        case species, breed, birthday, weight, sex
        var id: String { rawValue }
    }

    @Binding var draft: DogDraft
    let capture: CaptureResult
    let isProcessing: Bool
    let foundDog: Bool
    let onBack: () -> Void
    let onNext: () -> Void

    @State private var field: Field?
    @State private var progress = 0.0
    @FocusState private var nameFocused: Bool
    @Environment(\.duoOpen) private var duoOpen

    var body: some View {
        Group {
            if duoOpen {
                duoBody
            } else {
                phoneBody
            }
        }
        .sheet(item: $field) { field in
            FieldSheet(field: field, draft: $draft)
        }
        .task {
            withAnimation(.easeOut(duration: 6)) { progress = 0.9 }
            try? await Task.sleep(for: .milliseconds(400))
            if draft.name.isEmpty { nameFocused = true }
        }
    }

    // MARK: Open display (6m)

    private var duoBody: some View {
        DuoPanes(rail: false, leading: 36) {
            VStack(alignment: .leading, spacing: 0) {
                Group {
                    if let image = capture.original {
                        Image(uiImage: image).resizable().scaledToFill()
                    } else {
                        Color(hex: 0xE6E6E8)
                    }
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .clipShape(RoundedRectangle(cornerRadius: 30, style: .continuous))
                Text(title)
                    .font(.opaw(26, .black))
                    .foregroundStyle(Color.opawInk)
                    .lineLimit(1)
                    .minimumScaleFactor(0.7)
                    .contentTransition(.opacity)
                    .padding(.top, 16)
                Text(isProcessing ? "Building the avatar while you finish" : "Avatar ready")
                    .font(.opaw(14, .medium))
                    .foregroundStyle(Color.opawMuted)
                    .padding(.top, 4)
                ProgressBar(value: isProcessing ? progress : 1)
                    .padding(.top, 12)
            }
            .padding(.trailing, 26)
            .padding(.top, 16)
            .padding(.bottom, 6)
        } right: {
            VStack(spacing: 0) {
                StepHeader(index: 2, inset: 0, onBack: onBack)
                ScrollView {
                    card
                        .padding(.horizontal, 20)
                        .padding(.top, 16)
                        .padding(.bottom, 16)
                }
                .scrollIndicators(.hidden)
                .scrollDismissesKeyboard(.interactively)
                .padding(.horizontal, -20)
                Button("Continue", action: onNext)
                    .buttonStyle(BlackPillButtonStyle(height: 62))
                    .disabled(!draft.isValid)
            }
        }
    }

    private var phoneBody: some View {
        VStack(spacing: 0) {
            StepHeader(index: 2, onBack: onBack)
            ScrollView {
                VStack(spacing: 0) {
                    heading
                    ProgressBar(value: isProcessing ? progress : 1)
                        .padding(.horizontal, 20)
                        .padding(.top, 18)
                    card
                        .padding(.horizontal, 20)
                        .padding(.top, 18)
                    Text("Breed and age set walk goals and food portions. Tap any detected field to change it.")
                        .font(.opaw(14, .medium))
                        .foregroundStyle(Color.opawMuted)
                        .lineSpacing(3)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(.horizontal, 24)
                        .padding(.top, 12)
                }
                .padding(.bottom, 24)
            }
            .scrollIndicators(.hidden)
            .scrollDismissesKeyboard(.interactively)
            BottomActions(title: "Continue", isEnabled: draft.isValid, action: onNext)
        }
    }

    private var heading: some View {
        HStack(spacing: 14) {
            Group {
                if let image = capture.original {
                    Image(uiImage: image).resizable().scaledToFill()
                } else {
                    Color(hex: 0xE6E6E8)
                }
            }
            .frame(width: 72, height: 72)
            .clipShape(RoundedRectangle(cornerRadius: 24, style: .continuous))
            .shadow(color: .black.opacity(0.06), radius: 12, y: 10)
            VStack(alignment: .leading, spacing: 4) {
                Text(title)
                    .font(.opaw(26, .black))
                    .foregroundStyle(Color.opawInk)
                    .fixedSize(horizontal: false, vertical: true)
                    .contentTransition(.opacity)
                Text(isProcessing ? "Building the avatar while you finish" : "Avatar ready")
                    .font(.opaw(14, .medium))
                    .foregroundStyle(Color.opawMuted)
            }
            Spacer(minLength: 0)
        }
        .padding(.horizontal, 20)
        .padding(.top, 22)
    }

    private var title: String {
        if let guess = capture.guess { return "Looks like a\(guess.breed.name.first.map { "AEIOU".contains($0) } == true ? "n" : "") \(guess.breed.name)" }
        if isProcessing { return "Taking a good look…" }
        return "Tell us about your pet"
    }

    private var card: some View {
        VStack(spacing: 0) {
            VStack(alignment: .leading, spacing: 2) {
                Text("Name")
                    .font(.opaw(13, .semibold))
                    .foregroundStyle(Color.opawMuted)
                TextField("Their name", text: $draft.name)
                    .font(.opaw(26, .black))
                    .foregroundStyle(Color.opawInk)
                    .tint(Color.opawInk)
                    .textInputAutocapitalization(.words)
                    .autocorrectionDisabled()
                    .submitLabel(.done)
                    .focused($nameFocused)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.vertical, duoOpen ? 12 : 14)
            row(.species, "Species", value: draft.species.capitalized, detected: foundDog && draft.species == "dog")
            row(.breed, "Breed", value: draft.breed.isEmpty ? "Add" : draft.breed, detected: capture.guess?.breed.name == draft.breed && !draft.breed.isEmpty)
            row(.birthday, "Birthday", value: birthdayText)
            row(.weight, "Weight", value: draft.weightKg.map { OpawFormat.weight(kg: $0) } ?? "Add")
            row(.sex, "Sex", value: sexText)
        }
        .padding(.horizontal, 20)
        .padding(.top, duoOpen ? 2 : 4)
        .padding(.bottom, 2)
        .whiteCard()
    }

    private func row(_ field: Field, _ label: String, value: String, detected: Bool = false) -> some View {
        Button {
            nameFocused = false
            self.field = field
        } label: {
            HStack {
                HStack(spacing: 8) {
                    Text(label)
                        .font(.opaw(15, .semibold))
                        .foregroundStyle(Color.opawMuted)
                    if detected { SparkleChip(text: "Detected", size: 11, iconSize: 11) }
                }
                Spacer(minLength: 8)
                HStack(spacing: 6) {
                    Text(value)
                        .font(.opaw(16, .heavy))
                        .foregroundStyle(value == "Add" ? Color.opawGhost : Color.opawInk)
                        .lineLimit(1)
                    Icon(.chevronDown, size: 16)
                        .foregroundStyle(Color.opawGhost)
                }
            }
            .padding(.vertical, duoOpen ? 13 : 15)
            .contentShape(Rectangle())
            .overlay(alignment: .top) { Rectangle().fill(Color.opawHairline).frame(height: 1) }
        }
        .buttonStyle(.plain)
    }

    private var birthdayText: String {
        guard draft.hasBirthDate else { return "Add" }
        let years = Int(Dog.age(from: draft.birthDate))
        let age = years < 1 ? "puppy" : (years == 1 ? "1 yr" : "\(years) yrs")
        return "\(draft.birthDate.formatted(.dateTime.month(.abbreviated).year())) · \(age)"
    }

    private var sexText: String {
        guard !draft.sex.isEmpty else { return "Add" }
        let fixed = draft.sex == "female" ? "spayed" : "neutered"
        return draft.isNeutered ? "\(draft.sex.capitalized) · \(fixed)" : draft.sex.capitalized
    }
}

/// A thin black progress bar on a light track.
private struct ProgressBar: View {
    let value: Double

    var body: some View {
        GeometryReader { proxy in
            Capsule()
                .fill(Color(hex: 0x111111))
                .frame(width: proxy.size.width * min(1, max(0, value)))
        }
        .frame(height: 6)
        .background(Color.opawTrack, in: Capsule())
        .clipShape(Capsule())
        .animation(.easeOut(duration: 0.4), value: value >= 1)
    }
}

/// Changes one detail from 4c.
private struct FieldSheet: View {
    let field: DetailsStep.Field
    @Binding var draft: DogDraft
    @Environment(\.dismiss) private var dismiss
    @State private var weightText = ""

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack {
                Text(title)
                    .font(.opaw(22, .black))
                Spacer()
                Button("Done") { save(); dismiss() }
                    .font(.opaw(16, .heavy))
            }
            .foregroundStyle(Color.opawInk)
            .padding(.top, 24)
            content
                .padding(.top, 16)
            Spacer(minLength: 0)
        }
        .padding(.horizontal, 20)
        .presentationDetents([field == .breed ? PresentationDetent.large : PresentationDetent.medium])
        .presentationCornerRadius(36)
        .presentationBackground(Color.opawCream)
        .onAppear {
            if let kg = draft.weightKg {
                weightText = OpawFormat.weightValue(kg: kg).formatted(.number.precision(.fractionLength(0...1)))
            }
            if field == .birthday { draft.hasBirthDate = true }
        }
    }

    private var title: String {
        switch field {
        case .species: "Species"
        case .breed: "Breed"
        case .birthday: "Birthday"
        case .weight: "Weight"
        case .sex: "Sex"
        }
    }

    @ViewBuilder
    private var content: some View {
        switch field {
        case .species:
            VStack(spacing: 8) {
                ForEach(["dog", "cat", "bunny"], id: \.self) { species in
                    choice(species.capitalized, selected: draft.species == species) { draft.species = species }
                }
            }
        case .breed:
            BreedSearch(breed: $draft.breed) { info in
                draft.breed = info.name
                draft.size = info.size
                draft.energy = info.energy
                dismiss()
            }
        case .birthday:
            DatePicker("Birthday", selection: $draft.birthDate, in: ...Date(), displayedComponents: .date)
                .datePickerStyle(.wheel)
                .labelsHidden()
                .frame(maxWidth: .infinity)
                .padding(.vertical, 8)
                .whiteCard()
        case .weight:
            HStack {
                TextField("0", text: $weightText)
                    .keyboardType(.decimalPad)
                    .font(.opaw(34, .black))
                Text(OpawFormat.weightUnit)
                    .font(.opaw(20, .heavy))
                    .foregroundStyle(Color.opawMuted)
            }
            .padding(.horizontal, 20)
            .frame(height: 72)
            .whiteCard()
        case .sex:
            VStack(spacing: 8) {
                choice("Female", selected: draft.sex == "female") { draft.sex = "female" }
                choice("Male", selected: draft.sex == "male") { draft.sex = "male" }
                HStack {
                    Text(draft.sex == "male" ? "Neutered" : "Spayed")
                        .font(.opaw(16, .heavy))
                    Spacer()
                    DesignToggle(isOn: $draft.isNeutered)
                }
                .padding(.horizontal, 18)
                .frame(height: 60)
                .whiteCard(radius: 18)
                .padding(.top, 8)
            }
        }
    }

    private func choice(_ title: String, selected: Bool, action: @escaping () -> Void) -> some View {
        Button {
            Haptics.select()
            action()
        } label: {
            HStack {
                Text(title).font(.opaw(17, .heavy))
                Spacer()
                if selected { Icon(.check, size: 14) }
            }
            .foregroundStyle(selected ? Color.white : Color.opawInk)
            .padding(.horizontal, 18)
            .frame(height: 60)
            .background(selected ? Color(hex: 0x111111) : Color.opawSurface, in: RoundedRectangle(cornerRadius: 18, style: .continuous))
        }
        .buttonStyle(.pressable)
    }

    private func save() {
        guard field == .weight else { return }
        let normalized = weightText.replacingOccurrences(of: ",", with: ".")
        if let value = Double(normalized), value > 0 {
            let kg = OpawFormat.usesImperial ? value / OpawFormat.poundsPerKilogram : value
            draft.weightKg = kg
            if draft.breed.isEmpty || BreedCatalog.lookup(draft.breed) == nil {
                draft.size = kg < 10 ? .small : (kg < 25 ? .medium : (kg < 45 ? .large : .giant))
            }
        } else if normalized.isEmpty {
            draft.weightKg = nil
        }
    }
}

/// Type a breed, with suggestions from the catalog.
private struct BreedSearch: View {
    @Binding var breed: String
    let onPick: (BreedInfo) -> Void

    var body: some View {
        VStack(spacing: 8) {
            TextField("Breed or mix", text: $breed)
                .font(.opaw(20, .heavy))
                .textInputAutocapitalization(.words)
                .autocorrectionDisabled()
                .padding(.horizontal, 18)
                .frame(height: 56)
                .whiteCard(radius: 18)
            ScrollView {
                VStack(spacing: 0) {
                    ForEach(BreedCatalog.suggestions(for: breed, limit: 12)) { info in
                        Button { onPick(info) } label: {
                            HStack {
                                Text(info.name)
                                    .font(.opaw(16, .bold))
                                    .foregroundStyle(Color.opawInk)
                                Spacer()
                                Text(info.size.title)
                                    .font(.opaw(13, .semibold))
                                    .foregroundStyle(Color.opawMuted)
                            }
                            .padding(.vertical, 13)
                            .contentShape(Rectangle())
                            .overlay(alignment: .bottom) { Rectangle().fill(Color.opawHairline).frame(height: 1) }
                        }
                        .buttonStyle(.plain)
                    }
                }
                .padding(.horizontal, 4)
            }
        }
    }
}

// MARK: - 4d Reveal

private struct RevealStep: View {
    let name: String
    let capture: CaptureResult
    let isProcessing: Bool
    let onBack: () -> Void
    let onRetake: () -> Void
    let onNext: () -> Void

    private var image: UIImage? { capture.cutout ?? capture.original }
    @Environment(\.duoOpen) private var duoOpen

    var body: some View {
        Group {
            if duoOpen {
                duoBody
            } else {
                phoneBody
            }
        }
        .animation(.spring(response: 0.6, dampingFraction: 0.75), value: isProcessing)
    }

    // MARK: Open display (6n)

    private var duoBody: some View {
        DuoPanes(rail: false, leading: 0) {
            ZStack(alignment: .topLeading) {
                Group {
                    if isProcessing {
                        ProgressView()
                            .controlSize(.large)
                            .frame(width: 260, height: 290)
                    } else {
                        StickerPet(image: image, edge: 6, motion: .bob, duration: 3.2)
                            .frame(width: 260, height: 290)
                            .transition(.scale(scale: 0.7).combined(with: .opacity))
                    }
                }
                .padding(.leading, 92)
                .padding(.top, 110)
                SpeechBubble(text: "is that me?!")
                    .font(.opaw(15, .heavy))
                    .padding(.leading, 270)
                    .padding(.top, 80)
                    .opacity(isProcessing ? 0 : 1)
                readyChip
                    .padding(.leading, 36)
                    .padding(.top, 20)
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        } right: {
            VStack(alignment: .leading, spacing: 0) {
                StepHeader(index: 3, inset: 0, onBack: onBack)
                VStack(alignment: .leading, spacing: 8) {
                    Text("Say hi to \(name)")
                        .font(.opaw(34, .black))
                        .foregroundStyle(Color.opawInk)
                        .lineLimit(1)
                        .minimumScaleFactor(0.6)
                    Text("\(name)'s mood follows the care you log.")
                        .font(.opaw(16, .medium))
                        .foregroundStyle(Color.opawMuted)
                }
                .padding(.top, 26)
                HStack(spacing: 10) {
                    mood("Walked", .bob, 1.4)
                    mood("Fed", .breathe, 2.6)
                    mood("Ignored", .droop, 3)
                }
                .padding(.top, 20)
                Spacer(minLength: 16)
                actions(height: 62)
            }
        }
    }

    @ViewBuilder
    private var readyChip: some View {
        if isProcessing {
            HStack(spacing: 6) {
                ProgressView().controlSize(.mini).tint(.white)
                Text("Building avatar").font(.opaw(13, .heavy))
            }
            .foregroundStyle(.white)
            .padding(.horizontal, 12)
            .padding(.vertical, 7)
            .background(Color(hex: 0x111111), in: Capsule())
        } else {
            SparkleChip(text: "Avatar ready")
        }
    }

    private func actions(height: CGFloat) -> some View {
        HStack(spacing: 12) {
            Button(action: onRetake) {
                Icon(.refresh, size: 22)
                    .foregroundStyle(Color(hex: 0x111111))
                    .frame(width: height, height: height)
                    .background(Color.white, in: Circle())
                    .shadow(color: .black.opacity(0.08), radius: 13, y: 10)
            }
            .buttonStyle(.pressable)
            .accessibilityLabel("Retake photo")
            Button("Looks like \(name)", action: onNext)
                .buttonStyle(BlackPillButtonStyle(height: height))
                .disabled(isProcessing)
        }
    }

    private var phoneBody: some View {
        VStack(spacing: 0) {
            StepHeader(index: 3, onBack: onBack)
            ZStack(alignment: .topLeading) {
                Group {
                    if isProcessing {
                        ProgressView()
                            .controlSize(.large)
                            .frame(width: 230, height: 250)
                    } else {
                        StickerPet(image: image, edge: 5, motion: .bob, duration: 3.2)
                            .frame(width: 230, height: 250)
                            .transition(.scale(scale: 0.7).combined(with: .opacity))
                    }
                }
                .frame(maxWidth: .infinity)
                .padding(.top, 40)
                SpeechBubble(text: "is that me?!")
                    .padding(.leading, 218)
                    .padding(.top, 6)
                    .opacity(isProcessing ? 0 : 1)
                readyChip
                .padding(.leading, 24)
                .padding(.top, 14)
            }
            .frame(height: 300, alignment: .top)
            .padding(.top, 10)

            VStack(spacing: 6) {
                Text("Say hi to \(name)")
                    .font(.opaw(30, .black))
                    .foregroundStyle(Color.opawInk)
                    .lineLimit(1)
                    .minimumScaleFactor(0.6)
                Text("\(name)'s mood follows the care you log.")
                    .font(.opaw(15, .medium))
                    .foregroundStyle(Color.opawMuted)
            }
            .multilineTextAlignment(.center)
            .padding(.horizontal, 24)
            .padding(.top, 20)

            HStack(spacing: 10) {
                mood("Walked", .bob, 1.4)
                mood("Fed", .breathe, 2.6)
                mood("Ignored", .droop, 3)
            }
            .padding(.horizontal, 20)
            .padding(.top, 22)

            Spacer(minLength: 16)

            actions(height: 64)
                .padding(.horizontal, 20)
                .padding(.bottom, 8)
        }
    }

    private func mood(_ label: String, _ motion: LoopMotion, _ duration: Double) -> some View {
        VStack(spacing: 6) {
            StickerPet(image: isProcessing ? nil : image, edge: 3, motion: motion, duration: duration)
                .frame(width: duoOpen ? 70 : 76, height: duoOpen ? 78 : 84)
                .opacity(motion == .droop ? 0.6 : 1)
                .saturation(motion == .droop ? 0.3 : 1)
                .frame(maxWidth: .infinity)
                .frame(height: duoOpen ? 100 : 108)
                .whiteCard(radius: 24)
            Text(label)
                .font(.opaw(13, .bold))
                .foregroundStyle(Color.opawInk)
        }
    }
}

// MARK: - 4e Track

/// One choice on the "What should we track?" grid.
struct TrackOption: Identifiable {
    static let walkKey = "walks"

    let title: String
    let icon: DesignIcon
    let keys: [String]
    var id: String { title }

    static let all: [TrackOption] = [
        TrackOption(title: "Meals", icon: .meal, keys: [CareKind.meal.rawValue]),
        TrackOption(title: "Water", icon: .water, keys: [CareKind.water.rawValue]),
        TrackOption(title: "Walks", icon: .activity, keys: [walkKey]),
        TrackOption(title: "Poop", icon: .poop, keys: [CareKind.poop.rawValue, CareKind.pee.rawValue]),
        TrackOption(title: "Meds", icon: .meds, keys: [CareKind.meds.rawValue]),
        TrackOption(title: "Grooming", icon: .bath, keys: [CareKind.bath.rawValue, CareKind.brush.rawValue, CareKind.nails.rawValue]),
        TrackOption(title: "Weight", icon: .weight, keys: [CareKind.weight.rawValue]),
        TrackOption(title: "Vet visits", icon: .vet, keys: [CareKind.vet.rawValue]),
    ]
}

private struct TrackStep: View {
    @Binding var draft: DogDraft
    let onBack: () -> Void
    let onNext: () -> Void

    private var tracksMeals: Bool { draft.trackedKinds.contains(CareKind.meal.rawValue) }

    var body: some View {
        VStack(spacing: 0) {
            StepHeader(index: 4, onBack: onBack)
            ScrollView {
                VStack(spacing: 0) {
                    StepTitle(title: "What should we track?", subtitle: "You can change this any time in Settings.")
                    TrackGrid(trackedKinds: $draft.trackedKinds)
                        .padding(.horizontal, 20)
                        .padding(.top, 24)
                    if tracksMeals {
                        MealTimesCard(mealTimes: $draft.mealTimes, weightKg: draft.weightKg, species: draft.species)
                            .padding(.horizontal, 20)
                            .padding(.top, 22)
                            .transition(.opacity.combined(with: .offset(y: 8)))
                    }
                }
                .padding(.bottom, 24)
            }
            .scrollIndicators(.hidden)
            BottomActions(title: "Continue", isEnabled: !draft.trackedKinds.isEmpty, action: onNext)
        }
        .animation(.spring(response: 0.4, dampingFraction: 0.85), value: tracksMeals)
    }
}

// MARK: - 4f Household

private struct HouseholdStep: View {
    let name: String
    let onBack: () -> Void
    let onNext: () -> Void

    var body: some View {
        VStack(spacing: 0) {
            StepHeader(index: 5, onBack: onBack)
            ScrollView {
                VStack(spacing: 0) {
                    StepTitle(title: "Who else looks after \(name)?", subtitle: "Everyone logs to the same pet, so meals never get doubled.")
                    HouseholdCards(petName: name)
                        .padding(.horizontal, 20)
                        .padding(.top, 22)
                }
                .padding(.bottom, 24)
            }
            .scrollIndicators(.hidden)
            BottomActions(title: "Continue", secondary: "Skip for now", action: onNext, secondaryAction: onNext)
        }
    }
}

// MARK: - 4g Permissions

private struct PermissionsStep: View {
    let name: String
    let image: UIImage?
    let onBack: () -> Void
    let onNext: () -> Void

    @Environment(\.openURL) private var openURL
    @State private var notificationsOn = false
    @State private var locationOn = false
    @State private var location = LocationPermission()

    var body: some View {
        VStack(spacing: 0) {
            StepHeader(index: 6, onBack: onBack)
            StepTitle(title: "Two quick permissions", subtitle: "So \(name) can ask for dinner and walks get tracked.")
            VStack(spacing: 0) {
                permission(.bell, "Notifications", "Meal and meds reminders, plus a nudge when \(name) feels left out", isOn: notificationsBinding, first: true)
                permission(.location, "Location while walking", "Maps your walk route. Never shared without asking.", isOn: locationBinding)
            }
            .padding(.horizontal, 20)
            .whiteCard()
            .padding(.horizontal, 20)
            .padding(.top, 22)

            Spacer(minLength: 0)

            ZStack(alignment: .topLeading) {
                StickerPet(image: image, edge: 5, motion: .breathe, duration: 2.6)
                    .frame(width: 150, height: 170)
                    .padding(.leading, 26)
                    .padding(.top, 30)
                SpeechBubble(text: "don’t forget my dinner")
                    .padding(.leading, 168)
                    .padding(.top, 20)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .frame(height: 200, alignment: .top)
            .padding(.bottom, 16)

            BottomActions(title: "Meet \(name) at home", action: onNext)
        }
        .task { await refresh() }
        .onChange(of: location.status) { _, _ in
            locationOn = location.isAllowed
        }
        .onReceive(NotificationCenter.default.publisher(for: UIApplication.didBecomeActiveNotification)) { _ in
            Task { await refresh() }
        }
    }

    private var notificationsBinding: Binding<Bool> {
        Binding(get: { notificationsOn }, set: { on in
            if on {
                Task {
                    let settings = await UNUserNotificationCenter.current().notificationSettings()
                    if settings.authorizationStatus == .denied {
                        openSettings()
                    } else {
                        notificationsOn = await NotificationService.requestAuthorization()
                    }
                }
            } else {
                openSettings()
            }
        })
    }

    private var locationBinding: Binding<Bool> {
        Binding(get: { locationOn }, set: { on in
            if on && location.status == .notDetermined {
                location.request()
            } else {
                openSettings()
            }
        })
    }

    private func refresh() async {
        let settings = await UNUserNotificationCenter.current().notificationSettings()
        notificationsOn = settings.authorizationStatus == .authorized || settings.authorizationStatus == .provisional
        locationOn = location.isAllowed
    }

    private func openSettings() {
        if let url = URL(string: UIApplication.openSettingsURLString) { openURL(url) }
    }

    private func permission(_ icon: DesignIcon, _ title: String, _ detail: String, isOn: Binding<Bool>, first: Bool = false) -> some View {
        HStack(spacing: 14) {
            RowIcon(icon: icon, size: 44, iconSize: 20)
            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                    .font(.opaw(16, .heavy))
                    .foregroundStyle(Color.opawInk)
                Text(detail)
                    .font(.opaw(13, .medium))
                    .foregroundStyle(Color.opawMuted)
                    .lineSpacing(2)
                    .fixedSize(horizontal: false, vertical: true)
            }
            Spacer(minLength: 8)
            DesignToggle(isOn: isOn)
        }
        .padding(.vertical, 16)
        .overlay(alignment: .top) {
            if !first { Rectangle().fill(Color.opawHairline).frame(height: 1) }
        }
    }
}

/// Asks for location while walking and reports the answer.
@MainActor
@Observable
final class LocationPermission: NSObject, CLLocationManagerDelegate {
    private(set) var status: CLAuthorizationStatus
    @ObservationIgnored private let manager: CLLocationManager

    override init() {
        let manager = CLLocationManager()
        self.manager = manager
        self.status = manager.authorizationStatus
        super.init()
        manager.delegate = self
    }

    var isAllowed: Bool { status == .authorizedWhenInUse || status == .authorizedAlways }

    func request() { manager.requestWhenInUseAuthorization() }

    nonisolated func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        let status = manager.authorizationStatus
        Task { @MainActor in self.status = status }
    }
}
