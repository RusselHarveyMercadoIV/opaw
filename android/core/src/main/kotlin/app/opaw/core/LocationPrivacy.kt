package app.opaw.core

import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max

/**
 * Location privacy helpers for the social features.
 *
 * Opaw never shares an exact location with other people. Positions are snapped to a grid
 * and walk routes are trimmed near their start and end so a home address can't be inferred.
 */
object LocationPrivacy {
    /** Default grid size for "nearby" positions, in metres. */
    const val DEFAULT_GRID_METERS = 500.0

    /** Snaps a coordinate to the centre of a grid cell of roughly [gridMeters] per side. */
    fun coarsen(latitude: Double, longitude: Double, gridMeters: Double = DEFAULT_GRID_METERS): Pair<Double, Double> {
        val metersPerDegreeLat = 111_320.0
        val latStep = gridMeters / metersPerDegreeLat
        val snappedLat = (floor(latitude / latStep) + 0.5) * latStep
        // Use the snapped latitude so every point in a cell gets the same longitude step.
        val metersPerDegreeLon = max(1.0, metersPerDegreeLat * cos(snappedLat * Math.PI / 180))
        val lonStep = gridMeters / metersPerDegreeLon
        val snappedLon = (floor(longitude / lonStep) + 0.5) * lonStep
        return snappedLat to snappedLon
    }

    /** Removes the parts of a route within [radiusMeters] of its start and end before sharing. */
    fun trimRouteEnds(points: List<GeoPoint>, radiusMeters: Double = 200.0): List<GeoPoint> {
        val start = points.firstOrNull() ?: return emptyList()
        val end = points.last()
        return points.filter { Geo.distance(it, start) > radiusMeters && Geo.distance(it, end) > radiusMeters }
    }
}
