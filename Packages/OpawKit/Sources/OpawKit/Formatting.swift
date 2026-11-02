import Foundation

public enum OpawFormat {
    /// "1.24 km" or "850 m".
    public static func distance(meters: Double) -> String {
        if meters < 1000 {
            return "\(Int(meters.rounded())) m"
        }
        return String(format: "%.2f km", meters / 1000)
    }

    /// "1:05:09" or "12:04".
    public static func duration(_ seconds: TimeInterval) -> String {
        let total = max(0, Int(seconds.rounded()))
        let h = total / 3600
        let m = (total % 3600) / 60
        let s = total % 60
        if h > 0 {
            return String(format: "%d:%02d:%02d", h, m, s)
        }
        return String(format: "%d:%02d", m, s)
    }

    /// "12'30\" /km", or "--" when unknown.
    public static func pace(minutesPerKm: Double?) -> String {
        guard let pace = minutesPerKm, pace.isFinite, pace > 0 else { return "--" }
        var minutes = Int(pace)
        var seconds = Int(((pace - Double(minutes)) * 60).rounded())
        if seconds == 60 {
            minutes += 1
            seconds = 0
        }
        return String(format: "%d'%02d\" /km", minutes, seconds)
    }
}
