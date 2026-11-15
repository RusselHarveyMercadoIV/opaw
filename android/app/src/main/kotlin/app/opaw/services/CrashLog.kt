package app.opaw.services

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import android.util.Log
import java.io.File
import java.util.Date

/**
 * Remembers why the app last crashed, so a test build can show it on the next launch. Testers
 * install the APK without a cable, so there's no other way to read the crash.
 */
object CrashLog {
    private fun file(context: Context) = File(context.filesDir, "last-crash.txt")

    /** Saves the stack trace of any uncaught error, then lets the app crash as usual. */
    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { file(app).writeText("${Date()}\nThread: ${thread.name}\n\n${Log.getStackTraceString(error)}") }
            previous?.uncaughtException(thread, error)
        }
    }

    /** The last crash, the first time it's asked for; null when the app closed normally. */
    fun take(context: Context): String? = runCatching {
        val saved = file(context).takeIf { it.exists() }?.let { file -> file.readText().also { file.delete() } }
        val system = systemReport(context)
        listOfNotNull(saved, system).joinToString("\n\n").ifEmpty { null }
    }.getOrNull()

    /** Crashes in native code and low-memory kills leave no stack trace of ours; Android keeps its own record. */
    private fun systemReport(context: Context): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        val manager = context.getSystemService(ActivityManager::class.java) ?: return null
        val last = manager.getHistoricalProcessExitReasons(null, 0, 1).firstOrNull() ?: return null
        val prefs = context.getSharedPreferences("crash", Context.MODE_PRIVATE)
        if (prefs.getLong("seen", 0) >= last.timestamp) return null
        prefs.edit().putLong("seen", last.timestamp).apply()
        val reason = when (last.reason) {
            ApplicationExitInfo.REASON_CRASH -> return null // Ours, already saved with its stack trace.
            ApplicationExitInfo.REASON_CRASH_NATIVE -> "Native crash"
            ApplicationExitInfo.REASON_LOW_MEMORY -> "Killed for low memory"
            ApplicationExitInfo.REASON_ANR -> "Stopped responding"
            ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "Used too many resources"
            ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> "Failed to start"
            else -> return null
        }
        // The trace is text for a freeze and a binary tombstone for a native crash; keep its readable parts.
        val trace = runCatching { last.traceInputStream?.use { it.readBytes() } }.getOrNull()?.let(::readable)
        return listOfNotNull("${Date(last.timestamp)}\n$reason: ${last.description ?: "no details"}", trace).joinToString("\n\n")
    }

    /** Runs of printable text at least six characters long, like the `strings` tool. */
    private fun readable(bytes: ByteArray): String {
        val lines = mutableListOf<String>()
        val run = StringBuilder()
        fun flush() {
            if (run.length >= 6) lines += run.toString()
            run.clear()
        }
        for (byte in bytes) {
            val c = byte.toInt()
            if (c in 32..126) run.append(c.toChar()) else flush()
            if (lines.size >= 120) break
        }
        flush()
        return lines.joinToString("\n")
    }
}
