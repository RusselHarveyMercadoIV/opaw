package app.opaw.services

import app.opaw.core.BadgeEngine
import app.opaw.core.BadgeLevel
import android.content.Context
import app.opaw.OpawApplication
import app.opaw.core.CareKind
import app.opaw.data.CareEvent
import app.opaw.data.DogData
import app.opaw.data.displayTitle
import app.opaw.data.matches
import app.opaw.ui.home.RoomCue
import app.opaw.ui.home.RoomCues
import app.opaw.ui.profile.medalRes
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.util.UUID

/** Saving, undoing and deleting care logs. Mirrors `CareRecorder` in CareLogging.swift. */
object CareRecorder {
    /**
     * Saves a log, refreshes badges and widgets, and returns the reaction to show. A new badge follows
     * as a milestone toast; a failed save shows a failed toast with Retry instead.
     */
    suspend fun log(context: Context, event: CareEvent): CareToast? {
        val app = context.applicationContext as OpawApplication
        val repo = app.repository
        val toasts = app.toasts
        val before = repo.loadDogData(event.dogId) ?: return null
        val stamped = if (event.loggedBy.isEmpty()) event.copy(loggedBy = app.settings.caretakerName.first()) else event
        try {
            repo.saveEvent(stamped)
        } catch (error: Exception) {
            toasts.show(
                Notice(Notice.Kind.FAILED, "Couldn't save the ${stamped.displayTitle.lowercase()} log", "Nothing was logged", actionTitle = "Retry") {
                    app.scope.launch { toasts.show(log(context, stamped.copy(id = UUID.randomUUID().toString()))) }
                },
            )
            return null
        }
        toasts.haptics?.success()
        WidgetRefresher.refresh(context)
        val badges = refreshBadges(context, event.dogId)
        val after = repo.loadDogData(event.dogId) ?: before
        // Care earns Paws (16a): +5 a log, up to the day's allowance, and +20 once the day's reminders are all done.
        val now = Instant.now()
        val due = after.reminders(now)
        var paws = app.paws.earnForLog(event.dogId, stamped.id, stamped.kind, stamped.trackId, stamped.date, due.count { it.matches(stamped) }, now)
        if (due.isNotEmpty() && after.doneReminderIds(now).size >= due.size) paws += app.paws.dayDone(event.dogId)
        // In the room, the pet goes to its bowl: it eats after a meal (16g), drinks after water.
        when (stamped.kind) {
            CareKind.MEAL -> RoomCues.send(event.dogId, RoomCue(RoomCue.Kind.EAT))
            CareKind.WATER -> RoomCues.send(event.dogId, RoomCue(RoomCue.Kind.DRINK))
            else -> Unit
        }
        if (badges.isNotEmpty()) {
            val badge = badges.first()
            // The reaction first, then the milestone replaces it.
            app.scope.launch {
                delay(1600)
                toasts.show(badgeNotice(badge, after.dog.name) { toasts.showChallenges = true })
            }
        }
        return CareToast(
            kind = stamped.kind,
            name = stamped.displayTitle,
            imagePath = after.dog.cutoutPath ?: after.dog.photoPath,
            before = before.careReport().score,
            after = after.careReport().score,
            eventId = stamped.id,
            paws = paws,
        )
    }

    /**
     * Awards any newly earned badge levels and returns them, the one to celebrate first. A pet that only
     * holds badges from before bronze, silver and gold gets its levels quietly, so updating doesn't
     * fire a stack of toasts.
     */
    suspend fun refreshBadges(context: Context, dogId: String, friendsCount: Int = 0): List<BadgeLevel> {
        val app = context.applicationContext as OpawApplication
        val data = app.repository.loadDogData(dogId) ?: return emptyList()
        val new = data.newBadges(friendsCount)
        if (new.isEmpty() && !data.dog.hasLegacyBadges) return emptyList()
        val quiet = data.dog.hasLegacyBadges
        app.repository.saveDog(
            data.dog.copy(
                earnedBadgesRaw = (data.dog.earnedBadges + new).map { it.raw }.sorted(),
                lastBadgeEarnedAt = if (quiet || new.isEmpty()) data.dog.lastBadgeEarnedAt else Instant.now(),
            ),
        )
        if (quiet) return emptyList()
        val headline = BadgeEngine.headline(new) ?: return emptyList()
        app.settings.setNewestBadge(dogId, headline.raw)
        return listOf(headline) + (new - headline)
    }

    /** The milestone toast for a new badge level: its medal, "Rambler · Silver", and what earned it. */
    fun badgeNotice(level: BadgeLevel, petName: String, action: () -> Unit) =
        Notice(Notice.Kind.MILESTONE, level.title, "$petName · ${level.detail}", medalRes = level.medalRes, actionTitle = "View", action = action)

    /**
     * Removes a log and returns the white toast that says so: UNDONE after Undo on a logged toast,
     * DELETED (with Undo to put it back) after holding a log and confirming.
     */
    suspend fun remove(context: Context, eventId: String, style: CareToast.Style): CareToast? {
        val app = context.applicationContext as OpawApplication
        val repo = app.repository
        val event = repo.event(eventId) ?: return null
        val before = repo.loadDogData(event.dogId) ?: return null
        repo.deleteEvent(event)
        app.paws.refund(event.dogId, eventId)
        if (style == CareToast.Style.DELETED) app.toasts.haptics?.rigid() else app.toasts.haptics?.soft()
        WidgetRefresher.refresh(context)
        val after = repo.loadDogData(event.dogId) ?: before
        return CareToast(
            style = style,
            kind = event.kind,
            name = event.displayTitle,
            imagePath = after.dog.cutoutPath ?: after.dog.photoPath,
            before = before.careReport().score,
            after = after.careReport().score,
            restore = if (style == CareToast.Style.DELETED) event else null,
        )
    }

    /** Puts a deleted log back, for Undo on the deleted toast. */
    suspend fun restore(context: Context, event: CareEvent) {
        val app = context.applicationContext as OpawApplication
        if (app.repository.loadDogData(event.dogId) == null) return
        app.repository.saveEvent(event)
        app.toasts.haptics?.soft()
        WidgetRefresher.refresh(context)
    }

    /** A question to ask before logging when it looks like a repeat or more than planned, otherwise null. */
    fun repeatWarning(kind: CareKind, trackId: String? = null, title: String, data: DogData, now: Instant = Instant.now()): String? {
        val today = data.events(now).filter { it.kind == kind && (kind != CareKind.CUSTOM || it.trackId == trackId) && it.date <= now }
        val last = today.maxByOrNull { it.date } ?: return null
        val minutes = Duration.between(last.date, now).toMinutes().toInt()
        if (minutes < 5) {
            return "$title was logged ${if (minutes == 0) "just now" else "$minutes min ago"}. Log another?"
        }
        when (kind) {
            CareKind.MEAL -> {
                val planned = data.scheduledMealHours.size
                if (planned > 0 && today.size >= planned) return "${data.dog.name} has had ${today.size} of $planned meals today. Log another?"
            }
            CareKind.MEDS -> {
                val doses = data.reminders(now).count { it.kind == CareKind.MEDS }
                if (doses > 0 && today.size >= doses) {
                    return if (doses == 1) "Today's meds dose is already logged. Log another?" else "All $doses meds doses are logged today. Log another?"
                }
            }
            CareKind.BATH, CareKind.BRUSH, CareKind.NAILS, CareKind.VET ->
                return "$title was already logged today at ${clock(last.date)}. Log another?"
            else -> Unit
        }
        return null
    }
}

/** Refreshes the home screen widgets after a change. Filled in with the widget (Phase 7). */
object WidgetRefresher {
    var onRefresh: ((Context) -> Unit)? = null
    fun refresh(context: Context) = onRefresh?.invoke(context.applicationContext) ?: Unit
}
