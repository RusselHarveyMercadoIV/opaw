import MapKit
import OpawKit
import SwiftData
import SwiftUI

/// The social tab: dogs nearby and a feed of walks, memories and badges.
struct PackView: View {
    @Environment(PackService.self) private var pack

    var body: some View {
        NavigationStack {
            Group {
                if !pack.isConfigured {
                    ContentUnavailableView {
                        Label("The Pack is almost ready", systemImage: "person.3")
                    } description: {
                        Text("Connect Opaw to its Supabase server to meet dogs nearby. See docs/SETUP.md in the project.")
                    }
                } else if !pack.isSignedIn || !pack.hasAcceptedRules {
                    JoinPackView()
                } else {
                    PackHomeView()
                }
            }
        }
    }
}

// MARK: - Joining

private struct JoinPackView: View {
    @Environment(PackService.self) private var pack
    @State private var displayName = ""
    @State private var accepted = false
    @State private var isJoining = false
    @State private var error: String?

    private let rules = [
        "Be kind. No bullying, hate or harassment.",
        "Only post your own photos, and keep them about pets.",
        "No spam, ads or selling animals.",
        "Never share someone else's location or personal details.",
        "Report anything that breaks these rules. We review reports within 24 hours and remove offenders."
    ]

    var body: some View {
        Form {
            Section {
                Text("Meet dogs nearby, share walks and cheer each other on. Your exact location is never shared: other people only see a rough area, and the start and end of shared walks are hidden.")
            }
            Section("Your name in the pack") {
                TextField("e.g. Max's human", text: $displayName)
            }
            Section("Community rules") {
                ForEach(rules, id: \.self) { rule in
                    Label(rule, systemImage: "checkmark.shield")
                        .font(.subheadline)
                }
                Toggle("I agree to the community rules", isOn: $accepted)
            }
            if let error {
                Text(error).foregroundStyle(.red)
            }
            Section {
                Button {
                    Task {
                        isJoining = true
                        defer { isJoining = false }
                        do {
                            try await pack.join(displayName: displayName.trimmingCharacters(in: .whitespaces))
                        } catch {
                            self.error = error.localizedDescription
                        }
                    }
                } label: {
                    if isJoining {
                        ProgressView()
                    } else {
                        Text("Join the pack")
                    }
                }
                .frame(maxWidth: .infinity)
                .disabled(!accepted || displayName.trimmingCharacters(in: .whitespaces).isEmpty || isJoining)
            }
        }
    }
}

// MARK: - Nearby (2e)

private struct PackHomeView: View {
    @Environment(PackService.self) private var pack
    @Environment(WalkRecorder.self) private var location
    @Environment(\.modelContext) private var modelContext
    @Query(sort: \Dog.createdAt) private var myDogs: [Dog]

    enum Filter { case nearby, walking }

    @State private var filter: Filter = .nearby
    @State private var dogs: [PackDog] = []
    @State private var posts: [PackPost] = []
    @State private var following: Set<UUID> = []
    @State private var selected: PackDog?
    @State private var status: String?
    @State private var profile: PackDog?
    @State private var reporting: PackDog?
    @State private var confirmDelete = false
    @State private var deleteError: String?

    private var walkingNow: Set<UUID> {
        Set(posts.filter { $0.kind == "walk" && Date().timeIntervalSince($0.createdAt) < 2 * 3600 }.map(\.dogID))
    }

    private var shown: [PackDog] {
        let placed = dogs.filter { $0.lat != nil && $0.lon != nil }
        switch filter {
        case .nearby: return placed.filter { ($0.distanceM ?? 0) <= 1000 }
        case .walking: return placed.filter { walkingNow.contains($0.id) }
        }
    }

    var body: some View {
        ZStack(alignment: .bottom) {
            Map(initialPosition: .userLocation(fallback: .automatic)) {
                UserAnnotation { _ in
                    ZStack {
                        Circle().fill(Color.opawAlert.opacity(0.14)).frame(width: 90, height: 90)
                        Circle()
                            .fill(Color.opawAlert)
                            .frame(width: 20, height: 20)
                            .overlay(Circle().stroke(Color.white, lineWidth: 4))
                            .shadow(color: .black.opacity(0.2), radius: 5, y: 4)
                    }
                }
                ForEach(shown) { dog in
                    Annotation("", coordinate: CLLocationCoordinate2D(latitude: dog.lat ?? 0, longitude: dog.lon ?? 0), anchor: .bottom) {
                        Button {
                            withAnimation(.spring(response: 0.4, dampingFraction: 0.85)) { selected = dog }
                            Haptics.select()
                        } label: {
                            VStack(spacing: 6) {
                                PackAvatar(url: pack.publicURL(for: dog.avatarPath), size: 74, plain: true)
                                    .sticker(edge: 4, blur: 5, y: 8, opacity: 0.18)
                                    .loopMotion(walkingNow.contains(dog.id) ? .trot : .breathe, duration: walkingNow.contains(dog.id) ? 0.6 : 3)
                                Text(dog.name)
                                    .font(.opaw(13, .heavy))
                                    .foregroundStyle(Color(hex: 0x111111))
                                    .padding(.horizontal, 12)
                                    .padding(.vertical, 4)
                                    .background(Color.white, in: Capsule())
                                    .shadow(color: .black.opacity(0.08), radius: 7, y: 6)
                            }
                        }
                        .buttonStyle(.pressable)
                    }
                }
            }
            .mapStyle(.standard(pointsOfInterest: .excludingAll))
            .ignoresSafeArea()

            if let dog = selected ?? shown.first {
                card(dog)
                    .padding(.horizontal, 14)
                    .padding(.bottom, 8)
                    .transition(.move(edge: .bottom).combined(with: .opacity))
            } else if let status {
                Text(status)
                    .font(.opaw(15, .semibold))
                    .foregroundStyle(Color.opawMuted)
                    .padding(20)
                    .frame(maxWidth: .infinity)
                    .background(Color.white, in: RoundedRectangle(cornerRadius: 36, style: .continuous))
                    .padding(.horizontal, 14)
                    .padding(.bottom, 8)
            }
        }
        .overlay(alignment: .top) {
            HStack(spacing: 8) {
                FilterPill(title: "Within 1 km", isSelected: filter == .nearby, verticalPadding: 11) { filter = .nearby }
                FilterPill(title: "Out walking", isSelected: filter == .walking, verticalPadding: 11) { filter = .walking }
                Spacer()
                settingsMenu
            }
            .padding(.horizontal, 20)
            .padding(.top, 10)
        }
        .toolbar(.hidden, for: .navigationBar)
        .task {
            location.warmUp()
            await load()
        }
        .sheet(item: $profile) { dog in
            PackDogProfile(dog: dog, url: pack.publicURL(for: dog.avatarPath)) {
                reporting = dog
            } onBlock: {
                Task {
                    try? await pack.block(userID: dog.ownerID)
                    selected = nil
                    await load()
                }
            }
        }
        .sheet(item: $reporting) { dog in
            ReportSheet(subject: dog.name) { reason in
                try await pack.report(dogID: dog.id, reason: reason)
            }
        }
        .confirmationDialog("Delete your Pack account?", isPresented: $confirmDelete, titleVisibility: .visible) {
            Button("Delete account", role: .destructive) {
                Task {
                    do { try await pack.deleteAccount() } catch { deleteError = error.localizedDescription }
                }
            }
        } message: {
            Text("This removes your shared dogs, posts, likes and photos from the Pack. Your logs, walks and memories stay on this iPhone.")
        }
        .alert("Couldn't delete your account", isPresented: Binding(get: { deleteError != nil }, set: { if !$0 { deleteError = nil } })) {
            Button("OK", role: .cancel) {}
        } message: {
            Text(deleteError ?? "")
        }
    }

    private var settingsMenu: some View {
        Menu {
            Button {
                Task { await shareMyDogs() }
            } label: {
                Label("Show my dog on the map", systemImage: "mappin.and.ellipse")
            }
            Link(destination: URL(string: "https://github.com/RusselHarveyMercadoIV/opaw/blob/main/docs/PRIVACY.md")!) {
                Label("Privacy policy", systemImage: "hand.raised")
            }
            Button(role: .destructive) {
                confirmDelete = true
            } label: {
                Label("Leave the pack and delete my account", systemImage: "person.crop.circle.badge.xmark")
            }
        } label: {
            Image(systemName: "ellipsis")
                .font(.system(size: 16, weight: .black))
                .foregroundStyle(Color(hex: 0x111111))
                .frame(width: 44, height: 44)
                .background(Color.white, in: Circle())
                .shadow(color: .black.opacity(0.06), radius: 8, y: 6)
        }
        .accessibilityLabel("Pack settings")
    }

    private func card(_ dog: PackDog) -> some View {
        let today = Calendar.current.startOfDay(for: Date())
        let theirWalks = posts.filter { $0.kind == "walk" && $0.dogID == dog.id }
        let todayKm = theirWalks.filter { $0.createdAt >= today }.compactMap(\.distanceM).reduce(0, +)
        let weekAgo = Date().addingTimeInterval(-7 * 86_400)
        let weekWalks = theirWalks.filter { $0.createdAt >= weekAgo }.count
        let isFriend = following.contains(dog.id)
        var details = [dog.breed, dog.distanceM.map { OpawFormat.distance(meters: $0) + " away" }].compactMap { $0 }.filter { !$0.isEmpty }
        if walkingNow.contains(dog.id) { details.append("walking now") }
        return VStack(spacing: 0) {
            HStack(spacing: 14) {
                PackAvatar(url: pack.publicURL(for: dog.avatarPath), size: 66)
                VStack(alignment: .leading, spacing: 1) {
                    Text(dog.name)
                        .font(.opaw(23, .black))
                        .foregroundStyle(Color(hex: 0x111111))
                    Text(details.joined(separator: " · "))
                        .font(.opaw(14, .medium))
                        .foregroundStyle(Color(hex: 0x999999))
                        .lineLimit(1)
                }
                Spacer(minLength: 0)
            }
            HStack(spacing: 0) {
                StatColumn(value: OpawFormat.distance(meters: todayKm), label: "Today", valueSize: 18, labelSize: 13, valueColor: Color(hex: 0x111111))
                StatColumn(value: "\(weekWalks)×", label: "Walks this week", valueSize: 18, labelSize: 13, valueColor: Color(hex: 0x111111))
                StatColumn(value: isFriend ? "Friends" : "New", label: "With you", valueSize: 18, labelSize: 13, valueColor: Color(hex: 0x111111))
            }
            .padding(.vertical, 18)
            HStack(spacing: 10) {
                Button { profile = dog } label: {
                    Text("View profile")
                        .font(.opaw(16, .bold))
                        .foregroundStyle(Color(hex: 0x777777))
                        .frame(maxWidth: .infinity)
                        .frame(height: 58)
                        .background(Color(hex: 0xF1F1F2), in: Capsule())
                }
                .buttonStyle(.pressable)
                Button {
                    Task { await wave(dog) }
                } label: {
                    Text(isFriend ? "Friends ✓" : "Wave hello")
                        .font(.opaw(16, .bold))
                        .foregroundStyle(.white)
                        .frame(maxWidth: .infinity)
                        .frame(height: 58)
                        .background(Color(hex: 0x111111), in: Capsule())
                }
                .buttonStyle(.pressable)
            }
        }
        .padding(20)
        .background(Color.white, in: RoundedRectangle(cornerRadius: 36, style: .continuous))
        .shadow(color: .black.opacity(0.12), radius: 22, y: 18)
    }

    private func load() async {
        guard let here = location.lastLocation else {
            if !location.isAuthorized { location.requestPermission() }
            status = "Waiting for your location…"
            try? await Task.sleep(for: .seconds(2))
            if location.lastLocation != nil { await load() }
            return
        }
        do {
            dogs = try await pack.nearbyDogs(latitude: here.coordinate.latitude, longitude: here.coordinate.longitude)
            following = try await pack.followedDogIDs()
            posts = (try? await pack.feed(limit: 100)) ?? []
            status = dogs.isEmpty ? "No pets nearby yet. Invite your dog park friends!" : nil
        } catch {
            status = error.localizedDescription
        }
    }

    private func shareMyDogs() async {
        guard let here = location.lastLocation else {
            status = "We need your location first."
            return
        }
        do {
            for dog in myDogs {
                try await pack.shareDog(dog, latitude: here.coordinate.latitude, longitude: here.coordinate.longitude)
            }
            Haptics.success()
        } catch {
            status = error.localizedDescription
        }
    }

    private func wave(_ dog: PackDog) async {
        let follow = !following.contains(dog.id)
        do {
            try await pack.setFollowing(follow, dogID: dog.id)
            if follow { following.insert(dog.id) } else { following.remove(dog.id) }
            Haptics.success()
            for mine in myDogs {
                BadgeService.refresh(mine, friendsCount: following.count)
            }
            try? modelContext.save()
        } catch {
            status = error.localizedDescription
        }
    }
}

/// Another pet's profile: photo, breed, and report or block.
private struct PackDogProfile: View {
    let dog: PackDog
    let url: URL?
    let onReport: () -> Void
    let onBlock: () -> Void
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        VStack(spacing: 14) {
            Capsule().fill(Color(hex: 0xDDDDDD)).frame(width: 40, height: 5)
            PackAvatar(url: url, size: 140, plain: true)
                .sticker(edge: 5, blur: 7, y: 10)
                .padding(.top, 10)
            Text(dog.name)
                .font(.opaw(30, .black))
            Text([dog.breed, dog.size.capitalized].filter { !$0.isEmpty }.joined(separator: " · "))
                .font(.opaw(15, .medium))
                .foregroundStyle(Color.opawMuted)
            Spacer()
            HStack(spacing: 10) {
                Button {
                    dismiss()
                    onReport()
                } label: {
                    Text("Report")
                        .font(.opaw(16, .bold))
                        .foregroundStyle(Color(hex: 0x777777))
                        .frame(maxWidth: .infinity)
                        .frame(height: 58)
                        .background(Color(hex: 0xF1F1F2), in: Capsule())
                }
                Button(role: .destructive) {
                    dismiss()
                    onBlock()
                } label: {
                    Text("Block owner")
                        .font(.opaw(16, .bold))
                        .foregroundStyle(.white)
                        .frame(maxWidth: .infinity)
                        .frame(height: 58)
                        .background(Color.opawAlert, in: Capsule())
                }
            }
            .buttonStyle(.pressable)
        }
        .padding(24)
        .presentationDetents([.medium])
        .presentationCornerRadius(40)
    }
}

// MARK: - Feed

private struct PackFeedView: View {
    @Environment(PackService.self) private var pack
    @State private var posts: [PackPost] = []
    @State private var error: String?
    @State private var reporting: PackPost?

    var body: some View {
        List {
            if let error {
                Text(error).foregroundStyle(.red)
            }
            if posts.isEmpty && error == nil {
                ContentUnavailableView("Quiet in the pack", systemImage: "pawprint", description: Text("Share a walk or a memory to get things going."))
            }
            ForEach(posts) { post in
                PackPostRow(post: post, myID: pack.userID) { liked in
                    Task {
                        try? await pack.setLiked(liked, post: post)
                        await load()
                    }
                } onReport: {
                    reporting = post
                } onBlock: {
                    Task {
                        try? await pack.block(userID: post.ownerID)
                        await load()
                    }
                } onDelete: {
                    Task {
                        try? await pack.deletePost(post)
                        await load()
                    }
                }
            }
        }
        .listStyle(.plain)
        .refreshable { await load() }
        .task { await load() }
        .sheet(item: $reporting) { post in
            ReportSheet(subject: "this post") { reason in
                try await pack.report(postID: post.id, reason: reason)
            }
        }
    }

    private func load() async {
        do {
            posts = try await pack.feed()
            error = nil
        } catch {
            self.error = error.localizedDescription
        }
    }
}

private struct PackPostRow: View {
    let post: PackPost
    let myID: UUID?
    let onLike: (Bool) -> Void
    let onReport: () -> Void
    let onBlock: () -> Void
    let onDelete: () -> Void

    @Environment(PackService.self) private var pack

    private var likedByMe: Bool {
        guard let myID else { return false }
        return post.likes?.contains { $0.userID == myID } ?? false
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(spacing: 10) {
                PackAvatar(url: pack.publicURL(for: post.dog?.avatarPath), size: 40)
                VStack(alignment: .leading) {
                    Text(post.dog?.name ?? "A good dog").font(.headline)
                    Text(post.createdAt, style: .relative)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
                Spacer()
                Menu {
                    if post.ownerID == myID {
                        Button("Delete", systemImage: "trash", role: .destructive, action: onDelete)
                    } else {
                        Button("Report", systemImage: "flag", action: onReport)
                        Button("Block owner", systemImage: "hand.raised", role: .destructive, action: onBlock)
                    }
                } label: {
                    Image(systemName: "ellipsis")
                        .padding(8)
                }
            }

            switch post.kind {
            case "walk":
                if let route = post.route, route.count > 1 {
                    Map(initialPosition: .automatic) {
                        MapPolyline(coordinates: route.compactMap { pair in
                            pair.count == 2 ? CLLocationCoordinate2D(latitude: pair[0], longitude: pair[1]) : nil
                        })
                        .stroke(Color.opawOrange, lineWidth: 4)
                    }
                    .frame(height: 160)
                    .clipShape(RoundedRectangle(cornerRadius: 12))
                    .allowsHitTesting(false)
                }
                HStack {
                    StatTile(title: "Distance", value: OpawFormat.distance(meters: post.distanceM ?? 0))
                    StatTile(title: "Time", value: OpawFormat.duration(post.durationS ?? 0))
                }
            case "memory":
                if let url = pack.publicURL(for: post.imagePath) {
                    AsyncImage(url: url) { image in
                        image.resizable().scaledToFit()
                    } placeholder: {
                        Color.secondary.opacity(0.1).frame(height: 200)
                    }
                    .clipShape(RoundedRectangle(cornerRadius: 12))
                }
            case "badge":
                if let badge = post.badge.flatMap(Badge.init(rawValue:)) {
                    Label(badge.title, systemImage: badge.symbol)
                        .font(.title3.bold())
                        .foregroundStyle(Color.opawOrange)
                }
            default:
                EmptyView()
            }

            if !post.caption.isEmpty {
                Text(post.caption)
            }

            Button {
                onLike(!likedByMe)
            } label: {
                Label("\(post.likes?.count ?? 0)", systemImage: likedByMe ? "heart.fill" : "heart")
                    .foregroundStyle(likedByMe ? .red : .secondary)
            }
            .buttonStyle(.plain)
        }
        .padding(.vertical, 6)
    }
}

struct PackAvatar: View {
    let url: URL?
    var size: CGFloat = 40
    /// A cutout without the round background, for stickers on the map.
    var plain = false

    var body: some View {
        let image = AsyncImage(url: url) { image in
            image.resizable().scaledToFit()
        } placeholder: {
            Image(systemName: "pawprint.fill")
                .font(.system(size: size * 0.35))
                .foregroundStyle(Color.opawMuted)
        }
        .frame(width: size, height: size)
        if plain {
            image
        } else {
            image
                .background(Color.opawChip, in: Circle())
                .clipShape(Circle())
        }
    }
}

/// Collects a reason and files a report.
struct ReportSheet: View {
    let subject: String
    let submit: (String) async throws -> Void

    @Environment(\.dismiss) private var dismiss
    @State private var reason = "Inappropriate content"
    @State private var details = ""
    @State private var error: String?

    private let reasons = ["Inappropriate content", "Harassment or hate", "Spam or selling animals", "Animal welfare concern", "Something else"]

    var body: some View {
        NavigationStack {
            Form {
                Picker("Reason", selection: $reason) {
                    ForEach(reasons, id: \.self) { Text($0) }
                }
                TextField("Details (optional)", text: $details, axis: .vertical)
                if let error {
                    Text(error).foregroundStyle(.red)
                }
            }
            .navigationTitle("Report \(subject)")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Send") {
                        Task {
                            do {
                                try await submit(details.isEmpty ? reason : "\(reason): \(details)")
                                dismiss()
                            } catch {
                                self.error = error.localizedDescription
                            }
                        }
                    }
                }
            }
        }
        .presentationDetents([.medium])
    }
}
