package app.opaw.ui

import app.opaw.ui.walks.WalkDetailScreen
import app.opaw.ui.walks.WalkTab
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.opaw.core.CareKind
import app.opaw.data.CareEvent
import app.opaw.data.dueDate
import app.opaw.services.CareRecorder
import app.opaw.ui.care.CareScreen
import app.opaw.ui.home.AvatarStudioScreen
import app.opaw.ui.home.FamilyTreeScreen
import app.opaw.ui.pack.PackScreen
import app.opaw.ui.premium.PaywallRoute
import app.opaw.ui.premium.SubscriptionScreen
import app.opaw.ui.home.HomeScreen
import app.opaw.ui.life.LifeRoute
import app.opaw.ui.life.LifePhotosRoute
import app.opaw.ui.life.LifeToastHost
import app.opaw.ui.welcome.ClaimAfterSignIn
import app.opaw.ui.memories.MemoriesScreen
import app.opaw.ui.memories.MemoryDetailScreen
import app.opaw.ui.profile.ChallengesScreen
import app.opaw.ui.profile.HouseholdScreen
import app.opaw.ui.profile.PetProfileScreen
import app.opaw.ui.profile.ProfileScreen
import app.opaw.ui.profile.UnitsScreen
import app.opaw.ui.care.HistoryScreen
import app.opaw.ui.care.ScheduleScreen
import app.opaw.ui.care.TodayScreen
import app.opaw.ui.care.rememberNow
import app.opaw.ui.components.DesignIcon
import app.opaw.ui.components.Icon
import app.opaw.ui.components.ToastHost
import app.opaw.ui.components.pressable
import app.opaw.ui.components.rememberHaptics
import app.opaw.ui.theme.Opaw
import app.opaw.ui.theme.Space
import app.opaw.ui.theme.opaw
import kotlinx.coroutines.launch

enum class AppTab(val title: String, val icon: DesignIcon) {
    HOME("Home", DesignIcon.HOME),
    CARE("Care", DesignIcon.PAW),
    ACTIVITY("Walk", DesignIcon.ACTIVITY),
    MEMORIES("Memories", DesignIcon.PHOTO),
}

/** Lets a screen switch tabs, as the iOS views do with their `selectedTab` binding. */
val LocalSelectTab = androidx.compose.runtime.staticCompositionLocalOf<(AppTab) -> Unit> { {} }

/** The four tabs with the floating bottom bar. Mirrors `MainTabView` in OpawApp.swift. */
@Composable
fun MainTabs() {
    var selection by rememberSaveable { mutableStateOf(AppTab.HOME) }
    val navigator = remember { Navigator() }
    val app = rememberApp()
    val walkActive = TabBarState.walkActive
    // Detail screens and a running walk hide the bar, as in the design.
    val showsTabBar = navigator.stack.isEmpty() && !walkActive

    BackHandler(enabled = navigator.stack.isNotEmpty()) { navigator.pop() }

    CompositionLocalProvider(LocalNavigator provides navigator, LocalSelectTab provides { selection = it }) {
        Box(Modifier.fillMaxSize().background(Opaw.colors.cream)) {
            when (selection) {
                AppTab.HOME -> HomeScreen()
                AppTab.CARE -> CareScreen()
                AppTab.ACTIVITY -> WalkTab()
                AppTab.MEMORIES -> MemoriesScreen()
            }
            AnimatedContent(
                targetState = navigator.stack.lastOrNull(),
                transitionSpec = {
                    (slideInHorizontally { it / 3 } + fadeIn()) togetherWith (slideOutHorizontally { it / 3 } + fadeOut())
                },
                label = "route",
            ) { route ->
                if (route != null) {
                    Box(Modifier.fillMaxSize().background(Opaw.colors.cream)) { RouteScreen(route) }
                }
            }
            AnimatedVisibility(
                visible = showsTabBar,
                modifier = Modifier.align(Alignment.BottomCenter),
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
            ) {
                FloatingTabBar(selection, onSelect = { selection = it })
            }
            // Toasts sit 12 points above the bottom buttons.
            ToastHost(bottom = if (showsTabBar) 100.dp else 40.dp)
            ToastWatcher()
            LifeToastHost()
            ClaimAfterSignIn()
        }
    }
    LaunchedEffect(app.toasts.showChallenges) {
        if (app.toasts.showChallenges) {
            app.toasts.showChallenges = false
            navigator.push(Route.Challenges)
        }
    }
}

/** State shared with the tab bar from screens deep inside a tab. */
object TabBarState {
    var walkActive by mutableStateOf(false)
}

@Composable
private fun RouteScreen(route: Route) {
    when (route) {
        is Route.Today -> TodayScreen(route.dogId)
        is Route.History -> HistoryScreen(route.dogId)
        is Route.Schedule -> ScheduleScreen(route.dogId)
        is Route.WalkDetail -> WalkDetailScreen(route.walkId)
        is Route.Profile -> ProfileScreen(route.dogId)
        is Route.PetProfile -> PetProfileScreen(route.dogId)
        is Route.FamilyTree -> FamilyTreeScreen(route.dogId)
        is Route.Studio -> AvatarStudioScreen(route.dogId)
        is Route.LifePhotos -> LifePhotosRoute(route.dogId)
        is Route.Life -> LifeRoute(route.dogId)
        is Route.Decorate -> app.opaw.ui.home.RoomDecorateScreen(route.dogId)
        is Route.MemoryDetail -> MemoryDetailScreen(route.memoryId)
        is Route.Household -> HouseholdScreen(route.dogId)
        Route.Units -> UnitsScreen()
        Route.Challenges -> ChallengesScreen()
        Route.Pack -> PackScreen()
        Route.Subscription -> SubscriptionScreen()
        Route.Paywall -> PaywallRoute()
        else -> TabSlot(route.toString())
    }
}

/** Shows a due toast when a meds dose is late. Mirrors `ToastWatcher` in Toasts.swift. */
@Composable
private fun ToastWatcher() {
    val app = rememberApp()
    val now = rememberNow()
    val loaded by app.pets.collectAsStateWithLifecycle()
    val all = loaded ?: emptyList()
    LaunchedEffect(now, all) {
        for (data in all) {
            val done = data.doneReminderIds(now)
            val late = data.reminders(now)
                .filter { it.kind == CareKind.MEDS && it.id !in done && it.dueDate(now) <= now }
                .minByOrNull { it.dueDate(now) } ?: continue
            app.toasts.showDue(late, now) {
                app.scope.launch { app.toasts.show(CareRecorder.log(app, CareEvent(dogId = data.dog.id, kindRaw = CareKind.MEDS.raw, note = late.title))) }
            }
            return@LaunchedEffect
        }
    }
}

/**
 * The floating bottom bar: the current tab is a black pill with its name, the others are white
 * circles with just their icon.
 */
@Composable
fun FloatingTabBar(selection: AppTab, onSelect: (AppTab) -> Unit) {
    val haptics = rememberHaptics()
    Row(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = Space.l, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppTab.entries.forEach { tab ->
            val selected = tab == selection
            Box(
                Modifier
                    .semantics { this.selected = selected }
                    .pressable(role = Role.Tab) {
                        if (!selected) {
                            haptics.select()
                            onSelect(tab)
                        }
                    },
            ) {
                if (selected) SelectedItem(tab) else Item(tab)
            }
        }
    }
}

@Composable
private fun SelectedItem(tab: AppTab) {
    val colors = Opaw.colors
    Row(
        Modifier
            .height(64.dp)
            .shadow(14.dp, CircleShape, ambientColor = Color.Black.copy(alpha = 0.22f), spotColor = Color.Black.copy(alpha = 0.22f))
            .background(colors.action, RoundedCornerShape(50))
            .padding(start = 20.dp, end = 24.dp)
            .animateContentSize(spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(tab.icon, size = 24.dp, tint = colors.onAction, cutoutColor = colors.action)
        Spacer(Modifier.width(10.dp))
        Text(tab.title, style = opaw(16.sp, FontWeight.ExtraBold), color = colors.onAction, maxLines = 1)
    }
}

/** A tab you're not on: just its icon in a white circle. Only the selected tab shows its name (v3 audit). */
@Composable
private fun Item(tab: AppTab) {
    val colors = Opaw.colors
    Box(
        Modifier
            .size(64.dp)
            .shadow(10.dp, CircleShape, ambientColor = Color.Black.copy(alpha = 0.08f), spotColor = Color.Black.copy(alpha = 0.08f))
            .background(colors.surface, CircleShape)
            .semantics { contentDescription = tab.title },
        contentAlignment = Alignment.Center,
    ) {
        Icon(tab.icon, size = 24.dp, tint = colors.ink, cutoutColor = colors.surface)
    }
}

/** Stands in for a tab until its screens are ported. */
@Composable
private fun TabSlot(title: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(title, style = opaw(28.sp, FontWeight.Black), color = Opaw.colors.ink)
    }
}
