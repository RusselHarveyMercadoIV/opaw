package app.opaw.data

import app.opaw.core.CareKind
import app.opaw.core.CareReport
import app.opaw.core.CareScore
import app.opaw.core.CareSnapshot
import app.opaw.core.Badge
import app.opaw.core.BadgeEngine
import app.opaw.core.BadgeStats
import app.opaw.core.StreakCalculator
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

/**
 * A dog with everything it owns, the way SwiftData hands Dog and its relationships to the iOS views.
 * The read-only helpers mirror Services/CareInsights.swift, BadgeService.swift and OpawStore.swift.
 */
data class DogData(
    val dog: Dog,
    val careEvents: List<CareEvent> = emptyList(),
    val walks: List<Walk> = emptyList(),
    val memories: List<Memory> = emptyList(),
    val reminders: List<Reminder> = emptyList(),
    val customTracks: List<CustomTrack> = emptyList(),
) {
    private val zone: ZoneId get() = ZoneId.systemDefault()

    private fun startOfDay(day: Instant): Instant = day.atZone(zone).toLocalDate().atStartOfDay(zone).toInstant()
    private fun nextDay(start: Instant): Instant = start.atZone(zone).plusDays(1).toInstant()

    fun events(on day: Instant): List<CareEvent> {
        val start = startOfDay(day)
        val end = nextDay(start)
        return careEvents.filter { it.date >= start && it.date < end }
    }

    fun count(kind: CareKind, on: Instant = Instant.now()): Int = events(on).count { it.kind == kind }

    /** Kilometres walked on a day. */
    fun walkedKm(on: Instant = Instant.now()): Double {
        val start = startOfDay(on)
        return walkedKm(start, nextDay(start))
    }

    fun walkedKm(from: Instant, to: Instant): Double =
        walks.filter { it.startedAt >= from && it.startedAt < to }.sumOf { it.distanceMeters } / 1000

    val enabledReminders: List<Reminder>
        get() = reminders.filter { it.isEnabled }.sortedWith(compareBy({ it.hour }, { it.minute }))

    /** Reminders that fall on a day: every daily one, plus repeating ones due that day. */
    fun reminders(on: Instant): List<Reminder> {
        val day = on.atZone(zone).toLocalDate()
        return enabledReminders.filter { it.repeats == ReminderRepeat.DAILY || it.nextOccurrence(day, zone) == day }
    }

    /** Repeating reminders after today, soonest first. */
    fun upcomingReminders(after: Instant = Instant.now()): List<Pair<LocalDate, Reminder>> {
        val tomorrow = after.atZone(zone).toLocalDate().plusDays(1)
        return enabledReminders.filter { it.repeats != ReminderRepeat.DAILY }
            .map { it.nextOccurrence(tomorrow, zone) to it }
            .sortedBy { it.first }
    }

    /**
     * The day's reminders that have a log. Each log counts for one reminder only, so a single meal
     * can't tick off breakfast and dinner, and a log just before a reminder counts for it rather than
     * for the one before.
     */
    fun doneReminderIds(on: Instant): Set<String> {
        val todays = reminders(on).sortedBy { it.dueDate(on, zone) }
        val logs = careEvents.sortedBy { it.date }
        val used = mutableSetOf<String>()
        val done = mutableSetOf<String>()
        todays.forEachIndexed { index, reminder ->
            val due = reminder.dueDate(on, zone)
            var end = due.plusSeconds(6 * 3600)
            todays.drop(index + 1).firstOrNull { it.sameTrack(reminder) }?.let {
                end = minOf(end, it.dueDate(on, zone).minusSeconds(90 * 60))
            }
            val start = due.minusSeconds(90 * 60)
            logs.firstOrNull { it.id !in used && reminder.matches(it) && it.date >= start && it.date < end }?.let {
                used += it.id
                done += reminder.id
            }
        }
        return done
    }

    fun isDone(reminder: Reminder, on: Instant): Boolean = reminder.id in doneReminderIds(on)

    /** Care kinds with a reminder that isn't done and is late or due within three hours. */
    fun dueKinds(now: Instant = Instant.now()): Set<CareKind> = dueReminders(now).map { it.kind }.toSet()

    /** Custom tracks with a reminder that isn't done and is late or due within three hours. */
    fun dueTrackIds(now: Instant = Instant.now()): Set<String> = dueReminders(now).mapNotNull { it.trackId }.toSet()

    private fun dueReminders(now: Instant): List<Reminder> {
        val done = doneReminderIds(now)
        return reminders(now).filter { it.id !in done && Duration.between(now, it.dueDate(now, zone)).seconds < 3 * 3600 }
    }

    /** Today's logs for a custom track. */
    fun events(trackId: String, on: Instant = Instant.now()): List<CareEvent> =
        events(on).filter { it.kind == CareKind.CUSTOM && it.trackId == trackId }

    /** Days since the last log of a kind, if there is one. */
    fun daysSince(kinds: Set<CareKind>, now: Instant = Instant.now()): Int? {
        val last = careEvents.filter { it.kind in kinds && it.date <= now }.maxOfOrNull { it.date } ?: return null
        return ChronoUnit.DAYS.between(last.atZone(zone).toLocalDate(), now.atZone(zone).toLocalDate()).toInt()
    }

    /** Whether a meds dose is late, and the next one due today. */
    fun medsStatus(now: Instant = Instant.now()): Pair<Boolean, Instant?> {
        val done = doneReminderIds(now)
        val pending = reminders(now).filter { it.kind == CareKind.MEDS && it.id !in done }.map { it.dueDate(now, zone) }
        val overdue = pending.any { Duration.between(it, now).seconds > 30 * 60 }
        return overdue to pending.minOrNull()
    }

    fun careReport(now: Instant = Instant.now()): CareReport =
        CareScore.report(careSnapshot(now), medsOverdue = medsStatus(now).first, now = now, zone = zone)

    /** Snapshot used by the mood engine. */
    fun careSnapshot(now: Instant = Instant.now()): CareSnapshot {
        val last = mutableMapOf<CareKind, Instant>()
        for (event in careEvents) {
            val existing = last[event.kind]
            if (existing == null || event.date > existing) last[event.kind] = event.date
        }
        val startOfDay = startOfDay(now)
        val walkMinutes = walks.filter { it.startedAt >= startOfDay }.sumOf { it.movingSeconds / 60 }
        val playMinutes = careEvents.count { it.kind == CareKind.PLAY && it.date >= startOfDay } * 15.0
        return CareSnapshot(
            lastEvent = last,
            activityMinutesToday = walkMinutes + playMinutes,
            dailyActivityGoalMinutes = dog.dailyGoalMinutes.toDouble(),
            lastBadgeEarnedAt = dog.lastBadgeEarnedAt,
            mealHours = if (dog.tracks(CareKind.MEAL.raw)) dog.scheduledMealHours(reminders) else emptyList(),
        )
    }

    val scheduledMealHours: List<Int> get() = dog.scheduledMealHours(reminders)

    /** Days in a row, ending today or yesterday, with at least three logs. */
    val careStreak: Int
        get() {
            val byDay = careEvents.groupingBy { it.date.atZone(zone).toLocalDate() }.eachCount()
            var day = LocalDate.now(zone)
            if ((byDay[day] ?: 0) < 3) day = day.minusDays(1)
            var streak = 0
            while ((byDay[day] ?: 0) >= 3) {
                streak += 1
                day = day.minusDays(1)
            }
            return streak
        }

    /** Log counts for the last seven days, oldest first. */
    val lastSevenDayCounts: List<Int>
        get() {
            val today = LocalDate.now(zone)
            return (6 downTo 0).map { offset -> events(today.minusDays(offset.toLong()).atStartOfDay(zone).toInstant()).size }
        }

    val ageDescription: String?
        get() {
            val birth = dog.birthDate?.atZone(zone)?.toLocalDate() ?: return null
            val period = java.time.Period.between(birth, LocalDate.now(zone))
            if (period.years == 0) {
                val months = maxOf(1, period.months)
                return if (months == 1) "1 month old" else "$months months old"
            }
            return if (period.years == 1) "1 year old" else "${period.years} years old"
        }

    // MARK: Badges (BadgeService.swift)

    fun badgeStats(friendsCount: Int = 0, now: Instant = Instant.now()): BadgeStats {
        val nowZoned = now.atZone(zone)
        val monthStart = nowZoned.toLocalDate().withDayOfMonth(1).atStartOfDay(zone).toInstant()
        return BadgeStats(
            totalCareEvents = careEvents.size,
            careStreakDays = StreakCalculator.currentStreak(careEvents.map { it.date }, now, zone),
            walkCount = walks.size,
            totalWalkKm = walks.sumOf { it.distanceMeters } / 1000,
            longestWalkKm = (walks.maxOfOrNull { it.distanceMeters } ?: 0.0) / 1000,
            kmThisMonth = walks.filter { it.startedAt >= monthStart }.sumOf { it.distanceMeters } / 1000,
            bathCount = careEvents.count { it.kind == CareKind.BATH },
            memoryCount = memories.size,
            earlyWalkCount = walks.count { it.startedAt.atZone(zone).hour < 7 },
            walkStreakDays = StreakCalculator.currentStreak(walks.map { it.startedAt }, now, zone),
            vetVisitCount = careEvents.count { it.kind == CareKind.VET },
            friendsCount = friendsCount,
        )
    }

    /** Badges earned by these stats that the dog doesn't have yet. */
    fun newBadges(friendsCount: Int = 0): List<Badge> = BadgeEngine.newlyEarned(badgeStats(friendsCount), dog.earnedBadges)
}

fun Reminder.dueDate(on: Instant, zone: ZoneId = ZoneId.systemDefault()): Instant =
    ZonedDateTime.of(on.atZone(zone).toLocalDate(), java.time.LocalTime.of(hour, minute), zone).toInstant()

/** Whether a log ticks this reminder off: same kind, and for a custom track the same track. */
fun Reminder.matches(event: CareEvent): Boolean = event.kind == kind && (kind != CareKind.CUSTOM || event.trackId == trackId)

fun Reminder.sameTrack(other: Reminder): Boolean = other.kind == kind && (kind != CareKind.CUSTOM || other.trackId == trackId)

/** The custom track's name for a custom log, otherwise the kind's title. */
val CareEvent.displayTitle: String get() = if (kind == CareKind.CUSTOM && trackName.isNotEmpty()) trackName else kind.title
