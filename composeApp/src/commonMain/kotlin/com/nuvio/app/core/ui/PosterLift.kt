package com.nuvio.app.core.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.RememberObserver
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.GraphicsContext
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.platform.LocalGraphicsContext
import androidx.compose.ui.unit.Dp
import com.nuvio.app.controller.controllerFocusable
import com.nuvio.app.isDesktop

internal val LocalPosterClickAnchor = staticCompositionLocalOf<((PosterZoomAnchor) -> Unit)?> { null }

/**
 * Press-and-hold on a poster, when set: Home's hover wrapper sets it to open Quick look, and the
 * poster's long-click action (its menu) then stays on right-click only.
 */
internal val LocalPosterPressAndHold = staticCompositionLocalOf<(() -> Unit)?> { null }

private var nextPosterCardFocusId = 1L

internal class PosterLiftSource(private val graphicsContext: GraphicsContext) : RememberObserver {
    val layer = graphicsContext.createGraphicsLayer()
    private var coordinates: LayoutCoordinates? = null
    private var lastBounds: Rect? = null

    /** On-screen bounds in root, including the card's hover scale. */
    val bounds: Rect?
        get() = coordinates?.takeIf { it.isAttached }?.let { c ->
            Rect(
                c.localToRoot(Offset.Zero),
                c.localToRoot(Offset(c.size.width.toFloat(), c.size.height.toFloat())),
            )
        } ?: lastBounds

    var isLifted by mutableStateOf(false)
        private set

    /**
     * The overlay covers a lifted card, so the card receives a hover exit even when the pointer stays
     * on it. While non-null this replaces the card's hover state: the overlay sets it from the pointer
     * position, and the next hover enter after landing returns control to real hover events.
     */
    var hoverHold by mutableStateOf<Boolean?>(null)

    fun updatePosition(coordinates: LayoutCoordinates) {
        this.coordinates = coordinates
        lastBounds = bounds
    }
    private var rememberCount = 0

    val canLift: Boolean
        get() = rememberCount > 0 && !layer.isReleased && layer.size.width > 0 && layer.size.height > 0

    fun lift(): Boolean {
        if (!canLift) return false
        hoverHold = true
        isLifted = true
        return true
    }

    fun land() {
        isLifted = false
        if (rememberCount == 0) release()
    }

    override fun onRemembered() {
        rememberCount++
    }

    override fun onForgotten() {
        rememberCount--
        if (rememberCount == 0 && !isLifted) release()
    }

    override fun onAbandoned() {
        if (rememberCount == 0 && !isLifted) release()
    }

    private fun release() {
        coordinates = null
        if (!layer.isReleased) graphicsContext.releaseGraphicsLayer(layer)
    }
}

/** A lift source owned by the card when its hover scale sits outside [posterCardClickable]. */
@Composable
internal fun rememberPosterLiftSource(key: Any?): PosterLiftSource {
    val graphicsContext = LocalGraphicsContext.current
    return remember(graphicsContext, key) { PosterLiftSource(graphicsContext) }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun Modifier.posterCardClickable(
    onClick: (() -> Unit)?,
    onLongClick: (() -> Unit)?,
    zoomImageUrl: String? = null,
    zoomCornerRadius: Dp = NuvioTokens.Radius.poster,
    hoverScaleEnabled: Boolean = true,
    liftSource: PosterLiftSource? = null,
    focusId: String? = null,
): Modifier {
    val source = liftSource ?: rememberPosterLiftSource(zoomImageUrl)
    val onPosterClickAnchor = LocalPosterClickAnchor.current
    val pressAndHold = LocalPosterPressAndHold.current
    val controllerFocusId = remember(focusId, zoomImageUrl) {
        focusId ?: zoomImageUrl?.takeIf { it.isNotBlank() }?.let { "poster_${it.hashCode()}" }
            ?: "poster_card_${nextPosterCardFocusId++}"
    }
    val posterModifier = Modifier
        .drawWithContent {
            source.layer.record { this@drawWithContent.drawContent() }
            if (!source.isLifted) drawLayer(source.layer)
        }
        .onGloballyPositioned { coordinates -> source.updatePosition(coordinates) }
        .then(this)
    if (onClick == null && onLongClick == null) return posterModifier
    val interactionSource = remember { MutableInteractionSource() }
    val handleClick: () -> Unit = {
        if (onClick != null) {
            source.bounds?.let { bounds ->
                onPosterClickAnchor?.invoke(
                    PosterZoomAnchor(bounds, zoomImageUrl, zoomCornerRadius).also { it.source = source },
                )
            }
            onClick()
        }
    }
    val handleLongClick = onLongClick?.let { longClick ->
        {
            source.bounds?.let { cardBounds ->
                PosterZoomAnchorHolder.stash(
                    PosterZoomAnchor(cardBounds, zoomImageUrl, zoomCornerRadius).also {
                        it.source = source
                    },
                )
            }
            longClick()
        }
    }
    return Modifier
        .desktopPosterHoverScale(
            enabled = hoverScaleEnabled,
            interactionSource = interactionSource,
            liftSource = source,
        )
        .then(posterModifier)
        .then(
            if (isDesktop) {
                Modifier.controllerFocusable(
                    id = controllerFocusId,
                    enabled = onClick != null,
                    cornerRadius = zoomCornerRadius,
                    focusScale = 1.07f,
                    selectLabel = "Open",
                    secondaryActionLabel = if (handleLongClick != null) "Options" else null,
                    onSecondaryAction = handleLongClick,
                    onSelect = handleClick,
                )
            } else {
                Modifier
            },
        )
        .combinedClickable(
            interactionSource = interactionSource,
            indication = if (!isDesktop && onPosterClickAnchor == null) LocalIndication.current else null,
            onClick = handleClick,
            onLongClick = pressAndHold ?: handleLongClick,
        )
        .secondaryClick(handleLongClick)
}

internal fun DrawScope.drawLiftedPoster(source: PosterLiftSource) {
    val layer = source.layer
    if (layer.isReleased || layer.size.width <= 0 || layer.size.height <= 0) return
    scale(
        scaleX = size.width / layer.size.width,
        scaleY = size.height / layer.size.height,
        pivot = Offset.Zero,
    ) {
        drawLayer(layer)
    }
}
