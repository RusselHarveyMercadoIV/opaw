import OpawKit
import PhotosUI
import SwiftData
import SwiftUI

/// Takes or picks a photo, lifts the dog off the background and guesses the breed, all on device.
///
/// In onboarding it hands the result back with `onComplete`. When adding another dog from Profile
/// (no `onComplete`), it saves the dog itself and calls `onSaved`.
struct AvatarCaptureStep: View {
    let draft: DogDraft
    var eyebrow: String?
    var initial = CaptureResult()
    var onComplete: ((CaptureResult) -> Void)?
    var onSaved: () -> Void = {}

    @Environment(\.modelContext) private var modelContext
    @State private var pickerItem: PhotosPickerItem?
    @State private var showCamera = false
    @State private var result = CaptureResult()
    @State private var isProcessing = false
    @State private var message: String?
    @State private var didLoadInitial = false

    private var hasCamera: Bool { UIImagePickerController.isSourceTypeAvailable(.camera) }

    var body: some View {
        VStack(spacing: 0) {
            ScrollView {
                VStack(alignment: .leading, spacing: Space.l) {
                    VStack(alignment: .leading, spacing: 12) {
                        if let eyebrow { Eyebrow(text: eyebrow) }
                        Text(draft.isValid ? "Now, a portrait\nof \(draft.trimmedName)." : "First, a portrait\nof your dog.")
                            .font(.opawDisplay(36))
                            .foregroundStyle(Color.opawInk)
                            .fixedSize(horizontal: false, vertical: true)
                        Text("Whole body, good light. We'll lift them out of the photo and spot the breed, right here on your iPhone.")
                            .font(.body)
                            .foregroundStyle(.secondary)
                            .fixedSize(horizontal: false, vertical: true)
                    }

                    viewfinder
                        .frame(height: 360)

                    if let message {
                        Label(message, systemImage: "info.circle")
                            .font(.callout)
                            .foregroundStyle(.secondary)
                            .transition(.opacity)
                    }

                    if let guess = result.guess, !isProcessing {
                        HStack(spacing: Space.s) {
                            Image(systemName: "sparkles")
                                .foregroundStyle(Color.opawOrange)
                            Text("Looks like a ")
                                .foregroundColor(.secondary)
                            + Text(guess.breed.name).bold()
                        }
                        .font(.callout)
                        .transition(.opacity.combined(with: .offset(y: 6)))
                    }

                    HStack(spacing: 12) {
                        if hasCamera {
                            Button {
                                showCamera = true
                            } label: {
                                Label("Camera", systemImage: "camera")
                            }
                            .buttonStyle(.opawSecondary)
                        }
                        PhotosPicker(selection: $pickerItem, matching: .images) {
                            Label(result.original == nil ? "Choose photo" : "Change", systemImage: "photo")
                        }
                        .buttonStyle(.opawSecondary)
                    }
                    .disabled(isProcessing)
                }
                .padding(.horizontal, Space.l)
                .padding(.top, Space.l)
                .padding(.bottom, Space.xl)
                .animation(.spring(response: 0.5, dampingFraction: 0.85), value: result)
                .animation(.easeInOut, value: message)
            }
            .scrollBounceBehavior(.basedOnSize)

            VStack(spacing: 4) {
                Button(primaryTitle, action: finish)
                    .buttonStyle(.opawPrimary)
                    .disabled(isProcessing || result.original == nil)
                Button("Skip for now", action: finish)
                    .font(.subheadline.weight(.medium))
                    .foregroundStyle(.secondary)
                    .frame(minHeight: 44)
                    .opacity(result.original == nil ? 1 : 0)
                    .disabled(result.original != nil)
            }
            .padding(.horizontal, Space.l)
            .padding(.bottom, Space.s)
        }
        .opawBackground()
        .onAppear {
            guard !didLoadInitial else { return }
            didLoadInitial = true
            result = initial
        }
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

    private var primaryTitle: String {
        if onComplete != nil { return "Continue" }
        return "Meet \(draft.trimmedName)"
    }

    private var viewfinder: some View {
        ZStack {
            RoundedRectangle(cornerRadius: 32, style: .continuous)
                .fill(Color.opawSurface)

            if let cutout = result.cutout, !isProcessing {
                LivingPhotoView(image: cutout, mood: .happy)
                    .padding(28)
                    .transition(.scale(scale: 0.85).combined(with: .opacity))
            } else if let original = result.original {
                Color.clear
                    .overlay {
                        Image(uiImage: original)
                            .resizable()
                            .scaledToFill()
                    }
                    .clipped()
                    .overlay {
                        if isProcessing {
                            ScanSweep()
                        }
                    }
                    .transition(.opacity)
            } else {
                VStack(spacing: 12) {
                    Image(systemName: "dog.fill")
                        .font(.system(size: 56))
                        .foregroundStyle(Color.opawInk.opacity(0.1))
                    Text("Your photo goes here")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }
            }

            CornerBrackets()
                .stroke(Color.opawOrange, style: StrokeStyle(lineWidth: 3, lineCap: .round, lineJoin: .round))
                .padding(16)
                .opacity(result.cutout == nil || isProcessing ? 1 : 0)
                .phaseAnimator([false, true]) { content, breathe in
                    content.scaleEffect(result.original == nil && breathe ? 0.97 : 1)
                } animation: { _ in
                    .easeInOut(duration: 1.6)
                }

            if isProcessing {
                VStack {
                    Spacer()
                    HStack(spacing: Space.s) {
                        ProgressView()
                            .controlSize(.small)
                        Text(draft.isValid ? "Finding \(draft.trimmedName)…" : "Finding your dog…")
                            .font(.footnote.weight(.semibold))
                    }
                    .padding(.horizontal, Space.m)
                    .padding(.vertical, 10)
                    .background(.regularMaterial, in: Capsule())
                    .padding(.bottom, Space.l)
                }
                .transition(.opacity)
            }
        }
        .clipShape(RoundedRectangle(cornerRadius: 32, style: .continuous))
        .animation(.spring(response: 0.6, dampingFraction: 0.75), value: isProcessing)
    }

    private func process(_ image: UIImage) async {
        let prepared = image.preparedForProcessing()
        message = nil
        withAnimation {
            result = CaptureResult(original: prepared)
            isProcessing = true
        }
        let started = Date()
        async let hasDog = CutoutService.containsDog(prepared)
        async let guess = CutoutService.guessBreed(prepared)
        var cutout: UIImage?
        var note: String?
        do {
            cutout = try await CutoutService.cutout(prepared)
        } catch {
            note = "\(error.localizedDescription) We'll use the full photo for now."
        }
        let foundDog = await hasDog
        let breedGuess = await guess
        // Let the scan read as a moment rather than a flicker.
        let remaining = 1.2 - Date().timeIntervalSince(started)
        if remaining > 0 {
            try? await Task.sleep(for: .seconds(remaining))
        }
        if note == nil && !foundDog {
            note = "We're not sure that's a dog, but we'll go with it."
        }
        withAnimation(.spring(response: 0.6, dampingFraction: 0.7)) {
            result = CaptureResult(original: prepared, cutout: cutout, guess: breedGuess)
            isProcessing = false
            message = note
        }
        Haptics.success()
    }

    private func finish() {
        if let onComplete {
            onComplete(result)
        } else {
            draft.save(photo: result.original, cutout: result.cutout, in: modelContext)
            onSaved()
        }
    }
}
