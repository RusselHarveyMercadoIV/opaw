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

/** The three levels every badge goes through (design v5, 2g). */
enum class Tier(val raw: String, val title: String) {
    BRONZE("bronze", "Bronze"),
    SILVER("silver", "Silver"),
    GOLD("gold", "Gold");

    companion object {
        fun fromRaw(raw: String?): Tier? = entries.firstOrNull { it.raw == raw }
    }
}

/**
 * A badge that levels up bronze → silver → gold (design v5, 2f and 2g).
 * [targets] are the counts for each tier, in [unit]; [art] names the medal art (`badge_<art>_<tier>`).
 */
enum class Badge(
    val raw: String,
    val title: String,
    val art: String,
    val targets: List<Double>,
    private val goalText: (Double) -> String,
    private val stat: (BadgeStats) -> Double,
) {
    PAW_PRINTS("pawPrints", "Paw Prints", "paw_prints", listOf(10.0, 100.0, 500.0), { "Log care ${it.count} times" }, { it.totalCareEvents.toDouble() }),
    DEVOTED("devoted", "Devoted", "care_streak", listOf(7.0, 30.0, 100.0), { "Log care ${it.count} days in a row" }, { it.careStreakDays.toDouble() }),
    RAMBLER("rambler", "Rambler", "walks", listOf(10.0, 50.0, 200.0), { "Finish ${it.count} walks" }, { it.walkCount.toDouble() }),
    TRAIL_LEGEND("trailLegend", "Trail Legend", "long_walk", listOf(3.0, 5.0, 10.0), { "Walk ${it.count} km in one go" }, { it.longestWalkKm }),
    CENTURY_PAWS("centuryPaws", "Century Paws", "distance", listOf(25.0, 100.0, 500.0), { "Walk ${it.count} km in total" }, { it.totalWalkKm }),
    BUSY_MONTH("busyMonth", "Busy Month", "busy_month", listOf(20.0, 50.0, 100.0), { "Walk ${it.count} km in one month" }, { it.kmThisMonth }),
    EARLY_BIRD("earlyBird", "Early Bird", "early_bird", listOf(5.0, 20.0, 50.0), { "Start ${it.count} walks before 7 am" }, { it.earlyWalkCount.toDouble() }),
    NEVER_SKIP("neverSkip", "Never Skip", "walk_streak", listOf(7.0, 30.0, 100.0), { "Walk ${it.count} days in a row" }, { it.walkStreakDays.toDouble() }),
    SQUEAKY_CLEAN("squeakyClean", "Squeaky Clean", "squeaky_clean", listOf(5.0, 15.0, 30.0), { "Log ${it.count} baths" }, { it.bathCount.toDouble() }),
    MEMORY_KEEPER("memoryKeeper", "Memory Keeper", "memory_keeper", listOf(10.0, 50.0, 200.0), { "Save ${it.count} memories" }, { it.memoryCount.toDouble() }),
    HEALTH_CHECK("healthCheck", "Health Check", "health_check", listOf(1.0, 3.0, 10.0), { if (it == 1.0) "Log a vet visit" else "Log ${it.count} vet visits" }, { it.vetVisitCount.toDouble() }),
    PACK_LEADER("packLeader", "Pack Leader", "pack_leader", listOf(5.0, 15.0, 30.0), { "Make ${it.count} friends in the pack" }, { it.friendsCount.toDouble() });

    fun value(s: BadgeStats): Double = stat(s)
    fun target(tier: Tier): Double = targets[tier.ordinal]
    fun goal(tier: Tier): String = goalText(target(tier))

    /** The highest tier these stats reach, or null before bronze. */
    fun tier(s: BadgeStats): Tier? = Tier.entries.lastOrNull { value(s) >= target(it) }

    /** The next tier to earn, or null once gold. */
    fun nextTier(s: BadgeStats): Tier? = Tier.entries.firstOrNull { value(s) < target(it) }

    /** Progress toward the next tier (1 once gold), from 0 to 1. */
    fun progress(s: BadgeStats): Double {
        val next = nextTier(s) ?: return 1.0
        return min(1.0, max(0.0, value(s) / target(next)))
    }

    fun level(tier: Tier) = BadgeLevel(this, tier)

    companion object {
        fun fromRaw(raw: String?): Badge? = entries.firstOrNull { it.raw == raw }
    }
}

private val Double.count: String get() = if (this % 1.0 == 0.0) toLong().toString() else toString()

/** One tier of one badge, e.g. Rambler, silver. Stored as `rambler.silver`. */
data class BadgeLevel(val badge: Badge, val tier: Tier) {
    val raw: String get() = "${badge.raw}.${tier.raw}"
    /** "Rambler · Silver" */
    val title: String get() = "${badge.title} · ${tier.title}"
    /** What earned it: "Finish 50 walks". */
    val detail: String get() = badge.goal(tier)
    /** The medal art: `badge_walks_silver`. */
    val art: String get() = "badge_${badge.art}_${tier.raw}"

    companion object {
        fun fromRaw(raw: String?): BadgeLevel? {
            val parts = raw?.split('.') ?: return null
            if (parts.size != 2) return null
            val badge = Badge.fromRaw(parts[0]) ?: return null
            val tier = Tier.fromRaw(parts[1]) ?: return null
            return BadgeLevel(badge, tier)
        }
    }
}

/** The highest tier held for each badge in a set of earned levels. */
fun Set<BadgeLevel>.topTiers(): Map<Badge, Tier> =
    groupBy { it.badge }.mapValues { (_, levels) -> levels.maxOf { it.tier } }

object BadgeEngine {
    /** Every level these stats reach (a silver badge holds bronze too). */
    fun earned(stats: BadgeStats): Set<BadgeLevel> =
        Badge.entries.flatMap { badge -> Tier.entries.filter { badge.value(stats) >= badge.target(it) }.map(badge::level) }.toSet()

    /** Levels newly earned compared to what the pet already has, all of them, in badge then tier order. */
    fun newlyEarned(stats: BadgeStats, alreadyEarned: Set<BadgeLevel>): List<BadgeLevel> =
        earned(stats).filter { it !in alreadyEarned }.sortedWith(compareBy({ it.badge.ordinal }, { it.tier.ordinal }))

    /** The one to celebrate: the highest new tier, first badge first. */
    fun headline(new: List<BadgeLevel>): BadgeLevel? = new.maxWithOrNull(compareBy<BadgeLevel> { it.tier.ordinal }.thenByDescending { it.badge.ordinal })
}
