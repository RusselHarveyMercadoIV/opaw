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

final class CareScoreTests: XCTestCase {
    private var calendar: Calendar = {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(identifier: "UTC")!
        return calendar
    }()

    private func date(_ hour: Int, _ minute: Int = 0) -> Date {
        calendar.date(from: DateComponents(year: 2026, month: 11, day: 3, hour: hour, minute: minute))!
    }

    func testWellCaredForDogThrives() {
        let snapshot = CareSnapshot(
            lastEvent: [.meal: date(8), .water: date(12), .pee: date(13)],
            activityMinutesToday: 60,
            dailyActivityGoalMinutes: 60,
            mealHours: [8, 18]
        )
        let report = CareScore.report(for: snapshot, now: date(14), calendar: calendar)
        XCTAssertEqual(report.score, 100)
        XCTAssertEqual(report.tier, .thriving)
        XCTAssertTrue(report.needs.isEmpty)
    }

    func testOverdueMedsAndMealNeedYou() {
        let snapshot = CareSnapshot(
            lastEvent: [.meal: date(8), .water: date(17), .pee: date(17)],
            activityMinutesToday: 10,
            dailyActivityGoalMinutes: 60,
            mealHours: [8, 18]
        )
        let report = CareScore.report(for: snapshot, medsOverdue: true, now: date(19, 30), calendar: calendar)
        XCTAssertEqual(report.needs.first, .meds)
        XCTAssertTrue(report.needs.contains(.meal))
        XCTAssertTrue(report.needs.contains(.walk))
        XCTAssertEqual(report.tier, .needsYou)
        XCTAssertTrue(report.line.hasPrefix("My meds are late."))
    }

    func testTierBoundaries() {
        XCTAssertEqual(CareTier(score: 80), .thriving)
        XCTAssertEqual(CareTier(score: 79), .okay)
        XCTAssertEqual(CareTier(score: 50), .okay)
        XCTAssertEqual(CareTier(score: 49), .needsYou)
    }
}
