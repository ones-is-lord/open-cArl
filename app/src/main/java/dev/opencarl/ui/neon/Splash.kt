package dev.opencarl.ui.neon

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import dev.opencarl.appearance.AppearanceSettings
import dev.opencarl.appearance.Neon
import dev.opencarl.appearance.NeonWordmark
import kotlinx.coroutines.delay

/**
 * Launch screen. Uses a drawable named carl_splash when one is bundled (the
 * detailed artwork), otherwise falls back to the wordmark on black so the app
 * never ships a blank frame.
 */
@Composable
fun CarlSplash(appearance: AppearanceSettings, onDone: () -> Unit) {
    val context = LocalContext.current
    val art = remember {
        context.resources.getIdentifier("carl_splash", "drawable", context.packageName).takeIf { it != 0 }
    }
    LaunchedEffect(Unit) {
        delay(if (art != null) 1_200L else 700L)
        onDone()
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(Neon.Black),
        contentAlignment = Alignment.Center,
    ) {
        if (art != null) {
            Image(
                painter = painterResource(id = art),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().padding(12.dp),
            )
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                NeonWordmark(
                    text = appearance.brandLabel,
                    appearance = appearance,
                    modifier = Modifier.padding(horizontal = 24.dp),
                    maxSp = 56f,
                    minSp = 26f,
                )
                Tagline(
                    text = appearance.bannerLabel,
                    uppercase = appearance.bannerUppercase,
                    availablePx = 900f,
                    maxSp = 14f,
                )
            }
        }
        NeonFrameOverlay(Modifier.fillMaxSize())
    }
}
