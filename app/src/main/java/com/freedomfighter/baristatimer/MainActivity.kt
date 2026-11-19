package com.freedomfighter.baristatimer

import android.os.Bundle
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The screen is always dark: light icons in both bars, whatever the system theme.
        enableEdgeToEdge(SystemBarStyle.dark(android.graphics.Color.TRANSPARENT), SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
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

    // the brewer's care, one quiet line after the brew, a page when it is tapped
    var care by remember { mutableStateOf(false) }

    BackHandler(enabled = care) { care = false }
    BackHandler(enabled = !care && phase != Phase.IDLE) { phase = Phase.IDLE }

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
        if (care) care = false
        else when (phase) {
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
        if (care) CareContent()
        else when (phase) {
            Phase.IDLE -> IdleContent()
            Phase.HEATING -> HeatingContent(now - heatingStart)
            Phase.EXTRACTING -> ExtractingContent(now - extractionStart)
            Phase.DONE -> DoneContent(heatingMs, extractionMs) { care = true }
        }
        if (phase == Phase.IDLE && !care) {
            Text(
                stringResource(R.string.credits),
                color = Dim, fontSize = 11.sp, letterSpacing = 1.sp,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp)
            )
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
        Text(stringResource(R.string.espresso_timer), color = Copper, fontSize = 22.sp, fontWeight = FontWeight.Bold, letterSpacing = 4.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(10.dp))
        // the machine is named, and the app says at once that it is not the maker's
        Text(stringResource(R.string.for_machine), color = Dim, fontSize = 13.sp, letterSpacing = 1.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(64.dp))
        PulsingHint(stringResource(R.string.tap_to_start))
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
        Text(stringResource(R.string.heating), color = Copper, fontSize = 16.sp, fontWeight = FontWeight.Medium, letterSpacing = 6.sp)
        Spacer(Modifier.height(48.dp))
        Text(
            "%d:%02d".format(total / 60, total % 60),
            color = if (over) Red else WarmWhite,
            fontSize = 72.sp,
            fontWeight = FontWeight.Light
        )
        Spacer(Modifier.height(16.dp))
        Text(
            if (over) stringResource(R.string.remove_from_heat) else stringResource(R.string.heating_typical),
            color = if (over) Red else Dim,
            fontSize = 14.sp,
            letterSpacing = 2.sp
        )
        Spacer(Modifier.weight(1f))
        PulsingHint(stringResource(R.string.tap_when_appears))
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
        Text(stringResource(R.string.extracting), color = Copper, fontSize = 16.sp, fontWeight = FontWeight.Medium, letterSpacing = 6.sp)
        Spacer(Modifier.height(32.dp))
        Box(contentAlignment = Alignment.Center) {
            ExtractionRing(seconds, Modifier.size(240.dp))
            Text("%.1f".format(seconds), color = timerColor, fontSize = 64.sp, fontWeight = FontWeight.Light)
        }
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.target_time), color = Dim, fontSize = 14.sp, letterSpacing = 2.sp)
        Spacer(Modifier.weight(1f))
        PulsingHint(stringResource(R.string.tap_when_done))
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
private fun DoneContent(heatingMs: Long, extractionMs: Long, onCare: () -> Unit) {
    val heatSec = (heatingMs / 1000).toInt()
    val extSec = extractionMs / 1000f
    val extColor = if (extSec in 25f..30f) Green else Red
    val verdict = stringResource(when {
        extSec < 25f -> R.string.grind_finer
        extSec > 30f -> R.string.grind_coarser
        else -> R.string.perfect
    })

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxSize()
    ) {
        Spacer(Modifier.weight(1f))
        Text(stringResource(R.string.done), color = Copper, fontSize = 16.sp, fontWeight = FontWeight.Medium, letterSpacing = 6.sp)
        Spacer(Modifier.height(48.dp))

        Text(stringResource(R.string.heating), color = Dim, fontSize = 12.sp, letterSpacing = 3.sp)
        Spacer(Modifier.height(4.dp))
        Text("%d:%02d".format(heatSec / 60, heatSec % 60), color = WarmWhite, fontSize = 36.sp, fontWeight = FontWeight.Light)

        Spacer(Modifier.height(32.dp))

        Text(stringResource(R.string.extraction), color = Dim, fontSize = 12.sp, letterSpacing = 3.sp)
        Spacer(Modifier.height(4.dp))
        Text("%.1fs".format(extSec), color = extColor, fontSize = 48.sp, fontWeight = FontWeight.Light)
        Spacer(Modifier.height(12.dp))
        Text(verdict, color = extColor, fontSize = 14.sp, fontWeight = FontWeight.Medium, letterSpacing = 3.sp)

        Spacer(Modifier.height(24.dp))
        Text(stringResource(R.string.target_weight), color = Dim, fontSize = 12.sp, letterSpacing = 2.sp)

        Spacer(Modifier.weight(1f))
        // one quiet line, dimmer than the rest: the brewer's care is a tap away, never in the way
        Text(
            stringResource(R.string.care_line),
            color = Dim.copy(alpha = 0.7f),
            fontSize = 13.sp,
            letterSpacing = 1.sp,
            modifier = Modifier
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onCare)
                .padding(horizontal = 24.dp, vertical = 12.dp)
        )
        Spacer(Modifier.height(8.dp))
        PulsingHint(stringResource(R.string.tap_to_reset))
        Spacer(Modifier.height(32.dp))
    }
}

/** The brewer's care, from 9Barista's own guides: when, and what. Scrolls when the screen is small; a tap anywhere goes back. */
@Composable
private fun CareContent() {
    val items = listOf(
        R.string.care_when_1 to R.string.care_what_1,
        R.string.care_when_2 to R.string.care_what_2,
        R.string.care_when_3 to R.string.care_what_3,
        R.string.care_when_4 to R.string.care_what_4,
        R.string.care_when_5 to R.string.care_what_5
    )
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 32.dp)
    ) {
        Spacer(Modifier.height(48.dp))
        Text(stringResource(R.string.care_title), color = Copper, fontSize = 14.sp, fontWeight = FontWeight.Medium, letterSpacing = 4.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(36.dp))
        items.forEach { (whenRes, whatRes) ->
            Text(stringResource(whenRes), color = Dim, fontSize = 12.sp, letterSpacing = 3.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(6.dp))
            Text(stringResource(whatRes), color = WarmWhite, fontSize = 15.sp, fontWeight = FontWeight.Light, lineHeight = 22.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(26.dp))
        }
        Text(stringResource(R.string.care_source), color = Dim.copy(alpha = 0.7f), fontSize = 11.sp, letterSpacing = 1.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(36.dp))
        PulsingHint(stringResource(R.string.tap_to_go_back))
        Spacer(Modifier.height(32.dp))
    }
}
