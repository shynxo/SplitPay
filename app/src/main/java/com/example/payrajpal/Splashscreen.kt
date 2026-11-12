package com.example.payrajpal

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

private class Blob(
    val color: Color,
    val alpha: Float,
    val cx: Float,
    val cy: Float,
    val ax: Float,
    val ay: Float,
    val kx: Int,
    val ky: Int,
    val phase: Float,
    val radius: Float
)

private class Twinkle(val x: Float, val y: Float, val size: Float, val k: Int, val phase: Float)

private val Blobs = listOf(
    Blob(Color(0xFFFF9F99), 0.60f, 0.25f, 0.20f, 0.30f, 0.12f, 1, 2, 0.0f, 0.80f),
    Blob(Color(0xFFEEA6BE), 0.38f, 0.80f, 0.45f, 0.22f, 0.20f, 2, 1, 1.7f, 0.65f),
    Blob(Color(0xFFF8E1AB), 0.30f, 0.30f, 0.70f, 0.28f, 0.16f, 1, 1, 3.1f, 0.60f),
    Blob(Color(0xFFC9443F), 0.38f, 0.75f, 0.90f, 0.25f, 0.10f, 2, 3, 4.6f, 0.85f)
)

private val Twinkles = listOf(
    Twinkle(0.82f, 0.12f, 0.030f, 3, 0.0f),
    Twinkle(0.62f, 0.30f, 0.018f, 2, 1.3f),
    Twinkle(0.90f, 0.52f, 0.022f, 4, 2.6f),
    Twinkle(0.12f, 0.46f, 0.016f, 3, 3.9f),
    Twinkle(0.70f, 0.70f, 0.026f, 2, 5.2f)
)

/**
 * Always-moving coral splash with a PIN unlock.
 * Everything loops on one seamless clock (blobs, sparkles, word sway) so it never sits still.
 *
 * The butter star is a handle: grab it and slide it up/down the rail to focus a different title
 * (it starts on "Pay."). You feel a tick each time it crosses a title, and it springs onto the nearest one
 * when released. Tapping a title also moves the star there.
 * "Enter PIN" dims the words and slides up the keypad.
 */
@Composable
fun SplashScreen(onStart: () -> Unit, lockEnabled: Boolean = true) {
    val haptics = LocalHaptics.current
    val scope = rememberCoroutineScope()
    val titles = remember {
        listOf("Instant-pay", "Requests", "Split-bills", "Scan-&-pay", "Security", "Settle-up", "Pay.")
    }

    // one loop, 24 s; every animated value below uses whole multiples of it so the seam is invisible
    val transition = rememberInfiniteTransition(label = "splashClock")
    val clock = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(24000, easing = LinearEasing), RepeatMode.Restart),
        label = "clock"
    )

    // ── star handle state ──
    val centres = remember { mutableStateListOf<Float>().apply { repeat(titles.size) { add(0f) } } }
    var focus by rememberSaveable { mutableIntStateOf(titles.lastIndex) }
    var dragging by remember { mutableStateOf(false) }
    var placed by remember { mutableStateOf(false) }
    val starY = remember { Animatable(0f) }
    val bloom = remember { Animatable(0f) }
    val grab = animateFloatAsState(if (dragging) 1f else 0f, spring(dampingRatio = 0.5f, stiffness = 400f), label = "starGrab")

    var showPad by remember { mutableStateOf(false) }
    var pin by remember { mutableStateOf("") }
    var unlocked by remember { mutableStateOf(false) }
    var pinNote by remember { mutableStateOf("") }
    val shake = remember { Animatable(0f) }

    val wordsAlpha = animateFloatAsState(if (showPad) 0.16f else 1f, tween(450), label = "wordsAlpha")

    // star glides onto the focused title (first time: appears there and blooms in)
    val target = centres[focus]
    LaunchedEffect(focus, target, dragging) {
        if (target <= 0f || dragging) return@LaunchedEffect
        if (!placed) {
            starY.snapTo(target)
            placed = true
            bloom.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessLow))
        } else {
            starY.animateTo(target, spring(dampingRatio = 0.55f, stiffness = 380f))
        }
    }

    BackHandler(enabled = showPad && !unlocked) {
        showPad = false
        pin = ""
    }

    LaunchedEffect(pin) {
        if (pin.length == 4) {
            delay(160)
            if (PinStore.verify(pin)) {
                unlocked = true
                haptics.heavy()
                delay(420)
                onStart()
            } else {
                haptics.heavy()
                pinNote = PinStore.failureMessage()
                repeat(3) {
                    shake.animateTo(18f, tween(45))
                    shake.animateTo(-18f, tween(45))
                }
                shake.animateTo(0f, spring(dampingRatio = 0.5f))
                pin = ""
            }
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(Pal.Coral)) {
        val fontSize = (maxHeight.value / 15.5f).coerceIn(34f, 56f).sp

        // ── constantly moving backdrop ──
        Canvas(Modifier.fillMaxSize()) {
            val w = this.size.width
            val h = this.size.height
            val angle = clock.value * 2f * PI.toFloat()

            Blobs.forEach { b ->
                val centre = Offset(
                    w * (b.cx + b.ax * cos(angle * b.kx + b.phase)),
                    h * (b.cy + b.ay * sin(angle * b.ky + b.phase))
                )
                val radius = w * b.radius
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(b.color.copy(alpha = b.alpha), Color.Transparent),
                        center = centre,
                        radius = radius
                    ),
                    radius = radius,
                    center = centre
                )
            }

            // little twinkles drifting upward and pulsing
            Twinkles.forEach { t ->
                val pulse = 0.55f + 0.45f * sin(angle * t.k + t.phase)
                val drift = h * 0.012f * sin(angle * 2f + t.phase)
                val r = w * t.size * pulse
                fourPointStar(
                    Color.White.copy(alpha = 0.55f * pulse),
                    Offset(w * t.x, h * t.y + drift),
                    r,
                    r,
                    0.12f
                )
            }
        }

        // ── rail + star handle ──
        val density = LocalDensity.current
        val railX = with(density) { maxWidth.toPx() - 48.dp.toPx() }
        Canvas(Modifier.fillMaxSize().graphicsLayer { alpha = wordsAlpha.value }) {
            if (starY.value <= 0f) return@Canvas
            val top = centres.first()
            val bottom = centres.last()
            drawLine(
                Pal.OnCoral.copy(alpha = 0.14f),
                Offset(railX, top),
                Offset(railX, bottom),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round
            )
            centres.forEach { y -> drawCircle(Pal.OnCoral.copy(alpha = 0.28f), 3.dp.toPx(), Offset(railX, y)) }

            val c = Offset(railX, starY.value)
            val radius = 34.dp.toPx() * bloom.value * (1f + 0.12f * grab.value)
            // a radial gradient with radius 0 throws, and the star starts at 0 while it blooms in
            if (radius < 1f) return@Canvas
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(Pal.Sun.copy(alpha = 0.55f), Color.Transparent),
                    center = c,
                    radius = radius * 2.4f
                ),
                radius = radius * 2.4f,
                center = c
            )
            rotate(degrees = 360f * clock.value, pivot = c) {
                fourPointStar(Pal.Sun, c, radius * (1f - 0.18f * grab.value), radius * (1f + 0.25f * grab.value), 0.16f)
            }
        }

        // touch strip: only grabs when the finger lands near the star
        Box(
            Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(96.dp)
                .pointerInput(showPad) {
                    if (showPad) return@pointerInput
                    var grabbed = false
                    var y = 0f
                    detectVerticalDragGestures(
                        onDragStart = { offset ->
                            grabbed = abs(offset.y - starY.value) < 90.dp.toPx()
                            y = starY.value
                            dragging = grabbed
                            if (grabbed) haptics.click()
                        },
                        onDragEnd = {
                            if (grabbed) haptics.click()
                            grabbed = false
                            dragging = false
                        },
                        onDragCancel = {
                            grabbed = false
                            dragging = false
                        },
                        onVerticalDrag = { change, dragAmount ->
                            if (grabbed) {
                                change.consume()
                                y = (y + dragAmount).coerceIn(centres.first(), centres.last())
                                val at = y
                                scope.launch { starY.snapTo(at) }
                                var nearest = 0
                                for (i in centres.indices) {
                                    if (abs(centres[i] - at) < abs(centres[nearest] - at)) nearest = i
                                }
                                if (nearest != focus) {
                                    focus = nearest
                                    haptics.tick()
                                }
                            }
                        }
                    )
                }
        )

        // ── titles: swaying gently forever, the focused one pops ──
        Column(Modifier.fillMaxSize().statusBarsPadding().padding(start = 22.dp, top = 24.dp)) {
            titles.forEachIndexed { i, t ->
                SplashWord(
                    text = t,
                    index = i,
                    fontSize = fontSize,
                    clock = clock,
                    dim = wordsAlpha,
                    focused = i == focus,
                    centres = centres,
                    starY = starY.asState(),
                    enabled = !showPad,
                    onSelect = {
                        if (i != focus) {
                            focus = i
                            haptics.tick()
                        }
                    }
                )
            }
        }

        // ── bottom: Enter PIN pill ⇄ keypad ──
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp)
        ) {
            AnimatedContent(
                targetState = showPad,
                transitionSpec = {
                    (fadeIn(tween(300)) + slideInVertically(tween(380)) { it / 3 }) togetherWith
                            (fadeOut(tween(160)) + slideOutVertically(tween(260)) { it / 4 })
                },
                label = "splashBottom"
            ) { padVisible ->
                if (!padVisible) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(64.dp)
                            .bounceClick {
                                if (lockEnabled) showPad = true else onStart()
                            }
                            .border(1.dp, Pal.OnCoral, CircleShape)
                            .padding(horizontal = 28.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            if (lockEnabled) Icons.Outlined.Lock else Icons.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = Pal.OnCoral
                        )
                        Text(
                            if (lockEnabled) "Enter PIN" else "Get Started",
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Center,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Medium,
                            color = Pal.OnCoral
                        )
                        Icon(Icons.Filled.KeyboardArrowRight, contentDescription = null, tint = Pal.OnCoral)
                    }
                } else {
                    CompositionLocalProvider(LocalContentColor provides Pal.OnCoral) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                if (unlocked) "Welcome back" else "Enter your PIN",
                                fontSize = 22.sp,
                                color = Pal.OnCoral
                            )
                            Spacer(Modifier.height(16.dp))
                            Row(
                                Modifier.graphicsLayer { translationX = shake.value },
                                horizontalArrangement = Arrangement.spacedBy(18.dp)
                            ) {
                                repeat(4) { i ->
                                    val filled = i < pin.length || unlocked
                                    Row(
                                        Modifier
                                            .size(16.dp)
                                            .clip(CircleShape)
                                            .background(if (filled) Pal.OnCoral else Color.Transparent)
                                            .border(1.5.dp, Pal.OnCoral, CircleShape)
                                    ) {}
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(if (pinNote.isNotEmpty()) pinNote else "Enter your SplitPay PIN", fontSize = 12.sp, color = Pal.OnCoral.copy(alpha = 0.7f))
                            Spacer(Modifier.height(10.dp))
                            PayNumpad(showDot = false) { key ->
                                if (!unlocked && pin.length < 4 || key == "⌫") {
                                    pin = if (key == "⌫") pin.dropLast(1) else pin + key
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SplashWord(
    text: String,
    index: Int,
    fontSize: TextUnit,
    clock: State<Float>,
    dim: State<Float>,
    focused: Boolean,
    centres: MutableList<Float>,
    starY: State<Float>,
    enabled: Boolean,
    onSelect: () -> Unit
) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(120L + index * 85L)
        progress.animateTo(1f, tween(650, easing = FastOutSlowInEasing))
    }
    val color by animateColorAsState(
        targetValue = if (focused) Pal.OnCoral else Color(0xFFB4504D), // fixed: the splash is always coral
        animationSpec = tween(280),
        label = "wordColor"
    )
    Text(
        text,
        fontSize = fontSize,
        lineHeight = fontSize * 0.98f,
        fontWeight = if (focused) FontWeight.Normal else FontWeight.Light,
        letterSpacing = (-1.5).sp,
        color = color,
        maxLines = 1,
        softWrap = false,
        modifier = Modifier
            .onGloballyPositioned { coords ->
                val y = coords.positionInRoot().y + coords.size.height / 2f
                if (abs(centres[index] - y) > 0.5f) centres[index] = y
            }
            .bounceClick(enabled = enabled, onClick = onSelect)
            .graphicsLayer {
                val angle = (clock.value * 4f + index * 0.11f) * 2f * PI.toFloat() // 4 loops per clock = 6 s sway
                // 0..1: how close the star currently is to this title
                val near = (1f - abs(starY.value - centres[index]) / (fontSize.toPx() * 1.1f)).coerceIn(0f, 1f)
                alpha = progress.value * dim.value
                scaleX = 1f + 0.10f * near
                scaleY = 1f + 0.10f * near
                transformOrigin = TransformOrigin(0f, 0.5f)
                translationX = sin(angle) * 7.dp.toPx() + near * 12.dp.toPx()
                translationY = (1f - progress.value) * 40f + cos(angle) * 2.5.dp.toPx()
            }
    )
}