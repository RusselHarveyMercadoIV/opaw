import XCTest
@testable import OpawKit

private var utc: Calendar = {
    var calendar = Calendar(identifier: .gregorian)
    calendar.timeZone = TimeZone(identifier: "UTC")!
    return calendar
}()

private func date(_ day: Int, _ hour: Int = 12, _ minute: Int = 0) -> Date {
    utc.date(from: DateComponents(year: 2026, month: 11, day: day, hour: hour, minute: minute))!
}

final class StreakTests: XCTestCase {
    func testEmptyHistoryHasNoStreak() {
        XCTAssertEqual(StreakCalculator.currentStreak(dates: [], now: date(10), calendar: utc), 0)
    }

    func testStreakEndingToday() {
        let dates = [date(8), date(9), date(10, 8), date(10, 20)]
        XCTAssertEqual(StreakCalculator.currentStreak(dates: dates, now: date(10, 21), calendar: utc), 3)
    }

    func testStreakStillAliveIfLoggedYesterday() {
        let dates = [date(8), date(9)]
        XCTAssertEqual(StreakCalculator.currentStreak(dates: dates, now: date(10, 9), calendar: utc), 2)
    }

    func testStreakBrokenAfterMissedDay() {
        let dates = [date(6), date(7)]
        XCTAssertEqual(StreakCalculator.currentStreak(dates: dates, now: date(10), calendar: utc), 0)
    }

    func testLongestStreak() {
        let dates = [date(1), date(2), date(3), date(5), date(6), date(1, 18)]
        XCTAssertEqual(StreakCalculator.longestStreak(dates: dates, calendar: utc), 3)
    }
}

final class MoodTests: XCTestCase {
    func testHungryWhenMealOverdue() {
        let snapshot = CareSnapshot(lastEvent: [.meal: date(10, 7), .water: date(10, 17)], activityMinutesToday: 60, mealHours: [8, 18])
        XCTAssertEqual(MoodEngine.mood(for: snapshot, now: date(10, 19, 30), calendar: utc), .hungry)
    }

    func testHappyWhenFedAndWalked() {
        let snapshot = CareSnapshot(lastEvent: [.meal: date(10, 18), .water: date(10, 17)], activityMinutesToday: 60, mealHours: [8, 18])
        XCTAssertEqual(MoodEngine.mood(for: snapshot, now: date(10, 19, 30), calendar: utc), .happy)
    }

    func testRestlessInEveningWithoutWalk() {
        let snapshot = CareSnapshot(lastEvent: [.meal: date(10, 8), .water: date(10, 15)], activityMinutesToday: 5, dailyActivityGoalMinutes: 60, mealHours: [8])
        XCTAssertEqual(MoodEngine.mood(for: snapshot, now: date(10, 17), calendar: utc), .restless)
    }

    func testFreshAfterBath() {
        let snapshot = CareSnapshot(lastEvent: [.bath: date(10, 11)], mealHours: [])
        XCTAssertEqual(MoodEngine.mood(for: snapshot, now: date(10, 12), calendar: utc), .fresh)
    }

    func testProudAfterBadge() {
        let snapshot = CareSnapshot(lastBadgeEarnedAt: date(10, 11, 45), mealHours: [])
        XCTAssertEqual(MoodEngine.mood(for: snapshot, now: date(10, 12), calendar: utc), .proud)
    }

    func testSleepyAtNight() {
        let snapshot = CareSnapshot(lastEvent: [.meal: date(10, 18)], mealHours: [18])
        XCTAssertEqual(MoodEngine.mood(for: snapshot, now: date(10, 23), calendar: utc), .sleepy)
    }
}

final class BadgeTests: XCTestCase {
    func testFirstLogAndWalk() {
        let stats = BadgeStats(totalCareEvents: 1, walkCount: 1, totalWalkKm: 1.2, longestWalkKm: 1.2)
        let earned = BadgeEngine.earned(stats)
        XCTAssertTrue(earned.contains(.firstLog))
        XCTAssertTrue(earned.contains(.firstWalk))
        XCTAssertFalse(earned.contains(.km5Walk))
    }

    func testNewlyEarnedExcludesExisting() {
        let stats = BadgeStats(totalCareEvents: 3, careStreakDays: 7)
        let new = BadgeEngine.newlyEarned(stats, alreadyEarned: [.firstLog])
        XCTAssertEqual(new, [.careStreak7])
    }

    func testProgressIsClamped() {
        XCTAssertEqual(Badge.walks10.progress(BadgeStats(walkCount: 5)), 0.5, accuracy: 0.0001)
        XCTAssertEqual(Badge.walks10.progress(BadgeStats(walkCount: 50)), 1)
    }
}

final class WalkTests: XCTestCase {
    private func point(_ lat: Double, _ lon: Double, _ seconds: TimeInterval, accuracy: Double = 5) -> GeoPoint {
        GeoPoint(latitude: lat, longitude: lon, timestamp: date(10).addingTimeInterval(seconds), horizontalAccuracy: accuracy)
    }

    func testHaversineOneDegreeLatitude() {
        let d = Geo.distance(lat1: 0, lon1: 0, lat2: 1, lon2: 0)
        XCTAssertEqual(d, 111_195, accuracy: 100)
    }

    func testStatsForStraightWalk() {
        // About 11 m per 0.0001 degree of latitude, one sample every 10 s => ~1.1 m/s.
        let points = (0...100).map { point(14.5 + Double($0) * 0.0001, 121.0, Double($0) * 10) }
        let stats = WalkCalculator.stats(for: points)
        XCTAssertEqual(stats.distanceMeters, 1112, accuracy: 15)
        XCTAssertEqual(stats.elapsedSeconds, 1000, accuracy: 0.001)
        XCTAssertEqual(stats.movingSeconds, 1000, accuracy: 0.001)
        XCTAssertNotNil(stats.paceMinutesPerKm)
    }

    func testDropsInaccurateAndJumpingPoints() {
        let points = [
            point(14.5, 121.0, 0),
            point(14.5001, 121.0, 10),
            point(14.6, 121.0, 20),               // 11 km jump in 10 s
            point(14.5002, 121.0, 30, accuracy: 100), // inaccurate
            point(14.5002, 121.0, 40)
        ]
        let cleaned = WalkCalculator.clean(points)
        XCTAssertEqual(cleaned.count, 3)
    }

    func testPauseIsNotMovingTime() {
        let points = [
            point(14.5, 121.0, 0),
            point(14.5001, 121.0, 10),
            point(14.5002, 121.0, 310),  // five-minute gap
            point(14.5003, 121.0, 320)
        ]
        let stats = WalkCalculator.stats(for: points)
        XCTAssertEqual(stats.movingSeconds, 20, accuracy: 0.001)
        XCTAssertEqual(stats.elapsedSeconds, 320, accuracy: 0.001)
    }
}

final class PrivacyTests: XCTestCase {
    func testCoarsenIsStableWithinCell() {
        let a = LocationPrivacy.coarsen(latitude: 14.55001, longitude: 121.02001)
        let b = LocationPrivacy.coarsen(latitude: 14.55010, longitude: 121.02010)
        XCTAssertEqual(a.latitude, b.latitude, accuracy: 1e-12)
        XCTAssertEqual(a.longitude, b.longitude, accuracy: 1e-12)
    }

    func testCoarsenMovesLessThanGrid() {
        let original = (lat: 14.5547, lon: 121.0244)
        let snapped = LocationPrivacy.coarsen(latitude: original.lat, longitude: original.lon, gridMeters: 500)
        let moved = Geo.distance(lat1: original.lat, lon1: original.lon, lat2: snapped.latitude, lon2: snapped.longitude)
        XCTAssertLessThan(moved, 500)
    }

    func testTrimRouteEnds() {
        let start = date(10)
        let points = (0...40).map { i in
            GeoPoint(latitude: 14.5 + Double(i) * 0.0001, longitude: 121.0, timestamp: start.addingTimeInterval(Double(i) * 10))
        }
        let trimmed = LocationPrivacy.trimRouteEnds(points, radiusMeters: 100)
        XCTAssertFalse(trimmed.isEmpty)
        XCTAssertGreaterThan(Geo.distance(trimmed.first!, points.first!), 100)
        XCTAssertGreaterThan(Geo.distance(trimmed.last!, points.last!), 100)
    }
}

final class TraitsAndFormatTests: XCTestCase {
    func testPuppyGoalIsCapped() {
        let puppy = DogTraits(size: .large, energy: .high, ageYears: 0.25)
        XCTAssertEqual(puppy.lifeStage, .puppy)
        XCTAssertLessThanOrEqual(puppy.recommendedDailyActivityMinutes, 30)
    }

    func testSeniorGoalIsLowerThanAdult() {
        let adult = DogTraits(size: .medium, energy: .medium, ageYears: 4)
        let senior = DogTraits(size: .medium, energy: .medium, ageYears: 11)
        XCTAssertEqual(adult.recommendedDailyActivityMinutes, 60)
        XCTAssertLessThan(senior.recommendedDailyActivityMinutes, adult.recommendedDailyActivityMinutes)
    }

    func testFormatting() {
        XCTAssertEqual(OpawFormat.distance(meters: 850), "850 m")
        XCTAssertEqual(OpawFormat.distance(meters: 1240), "1.24 km")
        XCTAssertEqual(OpawFormat.duration(725), "12:05")
        XCTAssertEqual(OpawFormat.duration(3909), "1:05:09")
        XCTAssertEqual(OpawFormat.pace(minutesPerKm: 12.5), "12'30\" /km")
        XCTAssertEqual(OpawFormat.pace(minutesPerKm: nil), "--")
    }
}
