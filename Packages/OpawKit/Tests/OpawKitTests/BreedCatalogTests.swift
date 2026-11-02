import XCTest
@testable import OpawKit

final class BreedCatalogTests: XCTestCase {
    func testLookupIgnoresCaseAndUnderscores() {
        XCTAssertEqual(BreedCatalog.lookup("golden_retriever")?.name, "Golden Retriever")
        XCTAssertEqual(BreedCatalog.lookup("Labrador retriever")?.name, "Labrador Retriever")
        XCTAssertEqual(BreedCatalog.lookup("husky")?.name, "Siberian Husky")
        XCTAssertNil(BreedCatalog.lookup("tabby_cat"))
        XCTAssertNil(BreedCatalog.lookup(""))
    }

    func testBestGuessPicksHighestKnownBreed() {
        let labels: [(identifier: String, confidence: Double)] = [
            ("dog", 0.95),
            ("canine", 0.9),
            ("beagle", 0.31),
            ("basset", 0.42),
            ("pug", 0.02),
        ]
        let guess = BreedCatalog.bestGuess(from: labels)
        XCTAssertEqual(guess?.breed.name, "Basset Hound")
        XCTAssertEqual(guess?.confidence, 0.42)
    }

    func testBestGuessDropsLowConfidence() {
        XCTAssertNil(BreedCatalog.bestGuess(from: [("beagle", 0.03)]))
    }

    func testSuggestionsPreferPrefix() {
        let names = BreedCatalog.suggestions(for: "bo").map(\.name)
        XCTAssertEqual(names.first, "Boxer")
        XCTAssertTrue(names.contains("Border Collie"))
    }

    func testCatalogNamesAreUnique() {
        let names = BreedCatalog.all.map(\.name)
        XCTAssertEqual(Set(names).count, names.count)
    }
}
