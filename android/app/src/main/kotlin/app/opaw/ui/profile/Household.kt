package app.opaw.ui.profile

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.opaw.data.HouseholdMember
import app.opaw.data.OpawSettings
import app.opaw.ui.components.BlackPillButton
import app.opaw.ui.components.DesignIcon
import app.opaw.ui.components.Ink
import app.opaw.ui.components.MiniFace
import app.opaw.ui.components.RowIcon
import app.opaw.ui.components.pressable
import app.opaw.ui.components.rememberHaptics
import app.opaw.ui.theme.Opaw
import app.opaw.ui.theme.opaw
import app.opaw.ui.theme.whiteCard
import kotlinx.coroutines.launch

/** The household list (4f): you as owner, then everyone invited, and the invite link card. */
@Composable
fun HouseholdCards(petName: String, settings: OpawSettings) {
    val colors = Opaw.colors
    val scope = rememberCoroutineScope()
    val members by settings.household.collectAsStateWithLifecycle(emptyList())
    var showInvite by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.fillMaxWidth().whiteCard().padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 6.dp)) {
            Text("Household", style = opaw(17.sp, FontWeight.Black), color = colors.ink, modifier = Modifier.padding(top = 12.dp, bottom = 8.dp))
            MemberRow("You", "Owner · full access", "Owner", owner = true)
            members.forEach { member ->
                MemberRow(member.name, "${member.role.title} · ${member.role.detail}", "Invited", owner = false) {
                    scope.launch { settings.setHousehold(members.filter { it.id != member.id }) }
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().pressable { showInvite = true }.whiteCard().padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RowIcon(DesignIcon.LINK, size = 44.dp, iconSize = 20.dp)
            Column {
                Text("Share invite link", style = opaw(16.sp, FontWeight.ExtraBold), color = colors.ink)
                Text("Pick a role before sending", style = opaw(13.sp, FontWeight.Medium), color = colors.muted)
            }
        }
    }

    if (showInvite) {
        InviteSheet(petName, onDismiss = { showInvite = false }) { member ->
            scope.launch { settings.setHousehold(members + member) }
            showInvite = false
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun MemberRow(name: String, detail: String, chip: String, owner: Boolean, onRemove: (() -> Unit)? = null) {
    val colors = Opaw.colors
    var menu by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier
                .fillMaxWidth()
                .combinedClickable(enabled = onRemove != null, onLongClick = { menu = true }, onClick = {})
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MiniFace(null, size = 44.dp, rim = 0.dp, fill = colors.track, initial = if (owner) null else name.take(1))
            Column(Modifier.weight(1f)) {
                Text(name, style = opaw(16.sp, FontWeight.ExtraBold), color = colors.ink)
                Text(detail, style = opaw(13.sp, FontWeight.Medium), color = colors.muted, maxLines = 1)
            }
            Text(
                chip,
                style = opaw(13.sp, FontWeight.Bold),
                color = if (owner) Color.White else Color(0xFF888888),
                modifier = Modifier.background(if (owner) Ink else colors.chip, CircleShape).padding(horizontal = 12.dp, vertical = 7.dp),
            )
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.hairline))
        DropdownMenu(menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text("Remove $name", color = colors.alert) }, onClick = { menu = false; onRemove?.invoke() })
        }
    }
}

/** Pick who you're inviting and their role, then send the link with the share sheet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InviteSheet(petName: String, onDismiss: () -> Unit, onInvite: (HouseholdMember) -> Unit) {
    val colors = Opaw.colors
    val context = LocalContext.current
    val haptics = rememberHaptics()
    var name by remember { mutableStateOf("") }
    var role by remember { mutableStateOf(HouseholdMember.Role.FAMILY) }
    val trimmed = name.trim()

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = colors.cream, shape = RoundedCornerShape(topStart = 40.dp, topEnd = 40.dp)) {
        Column(Modifier.padding(horizontal = 20.dp).navigationBarsPadding().padding(bottom = 8.dp)) {
            Text("Invite to the household", style = opaw(22.sp, FontWeight.Black), color = colors.ink)
            Box(
                Modifier.padding(top = 18.dp).fillMaxWidth().height(56.dp).background(colors.chip, RoundedCornerShape(18.dp)).padding(horizontal = 18.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (name.isEmpty()) Text("Their name", style = opaw(20.sp, FontWeight.ExtraBold), color = colors.ghost)
                BasicTextField(
                    name, { name = it },
                    textStyle = opaw(20.sp, FontWeight.ExtraBold).copy(color = colors.ink),
                    singleLine = true,
                    cursorBrush = SolidColor(colors.ink),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Column(Modifier.padding(top = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                HouseholdMember.Role.entries.forEach { option ->
                    val selected = role == option
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .height(60.dp)
                            .pressable { haptics.select(); role = option }
                            .background(if (selected) Ink else colors.chip, RoundedCornerShape(18.dp))
                            .padding(horizontal = 18.dp),
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(option.title, style = opaw(16.sp, FontWeight.ExtraBold), color = if (selected) Color.White else colors.ink)
                        Text(
                            option.detail.replaceFirstChar(Char::uppercase),
                            style = opaw(13.sp, FontWeight.Medium),
                            color = if (selected) Color.White.copy(alpha = 0.7f) else colors.muted,
                        )
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
            BlackPillButton("Send invite", enabled = trimmed.isNotEmpty(), height = 62.dp) {
                val message = "Help me look after $petName on Opaw! You're invited as ${role.title.lowercase()} (${role.detail}). " +
                    "Everyone logs to the same pet, so meals never get doubled."
                val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, message)
                context.startActivity(Intent.createChooser(send, null))
                // Android doesn't report whether the share went through, so the invite is kept once the sheet opens.
                onInvite(HouseholdMember(name = trimmed, role = role))
            }
        }
    }
}
