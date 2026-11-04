import SwiftUI
import UIKit

// MARK: - Design tokens
//
// "Soft white": light gray ground, white cards with soft shadows, round floating
// buttons, black pill buttons, a full-color sticker pet, rounded heavy type.
// Red appears only when something needs attention.

extension UIColor {
    convenience init(hex: UInt32, alpha: CGFloat = 1) {
        self.init(
            red: CGFloat((hex >> 16) & 0xFF) / 255,
            green: CGFloat((hex >> 8) & 0xFF) / 255,
            blue: CGFloat(hex & 0xFF) / 255,
            alpha: alpha
        )
    }

    static func opaw(light: UInt32, dark: UInt32) -> UIColor {
        UIColor { $0.userInterfaceStyle == .dark ? UIColor(hex: dark) : UIColor(hex: light) }
    }
}

extension Color {
    init(hex: UInt32) {
        self.init(uiColor: UIColor(hex: hex))
    }

    /// The accent: black ink on light, white on dark. Defined in the asset catalog.
    static let opawOrange = Color("AccentColor")

    /// The ground behind cards.
    static let opawCream = Color(uiColor: .opaw(light: 0xF4F4F5, dark: 0x0B0B0C))

    /// Primary text and the primary button fill.
    static let opawInk = Color(uiColor: .opaw(light: 0x111111, dark: 0xF5F5F5))

    /// Text drawn on top of `opawInk`.
    static let opawOnInk = Color(uiColor: .opaw(light: 0xFFFFFF, dark: 0x111111))

    /// Cards, round buttons and sheets.
    static let opawSurface = Color(uiColor: .opaw(light: 0xFFFFFF, dark: 0x1C1C1E))

    /// Chips and inputs inside a card.
    static let opawChip = Color(uiColor: .opaw(light: 0xF1F1F2, dark: 0x2C2C2E))

    /// Secondary text.
    static let opawMuted = Color(uiColor: .opaw(light: 0x999999, dark: 0x8E8E93))

    /// Row dividers inside cards.
    static let opawHairline = Color(uiColor: .opaw(light: 0xEEEEEE, dark: 0x2C2C2E))

    /// Only for things that need attention: overdue meds, warnings.
    static let opawAlert = Color(hex: 0xEC3013)

    /// Quieter text steps used by the design: #AAA, #777, #BBB.
    static let opawFaint = Color(uiColor: .opaw(light: 0xAAAAAA, dark: 0x7C7C80))
    static let opawSoftText = Color(uiColor: .opaw(light: 0x777777, dark: 0xA1A1A6))
    static let opawGhost = Color(uiColor: .opaw(light: 0xBBBBBB, dark: 0x5C5C60))

    /// Empty bar and track fills.
    static let opawTrack = Color(uiColor: .opaw(light: 0xE6E6E8, dark: 0x2C2C2E))

    /// Mood dots.
    static let opawGood = Color(hex: 0x34C759)
    static let opawWarn = Color(hex: 0xF5B300)
}

enum Space {
    static let xs: CGFloat = 4
    static let s: CGFloat = 8
    static let m: CGFloat = 16
    static let l: CGFloat = 20
    static let xl: CGFloat = 32
    static let xxl: CGFloat = 48
}

enum Radius {
    static let control: CGFloat = 20
    static let card: CGFloat = 30
    static let sheet: CGFloat = 40
}

extension Font {
    /// The Nunito face for a weight. The design uses 500 to 900 only.
    static func nunitoName(_ weight: Font.Weight) -> String {
        switch weight {
        case .black: "Nunito-Black"
        case .heavy: "Nunito-ExtraBold"
        case .bold: "Nunito-Bold"
        case .semibold: "Nunito-SemiBold"
        default: "Nunito-Medium"
        }
    }

    /// Nunito Black display type (900).
    static func opawDisplay(_ size: CGFloat, weight: Font.Weight = .black) -> Font {
        .custom(nunitoName(weight), size: size)
    }

    /// Nunito at a weight: .medium 500, .semibold 600, .bold 700, .heavy 800, .black 900.
    static func opaw(_ size: CGFloat, _ weight: Font.Weight = .bold) -> Font {
        .custom(nunitoName(weight), size: size)
    }

    /// Nunito Black numerals for stats.
    static func opawNumber(_ size: CGFloat) -> Font {
        .custom("Nunito-Black", size: size).monospacedDigit()
    }
}

/// Small label above a section.
struct Eyebrow: View {
    let text: String

    var body: some View {
        Text(text)
            .font(.opaw(14, .bold))
            .foregroundStyle(Color.opawMuted)
    }
}

// MARK: - Surfaces

/// A white card with a soft shadow.
struct CardModifier: ViewModifier {
    var padding: CGFloat = 20

    func body(content: Content) -> some View {
        content
            .padding(padding)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(Color.opawSurface, in: RoundedRectangle(cornerRadius: Radius.card, style: .continuous))
            .shadow(color: .black.opacity(0.05), radius: 15, y: 12)
    }
}

/// A pet cutout printed like a sticker: a thick white edge and a soft drop shadow.
struct StickerModifier: ViewModifier {
    var edge: CGFloat = 5
    var shadowBlur: CGFloat = 7
    var shadowY: CGFloat = 10
    var shadowOpacity: Double = 0.16

    func body(content: Content) -> some View {
        content
            .shadow(color: .white, radius: 0, x: edge, y: 0)
            .shadow(color: .white, radius: 0, x: -edge, y: 0)
            .shadow(color: .white, radius: 0, x: 0, y: edge)
            .shadow(color: .white, radius: 0, x: 0, y: -edge)
            .shadow(color: .black.opacity(shadowOpacity), radius: shadowBlur, y: shadowY)
    }
}

/// A white rounded card with the design's soft shadow, at any radius.
struct WhiteCardModifier: ViewModifier {
    var radius: CGFloat = 30
    var shadowOpacity: Double = 0.05

    func body(content: Content) -> some View {
        content
            .background(Color.opawSurface, in: RoundedRectangle(cornerRadius: radius, style: .continuous))
            .shadow(color: .black.opacity(shadowOpacity), radius: 15, y: 12)
    }
}

extension View {
    func card(padding: CGFloat = 20) -> some View { modifier(CardModifier(padding: padding)) }

    func sticker(edge: CGFloat = 5, blur: CGFloat = 7, y: CGFloat = 10, opacity: Double = 0.16) -> some View {
        modifier(StickerModifier(edge: edge, shadowBlur: blur, shadowY: y, shadowOpacity: opacity))
    }

    /// White rounded background with the soft card shadow; no padding or sizing.
    func whiteCard(radius: CGFloat = 30, shadow: Double = 0.05) -> some View {
        modifier(WhiteCardModifier(radius: radius, shadowOpacity: shadow))
    }

    /// Screen background.
    func opawBackground() -> some View {
        background(Color.opawCream.ignoresSafeArea())
    }

    /// The soft shadow under floating round buttons.
    func floatingShadow() -> some View {
        shadow(color: .black.opacity(0.07), radius: 13, y: 10)
    }
}

// MARK: - Buttons

/// Full-width ink capsule with a soft press.
struct OpawPrimaryButtonStyle: ButtonStyle {
    @Environment(\.isEnabled) private var isEnabled

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.opaw(17, .bold))
            .foregroundStyle(Color.opawOnInk)
            .frame(maxWidth: .infinity, minHeight: 62)
            .background(Color.opawInk.opacity(isEnabled ? 1 : 0.18), in: Capsule())
            .scaleEffect(configuration.isPressed ? 0.97 : 1)
            .animation(.spring(response: 0.3, dampingFraction: 0.7), value: configuration.isPressed)
    }
}

/// Full-width outlined capsule.
struct OpawSecondaryButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.opaw(17, .heavy))
            .foregroundStyle(Color.opawInk)
            .frame(maxWidth: .infinity, minHeight: 62)
            .background(Color.opawSurface, in: Capsule())
            .floatingShadow()
            .scaleEffect(configuration.isPressed ? 0.97 : 1)
            .animation(.spring(response: 0.3, dampingFraction: 0.7), value: configuration.isPressed)
    }
}

/// Gives any tappable view a gentle press-down.
struct PressableStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .scaleEffect(configuration.isPressed ? 0.95 : 1)
            .animation(.spring(response: 0.25, dampingFraction: 0.65), value: configuration.isPressed)
    }
}

extension ButtonStyle where Self == OpawPrimaryButtonStyle {
    static var opawPrimary: OpawPrimaryButtonStyle { OpawPrimaryButtonStyle() }
}

extension ButtonStyle where Self == OpawSecondaryButtonStyle {
    static var opawSecondary: OpawSecondaryButtonStyle { OpawSecondaryButtonStyle() }
}

extension ButtonStyle where Self == PressableStyle {
    static var pressable: PressableStyle { PressableStyle() }
}

/// A white floating circle with an icon, used for top-bar and bottom-bar actions.
struct RoundIcon: View {
    let symbol: String
    var size: CGFloat = 54
    var tint: Color = .opawInk

    var body: some View {
        SymbolIcon(symbol: symbol, size: size * 0.41)
            .foregroundStyle(tint)
            .frame(width: size, height: size)
            .background(Color.opawSurface, in: Circle())
            .floatingShadow()
    }
}

/// A pill-shaped choice: black when selected, light gray otherwise.
struct ChoiceChip: View {
    let title: String
    let isSelected: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(title)
                .font(.opaw(14, .bold))
                .foregroundStyle(isSelected ? Color.opawOnInk : Color.opawInk.opacity(0.7))
                .padding(.horizontal, 14)
                .padding(.vertical, 10)
                .background(isSelected ? Color.opawInk : Color.opawChip, in: Capsule())
        }
        .buttonStyle(.pressable)
        .accessibilityAddTraits(isSelected ? .isSelected : [])
    }
}

/// A screen header: round button, title, round button.
struct ScreenHeader<Leading: View, Trailing: View>: View {
    let title: String
    @ViewBuilder var leading: Leading
    @ViewBuilder var trailing: Trailing

    var body: some View {
        ZStack {
            Text(title)
                .font(.opaw(22, .black))
                .foregroundStyle(Color.opawInk)
            HStack {
                leading
                Spacer()
                trailing
            }
        }
        .padding(.horizontal, Space.l)
        .padding(.top, 10)
    }
}

// MARK: - Shared pieces

/// The pet switcher chip in screen headers: a small avatar and the pet's name.
struct PetChip: View {
    let dog: Dog?

    var body: some View {
        HStack(spacing: 8) {
            DogThumbnail(data: dog?.cutoutData ?? dog?.photoData, size: 36)
                .overlay(Circle().strokeBorder(Color.opawSurface, lineWidth: 3))
            Text(dog?.name ?? "Pet")
                .font(.opaw(15, .heavy))
                .foregroundStyle(Color.opawInk)
                .lineLimit(1)
        }
        .padding(.vertical, 5)
        .padding(.leading, 5)
        .padding(.trailing, 14)
        .background(Color.opawSurface, in: Capsule())
        .floatingShadow()
    }
}

/// A centered stat: big number over a quiet label.
struct StatColumn: View {
    let value: String
    let label: String
    var valueSize: CGFloat = 21
    var labelSize: CGFloat = 14
    var valueColor: Color = .opawInk

    var body: some View {
        VStack(spacing: 2) {
            Text(value)
                .font(.opaw(valueSize, .black))
                .foregroundStyle(valueColor)
                .lineLimit(1)
                .minimumScaleFactor(0.6)
                .contentTransition(.numericText())
            Text(label)
                .font(.opaw(labelSize, .medium))
                .foregroundStyle(Color.opawMuted)
                .lineLimit(1)
                .minimumScaleFactor(0.8)
        }
        .frame(maxWidth: .infinity)
    }
}

/// A filter pill on the ground: black when selected, white with a soft shadow otherwise.
struct FilterPill: View {
    let title: String
    let isSelected: Bool
    var verticalPadding: CGFloat = 10
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(title)
                .font(.opaw(14, .bold))
                .foregroundStyle(isSelected ? Color.opawOnInk : Color.opawInk)
                .padding(.horizontal, 18)
                .padding(.vertical, verticalPadding)
                .background(isSelected ? Color.opawInk : Color.opawSurface, in: Capsule())
                .shadow(color: .black.opacity(isSelected ? 0 : 0.05), radius: 8, y: 6)
        }
        .buttonStyle(.pressable)
        .accessibilityAddTraits(isSelected ? .isSelected : [])
    }
}

/// Text with a thick white outline, like a sticker label.
struct OutlinedText: View {
    let text: String
    var size: CGFloat = 28

    var body: some View {
        Text(text)
            .font(.opaw(size, .black))
            .foregroundStyle(Color(hex: 0x111111))
            .shadow(color: .white, radius: 0, x: 3, y: 0)
            .shadow(color: .white, radius: 0, x: -3, y: 0)
            .shadow(color: .white, radius: 0, x: 0, y: 3)
            .shadow(color: .white, radius: 0, x: 0, y: -3)
            .shadow(color: .white, radius: 0, x: 2, y: 2)
            .shadow(color: .white, radius: 0, x: -2, y: -2)
            .shadow(color: .black.opacity(0.15), radius: 5, y: 6)
    }
}

/// The design's small repeating motions.
enum LoopMotion {
    case bob, breathe, droop, trot, tagPop

    var duration: Double {
        switch self {
        case .bob: 1.4
        case .breathe: 3.2
        case .droop: 4
        case .trot: 0.5
        case .tagPop: 1.6
        }
    }
}

struct LoopMotionModifier: ViewModifier {
    let motion: LoopMotion
    var duration: Double?
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    func body(content: Content) -> some View {
        if reduceMotion {
            content
        } else {
            content.phaseAnimator([false, true]) { view, up in
                switch motion {
                case .bob:
                    view.offset(y: up ? -10 : 0).rotationEffect(.degrees(up ? 1.5 : -1.5))
                case .breathe:
                    view.scaleEffect(up ? 1.03 : 1)
                case .droop:
                    view.offset(y: up ? 12 : 8).rotationEffect(.degrees(up ? -1 : -3))
                case .trot:
                    view.offset(y: up ? -5 : 0).rotationEffect(.degrees(up ? 3 : 0))
                case .tagPop:
                    view.rotationEffect(.degrees(up ? -6 : -12)).scaleEffect(up ? 1.12 : 1)
                }
            } animation: { _ in
                .easeInOut(duration: (duration ?? motion.duration) / 2)
            }
        }
    }
}

extension View {
    func loopMotion(_ motion: LoopMotion, duration: Double? = nil) -> some View {
        modifier(LoopMotionModifier(motion: motion, duration: duration))
    }
}

/// A dot that fades in and out, for "recording".
struct PulseDot: View {
    var color: Color = .opawAlert
    var size: CGFloat = 9
    @State private var dim = false

    var body: some View {
        Circle()
            .fill(color)
            .frame(width: size, height: size)
            .opacity(dim ? 0.25 : 1)
            .animation(.easeInOut(duration: 0.6).repeatForever(autoreverses: true), value: dim)
            .onAppear { dim = true }
    }
}

struct StatTile: View {
    let title: String
    let value: String
    var symbol: String? = nil

    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(value)
                .font(.opawNumber(24))
                .lineLimit(1)
                .minimumScaleFactor(0.6)
            HStack(spacing: 4) {
                if let symbol {
                    Image(systemName: symbol)
                        .foregroundStyle(Color.opawOrange)
                }
                Text(title)
            }
            .font(.caption)
            .foregroundStyle(.secondary)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

struct ProgressRing: View {
    let progress: Double
    var lineWidth: CGFloat = 8

    var body: some View {
        ZStack {
            Circle()
                .stroke(Color.opawChip, lineWidth: lineWidth)
            Circle()
                .trim(from: 0, to: min(1, max(0, progress)))
                .stroke(Color.opawOrange, style: StrokeStyle(lineWidth: lineWidth, lineCap: .round))
                .rotationEffect(.degrees(-90))
                .animation(.spring(response: 0.8, dampingFraction: 0.8), value: progress)
        }
    }
}

/// A care type icon in a soft circle.
struct CareGlyph: View {
    let symbol: String
    var size: CGFloat = 36
    var filled = false

    var body: some View {
        Image(systemName: symbol)
            .font(.system(size: size * 0.42, weight: .semibold))
            .foregroundStyle(filled ? Color.opawOnInk : Color.opawInk)
            .frame(width: size, height: size)
            .background(filled ? Color.opawInk : Color.opawChip, in: Circle())
    }
}

/// Small round photo of a dog, falling back to a paw.
struct DogThumbnail: View {
    let data: Data?
    var size: CGFloat = 44

    var body: some View {
        Group {
            if let data, let image = UIImage(data: data) {
                Image(uiImage: image)
                    .resizable()
                    .scaledToFill()
            } else {
                Image(systemName: "pawprint.fill")
                    .font(.system(size: size * 0.4))
                    .foregroundStyle(Color.opawOrange)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .background(Color.opawOrange.opacity(0.1))
            }
        }
        .frame(width: size, height: size)
        .clipShape(Circle())
    }
}

enum Haptics {
    static func success() {
        UINotificationFeedbackGenerator().notificationOccurred(.success)
    }

    static func tap() {
        UIImpactFeedbackGenerator(style: .light).impactOccurred()
    }

    static func select() {
        UISelectionFeedbackGenerator().selectionChanged()
    }

    static func soft() {
        UIImpactFeedbackGenerator(style: .soft).impactOccurred()
    }
}

/// Rounded heavy navigation titles across the app.
enum OpawAppearance {
    static func apply() {
        let appearance = UINavigationBarAppearance()
        appearance.configureWithTransparentBackground()
        appearance.backgroundColor = .opaw(light: 0xF4F4F5, dark: 0x0B0B0C)
        appearance.shadowColor = .clear
        if let large = UIFont(name: "Nunito-Black", size: 34) {
            appearance.largeTitleTextAttributes = [.font: large]
        }
        if let title = UIFont(name: "Nunito-Black", size: 18) {
            appearance.titleTextAttributes = [.font: title]
        }
        UINavigationBar.appearance().standardAppearance = appearance
        UINavigationBar.appearance().scrollEdgeAppearance = appearance
        UINavigationBar.appearance().compactAppearance = appearance
    }
}
