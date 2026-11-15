package app.opaw

import android.app.Application
import android.text.format.DateFormat
import app.opaw.data.OpawDatabase
import app.opaw.data.OpawRepository
import app.opaw.data.OpawSettings
import app.opaw.services.PetModelService
import app.opaw.services.ClockSettings
import app.opaw.services.CrashLog
import app.opaw.services.NotificationService
import app.opaw.services.PackService
import app.opaw.services.PremiumStore
import app.opaw.services.Sounds
import app.opaw.services.ToastCenter
import app.opaw.services.WidgetRefresher
import app.opaw.widget.refreshWidgets
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
    val pack: PackService by lazy { PackService(this) }
    val premium: PremiumStore by lazy { PremiumStore(this) }
    val petModels: PetModelService by lazy { PetModelService(this, pack, scope) }

    /** Work that should outlive a screen, such as saving after a toast's Undo. */
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) CrashLog.install(this)
        // Units must be right before the first number is drawn.
        runBlocking { settings.syncFormat() }
        ClockSettings.is24Hour = DateFormat.is24HourFormat(this)
        NotificationService.createChannel(this)
        WidgetRefresher.onRefresh = { context -> scope.launch { refreshWidgets(context) } }
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            launch { settings.hapticsOn.collect { Feedback.hapticsOn = it } }
            launch { settings.soundsOn.collect { Sounds.soundsOn = it } }
        }
        NotificationService.reschedule(this)
        premium // Connects to Google Play and checks for Premium.
        petModels.resume()
    }
}
