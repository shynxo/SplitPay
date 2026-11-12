package com.example.payrajpal

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.background

/** Shown after the splash until the user has logged in with PayPal. */
@Composable
fun LoginScreen() {
    val context = LocalContext.current
    var opening by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().background(Pal.Bg).navigationBarsPadding().padding(horizontal = 28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Sparkle(44.dp, Pal.Coral, spin = true)
        Spacer(Modifier.height(20.dp))
        Text("PayRajpal", fontSize = 40.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(8.dp))
        Text(
            "Pay anyone and split bills with AI.\nLog in with your PayPal account to start.",
            fontSize = 15.sp, color = Pal.Muted, textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(36.dp))
        CoralPill(
            title = "Log in with PayPal",
            subtitle = "Opens PayPal in your browser",
            loading = opening,
            haptic = HapticKind.Heavy,
            onClick = {
                Session.loginError = null
                opening = true
                try {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(AppConfig.SERVER_URL + "/auth/login")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                } catch (e: Exception) {
                    Session.loginError = "No browser found on this phone"
                }
                opening = false
            }
        )
        Session.loginError?.let {
            Spacer(Modifier.height(14.dp))
            Text(it, fontSize = 14.sp, color = Pal.CoralDeep, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(28.dp))
        Text(
            "Sandbox mode: log in with a PayPal test account. No real money moves.",
            fontSize = 12.sp, color = Pal.Muted, textAlign = TextAlign.Center
        )
    }
}
