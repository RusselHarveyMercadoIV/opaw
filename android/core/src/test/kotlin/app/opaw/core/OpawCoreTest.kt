package app.opaw.core

import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

// Mirrors Packages/OpawKit/Tests/OpawKitTests. Keep both in step so the two apps agree.

private val utc: ZoneId = ZoneOffset.UTC

private fun date(day: Int, hour: Int = 12, minute: Int = 0): Instant =
    ZonedDateTime.of(2026, 11, day, hour, minute, 0, 0, utc).toInstant()

class BreedCatalogTest {
    @Test fun lookupIgnoresCaseAndUnderscores() {
        assertEquals("Golden Retriever", BreedCatalog.lookup("golden_retriever")?.name)
        assertEquals("Labrador Retriever", BreedCatalog.lookup("Labrador retriever")?.name)
        assertEquals("Siberian Husky", BreedCatalog.lookup("husky")?.name)
        assertNull(BreedCatalog.lookup("tabby_cat"))
        assertNull(BreedCatalog.lookup(""))
    }

    @Test fun bestGuessPicksHighestKnownBreed() {
        val labels = listOf("dog" to 0.95, "canine" to 0.9, "beagle" to 0.31, "basset" to 0.42, "pug" to 0.02)
        val guess = BreedCatalog.bestGuess(labels)
        assertEquals("Basset Hound", guess?.breed?.name)
        assertEquals(0.42, guess?.confidence)
    }

    @Test fun bestGuessDropsLowConfidence() {
        assertNull(BreedCatalog.bestGuess(listOf("beagle" to 0.03)))
    }

    @Test fun suggestionsPreferPrefix() {
        val names = BreedCatalog.suggestions("bo").map { it.name }
        assertEquals("Boxer", names.first())
        assertTrue("Border Collie" in names)
    }

    @Test fun catalogNamesAreUnique() {
        val names = BreedCatalog.all.map { it.name }
        assertEquals(names.size, names.toSet().size)
    }
}

class CareScoreTest {
    private fun at(hour: Int, minute: Int = 0) = date(3, hour, minute)

    @Test fun wellCaredForDogThrives() {
        val snapshot = CareSnapshot(
            lastEvent = mapOf(CareKind.MEAL to at(8), CareKind.WATER to at(12), CareKind.PEE to at(13)),
            activityMinutesToday = 60.0,
            dailyActivityGoalMinutes = 60.0,
            mealHours = listOf(8, 18),
        )
        val report = CareScore.report(snapshot, now = at(14), zone = utc)
        assertEquals(100, report.score)
        assertEquals(CareTier.THRIVING, report.tier)
        assertTrue(report.needs.isEmpty())
    }

    @Test fun overdueMedsAndMealNeedYou() {
        val snapshot = CareSnapshot(
            lastEvent = mapOf(CareKind.MEAL to at(8), CareKind.WATER to at(17), CareKind.PEE to at(17)),
            activityMinutesToday = 10.0,
            dailyActivityGoalMinutes = 60.0,
            mealHours = listOf(8, 18),
        )
        val report = CareScore.report(snapshot, medsOverdue = true, now = at(19, 30), zone = utc)
        assertEquals(CareNeed.MEDS, report.needs.first())
        assertTrue(CareNeed.MEAL in report.needs)
        assertTrue(CareNeed.WALK in report.needs)
        assertEquals(CareTier.NEEDS_YOU, report.tier)
        assertTrue(report.line.startsWith("My meds are late."))
    }

    @Test fun tierBoundaries() {
        assertEquals(CareTier.THRIVING, CareTier.of(80))
        assertEquals(CareTier.OKAY, CareTier.of(79))
        assertEquals(CareTier.OKAY, CareTier.of(50))
        assertEquals(CareTier.NEEDS_YOU, CareTier.of(49))
    }
}

class StreakTest {
    @Test fun emptyHistoryHasNoStreak() {
        assertEquals(0, StreakCalculator.currentStreak(emptyList(), now = date(10), zone = utc))
    }

    @Test fun streakEndingToday() {
        val dates = listOf(date(8), date(9), date(10, 8), date(10, 20))
        assertEquals(3, StreakCalculator.currentStreak(dates, now = date(10, 21), zone = utc))
    }

    @Test fun streakStillAliveIfLoggedYesterday() {
        assertEquals(2, StreakCalculator.currentStreak(listOf(date(8), date(9)), now = date(10, 9), zone = utc))
    }

    @Test fun streakBrokenAfterMissedDay() {
        assertEquals(0, StreakCalculator.currentStreak(listOf(date(6), date(7)), now = date(10), zone = utc))
    }

    @Test fun longestStreak() {
        val dates = listOf(date(1), date(2), date(3), date(5), date(6), date(1, 18))
        assertEquals(3, StreakCalculator.longestStreak(dates, zone = utc))
    }
}

class MoodTest {
    @Test fun hungryWhenMealOverdue() {
        val snapshot = CareSnapshot(
            lastEvent = mapOf(CareKind.MEAL to date(10, 7), CareKind.WATER to date(10, 17)),
            activityMinutesToday = 60.0,
            mealHours = listOf(8, 18),
        )
        assertEquals(AvatarMood.HUNGRY, MoodEngine.mood(snapshot, now = date(10, 19, 30), zone = utc))
    }

    @Test fun happyWhenFedAndWalked() {
        val snapshot = CareSnapshot(
            lastEvent = mapOf(CareKind.MEAL to date(10, 18), CareKind.WATER to date(10, 17)),
            activityMinutesToday = 60.0,
            mealHours = listOf(8, 18),
        )
        assertEquals(AvatarMood.HAPPY, MoodEngine.mood(snapshot, now = date(10, 19, 30), zone = utc))
    }

    @Test fun restlessInEveningWithoutWalk() {
        val snapshot = CareSnapshot(
            lastEvent = mapOf(CareKind.MEAL to date(10, 8), CareKind.WATER to date(10, 15)),
            activityMinutesToday = 5.0,
            dailyActivityGoalMinutes = 60.0,
            mealHours = listOf(8),
        )
        assertEquals(AvatarMood.RESTLESS, MoodEngine.mood(snapshot, now = date(10, 17), zone = utc))
    }

    @Test fun freshAfterBath() {
        val snapshot = CareSnapshot(lastEvent = mapOf(CareKind.BATH to date(10, 11)), mealHours = emptyList())
        assertEquals(AvatarMood.FRESH, MoodEngine.mood(snapshot, now = date(10, 12), zone = utc))
    }

    @Test fun proudAfterBadge() {
        val snapshot = CareSnapshot(lastBadgeEarnedAt = date(10, 11, 45), mealHours = emptyList())
        assertEquals(AvatarMood.PROUD, MoodEngine.mood(snapshot, now = date(10, 12), zone = utc))
    }

    @Test fun sleepyAtNight() {
        val snapshot = CareSnapshot(lastEvent = mapOf(CareKind.MEAL to date(10, 18)), mealHours = listOf(18))
        assertEquals(AvatarMood.SLEEPY, MoodEngine.mood(snapshot, now = date(10, 23), zone = utc))
    }
}

class BadgeTest {
    @Test fun firstLogAndWalk() {
        val earned = BadgeEngine.earned(BadgeStats(totalCareEvents = 1, walkCount = 1, totalWalkKm = 1.2, longestWalkKm = 1.2))
        assertTrue(Badge.FIRST_LOG in earned)
        assertTrue(Badge.FIRST_WALK in earned)
        assertFalse(Badge.KM5_WALK in earned)
    }

    @Test fun newlyEarnedExcludesExisting() {
        val new = BadgeEngine.newlyEarned(BadgeStats(totalCareEvents = 3, careStreakDays = 7), alreadyEarned = setOf(Badge.FIRST_LOG))
        assertEquals(listOf(Badge.CARE_STREAK_7), new)
    }

    @Test fun progressIsClamped() {
        assertEquals(0.5, Badge.WALKS_10.progress(BadgeStats(walkCount = 5)), 0.0001)
        assertEquals(1.0, Badge.WALKS_10.progress(BadgeStats(walkCount = 50)))
    }
}

class WalkTest {
    private fun point(lat: Double, lon: Double, seconds: Double, accuracy: Double = 5.0) =
        GeoPoint(lat, lon, date(10).plusMillis((seconds * 1000).toLong()), accuracy)

    @Test fun haversineOneDegreeLatitude() {
        assertEquals(111_195.0, Geo.distance(0.0, 0.0, 1.0, 0.0), 100.0)
    }

    @Test fun statsForStraightWalk() {
        // About 11 m per 0.0001 degree of latitude, one sample every 10 s => ~1.1 m/s.
        val points = (0..100).map { point(14.5 + it * 0.0001, 121.0, it * 10.0) }
        val stats = WalkCalculator.stats(points)
        assertEquals(1112.0, stats.distanceMeters, 15.0)
        assertEquals(1000.0, stats.elapsedSeconds, 0.001)
        assertEquals(1000.0, stats.movingSeconds, 0.001)
        assertNotNull(stats.paceMinutesPerKm)
    }

    @Test fun dropsInaccurateAndJumpingPoints() {
        val points = listOf(
            point(14.5, 121.0, 0.0),
            point(14.5001, 121.0, 10.0),
            point(14.6, 121.0, 20.0), // 11 km jump in 10 s
            point(14.5002, 121.0, 30.0, accuracy = 100.0), // inaccurate
            point(14.5002, 121.0, 40.0),
        )
        assertEquals(3, WalkCalculator.clean(points).size)
    }

    @Test fun pauseIsNotMovingTime() {
        val points = listOf(
            point(14.5, 121.0, 0.0),
            point(14.5001, 121.0, 10.0),
            point(14.5002, 121.0, 310.0), // five-minute gap
            point(14.5003, 121.0, 320.0),
        )
        val stats = WalkCalculator.stats(points)
        assertEquals(20.0, stats.movingSeconds, 0.001)
        assertEquals(320.0, stats.elapsedSeconds, 0.001)
    }
}

class PrivacyTest {
    @Test fun coarsenIsStableWithinCell() {
        val a = LocationPrivacy.coarsen(14.55001, 121.02001)
        val b = LocationPrivacy.coarsen(14.55010, 121.02010)
        assertEquals(a.first, b.first, 1e-12)
        assertEquals(a.second, b.second, 1e-12)
    }

    @Test fun coarsenMovesLessThanGrid() {
        val snapped = LocationPrivacy.coarsen(14.5547, 121.0244, gridMeters = 500.0)
        assertTrue(Geo.distance(14.5547, 121.0244, snapped.first, snapped.second) < 500)
    }

    @Test fun trimRouteEnds() {
        val start = date(10)
        val points = (0..40).map { GeoPoint(14.5 + it * 0.0001, 121.0, start.plusSeconds(it * 10L)) }
        val trimmed = LocationPrivacy.trimRouteEnds(points, radiusMeters = 100.0)
        assertFalse(trimmed.isEmpty())
        assertTrue(Geo.distance(trimmed.first(), points.first()) > 100)
        assertTrue(Geo.distance(trimmed.last(), points.last()) > 100)
    }
}

class TraitsAndFormatTest {
    @Test fun puppyGoalIsCapped() {
        val puppy = DogTraits(DogSize.LARGE, EnergyLevel.HIGH, 0.25)
        assertEquals(LifeStage.PUPPY, puppy.lifeStage)
        assertTrue(puppy.recommendedDailyActivityMinutes <= 30)
    }

    @Test fun seniorGoalIsLowerThanAdult() {
        val adult = DogTraits(DogSize.MEDIUM, EnergyLevel.MEDIUM, 4.0)
        val senior = DogTraits(DogSize.MEDIUM, EnergyLevel.MEDIUM, 11.0)
        assertEquals(60, adult.recommendedDailyActivityMinutes)
        assertTrue(senior.recommendedDailyActivityMinutes < adult.recommendedDailyActivityMinutes)
    }

    @Test fun formatting() {
        assertEquals("850 m", OpawFormat.distance(850.0, imperial = false))
        assertEquals("1.24 km", OpawFormat.distance(1240.0, imperial = false))
        assertEquals("1.00 mi", OpawFormat.distance(1609.344, imperial = true))
        assertEquals("12:05", OpawFormat.duration(725.0))
        assertEquals("1:05:09", OpawFormat.duration(3909.0))
        assertEquals("12'30\" /km", OpawFormat.pace(12.5, imperial = false))
        assertEquals("--", OpawFormat.pace(null))
    }
}
