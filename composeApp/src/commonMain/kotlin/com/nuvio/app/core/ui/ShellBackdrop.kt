package com.nuvio.app.core.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Which artwork the ambient backdrop shows. Each screen sets [base] to one stable title
 * (Search: the top result). It deliberately does not follow poster hover; that felt dizzying.
 * Null falls back to the theme mesh.
 */
@Stable
class BackdropFocusController {
    var base by mutableStateOf<String?>(null)

    val target: String? get() = base
}

/** Present only on screens that draw [ShellBackdrop]. */
val LocalBackdropFocusController = staticCompositionLocalOf<BackdropFocusController?> { null }

private const val BackdropFocusDebounceMs = 250L

private val artworkScrim = Brush.verticalGradient(
    0f to Color.Black.copy(alpha = 0.40f),
    0.5f to Color.Black.copy(alpha = 0.55f),
    1f to Color.Black.copy(alpha = 0.74f),
)

/**
 * Ambient screen background: a theme-tinted mesh, with a blurred copy of the focused title's
 * artwork crossfaded on top when there is one.
 */
@OptIn(FlowPreview::class)
@Composable
fun ShellBackdrop(
    controller: BackdropFocusController,
    modifier: Modifier = Modifier,
) {
    val amoled = MaterialTheme.colorScheme.background == Color.Black
    var settledUrl by remember { mutableStateOf(controller.target) }
    LaunchedEffect(controller) {
        snapshotFlow { controller.target }
            .debounce(BackdropFocusDebounceMs)
            .distinctUntilChanged()
            .collect { settledUrl = it }
    }

    val loaded = rememberArtworkBackdrop(settledUrl)
    // Hold the previous artwork while the next one loads, so focus changes crossfade art→art.
    var shown by remember { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(settledUrl, loaded) {
        if (settledUrl == null) shown = null else if (loaded != null) shown = loaded
    }

    Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        ProfileMeshBackground(
            profileColor = MaterialTheme.colorScheme.primary,
            modifier = Modifier.graphicsLayer { alpha = if (amoled) 0.55f else 1f },
        )
        Crossfade(
            targetState = shown,
            animationSpec = tween(520, easing = FastOutSlowInEasing),
            label = "shell_backdrop_artwork",
        ) { bitmap ->
            if (bitmap != null) {
                Box(Modifier.fillMaxSize().graphicsLayer { alpha = if (amoled) 0.7f else 1f }) {
                    Image(
                        bitmap = bitmap,
                        contentDescription = null,
                        alignment = Alignment.TopCenter,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                    Box(Modifier.fillMaxSize().background(artworkScrim))
                }
            }
        }
    }
}

/** Translucent surface for sections that sit on the ambient backdrop. */
object BackdropSurface {
    val card = Color.White.copy(alpha = 0.045f)
    /** Nearly opaque so scrolled content never reads through the search bar. */
    val stickyHeader = Color(0xFF0E0F12).copy(alpha = 0.94f)
}

fun Modifier.backdropCard(shape: Shape): Modifier = background(BackdropSurface.card, shape)

/**
 * One slice of a [BackdropSurface.card] spanning several LazyColumn items. Middle slices bleed
 * half of [itemGap] up and down so consecutive items read as one continuous card.
 */
fun Modifier.backdropCardSegment(
    isFirst: Boolean,
    isLast: Boolean,
    itemGap: androidx.compose.ui.unit.Dp,
    cornerRadius: androidx.compose.ui.unit.Dp = 16.dp,
): Modifier = drawBehind {
    val bleed = itemGap.toPx() / 2f
    val top = if (isFirst) 0f else -bleed
    val bottom = if (isLast) size.height else size.height + bleed
    val r = CornerRadius(cornerRadius.toPx())
    val path = Path().apply {
        addRoundRect(
            RoundRect(
                left = 0f, top = top, right = size.width, bottom = bottom,
                topLeftCornerRadius = if (isFirst) r else CornerRadius.Zero,
                topRightCornerRadius = if (isFirst) r else CornerRadius.Zero,
                bottomLeftCornerRadius = if (isLast) r else CornerRadius.Zero,
                bottomRightCornerRadius = if (isLast) r else CornerRadius.Zero,
            ),
        )
    }
    drawPath(path, BackdropSurface.card)
}
