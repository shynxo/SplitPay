package com.example.payrajpal

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
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
 * Steps: 0 recipient · 1 amount · 2 PIN · 3 PayPal · 4 success
 * Requests skip the recipient step (the link works for anyone) and the PIN (no money leaves the account).
 */

@Composable
fun PayFlowScreen(route: Route.Pay, vm: SplitViewModel, ui: PayUiViewModel, nav: Navigator) {
    val kind = route.kind
    val skipsContact = route.recipient.isNotBlank() || kind.isRequest

    var step by rememberSaveable { mutableIntStateOf(if (skipsContact) 1 else 0) }
    var recipient by rememberSaveable { mutableStateOf(if (kind.isRequest) "Payment link" else route.recipient) }
    var detail by rememberSaveable { mutableStateOf(if (kind.isRequest) "Anyone with the link can pay you" else route.email) }
    var amount by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    var sourceIndex by rememberSaveable { mutableIntStateOf(0) }
    var pinMessage by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") } // deliberately NOT saved
    var paypalEmail by rememberSaveable { mutableStateOf("") }
    val haptics = LocalHaptics.current
    val context = LocalContext.current

    // "Pay contact" and "Request money" use the real PayPal rail. Bank / recharge / bills have no PayPal
    // equivalent, so they remain demo screens.
    val usesPayPal = kind == PayKind.PAY || kind == PayKind.REQUEST
    val knownEmail = if (detail.contains("@")) detail.trim() else ""
    val targetEmail = knownEmail.ifBlank { paypalEmail.trim() }
    val emailOk = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$").matches(targetEmail)
    val paidAt = remember { SimpleDateFormat("d MMM yyyy, h:mm a", Locale.getDefault()).format(Date()) }

    val sources = listOf("PayPal · ${Session.email}")
    val sourceLabel = sources.getOrElse(sourceIndex) { "PayPal" }
    val recents = txsForRecents(vm)
    val amountValue = amount.toDoubleOrNull() ?: 0.0

    BackHandler(enabled = step in 1..2) {
        pin = ""
        if (step == 1 && skipsContact) nav.pop() else step -= 1
    }
    BackHandler(enabled = step == 3) {
        // While PayPal is open or has failed the user may back out; otherwise wait for the result.
        if (usesPayPal && (PayPalPay.phase == PayPhase.InBrowser || PayPalPay.phase is PayPhase.Failed)) {
            PayPalPay.reset()
            pin = ""
            step = 1
        }
    }

    LaunchedEffect(pin) {
        if (pin.length == 4) {
            delay(200)
            if (PinStore.verify(pin)) {
                pinMessage = ""
                step = 3
            } else {
                haptics.heavy()
                pinMessage = PinStore.failureMessage()
                pin = ""
            }
        }
    }
    LaunchedEffect(step) {
        if (step == 3) {
            if (kind == PayKind.PAY) {
                PayPalPay.reset()
                if (!emailOk) PayPalPay.fail("Enter a valid PayPal email for $recipient.")
                else PayPalPay.start(context, Math.round(amountValue * 100), targetEmail, recipient, note)
            } else if (kind == PayKind.REQUEST) {
                PayPalPay.reset()
                PayPalPay.request(context, Math.round(amountValue * 100), note) { url ->
                    "Please pay me ${money(amountValue)} on PayRajpal: $url"
                }
            }
        }
    }
    // PayPal confirmed the money moved (or the request link was created): refresh history, show success.
    LaunchedEffect(PayPalPay.phase) {
        if (usesPayPal && step == 3 && PayPalPay.phase == PayPhase.Done) {
            refreshHistory(vm)
            step = 4
            haptics.heavy()
            PayPalPay.reset()
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
                0 -> ContactStep(kind, recents, onBack = { nav.pop() }, onPick = { name, d, preset ->
                    recipient = name
                    detail = d
                    if (preset.isNotEmpty()) amount = preset
                    step = 1
                })
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
                    onBack = { if (skipsContact) nav.pop() else step = 0 },
                    needEmail = kind == PayKind.PAY && knownEmail.isBlank(),
                    email = paypalEmail,
                    onEmail = { paypalEmail = it },
                    canContinue = kind != PayKind.PAY || emailOk,
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
                    message = pinMessage,
                    onKey = { key -> pin = if (key == "⌫") pin.dropLast(1) else if (pin.length < 4) pin + key else pin },
                    onBack = {
                        pin = ""
                        step = 1
                    }
                )
                3 -> PayPalStep(
                    phase = PayPalPay.phase,
                    recipient = recipient,
                    isRequest = kind == PayKind.REQUEST,
                    onCheck = { PayPalPay.check() },
                    onCancel = {
                        PayPalPay.reset()
                        pin = ""
                        step = 1
                    }
                )
                else -> SuccessStep(
                    kind = kind,
                    recipient = recipient,
                    amount = amountValue,
                    source = sourceLabel,
                    paidAt = paidAt,
                    link = PayPalPay.lastLink,
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
    recents: List<Contact>,
    onBack: () -> Unit,
    onPick: (String, String, String) -> Unit
) {
    var query by remember { mutableStateOf("") }
    val q = query.trim()
    val looksLikeUpi = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$").matches(q)
    val matches = recents.filter { c ->
        q.isBlank() ||
                c.name.contains(q, ignoreCase = true) ||
                c.phone.contains(q, ignoreCase = true) ||
                false
    }

    Column(Modifier.fillMaxSize()) {
        PayTopBar(kind.title, onBack)
        PayTextField(
            value = query,
            onValueChange = { query = it },
            label = "Name or PayPal email",
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
                if (looksLikeUpi) {
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
                                Text("New recipient · PayPal email", fontSize = 13.sp, color = Pal.Muted)
                            }
                        }
                    }
                }
                item { SmallCaps("Recent", Modifier.padding(bottom = 6.dp)) }
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
                if (matches.isEmpty() && !looksLikeUpi) {
                    item { Text("Nobody yet. Type their PayPal email above to pay them.", fontSize = 14.sp, color = Pal.Muted) }
                }
            }
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
    needEmail: Boolean = false,
    email: String = "",
    onEmail: (String) -> Unit = {},
    canContinue: Boolean = true,
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
            Spacer(Modifier.height(6.dp))
            Text("PayPal charges in US dollars at today's rate", fontSize = 12.sp, color = Pal.Muted)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                StatusChip((if (kind.isRequest) "Paid to: " else "From: ") + sourceLabel)
                OutlinePill(if (showNote) "Hide note" else "Add note", onClick = { showNote = !showNote }, compact = true)
            }
            Expand(showNote) {
                PayTextField(note, onNote, "Note (optional)", Modifier.padding(top = 12.dp))
            }
            if (needEmail) {
                PayTextField(
                    email, onEmail, "Recipient's PayPal email",
                    Modifier.padding(top = 12.dp),
                    keyboardType = KeyboardType.Email
                )
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
                enabled = value > 0 && canContinue,
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
    message: String = "",
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
            Text("Enter your PIN", fontSize = 24.sp)
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
            Text(
                message,
                fontSize = 13.sp,
                color = Pal.Coral,
                textAlign = TextAlign.Center,
                modifier = Modifier.height(20.dp).fillMaxWidth()
            )
        }
        Column(Modifier.navigationBarsPadding().padding(horizontal = 12.dp).padding(bottom = 8.dp)) {
            PayNumpad(showDot = false, onKey = onKey)
        }
    }
}

/* ───────────── step 4: success ───────────── */

@Composable
private fun SuccessStep(
    kind: PayKind,
    recipient: String,
    amount: Double,
    source: String,
    paidAt: String,
    link: String?,
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
            if (kind.isRequest) "requested with a payment link" else "paid to $recipient",
            fontSize = 15.sp,
            color = Pal.Muted
        )
        Spacer(Modifier.height(22.dp))
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Pal.Surface).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            DetailRow(if (kind.isRequest) "Paid into" else "Paid from", source)
            DetailRow("Time", paidAt)
            DetailRow("Status", if (kind.isRequest) "Waiting for payment" else "Completed")
            if (kind.isRequest) Text(
                "When someone pays your link it shows up in your activity automatically.",
                fontSize = 13.sp, color = Pal.Muted
            )
        }
        Spacer(Modifier.weight(1f))
        if (kind.isRequest && link != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinePill(
                    "Share link",
                    onClick = { shareText(context, "Please pay me ${money(amount)} on PayRajpal: $link") },
                    modifier = Modifier.weight(1f),
                    icon = Icons.Outlined.Share
                )
                OutlinePill(
                    "Copy link",
                    onClick = { copyText(context, "Payment link", link) },
                    modifier = Modifier.weight(1f)
                )
            }
        } else if (!kind.isRequest) {
            OutlinePill(
                "Share receipt",
                onClick = { shareText(context, "Paid ${money(amount)} to $recipient on PayRajpal.") },
                icon = Icons.Outlined.Share
            )
        }
        Spacer(Modifier.height(12.dp))
        CoralPill("Done", onClick = onDone)
    }
}

/** People you have paid before (with their PayPal email), newest first. */
private fun txsForRecents(vm: SplitViewModel): List<Contact> =
    vm.transactions.value
        .filter { !it.isCredit && it.email.contains("@") }
        .distinctBy { it.email.lowercase() }
        .take(12)
        .map { Contact(it.title, it.email) }

@Composable
private fun PayPalStep(
    phase: PayPhase,
    recipient: String,
    isRequest: Boolean,
    onCheck: () -> Unit,
    onCancel: () -> Unit
) {
    Column(
        Modifier.fillMaxSize().navigationBarsPadding().padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        when (phase) {
            is PayPhase.Failed -> {
                Text("Not completed", fontSize = 22.sp, textAlign = TextAlign.Center)
                Spacer(Modifier.height(10.dp))
                Text(phase.message, fontSize = 14.sp, color = Pal.Muted, textAlign = TextAlign.Center)
                Spacer(Modifier.height(28.dp))
                CoralPill("Back", onClick = onCancel)
            }
            PayPhase.InBrowser -> {
                CircularProgressIndicator(Modifier.size(56.dp), color = Pal.Coral, strokeWidth = 5.dp)
                Spacer(Modifier.height(24.dp))
                Text("Finish paying in PayPal", fontSize = 20.sp, textAlign = TextAlign.Center)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Approve the payment to $recipient, then you'll return here automatically.",
                    fontSize = 13.sp, color = Pal.Muted, textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(28.dp))
                OutlinePill("I've paid — check status", onClick = onCheck)
                Spacer(Modifier.height(10.dp))
                OutlinePill("Cancel", onClick = onCancel, compact = true)
            }
            else -> {
                CircularProgressIndicator(Modifier.size(64.dp), color = Pal.Coral, strokeWidth = 5.dp)
                Spacer(Modifier.height(24.dp))
                Text(
                    when {
                        phase == PayPhase.Confirming -> "Confirming with PayPal…"
                        isRequest -> "Creating your payment link…"
                        else -> "Contacting PayPal…"
                    },
                    fontSize = 20.sp
                )
                Spacer(Modifier.height(6.dp))
                Text("Please don't close the app", fontSize = 13.sp, color = Pal.Muted)
            }
        }
    }
}

/* ═════════════════════════════ SCAN & MY QR ═════════════════════════════ */

@Composable
fun ScanScreen(nav: Navigator) {
    val context = LocalContext.current
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var torch by remember { mutableStateOf(false) }
    var customPayPayload by remember { mutableStateOf<String?>(null) }
    var isPaying by remember { mutableStateOf(false) }
    val myQr = payQrPayload(Session.name.ifBlank { Session.email }, Session.email)

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    fun handleDecodedQr(qrData: String) {
        val cleanValue = qrData.trim()
        if (cleanValue.isBlank()) return

        if (cleanValue.startsWith("payrajpal://pay", ignoreCase = true)) {
            // A PayRajpal QR: open the pay screen with the person already filled in.
            val u = Uri.parse(cleanValue)
            val email = u.getQueryParameter("e").orEmpty()
            val name = u.getQueryParameter("n").orEmpty().ifBlank { email }
            if (email.contains("@")) nav.replace(Route.Pay(PayKind.PAY, name, email))
            else toast(context, "This QR code is not valid")
        } else if (cleanValue.startsWith("http://", ignoreCase = true) || cleanValue.startsWith("https://", ignoreCase = true)) {
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(cleanValue))
                context.startActivity(intent)
            } catch (e: Exception) {
                Log.e("ScanScreen", "Failed to open web link", e)
                toast(context, "Unable to open payment link")
            }
        } else {
            toast(context, "Not a PayRajpal QR code")
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            QrDecoder.decodeFromUri(context, uri) { qrCode ->
                if (!qrCode.isNullOrBlank()) {
                    handleDecodedQr(qrCode)
                } else {
                    toast(context, "No valid QR code found in selected image")
                }
            }
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
                        hasCameraPermission = hasCameraPermission,
                        torchEnabled = torch,
                        onRequestPermission = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                        onGallery = { picker.launch("image/*") },
                        onNumber = { nav.replace(Route.Pay(PayKind.PAY)) },
                        onQrDetected = { qr -> handleDecodedQr(qr) }
                    )
                } else {
                    MyQrTab(
                        name = Session.name,
                        email = Session.email,
                        payload = myQr,
                        onCopy = { copyText(context, "PayPal email", Session.email) },
                        onShare = { shareText(context, "Pay me on PayRajpal (PayPal): ${Session.email}") }
                    )
                }
            }
        }
        PinnedCta {
            if (tab == 0) {
                CoralPill(
                    title = "Pay by PayPal email",
                    subtitle = "No QR? Type their email instead",
                    onClick = { nav.replace(Route.Pay(PayKind.PAY)) }
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

    customPayPayload?.let { payload ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { if (!isPaying) customPayPayload = null },
            containerColor = Pal.Surface,
            shape = RoundedCornerShape(32.dp),
            title = { Text("Confirm Payment", fontSize = 22.sp, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Decoded Recipient / Payload:", fontSize = 13.sp, color = Pal.Muted)
                    Text(payload, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        isPaying = true
                        BackendService.payQr(payload, context) { success, _ ->
                            isPaying = false
                            customPayPayload = null
                            if (success) {
                                toast(context, "Payment completed successfully!")
                                nav.pop()
                            }
                        }
                    },
                    enabled = !isPaying
                ) {
                    if (isPaying) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Pal.CoralDeep, strokeWidth = 2.dp)
                    } else {
                        Text("Confirm & Pay", color = Pal.CoralDeep, fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { customPayPayload = null }, enabled = !isPaying) {
                    Text("Cancel", color = Pal.Ink)
                }
            }
        )
    }
}

@Composable
private fun ScanTab(
    hasCameraPermission: Boolean,
    torchEnabled: Boolean,
    onRequestPermission: () -> Unit,
    onGallery: () -> Unit,
    onNumber: () -> Unit,
    onQrDetected: (String) -> Unit
) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Viewfinder(
            modifier = Modifier.size(280.dp),
            hasCameraPermission = hasCameraPermission,
            torchEnabled = torchEnabled,
            onRequestPermission = onRequestPermission,
            onQrDetected = onQrDetected
        )
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
private fun MyQrTab(name: String, email: String, payload: String, onCopy: () -> Unit, onShare: () -> Unit) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.clip(RoundedCornerShape(36.dp)).background(Color.White).padding(22.dp)) {
            RealQr(payload, Modifier.size(210.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text(name.ifBlank { "You" }, fontSize = 22.sp, fontWeight = FontWeight.Medium)
        Text(email, fontSize = 14.sp, color = Pal.Muted)
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinePill("Copy ID", onClick = onCopy, icon = Icons.Outlined.ContentCopy)
            OutlinePill("Share", onClick = onShare, icon = Icons.Outlined.Share)
        }
    }
}

@Composable
private fun Viewfinder(
    modifier: Modifier = Modifier,
    hasCameraPermission: Boolean = false,
    torchEnabled: Boolean = false,
    onRequestPermission: () -> Unit = {},
    onQrDetected: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

    val transition = rememberInfiniteTransition(label = "viewfinder")
    val scan by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "scanLine"
    )

    Box(modifier.clip(RoundedCornerShape(36.dp)).background(Color(0xFF1C1C19))) {
        if (hasCameraPermission) {
            val previewView = remember { PreviewView(context) }
            val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
            var camera by remember { mutableStateOf<Camera?>(null) }

            LaunchedEffect(torchEnabled) {
                camera?.cameraControl?.enableTorch(torchEnabled)
            }

            androidx.compose.ui.viewinterop.AndroidView(
                factory = { previewView },
                modifier = Modifier.fillMaxSize(),
                update = {
                    cameraProviderFuture.addListener({
                        try {
                            val cameraProvider = cameraProviderFuture.get()
                            val preview = Preview.Builder().build().also {
                                it.setSurfaceProvider(previewView.surfaceProvider)
                            }

                            val imageAnalyzer = ImageAnalysis.Builder()
                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                .build()
                                .also {
                                    it.setAnalyzer(
                                        ContextCompat.getMainExecutor(context),
                                        QrDecoder.createImageAnalyzer(onQrDetected)
                                    )
                                }

                            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                            cameraProvider.unbindAll()
                            camera = cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                cameraSelector,
                                preview,
                                imageAnalyzer
                            )
                        } catch (e: Exception) {
                            Log.e("Viewfinder", "Error binding camera preview", e)
                        }
                    }, ContextCompat.getMainExecutor(context))
                }
            )
        } else {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("Camera permission required", color = Color.White, fontSize = 14.sp, textAlign = TextAlign.Center)
                Spacer(Modifier.height(10.dp))
                androidx.compose.material3.TextButton(onClick = onRequestPermission) {
                    Text("Grant Permission", color = Pal.Coral)
                }
            }
        }

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