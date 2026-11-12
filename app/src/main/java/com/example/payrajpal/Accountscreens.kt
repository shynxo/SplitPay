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
    val received = txs.filter { it.isCredit }.sumOf { it.amount.asDouble() }
    val spent = txs.filter { !it.isCredit }.sumOf { it.amount.asDouble() }

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
                        "No payments match.",
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
        "PRP" + Integer.toHexString("${tx.title}${tx.timestamp}${tx.amount}".hashCode()).uppercase()
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
                    StatusChip("UPI")
                }
                Spacer(Modifier.height(14.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DetailRow(if (tx.isCredit) "From" else "To", tx.title.toString())
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
                    OutlinePill(
                        "Report issue",
                        onClick = { toast(context, "Support arrives with the PayPal backend") },
                        modifier = Modifier.weight(1f),
                        icon = Icons.Outlined.Warning
                    )
                }
            }
        }
    }
}

/* ═════════════════════════════ AUTOPAYS ═════════════════════════════ */

private fun categoryIcon(category: String): ImageVector = when (category) {
    "Entertainment" -> Icons.Outlined.Movie
    "Mobile" -> Icons.Outlined.PhoneAndroid
    "Electricity" -> Icons.Outlined.Bolt
    "Rent" -> Icons.Outlined.Home
    "Investment" -> Icons.Outlined.TrendingUp
    else -> Icons.Outlined.Receipt
}

@Composable
fun AutopayScreen(vm: SplitViewModel, ui: PayUiViewModel, nav: Navigator) {
    val banks by vm.banks.collectAsState()
    val sourceName = (banks.find { it.isPrimary } ?: banks.firstOrNull())?.bankName ?: "Primary bank"
    var expanded by remember { mutableStateOf("") }
    var showAdd by remember { mutableStateOf(false) }
    var toCancel by remember { mutableStateOf<Autopay?>(null) }

    val active = ui.autopays.filter { it.active }
    val monthly = active.sumOf { it.monthlyEquivalent() }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            PayTopBar("Autopay", onBack = { nav.pop() }) {
                CircleIconButton(Icons.Filled.Add, "New autopay", onClick = { showAdd = true })
            }
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(bottom = 110.dp)) {
                item {
                    StackCard(color = Pal.Surface) {
                        SmallCaps("Per month")
                        Spacer(Modifier.height(10.dp))
                        Text(money(monthly), fontSize = 40.sp)
                        Text(
                            "${active.size} active · ${ui.autopays.size - active.size} paused",
                            fontSize = 13.sp,
                            color = Pal.Ink.copy(alpha = 0.6f)
                        )
                    }
                }
                itemsIndexed(ui.autopays) { index, a ->
                    val last = index == ui.autopays.lastIndex
                    StackCard(
                        color = Pal.stackAt(index),
                        overlap = if (last) 0.dp else 32.dp,
                        bottomRounded = last,
                        onClick = { expanded = if (expanded == a.id) "" else a.id }
                    ) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            WhiteBadge(categoryIcon(a.category), dimension = 44.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(a.name, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    "${money(a.amount)} · ${a.frequency}",
                                    fontSize = 13.sp,
                                    color = Pal.Ink.copy(alpha = 0.55f)
                                )
                            }
                            if (!a.active) StatusChip("Paused")
                        }
                        Expand(expanded == a.id) {
                            Column {
                                Spacer(Modifier.height(16.dp))
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    DetailRow("Next debit", a.nextDate)
                                    DetailRow("Pays from", a.source)
                                    DetailRow("Frequency", a.frequency)
                                    DetailRow("Mandate ID", "MND" + Integer.toHexString(a.id.hashCode()).uppercase())
                                }
                                Spacer(Modifier.height(16.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    OutlinePill(
                                        if (a.active) "Pause" else "Resume",
                                        onClick = { ui.toggleAutopay(a.id) },
                                        modifier = Modifier.weight(1f),
                                        icon = if (a.active) Icons.Outlined.Pause else Icons.Outlined.PlayArrow
                                    )
                                    OutlinePill(
                                        "Cancel",
                                        onClick = { toCancel = a },
                                        modifier = Modifier.weight(1f),
                                        tint = Pal.CoralDeep
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        PinnedCta {
            CoralPill("New autopay", subtitle = "Never miss a bill", onClick = { showAdd = true })
        }
    }

    if (showAdd) {
        AddAutopaySheet(
            source = sourceName,
            onDismiss = { showAdd = false },
            onCreate = { name, amount, freq ->
                ui.addAutopay(name, amount, freq, sourceName)
                showAdd = false
            }
        )
    }

    toCancel?.let { a ->
        AlertDialog(
            onDismissRequest = { toCancel = null },
            containerColor = Pal.Surface,
            shape = RoundedCornerShape(28.dp),
            title = { Text("Cancel ${a.name}?") },
            text = { Text("${money(a.amount)} will stop being debited from ${a.source}.") },
            confirmButton = {
                TextButton(onClick = {
                    ui.cancelAutopay(a.id)
                    toCancel = null
                }) { Text("Cancel autopay", color = Pal.Danger) }
            },
            dismissButton = { TextButton(onClick = { toCancel = null }) { Text("Keep it", color = Pal.Ink) } }
        )
    }
}

@Composable
private fun AddAutopaySheet(source: String, onDismiss: () -> Unit, onCreate: (String, Double, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var frequency by remember { mutableIntStateOf(1) }
    val frequencies = listOf("Weekly", "Monthly", "Yearly")
    val suggestions = listOf("Netflix", "Spotify", "Rent", "Electricity", "Jio", "SIP")
    val value = amount.toDoubleOrNull() ?: 0.0

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Pal.Bg,
        shape = RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp)
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("New autopay", fontSize = 24.sp)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                suggestions.forEach { s -> OutlinePill(s, onClick = { name = s }, compact = true, selected = name == s) }
            }
            PayTextField(name, { name = it }, "Biller or payee")
            PayTextField(
                value = amount,
                onValueChange = { input -> amount = input.filter { c -> c.isDigit() || c == '.' }.take(9) },
                label = "Amount (₹)",
                keyboardType = KeyboardType.Decimal
            )
            PillTabs(frequencies, frequency, { frequency = it })
            Text("Debited from $source", fontSize = 13.sp, color = Pal.Muted)
            CoralPill(
                title = "Set up autopay",
                enabled = name.isNotBlank() && value > 0.0,
                onClick = { onCreate(name.trim(), value, frequencies[frequency]) }
            )
        }
    }
}

/* ═════════════════════════════ WALLET: CARDS + BANKS ═════════════════════════════ */

@Composable
fun WalletScreen(vm: SplitViewModel, ui: PayUiViewModel, nav: Navigator) {
    val banks by vm.banks.collectAsState()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var expanded by remember { mutableStateOf("") }
    var showLink by remember { mutableStateOf(false) }
    var toRemove by remember { mutableStateOf<PayCard?>(null) }
    val cardColors = listOf(Pal.Butter, Pal.Green, Pal.Pink, Pal.Lavender)

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            PayTopBar("Wallet", onBack = { nav.pop() }) {
                CircleIconButton(
                    Icons.Filled.Add,
                    "Add",
                    onClick = { if (tab == 0) nav.push(Route.AddCard) else showLink = true }
                )
            }
            PillTabs(
                options = listOf("Cards", "Banks"),
                selected = tab,
                onSelect = {
                    tab = it
                    expanded = ""
                },
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 12.dp)
            )
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(bottom = 110.dp)) {
                if (tab == 0) {
                    if (ui.cards.isEmpty()) {
                        item {
                            Text(
                                "No cards yet. Add one to pay online and tap at terminals.",
                                modifier = Modifier.fillMaxWidth().padding(32.dp),
                                textAlign = TextAlign.Center,
                                color = Pal.Muted
                            )
                        }
                    }
                    itemsIndexed(ui.cards) { index, card ->
                        val key = "card:${card.id}"
                        val last = index == ui.cards.lastIndex
                        StackCard(
                            color = cardColors[index % cardColors.size],
                            overlap = if (last) 0.dp else 32.dp,
                            bottomRounded = last,
                            onClick = { expanded = if (expanded == key) "" else key }
                        ) {
                            Box(Modifier.graphicsLayer { alpha = if (card.frozen) 0.55f else 1f }) {
                                CardFace(
                                    network = card.network,
                                    numberText = "••••  ••••  ••••  ${card.last4}",
                                    holder = card.holder,
                                    expiry = card.expiry,
                                    badges = buildList {
                                        add(if (card.isCredit) "Credit" else "Debit")
                                        if (card.isDefault) add("Default")
                                        if (card.frozen) add("Frozen")
                                    }
                                )
                            }
                            Expand(expanded == key) {
                                Column {
                                    Spacer(Modifier.height(14.dp))
                                    Box(Modifier.fillMaxWidth().height(1.dp).background(Pal.Ink.copy(alpha = 0.1f)))
                                    Spacer(Modifier.height(6.dp))
                                    SettingRow("Tap to pay", card.tapEnabled, { ui.toggleCardTap(card.id) }, "Use at contactless terminals")
                                    SettingRow("Freeze card", card.frozen, { ui.toggleFreeze(card.id) }, "Blocks all payments instantly")
                                    Spacer(Modifier.height(10.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        if (!card.isDefault) {
                                            OutlinePill("Set default", onClick = { ui.setDefaultCard(card.id) }, modifier = Modifier.weight(1f))
                                        }
                                        OutlinePill("Remove", onClick = { toRemove = card }, modifier = Modifier.weight(1f), tint = Pal.CoralDeep)
                                    }
                                }
                            }
                        }
                    }
                } else {
                    itemsIndexed(banks) { index, bank ->
                        val key = "bank:${bank.id}"
                        val last = index == banks.lastIndex
                        BankStackItem(
                            name = bank.bankName.toString(),
                            balance = bank.balance.asDouble(),
                            isPrimary = bank.isPrimary,
                            color = cardColors[index % cardColors.size],
                            expanded = expanded == key,
                            last = last,
                            onToggle = { expanded = if (expanded == key) "" else key },
                            onMakePrimary = { vm.setPrimaryBank(bank.id) }
                        )
                    }
                }
            }
        }
        PinnedCta {
            if (tab == 0) {
                CoralPill("Add card", subtitle = "Debit or credit · Visa, Mastercard, RuPay", onClick = { nav.push(Route.AddCard) })
            } else {
                CoralPill("Link bank account", subtitle = "Protected by PayPal", onClick = { showLink = true })
            }
        }
    }

    if (showLink) {
        LinkBankSheet(
            onDismiss = { showLink = false },
            onLink = { bankName, last4 ->
                vm.addBankAccount(bankName, last4)
                showLink = false
            }
        )
    }

    toRemove?.let { card ->
        AlertDialog(
            onDismissRequest = { toRemove = null },
            containerColor = Pal.Surface,
            shape = RoundedCornerShape(28.dp),
            title = { Text("Remove card?") },
            text = { Text("${card.network} ••${card.last4} won't be available for payments or tap to pay.") },
            confirmButton = {
                TextButton(onClick = {
                    ui.removeCard(card.id)
                    toRemove = null
                }) { Text("Remove", color = Pal.Danger) }
            },
            dismissButton = { TextButton(onClick = { toRemove = null }) { Text("Keep", color = Pal.Ink) } }
        )
    }
}

@Composable
fun CardFace(network: String, numberText: String, holder: String, expiry: String, badges: List<String> = emptyList()) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                network,
                modifier = Modifier.weight(1f),
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                fontStyle = FontStyle.Italic,
                maxLines = 1
            )
            badges.forEach { b ->
                StatusChip(b)
                Spacer(Modifier.width(6.dp))
            }
            Icon(Icons.Outlined.Contactless, contentDescription = null)
        }
        Spacer(Modifier.height(22.dp))
        Text(numberText, fontSize = 20.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Medium, maxLines = 1)
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text("CARD HOLDER", fontSize = 10.sp, letterSpacing = 1.sp, color = Pal.Ink.copy(alpha = 0.55f))
                Text(holder.ifBlank { "YOUR NAME" }, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("EXPIRES", fontSize = 10.sp, letterSpacing = 1.sp, color = Pal.Ink.copy(alpha = 0.55f))
                Text(expiry.ifBlank { "MM/YY" }, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun BankStackItem(
    name: String,
    balance: Double,
    isPrimary: Boolean,
    color: Color,
    expanded: Boolean,
    last: Boolean,
    onToggle: () -> Unit,
    onMakePrimary: () -> Unit
) {
    var revealed by remember { mutableStateOf(false) }
    LaunchedEffect(revealed) {
        if (revealed) {
            delay(8000)
            revealed = false
        }
    }
    StackCard(
        color = color,
        overlap = if (last) 0.dp else 32.dp,
        bottomRounded = last,
        onClick = onToggle
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            WhiteBadge(Icons.Outlined.AccountBalance, dimension = 44.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(name, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("Savings account", fontSize = 13.sp, color = Pal.Ink.copy(alpha = 0.55f))
            }
            if (isPrimary) StatusChip("Primary")
        }
        Expand(expanded) {
            Column {
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text("Available balance", fontSize = 13.sp, color = Pal.Ink.copy(alpha = 0.55f))
                        AnimatedContent(targetState = revealed, label = "bankBalance") { shown ->
                            Text(if (shown) inr(balance) else "₹ ••••••", fontSize = 26.sp)
                        }
                    }
                    OutlinePill(if (revealed) "Hide" else "Check balance", onClick = { revealed = !revealed }, compact = true)
                }
                if (!isPrimary) {
                    Spacer(Modifier.height(14.dp))
                    OutlinePill("Make primary", onClick = onMakePrimary)
                }
            }
        }
    }
}

@Composable
fun LinkBankSheet(onDismiss: () -> Unit, onLink: (String, String) -> Unit) {
    val banks = remember { listOf("HDFC Bank", "ICICI Bank", "SBI", "Axis Bank", "Kotak Bank", "PNB") }
    var bankName by remember { mutableStateOf("") }
    var last4 by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Pal.Bg,
        shape = RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp)
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("Link a bank account", fontSize = 24.sp)
            Text("Pick your bank. Linking is handled by PayPal.", fontSize = 13.sp, color = Pal.Muted)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                banks.forEach { b -> OutlinePill(b, onClick = { bankName = b }, selected = bankName == b, compact = true) }
            }
            PayTextField(
                value = last4,
                onValueChange = { input -> last4 = input.filter(Char::isDigit).take(4) },
                label = "Last 4 digits of account",
                keyboardType = KeyboardType.Number
            )
            CoralPill(
                title = "Link via PayPal",
                enabled = bankName.isNotBlank() && last4.length == 4,
                onClick = { onLink(bankName, last4) }
            )
        }
    }
}

/* ═════════════════════════════ ADD CARD ═════════════════════════════ */

fun detectNetwork(digits: String): String {
    val p2 = digits.take(2).toIntOrNull() ?: -1
    val p4 = digits.take(4).toIntOrNull() ?: -1
    return when {
        digits.startsWith("4") -> "VISA"
        p2 == 34 || p2 == 37 -> "AMEX"
        p2 in 51..55 || p4 in 2221..2720 -> "MASTERCARD"
        digits.startsWith("60") || digits.startsWith("65") || digits.startsWith("81") ||
                digits.startsWith("82") || digits.startsWith("508") -> "RuPay"
        else -> "CARD"
    }
}

private fun groupDigits(digits: String): String = digits.chunked(4).joinToString(" ")

private fun formatExpiry(digits: String): String =
    if (digits.length > 2) digits.take(2) + "/" + digits.drop(2) else digits

@Composable
fun AddCardScreen(ui: PayUiViewModel, nav: Navigator) {
    val context = LocalContext.current
    // Typed card details live only in memory (not rememberSaveable) and only the last 4 digits are stored.
    var number by remember { mutableStateOf("") }
    var holder by remember { mutableStateOf("") }
    var expiry by remember { mutableStateOf("") }
    var cvv by remember { mutableStateOf("") }
    var credit by remember { mutableIntStateOf(0) } // 0 debit, 1 credit
    var tap by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }

    val network = detectNetwork(number)
    val maxLen = if (network == "AMEX") 15 else 16
    val month = expiry.take(2).toIntOrNull() ?: 0
    val valid = number.length == maxLen && holder.isNotBlank() && expiry.length == 4 &&
            month in 1..12 && cvv.length >= 3

    LaunchedEffect(saving) {
        if (saving) {
            delay(1300)
            ui.addCard(
                PayCard(
                    id = java.util.UUID.randomUUID().toString(),
                    network = network,
                    last4 = number.takeLast(4),
                    holder = holder.trim().uppercase(),
                    expiry = formatExpiry(expiry),
                    isCredit = credit == 1,
                    tapEnabled = tap
                )
            )
            toast(context, "Card added")
            nav.pop()
        }
    }

    Box(Modifier.fillMaxSize().imePadding()) {
        Column(Modifier.fillMaxSize()) {
            PayTopBar("Add card", onBack = { nav.pop() })
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 120.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(36.dp)).background(Pal.Butter).padding(24.dp)
                ) {
                    CardFace(
                        network = network,
                        numberText = groupDigits(number.padEnd(maxLen, '•')),
                        holder = holder.uppercase(),
                        expiry = formatExpiry(expiry)
                    )
                }
                PillTabs(listOf("Debit", "Credit"), credit, { credit = it })
                PayTextField(
                    value = groupDigits(number),
                    onValueChange = { input -> number = input.filter(Char::isDigit).take(16) },
                    label = "Card number",
                    keyboardType = KeyboardType.Number,
                    placeholder = "0000 0000 0000 0000",
                    trailing = { if (number.isNotEmpty()) StatusChip(network, container = Pal.Lavender) }
                )
                PayTextField(
                    value = holder,
                    onValueChange = { holder = it.take(26) },
                    label = "Name on card"
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PayTextField(
                        value = formatExpiry(expiry),
                        onValueChange = { input -> expiry = input.filter(Char::isDigit).take(4) },
                        label = "Expiry",
                        placeholder = "MM/YY",
                        keyboardType = KeyboardType.Number,
                        modifier = Modifier.weight(1f)
                    )
                    PayTextField(
                        value = cvv,
                        onValueChange = { input -> cvv = input.filter(Char::isDigit).take(4) },
                        label = "CVV",
                        keyboardType = KeyboardType.NumberPassword,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.weight(1f)
                    )
                }
                SettingRow("Use for tap to pay", tap, { tap = it }, "Pay at contactless terminals")
                Text(
                    "Your full card number is never stored on this device. PayPal tokenises it.",
                    fontSize = 12.sp,
                    color = Pal.Muted
                )
            }
        }
        PinnedCta {
            CoralPill("Add card", enabled = valid && !saving, loading = saving, onClick = { saving = true })
        }
    }
}

/* ═════════════════════════════ TAP TO PAY ═════════════════════════════ */

fun hasNfc(context: Context): Boolean = try {
    android.nfc.NfcAdapter.getDefaultAdapter(context) != null
} catch (e: Exception) {
    false
}

fun isNfcOn(context: Context): Boolean = try {
    android.nfc.NfcAdapter.getDefaultAdapter(context)?.isEnabled == true
} catch (e: Exception) {
    false
}

private fun openSettings(context: Context, action: String) {
    try {
        context.startActivity(Intent(action))
    } catch (e: Exception) {
        toast(context, "Couldn't open settings on this device")
    }
}

@Composable
fun TapToPayScreen(ui: PayUiViewModel, nav: Navigator) {
    val context = LocalContext.current
    var nfcOn by remember { mutableStateOf(isNfcOn(context)) }
    val nfcExists = remember { hasNfc(context) }
    var demo by remember { mutableIntStateOf(0) } // 0 idle, 1 reading, 2 done

    // Re-check NFC when the user comes back from system settings.
    val owner = context as? LifecycleOwner
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) nfcOn = isNfcOn(context)
        }
        owner?.lifecycle?.addObserver(observer)
        onDispose { owner?.lifecycle?.removeObserver(observer) }
    }

    LaunchedEffect(demo) {
        if (demo == 1) {
            delay(1800)
            demo = 2
        } else if (demo == 2) {
            delay(2400)
            demo = 0
        }
    }

    val defaultCard = ui.cards.firstOrNull { it.isDefault }
    val status = when {
        !ui.tapEnabled -> "Tap to pay is off"
        demo == 1 -> "Hold near the terminal…"
        demo == 2 -> "Payment complete"
        else -> "Ready to tap"
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 0.dp)) {
            item { PayTopBar("Tap to pay", onBack = { nav.pop() }) }
            item {
                StackCard(color = Pal.Surface) {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        ContactlessPulse(active = ui.tapEnabled && demo != 2, success = demo == 2)
                        Spacer(Modifier.height(8.dp))
                        Text(status, fontSize = 22.sp)
                        Text(
                            if (defaultCard != null) "${defaultCard.network} ••${defaultCard.last4}" else "No default card",
                            fontSize = 14.sp,
                            color = Pal.Ink.copy(alpha = 0.6f)
                        )
                    }
                }
            }
            item {
                StackCard(color = Pal.Lavender) {
                    SmallCaps("Default card")
                    Spacer(Modifier.height(12.dp))
                    if (ui.cards.isEmpty()) {
                        OutlinePill("Add a card", onClick = { nav.push(Route.AddCard) }, icon = Icons.Filled.Add)
                    } else {
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ui.cards.forEach { c ->
                                OutlinePill(
                                    "${c.network} ••${c.last4}",
                                    onClick = { ui.setDefaultCard(c.id) },
                                    selected = c.isDefault,
                                    compact = true
                                )
                            }
                        }
                    }
                }
            }
            item {
                StackCard(color = Pal.Butter) {
                    SmallCaps("Settings")
                    Spacer(Modifier.height(6.dp))
                    SettingRow("Enable tap to pay", ui.tapEnabled, { ui.tapEnabled = it }, "Pay at any contactless terminal")
                    SettingRow("Require unlock", ui.requireUnlock, { ui.requireUnlock = it }, "Fingerprint or PIN before each tap")
                }
            }
            item {
                StackCard(color = Pal.Green, overlap = 0.dp, bottomRounded = true, extraBottom = 100.dp) {
                    SmallCaps("Set up")
                    Spacer(Modifier.height(10.dp))
                    SetupStep(
                        done = nfcOn,
                        title = if (nfcExists) "NFC is on" else "NFC not found",
                        subtitle = if (nfcExists) "Needed to talk to the terminal" else "This device can't tap to pay",
                        action = if (nfcExists) "Turn on" else null,
                        onAction = { openSettings(context, Settings.ACTION_NFC_SETTINGS) }
                    )
                    SetupStep(
                        done = ui.cards.isNotEmpty(),
                        title = "Add a card",
                        subtitle = "At least one card to pay with",
                        action = "Add",
                        onAction = { nav.push(Route.AddCard) }
                    )
                    SetupStep(
                        done = false,
                        title = "Default payment app",
                        subtitle = "Android asks you to choose PayRajpal once",
                        action = "Choose",
                        onAction = { openSettings(context, "android.settings.NFC_PAYMENT_SETTINGS") }
                    )
                }
            }
        }
        PinnedCta {
            CoralPill(
                title = "Try a demo tap",
                subtitle = "Simulated · no money moves",
                enabled = ui.tapEnabled && demo == 0,
                onClick = { demo = 1 }
            )
        }
    }
}

@Composable
private fun SetupStep(done: Boolean, title: String, subtitle: String, action: String?, onAction: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(if (done) Pal.Ink else Color.Transparent)
                .border(1.5.dp, Pal.Ink, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (done) Icon(Icons.Filled.Check, contentDescription = null, tint = Pal.OnInk, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Text(subtitle, fontSize = 13.sp, color = Pal.Ink.copy(alpha = 0.55f))
        }
        if (action != null && !done) {
            Spacer(Modifier.width(8.dp))
            OutlinePill(action, onClick = onAction, compact = true)
        }
    }
}

@Composable
private fun ContactlessPulse(active: Boolean, success: Boolean) {
    val transition = rememberInfiniteTransition(label = "tapPulse")
    val ring by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1800, easing = LinearEasing)),
        label = "tapRing"
    )
    val scale = remember { Animatable(1f) }
    LaunchedEffect(success) {
        if (success) {
            scale.snapTo(0.7f)
            scale.animateTo(1f, tween(450, easing = FastOutSlowInEasing))
        }
    }
    Box(Modifier.size(200.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            if (active) {
                for (k in 0..2) {
                    val p = (ring + k / 3f) % 1f
                    drawCircle(
                        color = Pal.Coral.copy(alpha = (1f - p) * 0.4f),
                        radius = (this.size.minDimension / 2f) * (0.42f + 0.58f * p),
                        style = Stroke(width = 2.dp.toPx())
                    )
                }
            }
        }
        Box(
            Modifier
                .size(92.dp)
                .graphicsLayer {
                    scaleX = scale.value
                    scaleY = scale.value
                }
                .clip(CircleShape)
                .background(if (success) Pal.Green else Pal.Coral),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (success) Icons.Filled.Check else Icons.Outlined.Contactless,
                contentDescription = null,
                tint = if (success) Pal.Ink else Color.White,
                modifier = Modifier.size(44.dp)
            )
        }
    }
}

/* ═════════════════════════════ SECURITY & SETTINGS ═════════════════════════════ */

@Composable
fun SecurityScreen(ui: PayUiViewModel, nav: Navigator) {
    val context = LocalContext.current
    val limits = listOf(10000, 25000, 50000, 100000)
    val limitLabels = listOf("₹10k", "₹25k", "₹50k", "₹1L")
    val soon = { toast(context, "Coming with the PayPal backend") }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 0.dp)) {
        item { PayTopBar("Security", onBack = { nav.pop() }) }
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
                SmallCaps("App lock")
                Spacer(Modifier.height(6.dp))
                SettingRow("Biometric lock", ui.appLock, { ui.appLock = it }, "Ask for fingerprint when opening the app")
                Spacer(Modifier.height(8.dp))
                Text("Lock after", fontSize = 13.sp, color = Pal.Ink.copy(alpha = 0.6f))
                Spacer(Modifier.height(8.dp))
                PillTabs(listOf("Instantly", "1 min", "5 min"), ui.lockAfter, { ui.lockAfter = it })
            }
        }
        item {
            StackCard(color = Pal.Green) {
                SmallCaps("Daily payment limit")
                Spacer(Modifier.height(8.dp))
                Text(money(ui.dailyLimit.toDouble()), fontSize = 34.sp)
                Spacer(Modifier.height(10.dp))
                PillTabs(
                    options = limitLabels,
                    selected = limits.indexOf(ui.dailyLimit).coerceAtLeast(0),
                    onSelect = { ui.dailyLimit = limits[it] }
                )
            }
        }
        item {
            StackCard(color = Pal.Pink) {
                SmallCaps("Account")
                Spacer(Modifier.height(6.dp))
                ActionRow(Icons.Outlined.Lock, "Change UPI PIN", "Update the PIN used for payments", soon)
                ActionRow(Icons.Outlined.Devices, "Linked devices", "This phone and 1 other", soon)
                SettingRow("Payment notifications", ui.notifications, { ui.notifications = it })
            }
        }
        item {
            StackCard(color = Pal.Butter, overlap = 0.dp, bottomRounded = true, extraBottom = 24.dp) {
                SmallCaps("Support")
                Spacer(Modifier.height(6.dp))
                ActionRow(Icons.Outlined.Help, "Help & support", "FAQs and chat", soon)
                ActionRow(Icons.Outlined.ExitToApp, "Log out", "Sign out of this device", { Session.logout() })
            }
        }
    }
}