import AVFoundation

/// The short sound that goes with each haptic (7c). Every tone is synthesized from the design's table
/// of [frequency, length, loudness, start, wave], so nothing ships as an audio file.
///
/// The session is `.ambient`: sounds mix with music and podcasts, and the Ring/Silent switch mutes them.
/// Settings has its own switch (`Feedback.soundsKey`).
final class Sounds {
    static let shared = Sounds()

    enum Wave { case sine, triangle }

    /// One tone: pitch in Hz, length and start in seconds, loudness 0–1.
    struct Note {
        let hz: Double
        let length: Double
        let loudness: Double
        var start: Double = 0
        var wave: Wave = .sine
    }

    /// The design's sound for each haptic.
    enum Cue: Hashable {
        case light, medium, heavy, soft, rigid, selection, success, warning, error, milestone
        /// One tick of the hold-to-finish ring; `step` 0…1 raises the pitch as the hold fills.
        case tick(step: Double)

        var notes: [Note] {
            switch self {
            case .light: return [Note(hz: 1400, length: 0.04, loudness: 0.12)]
            case .medium: return [Note(hz: 880, length: 0.06, loudness: 0.18)]
            case .heavy: return [Note(hz: 170, length: 0.12, loudness: 0.4), Note(hz: 85, length: 0.16, loudness: 0.3)]
            case .soft: return [Note(hz: 520, length: 0.1, loudness: 0.12)]
            case .rigid: return [Note(hz: 2300, length: 0.025, loudness: 0.12)]
            case .selection: return [Note(hz: 1900, length: 0.02, loudness: 0.07)]
            case .success:
                return [Note(hz: 784, length: 0.09, loudness: 0.16), Note(hz: 1175, length: 0.18, loudness: 0.18, start: 0.09)]
            case .warning:
                return [Note(hz: 622, length: 0.1, loudness: 0.16), Note(hz: 466, length: 0.18, loudness: 0.16, start: 0.12)]
            case .error:
                return [
                    Note(hz: 220, length: 0.07, loudness: 0.22, wave: .triangle),
                    Note(hz: 220, length: 0.07, loudness: 0.22, start: 0.1, wave: .triangle),
                    Note(hz: 196, length: 0.16, loudness: 0.24, start: 0.2, wave: .triangle),
                ]
            case .milestone:
                return [
                    Note(hz: 170, length: 0.12, loudness: 0.4),
                    Note(hz: 784, length: 0.09, loudness: 0.16, start: 0.16),
                    Note(hz: 1175, length: 0.22, loudness: 0.18, start: 0.25),
                ]
            case .tick(let step):
                // The design's three ticks rise 1700 → 2000 → 2400 Hz and get a little louder.
                let s = min(1, max(0, step))
                return [Note(hz: 1700 + 700 * s, length: 0.025, loudness: 0.08 + 0.04 * s)]
            }
        }
    }

    private let engine = AVAudioEngine()
    private let player = AVAudioPlayerNode()
    private let format = AVAudioFormat(standardFormatWithSampleRate: 44_100, channels: 1)!
    private var buffers: [Cue: AVAudioPCMBuffer] = [:]
    private var isSetUp = false

    private init() {}

    func play(_ cue: Cue) {
        guard Feedback.soundsOn else { return }
        guard start(), let buffer = buffer(for: cue) else { return }
        player.scheduleBuffer(buffer, at: nil, options: [])
        if !player.isPlaying { player.play() }
    }

    /// Sets up the session and engine on first use, and restarts the engine after an interruption.
    private func start() -> Bool {
        if !isSetUp {
            do {
                try AVAudioSession.sharedInstance().setCategory(.ambient, options: [.mixWithOthers])
            } catch {
                return false
            }
            engine.attach(player)
            engine.connect(player, to: engine.mainMixerNode, format: format)
            isSetUp = true
        }
        if !engine.isRunning {
            do {
                try AVAudioSession.sharedInstance().setActive(true)
                try engine.start()
            } catch {
                return false
            }
        }
        return true
    }

    private func buffer(for cue: Cue) -> AVAudioPCMBuffer? {
        if let cached = buffers[cue] { return cached }
        guard let buffer = render(cue.notes) else { return nil }
        // Ticks vary by step; only cache the fixed cues so the cache stays small.
        if case .tick = cue {} else { buffers[cue] = buffer }
        return buffer
    }

    /// Mixes the notes into one buffer: each starts near silence, rises to its loudness in 5 ms and
    /// falls away exponentially by the end of its length, like the prototype's Web Audio envelope.
    private func render(_ notes: [Note]) -> AVAudioPCMBuffer? {
        let rate = format.sampleRate
        let end = notes.map { $0.start + $0.length + 0.03 }.max() ?? 0
        let frames = AVAudioFrameCount((end * rate).rounded(.up))
        guard frames > 0, let buffer = AVAudioPCMBuffer(pcmFormat: format, frameCapacity: frames),
              let samples = buffer.floatChannelData?[0] else { return nil }
        buffer.frameLength = frames
        for i in 0..<Int(frames) { samples[i] = 0 }

        let floor = 0.0001
        let attack = 0.005
        for note in notes {
            let first = Int(note.start * rate)
            let count = Int((note.length + 0.03) * rate)
            for n in 0..<count where first + n < Int(frames) {
                let t = Double(n) / rate
                let gain: Double
                if t < attack {
                    gain = floor * pow(note.loudness / floor, t / attack)
                } else if t < note.length {
                    gain = note.loudness * pow(floor / note.loudness, (t - attack) / (note.length - attack))
                } else {
                    gain = 0
                }
                let phase = note.hz * t
                let wave: Double
                switch note.wave {
                case .sine: wave = sin(2 * .pi * phase)
                case .triangle: wave = 4 * abs(phase - phase.rounded(.down) - 0.5) - 1
                }
                samples[first + n] += Float(wave * gain)
            }
        }
        return buffer
    }
}

/// Settings > Sounds and Settings > Haptics (7c): each has its own switch, on by default.
enum Feedback {
    static let soundsKey = "feedbackSounds"
    static let hapticsKey = "feedbackHaptics"

    static var soundsOn: Bool { UserDefaults.standard.object(forKey: soundsKey) as? Bool ?? true }
    static var hapticsOn: Bool { UserDefaults.standard.object(forKey: hapticsKey) as? Bool ?? true }
}
