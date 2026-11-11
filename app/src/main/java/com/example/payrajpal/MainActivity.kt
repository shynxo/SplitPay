package com.example.payrajpal

import android.content.Context
import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.viewmodel.compose.viewModel

private const val PREFS = "payrajpal_prefs"
private const val KEY_DARK = "dark_mode"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Restore the saved theme before the first frame so there is no light→dark flash.
        val prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        Pal.dark = prefs.getBoolean(KEY_DARK, false)
        Pal.blend = if (Pal.dark) 1f else 0f

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
        )

        setContent {
            val dark = Pal.dark

            // Cross-fade every colour in the app when the theme flips.
            LaunchedEffect(dark) {
                animate(
                    initialValue = Pal.blend,
                    targetValue = if (dark) 1f else 0f,
                    animationSpec = tween(450)
                ) { value, _ -> Pal.blend = value }
            }

            // Remember the choice, and flip the status/navigation bar icon colours with it.
            LaunchedEffect(dark) { prefs.edit().putBoolean(KEY_DARK, dark).apply() }
            DisposableEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = if (dark) SystemBarStyle.dark(AndroidColor.TRANSPARENT)
                    else SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT),
                    navigationBarStyle = if (dark) SystemBarStyle.dark(AndroidColor.TRANSPARENT)
                    else SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
                )
                onDispose { }
            }

            val view = LocalView.current
            val haptics = remember(view) { Haptics(view) }

            PayTheme {
                CompositionLocalProvider(LocalHaptics provides haptics) {
                    Surface(modifier = Modifier.fillMaxSize(), color = Pal.Bg, contentColor = Pal.Ink) {
                        PayRajpalApp()
                    }
                }
            }
        }
    }
}

@Composable
fun PayRajpalApp(
    vm: SplitViewModel = viewModel(),
    ui: PayUiViewModel = viewModel()
) {
    var splash by rememberSaveable { mutableStateOf(true) }
    val nav = ui.nav

    BackHandler(enabled = !splash && nav.stack.size > 1) { nav.pop() }

    AnimatedContent(
        targetState = splash,
        transitionSpec = { fadeIn(tween(350)) togetherWith fadeOut(tween(250)) },
        label = "splashGate"
    ) { showSplash ->
        if (showSplash) {
            SplashScreen(onStart = { splash = false }, lockEnabled = ui.appLock)
        } else {
            AppNav(vm, ui, nav)
        }
    }
}

@Composable
private fun AppNav(vm: SplitViewModel, ui: PayUiViewModel, nav: Navigator) {
    val scrollTicks = rememberScrollTicks(LocalHaptics.current)

    Box(
        Modifier
            .fillMaxSize()
            .background(Pal.Bg)
            .statusBarsPadding()
            .nestedScroll(scrollTicks)
    ) {
        AnimatedContent(
            targetState = nav.current,
            transitionSpec = {
                val slide = spring<androidx.compose.ui.unit.IntOffset>(
                    dampingRatio = 0.9f,
                    stiffness = Spring.StiffnessMediumLow
                )
                if (nav.forward) {
                    (slideInHorizontally(slide) { it / 4 } + fadeIn(tween(280)) + scaleIn(tween(320), initialScale = 0.96f)) togetherWith
                            (slideOutHorizontally(slide) { -it / 6 } + fadeOut(tween(180)) + scaleOut(tween(280), targetScale = 0.96f))
                } else {
                    (slideInHorizontally(slide) { -it / 6 } + fadeIn(tween(280)) + scaleIn(tween(320), initialScale = 0.96f)) togetherWith
                            (slideOutHorizontally(slide) { it / 4 } + fadeOut(tween(180)) + scaleOut(tween(280), targetScale = 0.96f))
                }
            },
            label = "appNav"
        ) { route ->
            Box(Modifier.fillMaxSize().background(Pal.Bg)) {
                when (route) {
                    Route.Home -> HomeScreen(vm, ui, nav)
                    Route.History -> HistoryScreen(vm, nav)
                    Route.Autopays -> AutopayScreen(vm, ui, nav)
                    Route.Wallet -> WalletScreen(vm, ui, nav)
                    Route.AddCard -> AddCardScreen(ui, nav)
                    Route.TapToPay -> TapToPayScreen(ui, nav)
                    Route.Split -> SplitScreen(vm, nav)
                    Route.Security -> SecurityScreen(ui, nav)
                    Route.Scan -> ScanScreen(nav)
                    is Route.Pay -> PayFlowScreen(route, vm, ui, nav)
                }
            }
        }
    }
}