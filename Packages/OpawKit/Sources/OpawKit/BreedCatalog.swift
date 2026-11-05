import Foundation

/// A breed Opaw knows about, with typical traits used to prefill onboarding.
public struct BreedInfo: Equatable, Sendable, Identifiable {
    public let name: String
    public let size: DogSize
    public let energy: EnergyLevel
    /// Other names an image classifier or a person might use.
    public let aliases: [String]

    public var id: String { name }

    public init(name: String, size: DogSize, energy: EnergyLevel, aliases: [String] = []) {
        self.name = name
        self.size = size
        self.energy = energy
        self.aliases = aliases
    }
}

/// A breed guess from a photo.
public struct BreedGuess: Equatable, Sendable {
    public let breed: BreedInfo
    public let confidence: Double

    public init(breed: BreedInfo, confidence: Double) {
        self.breed = breed
        self.confidence = confidence
    }
}

public enum BreedCatalog {
    public static let all: [BreedInfo] = [
        BreedInfo(name: "Labrador Retriever", size: .large, energy: .high, aliases: ["labrador", "lab"]),
        BreedInfo(name: "Golden Retriever", size: .large, energy: .high, aliases: ["golden"]),
        BreedInfo(name: "German Shepherd", size: .large, energy: .high, aliases: ["alsatian", "german shepherd dog"]),
        BreedInfo(name: "French Bulldog", size: .small, energy: .medium, aliases: ["frenchie", "french bull dog"]),
        BreedInfo(name: "Bulldog", size: .medium, energy: .low, aliases: ["english bulldog", "british bulldog"]),
        BreedInfo(name: "Poodle", size: .medium, energy: .high, aliases: ["standard poodle", "miniature poodle", "toy poodle"]),
        BreedInfo(name: "Beagle", size: .medium, energy: .high),
        BreedInfo(name: "Rottweiler", size: .large, energy: .medium),
        BreedInfo(name: "Dachshund", size: .small, energy: .medium, aliases: ["sausage dog", "wiener dog"]),
        BreedInfo(name: "Pembroke Welsh Corgi", size: .small, energy: .high, aliases: ["corgi", "pembroke", "cardigan", "welsh corgi"]),
        BreedInfo(name: "Siberian Husky", size: .large, energy: .high, aliases: ["husky", "eskimo dog"]),
        BreedInfo(name: "Alaskan Malamute", size: .giant, energy: .high, aliases: ["malamute"]),
        BreedInfo(name: "Shih Tzu", size: .small, energy: .low, aliases: ["shihtzu"]),
        BreedInfo(name: "Chihuahua", size: .small, energy: .medium),
        BreedInfo(name: "Pomeranian", size: .small, energy: .medium),
        BreedInfo(name: "Yorkshire Terrier", size: .small, energy: .medium, aliases: ["yorkie"]),
        BreedInfo(name: "Pug", size: .small, energy: .low),
        BreedInfo(name: "Boxer", size: .large, energy: .high),
        BreedInfo(name: "Border Collie", size: .medium, energy: .high, aliases: ["collie"]),
        BreedInfo(name: "Australian Shepherd", size: .medium, energy: .high, aliases: ["aussie"]),
        BreedInfo(name: "Shetland Sheepdog", size: .small, energy: .high, aliases: ["sheltie"]),
        BreedInfo(name: "Doberman Pinscher", size: .large, energy: .high, aliases: ["doberman"]),
        BreedInfo(name: "Great Dane", size: .giant, energy: .medium),
        BreedInfo(name: "Saint Bernard", size: .giant, energy: .low, aliases: ["st bernard"]),
        BreedInfo(name: "Bernese Mountain Dog", size: .giant, energy: .medium, aliases: ["bernese"]),
        BreedInfo(name: "Newfoundland", size: .giant, energy: .low),
        BreedInfo(name: "Cavalier King Charles Spaniel", size: .small, energy: .medium, aliases: ["cavalier", "blenheim spaniel", "king charles spaniel"]),
        BreedInfo(name: "Cocker Spaniel", size: .medium, energy: .medium, aliases: ["english cocker spaniel", "american cocker spaniel"]),
        BreedInfo(name: "Shiba Inu", size: .small, energy: .medium, aliases: ["shiba"]),
        BreedInfo(name: "Akita", size: .large, energy: .medium),
        BreedInfo(name: "Samoyed", size: .large, energy: .high),
        BreedInfo(name: "Maltese", size: .small, energy: .low, aliases: ["maltese dog"]),
        BreedInfo(name: "Bichon Frise", size: .small, energy: .medium, aliases: ["bichon"]),
        BreedInfo(name: "Miniature Schnauzer", size: .small, energy: .medium, aliases: ["schnauzer", "standard schnauzer", "giant schnauzer"]),
        BreedInfo(name: "Boston Terrier", size: .small, energy: .medium),
        BreedInfo(name: "Jack Russell Terrier", size: .small, energy: .high, aliases: ["jack russell", "parson russell terrier"]),
        BreedInfo(name: "West Highland White Terrier", size: .small, energy: .medium, aliases: ["westie", "west highland terrier"]),
        BreedInfo(name: "Bull Terrier", size: .medium, energy: .high),
        BreedInfo(name: "American Staffordshire Terrier", size: .medium, energy: .high, aliases: ["staffordshire bullterrier", "staffordshire bull terrier", "pit bull", "pitbull", "american pit bull terrier"]),
        BreedInfo(name: "Dalmatian", size: .large, energy: .high, aliases: ["coach dog"]),
        BreedInfo(name: "Weimaraner", size: .large, energy: .high),
        BreedInfo(name: "Vizsla", size: .medium, energy: .high, aliases: ["hungarian pointer"]),
        BreedInfo(name: "German Shorthaired Pointer", size: .large, energy: .high, aliases: ["pointer"]),
        BreedInfo(name: "Basset Hound", size: .medium, energy: .low, aliases: ["basset"]),
        BreedInfo(name: "Bloodhound", size: .large, energy: .medium),
        BreedInfo(name: "Greyhound", size: .large, energy: .medium),
        BreedInfo(name: "Whippet", size: .medium, energy: .medium),
        BreedInfo(name: "Italian Greyhound", size: .small, energy: .medium),
        BreedInfo(name: "Chow Chow", size: .medium, energy: .low, aliases: ["chow"]),
        BreedInfo(name: "Shar Pei", size: .medium, energy: .low),
        BreedInfo(name: "Lhasa Apso", size: .small, energy: .low, aliases: ["lhasa"]),
        BreedInfo(name: "Papillon", size: .small, energy: .high),
        BreedInfo(name: "Havanese", size: .small, energy: .medium),
        BreedInfo(name: "Belgian Malinois", size: .large, energy: .high, aliases: ["malinois", "groenendael"]),
        BreedInfo(name: "Rhodesian Ridgeback", size: .large, energy: .high, aliases: ["ridgeback"]),
        BreedInfo(name: "Great Pyrenees", size: .giant, energy: .low, aliases: ["pyrenean mountain dog"]),
        BreedInfo(name: "Mastiff", size: .giant, energy: .low, aliases: ["bull mastiff", "bullmastiff", "tibetan mastiff", "english mastiff"]),
        BreedInfo(name: "Irish Setter", size: .large, energy: .high, aliases: ["red setter", "english setter", "gordon setter"]),
        BreedInfo(name: "Aspin", size: .medium, energy: .medium, aliases: ["asong pinoy"]),
    ]

    /// Finds a breed by name or alias, ignoring case, spaces, hyphens and underscores.
    public static func lookup(_ text: String) -> BreedInfo? {
        let key = normalize(text)
        guard !key.isEmpty else { return nil }
        return all.first { breed in
            normalize(breed.name) == key || breed.aliases.contains { normalize($0) == key }
        }
    }

    /// Breeds whose name starts with or contains the text, for suggestions while typing.
    public static func suggestions(for text: String, limit: Int = 5) -> [BreedInfo] {
        let key = normalize(text)
        guard !key.isEmpty else { return [] }
        let prefix = all.filter { normalize($0.name).hasPrefix(key) }
        let contains = all.filter { !normalize($0.name).hasPrefix(key) && normalize($0.name).contains(key) }
        return Array((prefix + contains).prefix(limit))
    }

    /// Picks the most likely breed from image classifier labels and their confidences.
    ///
    /// Labels look like "golden_retriever" or "Labrador retriever". Unknown labels are ignored,
    /// and guesses under `minimumConfidence` are dropped.
    public static func bestGuess(from labels: [(identifier: String, confidence: Double)], minimumConfidence: Double = 0.08) -> BreedGuess? {
        var best: BreedGuess?
        for label in labels where label.confidence >= minimumConfidence {
            guard let breed = lookup(label.identifier) else { continue }
            if best == nil || label.confidence > best!.confidence {
                best = BreedGuess(breed: breed, confidence: label.confidence)
            }
        }
        return best
    }

    static func normalize(_ text: String) -> String {
        text.lowercased()
            .replacingOccurrences(of: "_", with: " ")
            .replacingOccurrences(of: "-", with: " ")
            .replacingOccurrences(of: ".", with: "")
            .split(separator: " ")
            .joined(separator: " ")
    }
}
