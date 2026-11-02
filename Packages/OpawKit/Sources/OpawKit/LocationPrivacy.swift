import Foundation

/// Location privacy helpers for the social features.
///
/// Opaw never shares an exact location with other people. Positions are snapped to a grid
/// and walk routes are trimmed near their start and end so a home address can't be inferred.
public enum LocationPrivacy {
    /// Default grid size for "nearby" positions, in metres.
    public static let defaultGridMeters: Double = 500

    /// Snaps a coordinate to the centre of a grid cell of roughly `gridMeters` per side.
    public static func coarsen(latitude: Double, longitude: Double, gridMeters: Double = defaultGridMeters) -> (latitude: Double, longitude: Double) {
        let metersPerDegreeLat = 111_320.0
        let latStep = gridMeters / metersPerDegreeLat
        let snappedLat = (floor(latitude / latStep) + 0.5) * latStep
        // Use the snapped latitude so every point in a cell gets the same longitude step.
        let metersPerDegreeLon = max(1, metersPerDegreeLat * cos(snappedLat * .pi / 180))
        let lonStep = gridMeters / metersPerDegreeLon
        let snappedLon = (floor(longitude / lonStep) + 0.5) * lonStep
        return (snappedLat, snappedLon)
    }

    /// Removes the parts of a route within `radiusMeters` of its start and end before sharing.
    public static func trimRouteEnds(_ points: [GeoPoint], radiusMeters: Double = 200) -> [GeoPoint] {
        guard let start = points.first, let end = points.last else { return [] }
        return points.filter { point in
            Geo.distance(point, start) > radiusMeters && Geo.distance(point, end) > radiusMeters
        }
    }
}
