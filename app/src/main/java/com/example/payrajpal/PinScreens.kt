package com.example.payrajpal

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** A row of 4 dots that fill as the PIN is typed. Shakes sideways when [shake] animates. */
@Composable
fun PinDots(length: Int, shakeOffset: Float = 0f) {
    Row(
        Modifier.offset(x = shakeOffset.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        repeat(4) { i ->
            val filled = i < length
            val dot by animateDpAsState(
                targetValue = if (filled) 18.dp else 12.dp,
                animationSpec = spring(androidx.compose.animation.core.Spring.DampingRatioMediumBouncy),
                label = "pinDot"
            )
            Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.size(dot).clip(CircleShape).background(if (filled) Pal.Ink else Pal.Hairline))
            }
        }
    }
}

private fun isWeak(pin: String): Boolean =
    pin.all { it == pin[0] } || pin == "1234" || pin == "4321" || pin == "0123" || pin == "9876"

/**
 * Create a PIN (first time) or change it.
 *  change = true : current PIN -> new PIN -> confirm
 *  change = false: new PIN -> confirm (shown right after the first login)
 * [onBack] is null for the first-time screen, because it cannot be skipped.
 */
@Composable
fun PinSetupScreen(change: Boolean, onDone: () -> Unit, onBack: (() -> Unit)?) {
    val context = LocalContext.current
    val haptics = LocalHaptics.current
    val scope = rememberCoroutineScope()
    val shake = remember { Animatable(0f) }

    // 0 = current PIN, 1 = new PIN, 2 = confirm new PIN
    var stage by remember { mutableIntStateOf(if (change && PinStore.hasPin) 0 else 1) }
    var entry by remember { mutableStateOf("") }
    var first by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }

    BackHandler(enabled = onBack != null) {
        if (stage == 2) {
            stage = 1; entry = ""; first = ""; message = ""
        } else onBack?.invoke()
    }

    fun wrong(text: String) {
        message = text
        haptics.heavy()
        scope.launch {
            repeat(3) {
                shake.animateTo(14f, tween(45))
                shake.animateTo(-14f, tween(45))
            }
            shake.animateTo(0f, tween(60))
        }
    }

    LaunchedEffect(entry) {
        if (entry.length != 4) return@LaunchedEffect
        delay(160)
        val typed = entry
        when (stage) {
            0 -> if (PinStore.verify(typed)) {
                stage = 1; message = ""
            } else wrong(PinStore.failureMessage())
            1 -> if (isWeak(typed)) {
                wrong("Too easy to guess. Pick another PIN.")
            } else {
                first = typed; stage = 2; message = ""
            }
            else -> if (typed == first) {
                PinStore.set(typed)
                haptics.heavy()
                toast(context, if (change) "PIN changed" else "PIN created")
                onDone()
            } else {
                first = ""; stage = 1
                wrong("PINs didn't match. Start again.")
            }
        }
        entry = ""
    }

    Column(Modifier.fillMaxSize().background(Pal.Bg)) {
        if (onBack != null) {
            PayTopBar(if (change) "Change PIN" else "Create PIN", onBack = {
                if (stage == 2) {
                    stage = 1; entry = ""; first = ""; message = ""
                } else onBack()
            })
        } else {
            Spacer(Modifier.height(56.dp))
        }
        Column(
            Modifier.weight(1f).fillMaxWidth().padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                Modifier.size(68.dp).clip(CircleShape).background(Pal.Butter),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Outlined.Lock, contentDescription = null, modifier = Modifier.size(30.dp)) }
            Spacer(Modifier.height(16.dp))
            Text(
                when (stage) {
                    0 -> "Enter current PIN"
                    1 -> if (change) "Enter new PIN" else "Create your PIN"
                    else -> "Confirm your PIN"
                },
                fontSize = 24.sp
            )
            Spacer(Modifier.height(6.dp))
            Text(
                when (stage) {
                    0 -> "We need your current PIN before changing it."
                    1 -> "4 digits. You'll use it to open the app and confirm payments."
                    else -> "Type the same PIN once more."
                },
                fontSize = 14.sp,
                color = Pal.Muted,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )
            Spacer(Modifier.height(28.dp))
            PinDots(entry.length, shake.value)
            Spacer(Modifier.height(16.dp))
            Text(
                message,
                fontSize = 13.sp,
                color = Pal.Coral,
                textAlign = TextAlign.Center,
                modifier = Modifier.height(20.dp).fillMaxWidth()
            )
        }
        Column(Modifier.navigationBarsPadding().padding(horizontal = 12.dp).padding(bottom = 8.dp)) {
            PayNumpad(showDot = false) { key ->
                entry = if (key == "⌫") entry.dropLast(1) else if (entry.length < 4) entry + key else entry
            }
        }
    }
}
