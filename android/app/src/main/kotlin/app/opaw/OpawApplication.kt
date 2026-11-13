package app.opaw

import android.app.Application
import android.text.format.DateFormat
import app.opaw.data.OpawDatabase
import app.opaw.data.OpawRepository
import app.opaw.data.OpawSettings
import app.opaw.services.ClockSettings
import app.opaw.services.NotificationService
import app.opaw.services.Sounds
import app.opaw.services.ToastCenter
import app.opaw.ui.components.Feedback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class OpawApplication : Application() {
    val database: OpawDatabase by lazy { OpawDatabase.get(this) }
    val repository: OpawRepository by lazy { OpawRepository(database) }
    val settings: OpawSettings by lazy { OpawSettings(this) }
    val toasts: ToastCenter by lazy { ToastCenter(this) }

    /** Work that should outlive a screen, such as saving after a toast's Undo. */
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        // Units must be right before the first number is drawn.
        runBlocking { settings.syncFormat() }
        ClockSettings.is24Hour = DateFormat.is24HourFormat(this)
        NotificationService.createChannel(this)
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            launch { settings.hapticsOn.collect { Feedback.hapticsOn = it } }
            launch { settings.soundsOn.collect { Sounds.soundsOn = it } }
        }
        NotificationService.reschedule(this)
    }
}
