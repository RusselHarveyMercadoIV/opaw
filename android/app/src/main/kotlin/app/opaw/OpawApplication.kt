package app.opaw

import android.app.Application
import app.opaw.data.OpawDatabase
import app.opaw.data.OpawSettings
import kotlinx.coroutines.runBlocking

class OpawApplication : Application() {
    val database: OpawDatabase by lazy { OpawDatabase.get(this) }
    val settings: OpawSettings by lazy { OpawSettings(this) }

    override fun onCreate() {
        super.onCreate()
        // Units must be right before the first number is drawn.
        runBlocking { settings.syncFormat() }
    }
}
