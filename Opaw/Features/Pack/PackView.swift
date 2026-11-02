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
            .navigationTitle("Pack")
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

// MARK: - Home

private struct PackHomeView: View {
    enum PackSection: String, CaseIterable {
        case nearby = "Nearby"
        case feed = "Feed"
    }

    @Environment(PackService.self) private var pack
    @State private var section: PackSection = .nearby
    @State private var confirmDelete = false
    @State private var deleteError: String?

    var body: some View {
        VStack(spacing: 0) {
            Picker("Section", selection: $section) {
                ForEach(PackSection.allCases, id: \.self) { Text($0.rawValue).tag($0) }
            }
            .pickerStyle(.segmented)
            .padding()
            switch section {
            case .nearby: NearbyDogsView()
            case .feed: PackFeedView()
            }
        }
        .toolbar {
            Menu {
                Link(destination: URL(string: "https://github.com/RusselHarveyMercadoIV/opaw/blob/main/docs/PRIVACY.md")!) {
                    Label("Privacy policy", systemImage: "hand.raised")
                }
                Button(role: .destructive) {
                    confirmDelete = true
                } label: {
                    Label("Leave the pack and delete my account", systemImage: "person.crop.circle.badge.xmark")
                }
            } label: {
                Label("Pack settings", systemImage: "gearshape")
            }
        }
        .confirmationDialog(
            "Delete your Pack account?",
            isPresented: $confirmDelete,
            titleVisibility: .visible
        ) {
            Button("Delete account", role: .destructive) {
                Task {
                    do {
                        try await pack.deleteAccount()
                    } catch {
                        deleteError = error.localizedDescription
                    }
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
}

private struct NearbyDogsView: View {
    @Environment(PackService.self) private var pack
    @Environment(WalkRecorder.self) private var location
    @Environment(\.modelContext) private var modelContext
    @Query(sort: \Dog.createdAt) private var myDogs: [Dog]
    @State private var dogs: [PackDog] = []
    @State private var following: Set<UUID> = []
    @State private var status: String?
    @State private var isLoading = false
    @State private var reporting: PackDog?

    var body: some View {
        List {
            Section {
                Map(initialPosition: .userLocation(fallback: .automatic)) {
                    UserAnnotation()
                    ForEach(dogs.filter { $0.lat != nil && $0.lon != nil }) { dog in
                        Annotation(dog.name, coordinate: CLLocationCoordinate2D(latitude: dog.lat ?? 0, longitude: dog.lon ?? 0)) {
                            PackAvatar(url: pack.publicURL(for: dog.avatarPath), size: 36)
                        }
                    }
                }
                .frame(height: 240)
                .listRowInsets(EdgeInsets())
                Button {
                    Task { await shareMyDogs() }
                } label: {
                    Label("Show my dog here", systemImage: "mappin.and.ellipse")
                }
                if let status {
                    Text(status).font(.footnote).foregroundStyle(.secondary)
                }
            } footer: {
                Text("Positions are rounded to about 1 km.")
            }

            Section("Dogs nearby") {
                if isLoading {
                    ProgressView()
                } else if dogs.isEmpty {
                    Text("No dogs nearby yet. Invite your dog park friends!")
                        .foregroundStyle(.secondary)
                }
                ForEach(dogs) { dog in
                    HStack(spacing: 12) {
                        PackAvatar(url: pack.publicURL(for: dog.avatarPath), size: 48)
                        VStack(alignment: .leading) {
                            Text(dog.name).font(.headline)
                            Text([dog.breed, dog.distanceM.map { OpawFormat.distance(meters: $0) + " away" }]
                                .compactMap { $0 }
                                .filter { !$0.isEmpty }
                                .joined(separator: " · "))
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }
                        Spacer()
                        Button(following.contains(dog.id) ? "Friends" : "Add") {
                            Task { await toggleFollow(dog) }
                        }
                        .buttonStyle(.bordered)
                    }
                    .contextMenu {
                        Button("Report", systemImage: "flag") { reporting = dog }
                        Button("Block owner", systemImage: "hand.raised", role: .destructive) {
                            Task {
                                try? await pack.block(userID: dog.ownerID)
                                await load()
                            }
                        }
                    }
                }
            }
        }
        .refreshable { await load() }
        .task {
            location.warmUp()
            await load()
        }
        .sheet(item: $reporting) { dog in
            ReportSheet(subject: dog.name) { reason in
                try await pack.report(dogID: dog.id, reason: reason)
            }
        }
    }

    private func load() async {
        guard let here = location.lastLocation else {
            if !location.isAuthorized { location.requestPermission() }
            status = "Waiting for your location…"
            try? await Task.sleep(for: .seconds(2))
            if location.lastLocation != nil { await load() }
            return
        }
        isLoading = true
        defer { isLoading = false }
        do {
            dogs = try await pack.nearbyDogs(latitude: here.coordinate.latitude, longitude: here.coordinate.longitude)
            following = try await pack.followedDogIDs()
            status = nil
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
            status = "Your dogs are on the map."
        } catch {
            status = error.localizedDescription
        }
    }

    private func toggleFollow(_ dog: PackDog) async {
        let follow = !following.contains(dog.id)
        do {
            try await pack.setFollowing(follow, dogID: dog.id)
            if follow { following.insert(dog.id) } else { following.remove(dog.id) }
            for mine in myDogs {
                BadgeService.refresh(mine, friendsCount: following.count)
            }
            try? modelContext.save()
        } catch {
            status = error.localizedDescription
        }
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

    var body: some View {
        AsyncImage(url: url) { image in
            image.resizable().scaledToFit()
        } placeholder: {
            Image(systemName: "pawprint.fill")
                .foregroundStyle(Color.opawOrange)
        }
        .frame(width: size, height: size)
        .background(Color.opawOrange.opacity(0.12), in: Circle())
        .clipShape(Circle())
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
