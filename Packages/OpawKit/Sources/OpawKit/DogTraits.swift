import Foundation

public enum DogSize: String, CaseIterable, Codable, Sendable, Identifiable {
    case small, medium, large, giant
    public var id: String { rawValue }
    public var title: String { rawValue.capitalized }
}

public enum EnergyLevel: String, CaseIterable, Codable, Sendable, Identifiable {
    case low, medium, high
    public var id: String { rawValue }
    public var title: String { rawValue.capitalized }
}

/// The traits that personalize goals and tips for a dog.
public struct DogTraits: Equatable, Sendable {
    public var size: DogSize
    public var energy: EnergyLevel
    public var ageYears: Double

    public init(size: DogSize, energy: EnergyLevel, ageYears: Double) {
        self.size = size
        self.energy = energy
        self.ageYears = ageYears
    }

    public var lifeStage: LifeStage {
        switch ageYears {
        case ..<1: return .puppy
        case ..<8: return .adult
        default: return .senior
        }
    }

    /// A sensible default daily activity goal in minutes.
    ///
    /// This is a starting point the owner can edit, not veterinary advice.
    public var recommendedDailyActivityMinutes: Int {
        var minutes: Double
        switch energy {
        case .low: minutes = 40
        case .medium: minutes = 60
        case .high: minutes = 90
        }
        switch size {
        case .small: minutes *= 0.8
        case .medium: break
        case .large: minutes *= 1.1
        case .giant: minutes *= 0.9
        }
        switch lifeStage {
        case .puppy:
            // Common rule of thumb: about 5 minutes per month of age, up to twice a day.
            let months = max(2, ageYears * 12)
            minutes = min(minutes, months * 5 * 2)
        case .adult: break
        case .senior: minutes *= 0.7
        }
        // Round to the nearest 5 minutes.
        return Int((minutes / 5).rounded()) * 5
    }

    /// Weekly distance goal in kilometres, derived from the daily minutes at a relaxed walking pace.
    public var recommendedWeeklyKm: Double {
        let kmPerMinute = 4.0 / 60.0
        let weekly = Double(recommendedDailyActivityMinutes) * 7 * kmPerMinute * 0.6
        return (weekly * 2).rounded() / 2
    }
}

public enum LifeStage: String, Sendable {
    case puppy, adult, senior
}
