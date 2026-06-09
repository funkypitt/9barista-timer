package com.freedomfighter.baristatimer

import android.os.Bundle
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent { TimerScreen() }
    }
}

private enum class Phase { IDLE, HEATING, EXTRACTING, DONE }

private val Copper = Color(0xFFC87941)
private val WarmWhite = Color(0xFFF5E6D3)
private val Dim = Color(0xFF6B6560)
private val Green = Color(0xFF6B9E6B)
private val Red = Color(0xFFC75050)
private val BgDefault = Color(0xFF0D0D0D)
private val BgHeating = Color(0xFF1A0E05)

@Composable
private fun TimerScreen() {
    var phase by remember { mutableStateOf(Phase.IDLE) }
    var heatingStart by remember { mutableLongStateOf(0L) }
    var extractionStart by remember { mutableLongStateOf(0L) }
    var heatingMs by remember { mutableLongStateOf(0L) }
    var extractionMs by remember { mutableLongStateOf(0L) }
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }

    val context = LocalContext.current
    val vibrator = remember { context.getSystemService(Vibrator::class.java) }

    BackHandler(enabled = phase != Phase.IDLE) { phase = Phase.IDLE }

    LaunchedEffect(phase) {
        if (phase != Phase.HEATING && phase != Phase.EXTRACTING) return@LaunchedEffect
        var buzzed25 = false
        var buzzed30 = false
        var buzzed8min = false
        while (true) {
            now = SystemClock.elapsedRealtime()
            if (phase == Phase.EXTRACTING) {
                val s = (now - extractionStart) / 1000f
                if (!buzzed25 && s >= 25f) {
                    vibrator?.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE))
                    buzzed25 = true
                }
                if (!buzzed30 && s >= 30f) {
                    vibrator?.vibrate(VibrationEffect.createOneShot(300, VibrationEffect.DEFAULT_AMPLITUDE))
                    buzzed30 = true
                }
            }
            if (phase == Phase.HEATING && !buzzed8min) {
                if ((now - heatingStart) / 1000f >= 480f) {
                    vibrator?.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE))
                    buzzed8min = true
                }
            }
            delay(50)
        }
    }

    val onTap: () -> Unit = {
        val t = SystemClock.elapsedRealtime()
        when (phase) {
            Phase.IDLE -> { heatingStart = t; now = t; phase = Phase.HEATING }
            Phase.HEATING -> { heatingMs = t - heatingStart; extractionStart = t; now = t; phase = Phase.EXTRACTING }
            Phase.EXTRACTING -> { extractionMs = t - extractionStart; now = t; phase = Phase.DONE }
            Phase.DONE -> { phase = Phase.IDLE }
        }
    }

    val bgColor by animateColorAsState(
        targetValue = if (phase == Phase.HEATING) BgHeating else BgDefault,
        animationSpec = tween(600),
        label = "bg"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onTap
            )
            .systemBarsPadding(),
        contentAlignment = Alignment.Center
    ) {
        when (phase) {
            Phase.IDLE -> IdleContent()
            Phase.HEATING -> HeatingContent(now - heatingStart)
            Phase.EXTRACTING -> ExtractingContent(now - extractionStart)
            Phase.DONE -> DoneContent(heatingMs, extractionMs)
        }
    }
}

@Composable
private fun PulsingHint(text: String) {
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 0.25f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(tween(1500), RepeatMode.Reverse),
        label = "alpha"
    )
    Text(text, color = WarmWhite.copy(alpha = pulse), fontSize = 13.sp, letterSpacing = 1.sp)
}

@Composable
private fun IdleContent() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("9BARISTA", color = Copper, fontSize = 32.sp, fontWeight = FontWeight.Bold, letterSpacing = 8.sp)
        Spacer(Modifier.height(8.dp))
        Text("ESPRESSO TIMER", color = Dim, fontSize = 14.sp, letterSpacing = 4.sp)
        Spacer(Modifier.height(64.dp))
        PulsingHint("TAP TO START")
    }
}

@Composable
private fun HeatingContent(elapsedMs: Long) {
    val total = (elapsedMs / 1000).toInt()
    val over = total >= 480

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxSize()
    ) {
        Spacer(Modifier.weight(1f))
        Text("HEATING", color = Copper, fontSize = 16.sp, fontWeight = FontWeight.Medium, letterSpacing = 6.sp)
        Spacer(Modifier.height(48.dp))
        Text(
            "%d:%02d".format(total / 60, total % 60),
            color = if (over) Red else WarmWhite,
            fontSize = 72.sp,
            fontWeight = FontWeight.Light
        )
        Spacer(Modifier.height(16.dp))
        Text(
            if (over) "REMOVE FROM HEAT" else "3–6 min typical",
            color = if (over) Red else Dim,
            fontSize = 14.sp,
            letterSpacing = 2.sp
        )
        Spacer(Modifier.weight(1f))
        PulsingHint("TAP WHEN ESPRESSO APPEARS")
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun ExtractingContent(elapsedMs: Long) {
    val seconds = elapsedMs / 1000f
    val timerColor = when {
        seconds < 25f -> WarmWhite
        seconds <= 30f -> Green
        else -> Red
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxSize()
    ) {
        Spacer(Modifier.weight(1f))
        Text("EXTRACTING", color = Copper, fontSize = 16.sp, fontWeight = FontWeight.Medium, letterSpacing = 6.sp)
        Spacer(Modifier.height(32.dp))
        Box(contentAlignment = Alignment.Center) {
            ExtractionRing(seconds, Modifier.size(240.dp))
            Text("%.1f".format(seconds), color = timerColor, fontSize = 64.sp, fontWeight = FontWeight.Light)
        }
        Spacer(Modifier.height(16.dp))
        Text("TARGET  25–30s", color = Dim, fontSize = 14.sp, letterSpacing = 2.sp)
        Spacer(Modifier.weight(1f))
        PulsingHint("TAP WHEN DONE")
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun ExtractionRing(elapsedSeconds: Float, modifier: Modifier = Modifier) {
    val track = Color(0xFF2A2A2A)

    Canvas(modifier = modifier) {
        val sw = 6.dp.toPx()
        val pad = sw / 2
        val arcSize = Size(size.width - sw, size.height - sw)
        val arcOffset = Offset(pad, pad)

        drawArc(track, 0f, 360f, false, arcOffset, arcSize, style = Stroke(sw))

        drawArc(
            Green.copy(alpha = 0.15f),
            startAngle = (25f / 30f) * 360f - 90f,
            sweepAngle = (5f / 30f) * 360f,
            useCenter = false,
            topLeft = arcOffset,
            size = arcSize,
            style = Stroke(sw)
        )

        val sweep = (elapsedSeconds / 30f).coerceAtMost(1f) * 360f
        val color = when {
            elapsedSeconds < 25f -> Copper
            elapsedSeconds <= 30f -> Green
            else -> Red
        }
        drawArc(color, -90f, sweep, false, arcOffset, arcSize, style = Stroke(sw, cap = StrokeCap.Round))
    }
}

@Composable
private fun DoneContent(heatingMs: Long, extractionMs: Long) {
    val heatSec = (heatingMs / 1000).toInt()
    val extSec = extractionMs / 1000f
    val extColor = if (extSec in 25f..30f) Green else Red
    val verdict = when {
        extSec < 25f -> "GRIND FINER"
        extSec > 30f -> "GRIND COARSER"
        else -> "PERFECT"
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxSize()
    ) {
        Spacer(Modifier.weight(1f))
        Text("DONE", color = Copper, fontSize = 16.sp, fontWeight = FontWeight.Medium, letterSpacing = 6.sp)
        Spacer(Modifier.height(48.dp))

        Text("HEATING", color = Dim, fontSize = 12.sp, letterSpacing = 3.sp)
        Spacer(Modifier.height(4.dp))
        Text("%d:%02d".format(heatSec / 60, heatSec % 60), color = WarmWhite, fontSize = 36.sp, fontWeight = FontWeight.Light)

        Spacer(Modifier.height(32.dp))

        Text("EXTRACTION", color = Dim, fontSize = 12.sp, letterSpacing = 3.sp)
        Spacer(Modifier.height(4.dp))
        Text("%.1fs".format(extSec), color = extColor, fontSize = 48.sp, fontWeight = FontWeight.Light)
        Spacer(Modifier.height(12.dp))
        Text(verdict, color = extColor, fontSize = 14.sp, fontWeight = FontWeight.Medium, letterSpacing = 3.sp)

        Spacer(Modifier.height(24.dp))
        Text("TARGET  35–40g", color = Dim, fontSize = 12.sp, letterSpacing = 2.sp)

        Spacer(Modifier.weight(1f))
        PulsingHint("TAP TO RESET")
        Spacer(Modifier.height(32.dp))
    }
}
