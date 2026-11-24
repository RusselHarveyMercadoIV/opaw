import Foundation

/// Stores a dog's generated motion clips on disk.
///
/// Clips are "stacked alpha" MP4 files: the top half is the colour video and the bottom half
/// is the transparency mask. The avatar worker produces them and `StackedAlphaVideoView` plays them.
enum AvatarClipStore {
    /// Clip names produced by the avatar worker. Keep in sync with `avatar-worker/opaw_worker/prompts.py`.
    static let clipNames = ["idle", "wag", "excited", "hungry", "sleepy", "shake", "celebrate", "walk"]

    static func directory(for dogID: UUID) -> URL {
        let base = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
        return base.appendingPathComponent("AvatarClips", isDirectory: true)
            .appendingPathComponent(dogID.uuidString, isDirectory: true)
    }

    static func url(dogID: UUID, clip: String) -> URL {
        directory(for: dogID).appendingPathComponent("\(clip).mp4")
    }

    /// The clip file if it exists, falling back to "idle".
    static func playableURL(dogID: UUID, preferred clip: String) -> URL? {
        for name in [clip, "idle"] {
            let url = url(dogID: dogID, clip: name)
            if FileManager.default.fileExists(atPath: url.path) {
                return url
            }
        }
        return nil
    }

    static func hasClips(dogID: UUID) -> Bool {
        playableURL(dogID: dogID, preferred: "idle") != nil
    }

    static func save(_ data: Data, dogID: UUID, clip: String) throws {
        let dir = directory(for: dogID)
        try FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        try data.write(to: url(dogID: dogID, clip: clip), options: .atomic)
    }

    static func deleteAll(dogID: UUID) {
        try? FileManager.default.removeItem(at: directory(for: dogID))
    }
}
