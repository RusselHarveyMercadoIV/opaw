package app.opaw.widget

import android.app.Activity
import android.os.Bundle
import android.widget.Toast
import app.opaw.OpawApplication
import app.opaw.core.CareKind
import kotlinx.coroutines.launch

/**
 * "Log poop in Opaw" from a launcher shortcut or Google Assistant: logs straight away and closes.
 * The Android counterpart of `LogCareIntent`.
 */
class LogShortcutActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val kind = CareKind.fromRaw(intent?.data?.lastPathSegment ?: intent?.getStringExtra("kind")) ?: CareKind.POOP
        val app = application as OpawApplication
        app.scope.launch {
            val name = logForActiveDog(this@LogShortcutActivity, kind)
            val message = if (name == null) "Open Opaw to add your dog." else "Logged ${kind.title.lowercase()} for $name."
            Toast.makeText(this@LogShortcutActivity, message, Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}
