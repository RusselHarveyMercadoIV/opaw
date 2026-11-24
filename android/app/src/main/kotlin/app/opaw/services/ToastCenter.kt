package app.opaw.services

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.opaw.core.CareKind
import app.opaw.data.CareEvent
import app.opaw.data.Reminder
import app.opaw.data.dueDate
import app.opaw.ui.components.Haptics
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

// Toasts (7b). One toast at a time, across the app: a new toast replaces the one on screen. Black
// toasts are the pet reacting to something you did (a log, a milestone); white ones are the app
// telling you something. Mirrors Toasts.swift and CareToast in CareLogging.swift.

sealed interface ToastItem {
    val id: String
    /** How long it stays, or null when it stays until dealt with. */
    val seconds: Double?
}

/** A toast about a log. */
data class CareToast(
    val style: Style = Style.LOGGED,
    val kind: CareKind,
    /** The log's name, such as "Poop" or a custom track's name. */
    val name: String,
    val imagePath: String?,
    val before: Int,
    val after: Int,
    /** The log this toast is about, so Undo can remove it. */
    val eventId: String? = null,
    /** A deleted log, so Undo can put it back. */
    val restore: CareEvent? = null,
    /** Paws this log earned, shown as "+5" on the toast (16a). */
    val paws: Int = 0,
    override val id: String = UUID.randomUUID().toString(),
) : ToastItem {
    enum class Style {
        /** "Ahh, that's the spot." Poop logged · score 90 → 92. Undo removes the log. */
        LOGGED,
        /** Pee log deleted · Score back to 89. Undo puts the log back. */
        DELETED,
        /** Poop log removed · Score back to 90, after Undo on a logged toast. */
        UNDONE,
    }

    override val seconds: Double get() = if (style == Style.UNDONE) 2.0 else 4.0
}

/** Something the app tells you: family logs, meds due, offline, a failed save, a milestone. */
data class Notice(
    val kind: Kind,
    val title: String,
    val detail: String,
    /** A photo for family toasts. */
    val imagePath: String? = null,
    /** The big number on a milestone toast, when there is no medal art. */
    val medal: String? = null,
    /** The badge medal on a milestone toast (design 2f). */
    val medalRes: Int? = null,
    val actionTitle: String? = null,
    override val id: String = UUID.randomUUID().toString(),
    val action: (() -> Unit)? = null,
) : ToastItem {
    enum class Kind {
        /** Someone else in the household logged something. 4 s. */
        FAMILY,
        /** Meds are due. Stays until you log it or swipe it away; the only toast that uses red. */
        DUE,
        /** You went offline. 4 s, then it shrinks to the Offline chip. */
        OFFLINE,
        /** A save failed. Stays until retried or dismissed. */
        FAILED,
        /** A streak or badge. 6 s. */
        MILESTONE,
    }

    override val seconds: Double?
        get() = when (kind) {
            Kind.FAMILY, Kind.OFFLINE -> 4.0
            Kind.MILESTONE -> 6.0
            Kind.DUE, Kind.FAILED -> null
        }
}

class ToastCenter(context: Context) {
    var item by mutableStateOf<ToastItem?>(null)
        private set

    /** True while the phone has no network; Today shows the Offline chip. */
    var isOffline by mutableStateOf(false)
        private set

    /** Set by a milestone's View button; the tab view opens Challenges. */
    var showChallenges by mutableStateOf(false)

    /** Set by the toast host so toasts can buzz. */
    var haptics: Haptics? = null

    /** Reminders whose due toast was already shown, so a swiped-away one doesn't come back. */
    private val shownDue = mutableSetOf<String>()

    private val main = android.os.Handler(android.os.Looper.getMainLooper())
    private fun post(block: () -> Unit) = main.post(block)

    init {
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        val request = NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build()
        val online = mutableSetOf<Network>()
        runCatching {
            connectivity.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    synchronized(online) { online += network }
                    post { updateOffline(false) }
                }

                override fun onLost(network: Network) {
                    val none = synchronized(online) { online -= network; online.isEmpty() }
                    if (none) post { updateOffline(true) }
                }
            })
            if (connectivity.activeNetwork == null) isOffline = true
        }
    }

    fun show(toast: CareToast?) {
        toast ?: return
        item = toast
    }

    fun show(notice: Notice) {
        when (notice.kind) {
            Notice.Kind.DUE -> haptics?.warning()
            Notice.Kind.FAILED -> haptics?.error()
            Notice.Kind.MILESTONE -> haptics?.milestone()
            Notice.Kind.FAMILY, Notice.Kind.OFFLINE -> Unit
        }
        item = notice
    }

    fun dismiss(id: String? = null) {
        if (id == null || item?.id == id) item = null
    }

    /** "Meds are due · Apoquel · 18:00", once per dose. */
    fun showDue(reminder: Reminder, on: Instant, log: () -> Unit) {
        val zone = ZoneId.systemDefault()
        val key = "${reminder.id}-${on.atZone(zone).toLocalDate()}"
        if (!shownDue.add(key)) return
        val time = clock(reminder.dueDate(on, zone))
        val name = reminder.title.ifEmpty { reminder.kind.title }
        val title = if (reminder.kind == CareKind.MEDS) "Meds are due" else "${reminder.kind.title} is due"
        show(Notice(Notice.Kind.DUE, title, "$name · $time", actionTitle = "Log", action = log))
    }

    private fun updateOffline(offline: Boolean) {
        if (offline == isOffline) return
        isOffline = offline
        if (offline) {
            show(Notice(Notice.Kind.OFFLINE, "You're offline", "Logging keeps working"))
        } else if ((item as? Notice)?.kind == Notice.Kind.OFFLINE) {
            item = null
        }
    }
}
