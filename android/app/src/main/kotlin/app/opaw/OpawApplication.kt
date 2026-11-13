package app.opaw

import android.app.Application
import app.opaw.data.OpawDatabase
import app.opaw.data.OpawSettings
import app.opaw.ui.components.Feedback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class OpawApplication : Application() {
    val database: OpawDatabase by lazy { OpawDatabase.get(this) }
    val settings: OpawSettings by lazy { OpawSettings(this) }

    override fun onCreate() {
        super.onCreate()
        // Units must be right before the first number is drawn.
        runBlocking { settings.syncFormat() }
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            settings.hapticsOn.collect { Feedback.hapticsOn = it }
        }
    }
}
