package com.example.payrajpal

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.delay

/* ═════════════════════════════ AI BILL SPLIT ═════════════════════════════
 * 1 Receipt → 2 Assign → 3 Request
 * Uses only: vm.session, selectedMemberId, receiptImageUri, setReceiptImage, selectMember,
 * toggleItemAssignment, updateItem, addMember, calculateMemberShare.
 */

@Composable
fun SplitScreen(vm: SplitViewModel, nav: Navigator) {
    val session by vm.session.collectAsState()
    val selectedMemberId by vm.selectedMemberId.collectAsState()
    val receiptUri by vm.receiptImageUri.collectAsState()

    var step by rememberSaveable { mutableIntStateOf(1) }
    var scanning by remember { mutableStateOf(false) }
    var showAdd by remember { mutableStateOf(false) }

    LaunchedEffect(scanning) {
        if (scanning) {
            delay(2200)
            scanning = false
            step = 1
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            vm.setReceiptImage(uri)
            scanning = true
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            PayTopBar("AI bill split", onBack = { nav.pop() }) {
                CircleIconButton(Icons.Outlined.PersonAdd, "Add friend", onClick = { showAdd = true })
            }
            Row(Modifier.padding(horizontal = 20.dp)) {
                PillTabs(listOf("1 Receipt", "2 Assign", "3 Request"), step, { step = it })
            }
            Spacer(Modifier.height(14.dp))

            AnimatedContent(
                targetState = step,
                transitionSpec = { fadeIn(tween(260)) togetherWith fadeOut(tween(160)) },
                label = "splitStep"
            ) { s ->
                when (s) {
                    0 -> ReceiptStep(
                        session = session,
                        receiptUri = receiptUri,
                        scanning = scanning,
                        onUpload = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                        onSample = { scanning = true }
                    )
                    1 -> AssignStep(
                        session = session,
                        selectedMemberId = selectedMemberId,
                        onSelectMember = { id -> vm.selectMember(id) },
                        onToggleItem = { id -> vm.toggleItemAssignment(id) },
                        onEditItem = { id, name, price -> vm.updateItem(id, name, price) }
                    )
                    else -> SummaryStep(session = session, vm = vm)
                }
            }
        }

        if (step == 0 && !scanning) {
            PinnedCta {
                CoralPill(
                    title = "Upload receipt",
                    subtitle = "AI reads dishes, tax & tip",
                    onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                )
            }
        }
        if (step == 1) {
            PinnedCta { CoralPill("Continue to request", onClick = { step = 2 }) }
        }
    }

    if (showAdd) {
        AddMemberDialog(
            onDismiss = { showAdd = false },
            onAdd = { name, contact ->
                if (name.isNotBlank()) vm.addMember(name.trim(), contact.trim())
                showAdd = false
            }
        )
    }
}

/* ───────────── step 0: receipt ───────────── */

@Composable
private fun ReceiptStep(
    session: BillSplitSession,
    receiptUri: android.net.Uri?,
    scanning: Boolean,
    onUpload: () -> Unit,
    onSample: () -> Unit
) {
    val overlayAlpha by animateFloatAsState(if (scanning) 1f else 0f, tween(300), label = "scanAlpha")
    val transition = rememberInfiniteTransition(label = "receiptScan")
    val line by transition.animateFloat(
        initialValue = 0.08f,
        targetValue = 0.92f,
        animationSpec = infiniteRepeatable(tween(1100, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "receiptLine"
    )
    val subtotal = session.items.sumOf { it.price.asDouble() }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 130.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(300.dp)
                    .clip(RoundedCornerShape(36.dp))
                    .background(Pal.Lavender),
                contentAlignment = Alignment.Center
            ) {
                if (receiptUri != null) {
                    AsyncImage(
                        model = receiptUri,
                        contentDescription = "Receipt",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Outlined.ReceiptLong, contentDescription = null, modifier = Modifier.size(54.dp))
                        Spacer(Modifier.height(10.dp))
                        Text("Snap or upload your bill", fontSize = 18.sp)
                        Text("We read every dish, tax and tip", fontSize = 13.sp, color = Pal.Muted)
                    }
                }
                if (overlayAlpha > 0.01f) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .graphicsLayer { alpha = overlayAlpha }
                            .background(Pal.Ink.copy(alpha = 0.55f))
                    ) {
                        Box(
                            Modifier
                                .align(Alignment.TopStart)
                                .padding(top = 300.dp * line)
                                .fillMaxWidth()
                                .height(3.dp)
                                .background(Brush.horizontalGradient(listOf(Color.Transparent, Pal.Line, Color.Transparent)))
                        )
                        Column(
                            Modifier.align(Alignment.Center),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Sparkle(30.dp, Pal.Sun, spin = true)
                            Spacer(Modifier.height(10.dp))
                            Text("Reading your bill…", fontSize = 18.sp, color = Color.White)
                        }
                    }
                }
            }
        }
        item {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Pal.Surface).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SmallCaps(session.merchantName.toString())
                DetailRow("Items", session.items.size.toString())
                DetailRow("Subtotal", money(subtotal))
                DetailRow("Tax", money(session.tax.asDouble()))
                DetailRow("Tip", money(session.tip.asDouble()))
            }
        }
        item {
            OutlinePill("Use sample bill", onClick = onSample, icon = Icons.Outlined.AutoAwesome)
        }
    }
}

/* ───────────── step 1: assign ───────────── */

@Composable
private fun AssignStep(
    session: BillSplitSession,
    selectedMemberId: String?,
    onSelectMember: (String) -> Unit,
    onToggleItem: (String) -> Unit,
    onEditItem: (String, String, Double) -> Unit
) {
    var itemToEdit by remember { mutableStateOf<ReceiptItem?>(null) }
    val total = session.items.size
    val assigned = session.items.count { it.assignedTo.isNotEmpty() }
    val progress by animateFloatAsState(
        if (total == 0) 0f else assigned.toFloat() / total,
        tween(400),
        label = "assignProgress"
    )

    Column(Modifier.fillMaxSize()) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(session.members, key = { it.id }) { m ->
                OutlinePill(
                    text = m.name,
                    onClick = { onSelectMember(m.id) },
                    selected = m.id == selectedMemberId,
                    compact = true
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        Column(Modifier.padding(horizontal = 20.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                SmallCaps("Tap items for the selected person")
                Text("$assigned/$total", fontSize = 12.sp, color = Pal.Muted)
            }
            Spacer(Modifier.height(8.dp))
            Box(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(Pal.Surface)) {
                Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).height(6.dp).clip(CircleShape).background(Pal.Coral))
            }
        }
        Spacer(Modifier.height(14.dp))

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 130.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(session.items, key = { it.id }) { item ->
                val mine = selectedMemberId != null && selectedMemberId in item.assignedTo
                val shared = item.assignedTo.isNotEmpty()
                val names = session.members.filter { it.id in item.assignedTo }.joinToString(", ") { it.name }
                val bg by animateColorAsState(
                    when {
                        mine -> Pal.Green
                        shared -> Pal.Butter
                        else -> Pal.Surface
                    },
                    label = "itemBg"
                )
                Row(
                    Modifier
                        .fillMaxWidth()
                        .bounceClick { onToggleItem(item.id) }
                        .clip(RoundedCornerShape(26.dp))
                        .background(bg)
                        .padding(start = 20.dp, top = 14.dp, bottom = 14.dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(item.name.toString(), fontSize = 16.sp, fontWeight = FontWeight.Medium)
                        Text(
                            if (names.isEmpty()) "Tap to assign" else "Shared by $names",
                            fontSize = 13.sp,
                            color = if (names.isEmpty()) Pal.CoralDeep else Pal.Ink.copy(alpha = 0.6f)
                        )
                    }
                    Text(money(item.price.asDouble()), fontSize = 17.sp, fontWeight = FontWeight.Medium)
                    CircleIconButton(
                        Icons.Outlined.Edit,
                        "Edit item",
                        onClick = { itemToEdit = item },
                        dimension = 38.dp,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
        }
    }

    itemToEdit?.let { item ->
        EditItemDialog(
            item = item,
            onDismiss = { itemToEdit = null },
            onSave = { name, price ->
                onEditItem(item.id, name, price)
                itemToEdit = null
            }
        )
    }
}

/* ───────────── step 2: request ───────────── */

@Composable
private fun SummaryStep(session: BillSplitSession, vm: SplitViewModel) {
    val context = LocalContext.current
    var phase by rememberSaveable { mutableIntStateOf(0) } // 0 idle · 1 sending · 2 sent
    val members = session.members

    LaunchedEffect(phase) {
        if (phase == 1) {
            delay(1600)
            phase = 2
        }
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 150.dp)) {
            itemsIndexed(members, key = { _, m -> m.id }) { index, m ->
                val share = vm.calculateMemberShare(m.id).asDouble()
                StackCard(
                    color = Pal.stackAt(index),
                    overlap = if (index == members.lastIndex) 0.dp else 32.dp,
                    bottomRounded = index == members.lastIndex
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AvatarCircle(m.name, 48.dp)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(m.name, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                            Text(m.contact.toString(), fontSize = 13.sp, color = Pal.Ink.copy(alpha = 0.6f))
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(money(share), fontSize = 22.sp)
                            StatusChip(if (phase == 2) "Requested" else "Pending")
                        }
                    }
                }
            }
        }
        PinnedCta {
            CoralPill(
                title = when (phase) {
                    0 -> "Send requests"
                    1 -> "Sending…"
                    else -> "Requests sent"
                },
                subtitle = if (phase == 0) "Each person gets their own amount" else null,
                loading = phase == 1,
                haptic = HapticKind.Heavy,
                enabled = phase != 2 && members.isNotEmpty(),
                onClick = {
                    toast(context, "Payment links sent (demo)")
                    phase = 1
                }
            )
        }
    }
}

/* ───────────── dialogs ───────────── */

@Composable
fun AddMemberDialog(onDismiss: () -> Unit, onAdd: (String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var contact by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Pal.Surface,
        shape = RoundedCornerShape(32.dp),
        title = { Text("Add friend", fontSize = 22.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                PayTextField(name, { name = it }, "Name")
                PayTextField(contact, { contact = it }, "Phone or email")
            }
        },
        confirmButton = { TextButton(onClick = { onAdd(name, contact) }) { Text("Add", color = Pal.CoralDeep) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Pal.Ink) } }
    )
}

@Composable
fun EditItemDialog(item: ReceiptItem, onDismiss: () -> Unit, onSave: (String, Double) -> Unit) {
    var name by remember { mutableStateOf(item.name.toString()) }
    var price by remember { mutableStateOf(item.price.toString()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Pal.Surface,
        shape = RoundedCornerShape(32.dp),
        title = { Text("Edit item", fontSize = 22.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                PayTextField(name, { name = it }, "Dish name")
                PayTextField(price, { price = it }, "Price", keyboardType = KeyboardType.Decimal)
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name, price.toDoubleOrNull() ?: item.price.asDouble()) }) {
                Text("Save", color = Pal.CoralDeep)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Pal.Ink) } }
    )
}