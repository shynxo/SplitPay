package com.example.payrajpal

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.exp
import kotlin.math.roundToInt

/**
 * The home "Scan & Pay" button: a flat coral pill. Only the title shows by default.
 * Drag it up: it stretches like jelly and, past a small threshold, stays stretched to reveal [subtitle].
 * Drag down on it, or tap anywhere else (see [dismissOnOutsideTap]), and it settles back.
 */
@Composable
fun ScanPayButton(
    title: String,
    subtitle: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onBounds: (Rect) -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalHaptics.current
    val scope = rememberCoroutineScope()
    val expandedNow by rememberUpdatedState(expanded)

    val baseH = 66.dp
    val extra = animateDpAsState(
        targetValue = if (expanded) 38.dp else 0.dp,
        animationSpec = spring(dampingRatio = 0.42f, stiffness = 320f),
        label = "scanExtra"
    )
    val stretch = remember { Animatable(0f) }

    val subtitleHeight by animateDpAsState(
        targetValue = if (expanded) 24.dp else 0.dp,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 300f),
        label = "scanSubH"
    )
    val subtitleAlpha by animateFloatAsState(
        targetValue = if (expanded) 1f else 0f,
        animationSpec = tween(durationMillis = 240, delayMillis = if (expanded) 90 else 0),
        label = "scanSubA"
    )

    Box(
        modifier
            .fillMaxWidth()
            // height is driven in the layout phase, so the jelly stretch never recomposes anything
            .layout { measurable, constraints ->
                val h = (baseH.toPx() + extra.value.toPx() + stretch.value).roundToInt().coerceAtLeast(1)
                val placeable = measurable.measure(constraints.copy(minHeight = h, maxHeight = h))
                layout(placeable.width, h) { placeable.place(0, 0) }
            }
            .onGloballyPositioned { onBounds(it.boundsInWindow()) }
            .graphicsLayer {
                val f = (stretch.value / 60.dp.toPx()).coerceIn(0f, 1f)
                scaleX = 1f - 0.04f * f
                transformOrigin = TransformOrigin(0.5f, 1f)
            }
            .bounceClick(haptic = HapticKind.Heavy, onClick = onClick)
            .clip(CircleShape)
            .background(Pal.Coral)
            .pointerInput(Unit) {
                var raw = 0f
                var armed = false
                val limit = 60.dp.toPx()
                val threshold = 28.dp.toPx()

                fun release() {
                    if (raw > threshold && !expandedNow) {
                        onExpandedChange(true)
                        haptics.click()
                    } else if (raw < -threshold && expandedNow) {
                        onExpandedChange(false)
                        haptics.click()
                    }
                    raw = 0f
                    armed = false
                    scope.launch { stretch.animateTo(0f, spring(dampingRatio = 0.38f, stiffness = 260f)) }
                }

                detectVerticalDragGestures(
                    onDragStart = {
                        raw = 0f
                        armed = false
                    },
                    onDragEnd = { release() },
                    onDragCancel = { release() },
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        raw -= dragAmount // finger moving up = positive
                        val up = raw.coerceAtLeast(0f)
                        val rubber = limit * (1f - exp(-up / limit)) // resists the further you pull
                        scope.launch { stretch.snapTo(rubber) }
                        if (!armed && raw > threshold && !expandedNow) {
                            armed = true
                            haptics.tick()
                        }
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        // grab handle hinting that the button can be pulled up
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .padding(top = 7.dp)
                .size(width = 30.dp, height = 4.dp)
                .clip(CircleShape)
                .background(Pal.OnCoral.copy(alpha = 0.25f))
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, fontSize = 20.sp, fontWeight = FontWeight.Medium, color = Pal.OnCoral)
            Box(
                Modifier
                    .height(subtitleHeight)
                    .clipToBounds()
                    .graphicsLayer { alpha = subtitleAlpha },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    subtitle,
                    fontSize = 13.sp,
                    color = Pal.OnCoral.copy(alpha = 0.5f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.wrapContentHeight(align = Alignment.CenterVertically, unbounded = true)
                )
            }
        }
    }
}

/**
 * Calls [onDismiss] when a finger lands outside [bounds] (window coordinates) while [active].
 * Observes on the Initial pass without consuming, so the tapped control still works normally.
 */
fun Modifier.dismissOnOutsideTap(active: Boolean, bounds: () -> Rect, onDismiss: () -> Unit): Modifier = composed {
    var origin by remember { mutableStateOf(Offset.Zero) }
    this
        .onGloballyPositioned { origin = it.positionInWindow() }
        .pointerInput(active) {
            if (active) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    if (!bounds().contains(down.position + origin)) onDismiss()
                }
            }
        }
}