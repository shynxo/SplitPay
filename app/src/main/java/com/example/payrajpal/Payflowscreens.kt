package com.example.payrajpal

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Dialpad
import androidx.compose.material.icons.outlined.FlashOff
import androidx.compose.material.icons.outlined.FlashOn
import androidx.compose.material.icons.outlined.LocalGasStation
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Opacity
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random

/* ═════════════════════════════ PAY FLOW ═════════════════════════════
 * Steps: 0 recipient · 1 amount · 2 PIN · 3 processing · 4 success
 * Requests skip the PIN (no money leaves the account).
 */

@Composable
fun PayFlowScreen(route: Route.Pay, vm: SplitViewModel, ui: PayUiViewModel, nav: Navigator) {
    val kind = route.kind
    val banks by vm.banks.collectAsState()

    var step by rememberSaveable { mutableIntStateOf(if (route.recipient.isNotBlank()) 1 else 0) }
    var recipient by rememberSaveable { mutableStateOf(route.recipient) }
    var detail by rememberSaveable { mutableStateOf("") }
    var amount by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    var sourceIndex by rememberSaveable { mutableIntStateOf(0) }
    var pin by remember { mutableStateOf("") } // deliberately NOT saved
    val haptics = LocalHaptics.current
    val refId = rememberSaveable { "PRP" + (100000000..999999999).random() }
    val paidAt = remember { SimpleDateFormat("d MMM yyyy, h:mm a", Locale.getDefault()).format(Date()) }

    val sources = banks.map { it.bankName.toString() } + ui.cards.map { "${it.network} ••${it.last4}" }
    val sourceLabel = sources.getOrElse(sourceIndex) { "your bank" }
    val amountValue = amount.toDoubleOrNull() ?: 0.0

    BackHandler(enabled = step in 1..2) {
        pin = ""
        if (step == 1 && route.recipient.isNotBlank()) nav.pop() else step -= 1
    }
    BackHandler(enabled = step == 3) { /* can't leave while processing */ }

    LaunchedEffect(pin) {
        if (pin.length == 4) {
            delay(200)
            step = 3
        }
    }
    LaunchedEffect(step) {
        if (step == 3) {
            delay(1700)
            if (!kind.isRequest) vm.sendDirectPayment(recipient, amountValue, note)
            step = 4
            haptics.heavy()
        }
    }

    AnimatedContent(
        targetState = step,
        transitionSpec = {
            if (targetState > initialState) {
                (slideInHorizontally(tween(300)) { it / 6 } + fadeIn(tween(300))) togetherWith
                        (slideOutHorizontally(tween(240)) { -it / 6 } + fadeOut(tween(160)))
            } else {
                (slideInHorizontally(tween(300)) { -it / 6 } + fadeIn(tween(300))) togetherWith
                        (slideOutHorizontally(tween(240)) { it / 6 } + fadeOut(tween(160)))
            }
        },
        label = "payStep"
    ) { s ->
        Box(Modifier.fillMaxSize().background(Pal.Bg)) {
            when (s) {
                0 -> when (kind) {
                    PayKind.PAY, PayKind.REQUEST -> ContactStep(kind, ui, onBack = { nav.pop() }, onPick = { name, d, preset ->
                        recipient = name
                        detail = d
                        if (preset.isNotEmpty()) amount = preset
                        step = 1
                    })
                    PayKind.BANK -> BankFormStep(kind, onBack = { nav.pop() }, onPick = { name, d, preset ->
                        recipient = name
                        detail = d
                        if (preset.isNotEmpty()) amount = preset
                        step = 1
                    })
                    PayKind.RECHARGE -> RechargeStep(kind, onBack = { nav.pop() }, onPick = { name, d, preset ->
                        recipient = name
                        detail = d
                        if (preset.isNotEmpty()) amount = preset
                        step = 1
                    })
                    PayKind.BILLS -> BillStep(kind, onBack = { nav.pop() }, onPick = { name, d, preset ->
                        recipient = name
                        detail = d
                        if (preset.isNotEmpty()) amount = preset
                        step = 1
                    })
                }
                1 -> AmountStep(
                    kind = kind,
                    recipient = recipient,
                    detail = detail,
                    amount = amount,
                    onAmount = { amount = it },
                    note = note,
                    onNote = { note = it },
                    sources = sources,
                    sourceIndex = sourceIndex,
                    onSource = { sourceIndex = it },
                    onBack = { if (route.recipient.isNotBlank()) nav.pop() else step = 0 },
                    onContinue = {
                        pin = ""
                        step = if (kind.isRequest) 3 else 2
                    }
                )
                2 -> PinStep(
                    recipient = recipient,
                    amount = amountValue,
                    source = sourceLabel,
                    pin = pin,
                    onKey = { key -> pin = if (key == "⌫") pin.dropLast(1) else if (pin.length < 4) pin + key else pin },
                    onBack = {
                        pin = ""
                        step = 1
                    }
                )
                3 -> ProcessingStep(kind)
                else -> SuccessStep(
                    kind = kind,
                    recipient = recipient,
                    amount = amountValue,
                    source = sourceLabel,
                    refId = refId,
                    paidAt = paidAt,
                    onDone = { nav.pop() }
                )
            }
        }
    }
}

/* ───────────── step 0 variants ───────────── */

@Composable
private fun ContactStep(
    kind: PayKind,
    ui: PayUiViewModel,
    onBack: () -> Unit,
    onPick: (String, String, String) -> Unit
) {
    var query by remember { mutableStateOf("") }
    val q = query.trim()
    val digits = q.filter(Char::isDigit)
    val looksLikeNumber = digits.length >= 10 && q.all { it.isDigit() || it == ' ' || it == '+' || it == '-' }
    val looksLikeUpi = q.contains("@") && q.length > 3
    val matches = ui.contacts.filter { c ->
        q.isBlank() ||
                c.name.contains(q, ignoreCase = true) ||
                (digits.isNotEmpty() && c.phone.filter(Char::isDigit).contains(digits))
    }

    Column(Modifier.fillMaxSize()) {
        PayTopBar(kind.title, onBack)
        PayTextField(
            value = query,
            onValueChange = { query = it },
            label = "Name, phone number or UPI ID",
            modifier = Modifier.padding(horizontal = 20.dp),
            leading = { Icon(Icons.Filled.Search, contentDescription = null) }
        )
        Spacer(Modifier.height(14.dp))
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .clip(RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp))
                .background(Pal.Surface)
        ) {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 40.dp)
            ) {
                if (looksLikeNumber || looksLikeUpi) {
                    item {
                        Row(
                            Modifier.fillMaxWidth().bounceClick { onPick(q, q, "") }.padding(bottom = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                Modifier.size(46.dp).clip(CircleShape).background(Pal.Coral),
                                contentAlignment = Alignment.Center
                            ) { Icon(Icons.Filled.KeyboardArrowRight, contentDescription = null) }
                            Spacer(Modifier.width(14.dp))
                            Column {
                                Text(if (kind.isRequest) "Request from $q" else "Pay $q", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                                Text("New recipient", fontSize = 13.sp, color = Pal.Muted)
                            }
                        }
                    }
                }
                item { SmallCaps("Contacts", Modifier.padding(bottom = 6.dp)) }
                itemsIndexed(matches) { _, c ->
                    Row(
                        Modifier.fillMaxWidth().bounceClick { onPick(c.name, c.phone, "") }.padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AvatarCircle(c.name, 46.dp)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(c.name, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                            Text(c.phone, fontSize = 13.sp, color = Pal.Muted)
                        }
                    }
                }
                if (matches.isEmpty() && !looksLikeNumber && !looksLikeUpi) {
                    item { Text("No contacts found. Type a number or UPI ID instead.", fontSize = 14.sp, color = Pal.Muted) }
                }
            }
        }
    }
}

@Composable
private fun BankFormStep(kind: PayKind, onBack: () -> Unit, onPick: (String, String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var account by remember { mutableStateOf("") }
    var ifsc by remember { mutableStateOf("") }

    Box(Modifier.fillMaxSize().imePadding()) {
        Column(Modifier.fillMaxSize()) {
            PayTopBar(kind.title, onBack)
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 120.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                PayTextField(name, { name = it }, "Account holder name")
                PayTextField(
                    value = account,
                    onValueChange = { input -> account = input.filter(Char::isDigit).take(18) },
                    label = "Account number",
                    keyboardType = KeyboardType.Number
                )
                PayTextField(ifsc, { input -> ifsc = input.uppercase().take(11) }, "IFSC code")
                Text("IMPS transfers arrive instantly, any time of day.", fontSize = 13.sp, color = Pal.Muted)
            }
        }
        PinnedCta {
            CoralPill(
                title = "Continue",
                enabled = name.isNotBlank() && account.length >= 9 && ifsc.length == 11,
                onClick = { onPick(name.trim(), "A/C ••${account.takeLast(4)} · $ifsc", "") }
            )
        }
    }
}

@Composable
private fun RechargeStep(kind: PayKind, onBack: () -> Unit, onPick: (String, String, String) -> Unit) {
    var number by remember { mutableStateOf("") }
    var operator by remember { mutableIntStateOf(0) }
    var plan by remember { mutableIntStateOf(1) }
    val operators = listOf("Jio", "Airtel", "Vi", "BSNL")
    val plans = listOf(199, 299, 399, 599, 799)

    Box(Modifier.fillMaxSize().imePadding()) {
        Column(Modifier.fillMaxSize()) {
            PayTopBar(kind.title, onBack)
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 120.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                PayTextField(
                    value = number,
                    onValueChange = { input -> number = input.filter(Char::isDigit).take(10) },
                    label = "Mobile number",
                    keyboardType = KeyboardType.Phone
                )
                SmallCaps("Operator")
                PillTabs(operators, operator, { operator = it })
                SmallCaps("Plan")
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    plans.forEachIndexed { i, p ->
                        OutlinePill("₹$p", onClick = { plan = i }, selected = plan == i, compact = true)
                    }
                }
            }
        }
        PinnedCta {
            CoralPill(
                title = "Continue",
                enabled = number.length == 10,
                onClick = { onPick("${operators[operator]} · $number", "Prepaid recharge", plans[plan].toString()) }
            )
        }
    }
}

@Composable
private fun BillStep(kind: PayKind, onBack: () -> Unit, onPick: (String, String, String) -> Unit) {
    val categories: List<Pair<String, ImageVector>> = listOf(
        Pair("Electricity", Icons.Outlined.Bolt),
        Pair("Water", Icons.Outlined.Opacity),
        Pair("Gas", Icons.Outlined.LocalGasStation),
        Pair("Broadband", Icons.Outlined.Wifi),
        Pair("DTH", Icons.Outlined.Tv),
        Pair("Postpaid", Icons.Outlined.PhoneAndroid)
    )
    var selected by remember { mutableIntStateOf(0) }
    var consumer by remember { mutableStateOf("") }

    Box(Modifier.fillMaxSize().imePadding()) {
        Column(Modifier.fillMaxSize()) {
            PayTopBar(kind.title, onBack)
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 120.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                categories.chunked(3).forEachIndexed { rowIndex, rowItems ->
                    Row(Modifier.fillMaxWidth()) {
                        rowItems.forEachIndexed { colIndex, cat ->
                            val index = rowIndex * 3 + colIndex
                            val isSelected = index == selected
                            Column(
                                Modifier.weight(1f).bounceClick { selected = index },
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Box(
                                    Modifier
                                        .size(62.dp)
                                        .clip(CircleShape)
                                        .background(if (isSelected) Pal.Ink else Pal.Surface),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        cat.second,
                                        contentDescription = cat.first,
                                        tint = if (isSelected) Pal.OnInk else Pal.Ink,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                                Spacer(Modifier.height(8.dp))
                                Text(cat.first, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
                PayTextField(
                    value = consumer,
                    onValueChange = { input -> consumer = input.take(20) },
                    label = "Consumer / account number"
                )
            }
        }
        PinnedCta {
            CoralPill(
                title = "Fetch bill",
                subtitle = "Demo amount until BBPS is connected",
                enabled = consumer.length >= 4,
                onClick = {
                    // Deterministic demo amount so the same consumer number always shows the same bill.
                    val demo = 400 + (consumer.hashCode() and 0x7FFFFFFF) % 1800
                    onPick("${categories[selected].first} bill", consumer, demo.toString())
                }
            )
        }
    }
}

/* ───────────── step 1: amount ───────────── */

private fun appendAmountKey(current: String, key: String): String = when (key) {
    "⌫" -> current.dropLast(1)
    "." -> if ("." in current) current else if (current.isEmpty()) "0." else "$current."
    else -> when {
        current == "0" -> key
        "." in current && current.substringAfter(".").length >= 2 -> current
        "." !in current && current.length >= 7 -> current
        else -> current + key
    }
}

private val groupingFormat: NumberFormat = NumberFormat.getNumberInstance(Locale.forLanguageTag("en-IN"))

private fun typedAmount(raw: String): String {
    if (raw.isEmpty()) return "0"
    val whole = raw.substringBefore(".")
    val fraction = if (raw.contains(".")) "." + raw.substringAfter(".") else ""
    val grouped = whole.toLongOrNull()?.let { groupingFormat.format(it) } ?: "0"
    return grouped + fraction
}

@Composable
private fun AmountStep(
    kind: PayKind,
    recipient: String,
    detail: String,
    amount: String,
    onAmount: (String) -> Unit,
    note: String,
    onNote: (String) -> Unit,
    sources: List<String>,
    sourceIndex: Int,
    onSource: (Int) -> Unit,
    onBack: () -> Unit,
    onContinue: () -> Unit
) {
    var menu by remember { mutableStateOf(false) }
    var showNote by remember { mutableStateOf(note.isNotBlank()) }
    val value = amount.toDoubleOrNull() ?: 0.0
    val sourceLabel = sources.getOrElse(sourceIndex) { "Bank" }

    Column(Modifier.fillMaxSize()) {
        PayTopBar(kind.title, onBack)
        Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            AvatarCircle(recipient, 44.dp)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(recipient, fontSize = 17.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (detail.isNotBlank()) Text(detail, fontSize = 13.sp, color = Pal.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }

        Column(
            Modifier.weight(1f).fillMaxWidth().padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Text("₹", fontSize = 30.sp, color = Pal.Muted, modifier = Modifier.padding(top = 8.dp, end = 4.dp))
                Text(
                    typedAmount(amount),
                    fontSize = if (amount.length > 8) 44.sp else 60.sp,
                    color = if (amount.isEmpty()) Pal.Hairline else Pal.Ink,
                    maxLines = 1
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(100, 500, 1000).forEach { v ->
                    OutlinePill("₹$v", onClick = { onAmount(v.toString()) }, compact = true)
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box {
                    OutlinePill(
                        text = (if (kind.isRequest) "Credit to: " else "From: ") + sourceLabel,
                        onClick = { menu = true },
                        compact = true,
                        icon = Icons.Outlined.AccountBalance
                    )
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        sources.forEachIndexed { i, s ->
                            DropdownMenuItem(
                                text = { Text(s) },
                                onClick = {
                                    onSource(i)
                                    menu = false
                                }
                            )
                        }
                    }
                }
                OutlinePill(if (showNote) "Hide note" else "Add note", onClick = { showNote = !showNote }, compact = true)
            }
            Expand(showNote) {
                PayTextField(note, onNote, "Note (optional)", Modifier.padding(top = 12.dp))
            }
        }

        Column(Modifier.navigationBarsPadding().padding(bottom = 10.dp)) {
            Column(Modifier.padding(horizontal = 12.dp)) {
                PayNumpad(showDot = true) { key -> onAmount(appendAmountKey(amount, key)) }
            }
            Spacer(Modifier.height(8.dp))
            CoralPill(
                title = if (value > 0) "${kind.cta} ${money(value)}" else kind.cta,
                modifier = Modifier.padding(horizontal = 16.dp),
                enabled = value > 0,
                haptic = if (kind.isRequest) HapticKind.Click else HapticKind.Heavy,
                onClick = onContinue
            )
        }
    }
}

/* ───────────── step 2: PIN ───────────── */

@Composable
private fun PinStep(
    recipient: String,
    amount: Double,
    source: String,
    pin: String,
    onKey: (String) -> Unit,
    onBack: () -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        PayTopBar("Confirm payment", onBack)
        Column(
            Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                Modifier.size(68.dp).clip(CircleShape).background(Pal.Butter),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Outlined.Lock, contentDescription = null, modifier = Modifier.size(30.dp)) }
            Spacer(Modifier.height(16.dp))
            Text("Enter PIN", fontSize = 24.sp)
            Spacer(Modifier.height(6.dp))
            Text(
                "Paying ${money(amount)} to $recipient\nfrom $source",
                fontSize = 14.sp,
                color = Pal.Muted,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )
            Spacer(Modifier.height(28.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                repeat(4) { i ->
                    val filled = i < pin.length
                    val dot by animateDpAsState(
                        targetValue = if (filled) 18.dp else 12.dp,
                        animationSpec = spring(Spring.DampingRatioMediumBouncy),
                        label = "pinDot"
                    )
                    Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                        Box(Modifier.size(dot).clip(CircleShape).background(if (filled) Pal.Ink else Pal.Hairline))
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Outlined.Security, contentDescription = null, tint = Pal.Muted, modifier = Modifier.size(14.dp))
                Text("Secured by PayPal", fontSize = 12.sp, color = Pal.Muted)
            }
        }
        Column(Modifier.navigationBarsPadding().padding(horizontal = 12.dp).padding(bottom = 8.dp)) {
            PayNumpad(showDot = false, onKey = onKey)
        }
    }
}

/* ───────────── step 3: processing ───────────── */

@Composable
private fun ProcessingStep(kind: PayKind) {
    Column(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator(Modifier.size(64.dp), color = Pal.Coral, strokeWidth = 5.dp)
        Spacer(Modifier.height(24.dp))
        Text(if (kind.isRequest) "Sending request…" else "Processing securely…", fontSize = 20.sp)
        Spacer(Modifier.height(6.dp))
        Text("Please don't close the app", fontSize = 13.sp, color = Pal.Muted)
    }
}

/* ───────────── step 4: success ───────────── */

@Composable
private fun SuccessStep(
    kind: PayKind,
    recipient: String,
    amount: Double,
    source: String,
    refId: String,
    paidAt: String,
    onDone: () -> Unit
) {
    val context = LocalContext.current
    val pop = remember { Animatable(0f) }
    val ripple = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        launch { ripple.animateTo(1f, tween(1000)) }
        pop.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessLow))
    }

    Column(
        Modifier.fillMaxSize().navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.weight(1f))
        Box(Modifier.size(190.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                drawCircle(
                    Pal.Green.copy(alpha = (1f - ripple.value) * 0.8f),
                    radius = (this.size.minDimension / 2f) * (0.55f + ripple.value * 0.45f)
                )
            }
            Box(
                Modifier
                    .size(108.dp)
                    .graphicsLayer {
                        scaleX = pop.value
                        scaleY = pop.value
                    }
                    .clip(CircleShape)
                    .background(Pal.Green),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(56.dp)) }
            Sparkle(20.dp, Pal.Coral, Modifier.align(Alignment.TopEnd), spin = true)
            Sparkle(14.dp, Pal.Sun, Modifier.align(Alignment.BottomStart), spin = true)
        }
        Spacer(Modifier.height(18.dp))
        Text(if (kind.isRequest) "Request sent" else "Payment successful", fontSize = 26.sp)
        Spacer(Modifier.height(6.dp))
        Text(money(amount), fontSize = 46.sp)
        Text(
            if (kind.isRequest) "requested from $recipient" else "paid to $recipient",
            fontSize = 15.sp,
            color = Pal.Muted
        )
        Spacer(Modifier.height(22.dp))
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Pal.Surface).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            DetailRow(if (kind.isRequest) "Credit to" else "Debited from", source)
            DetailRow("Reference ID", refId)
            DetailRow("Time", paidAt)
            DetailRow("Status", if (kind.isRequest) "Awaiting payment" else "Completed")
        }
        Spacer(Modifier.weight(1f))
        OutlinePill(
            "Share receipt",
            onClick = {
                shareText(
                    context,
                    if (kind.isRequest) "Please pay me ${money(amount)} on PayRajpal. Ref $refId"
                    else "Paid ${money(amount)} to $recipient on PayRajpal. Ref $refId"
                )
            },
            icon = Icons.Outlined.Share
        )
        Spacer(Modifier.height(12.dp))
        CoralPill("Done", onClick = onDone)
    }
}

/* ═════════════════════════════ SCAN & MY QR ═════════════════════════════ */

@Composable
fun ScanScreen(nav: Navigator) {
    val context = LocalContext.current
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var torch by remember { mutableStateOf(false) }
    val upiId = "shreyan@payrajpal"

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            toast(context, "QR read from image (demo)")
            nav.replace(Route.Pay(PayKind.PAY, "Starbucks CyberHub"))
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            PayTopBar("Scan & Pay", onBack = { nav.pop() }) {
                if (tab == 0) {
                    CircleIconButton(
                        if (torch) Icons.Outlined.FlashOn else Icons.Outlined.FlashOff,
                        "Torch",
                        onClick = { torch = !torch }
                    )
                }
            }
            PillTabs(listOf("Scan QR", "My QR"), tab, { tab = it })
            Spacer(Modifier.height(22.dp))
            AnimatedContent(
                targetState = tab,
                transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(150)) },
                label = "scanTab"
            ) { t ->
                if (t == 0) {
                    ScanTab(
                        onGallery = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                        onNumber = { nav.replace(Route.Pay(PayKind.PAY)) }
                    )
                } else {
                    MyQrTab(
                        upiId = upiId,
                        onCopy = { copyText(context, "UPI ID", upiId) },
                        onShare = { shareText(context, "Pay me on PayRajpal: $upiId") }
                    )
                }
            }
        }
        PinnedCta {
            if (tab == 0) {
                CoralPill(
                    title = "Simulate scan",
                    subtitle = "Demo until the camera scanner is wired up",
                    onClick = { nav.replace(Route.Pay(PayKind.PAY, "Starbucks CyberHub")) }
                )
            } else {
                CoralPill(
                    title = "Request money",
                    subtitle = "Share your QR to get paid",
                    onClick = { nav.replace(Route.Pay(PayKind.REQUEST)) }
                )
            }
        }
    }
}

@Composable
private fun ScanTab(onGallery: () -> Unit, onNumber: () -> Unit) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Viewfinder(Modifier.size(280.dp))
        Spacer(Modifier.height(16.dp))
        Text("Align the QR code inside the frame", fontSize = 14.sp, color = Pal.Muted)
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinePill("Gallery", onClick = onGallery, icon = Icons.Outlined.PhotoLibrary)
            OutlinePill("Pay by number", onClick = onNumber, icon = Icons.Outlined.Dialpad)
        }
    }
}

@Composable
private fun MyQrTab(upiId: String, onCopy: () -> Unit, onShare: () -> Unit) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.clip(RoundedCornerShape(36.dp)).background(Color.White).padding(22.dp)) {
            FakeQr(Modifier.size(210.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text("Shreyan", fontSize = 22.sp, fontWeight = FontWeight.Medium)
        Text(upiId, fontSize = 14.sp, color = Pal.Muted)
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinePill("Copy ID", onClick = onCopy, icon = Icons.Outlined.ContentCopy)
            OutlinePill("Share", onClick = onShare, icon = Icons.Outlined.Share)
        }
    }
}

@Composable
private fun Viewfinder(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "viewfinder")
    val scan by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "scanLine"
    )
    Box(modifier.clip(RoundedCornerShape(36.dp)).background(Color(0xFF1C1C19))) {
        Canvas(Modifier.fillMaxSize()) {
            val len = 46.dp.toPx()
            val stroke = 5.dp.toPx()
            val pad = 28.dp.toPx()
            val l = pad
            val t = pad
            val r = this.size.width - pad
            val b = this.size.height - pad

            fun seg(from: Offset, to: Offset) {
                drawLine(Color.White, from, to, strokeWidth = stroke, cap = StrokeCap.Round)
            }
            seg(Offset(l, t), Offset(l + len, t)); seg(Offset(l, t), Offset(l, t + len))
            seg(Offset(r, t), Offset(r - len, t)); seg(Offset(r, t), Offset(r, t + len))
            seg(Offset(l, b), Offset(l + len, b)); seg(Offset(l, b), Offset(l, b - len))
            seg(Offset(r, b), Offset(r - len, b)); seg(Offset(r, b), Offset(r, b - len))

            val y = t + (b - t) * scan
            drawLine(
                brush = Brush.horizontalGradient(listOf(Color.Transparent, Pal.Line, Color.Transparent)),
                start = Offset(l, y),
                end = Offset(r, y),
                strokeWidth = 3.dp.toPx(),
                cap = StrokeCap.Round
            )
        }
    }
}

/** Decorative placeholder until real QR generation (ZXing) is wired in. */
@Composable
fun FakeQr(modifier: Modifier = Modifier, seed: Int = 7) {
    val n = 25
    val cells = remember {
        val r = Random(seed)
        List(n) { List(n) { r.nextFloat() > 0.5f } }
    }
    Canvas(modifier) {
        val cell = this.size.width / n
        fun inFinder(x: Int, y: Int) = (x < 8 && y < 8) || (x >= n - 8 && y < 8) || (x < 8 && y >= n - 8)
        for (y in 0 until n) {
            for (x in 0 until n) {
                if (!inFinder(x, y) && cells[y][x]) {
                    drawRoundRect(
                        Color.Black,
                        topLeft = Offset(x * cell, y * cell),
                        size = Size(cell * 0.9f, cell * 0.9f),
                        cornerRadius = CornerRadius(cell * 0.25f)
                    )
                }
            }
        }
        listOf(Pair(0, 0), Pair(n - 7, 0), Pair(0, n - 7)).forEach { (fx, fy) ->
            drawRoundRect(Color.Black, Offset(fx * cell, fy * cell), Size(cell * 7, cell * 7), CornerRadius(cell * 1.5f))
            drawRoundRect(Color.White, Offset((fx + 1) * cell, (fy + 1) * cell), Size(cell * 5, cell * 5), CornerRadius(cell))
            drawRoundRect(Color.Black, Offset((fx + 2) * cell, (fy + 2) * cell), Size(cell * 3, cell * 3), CornerRadius(cell * 0.7f))
        }
    }
}