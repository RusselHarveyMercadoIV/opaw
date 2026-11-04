import OpawKit
import SwiftData
import SwiftUI
import WidgetKit

extension CareKind {
    /// A short label that fits under a circle.
    var shortTitle: String {
        self == .vet ? "Vet visit" : title
    }

    /// Care points earned for a log, shown as "+4" in the day's log.
    var points: Int {
        switch self {
        case .meal: return 4
        case .meds, .vet: return 5
        case .bath: return 3
        case .poop, .brush, .nails, .play: return 2
        case .water, .pee, .weight: return 1
        }
    }

    /// What the avatar says right after this is logged.
    var reaction: String {
        switch self {
        case .meal: return "Yum. Seconds?"
        case .water: return "Ahh, refreshing."
        case .pee: return "Much better."
        case .poop: return "Ahh, that's the spot."
        case .bath: return "Squeaky clean. Hmph."
        case .brush: return "So fluffy now."
        case .nails: return "No more click-clack."
        case .meds: return "Not my favourite, but thanks."
        case .vet: return "Brave dog award, please."
        case .weight: return "Don't tell me the number."
        case .play: return "Again! Again!"
        }
    }
}

/// Points for a walk: six for any walk, as in the design.
enum WalkPoints {
    static let perWalk = 6
}

/// The reaction shown after a log.
struct CareToast: Identifiable, Equatable {
    let id = UUID()
    let kind: CareKind
    let image: Data?
    let before: Int
    let after: Int
}

enum CareRecorder {
    /// Saves a log, refreshes badges and widgets, and returns the reaction to show.
    @MainActor
    static func log(_ event: CareEvent, dog: Dog, in context: ModelContext) -> (toast: CareToast, badge: Badge?) {
        let before = dog.careReport().score
        context.insert(event)
        try? context.save()
        Haptics.success()
        WidgetCenter.shared.reloadTimelines(ofKind: OpawWidgetKind.quickLog)
        let badge = BadgeService.refresh(dog).first
        try? context.save()
        let after = dog.careReport().score
        return (CareToast(kind: event.kind, image: dog.cutoutData ?? dog.photoData, before: before, after: after), badge)
    }

    /// The display name for who logged something.
    static func name(_ loggedBy: String) -> String {
        loggedBy.isEmpty ? "You" : loggedBy
    }
}

/// The black pill that pops up after a log: the pet bobbing, its line, and the points earned.
struct CareToastView: View {
    let toast: CareToast

    var body: some View {
        HStack(spacing: 12) {
            ZStack {
                Circle().fill(Color.white)
                Group {
                    if let data = toast.image, let image = UIImage(data: data) {
                        Image(uiImage: image)
                            .resizable()
                            .scaledToFit()
                            .padding(4)
                    } else {
                        Image(systemName: "dog.fill")
                            .font(.system(size: 28))
                            .foregroundStyle(Color(hex: 0x111111))
                    }
                }
                .loopMotion(.bob, duration: 0.9)
            }
            .frame(width: 62, height: 62)
            .clipShape(Circle())

            VStack(alignment: .leading, spacing: 1) {
                Text("\u{201C}\(toast.kind.reaction)\u{201D}")
                    .font(.opaw(16, .heavy))
                    .foregroundStyle(.white)
                    .lineLimit(1)
                    .minimumScaleFactor(0.8)
                Text("\(toast.kind.title) logged · score \(toast.before) → \(toast.after)")
                    .font(.opaw(13, .medium))
                    .foregroundStyle(Color(hex: 0xAAAAAA))
                    .lineLimit(1)
                    .minimumScaleFactor(0.8)
            }
            Spacer(minLength: 0)
            Text("+\(toast.kind.points)")
                .font(.opaw(19, .black))
                .foregroundStyle(.white)
        }
        .padding(.leading, 8)
        .padding(.trailing, 22)
        .padding(.vertical, 8)
        .background(Color(hex: 0x111111), in: Capsule())
        .shadow(color: .black.opacity(0.25), radius: 18, y: 18)
        .accessibilityElement(children: .combine)
    }
}

/// Hosts a toast: shows it, then hides it after three seconds.
struct ToastSlot: ViewModifier {
    @Binding var toast: CareToast?
    var bottom: CGFloat = 32

    func body(content: Content) -> some View {
        content.overlay(alignment: .bottom) {
            if let toast {
                CareToastView(toast: toast)
                    .padding(.horizontal, 18)
                    .padding(.bottom, bottom)
                    .transition(.move(edge: .bottom).combined(with: .opacity))
                    .onTapGesture { withAnimation { self.toast = nil } }
                    .task(id: toast.id) {
                        try? await Task.sleep(for: .seconds(3))
                        withAnimation(.easeInOut(duration: 0.35)) {
                            if self.toast?.id == toast.id { self.toast = nil }
                        }
                    }
            }
        }
    }
}

extension View {
    func careToast(_ toast: Binding<CareToast?>, bottom: CGFloat = 32) -> some View {
        modifier(ToastSlot(toast: toast, bottom: bottom))
    }
}
