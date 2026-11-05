import SwiftUI

// Layouts for a foldable phone's open display (6a to 6p): wider than tall, split into two panes at
// the fold, with the tab buttons on a rail at the left edge. The closed display and every ordinary
// iPhone keep the phone layouts.

private struct DuoOpenKey: EnvironmentKey {
    static let defaultValue = false
}

private struct DuoClosedKey: EnvironmentKey {
    static let defaultValue = false
}

private struct WindowWidthKey: EnvironmentKey {
    static let defaultValue: CGFloat = 0
}

extension EnvironmentValues {
    /// True on a wide, open display (about 890 × 626), where screens split into two panes.
    var duoOpen: Bool {
        get { self[DuoOpenKey.self] }
        set { self[DuoOpenKey.self] = newValue }
    }

    /// True on a short, wide-ish portrait display like the closed outer screen (about 466 × 678),
    /// where Home tightens its vertical spacing (6d).
    var duoClosed: Bool {
        get { self[DuoClosedKey.self] }
        set { self[DuoClosedKey.self] = newValue }
    }

    /// The window's full width, so panes can find the fold wherever they sit.
    var windowWidth: CGFloat {
        get { self[WindowWidthKey.self] }
        set { self[WindowWidthKey.self] = newValue }
    }
}

enum Duo {
    /// Where the left pane starts when the rail is showing.
    static let railContentLeading: CGFloat = 96
    /// The width the rail takes from content on screens without a two-pane design.
    static let railInset: CGFloat = 78
    static let outerMargin: CGFloat = 22
    /// Gaps on each side of the fold, from the designs.
    static let foldGapLeft: CGFloat = 9
    static let foldGapRight: CGFloat = 25

    static func isOpen(_ size: CGSize) -> Bool {
        size.width >= 700 && size.width > size.height && size.height >= 500
    }

    static func isClosed(_ size: CGSize) -> Bool {
        size.height > size.width && size.height < 760 && size.width / size.height > 0.62
    }
}

extension View {
    /// Measures the window and tells every screen below whether the open layout applies.
    func duoEnvironment() -> some View {
        modifier(DuoEnvironmentModifier())
    }
}

private struct DuoEnvironmentModifier: ViewModifier {
    @State private var size = CGSize.zero

    func body(content: Content) -> some View {
        content
            .environment(\.duoOpen, Duo.isOpen(size))
            .environment(\.duoClosed, Duo.isClosed(size))
            .environment(\.windowWidth, size.width)
            .background {
                GeometryReader { proxy in
                    Color.clear
                        .onAppear { size = full(proxy) }
                        .onChange(of: full(proxy)) { _, new in size = new }
                }
            }
    }

    private func full(_ proxy: GeometryProxy) -> CGSize {
        CGSize(
            width: proxy.size.width + proxy.safeAreaInsets.leading + proxy.safeAreaInsets.trailing,
            height: proxy.size.height + proxy.safeAreaInsets.top + proxy.safeAreaInsets.bottom
        )
    }
}

/// Two panes split at the fold. The left pane starts after the rail (or at `leading` without it),
/// and `bleedLeft` stretches the left pane to the screen edge, for maps and the camera.
struct DuoPanes<Left: View, Right: View>: View {
    var rail = true
    var leading: CGFloat = 30
    var bleedLeft = false
    var showFold = true
    @ViewBuilder var left: Left
    @ViewBuilder var right: Right

    @Environment(\.windowWidth) private var windowWidth

    var body: some View {
        GeometryReader { proxy in
            let frame = proxy.frame(in: .global)
            let total = windowWidth > 0 ? windowWidth : frame.width
            let fold = total / 2 - frame.minX
            let start = (rail ? Duo.railContentLeading : leading) - frame.minX
            let leftStart = bleedLeft ? -frame.minX : start
            let leftWidth = bleedLeft ? fold - leftStart : fold - Duo.foldGapLeft - leftStart
            let rightWidth = frame.width - fold - Duo.foldGapRight - Duo.outerMargin
            ZStack(alignment: .topLeading) {
                left
                    .frame(width: max(0, leftWidth), height: proxy.size.height, alignment: .top)
                    .offset(x: leftStart)
                right
                    .frame(width: max(0, rightWidth), height: proxy.size.height, alignment: .top)
                    .offset(x: fold + Duo.foldGapRight)
                if showFold {
                    FoldLine()
                        .frame(width: 1, height: proxy.size.height + proxy.safeAreaInsets.top + proxy.safeAreaInsets.bottom)
                        .offset(x: fold, y: -proxy.safeAreaInsets.top)
                        .allowsHitTesting(false)
                }
            }
        }
    }
}

/// A pane's heading on the open display: the title on the left, buttons on the right, 54 high.
struct PaneHeader<Trailing: View>: View {
    let title: String
    var size: CGFloat = 26
    @ViewBuilder var trailing: Trailing

    var body: some View {
        HStack(spacing: 10) {
            Text(title)
                .font(.opaw(size, .black))
                .foregroundStyle(Color.opawInk)
                .lineLimit(1)
                .minimumScaleFactor(0.7)
            Spacer(minLength: 8)
            trailing
        }
        .frame(minHeight: 54)
        .padding(.top, 10)
        .background(Color.opawCream)
    }
}

extension PaneHeader where Trailing == EmptyView {
    init(title: String, size: CGFloat = 26) {
        self.init(title: title, size: size) { EmptyView() }
    }
}

/// Counts taps on the tab that's already showing, so a tab can go back to its first screen.
@Observable
final class TabReselect {
    var taps: [AppTab: Int] = [:]
}

/// Schedule on the left and History on the right (6f).
struct ScheduleHistoryDuo: View {
    let dog: Dog

    var body: some View {
        DuoPanes {
            ScheduleView(dog: dog, inPane: true)
        } right: {
            HistoryView(dog: dog, inPane: true)
        }
        .opawBackground()
        .toolbar(.hidden, for: .navigationBar)
    }
}

/// The faint line where the display folds.
struct FoldLine: View {
    var body: some View {
        LinearGradient(
            stops: [
                .init(color: .clear, location: 0),
                .init(color: .black.opacity(0.06), location: 0.2),
                .init(color: .black.opacity(0.06), location: 0.8),
                .init(color: .clear, location: 1),
            ],
            startPoint: .top,
            endPoint: .bottom
        )
    }
}

/// The four tab buttons stacked on the left edge (open display).
struct TabRail: View {
    @Binding var selection: AppTab
    var reselect: TabReselect?

    var body: some View {
        VStack(spacing: 12) {
            ForEach(AppTab.allCases, id: \.self) { tab in
                Button {
                    guard selection != tab else {
                        reselect?.taps[tab, default: 0] += 1
                        return
                    }
                    Haptics.select()
                    withAnimation(.spring(response: 0.4, dampingFraction: 0.8)) { selection = tab }
                } label: {
                    item(tab)
                }
                .buttonStyle(.pressable)
                .accessibilityLabel(tab.title)
                .accessibilityAddTraits(selection == tab ? .isSelected : [])
            }
        }
        .padding(.leading, 18)
    }

    @ViewBuilder
    private func item(_ tab: AppTab) -> some View {
        if selection == tab {
            VStack(spacing: 4) {
                SymbolIcon(symbol: tab.symbol, size: 20, cutoutColor: Color.opawInk)
                Text(tab.title)
                    .font(.opaw(11, .heavy))
                    .lineLimit(1)
                    .minimumScaleFactor(0.8)
            }
            .foregroundStyle(Color.opawOnInk)
            .frame(width: 60)
            .padding(.top, 12)
            .padding(.bottom, 10)
            .background(Color.opawInk, in: Capsule())
            .shadow(color: .black.opacity(0.22), radius: 14, y: 14)
        } else {
            SymbolIcon(symbol: tab.symbol, size: 20, cutoutColor: Color.opawSurface)
                .foregroundStyle(Color.opawInk)
                .frame(width: 60, height: 60)
                .background(Color.opawSurface, in: Circle())
                .shadow(color: .black.opacity(0.08), radius: 13, y: 10)
        }
    }
}

extension View {
    /// A sheet on the phone; full screen on the open display, where the screen has a two-pane design.
    func adaptiveSheet<Content: View>(isPresented: Binding<Bool>, wide: Bool, @ViewBuilder content: @escaping () -> Content) -> some View {
        modifier(AdaptiveSheet(isPresented: isPresented, wide: wide, sheetContent: content))
    }
}

private struct AdaptiveSheet<SheetContent: View>: ViewModifier {
    @Binding var isPresented: Bool
    let wide: Bool
    let sheetContent: () -> SheetContent

    func body(content: Content) -> some View {
        if wide {
            content.fullScreenCover(isPresented: $isPresented, content: sheetContent)
        } else {
            content.sheet(isPresented: $isPresented, content: sheetContent)
        }
    }
}
