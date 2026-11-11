package com.example.payrajpal

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.math.abs

enum class HapticKind { Tick, Click, Heavy }

/**
 * One place for every vibration in the app.
 *  - Tick  : barely-there detent, used while scrolling
 *  - Click : normal tap
 *  - Heavy : the "thunk" for money leaving the account
 *
 * Heavy uses the system's EFFECT_HEAVY_CLICK when the VIBRATE permission is declared, otherwise it falls
 * back to the strongest haptic that needs no permission. Everything is wrapped so it can never crash.
 */
class Haptics(private val view: View?) {

    private val vibrator: Vibrator? by lazy {
        view?.context?.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    fun fire(kind: HapticKind) = when (kind) {
        HapticKind.Tick -> tick()
        HapticKind.Click -> click()
        HapticKind.Heavy -> heavy()
    }

    fun tick() {
        val v = view ?: return
        v.performHapticFeedback(
            if (Build.VERSION.SDK_INT >= 27) HapticFeedbackConstants.TEXT_HANDLE_MOVE else HapticFeedbackConstants.CLOCK_TICK
        )
    }

    fun click() {
        val v = view ?: return
        v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
    }

    fun heavy() {
        val v = view ?: return
        if (Build.VERSION.SDK_INT >= 29) {
            try {
                val vib = vibrator
                if (vib != null && vib.hasVibrator()) {
                    vib.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK))
                    return
                }
            } catch (_: Exception) {
                // missing permission or unsupported — use the fallback below
            }
        }
        v.performHapticFeedback(
            if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.LONG_PRESS
        )
    }
}

val LocalHaptics = staticCompositionLocalOf { Haptics(null) }

/**
 * Attach with Modifier.nestedScroll(...) on the app root: every list underneath gets a soft detent
 * every ~72dp of real scrolling, throttled so a fling doesn't turn into a buzz.
 */
@Composable
fun rememberScrollTicks(haptics: Haptics): NestedScrollConnection {
    val stepPx = with(LocalDensity.current) { 72.dp.toPx() }
    return remember(haptics, stepPx) {
        object : NestedScrollConnection {
            private var travelled = 0f
            private var lastTick = 0L

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                travelled += abs(consumed.y)
                if (travelled >= stepPx) {
                    travelled = 0f
                    val now = System.nanoTime()
                    if (now - lastTick > 60_000_000L) {
                        lastTick = now
                        haptics.tick()
                    }
                }
                return Offset.Zero
            }
        }
    }
}