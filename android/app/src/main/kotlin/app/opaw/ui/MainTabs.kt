package app.opaw.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.opaw.ui.theme.Opaw
import app.opaw.ui.theme.Space
import app.opaw.ui.theme.opaw

enum class AppTab(val title: String, val icon: ImageVector) {
    HOME("Home", Icons.Filled.Home),
    CARE("Care", Icons.Filled.Pets),
    ACTIVITY("Walk", Icons.Filled.MonitorHeart),
    MEMORIES("Memories", Icons.Filled.Photo),
}

/** The four tabs with the floating bottom bar. Mirrors `MainTabView` in OpawApp.swift. */
@Composable
fun MainTabs() {
    var selection by rememberSaveable { mutableStateOf(AppTab.HOME) }
    // Detail screens and a running walk hide the bar, as in the design.
    val showsTabBar = true

    Box(Modifier.fillMaxSize()) {
        when (selection) {
            AppTab.HOME -> TabPlaceholder("Home")
            AppTab.CARE -> TabPlaceholder("Care")
            AppTab.ACTIVITY -> TabPlaceholder("Walk")
            AppTab.MEMORIES -> TabPlaceholder("Memories")
        }
        AnimatedVisibility(
            visible = showsTabBar,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
        ) {
            FloatingTabBar(selection, onSelect = { selection = it })
        }
    }
}

/**
 * The floating bottom bar: the current tab is a black pill with its name, the others are white
 * circles with a small name under the icon.
 */
@Composable
fun FloatingTabBar(selection: AppTab, onSelect: (AppTab) -> Unit) {
    val haptics = LocalHapticFeedback.current
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
            val interaction = remember { MutableInteractionSource() }
            Box(
                Modifier
                    .semantics { this.selected = selected }
                    .clickable(interaction, indication = null, role = Role.Tab) {
                        if (!selected) {
                            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
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
            .background(colors.ink, RoundedCornerShape(50))
            .padding(start = 20.dp, end = 24.dp)
            .animateContentSize(spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(tab.icon, contentDescription = null, tint = colors.onInk, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(10.dp))
        Text(tab.title, style = opaw(16.sp, FontWeight.ExtraBold), color = colors.onInk, maxLines = 1)
    }
}

@Composable
private fun Item(tab: AppTab) {
    val colors = Opaw.colors
    Column(
        Modifier
            .size(64.dp)
            .shadow(10.dp, CircleShape, ambientColor = Color.Black.copy(alpha = 0.08f), spotColor = Color.Black.copy(alpha = 0.08f))
            .background(colors.surface, CircleShape),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(tab.icon, contentDescription = null, tint = colors.ink, modifier = Modifier.size(20.dp))
        Spacer(Modifier.height(2.dp))
        Text(tab.title, style = opaw(10.sp, FontWeight.ExtraBold), color = colors.ink, maxLines = 1, textAlign = TextAlign.Center)
    }
}

/** Stands in for each tab until its screens are ported (Phases 2–5). */
@Composable
private fun TabPlaceholder(title: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(title, style = opaw(28.sp, FontWeight.Black), color = Opaw.colors.ink)
    }
}
