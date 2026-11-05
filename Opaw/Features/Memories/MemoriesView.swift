import OpawKit
import PhotosUI
import SwiftData
import SwiftUI

/// Memories (2d): walks, photos and milestones as one feed.
struct MemoriesView: View {
    @Query(sort: \Dog.createdAt) private var dogs: [Dog]
    @AppStorage(OpawDefaults.activeDogKey, store: OpawDefaults.shared) private var activeDogID = ""
    @State private var showAdd = false
    @State private var filter: Filter = .all
    @State private var path = NavigationPath()
    @Environment(\.duoOpen) private var duoOpen
    @Environment(TabReselect.self) private var reselect: TabReselect?

    enum Filter: String, CaseIterable {
        case all = "All"
        case walks = "Walks"
        case milestones = "Milestones"
    }

    private var dog: Dog? {
        dogs.first { $0.id.uuidString == activeDogID } ?? dogs.first
    }

    var body: some View {
        NavigationStack(path: $path) {
            Group {
                if duoOpen, let dog {
                    MemoriesDuo(dog: dog, filter: $filter, showAdd: $showAdd)
                } else {
                    feed
                }
            }
            .opawBackground()
            .toolbar(.hidden, for: .navigationBar)
            .navigationDestination(for: MemoryRoute.self) { route in
                switch route {
                case .walk(let walk): WalkDetailView(walk: walk)
                case .photo(let memory): MemoryDetailView(memory: memory)
                }
            }
            .sheet(isPresented: $showAdd) {
                if let dog {
                    AddMemorySheet(dog: dog)
                }
            }
        }
        .onChange(of: reselect?.taps[.memories]) { path = NavigationPath() }
    }

    private var feed: some View {
            ScrollView {
                VStack(alignment: .leading, spacing: 0) {
                    HStack {
                        Text("Memories")
                            .font(.opaw(36, .black))
                            .foregroundStyle(Color.opawInk)
                        Spacer()
                        Button { showAdd = true } label: {
                            Icon(.camera, size: 22)
                                .foregroundStyle(.white)
                                .frame(width: 54, height: 54)
                                .background(Color(hex: 0x111111), in: Circle())
                        }
                        .buttonStyle(.pressable)
                        .disabled(dog == nil)
                        .accessibilityLabel("Add memory")
                    }
                    .padding(.horizontal, 24)
                    .padding(.top, 12)

                    HStack(spacing: 8) {
                        ForEach(Filter.allCases, id: \.self) { option in
                            FilterPill(title: option.rawValue, isSelected: option == filter) {
                                withAnimation(.snappy) { filter = option }
                                Haptics.select()
                            }
                        }
                    }
                    .padding(.horizontal, 24)
                    .padding(.top, 14)

                    if let dog {
                        let items = MemoryFeed.items(for: dog, filter: filter)
                        if items.isEmpty {
                            Text(emptyText(dog))
                                .font(.opaw(15, .semibold))
                                .foregroundStyle(Color.opawMuted)
                                .padding(.horizontal, 24)
                                .padding(.top, 28)
                        }
                        LazyVStack(spacing: 14) {
                            ForEach(items) { item in
                                row(item, dog: dog)
                            }
                        }
                        .padding(.horizontal, 20)
                        .padding(.top, 18)
                    }
                }
                .padding(.bottom, 120)
            }
            .scrollIndicators(.hidden)
    }

    private func emptyText(_ dog: Dog) -> String {
        switch filter {
        case .all: return "Walks, photos and milestones with \(dog.name) will show up here."
        case .walks: return "Finish a walk and it shows up here."
        case .milestones: return "Milestones arrive as you care for \(dog.name)."
        }
    }

    @ViewBuilder
    private func row(_ item: MemoryFeed.Item, dog: Dog) -> some View {
        switch item.content {
        case .walk(let walk, let photo):
            NavigationLink {
                WalkDetailView(walk: walk)
            } label: {
                BigMemoryCard(
                    title: walk.title,
                    subtitle: "\(MemoryFeed.dayText(walk.startedAt)) · \(OpawFormat.distance(meters: walk.distanceMeters)) · \(Int((walk.elapsedSeconds / 60).rounded())) min",
                    isFavorite: walk.isFavorite,
                    toggleFavorite: { walk.isFavorite.toggle() }
                ) {
                    if let data = photo?.photoData, let image = UIImage(data: data) {
                        Image(uiImage: image).resizable().scaledToFill()
                    } else {
                        RouteMap(points: walk.route, pins: walk.pins)
                    }
                }
            }
            .buttonStyle(.pressable)
        case .photo(let memory):
            NavigationLink {
                MemoryDetailView(memory: memory)
            } label: {
                BigMemoryCard(
                    title: memory.caption,
                    subtitle: MemoryFeed.dayText(memory.date),
                    isFavorite: memory.isFavorite,
                    toggleFavorite: { memory.isFavorite.toggle() }
                ) {
                    MemoryPhoto(memory: memory)
                }
            }
            .buttonStyle(.pressable)
        case .pair(let first, let second):
            HStack(spacing: 10) {
                pairTile(first)
                if let second { pairTile(second) } else { Color.clear }
            }
            .frame(height: 150)
        case .milestone(let text):
            HStack(spacing: 14) {
                MiniFace(data: dog.cutoutData ?? dog.photoData, size: 64, rim: 0, fill: .white)
                VStack(alignment: .leading, spacing: 2) {
                    Text("\(item.date.formatted(.dateTime.day().month(.abbreviated))) · Milestone")
                        .font(.opaw(13, .bold))
                        .foregroundStyle(Color(hex: 0xAAAAAA))
                    Text(text)
                        .font(.opaw(16, .heavy))
                        .foregroundStyle(.white)
                        .lineSpacing(3)
                        .fixedSize(horizontal: false, vertical: true)
                }
                Spacer(minLength: 0)
            }
            .padding(.leading, 14)
            .padding(.trailing, 18)
            .padding(.vertical, 14)
            .background(Color(hex: 0x111111), in: RoundedRectangle(cornerRadius: 30, style: .continuous))
        }
    }

    private func pairTile(_ memory: Memory) -> some View {
        NavigationLink {
            MemoryDetailView(memory: memory)
        } label: {
            MemoryPhoto(memory: memory)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .clipShape(RoundedRectangle(cornerRadius: 26, style: .continuous))
        }
        .buttonStyle(.pressable)
    }
}

enum MemoryRoute: Hashable {
    case walk(Walk)
    case photo(Memory)
}

// MARK: - Open display (6h)

/// A two-column grid on the left and the chosen memory, large, on the right.
private struct MemoriesDuo: View {
    let dog: Dog
    @Binding var filter: MemoriesView.Filter
    @Binding var showAdd: Bool
    @State private var selectedID: String?

    private enum Entry: Identifiable {
        case walk(Walk, photo: Memory?)
        case photo(Memory)
        case milestone(Date, String)

        var id: String {
            switch self {
            case .walk(let walk, _): return "walk-\(walk.id)"
            case .photo(let memory): return "photo-\(memory.id)"
            case .milestone(let date, let text): return "milestone-\(date.timeIntervalSince1970)-\(text.hashValue)"
            }
        }
    }

    private var entries: [Entry] {
        MemoryFeed.items(for: dog, filter: filter).flatMap { item -> [Entry] in
            switch item.content {
            case .walk(let walk, let photo): return [.walk(walk, photo: photo)]
            case .photo(let memory): return [.photo(memory)]
            case .pair(let first, let second): return [.photo(first)] + (second.map { [.photo($0)] } ?? [])
            case .milestone(let text): return [.milestone(item.date, text)]
            }
        }
    }

    var body: some View {
        let list = entries
        let selected = list.first { $0.id == selectedID } ?? list.first
        DuoPanes {
            VStack(alignment: .leading, spacing: 0) {
                PaneHeader(title: "Memories") {
                    Button { showAdd = true } label: {
                        Icon(.camera, size: 22)
                            .foregroundStyle(.white)
                            .frame(width: 54, height: 54)
                            .background(Color(hex: 0x111111), in: Circle())
                    }
                    .buttonStyle(.pressable)
                    .accessibilityLabel("Add memory")
                }
                HStack(spacing: 6) {
                    ForEach(MemoriesView.Filter.allCases, id: \.self) { option in
                        FilterPill(title: option.rawValue, isSelected: option == filter) {
                            withAnimation(.snappy) { filter = option }
                            Haptics.select()
                        }
                    }
                }
                .padding(.top, 8)
                if list.isEmpty {
                    Text("Walks, photos and milestones with \(dog.name) will show up here.")
                        .font(.opaw(15, .semibold))
                        .foregroundStyle(Color.opawMuted)
                        .padding(.top, 28)
                    Spacer()
                } else {
                    grid(list, selected: selected)
                        .padding(.top, 14)
                }
            }
        } right: {
            if let selected {
                detail(selected, milestone: latestMilestone(before: date(of: selected)))
            }
        }
    }

    private func grid(_ list: [Entry], selected: Entry?) -> some View {
        GeometryReader { proxy in
            let height = max(120, (proxy.size.height - 16) / 3)
            ScrollView {
                LazyVGrid(columns: [GridItem(.flexible(), spacing: 8), GridItem(.flexible(), spacing: 8)], spacing: 8) {
                    ForEach(list) { entry in
                        Button {
                            Haptics.select()
                            withAnimation(.snappy) { selectedID = entry.id }
                        } label: {
                            tile(entry)
                                .frame(height: height)
                                .clipShape(RoundedRectangle(cornerRadius: 22, style: .continuous))
                                .padding(3)
                                .overlay {
                                    if entry.id == selected?.id {
                                        RoundedRectangle(cornerRadius: 25, style: .continuous)
                                            .strokeBorder(Color.opawInk, lineWidth: 2.5)
                                            .padding(-2.5)
                                    }
                                }
                                .padding(-3)
                        }
                        .buttonStyle(.pressable)
                    }
                }
                .padding(.vertical, 6)
            }
            .scrollIndicators(.hidden)
            .padding(.vertical, -6)
        }
    }

    @ViewBuilder
    private func tile(_ entry: Entry) -> some View {
        switch entry {
        case .walk(let walk, let photo):
            ZStack {
                Color.opawTrack
                if let data = photo?.photoData, let image = UIImage(data: data) {
                    Image(uiImage: image).resizable().scaledToFill()
                } else {
                    RouteMap(points: walk.route, pins: walk.pins)
                }
            }
        case .photo(let memory):
            MemoryPhoto(memory: memory)
        case .milestone(_, let text):
            VStack(alignment: .leading, spacing: 8) {
                MiniFace(data: dog.cutoutData ?? dog.photoData, size: 40, rim: 0, fill: .white)
                Spacer(minLength: 0)
                Text(text)
                    .font(.opaw(14, .heavy))
                    .foregroundStyle(.white)
                    .lineLimit(4)
                    .multilineTextAlignment(.leading)
            }
            .padding(14)
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
            .background(Color(hex: 0x111111))
        }
    }

    private func isMilestone(_ entry: Entry) -> Bool {
        if case .milestone = entry { return true }
        return false
    }

    private func date(of entry: Entry) -> Date {
        switch entry {
        case .walk(let walk, _): return walk.startedAt
        case .photo(let memory): return memory.date
        case .milestone(let date, _): return date
        }
    }

    private func latestMilestone(before date: Date) -> (date: Date, text: String)? {
        let all = MemoryFeed.milestones(for: dog).sorted { $0.date > $1.date }
        return all.first { $0.date <= date.addingTimeInterval(86_400) } ?? all.last
    }

    @ViewBuilder
    private func detail(_ entry: Entry, milestone: (date: Date, text: String)?) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            switch entry {
            case .walk(let walk, _):
                NavigationLink(value: MemoryRoute.walk(walk)) {
                    tile(entry).frame(maxWidth: .infinity, maxHeight: .infinity).clipShape(RoundedRectangle(cornerRadius: 30, style: .continuous))
                }
                .buttonStyle(.pressable)
                caption(
                    title: walk.title,
                    subtitle: "\(MemoryFeed.dayText(walk.startedAt)) · \(OpawFormat.distance(meters: walk.distanceMeters)) · \(Int((walk.elapsedSeconds / 60).rounded())) min",
                    share: "\(walk.title): \(OpawFormat.distance(meters: walk.distanceMeters)) with \(dog.name). Logged with Opaw."
                )
            case .photo(let memory):
                NavigationLink(value: MemoryRoute.photo(memory)) {
                    tile(entry).frame(maxWidth: .infinity, maxHeight: .infinity).clipShape(RoundedRectangle(cornerRadius: 30, style: .continuous))
                }
                .buttonStyle(.pressable)
                caption(
                    title: memory.caption.isEmpty ? "A moment with \(dog.name)" : memory.caption,
                    subtitle: MemoryFeed.dayText(memory.date),
                    share: memory.caption.isEmpty ? "A moment with \(dog.name), from Opaw." : memory.caption
                )
            case .milestone(let date, let text):
                StickerPet(image: (dog.cutoutData ?? dog.photoData).flatMap(UIImage.init(data:)), edge: 6)
                    .padding(40)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .background(Color.opawTrack, in: RoundedRectangle(cornerRadius: 30, style: .continuous))
                caption(title: "Milestone", subtitle: "\(MemoryFeed.dayText(date)) · \(dog.name)", share: text)
            }
            if let milestone, !isMilestone(entry) {
                VStack(alignment: .leading, spacing: 2) {
                    Text("\(milestone.date.formatted(.dateTime.day().month(.abbreviated))) · Milestone")
                        .font(.opaw(13, .bold))
                        .foregroundStyle(Color.opawMuted)
                    Text(milestone.text)
                        .font(.opaw(15, .bold))
                        .foregroundStyle(Color.opawInk)
                        .fixedSize(horizontal: false, vertical: true)
                }
                .padding(.horizontal, 20)
                .padding(.vertical, 14)
                .frame(maxWidth: .infinity, alignment: .leading)
                .whiteCard()
                .padding(.top, 12)
            }
        }
        .padding(.top, 10)
    }

    private func caption(title: String, subtitle: String, share: String) -> some View {
        HStack(alignment: .bottom) {
            VStack(alignment: .leading, spacing: 0) {
                Text(title)
                    .font(.opaw(26, .black))
                    .foregroundStyle(Color.opawInk)
                    .lineLimit(1)
                    .minimumScaleFactor(0.7)
                Text(subtitle)
                    .font(.opaw(15, .medium))
                    .foregroundStyle(Color.opawMuted)
                    .lineLimit(1)
            }
            Spacer(minLength: 8)
            ShareLink(item: share) { RoundIcon(symbol: "point.3.filled.connected.trianglepath.dotted") }
                .buttonStyle(.pressable)
                .accessibilityLabel("Share")
        }
        .padding(.top, 14)
    }
}

/// The feed behind Memories: walks, photos (single cards or pairs) and milestones, newest first.
enum MemoryFeed {
    struct Item: Identifiable {
        enum Content {
            case walk(Walk, photo: Memory?)
            case photo(Memory)
            case pair(Memory, Memory?)
            case milestone(String)
        }

        let id: String
        let date: Date
        let content: Content
    }

    static func dayText(_ date: Date) -> String {
        let calendar = Calendar.current
        if calendar.isDateInToday(date) { return "Today" }
        if calendar.isDateInYesterday(date) { return "Yesterday" }
        return date.formatted(.dateTime.day().month(.abbreviated))
    }

    @MainActor
    static func items(for dog: Dog, filter: MemoriesView.Filter) -> [Item] {
        let memories = dog.memories ?? []
        let walks = dog.walks ?? []
        var items: [Item] = []

        if filter != .milestones {
            for walk in walks {
                let photo = memories.first { $0.walkID == walk.id && $0.photoData != nil }
                items.append(Item(id: "walk-\(walk.id)", date: walk.startedAt, content: .walk(walk, photo: photo)))
            }
        }
        if filter == .all {
            let linked = Set(walks.map(\.id))
            let loose = memories.filter { $0.walkID == nil || !linked.contains($0.walkID!) }.sorted { $0.date > $1.date }
            var pending: [Memory] = []
            func flush() {
                guard let first = pending.first else { return }
                items.append(Item(id: "pair-\(first.id)", date: first.date, content: .pair(first, pending.count > 1 ? pending[1] : nil)))
                pending.removeAll()
            }
            for memory in loose {
                if memory.caption.trimmingCharacters(in: .whitespaces).isEmpty {
                    pending.append(memory)
                    if pending.count == 2 { flush() }
                } else {
                    flush()
                    items.append(Item(id: "photo-\(memory.id)", date: memory.date, content: .photo(memory)))
                }
            }
            flush()
        }
        if filter != .walks {
            items += milestones(for: dog).map { Item(id: "milestone-\($0.date.timeIntervalSince1970)-\($0.text.hashValue)", date: $0.date, content: .milestone($0.text)) }
        }
        return items.sorted { $0.date > $1.date }
    }

    /// Milestones worked out from the dog's history.
    static func milestones(for dog: Dog, now: Date = Date()) -> [(date: Date, text: String)] {
        let calendar = Calendar.current
        var result: [(Date, String)] = []
        let walks = (dog.walks ?? []).sorted { $0.startedAt < $1.startedAt }
        if let first = walks.first {
            result.append((first.startedAt, "First walk together. \(dog.name) is officially on the map."))
        }
        var total = 0.0
        var marks = [50.0, 100, 250, 500, 1000]
        for walk in walks {
            total += walk.distanceMeters / 1000
            while let mark = marks.first, total >= mark {
                result.append((walk.startedAt, "\(Int(mark)) km walked together."))
                marks.removeFirst()
            }
        }
        let days = calendar.dateComponents([.day], from: calendar.startOfDay(for: dog.createdAt), to: now).day ?? 0
        var month = 30
        while month <= days {
            if let date = calendar.date(byAdding: .day, value: month, to: dog.createdAt) {
                result.append((date, "\(month) days of great care. \(dog.name) thanks you."))
            }
            month += 30
        }
        if let birth = dog.birthDate {
            let years = calendar.dateComponents([.year], from: birth, to: now).year ?? 0
            for age in max(1, years - 2)...max(1, years) where age <= years {
                if let date = calendar.date(byAdding: .year, value: age, to: birth), date >= dog.createdAt.addingTimeInterval(-86_400) {
                    result.append((date, "\(dog.name) turned \(age). Happy birthday!"))
                }
            }
        }
        return result.map { (date: $0.0, text: $0.1) }
    }
}

/// White card with a big rounded photo, a title, a subtitle and a heart.
private struct BigMemoryCard<Photo: View>: View {
    let title: String
    let subtitle: String
    let isFavorite: Bool
    let toggleFavorite: () -> Void
    @ViewBuilder var photo: Photo

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            photo
                .frame(maxWidth: .infinity)
                .frame(height: 210)
                .clipShape(RoundedRectangle(cornerRadius: 22, style: .continuous))
            HStack {
                VStack(alignment: .leading, spacing: 1) {
                    Text(title)
                        .font(.opaw(18, .heavy))
                        .foregroundStyle(Color.opawInk)
                        .lineLimit(1)
                    Text(subtitle)
                        .font(.opaw(14, .medium))
                        .foregroundStyle(Color.opawMuted)
                        .lineLimit(1)
                }
                Spacer()
                Button {
                    toggleFavorite()
                    Haptics.tap()
                } label: {
                    Icon(.heart, size: 22)
                        .foregroundStyle(isFavorite ? Color.opawAlert : Color.opawGhost)
                        .frame(width: 44, height: 44)
                }
                .buttonStyle(.pressable)
                .accessibilityLabel(isFavorite ? "Unfavourite" : "Favourite")
            }
            .padding(.leading, 8)
            .padding(.bottom, 0)
        }
        .padding(10)
        .whiteCard(shadow: 0.06)
    }
}

/// A memory's photo filling its frame, or a soft placeholder.
struct MemoryPhoto: View {
    let memory: Memory

    var body: some View {
        ZStack {
            Color.opawChip
            if let data = memory.photoData, let image = UIImage(data: data) {
                Image(uiImage: image)
                    .resizable()
                    .scaledToFill()
            } else {
                Image(systemName: "pawprint.fill")
                    .font(.system(size: 30))
                    .foregroundStyle(Color.opawMuted)
            }
        }
        .clipped()
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
        .hidesFloatingTabBar()
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
