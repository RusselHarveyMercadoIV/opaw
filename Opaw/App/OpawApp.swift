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

    var body: some View {
        TabView(selection: $selection) {
            HomeView(selectedTab: $selection)
                .tabItem { Label("Today", systemImage: "pawprint.fill") }
                .tag(AppTab.home)
            WalkTabView()
                .tabItem { Label("Walk", systemImage: "figure.walk") }
                .tag(AppTab.walk)
            MemoriesView()
                .tabItem { Label("Memories", systemImage: "photo.stack") }
                .tag(AppTab.memories)
            PackView()
                .tabItem { Label("Pack", systemImage: "person.3.fill") }
                .tag(AppTab.pack)
            ProfileView()
                .tabItem { Label("Profile", systemImage: "person.crop.circle") }
                .tag(AppTab.profile)
        }
    }
}

enum AppTab: Hashable {
    case home, walk, memories, pack, profile
}
