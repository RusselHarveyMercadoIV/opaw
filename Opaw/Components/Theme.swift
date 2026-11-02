import SwiftUI
import UIKit

// MARK: - Design tokens
//
// Direction: smooth, minimalist white. Pure white canvas, near-black ink,
// one warm accent (persimmon), soft gray surfaces instead of shadows,
// a serif display face for headlines and generous whitespace.

extension Color {
    /// The single accent color. Defined in the asset catalog so it adapts to dark mode.
    static let opawOrange = Color("AccentColor")

    /// The app canvas: pure white in light mode, true black in dark mode.
    static let opawCream = Color(uiColor: .systemBackground)

    /// Primary text and the primary button fill.
    static let opawInk = Color(uiColor: UIColor { traits in
        traits.userInterfaceStyle == .dark
            ? UIColor(white: 0.96, alpha: 1)
            : UIColor(red: 0.07, green: 0.07, blue: 0.08, alpha: 1)
    })

    /// Text drawn on top of `opawInk`.
    static let opawOnInk = Color(uiColor: UIColor { traits in
        traits.userInterfaceStyle == .dark ? .black : .white
    })

    /// Soft surface for cards and controls, a whisper off white.
    static let opawSurface = Color(uiColor: UIColor { traits in
        traits.userInterfaceStyle == .dark
            ? UIColor(white: 0.11, alpha: 1)
            : UIColor(red: 0.965, green: 0.965, blue: 0.97, alpha: 1)
    })

    /// Hairlines and inactive marks.
    static let opawHairline = Color(uiColor: UIColor { traits in
        traits.userInterfaceStyle == .dark
            ? UIColor(white: 1, alpha: 0.12)
            : UIColor(white: 0, alpha: 0.08)
    })
}

enum Space {
    static let xs: CGFloat = 4
    static let s: CGFloat = 8
    static let m: CGFloat = 16
    static let l: CGFloat = 24
    static let xl: CGFloat = 32
    static let xxl: CGFloat = 48
}

enum Radius {
    static let control: CGFloat = 16
    static let card: CGFloat = 24
}

extension Font {
    /// Serif display face (New York) for headlines.
    static func opawDisplay(_ size: CGFloat, weight: Font.Weight = .semibold) -> Font {
        .system(size: size, weight: weight, design: .serif)
    }

    /// Rounded numerals for stats.
    static func opawNumber(_ size: CGFloat) -> Font {
        .system(size: size, weight: .semibold, design: .rounded).monospacedDigit()
    }
}

/// Small uppercase label above a headline, such as "01  NAME".
struct Eyebrow: View {
    let text: String

    var body: some View {
        Text(text.uppercased())
            .font(.system(size: 12, weight: .semibold))
            .tracking(1.6)
            .foregroundStyle(.secondary)
    }
}

// MARK: - Surfaces

/// A flat, soft card: no shadow, just a quiet surface.
struct CardModifier: ViewModifier {
    func body(content: Content) -> some View {
        content
            .padding(20)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(Color.opawSurface, in: RoundedRectangle(cornerRadius: Radius.card, style: .continuous))
    }
}

extension View {
    func card() -> some View { modifier(CardModifier()) }

    /// Screen background.
    func opawBackground() -> some View {
        background(Color.opawCream.ignoresSafeArea())
    }
}

// MARK: - Buttons

/// Full-width ink capsule with a soft press.
struct OpawPrimaryButtonStyle: ButtonStyle {
    @Environment(\.isEnabled) private var isEnabled

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.system(size: 17, weight: .semibold))
            .foregroundStyle(Color.opawOnInk)
            .frame(maxWidth: .infinity, minHeight: 56)
            .background(Color.opawInk.opacity(isEnabled ? 1 : 0.18), in: Capsule())
            .scaleEffect(configuration.isPressed ? 0.97 : 1)
            .animation(.spring(response: 0.3, dampingFraction: 0.7), value: configuration.isPressed)
    }
}

/// Full-width outlined capsule.
struct OpawSecondaryButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.system(size: 17, weight: .semibold))
            .foregroundStyle(Color.opawInk)
            .frame(maxWidth: .infinity, minHeight: 56)
            .background(Color.opawSurface, in: Capsule())
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
                .stroke(Color.opawHairline, lineWidth: lineWidth)
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
            .foregroundStyle(filled ? Color.white : Color.opawOrange)
            .frame(width: size, height: size)
            .background(filled ? Color.opawOrange : Color.opawOrange.opacity(0.1), in: Circle())
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

/// Serif navigation titles across the app.
enum OpawAppearance {
    static func apply() {
        let large = UIFont.systemFont(ofSize: 34, weight: .bold)
        let inline = UIFont.systemFont(ofSize: 17, weight: .semibold)
        let appearance = UINavigationBarAppearance()
        appearance.configureWithTransparentBackground()
        appearance.backgroundColor = .systemBackground
        appearance.shadowColor = .clear
        if let serif = large.fontDescriptor.withDesign(.serif) {
            appearance.largeTitleTextAttributes = [.font: UIFont(descriptor: serif, size: 34)]
        }
        if let serif = inline.fontDescriptor.withDesign(.serif) {
            appearance.titleTextAttributes = [.font: UIFont(descriptor: serif, size: 17)]
        }
        UINavigationBar.appearance().standardAppearance = appearance
        UINavigationBar.appearance().scrollEdgeAppearance = appearance
        UINavigationBar.appearance().compactAppearance = appearance
    }
}
