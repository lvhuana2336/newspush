package com.newspush.app.ui

import android.content.Context
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import com.newspush.app.R
import com.newspush.app.data.model.AppSettings
import com.newspush.app.util.decodeSampledBitmap
import kotlinx.coroutines.delay

@Composable
fun AppRoot(settings: AppSettings) {
    var showSplash by rememberSaveable { mutableStateOf(true) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // NavHost 在开屏期间已经组合，Room/WorkManager 因此可以提前预热。
        CustomBackground(settings.customBackgroundPath)
        NewsNavHost()

        if (showSplash) {
            SplashOverlay(
                customIconPath = settings.customIconPath,
                onFinished = { showSplash = false }
            )
        }
    }
}

@Composable
private fun CustomBackground(path: String) {
    if (path.isBlank()) return
    val bitmap = remember(path) {
        decodeSampledBitmap(path, 1600, 1600)
    } ?: return

    Image(
        bitmap = bitmap.asImageBitmap(),
        contentDescription = null,
        modifier = Modifier.fillMaxSize(),
        contentScale = ContentScale.Crop
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.82f))
    )
}

@Composable
private fun SplashOverlay(
    customIconPath: String,
    onFinished: () -> Unit
) {
    val context = LocalContext.current
    val icon = remember(customIconPath) { loadIconBitmap(context, customIconPath) }
    val progress = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        delay(420)
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 760, easing = FastOutSlowInEasing)
        )
        delay(60)
        onFinished()
    }

    val p = progress.value
    val iconSize = 128.dp
    val travel = iconSize * 1.5f * p

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.alpha(1f - p)
        ) {
            Box(
                modifier = Modifier
                    .size(iconSize, iconSize * 0.5f)
                    .offset(y = -travel)
                    .clipToBounds()
            ) {
                Image(
                    bitmap = icon,
                    contentDescription = null,
                    modifier = Modifier
                        .size(iconSize)
                        .align(Alignment.TopCenter)
                )
            }
            Box(
                modifier = Modifier
                    .size(iconSize, iconSize * 0.5f)
                    .offset(y = travel)
                    .clipToBounds()
            ) {
                Image(
                    bitmap = icon,
                    contentDescription = null,
                    modifier = Modifier
                        .size(iconSize)
                        .align(Alignment.TopCenter)
                        .offset(y = -(iconSize * 0.5f))
                )
            }
        }
    }
}

private fun loadIconBitmap(context: Context, path: String): ImageBitmap {
    if (path.isNotBlank()) {
        decodeSampledBitmap(path, 512, 512)?.let { return it.asImageBitmap() }
    }
    val drawable = ContextCompat.getDrawable(context, R.drawable.ic_app_logo)
    val bitmap = drawable?.toBitmap(width = 512, height = 512)
    return bitmap?.asImageBitmap() ?: ImageBitmap(1, 1)
}
