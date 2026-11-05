import SwiftUI

// Pieces shared by onboarding (4a to 4g) and Premium (5a to 5f).

/// A white speech bubble with a sharp bottom-left corner, as the pet talks.
struct SpeechBubble: View {
    let text: String

    var body: some View {
        Text(text)
            .font(.opaw(16, .heavy))
            .foregroundStyle(Color(hex: 0x111111))
            .lineLimit(1)
            .fixedSize()
            .padding(.horizontal, 16)
            .padding(.vertical, 10)
            .background(
                UnevenRoundedRectangle(topLeadingRadius: 22, bottomLeadingRadius: 6, bottomTrailingRadius: 22, topTrailingRadius: 22, style: .continuous)
                    .fill(Color.white)
            )
            .shadow(color: .black.opacity(0.1), radius: 12, y: 10)
    }
}

/// The pet's cut-out photo with the white sticker edge, or a soft placeholder before there is one.
struct StickerPet: View {
    let image: UIImage?
    var edge: CGFloat = 5
    var motion: LoopMotion? = .bob
    var duration: Double? = nil

    var body: some View {
        Group {
            if let image {
                Image(uiImage: image)
                    .resizable()
                    .scaledToFit()
                    .sticker(edge: edge, blur: edge * 2.8, y: edge * 2)
            } else {
                Image(systemName: "pawprint.fill")
                    .resizable()
                    .scaledToFit()
                    .padding(28)
                    .foregroundStyle(Color(hex: 0x111111).opacity(0.09))
            }
        }
        .modifier(OptionalLoopMotion(motion: motion, duration: duration))
    }
}

private struct OptionalLoopMotion: ViewModifier {
    let motion: LoopMotion?
    let duration: Double?

    func body(content: Content) -> some View {
        if let motion {
            content.loopMotion(motion, duration: duration)
        } else {
            content
        }
    }
}

/// The black full-width pill button from the designs: 64 high in onboarding, 62 in Premium.
struct BlackPillButtonStyle: ButtonStyle {
    var height: CGFloat = 64
    var inverted = false
    @Environment(\.isEnabled) private var isEnabled

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.opaw(17, .heavy))
            .foregroundStyle(inverted ? Color(hex: 0x111111) : Color.white)
            .frame(maxWidth: .infinity)
            .frame(height: height)
            .background(inverted ? Color.white : Color(hex: 0x111111), in: Capsule())
            .opacity(isEnabled ? 1 : 0.3)
            .shadow(color: .black.opacity(0.18), radius: 14, y: 14)
            .scaleEffect(configuration.isPressed ? 0.97 : 1)
            .animation(.spring(response: 0.3, dampingFraction: 0.7), value: configuration.isPressed)
    }
}

/// The design's on/off switch: black when on, light gray when off.
struct DesignToggle: View {
    @Binding var isOn: Bool

    var body: some View {
        Button {
            Haptics.select()
            withAnimation(.spring(response: 0.3, dampingFraction: 0.8)) { isOn.toggle() }
        } label: {
            Capsule()
                .fill(isOn ? Color(hex: 0x111111) : Color(hex: 0xDCDCDF))
                .frame(width: 51, height: 31)
                .overlay(alignment: isOn ? .trailing : .leading) {
                    Circle()
                        .fill(Color.white)
                        .frame(width: 27, height: 27)
                        .shadow(color: .black.opacity(0.2), radius: 3, y: 2)
                        .padding(2)
                }
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(.isToggle)
        .accessibilityValue(isOn ? "On" : "Off")
    }
}

/// An icon in a light gray circle, used at the start of list rows.
struct RowIcon: View {
    let icon: DesignIcon
    var size: CGFloat = 36
    var iconSize: CGFloat = 17

    var body: some View {
        Icon(icon, size: iconSize)
            .foregroundStyle(Color.opawInk)
            .frame(width: size, height: size)
            .background(Color.opawChip, in: Circle())
    }
}

/// The small "Premium" tag with a lock, on rows free users can't open yet.
struct PremiumTag: View {
    var body: some View {
        HStack(spacing: 4) {
            Icon(.lock, size: 12, weight: 2.8)
            Text("Premium")
                .font(.opaw(12, .heavy))
        }
        .foregroundStyle(Color(hex: 0x888888))
        .padding(.horizontal, 10)
        .padding(.vertical, 5)
        .background(Color.opawChip, in: Capsule())
    }
}

/// A sparkle chip, such as "Detected" or "Avatar ready".
struct SparkleChip: View {
    let text: String
    var size: CGFloat = 13
    var iconSize: CGFloat = 14

    var body: some View {
        HStack(spacing: size < 12 ? 4 : 6) {
            Icon(.sparkle, size: iconSize, weight: 2.4)
            Text(text)
                .font(.opaw(size, .heavy))
        }
        .foregroundStyle(.white)
        .padding(.horizontal, size < 12 ? 9 : 12)
        .padding(.vertical, size < 12 ? 4 : 7)
        .background(Color(hex: 0x111111), in: Capsule())
    }
}

/// A grabber and rounded white top, for the bottom sheets in 5c and 5d.
struct SheetGrabber: View {
    var body: some View {
        Capsule()
            .fill(Color(hex: 0xDDDDDD))
            .frame(width: 40, height: 5)
            .padding(.top, 12)
    }
}
