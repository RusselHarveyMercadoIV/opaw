package app.opaw.core

import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/** The avatar's mood, derived from how the dog has been cared for. */
enum class AvatarMood(val raw: String, val caption: String, val clipName: String) {
    HAPPY("happy", "Feeling great", "wag"),
    HUNGRY("hungry", "Is it dinner time yet?", "hungry"),
    THIRSTY("thirsty", "Could use some water", "hungry"),
    RESTLESS("restless", "Let's go for a walk!", "excited"), // needs a walk
    SLEEPY("sleepy", "Zzz… cozy time", "sleepy"),
    FRESH("fresh", "Squeaky clean", "shake"), // just had a bath
    PROUD("proud", "Look what we did!", "celebrate"), // just earned a badge or finished a big walk
}

/** A snapshot of recent care, used to compute the mood. */
data class CareSnapshot(
    val lastEvent: Map<CareKind, Instant> = emptyMap(),
    val activityMinutesToday: Double = 0.0,
    val dailyActivityGoalMinutes: Double = 60.0,
    val lastBadgeEarnedAt: Instant? = null,
    /** Usual meal times as hours of the day, e.g. [8, 18]. */
    val mealHours: List<Int> = listOf(8, 18),
)

internal fun hoursBetween(from: Instant, to: Instant): Double = Duration.between(from, to).toMillis() / 3_600_000.0

object MoodEngine {
    /** Rules are checked in priority order; the first match wins. */
    fun mood(snapshot: CareSnapshot, now: Instant = Instant.now(), zone: ZoneId = ZoneId.systemDefault()): AvatarMood {
        fun hoursSince(kind: CareKind): Double? = snapshot.lastEvent[kind]?.let { hoursBetween(it, now) }

        snapshot.lastBadgeEarnedAt?.let { if (hoursBetween(it, now) < 1) return AvatarMood.PROUD }
        hoursSince(CareKind.BATH)?.let { if (it < 3) return AvatarMood.FRESH }
        val hour = now.atZone(zone).hour
        if (hour >= 22 || hour < 6) return AvatarMood.SLEEPY
        if (isMealOverdue(snapshot, now, zone)) return AvatarMood.HUNGRY
        hoursSince(CareKind.WATER)?.let { if (it > 8) return AvatarMood.THIRSTY }
        // After 4 pm, nudge for a walk if under half the daily goal.
        if (hour >= 16 && snapshot.activityMinutesToday < snapshot.dailyActivityGoalMinutes / 2) return AvatarMood.RESTLESS
        return AvatarMood.HAPPY
    }

    /** A meal is overdue when a scheduled meal hour passed more than an hour ago and nothing was logged since. */
    fun isMealOverdue(snapshot: CareSnapshot, now: Instant, zone: ZoneId): Boolean {
        val startOfDay = now.atZone(zone).toLocalDate().atStartOfDay(zone)
        val latestDue = snapshot.mealHours
            .map { startOfDay.plusHours(it.toLong()).toInstant() }
            .filter { hoursBetween(it, now) > 1 }
            .maxOrNull() ?: return false
        val lastMeal = snapshot.lastEvent[CareKind.MEAL] ?: return true
        return lastMeal < latestDue.minusSeconds(2 * 3600)
    }
}
