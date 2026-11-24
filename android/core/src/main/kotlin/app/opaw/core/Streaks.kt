package app.opaw.core

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

object StreakCalculator {
    /**
     * Number of consecutive days, ending today or yesterday, that have at least one date.
     *
     * A streak stays alive through today until midnight, so logging yesterday but not yet today still counts.
     */
    fun currentStreak(dates: List<Instant>, now: Instant = Instant.now(), zone: ZoneId = ZoneId.systemDefault()): Int {
        val days = dates.map { it.atZone(zone).toLocalDate() }.toSet()
        if (days.isEmpty()) return 0
        var day = now.atZone(zone).toLocalDate()
        if (day !in days) {
            val yesterday = day.minusDays(1)
            if (yesterday !in days) return 0
            day = yesterday
        }
        var streak = 0
        while (day in days) {
            streak += 1
            day = day.minusDays(1)
        }
        return streak
    }

    /** The longest run of consecutive days in the whole history. */
    fun longestStreak(dates: List<Instant>, zone: ZoneId = ZoneId.systemDefault()): Int {
        val days = dates.map { it.atZone(zone).toLocalDate() }.toSortedSet()
        var previous: LocalDate = days.firstOrNull() ?: return 0
        var longest = 1
        var current = 1
        for (day in days.drop(1)) {
            current = if (day == previous.plusDays(1)) current + 1 else 1
            longest = maxOf(longest, current)
            previous = day
        }
        return longest
    }
}
