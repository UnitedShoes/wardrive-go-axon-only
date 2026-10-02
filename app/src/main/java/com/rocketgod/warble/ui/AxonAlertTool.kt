package com.rocketgod.warble.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rocketgod.warble.classify.NotableDevices
import com.rocketgod.warble.model.Contact
import com.rocketgod.warble.model.SignalType

private val AlertDark = Color(0xFF7A0000)
private val AlertBright = Color(0xFFFF1A1A)
private val ClearGreen = Color(0xFF7CFC00)

fun isAxon(c: Contact): Boolean =
    c.type != SignalType.CELL &&
        NotableDevices.threat(c.name, c.key, c.companyId, c.category)?.label?.startsWith("Axon", ignoreCase = true) == true

@Composable
fun AxonAlertTool(contacts: List<Contact>) {

    val target: Contact? = remember(contacts) { contacts.filter(::isAxon).maxByOrNull { it.smoothRssi } }
    val live = target != null
    val rssi = target?.let { Math.round(it.smoothRssi).toInt() }

    // Keep the screen awake while this view is showing so a driver doesn't lose the warning.
    val view = LocalView.current
    DisposableEffect(view) {
        val prior = view.keepScreenOn
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = prior }
    }

    // Pulse faster the closer the camera is.
    val periodMs = when {
        rssi == null -> 1000
        rssi >= -60 -> 280
        rssi >= -75 -> 450
        else -> 700
    }
    val pulse by rememberInfiniteTransition(label = "axonPulse").animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(periodMs, easing = LinearEasing), RepeatMode.Reverse),
        label = "axonPulseValue"
    )
    val p = if (UiFlags.reduceMotion) 1f else pulse

    val rssiNow = rememberUpdatedState(rssi)
    val ctx = LocalContext.current
    LaunchedEffect(live) {
        if (!live) return@LaunchedEffect
        val vib = ctx.getSystemService(android.content.Context.VIBRATOR_SERVICE) as? android.os.Vibrator
        while (true) {
            runCatching {
                if (android.os.Build.VERSION.SDK_INT >= 26)
                    vib?.vibrate(android.os.VibrationEffect.createOneShot(120, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
                else @Suppress("DEPRECATION") vib?.vibrate(120)
            }
            val r = rssiNow.value ?: -100
            kotlinx.coroutines.delay(when { r >= -60 -> 400L; r >= -75 -> 700L; else -> 1100L })
        }
    }

    val bg = if (live) lerp(AlertDark, AlertBright, p) else Color.Transparent
    val shape = RoundedCornerShape(10.dp)

    Box(Modifier.fillMaxSize().clip(shape).background(bg), contentAlignment = Alignment.Center) {
        if (target == null) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Icon(Icons.Filled.Videocam, null, tint = ClearGreen, modifier = Modifier.size(72.dp))
                Spacer(Modifier.height(12.dp))
                Text("ALL CLEAR", color = ClearGreen, fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 36.sp)
                Spacer(Modifier.height(6.dp))
                Text("No Axon cameras in range", color = Palette.muted, fontFamily = Mono, fontSize = 14.sp)
                Text("scanning…", color = Palette.muted, fontFamily = Mono, fontSize = 12.sp)
            }
        } else {
            val r = rssi ?: -100
            val (distLabel, distSub) = when {
                r >= -50 -> "RIGHT HERE" to "< 1 m"
                r >= -60 -> "VERY CLOSE" to "~1-3 m"
                r >= -70 -> "NEARBY" to "~3-8 m"
                r >= -80 -> "IN RANGE" to "~8-20 m"
                else -> "FAINT" to "weak signal"
            }
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 36.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Filled.Warning, null, tint = Color.White, modifier = Modifier.size(88.dp))
                Spacer(Modifier.height(8.dp))
                Text("AXON CAMERA", color = Color.White, fontFamily = Mono, fontWeight = FontWeight.Black,
                    fontSize = 40.sp, textAlign = TextAlign.Center, maxLines = 2)
                Spacer(Modifier.height(4.dp))
                Text("DETECTED", color = Color.White, fontFamily = Mono, fontWeight = FontWeight.Bold,
                    fontSize = 26.sp, textAlign = TextAlign.Center)
                Spacer(Modifier.height(20.dp))
                Text(distLabel, color = Color.White, fontFamily = Mono, fontWeight = FontWeight.Black,
                    fontSize = 34.sp, textAlign = TextAlign.Center, maxLines = 1)
                Text(distSub, color = Color.White.copy(alpha = 0.85f), fontFamily = Mono, fontSize = 16.sp)
                Spacer(Modifier.height(16.dp))
                Text("$r dBm", color = Color.White, fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 30.sp)
                Spacer(Modifier.height(6.dp))
                Text(target.name ?: target.maker ?: "Axon", color = Color.White.copy(alpha = 0.85f),
                    fontFamily = Mono, fontSize = 13.sp, maxLines = 1)
            }
        }
    }
}
