@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.payrajpal

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallMade
import androidx.compose.material.icons.filled.CallReceived
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.CallReceived
import androidx.compose.material.icons.outlined.Contactless
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.Dialpad
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/* ───────────────────────────── HOME ───────────────────────────── */

private data class HomeAction(val icon: ImageVector, val label: String, val onClick: () -> Unit)

@Composable
fun HomeScreen(vm: SplitViewModel, ui: PayUiViewModel, nav: Navigator) {
    val banks by vm.banks.collectAsState()
    val txs by vm.transactions.collectAsState()
    val session by vm.session.collectAsState()

    val primary = banks.find { it.isPrimary } ?: banks.firstOrNull()
    val balance = primary?.balance.asDouble()
    val spent = txs.filter { !it.isCredit }.sumOf { it.amount.asDouble() }
    val lastPay = txs.firstOrNull { !it.isCredit }
    val activeAutopays = ui.autopays.count { it.active }

    var showBalance by remember { mutableStateOf(false) }
    var period by rememberSaveable { mutableIntStateOf(0) }
    var showMore by remember { mutableStateOf(false) }
    var scanExpanded by remember { mutableStateOf(false) }
    var scanBounds by remember { mutableStateOf(Rect.Zero) }

    // Real UPI apps hide the balance again after a few seconds.
    LaunchedEffect(showBalance) {
        if (showBalance) {
            delay(8000)
            showBalance = false
        }
    }

    val actions = listOf(
        HomeAction(Icons.Outlined.Dialpad, "Pay phone") { nav.push(Route.Pay(PayKind.PAY)) },
        HomeAction(Icons.Outlined.AccountBalance, "To bank") { nav.push(Route.Pay(PayKind.BANK)) },
        HomeAction(Icons.Outlined.CallReceived, "Request") { nav.push(Route.Pay(PayKind.REQUEST)) },
        HomeAction(Icons.Outlined.History, "History") { nav.push(Route.History) },
        HomeAction(Icons.Outlined.Autorenew, "Autopay") { nav.push(Route.Autopays) },
        HomeAction(Icons.Outlined.CreditCard, "Cards") { nav.push(Route.Wallet) },
        HomeAction(Icons.Outlined.Contactless, "Tap to pay") { nav.push(Route.TapToPay) },
        HomeAction(Icons.Outlined.MoreHoriz, "More") { showMore = true }
    )

    Box(
        Modifier
            .fillMaxSize()
            .dismissOnOutsideTap(active = scanExpanded, bounds = { scanBounds }, onDismiss = { scanExpanded = false })
    ) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 0.dp)) {
            item { Reveal(0) { HomeHeader(onSettings = { nav.push(Route.Security) }) } }
            item {
                Column(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    Reveal(1) {
                        BalanceCard(
                            bankName = primary?.bankName ?: "No bank linked",
                            balance = balance,
                            showBalance = showBalance,
                            onToggleBalance = { showBalance = !showBalance },
                            spent = spent,
                            autopays = activeAutopays,
                            period = period,
                            onPeriod = { period = if (period == 0) 1 else 0 },
                            onAccounts = { nav.push(Route.Wallet) },
                            onAutopays = { nav.push(Route.Autopays) }
                        )
                    }
                    Reveal(2) { QuickActionsCard(actions) }
                    Reveal(3) {
                        SplitPromoCard(
                            members = session.members.map { it.name },
                            title = session.merchantName,
                            onClick = { nav.push(Route.Split) }
                        )
                    }
                    Reveal(4) { RecentCard(txs.take(4), onSeeAll = { nav.push(Route.History) }) }
                }
            }
        }

        PinnedCta {
            ScanPayButton(
                title = "Scan & Pay",
                subtitle = if (lastPay != null) "Last payment: ${lastPay.title} · ${lastPay.timestamp}" else "Pay anyone, anywhere",
                expanded = scanExpanded,
                onExpandedChange = { scanExpanded = it },
                onBounds = { scanBounds = it },
                onClick = { nav.push(Route.Scan) }
            )
        }
    }

    if (showMore) {
        MoreSheet(
            onDismiss = { showMore = false },
            onBills = { showMore = false; nav.push(Route.Pay(PayKind.BILLS)) },
            onRecharge = { showMore = false; nav.push(Route.Pay(PayKind.RECHARGE)) },
            onSplit = { showMore = false; nav.push(Route.Split) },
            onSecurity = { showMore = false; nav.push(Route.Security) }
        )
    }
}

@Composable
private fun HomeHeader(onSettings: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AvatarCircle("Shreyan", 52.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("Welcome back,", fontSize = 13.sp)
            Text("Shreyan", fontSize = 22.sp, fontWeight = FontWeight.Medium)
        }
        CircleIconButton(Icons.Outlined.Tune, "Settings", onSettings)
    }
}

@Composable
private fun BalanceCard(
    bankName: String,
    balance: Double,
    showBalance: Boolean,
    onToggleBalance: () -> Unit,
    spent: Double,
    autopays: Int,
    period: Int,
    onPeriod: () -> Unit,
    onAccounts: () -> Unit,
    onAutopays: () -> Unit
) {
    val chart = remember(period) { chartData(period) }

    StackCard(color = Pal.Surface) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                SmallCaps("Balance")
                Text(bankName, fontSize = 13.sp, color = Pal.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            OutlinePill(if (showBalance) "Hide" else "Check balance", onClick = onToggleBalance, compact = true)
            Spacer(Modifier.width(8.dp))
            CircleIconButton(Icons.Outlined.AccountBalance, "Accounts", onAccounts, dimension = 38.dp)
        }

        Spacer(Modifier.height(18.dp))
        BalanceNumber(balance, showBalance)
        Text("Available balance", fontSize = 13.sp, color = Pal.Ink.copy(alpha = 0.6f))

        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(36.dp)) {
            Stat(money(spent), "Spent this month")
            Stat("$autopays", "Active autopays", Modifier.bounceClick(onClick = onAutopays))
        }

        Spacer(Modifier.height(24.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            SmallCaps("Spending", Modifier.weight(1f))
            OutlinePill(if (period == 0) "Last 7 days" else "6 months", onClick = onPeriod, compact = true)
        }
        Spacer(Modifier.height(10.dp))
        SpendChart(points = chart.first, labels = chart.second)
    }
}

@Composable
private fun BalanceNumber(balance: Double, show: Boolean) {
    val fraction by animateFloatAsState(
        targetValue = if (show) 1f else 0f,
        animationSpec = tween(900, easing = FastOutSlowInEasing),
        label = "balanceCount"
    )
    AnimatedContent(
        targetState = show,
        transitionSpec = {
            (fadeIn(tween(260)) + slideInVertically(tween(260)) { it / 3 }) togetherWith
                    (fadeOut(tween(140)) + slideOutVertically(tween(140)) { -it / 3 })
        },
        label = "balanceSwap"
    ) { visible ->
        if (visible) {
            val text = inr(balance * fraction)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(text.substringBefore("."), fontSize = 46.sp, maxLines = 1)
                Text(
                    "." + text.substringAfter("."),
                    fontSize = 24.sp,
                    color = Pal.Muted,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
            }
        } else {
            Text("₹ ••••••", fontSize = 46.sp, maxLines = 1)
        }
    }
}

@Composable
private fun Stat(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(value, fontSize = 20.sp, fontWeight = FontWeight.Medium)
        Text(label, fontSize = 13.sp, color = Pal.Ink.copy(alpha = 0.6f))
    }
}

@Composable
private fun QuickActionsCard(actions: List<HomeAction>) {
    StackCard(color = Pal.Lavender) {
        SmallCaps("Quick actions")
        Spacer(Modifier.height(14.dp))
        actions.chunked(4).forEach { rowItems ->
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                rowItems.forEach { a -> ActionTile(a, Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun ActionTile(action: HomeAction, modifier: Modifier = Modifier) {
    Column(modifier.bounceClick(onClick = action.onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(58.dp)
                .clip(CircleShape)
                .background(Pal.wash(0.7f))
                .border(1.dp, Pal.Ink.copy(alpha = 0.45f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(action.icon, contentDescription = action.label, modifier = Modifier.size(26.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text(action.label, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1, textAlign = TextAlign.Center)
    }
}

@Composable
private fun SplitPromoCard(members: List<String>, title: String, onClick: () -> Unit) {
    StackCard(color = Pal.Butter, onClick = onClick) {
        SmallCaps("AI bill split")
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${members.size}", fontSize = 44.sp)
                    Spacer(Modifier.width(8.dp))
                    Sparkle(26.dp, Pal.Coral, spin = true)
                }
                Text(title, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Column(horizontalAlignment = Alignment.End) {
                OverlapAvatars(members.take(5), 40.dp)
                Spacer(Modifier.height(6.dp))
                Text("In this split", fontSize = 13.sp)
            }
        }
        Spacer(Modifier.height(18.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(48.dp).clip(CircleShape).background(Pal.wash(0.5f)),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Outlined.AutoAwesome, contentDescription = null, modifier = Modifier.size(22.dp)) }
            Spacer(Modifier.width(14.dp))
            Text(
                "Snap the bill, tell Claude who ate what, and everyone gets their share to pay you.",
                fontSize = 15.sp,
                lineHeight = 20.sp
            )
        }
    }
}

@Composable
private fun RecentCard(txs: List<Transaction>, onSeeAll: () -> Unit) {
    StackCard(color = Pal.Pink, overlap = 0.dp, bottomRounded = true, extraBottom = 130.dp) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            SmallCaps("Recent activity", Modifier.weight(1f))
            OutlinePill("See all", onClick = onSeeAll, compact = true)
        }
        Spacer(Modifier.height(8.dp))
        txs.forEach { tx ->
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                WhiteBadge(
                    if (tx.isCredit) Icons.Filled.CallReceived else Icons.Filled.CallMade,
                    tint = if (tx.isCredit) Pal.Credit else Pal.Ink
                )
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(tx.title, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "${tx.subtitle} · ${tx.timestamp}",
                        fontSize = 12.sp,
                        color = Pal.Ink.copy(alpha = 0.6f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    (if (tx.isCredit) "+" else "-") + money(tx.amount.asDouble()),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun MoreSheet(
    onDismiss: () -> Unit,
    onBills: () -> Unit,
    onRecharge: () -> Unit,
    onSplit: () -> Unit,
    onSecurity: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Pal.Bg,
        shape = RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp)
    ) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 24.dp).padding(bottom = 24.dp)
        ) {
            Text("More", fontSize = 24.sp)
            Spacer(Modifier.height(8.dp))
            ActionRow(Icons.Outlined.Receipt, "Pay bills", "Electricity, water, gas, broadband", onBills)
            ActionRow(Icons.Outlined.PhoneAndroid, "Mobile recharge", "Prepaid and postpaid", onRecharge)
            ActionRow(Icons.Outlined.AutoAwesome, "AI bill split", "Scan a receipt and split it", onSplit)
            ActionRow(Icons.Outlined.Security, "Security & limits", "App lock, daily limit, PIN", onSecurity)
        }
    }
}

/* ───────────────────────────── SPENDING CHART ───────────────────────────── */

private fun chartData(period: Int): Pair<List<Float>, List<String>> {
    val cal = Calendar.getInstance()
    val labels = ArrayList<String>()
    return if (period == 0) {
        val fmt = SimpleDateFormat("EEE", Locale.getDefault())
        cal.add(Calendar.DAY_OF_YEAR, -6)
        repeat(7) {
            labels.add(fmt.format(cal.time))
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        // Demo rupee totals per day — replace with real data from the backend.
        Pair(listOf(1450f, 780f, 3100f, 1800f, 3900f, 1300f, 2350f), labels)
    } else {
        val fmt = SimpleDateFormat("MMM", Locale.getDefault())
        cal.add(Calendar.MONTH, -5)
        repeat(6) {
            labels.add(fmt.format(cal.time))
            cal.add(Calendar.MONTH, 1)
        }
        Pair(listOf(21400f, 26500f, 16200f, 34000f, 24800f, 30100f), labels)
    }
}

/** Rounds the top of the axis up so the three labels (0, half, max) read cleanly. */
private fun niceAxisMax(maxValue: Float): Float {
    val raw = (maxValue * 1.1f).coerceAtLeast(1f)
    var magnitude = 1f
    while (magnitude * 10f <= raw) magnitude *= 10f
    return kotlin.math.ceil(raw / magnitude) * magnitude
}

private fun axisLabel(value: Float): String = when {
    value <= 0f -> "₹0"
    value >= 1000f -> {
        val k = value / 1000f
        if (k % 1f == 0f) "₹${k.toInt()}k" else "₹${"%.1f".format(Locale.US, k)}k"
    }
    else -> "₹${value.toInt()}"
}

@Composable
fun SpendChart(points: List<Float>, labels: List<String>, modifier: Modifier = Modifier) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(points) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(1100, easing = FastOutSlowInEasing))
    }
    val axisMax = remember(points) { niceAxisMax(points.maxOrNull() ?: 1f) }
    val gridColor = Pal.Hairline.copy(alpha = 0.6f)
    val ink = Pal.Ink
    val chartHeight = 112.dp

    Row(modifier.fillMaxWidth()) {
        // y-axis: amounts aligned with the three grid lines
        Column(
            Modifier.height(chartHeight).padding(end = 8.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.End
        ) {
            listOf(axisMax, axisMax / 2f, 0f).forEach { v ->
                Text(axisLabel(v), fontSize = 11.sp, lineHeight = 14.sp, color = Pal.Muted)
            }
        }
        Column(Modifier.weight(1f)) {
            Canvas(Modifier.fillMaxWidth().height(chartHeight)) {
                val w = this.size.width
                val h = this.size.height
                val padY = 7.dp.toPx()
                val usable = h - padY * 2f
                val endX = w * 0.93f
                val stepX = endX / (points.size - 1).coerceAtLeast(1)

                fun px(i: Int): Float = i * stepX
                fun py(v: Float): Float = padY + usable * (1f - (v / axisMax).coerceIn(0f, 1f))

                for (k in 0..2) {
                    val y = padY + usable * k / 2f
                    drawLine(gridColor, Offset(0f, y), Offset(w, y), strokeWidth = 1.dp.toPx())
                }

                val line = Path().apply {
                    moveTo(px(0), py(points[0]))
                    for (i in 1 until points.size) {
                        val x0 = px(i - 1)
                        val y0 = py(points[i - 1])
                        val x1 = px(i)
                        val y1 = py(points[i])
                        cubicTo((x0 + x1) / 2f, y0, (x0 + x1) / 2f, y1, x1, y1)
                    }
                }
                val fill = Path().apply {
                    addPath(line)
                    lineTo(px(points.size - 1), h)
                    lineTo(0f, h)
                    close()
                }

                clipRect(left = 0f, top = 0f, right = w * progress.value, bottom = h) {
                    drawPath(fill, Brush.verticalGradient(listOf(Pal.Line.copy(alpha = 0.28f), Color.Transparent)))
                    drawPath(line, Pal.Line, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                }

                val marker = ((progress.value - 0.85f) / 0.15f).coerceIn(0f, 1f)
                if (marker > 0f) {
                    drawLine(
                        ink.copy(alpha = 0.55f * marker),
                        Offset(endX, 0f),
                        Offset(endX, h),
                        strokeWidth = 1.5.dp.toPx()
                    )
                    drawCircle(Pal.Line.copy(alpha = marker), 6.dp.toPx(), Offset(endX, py(points.last())))
                }
            }
            Row(Modifier.fillMaxWidth(0.93f).padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                labels.forEach { Text(it, fontSize = 12.sp, color = Pal.Muted) }
            }
        }
    }
}