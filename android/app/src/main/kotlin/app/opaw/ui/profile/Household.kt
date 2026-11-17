package app.opaw.ui.profile

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import app.opaw.OpawApplication
import app.opaw.data.Dog
import app.opaw.data.HouseholdMember
import app.opaw.services.HouseholdSync
import app.opaw.ui.components.BlackPillButton
import app.opaw.ui.components.DesignIcon
import app.opaw.ui.components.Ink
import app.opaw.ui.components.MiniFace
import app.opaw.ui.components.RowIcon
import app.opaw.ui.components.pressable
import app.opaw.ui.components.rememberHaptics
import app.opaw.ui.rememberApp
import app.opaw.ui.theme.Opaw
import app.opaw.ui.theme.opaw
import app.opaw.ui.theme.whiteCard
import kotlinx.coroutines.launch

/**
 * The household (4f): who looks after the pet, and the invite link card. Invites are real: the
 * link opens Opaw on the other person's phone, they join with the role picked here, and from then
 * on everyone logs to the same pet. [pet] gives the pet to share; during setup it isn't saved yet.
 */
@Composable
fun HouseholdCards(petId: String, petName: String, pet: () -> Dog?) {
    val app = rememberApp()
    val colors = Opaw.colors
    val scope = rememberCoroutineScope()
    val sync = app.household
    val role = sync.role(petId) ?: "owner"
    val isOwner = role == "owner"
    var refresh by remember { mutableIntStateOf(0) }
    var members by remember { mutableStateOf(emptyList<HouseholdSync.Member>()) }
    var invites by remember { mutableStateOf(emptyList<HouseholdSync.Invite>()) }
    var showInvite by remember { mutableStateOf(false) }
    var showJoin by remember { mutableStateOf(false) }
    val shared = sync.role(petId) != null

    LaunchedEffect(petId, shared, refresh) {
        if (!shared) return@LaunchedEffect
        runCatching { members = sync.members(petId) }
        if (isOwner) runCatching { invites = sync.invites(petId) }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.fillMaxWidth().whiteCard().padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 6.dp)) {
            Text("Household", style = opaw(17.sp, FontWeight.Black), color = colors.ink, modifier = Modifier.padding(top = 12.dp, bottom = 8.dp))
            if (isOwner) {
                MemberRow("You", "Owner · full access", "Owner", owner = true)
            } else {
                MemberRow("Owner", "Full access", "Owner", owner = true)
            }
            val me = app.pack.userId
            members.forEach { member ->
                val isMe = member.userId == me
                val title = roleTitle(member.role)
                MemberRow(
                    if (isMe) "You" else member.displayName.ifBlank { title },
                    "$title · ${roleDetail(member.role)}", title, owner = false,
                    action = when {
                        isMe -> "Leave $petName's household" to { scope.launch { runCatching { sync.leave(petId) } } }
                        isOwner -> "Remove ${member.displayName.ifBlank { "them" }}" to { scope.launch { runCatching { sync.removeMember(petId, member.userId) }; refresh++ } }
                        else -> null
                    },
                )
            }
            invites.forEach { invite ->
                MemberRow(
                    invite.inviteeName.ifBlank { "Someone" }, "${roleTitle(invite.role)} · hasn't opened the link yet", "Invited", owner = false,
                    action = "Cancel invite" to { scope.launch { runCatching { sync.cancelInvite(invite.code) }; refresh++ } },
                )
            }
        }
        if (isOwner) {
            ActionCard(DesignIcon.LINK, "Share invite link", "Pick a role before sending") { showInvite = true }
        }
        ActionCard(DesignIcon.USERS, "Join with a code", "Someone invited you to help with their pet") { showJoin = true }
    }

    if (showInvite) {
        InviteSheet(petName, onDismiss = { showInvite = false }) { name, memberRole ->
            val dog = pet() ?: throw IllegalStateException("Add $petName first.")
            val (code, link) = sync.invite(dog, name, memberRole)
            refresh++
            val message = "Help me look after $petName on Opaw! You're invited as ${roleTitle(memberRole).lowercase()} " +
                "(${roleDetail(memberRole)}). Everyone logs to the same pet, so meals never get doubled.\n\n" +
                "Tap to join: $link\nOr enter code $code in Opaw."
            message
        }
    }
    if (showJoin) JoinCodeDialog(app) { showJoin = false }
}

private fun roleTitle(role: String) = if (role == "walker") HouseholdMember.Role.WALKER.title else HouseholdMember.Role.FAMILY.title
private fun roleDetail(role: String) = if (role == "walker") HouseholdMember.Role.WALKER.detail else HouseholdMember.Role.FAMILY.detail

@Composable
private fun ActionCard(icon: DesignIcon, title: String, detail: String, onClick: () -> Unit) {
    val colors = Opaw.colors
    Row(
        Modifier.fillMaxWidth().pressable(onClick = onClick).whiteCard().padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RowIcon(icon, size = 44.dp, iconSize = 20.dp)
        Column {
            Text(title, style = opaw(16.sp, FontWeight.ExtraBold), color = colors.ink)
            Text(detail, style = opaw(13.sp, FontWeight.Medium), color = colors.muted)
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun MemberRow(name: String, detail: String, chip: String, owner: Boolean, action: Pair<String, () -> Unit>? = null) {
    val colors = Opaw.colors
    var menu by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier
                .fillMaxWidth()
                .combinedClickable(enabled = action != null, onLongClick = { menu = true }, onClick = { menu = true })
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
        if (action != null) {
            DropdownMenu(menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text(action.first, color = colors.alert) }, onClick = { menu = false; action.second() })
            }
        }
    }
}

/**
 * Pick who you're inviting and their role, then make the invite and send its link with the share
 * sheet. [makeInvite] makes it on the server and returns the message to send.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InviteSheet(petName: String, onDismiss: () -> Unit, makeInvite: suspend (name: String, role: String) -> String) {
    val colors = Opaw.colors
    val context = LocalContext.current
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var role by remember { mutableStateOf(HouseholdMember.Role.FAMILY) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val trimmed = name.trim()

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = colors.cream, shape = RoundedCornerShape(topStart = 40.dp, topEnd = 40.dp)) {
        Column(Modifier.padding(horizontal = 20.dp).navigationBarsPadding().padding(bottom = 8.dp)) {
            Text("Invite to $petName's household", style = opaw(22.sp, FontWeight.Black), color = colors.ink)
            Box(
                Modifier.padding(top = 18.dp).fillMaxWidth().height(56.dp).background(colors.chip, RoundedCornerShape(18.dp)).padding(horizontal = 18.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (name.isEmpty()) Text("Their name", style = opaw(20.sp, FontWeight.ExtraBold), color = colors.ghost)
                BasicTextField(
                    name, { name = it.take(40) },
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
            error?.let { Text(it, style = opaw(14.sp, FontWeight.SemiBold), color = colors.alert, modifier = Modifier.padding(top = 12.dp)) }
            Spacer(Modifier.height(24.dp))
            BlackPillButton(if (busy) "Making the link…" else "Send invite", enabled = trimmed.isNotEmpty() && !busy, height = 62.dp) {
                busy = true
                error = null
                scope.launch {
                    try {
                        val message = makeInvite(trimmed, if (role == HouseholdMember.Role.WALKER) "walker" else "family")
                        haptics.success()
                        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, message), null))
                        onDismiss()
                    } catch (failure: Throwable) {
                        error = HouseholdSync.readable(failure)
                    } finally {
                        busy = false
                    }
                }
            }
        }
    }
}

/** Type in an invite code from someone's message. Joining happens in [HouseholdJoinHost]. */
@Composable
fun JoinCodeDialog(app: OpawApplication, onDismiss: () -> Unit) {
    val colors = Opaw.colors
    var code by remember { mutableStateOf("") }
    val clean = code.uppercase().filter { it.isLetterOrDigit() }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.cream,
        title = { Text("Join a household", style = opaw(20.sp, FontWeight.Black), color = colors.ink) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Enter the code from your invite to help look after their pet.", style = opaw(15.sp, FontWeight.Medium), color = colors.muted)
                Box(
                    Modifier.fillMaxWidth().height(56.dp).background(colors.chip, RoundedCornerShape(18.dp)).padding(horizontal = 18.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (code.isEmpty()) Text("Invite code", style = opaw(20.sp, FontWeight.ExtraBold), color = colors.ghost)
                    BasicTextField(
                        code, { code = it.take(16) },
                        textStyle = opaw(20.sp, FontWeight.ExtraBold).copy(color = colors.ink, letterSpacing = 2.sp),
                        singleLine = true,
                        cursorBrush = SolidColor(colors.ink),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            TextButton({ app.household.offer(clean); onDismiss() }, enabled = clean.length >= 6) {
                Text("Join", style = opaw(16.sp, FontWeight.ExtraBold), color = if (clean.length >= 6) colors.ink else colors.ghost)
            }
        },
        dismissButton = { TextButton(onDismiss) { Text("Cancel", style = opaw(16.sp, FontWeight.SemiBold), color = colors.muted) } },
    )
}

/**
 * Joins an invite from a link or a typed code as soon as someone is signed in, over whatever
 * screen is showing, then says how it went.
 */
@Composable
fun HouseholdJoinHost(app: OpawApplication) {
    val sync = app.household
    val colors = Opaw.colors
    val code = sync.pendingCode
    val signedIn = app.pack.userId != null
    LaunchedEffect(code, signedIn) {
        if (code != null && signedIn) sync.joinPending()
    }
    if (sync.isJoining) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)).pressable {}, contentAlignment = Alignment.Center) {
            Column(
                Modifier.background(colors.cream, RoundedCornerShape(28.dp)).padding(horizontal = 32.dp, vertical = 26.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                CircularProgressIndicator(color = colors.ink)
                Text("Joining the household…", style = opaw(16.sp, FontWeight.ExtraBold), color = colors.ink)
            }
        }
    }
    sync.joinResult?.let { result ->
        AlertDialog(
            onDismissRequest = { sync.joinResult = null },
            containerColor = colors.cream,
            title = { Text(result.title, style = opaw(20.sp, FontWeight.Black), color = colors.ink) },
            text = { Text(result.message, style = opaw(15.sp, FontWeight.Medium), color = colors.muted) },
            confirmButton = { TextButton({ sync.joinResult = null }) { Text("OK", style = opaw(16.sp, FontWeight.ExtraBold), color = colors.ink) } },
        )
    }
}
