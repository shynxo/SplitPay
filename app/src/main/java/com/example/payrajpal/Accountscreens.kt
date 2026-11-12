@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.payrajpal

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.CallMade
import androidx.compose.material.icons.filled.CallReceived
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Contactless
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.ExitToApp
import androidx.compose.material.icons.outlined.Help
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.TrendingUp
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.delay

/* ═════════════════════════════ ACTIVITY (PAYMENT HISTORY) ═════════════════════════════ */

@Composable
fun HistoryScreen(vm: SplitViewModel, nav: Navigator) {
    val txs by vm.transactions.collectAsState()
    var query by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    var filter by remember { mutableIntStateOf(0) }
    var expanded by remember { mutableIntStateOf(-1) }

    val shown = txs.filter { tx ->
        val passesFilter = filter == 0 || (filter == 1 && !tx.isCredit) || (filter == 2 && tx.isCredit)
        val passesQuery = query.isBlank() ||
                tx.title.toString().contains(query, ignoreCase = true) ||
                tx.subtitle.toString().contains(query, ignoreCase = true)
        passesFilter && passesQuery
    }
    val monthStart = remember { java.util.Calendar.getInstance().apply {
        set(java.util.Calendar.DAY_OF_MONTH, 1); set(java.util.Calendar.HOUR_OF_DAY, 0)
        set(java.util.Calendar.MINUTE, 0); set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
    }.timeInMillis }
    val received = txs.filter { it.isCredit && it.epochMillis >= monthStart }.sumOf { it.amount.asDouble() }
    val spent = txs.filter { !it.isCredit && it.epochMillis >= monthStart }.sumOf { it.amount.asDouble() }

    // New payments (for example someone paying your request link) show up without leaving the screen.
    AutoRefreshHistory(vm)

    Column(Modifier.fillMaxSize()) {
        PayTopBar("Activity", onBack = { nav.pop() }) {
            CircleIconButton(
                Icons.Filled.Search,
                "Search",
                onClick = {
                    searching = !searching
                    if (!searching) query = ""
                }
            )
        }
        Expand(searching) {
            PayTextField(
                value = query,
                onValueChange = {
                    query = it
                    expanded = -1
                },
                label = "Search payments",
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 10.dp)
            )
        }
        PillTabs(
            options = listOf("All", "Sent", "Received"),
            selected = filter,
            onSelect = {
                filter = it
                expanded = -1
            },
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 12.dp)
        )

        LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                StackCard(color = Pal.Surface) {
                    SmallCaps("This month")
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(36.dp)) {
                        Column {
                            Text(money(spent), fontSize = 30.sp)
                            Text("Spent", fontSize = 13.sp, color = Pal.Ink.copy(alpha = 0.6f))
                        }
                        Column {
                            Text(money(received), fontSize = 30.sp)
                            Text("Received", fontSize = 13.sp, color = Pal.Ink.copy(alpha = 0.6f))
                        }
                    }
                }
            }
            itemsIndexed(shown) { index, tx ->
                TxStackCard(
                    tx = tx,
                    color = Pal.stackAt(index),
                    expanded = expanded == index,
                    last = index == shown.lastIndex,
                    onToggle = { expanded = if (expanded == index) -1 else index }
                )
            }
            if (shown.isEmpty()) {
                item {
                    Text(
                        if (txs.isEmpty()) "No payments yet.\nPay or request money and it will show up here." else "No payments match.",
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                        textAlign = TextAlign.Center,
                        color = Pal.Muted
                    )
                }
            }
        }
    }
}

@Composable
private fun TxStackCard(tx: Transaction, color: Color, expanded: Boolean, last: Boolean, onToggle: () -> Unit) {
    val context = LocalContext.current
    val ref = remember(tx) {
        "PRP" + tx.id.filter { it.isLetterOrDigit() }.takeLast(8).uppercase()
    }
    val value = tx.amount.asDouble()
    val signed = (if (tx.isCredit) "+" else "-") + money(value)
    val rotation by animateFloatAsState(if (expanded) 180f else 0f, tween(300), label = "chevron")

    StackCard(
        color = color,
        overlap = if (last) 0.dp else 32.dp,
        bottomRounded = last,
        onClick = onToggle
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            WhiteBadge(
                if (tx.isCredit) Icons.Filled.CallReceived else Icons.Filled.CallMade,
                dimension = 44.dp,
                tint = if (tx.isCredit) Pal.Credit else Pal.Ink
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(tx.title.toString(), fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${tx.subtitle} · ${tx.timestamp}",
                    fontSize = 13.sp,
                    color = Pal.Ink.copy(alpha = 0.55f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(signed, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Icon(
                    Icons.Filled.KeyboardArrowDown,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp).graphicsLayer { rotationZ = rotation }
                )
            }
        }
        Expand(expanded) {
            Column {
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatusChip("Completed")
                    StatusChip("PayPal")
                }
                Spacer(Modifier.height(14.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DetailRow(if (tx.isCredit) "From" else "To", tx.title.toString())
                    if (tx.email.isNotBlank()) DetailRow("PayPal email", tx.email)
                    DetailRow("Note", tx.subtitle.toString())
                    DetailRow("Time", tx.timestamp.toString())
                    DetailRow("Reference ID", ref)
                }
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinePill(
                        "Share receipt",
                        onClick = { shareText(context, "PayRajpal receipt · ${tx.title} · ${money(value)} · Ref $ref") },
                        modifier = Modifier.weight(1f),
                        icon = Icons.Outlined.Share
                    )
                }
            }
        }
    }
}

/* ═════════════════════════════ SECURITY & SETTINGS ═════════════════════════════ */

@Composable
fun SecurityScreen(ui: PayUiViewModel, nav: Navigator) {
    val context = LocalContext.current

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 0.dp)) {
        item { PayTopBar("Settings", onBack = { nav.pop() }) }
        item {
            StackCard(color = Pal.Surface) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AvatarCircle(Session.name.ifBlank { "You" }, 60.dp)
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(Session.name.ifBlank { "You" }, fontSize = 22.sp, fontWeight = FontWeight.Medium)
                        Text(Session.email, fontSize = 14.sp, color = Pal.Ink.copy(alpha = 0.6f))
                    }
                }
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatusChip("PayPal verified", container = Pal.Green)
                    OutlinePill("Copy PayPal email", onClick = { copyText(context, "PayPal email", Session.email) }, compact = true)
                }
            }
        }
        item {
            StackCard(color = Pal.Butter) {
                SmallCaps("Appearance")
                Spacer(Modifier.height(6.dp))
                SettingRow(
                    title = "Dark mode",
                    checked = Pal.dark,
                    onChange = { Pal.dark = it },
                    subtitle = "Easier on the eyes at night"
                )
            }
        }
        item {
            StackCard(color = Pal.Lavender) {
                SmallCaps("PIN & lock")
                Spacer(Modifier.height(6.dp))
                ActionRow(Icons.Outlined.Lock, "Change PIN", "Used to open the app and confirm payments") {
                    nav.push(Route.ChangePin)
                }
                SettingRow(
                    "Ask for PIN when app opens",
                    PinStore.lockOnOpen,
                    { PinStore.changeLockOnOpen(it) },
                    "Payments always ask for your PIN"
                )
            }
        }
        item {
            StackCard(color = Pal.Pink, overlap = 0.dp, bottomRounded = true, extraBottom = 24.dp) {
                SmallCaps("Account")
                Spacer(Modifier.height(6.dp))
                ActionRow(Icons.Outlined.ExitToApp, "Log out", "Sign out of this device", {
                    Session.logout()
                    nav.stack.clear()
                    nav.stack.add(Route.Home)
                })
            }
        }
    }
}