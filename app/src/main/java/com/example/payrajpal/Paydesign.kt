package com.example.payrajpal

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Backspace
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import kotlin.math.abs
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.text.NumberFormat
import java.util.Locale

/* ───────────────────────────── PALETTE ───────────────────────────── */

object Pal {
    /** Which theme the user picked. Persisted by MainActivity. */
    var dark by mutableStateOf(false)

    /** 0 = light, 1 = dark. Animated, so every colour below cross-fades when the theme flips. */
    var blend by mutableFloatStateOf(0f)

    // at rest (fully light or fully dark) skip the interpolation entirely
    private fun pick(light: Color, dk: Color): Color = when {
        blend <= 0f -> light
        blend >= 1f -> dk
        else -> lerp(light, dk, blend)
    }

    val Bg get() = pick(Color(0xFFE4E4D6), Color(0xFF121310))           // warm sage / near-black
    val Surface get() = pick(Color.White, Color(0xFF1E1F1B))
    val Lavender get() = pick(Color(0xFFE9EDFB), Color(0xFF2A2E42))
    val Green get() = pick(Color(0xFFBDE8A1), Color(0xFF2B4226))
    val Pink get() = pick(Color(0xFFEEA6BE), Color(0xFF4A2A38))
    val Butter get() = pick(Color(0xFFF8E1AB), Color(0xFF45391F))
    val Coral = Color(0xFFEB6862)
    val OnCoral = Color(0xFF121212)
    val CoralDeep get() = pick(Color(0xFFB4504D), Color(0xFFF2928D))
    val Line = Color(0xFF8DD66B)
    val Ink get() = pick(Color(0xFF121212), Color(0xFFF1F1E8))
    val OnInk get() = pick(Color.White, Color(0xFF121212))
    val Muted get() = pick(Color(0xFF6F6F66), Color(0xFFA3A398))
    val Hairline get() = pick(Color(0xFFCBCBBB), Color(0xFF3A3B34))
    val Danger get() = pick(Color(0xFFD64545), Color(0xFFFF7A70))
    val Credit get() = pick(Color(0xFF17794A), Color(0xFF6FE0A0))

    /** Bright butter that stays bright in both themes — for sparkles and other foreground accents. */
    val Sun = Color(0xFFF8E1AB)

    /** Card colours cycled through in every stacked list. */
    /** Pastel for item [index] in a stacked list (no list allocation). */
    fun stackAt(index: Int): Color = when (Math.floorMod(index, 4)) {
        0 -> Lavender
        1 -> Green
        2 -> Pink
        else -> Butter
    }

    /** Translucent white wash for chips/buttons that sit on a pastel card. Fades out in dark mode. */
    fun wash(alpha: Float): Color = Color.White.copy(alpha = alpha * (1f - 0.8f * blend))
}

private fun payScheme() = lightColorScheme(
    primary = Pal.Coral,
    onPrimary = Pal.OnCoral,
    primaryContainer = Pal.Butter,
    onPrimaryContainer = Pal.Ink,
    secondary = Pal.Ink,
    onSecondary = Pal.OnInk,
    tertiary = Pal.Line,
    background = Pal.Bg,
    onBackground = Pal.Ink,
    surface = Pal.Surface,
    onSurface = Pal.Ink,
    surfaceVariant = Pal.Lavender,
    onSurfaceVariant = Pal.Muted,
    outline = Pal.Hairline,
    outlineVariant = Pal.Hairline,
    error = Pal.Danger
)

private val PayShapes = Shapes(
    extraSmall = RoundedCornerShape(12.dp),
    small = RoundedCornerShape(18.dp),
    medium = RoundedCornerShape(24.dp),
    large = RoundedCornerShape(32.dp),
    extraLarge = RoundedCornerShape(40.dp)
)

@Composable
fun PayTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = payScheme(), shapes = PayShapes, content = content)
}

/* ───────────────────────────── FORMAT + ANDROID HELPERS ───────────────────────────── */

/** Works whatever numeric type (or string) your models use for money. */
fun Any?.asDouble(): Double = when (this) {
    null -> 0.0
    is Number -> this.toDouble()
    else -> this.toString().replace(",", "").toDoubleOrNull() ?: 0.0
}

// Built once: creating a NumberFormat per call is slow and this runs on every recomposition of a balance.
private val inrWithPaise: NumberFormat = NumberFormat.getNumberInstance(Locale.forLanguageTag("en-IN")).apply {
    minimumFractionDigits = 2
    maximumFractionDigits = 2
}
private val inrWhole: NumberFormat = NumberFormat.getNumberInstance(Locale.forLanguageTag("en-IN")).apply {
    minimumFractionDigits = 0
    maximumFractionDigits = 0
}

fun inr(value: Double, decimals: Boolean = true): String =
    "₹" + (if (decimals) inrWithPaise else inrWhole).format(value)

/** Drops the paise when they are .00 */
fun money(value: Double): String = inr(value, decimals = value % 1.0 != 0.0)

fun toast(context: Context, message: String) {
    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
}

fun shareText(context: Context, text: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(send, null))
}

fun copyText(context: Context, label: String, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText(label, text))
    toast(context, "$label copied")
}

/* ───────────────────────────── MODIFIERS ───────────────────────────── */

/** Springy press feedback used on every tappable thing. Put it BEFORE clip/background so the whole shape scales. */
fun Modifier.bounceClick(
    enabled: Boolean = true,
    haptic: HapticKind = HapticKind.Click,
    onClick: () -> Unit
): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val haptics = LocalHaptics.current
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.94f else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 520f),
        label = "bounce"
    )
    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clickable(
            interactionSource = source,
            indication = null,
            enabled = enabled,
            onClick = {
                haptics.fire(haptic)
                onClick()
            }
        )
}

/**
 * Parallax: a card shrinks very slightly the further its centre is from the middle of the screen,
 * so cards swell into full size as they scroll into view and ease away as they leave.
 */
fun Modifier.parallaxScale(minScale: Float = 0.95f): Modifier = composed {
    var centreFraction by remember { mutableFloatStateOf(0.5f) }
    this
        .onGloballyPositioned { coords ->
            val rootHeight = coords.findRootCoordinates().size.height.toFloat()
            if (rootHeight > 0f) {
                val fraction = (coords.positionInRoot().y + coords.size.height / 2f) / rootHeight
                if (abs(fraction - centreFraction) > 0.002f) centreFraction = fraction
            }
        }
        .graphicsLayer {
            val distance = (abs(centreFraction - 0.5f) * 2f).coerceIn(0f, 1f)
            val s = 1f - (1f - minScale) * distance
            scaleX = s
            scaleY = s
            // pivot on the bottom edge so a scaled card never pulls its lower edge up into view
            transformOrigin = TransformOrigin(0.5f, 1f)
        }
}

/**
 * Melts the last [fade] of a card into the page background. Draws one gradient over the card
 * (no offscreen layer), which is much cheaper than masking when many cards are on screen.
 */
fun Modifier.bottomFade(fade: Dp): Modifier = drawWithContent {
    drawContent()
    val f = fade.toPx().coerceAtMost(this.size.height)
    if (f > 0f) {
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color.Transparent, Pal.Bg),
                startY = this.size.height - f,
                endY = this.size.height
            ),
            topLeft = Offset(0f, this.size.height - f),
            size = Size(this.size.width, f)
        )
    }
}

/**
 * Reports a height [amount] shorter than the real one, so the NEXT item in a Column / LazyColumn
 * is placed on top of this one's bottom edge. This is what produces the overlapping card stack.
 */
fun Modifier.pullUp(amount: Dp): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    val pull = amount.roundToPx().coerceIn(0, placeable.height)
    layout(placeable.width, placeable.height - pull) { placeable.place(0, 0) }
}

/* ───────────────────────────── VISIBILITY HELPERS ─────────────────────────────
 * Always use these instead of calling AnimatedVisibility inside a Column/Row/Box lambda:
 * from a top-level function there is no implicit receiver, which avoids the
 * "cannot be called in this context with an implicit receiver" compile error.
 */

@Composable
fun Expand(visible: Boolean, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(tween(240)) + expandVertically(spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)),
        exit = fadeOut(tween(120)) + shrinkVertically(tween(220))
    ) { content() }
}

/** Fade + rise entrance, staggered by [index]. */
@Composable
fun Reveal(index: Int, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    var shown by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!shown) delay(index * 70L)
        shown = true
    }
    val p by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessLow),
        label = "reveal"
    )
    Box(
        modifier.graphicsLayer {
            alpha = p.coerceIn(0f, 1f)
            translationY = (1f - p) * 48f
        }
    ) { content() }
}

/* ───────────────────────────── SPARKLE ───────────────────────────── */

fun DrawScope.fourPointStar(color: Color, center: Offset, rx: Float, ry: Float, pinch: Float = 0.16f) {
    val path = Path().apply {
        moveTo(center.x, center.y - ry)
        quadraticBezierTo(center.x + rx * pinch, center.y - ry * pinch, center.x + rx, center.y)
        quadraticBezierTo(center.x + rx * pinch, center.y + ry * pinch, center.x, center.y + ry)
        quadraticBezierTo(center.x - rx * pinch, center.y + ry * pinch, center.x - rx, center.y)
        quadraticBezierTo(center.x - rx * pinch, center.y - ry * pinch, center.x, center.y - ry)
        close()
    }
    drawPath(path, color)
}

@Composable
fun Sparkle(dimension: Dp, color: Color, modifier: Modifier = Modifier, spin: Boolean = false) {
    val transition = rememberInfiniteTransition(label = "sparkle")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = if (spin) 90f else 0f,
        animationSpec = infiniteRepeatable(tween(2600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "sparkleAngle"
    )
    val pulse by transition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "sparklePulse"
    )
    Canvas(
        modifier
            .size(dimension)
            .graphicsLayer {
                rotationZ = angle
                scaleX = pulse
                scaleY = pulse
            }
    ) {
        fourPointStar(color, center, this.size.width / 2f, this.size.height / 2f, 0.2f)
    }
}

/* ───────────────────────────── BUTTONS + SMALL PARTS ───────────────────────────── */

@Composable
fun SmallCaps(text: String, modifier: Modifier = Modifier, color: Color = Pal.Ink) {
    Text(
        text.uppercase(),
        modifier = modifier,
        color = color,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 1.2.sp
    )
}

@Composable
fun CircleIconButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    dimension: Dp = 44.dp,
    container: Color = Color.Transparent
) {
    Box(
        modifier
            .size(dimension)
            .bounceClick(onClick = onClick)
            .clip(CircleShape)
            .background(container)
            .border(1.dp, Pal.Ink.copy(alpha = 0.5f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = description, tint = Pal.Ink, modifier = Modifier.size(dimension * 0.5f))
    }
}

@Composable
fun OutlinePill(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    selected: Boolean = false,
    compact: Boolean = false,
    tint: Color = Pal.Ink
) {
    val bg by animateColorAsState(if (selected) Pal.Ink else Color.Transparent, label = "pillBg")
    val fg by animateColorAsState(if (selected) Pal.OnInk else tint, label = "pillFg")
    Row(
        modifier
            .height(if (compact) 36.dp else 44.dp)
            .bounceClick(onClick = onClick)
            .clip(CircleShape)
            .background(bg)
            .border(1.dp, if (selected) Pal.Ink else tint.copy(alpha = 0.5f), CircleShape)
            .padding(horizontal = if (compact) 14.dp else 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, color = fg, fontSize = if (compact) 13.sp else 14.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

/** Row of selectable pills (filters, tabs, steppers). */
@Composable
fun PillTabs(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEachIndexed { i, label ->
            OutlinePill(label, onClick = { onSelect(i) }, selected = i == selected, compact = true)
        }
    }
}

@Composable
fun CoralPill(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    enabled: Boolean = true,
    loading: Boolean = false,
    haptic: HapticKind = HapticKind.Click,
    onClick: () -> Unit
) {
    val bg by animateColorAsState(if (enabled) Pal.Coral else Pal.Coral.copy(alpha = 0.35f), label = "coralBg")
    Column(
        modifier
            .fillMaxWidth()
            .height(if (subtitle != null) 74.dp else 62.dp)
            .bounceClick(enabled = enabled && !loading, haptic = haptic, onClick = onClick)
            .clip(CircleShape)
            .background(bg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (loading) {
            CircularProgressIndicator(Modifier.size(26.dp), color = Pal.OnCoral, strokeWidth = 2.5.dp)
        } else {
            Text(title, fontSize = 19.sp, fontWeight = FontWeight.Medium, color = Pal.OnCoral)
            if (subtitle != null) {
                Text(subtitle, fontSize = 13.sp, color = Pal.OnCoral.copy(alpha = 0.5f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** Sticks to the bottom of a Box, with a soft fade so content scrolls under it. */
@Composable
fun BoxScope.PinnedCta(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Pal.Bg.copy(alpha = 0.92f), Pal.Bg)))
            .navigationBarsPadding()
            .padding(start = 16.dp, end = 16.dp, top = 28.dp, bottom = 10.dp),
        content = content
    )
}

@Composable
fun WhiteBadge(icon: ImageVector, modifier: Modifier = Modifier, dimension: Dp = 46.dp, tint: Color = Pal.Ink) {
    Box(
        modifier.size(dimension).clip(CircleShape).background(Pal.Surface),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(dimension * 0.48f))
    }
}

@Composable
fun StatusChip(text: String, modifier: Modifier = Modifier, container: Color = Pal.wash(0.65f)) {
    Box(modifier.clip(CircleShape).background(container).padding(horizontal = 12.dp, vertical = 6.dp)) {
        Text(text, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

fun avatarTint(name: String): Color = Pal.stackAt(name.hashCode())

@Composable
fun AvatarCircle(name: String, dimension: Dp = 48.dp, modifier: Modifier = Modifier) {
    Box(
        modifier.size(dimension).clip(CircleShape).background(avatarTint(name)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            name.trim().take(1).uppercase(),
            fontWeight = FontWeight.SemiBold,
            fontSize = (dimension.value * 0.4f).sp
        )
    }
}

@Composable
fun OverlapAvatars(names: List<String>, dimension: Dp = 40.dp) {
    Row(horizontalArrangement = Arrangement.spacedBy(-(dimension.value * 0.25f).dp)) {
        names.forEach { n ->
            Box(
                Modifier.size(dimension).clip(CircleShape).background(Pal.Surface).padding(2.dp)
            ) { AvatarCircle(n, dimension - 4.dp) }
        }
    }
}

@Composable
fun DetailRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 14.sp, color = Pal.Ink.copy(alpha = 0.55f))
        Spacer(Modifier.width(16.dp))
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.End, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun PaySwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    val haptics = LocalHaptics.current
    Switch(
        checked = checked,
        onCheckedChange = { value ->
            haptics.click()
            onChange(value)
        },
        colors = SwitchDefaults.colors(
            checkedThumbColor = Pal.OnInk,
            checkedTrackColor = Pal.Ink,
            checkedBorderColor = Pal.Ink,
            uncheckedThumbColor = Pal.Ink,
            uncheckedTrackColor = Color.Transparent,
            uncheckedBorderColor = Pal.Ink
        )
    )
}

@Composable
fun SettingRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit, subtitle: String? = null) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            if (subtitle != null) Text(subtitle, fontSize = 13.sp, color = Pal.Ink.copy(alpha = 0.55f))
        }
        PaySwitch(checked, onChange)
    }
}

@Composable
fun ActionRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().bounceClick(onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        WhiteBadge(icon)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Text(subtitle, fontSize = 13.sp, color = Pal.Ink.copy(alpha = 0.55f))
        }
        Icon(Icons.Filled.KeyboardArrowRight, contentDescription = null, tint = Pal.Ink.copy(alpha = 0.6f))
    }
}

/* ───────────────────────────── STACKED CARD ───────────────────────────── */

/**
 * The signature surface from the reference: a big colour block with deep rounded TOP corners that the
 * next card slides over. Give every card except the last an [overlap]; the last one gets bottomRounded = true.
 */
@Composable
fun StackCard(
    color: Color,
    modifier: Modifier = Modifier,
    overlap: Dp = 32.dp,
    bottomRounded: Boolean = false,
    extraBottom: Dp = 0.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val radius = 36.dp
    val shape = RoundedCornerShape(
        topStart = radius,
        topEnd = radius,
        bottomStart = if (bottomRounded) radius else 0.dp,
        bottomEnd = if (bottomRounded) radius else 0.dp
    )
    val haptics = LocalHaptics.current
    val sizeAnim = Modifier.animateContentSize(spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow))
    val clickMod = if (onClick != null) {
        Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = {
                haptics.click()
                onClick()
            }
        )
    } else {
        Modifier
    }
    Column(
        modifier
            .padding(horizontal = 12.dp)
            .fillMaxWidth()
            .pullUp(overlap)
            .parallaxScale()
            .bottomFade((24.dp + overlap + extraBottom).coerceAtMost(40.dp))
            .clip(shape)
            .background(color)
            .then(clickMod)
            .then(if (onClick != null) sizeAnim else Modifier) // only expandable cards pay for size animation
            .padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 24.dp + overlap + extraBottom),
        content = content
    )
}

/* ───────────────────────────── TOP BAR + INPUTS ───────────────────────────── */

@Composable
fun PayTopBar(title: String, onBack: () -> Unit, trailing: @Composable RowScope.() -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().padding(start = 12.dp, end = 20.dp, top = 8.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(44.dp).bounceClick(onClick = onBack),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.KeyboardArrowLeft, contentDescription = "Back", modifier = Modifier.size(32.dp))
        }
        Text(title, fontSize = 24.sp, fontWeight = FontWeight.Normal, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        trailing()
    }
}

@Composable
fun PayTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    placeholder: String = "",
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    var focused by remember { mutableStateOf(false) }
    val borderColor by animateColorAsState(if (focused) Pal.Ink else Pal.Hairline, label = "fieldBorder")
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Pal.Surface)
            .border(1.5.dp, borderColor, RoundedCornerShape(22.dp))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(label, fontSize = 11.sp, color = Pal.Muted, letterSpacing = 0.4.sp)
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = TextStyle(fontSize = 17.sp, color = Pal.Ink, fontWeight = FontWeight.Medium),
                cursorBrush = SolidColor(Pal.Ink),
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                visualTransformation = visualTransformation,
                modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused },
                decorationBox = { inner ->
                    Box(Modifier.padding(vertical = 2.dp)) {
                        if (value.isEmpty() && placeholder.isNotEmpty()) {
                            Text(placeholder, fontSize = 17.sp, color = Pal.Muted.copy(alpha = 0.6f))
                        }
                        inner()
                    }
                }
            )
        }
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            trailing()
        }
    }
}

/* ───────────────────────────── NUMPAD ───────────────────────────── */

@Composable
fun PayNumpad(showDot: Boolean, onKey: (String) -> Unit) {
    val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", if (showDot) "." else "", "0", "⌫")
    Column {
        keys.chunked(3).forEach { rowKeys ->
            Row(Modifier.fillMaxWidth()) {
                rowKeys.forEach { key ->
                    val tap = if (key.isNotEmpty()) {
                        Modifier.bounceClick { onKey(key) }
                    } else {
                        Modifier
                    }
                    Box(
                        Modifier.weight(1f).height(54.dp).then(tap),
                        contentAlignment = Alignment.Center
                    ) {
                        if (key == "⌫") {
                            Icon(Icons.Outlined.Backspace, contentDescription = "Delete", modifier = Modifier.size(26.dp))
                        } else {
                            Text(key, fontSize = 26.sp)
                        }
                    }
                }
            }
        }
    }
}