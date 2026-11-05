import Foundation

public enum OpawFormat {
    /// Where the Units setting is kept, shared with the widgets.
    public static let unitsSuiteName = "group.app.opaw.shared"
    public static let imperialKey = "usesImperialUnits"

    /// Miles and pounds instead of kilometres and kilograms, from Profile > Units.
    public static var usesImperial: Bool {
        UserDefaults(suiteName: unitsSuiteName)?.bool(forKey: imperialKey) ?? false
    }

    public static let metersPerMile = 1609.344
    public static let poundsPerKilogram = 2.20462

    /// "1.24 km" or "850 m"; "0.77 mi" or "300 ft" in imperial.
    public static func distance(meters: Double, imperial: Bool = usesImperial) -> String {
        if imperial {
            let miles = meters / metersPerMile
            if miles < 0.1 {
                return "\(Int((meters * 3.28084).rounded())) ft"
            }
            return String(format: "%.2f mi", miles)
        }
        if meters < 1000 {
            return "\(Int(meters.rounded())) m"
        }
        return String(format: "%.2f km", meters / 1000)
    }

    /// The long-distance unit: "km" or "mi".
    public static var distanceUnit: String { usesImperial ? "mi" : "km" }

    /// Kilometres in the chosen unit, for goals and big numbers.
    public static func longDistance(km: Double) -> Double { usesImperial ? km * 1000 / metersPerMile : km }

    /// The weight unit: "kg" or "lb".
    public static var weightUnit: String { usesImperial ? "lb" : "kg" }

    /// Kilograms in the chosen unit.
    public static func weightValue(kg: Double) -> Double { usesImperial ? kg * poundsPerKilogram : kg }

    /// "9.8 kg" or "21.6 lb".
    public static func weight(kg: Double) -> String {
        let value = weightValue(kg: kg)
        let text = value.rounded() == value ? String(Int(value)) : String(format: "%.1f", value)
        return "\(text) \(weightUnit)"
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

    /// "12'30\" /km" (or "/mi"), or "--" when unknown.
    public static func pace(minutesPerKm: Double?, imperial: Bool = usesImperial) -> String {
        guard var pace = minutesPerKm, pace.isFinite, pace > 0 else { return "--" }
        if imperial { pace *= metersPerMile / 1000 }
        var minutes = Int(pace)
        var seconds = Int(((pace - Double(minutes)) * 60).rounded())
        if seconds == 60 {
            minutes += 1
            seconds = 0
        }
        return String(format: "%d'%02d\" /%@", minutes, seconds, imperial ? "mi" : "km")
    }
}
