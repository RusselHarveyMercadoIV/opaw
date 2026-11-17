package app.opaw.core

import java.util.Locale
import kotlin.math.max

object OpawFormat {
    /**
     * Miles and pounds instead of kilometres and kilograms, from Profile > Units.
     *
     * The app sets this from its settings store at launch and when the setting changes.
     */
    @Volatile
    var usesImperial: Boolean = false

    const val METERS_PER_MILE = 1609.344
    const val POUNDS_PER_KILOGRAM = 2.20462

    private fun fmt(pattern: String, vararg args: Any): String = String.format(Locale.ROOT, pattern, *args)

    /** "1.24 km" or "850 m"; "0.77 mi" or "300 ft" in imperial. */
    fun distance(meters: Double, imperial: Boolean = usesImperial): String {
        if (imperial) {
            val miles = meters / METERS_PER_MILE
            if (miles < 0.1) return "${roundHalfAway(meters * 3.28084).toInt()} ft"
            return fmt("%.2f mi", miles)
        }
        if (meters < 1000) return "${roundHalfAway(meters).toInt()} m"
        return fmt("%.2f km", meters / 1000)
    }

    /** The long-distance unit: "km" or "mi". */
    val distanceUnit: String get() = if (usesImperial) "mi" else "km"

    /** Kilometres in the chosen unit, for goals and big numbers. */
    fun longDistance(km: Double): Double = if (usesImperial) km * 1000 / METERS_PER_MILE else km

    /** The weight unit: "kg" or "lb". */
    val weightUnit: String get() = if (usesImperial) "lb" else "kg"

    /** Kilograms in the chosen unit. */
    fun weightValue(kg: Double): Double = if (usesImperial) kg * POUNDS_PER_KILOGRAM else kg

    /** "9.8 kg" or "21.6 lb". */
    fun weight(kg: Double): String {
        val value = weightValue(kg)
        val text = if (roundHalfAway(value) == value) value.toInt().toString() else fmt("%.1f", value)
        return "$text $weightUnit"
    }

    /** "1:05:09" or "12:04". */
    fun duration(seconds: Double): String {
        val total = max(0, roundHalfAway(seconds).toInt())
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) fmt("%d:%02d:%02d", h, m, s) else fmt("%d:%02d", m, s)
    }

    /** "12'30\" /km" (or "/mi"), or "--" when unknown. */
    fun pace(minutesPerKm: Double?, imperial: Boolean = usesImperial): String {
        var pace = minutesPerKm ?: return "--"
        if (!pace.isFinite() || pace <= 0) return "--"
        if (imperial) pace *= METERS_PER_MILE / 1000
        var minutes = pace.toInt()
        var seconds = roundHalfAway((pace - minutes) * 60).toInt()
        if (seconds == 60) {
            minutes += 1
            seconds = 0
        }
        return fmt("%d'%02d\" /%s", minutes, seconds, if (imperial) "mi" else "km")
    }
}
