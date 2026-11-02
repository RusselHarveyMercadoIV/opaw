import Foundation

/// A recorded location sample. Kept free of CoreLocation so it can be tested anywhere.
public struct GeoPoint: Codable, Equatable, Sendable {
    public var latitude: Double
    public var longitude: Double
    public var timestamp: Date
    public var horizontalAccuracy: Double

    public init(latitude: Double, longitude: Double, timestamp: Date, horizontalAccuracy: Double = 5) {
        self.latitude = latitude
        self.longitude = longitude
        self.timestamp = timestamp
        self.horizontalAccuracy = horizontalAccuracy
    }
}

public enum Geo {
    static let earthRadiusMeters = 6_371_000.0

    /// Great-circle distance in metres.
    public static func distance(_ a: GeoPoint, _ b: GeoPoint) -> Double {
        distance(lat1: a.latitude, lon1: a.longitude, lat2: b.latitude, lon2: b.longitude)
    }

    public static func distance(lat1: Double, lon1: Double, lat2: Double, lon2: Double) -> Double {
        let toRad = Double.pi / 180
        let dLat = (lat2 - lat1) * toRad
        let dLon = (lon2 - lon1) * toRad
        let h = sin(dLat / 2) * sin(dLat / 2)
            + cos(lat1 * toRad) * cos(lat2 * toRad) * sin(dLon / 2) * sin(dLon / 2)
        return 2 * earthRadiusMeters * asin(min(1, sqrt(h)))
    }
}

public struct WalkStats: Equatable, Sendable {
    public var distanceMeters: Double
    public var movingSeconds: TimeInterval
    public var elapsedSeconds: TimeInterval

    public var distanceKm: Double { distanceMeters / 1000 }

    /// Minutes per kilometre while moving, or nil when too short to be meaningful.
    public var paceMinutesPerKm: Double? {
        guard distanceMeters >= 50, movingSeconds > 0 else { return nil }
        return (movingSeconds / 60) / distanceKm
    }

    public static let zero = WalkStats(distanceMeters: 0, movingSeconds: 0, elapsedSeconds: 0)
}

public enum WalkCalculator {
    /// Samples worse than this accuracy (metres) are ignored.
    public static let maxAccuracy: Double = 30
    /// Faster than this (m/s, about 36 km/h) is treated as a GPS jump or a car ride and ignored.
    public static let maxSpeed: Double = 10
    /// Gaps longer than this (seconds) between samples count as paused, not moving.
    public static let pauseGap: TimeInterval = 30

    /// Removes inaccurate samples and impossible jumps.
    public static func clean(_ points: [GeoPoint]) -> [GeoPoint] {
        var result: [GeoPoint] = []
        for point in points where point.horizontalAccuracy >= 0 && point.horizontalAccuracy <= maxAccuracy {
            if let last = result.last {
                let dt = point.timestamp.timeIntervalSince(last.timestamp)
                guard dt > 0 else { continue }
                let speed = Geo.distance(last, point) / dt
                if speed > maxSpeed { continue }
            }
            result.append(point)
        }
        return result
    }

    public static func stats(for rawPoints: [GeoPoint]) -> WalkStats {
        let points = clean(rawPoints)
        guard let first = points.first, let last = points.last else { return .zero }
        var distance = 0.0
        var moving: TimeInterval = 0
        for (a, b) in zip(points, points.dropFirst()) {
            let d = Geo.distance(a, b)
            let dt = b.timestamp.timeIntervalSince(a.timestamp)
            distance += d
            // Ignore jitter while standing still and long pauses.
            if dt <= pauseGap && d / max(dt, 0.001) > 0.3 {
                moving += dt
            }
        }
        return WalkStats(
            distanceMeters: distance,
            movingSeconds: moving,
            elapsedSeconds: last.timestamp.timeIntervalSince(first.timestamp)
        )
    }
}
