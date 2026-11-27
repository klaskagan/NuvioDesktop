package com.nuvio.app.core.ui

import androidx.compose.foundation.layout.BoxScope

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.HoverInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.stopScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight as ShelfFontWeight
import androidx.compose.ui.zIndex
import com.nuvio.app.navigation.excludeFromHistoryGestures
import com.nuvio.app.isDesktop
import coil3.request.crossfade
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.home_view_all
import nuvio.composeapp.generated.resources.poster_logo_content_description
import org.jetbrains.compose.resources.stringResource
import kotlin.math.abs
import kotlin.math.roundToInt

enum class NuvioPosterShape {
    Poster,
    Square,
    Landscape,
}

enum class NuvioViewAllPillSize {
    Default,
    Compact,
}

/** Stable per-row group key so spatial Up/Down can jump between shelves predictably. */
private var nextShelfGroupId = 1L

@Composable
fun <T> NuvioShelfSection(
    title: String,
    entries: List<T>,
    modifier: Modifier = Modifier,
    rowModifier: Modifier = Modifier,
    headerHorizontalPadding: Dp = 0.dp,
    rowContentPadding: PaddingValues = PaddingValues(0.dp),
    itemSpacing: Dp = 10.dp,
    onViewAllClick: (() -> Unit)? = null,
    onTitleClick: (() -> Unit)? = null,
    viewAllPillSize: NuvioViewAllPillSize = NuvioViewAllPillSize.Default,
    eyebrow: String? = null,
    key: ((T) -> Any)? = null,
    animatePlacement: Boolean = false,
    state: LazyListState = rememberLazyListState(),
    trailingContent: (@Composable () -> Unit)? = null,
    headerModifier: Modifier = Modifier,
    headerLeading: (@Composable () -> Unit)? = null,
    headerActions: (@Composable RowScope.() -> Unit)? = null,
    showViewAll: Boolean = true,
    /** Drawn over the row's items, same size (Home's reorder uses it as a drag surface). */
    rowOverlay: (@Composable BoxScope.() -> Unit)? = null,
    itemContent: @Composable (T) -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    val duplicateSafeEntries = remember(entries, key) {
        key?.let { entries.withDuplicateSafeLazyKeys(it) }
    }
    val shelfGroupKey = remember { "shelf_${nextShelfGroupId++}" }

    ScreenActivityEffect(state) { active ->
        if (!active) state.stopScroll()
    }
    com.nuvio.app.controller.ProvideControllerRowLazyListState(state) {
        androidx.compose.runtime.CompositionLocalProvider(
            com.nuvio.app.controller.LocalControllerGroupKey provides shelfGroupKey,
        ) {
            Column(
                modifier = modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(tokens.spacing.controlGap + NuvioTokens.Space.s2),
            ) {
                if (title.isNotBlank()) {
                    NuvioShelfSectionHeader(
                        title = title,
                        modifier = headerModifier.padding(horizontal = headerHorizontalPadding),
                        onViewAllClick = onViewAllClick,
                        onTitleClick = onTitleClick,
                        viewAllPillSize = viewAllPillSize,
                        eyebrow = eyebrow,
                        leading = headerLeading,
                        actions = headerActions,
                        showViewAll = showViewAll,
                    )
                }
                Box {
                    LazyRow(
                        state = state,
                        modifier = rowModifier
                            .nuvioHorizontalScrollBleed(LocalVerticalBarPadding.current)
                            .nuvioDesktopDragScroll(state),
                        contentPadding = rowContentPadding.withVerticalBar(),
                        horizontalArrangement = Arrangement.spacedBy(itemSpacing),
                    ) {
                        if (duplicateSafeEntries != null) {
                            itemsIndexed(
                                items = duplicateSafeEntries,
                                key = { _, entry -> entry.lazyKey },
                                contentType = { _, _ -> "poster" },
                            ) { _, keyedEntry ->
                                if (animatePlacement) {
                                    Box(modifier = Modifier.animateItem()) { itemContent(keyedEntry.value) }
                                } else {
                                    itemContent(keyedEntry.value)
                                }
                            }
                        } else {
                            itemsIndexed(
                                items = entries,
                                contentType = { _, _ -> "poster" },
                            ) { _, entry ->
                                if (animatePlacement) {
                                    Box(modifier = Modifier.animateItem()) { itemContent(entry) }
                                } else {
                                    itemContent(entry)
                                }
                            }
                        }
                        if (trailingContent != null) {
                            item(contentType = "shelf-action") { trailingContent() }
                        }
                    }
                    rowOverlay?.let { overlay -> Box(Modifier.matchParentSize()) { overlay() } }
                }
            }
        }
    }
}

internal fun Modifier.nuvioDesktopDragScroll(
    state: LazyListState,
): Modifier {
    if (!isDesktop) return this

    return excludeFromHistoryGestures(state).pointerInput(state) {
        awaitEachGesture {
            val down = awaitFirstDown(pass = PointerEventPass.Initial)
            var totalDx = 0f
            var totalDy = 0f
            var dragging = false

            while (true) {
                val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) break

                val delta = change.position - change.previousPosition
                totalDx += delta.x
                totalDy += delta.y

                if (!dragging) {
                    val horizontalDrag =
                        abs(totalDx) > viewConfiguration.touchSlop && abs(totalDx) > abs(totalDy)
                    val verticalDrag =
                        abs(totalDy) > viewConfiguration.touchSlop && abs(totalDy) > abs(totalDx)

                    when {
                        verticalDrag -> break
                        horizontalDrag -> dragging = true
                        else -> continue
                    }
                }

                state.dispatchRawDelta(-delta.x)
                change.consume()
            }
        }
    }
}

internal fun Modifier.nuvioDesktopDragScroll(
    state: ScrollState,
): Modifier {
    if (!isDesktop) return this

    return pointerInput(state) {
        awaitEachGesture {
            val down = awaitFirstDown(pass = PointerEventPass.Initial)
            var totalDx = 0f
            var totalDy = 0f
            var dragging = false

            while (true) {
                val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) break

                val delta = change.position - change.previousPosition
                totalDx += delta.x
                totalDy += delta.y

                if (!dragging) {
                    val horizontalDrag =
                        abs(totalDx) > viewConfiguration.touchSlop && abs(totalDx) > abs(totalDy)
                    val verticalDrag =
                        abs(totalDy) > viewConfiguration.touchSlop && abs(totalDy) > abs(totalDx)

                    when {
                        verticalDrag -> break
                        horizontalDrag -> dragging = true
                        else -> continue
                    }
                }

                state.dispatchRawDelta(-delta.x)
                change.consume()
            }
        }
    }
}

@Composable
fun NuvioPosterCard(
    title: String,
    imageUrl: String?,
    modifier: Modifier = Modifier,
    basePosterWidthDp: Int? = null,
    fallbackImageUrl: String? = null,
    shape: NuvioPosterShape = NuvioPosterShape.Poster,
    detailLine: String? = null,
    secondaryDetailLine: String? = null,
    artworkBadge: String? = null,
    requiredLabels: Boolean = false,
    showTitleBelow: Boolean = true,
    bottomLeftLogoUrl: String? = null,
    bottomLeftText: String? = null,
    logoTextFallback: Boolean = false,
    artworkPending: Boolean = false,
    fillWidth: Boolean = false,
    onImageError: (() -> Unit)? = null,
    isWatched: Boolean = false,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    placeholderTitle: String = title,
    artworkHeight: Dp? = null,
    artworkCornerRadius: Dp? = null,
    artworkFocusRing: Dp? = null,
    circularArtwork: Boolean = false,
    bottomLeftDetail: String? = null,
    /** Drawn over the artwork, last (Home's hover ring and action strip); gets the artwork's shape. */
    artworkOverlay: (@Composable androidx.compose.foundation.layout.BoxScope.(androidx.compose.ui.graphics.Shape) -> Unit)? = null,
) {
    val posterCardStyle = rememberPosterCardStyleUiState()
    val tokens = MaterialTheme.nuvio
    val effectiveBasePosterWidthDp = basePosterWidthDp ?: posterCardStyle.widthDp
    val cardWidth = shape.cardWidth(basePosterWidthDp = effectiveBasePosterWidthDp)
    val cornerRadius = artworkCornerRadius ?: posterCardStyle.cornerRadiusDp.dp
    val cardShape = if (circularArtwork) CircleShape else RoundedCornerShape(cornerRadius)
    var focused by remember { mutableStateOf(false) }
    val focusColor = tokens.colors.accent
    val catalogLogoOverlaySize = catalogLogoOverlaySize(
        basePosterWidthDp = effectiveBasePosterWidthDp,
        shape = shape,
    )
    val shouldShowTitleBelow = showTitleBelow && (requiredLabels || !posterCardStyle.hideLabelsEnabled)
    val platformContext = coil3.compose.LocalPlatformContext.current
    val imageModel = remember(imageUrl, fallbackImageUrl, platformContext) {
        if (imageUrl == null) null
        else if (!fallbackImageUrl.isNullOrBlank() && fallbackImageUrl != imageUrl) coil3.request.ImageRequest.Builder(platformContext)
            .data(imageUrl).memoryCacheKeyExtras(mapOf(com.nuvio.app.core.poster.CustomPosterFallbackInterceptor.FALLBACK_URL_KEY to fallbackImageUrl)).build()
        else imageUrl
    }
    var logoFailed by remember(title, imageUrl, bottomLeftLogoUrl) { mutableStateOf(false) }

    // The column scales on hover; share the lift source so a lifted card keeps its hover state.
    val liftSource = rememberPosterLiftSource(imageUrl)
    Column(
        modifier = Modifier
            .desktopPosterHoverScale(enabled = artworkFocusRing == null, liftSource = liftSource)
            .then(modifier)
            .then(if (fillWidth) Modifier.fillMaxWidth() else Modifier.width(cardWidth)),
        verticalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s6),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (artworkHeight == null) Modifier.aspectRatio(shape.aspectRatio) else Modifier.height(artworkHeight))
                .then(if (artworkFocusRing == null) Modifier else Modifier
                    .onFocusChanged { focused = it.isFocused }
                    .drawWithCache {
                        val halfRing = artworkFocusRing.toPx() / 2f
                        val radius = CornerRadius(cornerRadius.toPx() + halfRing)
                        onDrawWithContent {
                            drawContent()
                            if (focused) drawRoundRect(
                                color = focusColor,
                                topLeft = Offset(-halfRing, -halfRing),
                                size = Size(size.width + 2 * halfRing, size.height + 2 * halfRing),
                                cornerRadius = radius,
                                style = Stroke(2 * halfRing),
                            )
                        }
                    })
                .clip(cardShape)
                .background(tokens.colors.surface)
                .nuvioCardDepth(
                    shape = cardShape,
                    surface = NuvioCardDepthSurface.Posters,
                )
                .posterCardClickable(
                    onClick = onClick,
                    onLongClick = onLongClick,
                    zoomImageUrl = imageUrl,
                    zoomCornerRadius = if (circularArtwork) cardWidth / 2 else cornerRadius,
                    hoverScaleEnabled = false,
                    liftSource = liftSource,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier.matchParentSize(),
                contentAlignment = Alignment.Center,
            ) {
                if (imageUrl != null) {
                    NuvioAsyncImage(
                        model = imageModel,
                        contentDescription = title,
                        modifier = Modifier.matchParentSize(),
                        onError = { onImageError?.invoke() },
                        contentScale = ContentScale.Crop,
                        desktopAnimation = posterCardStyle.animatedArtworkMode.toDesktopAnimation(),
                    )
                } else if (!artworkPending && !circularArtwork && bottomLeftDetail == null) {
                    Text(
                        text = placeholderTitle,
                        modifier = Modifier.padding(horizontal = NuvioTokens.Space.s14),
                        style = MaterialTheme.typography.titleMedium,
                        color = tokens.colors.textMuted,
                        textAlign = TextAlign.Center,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                if (!bottomLeftLogoUrl.isNullOrBlank() || !bottomLeftText.isNullOrBlank()) {
                    // Gradient scrim for readability — matching NuvioTV Modern Home style
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .drawWithCache {
                                val gradient = Brush.verticalGradient(
                                    colorStops = arrayOf(
                                        0.0f to Color.Transparent,
                                        (if (bottomLeftDetail == null) 0.58f else 0.12f) to Color.Transparent,
                                        1.0f to Color.Black.copy(alpha = if (bottomLeftDetail == null) 0.75f else 0.94f)
                                    )
                                )
                                onDrawBehind { drawRect(gradient) }
                            }
                    )
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(horizontal = NuvioTokens.Space.s10, vertical = NuvioTokens.Space.s10),
                    ) {
                        if (!bottomLeftLogoUrl.isNullOrBlank()) {
                            NuvioAsyncImage(
                                model = bottomLeftLogoUrl,
                                contentDescription = stringResource(Res.string.poster_logo_content_description, title),
                                modifier = Modifier
                                    .width(catalogLogoOverlaySize.width)
                                    .height(catalogLogoOverlaySize.height),
                                contentScale = ContentScale.Fit,
                                alignment = Alignment.CenterStart,
                                onSuccess = { logoFailed = false },
                                onError = { logoFailed = true },
                            )
                        }
                        if (bottomLeftLogoUrl.isNullOrBlank() || (logoTextFallback && logoFailed)) {
                            Column(Modifier.align(Alignment.BottomStart)) {
                                Text(
                                    text = bottomLeftText.orEmpty(),
                                    style = if (logoTextFallback) MaterialTheme.typography.titleSmall else MaterialTheme.typography.labelMedium,
                                    color = tokens.colors.textPrimary,
                                    maxLines = if (logoTextFallback) 2 else 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = if (bottomLeftDetail == null) Modifier.widthIn(max = catalogLogoOverlaySize.textMaxWidth) else Modifier,
                                )
                                if (!bottomLeftDetail.isNullOrBlank()) {
                                    Text(
                                        text = bottomLeftDetail,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = Color.White.copy(alpha = 0.88f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }

                if (!artworkBadge.isNullOrBlank()) {
                    Text(
                        text = artworkBadge,
                        modifier = Modifier.align(Alignment.BottomStart).padding(8.dp)
                            .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = .78f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 7.dp, vertical = 4.dp),
                        color = androidx.compose.ui.graphics.Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 2,
                    )
                }
                NuvioPosterWatchedOverlay(isWatched = isWatched)
                artworkOverlay?.invoke(this, cardShape)
            }
        }
        if (shouldShowTitleBelow) {
            val labelVisibility = Modifier
            Text(
                text = title,
                modifier = if (circularArtwork) labelVisibility.fillMaxWidth() else labelVisibility,
                textAlign = if (circularArtwork) TextAlign.Center else TextAlign.Start,
                style = MaterialTheme.typography.bodyMedium,
                color = tokens.colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!detailLine.isNullOrBlank()) {
                Text(
                    text = detailLine,
                    modifier = labelVisibility,
                    style = MaterialTheme.typography.labelSmall,
                    color = tokens.colors.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            } else {
                Box(modifier = Modifier.height(NuvioTokens.Space.none))
            }
            if (!secondaryDetailLine.isNullOrBlank()) Text(
                text = secondaryDetailLine, modifier = labelVisibility,
                style = MaterialTheme.typography.labelSmall, color = tokens.colors.textMuted,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        } else {
            Box(modifier = Modifier.height(NuvioTokens.Space.none))
        }
    }
}

/** Shelf header look. Editorial (desktop Home redesign) sets a small label above a larger title. */
enum class NuvioShelfHeaderStyle { Standard, Editorial }

val LocalNuvioShelfHeaderStyle = androidx.compose.runtime.compositionLocalOf { NuvioShelfHeaderStyle.Standard }

@Composable
internal fun NuvioShelfSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    onViewAllClick: (() -> Unit)? = null,
    onTitleClick: (() -> Unit)? = null,
    viewAllPillSize: NuvioViewAllPillSize = NuvioViewAllPillSize.Default,
    eyebrow: String? = null,
    leading: (@Composable () -> Unit)? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
    showViewAll: Boolean = true,
) {
    val tokens = MaterialTheme.nuvio
    val editorial = LocalNuvioShelfHeaderStyle.current == NuvioShelfHeaderStyle.Editorial
    Column(
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(tokens.spacing.controlGap),
            verticalAlignment = if (editorial) Alignment.Bottom else Alignment.CenterVertically,
        ) {
            leading?.invoke()
            Column(modifier = Modifier.weight(1f)) {
            if (editorial && !eyebrow.isNullOrBlank()) {
                Text(
                    text = eyebrow.uppercase(),
                    style = redesignText(11.sp, ShelfFontWeight.Medium, RedesignColors.textMuted, letterSpacing = 0.1.em),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(bottom = 2.dp),
                )
            }
            Text(
                text = title,
                modifier = Modifier
                    .then(if (onTitleClick != null) {
                        Modifier.nuvioFocusBorder(RoundedCornerShape(NuvioTokens.Radius.md))
                            .clickable(onClick = onTitleClick)
                    } else Modifier),
                style = if (editorial) {
                    redesignText(24.sp, ShelfFontWeight.Bold, RedesignColors.textPrimary, letterSpacing = RedesignTightTracking)
                } else {
                    MaterialTheme.typography.titleLarge
                },
                color = if (editorial) RedesignColors.textPrimary else tokens.colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            }
            // Row actions (Home's ⋯, or ↑ ↓ Done while reordering) sit just before the ›.
            if (actions != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) { actions() }
            }
            if (showViewAll && (actions == null || onViewAllClick != null)) {
                val viewAllPlaceholderModifier = if (onViewAllClick == null) {
                    Modifier
                        .alpha(0f)
                        .clearAndSetSemantics { }
                } else {
                    Modifier
                }
                NuvioViewAllPill(
                    onClick = onViewAllClick,
                    size = viewAllPillSize,
                    modifier = viewAllPlaceholderModifier,
                )
            }
        }
    }
}

@Composable
private fun NuvioViewAllPill(
    onClick: (() -> Unit)?,
    size: NuvioViewAllPillSize,
    modifier: Modifier = Modifier,
) {
    val tokens = MaterialTheme.nuvio
    val actionSize = if (size == NuvioViewAllPillSize.Compact) NuvioTokens.Space.s32 else NuvioTokens.Space.s40
    val iconSize = if (size == NuvioViewAllPillSize.Compact) NuvioTokens.Icon.sm else tokens.icons.md
    val viewAllText = stringResource(Res.string.home_view_all)

    val pillShape = RoundedCornerShape(NuvioTokens.Radius.xl)
    Box(
        modifier = modifier
            .size(actionSize)
            .background(color = tokens.colors.surface, shape = pillShape)
            .then(if (onClick != null) {
                Modifier.nuvioFocusBorder(pillShape).clickable(onClick = onClick)
            } else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = viewAllText,
            tint = tokens.colors.textMuted,
            modifier = Modifier.size(iconSize),
        )
    }
}

internal const val NuvioDesktopCatalogShelfPosterScale = 1.4f
private const val DesktopPosterHoverScale = 1.045f

/**
 * True while hover must leave posters alone, such as while a Home row is picked up to be moved
 * (Home design "Row · reorder"): no zoom, ring, strip, Quick look or inline expand.
 */
internal val LocalPosterHoverSuspended = androidx.compose.runtime.compositionLocalOf { false }

internal fun desktopCatalogShelfPosterBaseWidthDp(
    basePosterWidthDp: Int,
): Int =
    if (isDesktop) {
        (basePosterWidthDp * NuvioDesktopCatalogShelfPosterScale).roundToInt()
    } else {
        basePosterWidthDp
    }

internal fun Modifier.nuvioShelfHoverOverdraw(inset: Dp): Modifier {
    if (inset == 0.dp) return this

    // Expand the measured viewport, then place it negatively so edge items keep
    // their visual alignment while desktop hover scale can draw into the gutter.
    return layout { measurable, constraints ->
        val insetPx = inset.roundToPx()
        val horizontalInset = insetPx * 2
        val verticalInset = insetPx * 2
        val expandedMaxWidth = if (constraints.hasBoundedWidth) {
            constraints.maxWidth + horizontalInset
        } else {
            constraints.maxWidth
        }
        val expandedMinWidth = if (constraints.hasBoundedWidth) {
            (constraints.minWidth + horizontalInset).coerceAtMost(expandedMaxWidth)
        } else {
            constraints.minWidth
        }
        val expandedMaxHeight = if (constraints.hasBoundedHeight) {
            constraints.maxHeight + verticalInset
        } else {
            constraints.maxHeight
        }
        val expandedConstraints = constraints.copy(
            minWidth = expandedMinWidth,
            maxWidth = expandedMaxWidth,
            minHeight = 0,
            maxHeight = expandedMaxHeight,
        )
        val placeable = measurable.measure(expandedConstraints)
        val width = (placeable.width - horizontalInset)
            .coerceIn(constraints.minWidth, constraints.maxWidth)
        val height = (placeable.height - verticalInset)
            .coerceIn(constraints.minHeight, constraints.maxHeight)

        layout(width, height) {
            placeable.placeRelative(-insetPx, -insetPx)
        }
    }
}

private val NuvioPosterShape.aspectRatio: Float
    get() = when (this) {
        NuvioPosterShape.Poster -> 0.675f
        NuvioPosterShape.Square -> 1f
        NuvioPosterShape.Landscape -> PosterLandscapeAspectRatio
    }

private data class CatalogLogoOverlaySize(
    val width: Dp,
    val height: Dp,
    val textMaxWidth: Dp,
)

private fun catalogLogoOverlaySize(
    basePosterWidthDp: Int,
    shape: NuvioPosterShape,
): CatalogLogoOverlaySize =
    if (shape == NuvioPosterShape.Landscape) {
        when {
            basePosterWidthDp <= 108 -> CatalogLogoOverlaySize(width = 92.dp, height = 24.dp, textMaxWidth = 120.dp)
            basePosterWidthDp <= 120 -> CatalogLogoOverlaySize(width = 104.dp, height = 28.dp, textMaxWidth = 132.dp)
            basePosterWidthDp <= 132 -> CatalogLogoOverlaySize(width = 116.dp, height = 30.dp, textMaxWidth = 144.dp)
            else -> CatalogLogoOverlaySize(width = 128.dp, height = 34.dp, textMaxWidth = 156.dp)
        }
    } else {
        when {
            basePosterWidthDp <= 108 -> CatalogLogoOverlaySize(width = 72.dp, height = 18.dp, textMaxWidth = 92.dp)
            basePosterWidthDp <= 120 -> CatalogLogoOverlaySize(width = 80.dp, height = 20.dp, textMaxWidth = 104.dp)
            basePosterWidthDp <= 132 -> CatalogLogoOverlaySize(width = 88.dp, height = 22.dp, textMaxWidth = 112.dp)
            else -> CatalogLogoOverlaySize(width = 96.dp, height = 24.dp, textMaxWidth = 124.dp)
        }
    }

private fun NuvioPosterShape.cardWidth(basePosterWidthDp: Int): Dp =
    when (this) {
        NuvioPosterShape.Poster -> basePosterWidthDp.dp
        NuvioPosterShape.Square -> basePosterWidthDp.dp
        NuvioPosterShape.Landscape -> landscapePosterWidth(basePosterWidthDp)
    }
@Composable
internal fun Modifier.desktopPosterHoverScale(
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    liftSource: PosterLiftSource? = null,
): Modifier {
    if (!enabled || !isDesktop || LocalPosterHoverSuspended.current) return this

    val hovered by interactionSource.collectIsHoveredAsState()
    if (liftSource != null) {
        LaunchedEffect(interactionSource, liftSource) {
            interactionSource.interactions.collect { interaction ->
                if (interaction is HoverInteraction.Enter && !liftSource.isLifted) liftSource.hoverHold = null
            }
        }
    }
    val scale by animateFloatAsState(
        targetValue = if (liftSource?.hoverHold ?: hovered) DesktopPosterHoverScale else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "desktop_poster_hover_scale",
    )

    val isScaling = (liftSource?.hoverHold ?: hovered) || scale != 1f

    return this
        .then(
            if (isScaling) {
                Modifier
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    }
                    .zIndex(1f)
            } else {
                Modifier
            },
        )
        .hoverable(interactionSource)
}
