import ActivityKit
import Foundation

/// Live Activity shown on the Lock Screen and in the Dynamic Island while a walk is recording.
struct WalkActivityAttributes: ActivityAttributes {
    struct ContentState: Codable, Hashable {
        var distanceMeters: Double
        /// When the timer should appear to have started, adjusted for pauses.
        var timerStart: Date
        var isPaused: Bool
        var pausedElapsedSeconds: Double
    }

    var dogName: String
}
