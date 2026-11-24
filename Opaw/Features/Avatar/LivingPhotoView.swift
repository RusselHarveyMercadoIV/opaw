import OpawKit
import SwiftUI

/// The on-device avatar: the dog's cutout gently breathing, bobbing and reacting to its mood.
///
/// It's always available instantly and works offline, and it's the fallback while motion clips generate.
struct LivingPhotoView: View {
    let image: UIImage
    let mood: AvatarMood

    @State private var hopTrigger = 0

    var body: some View {
        TimelineView(.animation) { timeline in
            let t = timeline.date.timeIntervalSinceReferenceDate
            let motion = Motion(mood: mood, time: t)
            Image(uiImage: image)
                .resizable()
                .scaledToFit()
                .scaleEffect(x: motion.scaleX, y: motion.scaleY, anchor: .bottom)
                .rotationEffect(.degrees(motion.tilt), anchor: .bottom)
                .offset(y: motion.bob)
                .shadow(color: .black.opacity(0.18), radius: 10, y: 8)
        }
        .keyframeAnimator(initialValue: 0.0, trigger: hopTrigger) { content, hop in
            content.offset(y: -hop)
        } keyframes: { _ in
            KeyframeTrack {
                SpringKeyframe(28, duration: 0.18)
                SpringKeyframe(0, duration: 0.35)
            }
        }
        .contentShape(Rectangle())
        .onTapGesture {
            hopTrigger += 1
            Haptics.tap()
        }
        .accessibilityLabel("Your dog's avatar, \(mood.caption.lowercased())")
    }

    /// Small procedural motions per mood.
    struct Motion {
        var scaleX: CGFloat = 1
        var scaleY: CGFloat = 1
        var tilt: Double = 0
        var bob: CGFloat = 0

        init(mood: AvatarMood, time t: Double) {
            let breathe = sin(t * 2 * .pi / 3.2)
            scaleY = 1 + 0.018 * breathe
            scaleX = 1 - 0.008 * breathe
            switch mood {
            case .happy, .proud:
                tilt = 2.5 * sin(t * 2 * .pi / 1.6)
                bob = -3 * CGFloat(max(0, sin(t * 2 * .pi / 0.8)))
            case .restless:
                tilt = 3.5 * sin(t * 2 * .pi / 0.7)
                bob = -6 * CGFloat(max(0, sin(t * 2 * .pi / 0.5)))
            case .hungry, .thirsty:
                tilt = 6 * sin(t * 2 * .pi / 4)
            case .sleepy:
                scaleY = 1 + 0.03 * sin(t * 2 * .pi / 5)
                tilt = -3
            case .fresh:
                tilt = 4 * sin(t * 2 * .pi / 0.25) * max(0, sin(t * 2 * .pi / 4))
            }
        }
    }
}
