package app.opaw.core

import java.time.Duration
import java.time.Instant
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** A recorded location sample. Kept free of Android location types so it can be tested anywhere. */
data class GeoPoint(
    val latitude: Double,
    val longitude: Double,
    val timestamp: Instant,
    val horizontalAccuracy: Double = 5.0,
)

internal fun secondsBetween(from: Instant, to: Instant): Double = Duration.between(from, to).toNanos() / 1e9

object Geo {
    internal const val EARTH_RADIUS_METERS = 6_371_000.0

    /** Great-circle distance in metres. */
    fun distance(a: GeoPoint, b: GeoPoint): Double = distance(a.latitude, a.longitude, b.latitude, b.longitude)

    fun distance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val toRad = Math.PI / 180
        val dLat = (lat2 - lat1) * toRad
        val dLon = (lon2 - lon1) * toRad
        val h = sin(dLat / 2) * sin(dLat / 2) +
            cos(lat1 * toRad) * cos(lat2 * toRad) * sin(dLon / 2) * sin(dLon / 2)
        return 2 * EARTH_RADIUS_METERS * asin(min(1.0, sqrt(h)))
    }
}

data class WalkStats(
    val distanceMeters: Double,
    val movingSeconds: Double,
    val elapsedSeconds: Double,
) {
    val distanceKm: Double get() = distanceMeters / 1000

    /** Minutes per kilometre while moving, or null when too short to be meaningful. */
    val paceMinutesPerKm: Double?
        get() = if (distanceMeters >= 50 && movingSeconds > 0) (movingSeconds / 60) / distanceKm else null

    companion object {
        val ZERO = WalkStats(0.0, 0.0, 0.0)
    }
}

object WalkCalculator {
    /** Samples worse than this accuracy (metres) are ignored. */
    const val MAX_ACCURACY = 30.0

    /** Faster than this (m/s, about 36 km/h) is treated as a GPS jump or a car ride and ignored. */
    const val MAX_SPEED = 10.0

    /** Gaps longer than this (seconds) between samples count as paused, not moving. */
    const val PAUSE_GAP = 30.0

    /** Removes inaccurate samples and impossible jumps. */
    fun clean(points: List<GeoPoint>): List<GeoPoint> {
        val result = mutableListOf<GeoPoint>()
        for (point in points) {
            if (point.horizontalAccuracy < 0 || point.horizontalAccuracy > MAX_ACCURACY) continue
            val last = result.lastOrNull()
            if (last != null) {
                val dt = secondsBetween(last.timestamp, point.timestamp)
                if (dt <= 0) continue
                if (Geo.distance(last, point) / dt > MAX_SPEED) continue
            }
            result += point
        }
        return result
    }

    fun stats(rawPoints: List<GeoPoint>): WalkStats {
        val points = clean(rawPoints)
        val first = points.firstOrNull() ?: return WalkStats.ZERO
        val last = points.last()
        var distance = 0.0
        var moving = 0.0
        for ((a, b) in points.zipWithNext()) {
            val d = Geo.distance(a, b)
            val dt = secondsBetween(a.timestamp, b.timestamp)
            distance += d
            // Ignore jitter while standing still and long pauses.
            if (dt <= PAUSE_GAP && d / max(dt, 0.001) > 0.3) moving += dt
        }
        return WalkStats(distance, moving, secondsBetween(first.timestamp, last.timestamp))
    }
}
