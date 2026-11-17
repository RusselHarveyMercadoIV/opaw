package app.opaw.core

import kotlin.math.max
import kotlin.math.min

/** Aggregated stats the badge rules look at. */
data class BadgeStats(
    val totalCareEvents: Int = 0,
    val careStreakDays: Int = 0,
    val walkCount: Int = 0,
    val totalWalkKm: Double = 0.0,
    val longestWalkKm: Double = 0.0,
    val kmThisMonth: Double = 0.0,
    val bathCount: Int = 0,
    val memoryCount: Int = 0,
    val earlyWalkCount: Int = 0, // walks started before 7 am
    val walkStreakDays: Int = 0,
    val vetVisitCount: Int = 0,
    val friendsCount: Int = 0,
)

/** `symbol` is the iOS SF Symbol name; Android maps it to a drawable in the app module. */
enum class Badge(val raw: String, val title: String, val detail: String, val symbol: String) {
    FIRST_LOG("firstLog", "First Paw Print", "Log your first care event", "pawprint.fill"),
    CARE_STREAK_7("careStreak7", "Week of Love", "Log care 7 days in a row", "flame.fill"),
    CARE_STREAK_30("careStreak30", "Devoted Human", "Log care 30 days in a row", "flame.fill"),
    FIRST_WALK("firstWalk", "First Steps", "Finish your first tracked walk", "figure.walk"),
    WALKS_10("walks10", "Regular Rambler", "Finish 10 walks", "figure.walk"),
    WALKS_100("walks100", "Trail Legend", "Finish 100 walks", "figure.walk"),
    KM5_WALK("km5Walk", "5K Pup", "Walk 5 km in one go", "medal.fill"),
    KM10_WALK("km10Walk", "10K Hound", "Walk 10 km in one go", "medal.fill"),
    KM50_MONTH("km50Month", "50 km Month", "Walk 50 km in a calendar month", "trophy.fill"),
    KM100_TOTAL("km100Total", "Century Paws", "Walk 100 km in total", "trophy.fill"),
    EARLY_BIRD("earlyBird", "Early Bird", "Start 5 walks before 7 am", "sunrise.fill"),
    WALK_STREAK_7("walkStreak7", "Never Skip a Walk", "Walk 7 days in a row", "calendar"),
    SQUEAKY_CLEAN("squeakyClean", "Squeaky Clean", "Log 5 baths", "bubbles.and.sparkles.fill"),
    MEMORY_KEEPER("memoryKeeper", "Memory Keeper", "Save 10 memories", "photo.stack.fill"),
    HEALTH_CHECK("healthCheck", "Health Check", "Log a vet visit", "heart.text.square.fill"),
    PACK_LEADER("packLeader", "Pack Leader", "Make 5 friends in the pack", "person.3.fill");

    /** Progress toward the badge, from 0 to 1. */
    fun progress(s: BadgeStats): Double {
        fun ratio(value: Double, target: Double) = min(1.0, max(0.0, value / target))
        return when (this) {
            FIRST_LOG -> ratio(s.totalCareEvents.toDouble(), 1.0)
            CARE_STREAK_7 -> ratio(s.careStreakDays.toDouble(), 7.0)
            CARE_STREAK_30 -> ratio(s.careStreakDays.toDouble(), 30.0)
            FIRST_WALK -> ratio(s.walkCount.toDouble(), 1.0)
            WALKS_10 -> ratio(s.walkCount.toDouble(), 10.0)
            WALKS_100 -> ratio(s.walkCount.toDouble(), 100.0)
            KM5_WALK -> ratio(s.longestWalkKm, 5.0)
            KM10_WALK -> ratio(s.longestWalkKm, 10.0)
            KM50_MONTH -> ratio(s.kmThisMonth, 50.0)
            KM100_TOTAL -> ratio(s.totalWalkKm, 100.0)
            EARLY_BIRD -> ratio(s.earlyWalkCount.toDouble(), 5.0)
            WALK_STREAK_7 -> ratio(s.walkStreakDays.toDouble(), 7.0)
            SQUEAKY_CLEAN -> ratio(s.bathCount.toDouble(), 5.0)
            MEMORY_KEEPER -> ratio(s.memoryCount.toDouble(), 10.0)
            HEALTH_CHECK -> ratio(s.vetVisitCount.toDouble(), 1.0)
            PACK_LEADER -> ratio(s.friendsCount.toDouble(), 5.0)
        }
    }

    companion object {
        fun fromRaw(raw: String?): Badge? = entries.firstOrNull { it.raw == raw }
    }
}

object BadgeEngine {
    /** All badges whose requirements are met by these stats. */
    fun earned(stats: BadgeStats): Set<Badge> = Badge.entries.filter { it.progress(stats) >= 1 }.toSet()

    /** Badges newly earned compared to what the dog already has. */
    fun newlyEarned(stats: BadgeStats, alreadyEarned: Set<Badge>): List<Badge> {
        val earned = earned(stats)
        return Badge.entries.filter { it in earned && it !in alreadyEarned }
    }
}
