import Foundation
import Observation
import OpawKit
import Supabase
import UIKit

// MARK: - Wire types (match supabase/migrations)

struct PackDog: Codable, Identifiable, Hashable {
    let id: UUID
    let ownerID: UUID
    let name: String
    let breed: String
    let size: String
    let avatarPath: String?
    let lat: Double?
    let lon: Double?
    let distanceM: Double?

    enum CodingKeys: String, CodingKey {
        case id, name, breed, size, lat, lon
        case ownerID = "owner_id"
        case avatarPath = "avatar_path"
        case distanceM = "distance_m"
    }
}

struct PackPost: Codable, Identifiable, Hashable {
    struct DogSummary: Codable, Hashable {
        let name: String
        let breed: String
        let avatarPath: String?

        enum CodingKeys: String, CodingKey {
            case name, breed
            case avatarPath = "avatar_path"
        }
    }

    struct Like: Codable, Hashable {
        let userID: UUID

        enum CodingKeys: String, CodingKey {
            case userID = "user_id"
        }
    }

    let id: UUID
    let ownerID: UUID
    let dogID: UUID
    let kind: String
    let caption: String
    let distanceM: Double?
    let durationS: Double?
    let route: [[Double]]?
    let imagePath: String?
    let badge: String?
    let createdAt: Date
    let dog: DogSummary?
    let likes: [Like]?

    enum CodingKeys: String, CodingKey {
        case id, kind, caption, route, badge, likes
        case ownerID = "owner_id"
        case dogID = "dog_id"
        case distanceM = "distance_m"
        case durationS = "duration_s"
        case imagePath = "image_path"
        case createdAt = "created_at"
        case dog = "dogs"
    }
}

struct AvatarJob: Codable {
    let id: UUID
    let status: String
    let outputs: [String: String]
    let error: String?
}

private struct DogUpsert: Encodable {
    let id: UUID
    let owner_id: UUID
    let name: String
    let breed: String
    let size: String
    let avatar_path: String?
    let lat: Double?
    let lon: Double?
    let is_discoverable: Bool
}

private struct PostInsert: Encodable {
    let owner_id: UUID
    let dog_id: UUID
    let kind: String
    let caption: String
    let distance_m: Double?
    let duration_s: Double?
    let route: [[Double]]?
    let image_path: String?
    let badge: String?
}

private struct AvatarJobInsert: Encodable {
    let owner_id: UUID
    let dog_id: UUID
    let breed: String
    let input_path: String
}

private struct ProfileUpsert: Encodable {
    let id: UUID
    let display_name: String
    let accepted_rules_at: Date
}

private struct NearbyParams: Encodable {
    let p_lat: Double
    let p_lon: Double
    let p_radius_m: Double
}

private struct ReportInsert: Encodable {
    let reporter_id: UUID
    let post_id: UUID?
    let dog_id: UUID?
    let reason: String
}

private struct BlockInsert: Encodable {
    let blocker_id: UUID
    let blocked_id: UUID
}

private struct LikeRow: Encodable {
    let post_id: UUID
    let user_id: UUID
}

private struct FollowRow: Codable {
    let follower_id: UUID
    let dog_id: UUID
}

enum PackError: LocalizedError {
    case notConfigured
    case notSignedIn

    var errorDescription: String? {
        switch self {
        case .notConfigured: return "The Pack server isn't set up yet. See docs/SETUP.md."
        case .notSignedIn: return "Join the Pack first."
        }
    }
}

// MARK: - Service

/// Talks to Supabase for the social "Pack" features and the avatar clip queue.
@MainActor
@Observable
final class PackService {
    @ObservationIgnored let client: SupabaseClient?
    private(set) var userID: UUID?
    private(set) var hasAcceptedRules: Bool

    private static let rulesKey = "pack.acceptedRules"

    init() {
        let info = Bundle.main.infoDictionary ?? [:]
        let host = (info["SupabaseHost"] as? String ?? "").trimmingCharacters(in: .whitespaces)
        let key = (info["SupabaseAnonKey"] as? String ?? "").trimmingCharacters(in: .whitespaces)
        if !host.isEmpty, !key.isEmpty, !host.contains("$("), let url = URL(string: "https://\(host)") {
            client = SupabaseClient(supabaseURL: url, supabaseKey: key)
        } else {
            client = nil
        }
        hasAcceptedRules = UserDefaults.standard.bool(forKey: Self.rulesKey)
        userID = client?.auth.currentUser?.id
    }

    var isConfigured: Bool { client != nil }
    var isSignedIn: Bool { userID != nil }

    private func requireClient() throws -> SupabaseClient {
        guard let client else { throw PackError.notConfigured }
        return client
    }

    private func requireUser() throws -> UUID {
        guard let userID else { throw PackError.notSignedIn }
        return userID
    }

    /// Signs in anonymously (no account needed) and records that the community rules were accepted.
    func join(displayName: String) async throws {
        let client = try requireClient()
        if client.auth.currentUser == nil {
            try await client.auth.signInAnonymously()
        }
        let id = try await client.auth.session.user.id
        userID = id
        try await client.from("profiles")
            .upsert(ProfileUpsert(id: id, display_name: displayName, accepted_rules_at: Date()))
            .execute()
        hasAcceptedRules = true
        UserDefaults.standard.set(true, forKey: Self.rulesKey)
    }

    /// Makes sure there's a session, for features like avatar generation that don't need the rules.
    func ensureSession() async throws -> UUID {
        let client = try requireClient()
        if client.auth.currentUser == nil {
            try await client.auth.signInAnonymously()
        }
        let id = try await client.auth.session.user.id
        userID = id
        return id
    }

    // MARK: Dogs

    /// Shares the dog with the Pack at a coarse location.
    func shareDog(_ dog: Dog, latitude: Double?, longitude: Double?, discoverable: Bool = true) async throws {
        let client = try requireClient()
        let owner = try requireUser()
        var avatarPath: String?
        if let data = dog.cutoutData ?? dog.photoData {
            let path = "\(owner.uuidString.lowercased())/dog-\(dog.id.uuidString.lowercased()).png"
            try await client.storage.from("post-images").upload(
                path: path,
                file: data,
                options: FileOptions(contentType: "image/png", upsert: true)
            )
            avatarPath = path
        }
        var lat: Double?
        var lon: Double?
        if let latitude, let longitude {
            let coarse = LocationPrivacy.coarsen(latitude: latitude, longitude: longitude)
            lat = coarse.latitude
            lon = coarse.longitude
        }
        try await client.from("dogs").upsert(DogUpsert(
            id: dog.id,
            owner_id: owner,
            name: dog.name,
            breed: dog.breed,
            size: dog.size.rawValue,
            avatar_path: avatarPath,
            lat: lat,
            lon: lon,
            is_discoverable: discoverable
        )).execute()
    }

    func nearbyDogs(latitude: Double, longitude: Double, radiusMeters: Double = 5000) async throws -> [PackDog] {
        let client = try requireClient()
        return try await client
            .rpc("nearby_dogs", params: NearbyParams(p_lat: latitude, p_lon: longitude, p_radius_m: radiusMeters))
            .execute()
            .value
    }

    func publicURL(for path: String?) -> URL? {
        guard let path, let client else { return nil }
        return try? client.storage.from("post-images").getPublicURL(path: path)
    }

    // MARK: Feed

    func feed(limit: Int = 50) async throws -> [PackPost] {
        let client = try requireClient()
        return try await client.from("posts")
            .select("*, dogs(name, breed, avatar_path), likes(user_id)")
            .order("created_at", ascending: false)
            .limit(limit)
            .execute()
            .value
    }

    func shareWalk(_ walk: Walk, dog: Dog, caption: String) async throws {
        let client = try requireClient()
        let owner = try requireUser()
        // Trim the start and end so home isn't revealed, then thin the route.
        let trimmed = LocationPrivacy.trimRouteEnds(walk.route)
        let step = max(1, trimmed.count / 200)
        let route = stride(from: 0, to: trimmed.count, by: step).map { [trimmed[$0].latitude, trimmed[$0].longitude] }
        try await client.from("posts").insert(PostInsert(
            owner_id: owner,
            dog_id: dog.id,
            kind: "walk",
            caption: caption,
            distance_m: walk.distanceMeters,
            duration_s: walk.movingSeconds,
            route: route,
            image_path: nil,
            badge: nil
        )).execute()
        walk.sharedToPack = true
    }

    func shareMemory(_ memory: Memory, dog: Dog) async throws {
        let client = try requireClient()
        let owner = try requireUser()
        var imagePath: String?
        if let data = memory.photoData,
           let jpeg = UIImage(data: data)?.preparedForProcessing(maxDimension: 1600).jpegData(compressionQuality: 0.8) {
            let path = "\(owner.uuidString.lowercased())/\(memory.id.uuidString.lowercased()).jpg"
            try await client.storage.from("post-images").upload(
                path: path,
                file: jpeg,
                options: FileOptions(contentType: "image/jpeg", upsert: true)
            )
            imagePath = path
        }
        try await client.from("posts").insert(PostInsert(
            owner_id: owner,
            dog_id: dog.id,
            kind: "memory",
            caption: memory.caption,
            distance_m: nil,
            duration_s: nil,
            route: nil,
            image_path: imagePath,
            badge: nil
        )).execute()
    }

    func shareBadge(_ badge: Badge, dog: Dog) async throws {
        let client = try requireClient()
        let owner = try requireUser()
        try await client.from("posts").insert(PostInsert(
            owner_id: owner,
            dog_id: dog.id,
            kind: "badge",
            caption: "\(dog.name) earned \(badge.title)!",
            distance_m: nil,
            duration_s: nil,
            route: nil,
            image_path: nil,
            badge: badge.rawValue
        )).execute()
    }

    func setLiked(_ liked: Bool, post: PackPost) async throws {
        let client = try requireClient()
        let user = try requireUser()
        if liked {
            try await client.from("likes").upsert(LikeRow(post_id: post.id, user_id: user)).execute()
        } else {
            try await client.from("likes").delete()
                .eq("post_id", value: post.id.uuidString)
                .eq("user_id", value: user.uuidString)
                .execute()
        }
    }

    func deletePost(_ post: PackPost) async throws {
        let client = try requireClient()
        try await client.from("posts").delete().eq("id", value: post.id.uuidString).execute()
    }

    // MARK: Friends

    func followedDogIDs() async throws -> Set<UUID> {
        let client = try requireClient()
        let user = try requireUser()
        let rows: [FollowRow] = try await client.from("follows")
            .select()
            .eq("follower_id", value: user.uuidString)
            .execute()
            .value
        return Set(rows.map(\.dog_id))
    }

    func setFollowing(_ following: Bool, dogID: UUID) async throws {
        let client = try requireClient()
        let user = try requireUser()
        if following {
            try await client.from("follows").upsert(FollowRow(follower_id: user, dog_id: dogID)).execute()
        } else {
            try await client.from("follows").delete()
                .eq("follower_id", value: user.uuidString)
                .eq("dog_id", value: dogID.uuidString)
                .execute()
        }
    }

    // MARK: Safety

    func report(postID: UUID? = nil, dogID: UUID? = nil, reason: String) async throws {
        let client = try requireClient()
        let user = try requireUser()
        try await client.from("reports")
            .insert(ReportInsert(reporter_id: user, post_id: postID, dog_id: dogID, reason: reason))
            .execute()
    }

    func block(userID blocked: UUID) async throws {
        let client = try requireClient()
        let user = try requireUser()
        try await client.from("blocks").upsert(BlockInsert(blocker_id: user, blocked_id: blocked)).execute()
    }

    // MARK: Account

    /// Deletes the user's shared files, Pack data and account. Local data on the phone is kept.
    func deleteAccount() async throws {
        let client = try requireClient()
        let user = try requireUser()
        let folder = user.uuidString.lowercased()
        for bucket in ["post-images", "avatar-inputs"] {
            let files = (try? await client.storage.from(bucket).list(path: folder)) ?? []
            let paths = files.map { "\(folder)/\($0.name)" }
            if !paths.isEmpty {
                _ = try? await client.storage.from(bucket).remove(paths: paths)
            }
        }
        try await client.rpc("delete_my_account").execute()
        try? await client.auth.signOut()
        userID = nil
        hasAcceptedRules = false
        UserDefaults.standard.set(false, forKey: Self.rulesKey)
    }

    // MARK: Avatar clips

    /// Uploads the dog's cutout and queues a job for the avatar worker. Returns the job id.
    func requestAvatarClips(for dog: Dog) async throws -> UUID {
        let client = try requireClient()
        let owner = try await ensureSession()
        guard let data = dog.cutoutData ?? dog.photoData else { throw CutoutError.unreadableImage }
        let path = "\(owner.uuidString.lowercased())/\(dog.id.uuidString.lowercased())-\(Int(Date().timeIntervalSince1970)).png"
        try await client.storage.from("avatar-inputs").upload(
            path: path,
            file: data,
            options: FileOptions(contentType: "image/png", upsert: true)
        )
        let job: AvatarJob = try await client.from("avatar_jobs")
            .insert(AvatarJobInsert(owner_id: owner, dog_id: dog.id, breed: dog.breed, input_path: path))
            .select("id, status, outputs, error")
            .single()
            .execute()
            .value
        return job.id
    }

    func avatarJob(id: UUID) async throws -> AvatarJob {
        let client = try requireClient()
        return try await client.from("avatar_jobs")
            .select("id, status, outputs, error")
            .eq("id", value: id.uuidString)
            .single()
            .execute()
            .value
    }

    /// Downloads finished clips into `AvatarClipStore`. Returns true when the job is done.
    func syncAvatarClips(for dog: Dog) async throws -> Bool {
        guard let jobString = dog.avatarJobID, let jobID = UUID(uuidString: jobString) else { return false }
        let client = try requireClient()
        let job = try await avatarJob(id: jobID)
        switch job.status {
        case "done":
            for (clip, path) in job.outputs {
                let data = try await client.storage.from("avatar-clips").download(path: path)
                try AvatarClipStore.save(data, dogID: dog.id, clip: clip)
            }
            dog.avatarStatus = .ready
            return true
        case "failed":
            dog.avatarStatus = .failed
            return true
        default:
            return false
        }
    }
}
