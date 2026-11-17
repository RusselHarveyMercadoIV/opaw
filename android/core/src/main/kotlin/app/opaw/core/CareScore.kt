package app.opaw.core

import java.time.Instant
import java.time.ZoneId
import kotlin.math.max
import kotlin.math.min

/** How the avatar is doing overall, from 0 to 100. */
enum class CareTier(val title: String, val sound: String) {
    THRIVING("Thriving", "woof!"), // 80–100
    OKAY("Okay", "hmm…"), // 50–79
    NEEDS_YOU("Needs you", "whine"); // 0–49

    companion object {
        fun of(score: Int): CareTier = when {
            score >= 80 -> THRIVING
            score >= 50 -> OKAY
            else -> NEEDS_YOU
        }
    }
}

/** Something the dog is waiting on. */
enum class CareNeed(val line: String, internal val penalty: Int) {
    MEDS("My meds are late.", 30),
    MEAL("Is it dinner time yet?", 25),
    WATER("The water bowl is looking empty.", 12),
    POTTY("I really need to go out.", 12),
    WALK("Bit bored. Nobody's logged a walk today.", 20),
}

data class CareReport(val score: Int, val needs: List<CareNeed>) {
    val tier: CareTier get() = CareTier.of(score)

    /** What the avatar says, in its own voice. */
    val line: String
        get() = when (needs.size) {
            0 -> "Feeling great. Walk later?"
            1 -> needs[0].line
            else -> "${needs[0].line} ${needs[1].line}"
        }
}

object CareScore {
    /**
     * Scores today's care. Needs are listed most urgent first.
     *
     * @param medsOverdue whether a meds reminder passed more than 30 minutes ago without a log.
     */
    fun report(
        snapshot: CareSnapshot,
        medsOverdue: Boolean = false,
        now: Instant = Instant.now(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): CareReport {
        val needs = mutableListOf<CareNeed>()
        fun hoursSince(kind: CareKind): Double =
            snapshot.lastEvent[kind]?.let { hoursBetween(it, now) } ?: Double.POSITIVE_INFINITY
        val hour = now.atZone(zone).hour
        val awake = hour in 7 until 22

        if (medsOverdue) needs += CareNeed.MEDS
        if (MoodEngine.isMealOverdue(snapshot, now, zone)) needs += CareNeed.MEAL
        if (awake && hoursSince(CareKind.WATER) > 8) needs += CareNeed.WATER
        if (awake && hoursSince(CareKind.PEE) > 7) needs += CareNeed.POTTY
        if (hour >= 15 && snapshot.activityMinutesToday < snapshot.dailyActivityGoalMinutes / 2) needs += CareNeed.WALK

        var score = 100 - needs.sumOf { it.penalty }
        // Reward progress toward the activity goal a little, so a good walk shows.
        val goal = max(1.0, snapshot.dailyActivityGoalMinutes)
        val progress = min(1.0, snapshot.activityMinutesToday / goal)
        score = score - 8 + roundHalfAway(progress * 8).toInt()
        return CareReport(score.coerceIn(0, 100), needs)
    }
}
