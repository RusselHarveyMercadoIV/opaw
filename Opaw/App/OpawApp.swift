import SwiftData
import SwiftUI

@main
struct OpawApp: App {
    @State private var walkRecorder = WalkRecorder()
    @State private var pack = PackService()

    init() {
        OpawAppearance.apply()
    }

    var body: some Scene {
        WindowGroup {
            RootView()
                .environment(walkRecorder)
                .environment(pack)
                .tint(.opawOrange)
        }
        .modelContainer(OpawStore.container)
    }
}

struct RootView: View {
    @Query(sort: \Dog.createdAt) private var dogs: [Dog]

    var body: some View {
        ZStack {
            if dogs.isEmpty {
                OnboardingView()
                    .transition(.opacity)
            } else {
                MainTabView()
                    .transition(.opacity.combined(with: .scale(scale: 1.04)))
            }
        }
        .animation(.easeInOut(duration: 0.6), value: dogs.isEmpty)
    }
}

struct MainTabView: View {
    @State private var selection: AppTab = .home
    @State private var tabBar = TabBarVisibility()
    @Environment(WalkRecorder.self) private var recorder

    var body: some View {
        TabView(selection: $selection) {
            HomeView(selectedTab: $selection)
                .tag(AppTab.home)
                .toolbar(.hidden, for: .tabBar)
            CareView(selectedTab: $selection)
                .tag(AppTab.care)
                .toolbar(.hidden, for: .tabBar)
            WalkTabView(selectedTab: $selection)
                .tag(AppTab.activity)
                .toolbar(.hidden, for: .tabBar)
            MemoriesView()
                .tag(AppTab.memories)
                .toolbar(.hidden, for: .tabBar)
        }
        .environment(tabBar)
        .overlay(alignment: .bottom) {
            if recorder.state == .idle && tabBar.hiddenCount == 0 {
                FloatingTabBar(selection: $selection)
                    .transition(.move(edge: .bottom).combined(with: .opacity))
            }
        }
        .animation(.spring(response: 0.4, dampingFraction: 0.85), value: recorder.state == .idle && tabBar.hiddenCount == 0)
    }
}

enum AppTab: Hashable, CaseIterable {
    case home, care, activity, memories

    var title: String {
        switch self {
        case .home: return "Home"
        case .care: return "Care"
        case .activity: return "Walk"
        case .memories: return "Memories"
        }
    }

    var symbol: String {
        switch self {
        case .home: return "house.fill"
        case .care: return "pawprint.fill"
        case .activity: return "waveform.path.ecg"
        case .memories: return "photo.fill"
        }
    }
}

/// The floating bottom bar: the current tab is a black pill with its name, the others are white circles.
struct FloatingTabBar: View {
    @Binding var selection: AppTab
    @Namespace private var pill

    var body: some View {
        HStack(spacing: 0) {
            ForEach(AppTab.allCases, id: \.self) { tab in
                if tab != AppTab.allCases.first { Spacer(minLength: 8) }
                Button {
                    guard selection != tab else { return }
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
        .padding(.horizontal, Space.l)
    }

    @ViewBuilder
    private func item(_ tab: AppTab) -> some View {
        if selection == tab {
            HStack(spacing: 10) {
                SymbolIcon(symbol: tab.symbol, size: 24, cutoutColor: Color.opawInk)
                Text(tab.title)
                    .font(.opaw(16, .heavy))
                    .lineLimit(1)
            }
            .foregroundStyle(Color.opawOnInk)
            .padding(.leading, 20)
            .padding(.trailing, 24)
            .frame(height: 64)
            .background {
                Capsule()
                    .fill(Color.opawInk)
                    .matchedGeometryEffect(id: "pill", in: pill)
            }
            .shadow(color: .black.opacity(0.22), radius: 14, y: 14)
        } else {
            SymbolIcon(symbol: tab.symbol, size: 24, cutoutColor: Color.opawSurface)
                .foregroundStyle(Color.opawInk)
                .frame(width: 64, height: 64)
                .background(Color.opawSurface, in: Circle())
                .shadow(color: .black.opacity(0.08), radius: 13, y: 10)
        }
    }
}

extension View {
    /// Leaves room under scrolling content for the floating tab bar.
    func tabBarInset() -> some View {
        safeAreaInset(edge: .bottom) { Color.clear.frame(height: 76) }
    }
}

/// Detail screens hide the floating tab bar while they're on screen, as in the design.
@Observable
final class TabBarVisibility {
    var hiddenCount = 0
}

private struct HidesFloatingTabBar: ViewModifier {
    @Environment(TabBarVisibility.self) private var tabBar: TabBarVisibility?

    func body(content: Content) -> some View {
        content
            .onAppear { tabBar?.hiddenCount += 1 }
            .onDisappear { tabBar.map { $0.hiddenCount = max(0, $0.hiddenCount - 1) } }
    }
}

extension View {
    func hidesFloatingTabBar() -> some View {
        modifier(HidesFloatingTabBar())
    }
}
