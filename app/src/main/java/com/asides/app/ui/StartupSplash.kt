package com.asides.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.viewinterop.AndroidView

import android.widget.ImageView
import androidx.compose.ui.unit.dp
import com.asides.app.R
import kotlinx.coroutines.delay

/**
 * Picks up where the system splash screen leaves off: the same icon in the same place, plus the
 * version at the bottom (the system splash can't show text), then fades into the app. Shown once
 * per launch, not again after a rotation.
 */
@Composable
fun StartupSplash(version: String) {
    var visible by rememberSaveable { mutableStateOf(true) }
    // The logo's own animation and the name and version rise together, landing at the same moment.
    var logoStarted by rememberSaveable { mutableStateOf(false) }
    val arrival = remember { Animatable(if (logoStarted) 1f else 0f) }
    LaunchedEffect(logoStarted) {
        if (logoStarted) arrival.animateTo(1f, tween(ARRIVAL_MS, easing = CubicBezierEasing(0.215f, 0.61f, 0.355f, 1f)))
    }
    LaunchedEffect(Unit) {
        if (!logoStarted) delay(LOGO_DELAY_MS)
        logoStarted = true
        delay(HOLD_MS)
        visible = false
    }
    AnimatedVisibility(visible, exit = fadeOut(tween(500))) {
        Box(
            Modifier.fillMaxSize()
                // The same colour the system splash uses, so the hand-over is invisible.
                .background(colorResource(R.color.window_bg))
                // Don't let taps through to the app underneath while this is showing.
                .pointerInput(Unit) {},
            contentAlignment = Alignment.Center,
        ) {
            // The system splash draws its icon on a 288dp canvas; match it.
            // Invisible until its turn, then the parentheses slide in and the sound wave grows.
            AndroidView(
                factory = { ctx ->
                    ImageView(ctx).apply {
                        scaleType = ImageView.ScaleType.FIT_CENTER
                        setImageDrawable(ctx.getDrawable(R.drawable.splash_icon))
                    }
                },
                update = { v ->
                    if (logoStarted) {
                        if (v.alpha == 0f) {
                            v.alpha = 1f
                            (v.drawable as? android.graphics.drawable.Animatable)?.start()
                        }
                    } else v.alpha = 0f
                },
                modifier = Modifier.size(288.dp),
            )
            // Just below the disc, without moving the icon off the system splash's spot.
            Column(
                Modifier.offset(y = 152.dp).graphicsLayer {
                    alpha = arrival.value
                    translationY = (1f - arrival.value) * 56.dp.toPx()
                },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Wordmark(fontSize = 38.sp, parentheses = false)
                Text(
                    "Version $version",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                )
            }
        }
    }
}

// The parentheses slide for 1300 ms (res/animator/paren_*_in.xml); the text takes the same time.
private const val ARRIVAL_MS = 1300
private const val LOGO_DELAY_MS = 300L
private const val HOLD_MS = 2900L
