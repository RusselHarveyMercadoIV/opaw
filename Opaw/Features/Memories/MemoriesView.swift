import PhotosUI
import SwiftData
import SwiftUI

/// A photo journal per dog, with "on this day" throwbacks.
struct MemoriesView: View {
    @Query(sort: \Dog.createdAt) private var dogs: [Dog]
    @AppStorage(OpawDefaults.activeDogKey, store: OpawDefaults.shared) private var activeDogID = ""
    @State private var showAdd = false

    private var dog: Dog? {
        dogs.first { $0.id.uuidString == activeDogID } ?? dogs.first
    }

    private var memories: [Memory] {
        (dog?.memories ?? []).sorted { $0.date > $1.date }
    }

    /// Memories from this calendar day in earlier years.
    private var onThisDay: [Memory] {
        let calendar = Calendar.current
        let today = calendar.dateComponents([.month, .day, .year], from: Date())
        return memories.filter {
            let parts = calendar.dateComponents([.month, .day, .year], from: $0.date)
            return parts.month == today.month && parts.day == today.day && parts.year != today.year
        }
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 20) {
                    if let dog {
                        milestoneBanner(dog: dog)
                    }
                    if !onThisDay.isEmpty {
                        VStack(alignment: .leading, spacing: 8) {
                            Label("On this day", systemImage: "clock.arrow.circlepath")
                                .font(.headline)
                            ScrollView(.horizontal, showsIndicators: false) {
                                HStack {
                                    ForEach(onThisDay) { memory in
                                        NavigationLink {
                                            MemoryDetailView(memory: memory)
                                        } label: {
                                            MemoryTile(memory: memory)
                                                .frame(width: 160, height: 160)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if memories.isEmpty {
                        ContentUnavailableView(
                            "No memories yet",
                            systemImage: "photo.on.rectangle.angled",
                            description: Text("Save favorite moments with \(dog?.name ?? "your dog").")
                        )
                    } else {
                        LazyVGrid(columns: [GridItem(.adaptive(minimum: 150), spacing: 8)], spacing: 8) {
                            ForEach(memories) { memory in
                                NavigationLink {
                                    MemoryDetailView(memory: memory)
                                } label: {
                                    MemoryTile(memory: memory)
                                        .aspectRatio(1, contentMode: .fit)
                                }
                            }
                        }
                    }
                }
                .padding()
            }
            .opawBackground()
            .navigationTitle("Memories")
            .toolbar {
                Button {
                    showAdd = true
                } label: {
                    Label("Add memory", systemImage: "plus")
                }
                .disabled(dog == nil)
            }
            .sheet(isPresented: $showAdd) {
                if let dog {
                    AddMemorySheet(dog: dog)
                }
            }
        }
    }

    /// Birthday and "gotcha day" style milestones.
    @ViewBuilder
    private func milestoneBanner(dog: Dog) -> some View {
        let calendar = Calendar.current
        if let birth = dog.birthDate,
           calendar.component(.month, from: birth) == calendar.component(.month, from: Date()),
           calendar.component(.day, from: birth) == calendar.component(.day, from: Date()) {
            Label("Happy birthday, \(dog.name)! 🎂", systemImage: "gift.fill")
                .font(.headline)
                .card()
        } else {
            let days = calendar.dateComponents([.day], from: dog.createdAt, to: Date()).day ?? 0
            if days > 0, days % 100 == 0 {
                Label("\(days) days with \(dog.name) on Opaw!", systemImage: "sparkles")
                    .font(.headline)
                    .card()
            }
        }
    }
}

struct MemoryTile: View {
    let memory: Memory

    var body: some View {
        ZStack(alignment: .bottomLeading) {
            Color.opawOrange.opacity(0.15)
            if let data = memory.photoData, let image = UIImage(data: data) {
                Image(uiImage: image)
                    .resizable()
                    .scaledToFill()
            } else {
                Image(systemName: "pawprint.fill")
                    .font(.largeTitle)
                    .foregroundStyle(Color.opawOrange)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            }
            Text(memory.date.formatted(.dateTime.month(.abbreviated).day().year()))
                .font(.caption2.weight(.semibold))
                .padding(6)
                .background(.ultraThinMaterial, in: Capsule())
                .padding(6)
        }
        .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
    }
}

struct MemoryDetailView: View {
    @Bindable var memory: Memory
    @Environment(\.modelContext) private var modelContext
    @Environment(\.dismiss) private var dismiss
    @Environment(PackService.self) private var pack
    @State private var shareStatus: String?

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                if let data = memory.photoData, let image = UIImage(data: data) {
                    Image(uiImage: image)
                        .resizable()
                        .scaledToFit()
                        .clipShape(RoundedRectangle(cornerRadius: 20))
                }
                Text(memory.date.formatted(date: .long, time: .shortened))
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                TextField("Caption", text: $memory.caption, axis: .vertical)
                    .font(.title3)
                if let shareStatus {
                    Text(shareStatus).font(.footnote).foregroundStyle(.secondary)
                }
            }
            .padding()
            .frame(maxWidth: 700)
            .frame(maxWidth: .infinity)
        }
        .navigationTitle("Memory")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            Menu {
                if let data = memory.photoData, let image = UIImage(data: data) {
                    ShareLink(item: Image(uiImage: image), preview: SharePreview(memory.caption.isEmpty ? "Memory" : memory.caption, image: Image(uiImage: image)))
                }
                if pack.isSignedIn, let dog = memory.dog {
                    Button {
                        Task {
                            do {
                                try await pack.shareMemory(memory, dog: dog)
                                shareStatus = "Shared with the pack."
                            } catch {
                                shareStatus = error.localizedDescription
                            }
                        }
                    } label: {
                        Label("Share with the pack", systemImage: "person.3")
                    }
                }
                Button(role: .destructive) {
                    modelContext.delete(memory)
                    try? modelContext.save()
                    dismiss()
                } label: {
                    Label("Delete", systemImage: "trash")
                }
            } label: {
                Label("More", systemImage: "ellipsis.circle")
            }
        }
        .onDisappear { try? modelContext.save() }
    }
}

struct AddMemorySheet: View {
    let dog: Dog

    @Environment(\.modelContext) private var modelContext
    @Environment(\.dismiss) private var dismiss
    @State private var pickerItem: PhotosPickerItem?
    @State private var showCamera = false
    @State private var image: UIImage?
    @State private var caption = ""
    @State private var date = Date()

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    if let image {
                        Image(uiImage: image)
                            .resizable()
                            .scaledToFit()
                            .frame(maxHeight: 280)
                            .frame(maxWidth: .infinity)
                    }
                    PhotosPicker(selection: $pickerItem, matching: .images) {
                        Label(image == nil ? "Choose a photo" : "Choose another", systemImage: "photo")
                    }
                    if UIImagePickerController.isSourceTypeAvailable(.camera) {
                        Button {
                            showCamera = true
                        } label: {
                            Label("Take a photo", systemImage: "camera")
                        }
                    }
                }
                Section {
                    TextField("What happened?", text: $caption, axis: .vertical)
                    DatePicker("Date", selection: $date, in: ...Date())
                }
            }
            .navigationTitle("New memory")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save", action: save)
                        .disabled(image == nil && caption.isEmpty)
                }
            }
            .onChange(of: pickerItem) { _, item in
                guard let item else { return }
                Task {
                    if let data = try? await item.loadTransferable(type: Data.self), let picked = UIImage(data: data) {
                        image = picked.preparedForProcessing(maxDimension: 2400)
                    }
                }
            }
            .sheet(isPresented: $showCamera) {
                CameraPicker { picked in
                    image = picked.preparedForProcessing(maxDimension: 2400)
                }
                .ignoresSafeArea()
            }
        }
    }

    private func save() {
        let memory = Memory(date: date, caption: caption, photoData: image?.jpegData(compressionQuality: 0.85), dog: dog)
        modelContext.insert(memory)
        BadgeService.refresh(dog)
        try? modelContext.save()
        Haptics.success()
        dismiss()
    }
}
