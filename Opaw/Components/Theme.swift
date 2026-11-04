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
    /// Rounded heavy display type, close to Nunito Black.
    static func opawDisplay(_ size: CGFloat, weight: Font.Weight = .black) -> Font {
        .system(size: size, weight: weight == .regular ? .bold : weight == .semibold ? .black : weight, design: .rounded)
    }

    /// Rounded text at a given weight.
    static func opaw(_ size: CGFloat, _ weight: Font.Weight = .bold) -> Font {
        .system(size: size, weight: weight, design: .rounded)
    }

    /// Rounded numerals for stats.
    static func opawNumber(_ size: CGFloat) -> Font {
        .system(size: size, weight: .black, design: .rounded).monospacedDigit()
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

    func body(content: Content) -> some View {
        content
            .shadow(color: .white, radius: 0, x: edge, y: 0)
            .shadow(color: .white, radius: 0, x: -edge, y: 0)
            .shadow(color: .white, radius: 0, x: 0, y: edge)
            .shadow(color: .white, radius: 0, x: 0, y: -edge)
            .shadow(color: .black.opacity(0.16), radius: 7, y: 10)
    }
}

extension View {
    func card(padding: CGFloat = 20) -> some View { modifier(CardModifier(padding: padding)) }

    func sticker(edge: CGFloat = 5) -> some View { modifier(StickerModifier(edge: edge)) }

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
            .font(.opaw(17, .heavy))
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
        Image(systemName: symbol)
            .font(.system(size: size * 0.38, weight: .bold))
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
        if let rounded = UIFont.systemFont(ofSize: 34, weight: .black).fontDescriptor.withDesign(.rounded) {
            appearance.largeTitleTextAttributes = [.font: UIFont(descriptor: rounded, size: 34)]
        }
        if let rounded = UIFont.systemFont(ofSize: 18, weight: .black).fontDescriptor.withDesign(.rounded) {
            appearance.titleTextAttributes = [.font: UIFont(descriptor: rounded, size: 18)]
        }
        UINavigationBar.appearance().standardAppearance = appearance
        UINavigationBar.appearance().scrollEdgeAppearance = appearance
        UINavigationBar.appearance().compactAppearance = appearance
    }
}
