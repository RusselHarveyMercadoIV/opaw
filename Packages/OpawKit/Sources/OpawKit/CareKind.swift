import Foundation

/// Every kind of care event a dog owner can log.
public enum CareKind: String, CaseIterable, Codable, Sendable, Identifiable {
    case meal
    case water
    case pee
    case poop
    case bath
    case brush
    case nails
    case meds
    case vet
    case weight
    case play

    public var id: String { rawValue }

    public var title: String {
        switch self {
        case .meal: return "Meal"
        case .water: return "Water"
        case .pee: return "Pee"
        case .poop: return "Poop"
        case .bath: return "Bath"
        case .brush: return "Brush"
        case .nails: return "Nails"
        case .meds: return "Meds"
        case .vet: return "Vet visit"
        case .weight: return "Weight"
        case .play: return "Play"
        }
    }

    /// SF Symbol name used across the app and widgets.
    public var symbol: String {
        switch self {
        case .meal: return "fork.knife"
        case .water: return "drop.fill"
        case .pee: return "drop.triangle"
        case .poop: return "leaf.fill"
        case .bath: return "bathtub.fill"
        case .brush: return "comb.fill"
        case .nails: return "scissors"
        case .meds: return "pills.fill"
        case .vet: return "cross.case.fill"
        case .weight: return "scalemass.fill"
        case .play: return "tennisball.fill"
        }
    }

    public var emoji: String {
        switch self {
        case .meal: return "🍖"
        case .water: return "💧"
        case .pee: return "💛"
        case .poop: return "💩"
        case .bath: return "🛁"
        case .brush: return "🪮"
        case .nails: return "✂️"
        case .meds: return "💊"
        case .vet: return "🩺"
        case .weight: return "⚖️"
        case .play: return "🎾"
        }
    }

    /// The kinds shown as one-tap buttons on Home and in the widget.
    public static let quickLog: [CareKind] = [.meal, .water, .pee, .poop]

    /// Whether this kind usually happens several times a day.
    public var isDaily: Bool {
        switch self {
        case .meal, .water, .pee, .poop, .play: return true
        default: return false
        }
    }
}

/// Optional detail for poop logs, useful for spotting health issues.
public enum PoopConsistency: String, CaseIterable, Codable, Sendable, Identifiable {
    case hard
    case firm
    case normal
    case soft
    case runny
    case bloody

    public var id: String { rawValue }

    /// The choices shown when logging; blood is logged as a color instead.
    public static let pickerCases: [PoopConsistency] = [.hard, .firm, .normal, .soft, .runny]

    public var title: String {
        switch self {
        case .hard: return "Hard"
        case .firm: return "Firm"
        case .normal: return "Normal"
        case .soft: return "Soft"
        case .runny: return "Runny"
        case .bloody: return "Blood visible"
        }
    }

    /// Whether this consistency deserves a gentle "keep an eye on it" hint.
    public var isConcerning: Bool {
        switch self {
        case .firm, .normal, .soft: return false
        case .runny, .hard, .bloody: return true
        }
    }
}

public enum PoopColor: String, CaseIterable, Codable, Sendable, Identifiable {
    case brown
    case yellow
    case green
    case black
    case redTinge

    public var id: String { rawValue }

    public var title: String {
        switch self {
        case .brown: return "Brown"
        case .yellow: return "Yellow"
        case .green: return "Green"
        case .black: return "Black"
        case .redTinge: return "Red tinge"
        }
    }

    /// Black or red stool can mean bleeding; worth a call to the vet.
    public var isConcerning: Bool {
        self == .black || self == .redTinge
    }
}
