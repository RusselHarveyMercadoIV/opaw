import OpawKit
import SwiftUI

/// Shows the best avatar available for a dog: generated motion clips, else the living photo.
struct AvatarView: View {
    let dog: Dog
    let mood: AvatarMood

    var body: some View {
        Group {
            if dog.avatarStatus == .ready,
               let url = AvatarClipStore.playableURL(dogID: dog.id, preferred: mood.clipName) {
                StackedAlphaVideoView(url: url)
                    .id(url)
                    .transition(.opacity)
            } else if let data = dog.cutoutData ?? dog.photoData, let image = UIImage(data: data) {
                LivingPhotoView(image: image, mood: mood)
            } else {
                Image(systemName: "dog.fill")
                    .resizable()
                    .scaledToFit()
                    .padding(40)
                    .foregroundStyle(Color.opawOrange.opacity(0.7))
            }
        }
        .animation(.easeInOut, value: mood)
    }
}

/// Lets the owner generate realistic motion clips of their dog, or retake the photo.
struct AvatarStudioView: View {
    @Bindable var dog: Dog
    @Environment(PackService.self) private var pack
    @State private var isWorking = false
    @State private var errorMessage: String?
    @State private var previewMood: AvatarMood = .happy

    var body: some View {
        List {
            Section {
                AvatarView(dog: dog, mood: previewMood)
                    .frame(height: 280)
                    .frame(maxWidth: .infinity)
                    .listRowBackground(Color.opawOrange.opacity(0.1))
                Picker("Mood", selection: $previewMood) {
                    ForEach(AvatarMood.allCases, id: \.self) { mood in
                        Text(mood.rawValue.capitalized).tag(mood)
                    }
                }
            }

            Section {
                statusRow
                if let errorMessage {
                    Text(errorMessage)
                        .font(.footnote)
                        .foregroundStyle(.red)
                }
            } header: {
                Text("Realistic motion")
            } footer: {
                Text("We send \(dog.name)'s cutout to the Opaw avatar worker, which uses the open-source Wan 2.2 model to make short clips of \(dog.name) wagging, sleeping, celebrating and more. It takes a few minutes and runs once.")
            }
        }
        .navigationTitle("Avatar")
        .task {
            await pollWhileGenerating()
        }
    }

    @ViewBuilder
    private var statusRow: some View {
        switch dog.avatarStatus {
        case .livingPhoto, .failed:
            Button {
                Task { await requestClips() }
            } label: {
                Label(dog.avatarStatus == .failed ? "Try again" : "Generate motion clips", systemImage: "sparkles")
            }
            .disabled(isWorking || !pack.isConfigured || dog.cutoutData == nil && dog.photoData == nil)
            if !pack.isConfigured {
                Text("Needs the Opaw server. See docs/SETUP.md.")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
            }
        case .generating:
            HStack {
                ProgressView()
                Text("Generating \(dog.name)'s clips…")
            }
        case .ready:
            Label("Motion clips ready", systemImage: "checkmark.seal.fill")
                .foregroundStyle(.green)
            Button("Regenerate", role: .destructive) {
                AvatarClipStore.deleteAll(dogID: dog.id)
                Task { await requestClips() }
            }
            .disabled(isWorking)
        }
    }

    private func requestClips() async {
        isWorking = true
        errorMessage = nil
        defer { isWorking = false }
        do {
            let jobID = try await pack.requestAvatarClips(for: dog)
            dog.avatarJobID = jobID.uuidString
            dog.avatarStatus = .generating
            await pollWhileGenerating()
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    private func pollWhileGenerating() async {
        while dog.avatarStatus == .generating && !Task.isCancelled {
            do {
                if try await pack.syncAvatarClips(for: dog) { return }
            } catch {
                errorMessage = error.localizedDescription
            }
            try? await Task.sleep(for: .seconds(15))
        }
    }
}
