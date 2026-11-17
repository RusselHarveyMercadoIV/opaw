package app.opaw.data

import app.opaw.core.Badge
import app.opaw.core.CareKind
import app.opaw.core.OpawFormat
import app.opaw.core.PoopColor
import java.text.DecimalFormat
import java.time.ZoneId

// Display text shared by many screens. Mirrors CareLogging.swift, HomeView.swift, WalkViews.swift
// and ChallengesView.swift.

/** A short label that fits under a circle. */
val CareKind.shortTitle: String get() = title

/** Care points earned for a log, shown as "+4" in the day's log. */
val CareKind.points: Int
    get() = when (this) {
        CareKind.MEAL -> 4
        CareKind.MEDS, CareKind.VET -> 5
        CareKind.BATH -> 3
        CareKind.POOP, CareKind.BRUSH, CareKind.NAILS, CareKind.PLAY, CareKind.CUSTOM -> 2
        CareKind.WATER, CareKind.PEE, CareKind.WEIGHT -> 1
    }

/** What the avatar says right after this is logged. */
val CareKind.reaction: String
    get() = when (this) {
        CareKind.MEAL -> "Yum. Seconds?"
        CareKind.WATER -> "Ahh, refreshing."
        CareKind.PEE -> "Much better."
        CareKind.POOP -> "Ahh, that's the spot."
        CareKind.BATH -> "Squeaky clean. Hmph."
        CareKind.BRUSH -> "So fluffy now."
        CareKind.NAILS -> "No more click-clack."
        CareKind.MEDS -> "Not my favourite, but thanks."
        CareKind.VET -> "Brave dog award, please."
        CareKind.WEIGHT -> "Don't tell me the number."
        CareKind.PLAY -> "Again! Again!"
        CareKind.CUSTOM -> "Noted. Good human."
    }

/** Points for a walk: six for any walk, as in the design. */
const val WALK_POINTS = 6

private val upToTwo = DecimalFormat("0.##")

/** "Poop · normal", "Weight · 9.8 kg", "Breakfast · 120 g". */
val CareEvent.summary: String
    get() {
        var text = displayTitle
        amount?.let { text += " · ${upToTwo.format(it)}" }
        poopConsistency?.let { text += " · ${it.title.lowercase()}" }
        poopColor?.let { if (it != PoopColor.BROWN) text += " · ${it.title.lowercase()}" }
        weightKg?.let { text += " · ${OpawFormat.weight(it)}" }
        if (note.isNotEmpty()) text += " · $note"
        return text
    }

/** The display name for who logged something. */
fun loggerName(loggedBy: String): String = loggedBy.ifEmpty { "You" }

enum class WalkActivity(val raw: String, val title: String) {
    WALK("walk", "Walk"), RUN("run", "Run"), HIKE("hike", "Hike");

    companion object {
        fun fromRaw(raw: String?) = entries.firstOrNull { it.raw == raw } ?: WALK
    }
}

/** "Morning walk", "Evening loop"… from when it started. */
val Walk.title: String get() = walkTitle(startedAt, WalkActivity.fromRaw(activityRaw))

fun walkTitle(date: java.time.Instant, activity: WalkActivity = WalkActivity.WALK): String {
    val hour = date.atZone(ZoneId.systemDefault()).hour
    return when (activity) {
        WalkActivity.WALK -> when (hour) {
            in 5 until 11 -> "Morning walk"
            in 11 until 14 -> "Midday stroll"
            in 14 until 17 -> "Afternoon walk"
            in 17 until 21 -> "Evening loop"
            else -> "Night stroll"
        }
        WalkActivity.RUN, WalkActivity.HIKE -> {
            val part = when (hour) {
                in 5 until 12 -> "Morning"
                in 12 until 17 -> "Afternoon"
                in 17 until 21 -> "Evening"
                else -> "Night"
            }
            "$part ${activity.title.lowercase()}"
        }
    }
}

/** The big text on the medal. */
val Badge.medal: String
    get() = when (this) {
        Badge.FIRST_LOG, Badge.FIRST_WALK -> "1st"
        Badge.CARE_STREAK_7, Badge.WALK_STREAK_7 -> "7"
        Badge.CARE_STREAK_30 -> "30"
        Badge.WALKS_10, Badge.MEMORY_KEEPER -> "10"
        Badge.WALKS_100, Badge.KM100_TOTAL -> "100"
        Badge.KM5_WALK -> "5K"
        Badge.KM10_WALK -> "10K"
        Badge.KM50_MONTH -> "50"
        Badge.EARLY_BIRD -> "7am"
        Badge.SQUEAKY_CLEAN, Badge.PACK_LEADER -> "5"
        Badge.HEALTH_CHECK -> "Vet"
    }

/** The caption under the medal. */
val Badge.medalLabel: String
    get() = when (this) {
        Badge.FIRST_LOG -> "First log"
        Badge.FIRST_WALK -> "First walk"
        Badge.CARE_STREAK_7, Badge.CARE_STREAK_30 -> "day streak"
        Badge.WALK_STREAK_7 -> "walk streak"
        Badge.WALKS_10, Badge.WALKS_100 -> "walks"
        Badge.KM5_WALK, Badge.KM10_WALK -> "in one walk"
        Badge.KM50_MONTH -> "km in a month"
        Badge.KM100_TOTAL -> "km walked"
        Badge.EARLY_BIRD -> "Early bird"
        Badge.SQUEAKY_CLEAN -> "baths"
        Badge.MEMORY_KEEPER -> "memories"
        Badge.HEALTH_CHECK -> "Health check"
        Badge.PACK_LEADER -> "pack friends"
    }
