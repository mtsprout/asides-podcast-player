package com.asides.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
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
import androidx.compose.ui.res.painterResource
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
    LaunchedEffect(Unit) {
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
            Image(painterResource(R.drawable.splash_icon_static), null, Modifier.size(288.dp))
            // Just below the disc (about 90dp below centre), without moving the icon off the system splash's spot.
            Wordmark(fontSize = 38.sp, parentheses = false, modifier = Modifier.offset(y = 140.dp))
            Text(
                "Version $version",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 32.dp),
            )
        }
    }
}

private const val HOLD_MS = 3660L
