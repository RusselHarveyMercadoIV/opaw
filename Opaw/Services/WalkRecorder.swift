import ActivityKit
import CoreLocation
import Foundation
import Observation
import OpawKit

/// Records a walk with GPS, keeps stats up to date and drives the Live Activity.
@MainActor
@Observable
final class WalkRecorder: NSObject {
    enum State {
        case idle, recording, paused
    }

    struct FinishedWalk {
        let startedAt: Date
        let endedAt: Date
        let points: [GeoPoint]
        let pins: [WalkPin]
    }

    private(set) var state: State = .idle
    private(set) var points: [GeoPoint] = []
    private(set) var pins: [WalkPin] = []
    private(set) var stats: WalkStats = .zero
    private(set) var startedAt: Date?
    private(set) var authorization: CLAuthorizationStatus = .notDetermined
    private(set) var lastLocation: CLLocation?
    private(set) var dogName: String = ""

    private var pausedTotal: TimeInterval = 0
    private var pauseStartedAt: Date?
    private var activity: Activity<WalkActivityAttributes>?
    private var lastActivityUpdate: Date = .distantPast
    /// Keeps location updates flowing in the background while a walk records.
    @ObservationIgnored private var backgroundSession: CLBackgroundActivitySession?

    @ObservationIgnored private let manager = CLLocationManager()

    override init() {
        super.init()
        manager.delegate = self
        manager.activityType = .fitness
        manager.desiredAccuracy = kCLLocationAccuracyBest
        manager.distanceFilter = 5
        authorization = manager.authorizationStatus
    }

    var isAuthorized: Bool {
        authorization == .authorizedWhenInUse || authorization == .authorizedAlways
    }

    var isDenied: Bool {
        authorization == .denied || authorization == .restricted
    }

    func requestPermission() {
        manager.requestWhenInUseAuthorization()
    }

    /// Starts location updates without recording, to center the map before a walk.
    func warmUp() {
        guard isAuthorized else { return }
        manager.startUpdatingLocation()
    }

    func coolDown() {
        guard state == .idle else { return }
        manager.stopUpdatingLocation()
    }

    /// Elapsed time excluding pauses.
    func elapsed(at now: Date = Date()) -> TimeInterval {
        guard let startedAt else { return 0 }
        let currentPause = pauseStartedAt.map { now.timeIntervalSince($0) } ?? 0
        return max(0, now.timeIntervalSince(startedAt) - pausedTotal - currentPause)
    }

    func start(dogName: String) {
        guard state == .idle else { return }
        self.dogName = dogName
        points = []
        pins = []
        stats = .zero
        pausedTotal = 0
        pauseStartedAt = nil
        startedAt = Date()
        state = .recording
        manager.allowsBackgroundLocationUpdates = true
        manager.showsBackgroundLocationIndicator = true
        manager.pausesLocationUpdatesAutomatically = false
        backgroundSession = CLBackgroundActivitySession()
        manager.startUpdatingLocation()
        startLiveActivity()
    }

    func pause() {
        guard state == .recording else { return }
        state = .paused
        pauseStartedAt = Date()
        updateLiveActivity(force: true)
    }

    func resume() {
        guard state == .paused else { return }
        if let pauseStartedAt {
            pausedTotal += Date().timeIntervalSince(pauseStartedAt)
        }
        pauseStartedAt = nil
        state = .recording
        updateLiveActivity(force: true)
    }

    /// Stops recording and returns everything needed to save the walk.
    func finish() -> FinishedWalk? {
        guard state != .idle, let startedAt else { return nil }
        if state == .paused { resume() }
        let result = FinishedWalk(startedAt: startedAt, endedAt: Date(), points: points, pins: pins)
        manager.stopUpdatingLocation()
        manager.allowsBackgroundLocationUpdates = false
        backgroundSession?.invalidate()
        backgroundSession = nil
        state = .idle
        self.startedAt = nil
        endLiveActivity()
        return result
    }

    /// Discards the current walk.
    func cancel() {
        _ = finish()
        points = []
        pins = []
        stats = .zero
    }

    /// Drops a pee or poop pin at the current location.
    func dropPin(_ kind: CareKind) {
        guard let location = lastLocation else { return }
        pins.append(WalkPin(
            kind: kind.rawValue,
            latitude: location.coordinate.latitude,
            longitude: location.coordinate.longitude,
            date: Date()
        ))
        updateLiveActivity(force: true)
    }

    private func handle(_ locations: [CLLocation]) {
        guard let latest = locations.last else { return }
        lastLocation = latest
        guard state == .recording else { return }
        let newPoints = locations.map {
            GeoPoint(
                latitude: $0.coordinate.latitude,
                longitude: $0.coordinate.longitude,
                timestamp: $0.timestamp,
                horizontalAccuracy: $0.horizontalAccuracy
            )
        }
        points.append(contentsOf: newPoints)
        stats = WalkCalculator.stats(for: points)
        updateLiveActivity(force: false)
    }

    // MARK: Live Activity

    private func activityState() -> WalkActivityAttributes.ContentState {
        let elapsed = elapsed()
        return WalkActivityAttributes.ContentState(
            distanceMeters: stats.distanceMeters,
            timerStart: Date().addingTimeInterval(-elapsed),
            isPaused: state == .paused,
            pausedElapsedSeconds: elapsed,
            peeCount: pins.filter { $0.kind == CareKind.pee.rawValue }.count,
            poopCount: pins.filter { $0.kind == CareKind.poop.rawValue }.count
        )
    }

    private func startLiveActivity() {
        guard ActivityAuthorizationInfo().areActivitiesEnabled else { return }
        do {
            activity = try Activity.request(
                attributes: WalkActivityAttributes(dogName: dogName),
                content: ActivityContent(state: activityState(), staleDate: Date().addingTimeInterval(5 * 60)),
                pushType: nil
            )
        } catch {
            activity = nil
        }
    }

    private func updateLiveActivity(force: Bool) {
        guard let activity else { return }
        // Live Activity updates are budgeted by the system, so throttle them.
        guard force || Date().timeIntervalSince(lastActivityUpdate) > 15 else { return }
        lastActivityUpdate = Date()
        let content = ActivityContent(state: activityState(), staleDate: Date().addingTimeInterval(5 * 60))
        Task {
            await activity.update(content)
        }
    }

    private func endLiveActivity() {
        guard let activity else { return }
        let content = ActivityContent(state: activityState(), staleDate: Date().addingTimeInterval(5 * 60))
        self.activity = nil
        Task {
            await activity.end(content, dismissalPolicy: .immediate)
        }
    }
}

extension WalkRecorder: CLLocationManagerDelegate {
    nonisolated func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        Task { @MainActor in
            self.handle(locations)
        }
    }

    nonisolated func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        let status = manager.authorizationStatus
        Task { @MainActor in
            self.authorization = status
        }
    }

    nonisolated func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
        // Temporary failures (for example no fix indoors) are expected; keep recording.
    }
}
