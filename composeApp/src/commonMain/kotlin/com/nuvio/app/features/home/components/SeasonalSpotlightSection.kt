package com.nuvio.app.features.home.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.nuvio.app.core.ui.NuvioAsyncImage
import com.nuvio.app.core.ui.NuvioShelfSection
import com.nuvio.app.core.ui.RedesignColors
import com.nuvio.app.core.ui.RedesignIconButton
import com.nuvio.app.core.ui.RedesignPillButton
import com.nuvio.app.core.ui.RedesignTileShape
import com.nuvio.app.core.ui.redesignText
import com.nuvio.app.features.calendar.CalendarPlatform
import com.nuvio.app.features.home.HomeCatalogSection
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.home.SeasonalTheme
import com.nuvio.app.features.home.stableKey
import com.nuvio.app.features.watching.application.WatchingState
import com.nuvio.app.features.watchprogress.CurrentDateProvider
import kotlinx.coroutines.launch
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** Band sizes from the Home design's “Seasonal spotlight” frame (dp at a 1360 dp window). */
private val BandHeight = 322.dp
private val BandCopyInset = 26.dp
private val BandCopyWidth = 330.dp
private val BandCopyGap = 16.dp
private val BandCardGap = 11.dp
private val BandEdgeFade = 90.dp
/** Room before the first card so its hover ring and lift aren't clipped by the row. */
private val BandRingRoom = 4.dp
/** `landscapePosterWidth(158)` ≈ 258 dp: the frame's 258 × 145 cards. */
private const val BandCardBaseWidthDp = 158
private val BandInk = Color(0xFF070606)
private val BandArtworkAlignment = BiasAlignment(0f, -0.2f)

/**
 * The seasonal band in the board's language: an 18 dp tile with a hairline (as [HomeBoard]'s tiles),
 * the theme's artwork clipped to it, the copy on the left and the collection's 16:9 cards on the
 * right. Its own measured list item, inset like the board, so it never covers the rows around it.
 */
@Composable
internal fun SeasonalSpotlightSection(
    theme: SeasonalTheme,
    section: HomeCatalogSection,
    sectionPadding: Dp,
    watchedKeys: Set<String>,
    fullyWatchedSeriesKeys: Set<String>,
    onExplore: (() -> Unit)?,
    onPosterClick: ((MetaPreview) -> Unit)?,
    onPosterLongClick: ((MetaPreview) -> Unit)?,
    modifier: Modifier = Modifier,
    artworkFolder: com.nuvio.app.features.collection.CollectionFolder? = null,
) {
    val listState = remember(theme.id) { LazyListState() }
    val scope = rememberCoroutineScope()
    BoxWithConstraints(modifier.fillMaxWidth().padding(horizontal = sectionPadding)) {
        val wide = maxWidth >= 900.dp
        val tile = Modifier.fillMaxWidth().clip(RedesignTileShape).background(RedesignColors.fill)
            .border(1.dp, RedesignColors.hairline, RedesignTileShape).testTag("seasonal_band")
        Box(if (wide) tile.height(BandHeight) else tile.heightIn(min = BandHeight)) {
            SeasonalBandArtwork(theme, section, artworkFolder, Modifier.matchParentSize())
            val copy: @Composable (Modifier) -> Unit = { copyModifier ->
                SeasonalBandCopy(theme, section, onExplore, copyModifier)
            }
            val cards: @Composable (Modifier, PaddingValues) -> Unit = { rowModifier, padding ->
                SeasonalBandCards(
                    section = section,
                    listState = listState,
                    contentPadding = padding,
                    watchedKeys = watchedKeys,
                    fullyWatchedSeriesKeys = fullyWatchedSeriesKeys,
                    onPosterClick = onPosterClick,
                    onPosterLongClick = onPosterLongClick,
                    onNext = {
                        scope.launch {
                            listState.animateScrollToItem((listState.firstVisibleItemIndex + 2).coerceAtMost(section.items.lastIndex))
                        }
                    },
                    modifier = rowModifier,
                )
            }
            if (wide) {
                copy(Modifier.align(Alignment.CenterStart).padding(start = BandCopyInset).width(BandCopyWidth))
                cards(
                    Modifier.align(Alignment.CenterStart)
                        .padding(start = BandCopyInset + BandCopyWidth + BandCopyGap - BandRingRoom).fillMaxWidth(),
                    PaddingValues(start = BandRingRoom, end = BandCopyInset),
                )
            } else {
                Column(
                    Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    copy(Modifier.padding(horizontal = BandCopyInset))
                    cards(Modifier.fillMaxWidth(), PaddingValues(horizontal = BandCopyInset))
                }
            }
        }
    }
}

@Composable
private fun SeasonalBandArtwork(
    theme: SeasonalTheme,
    section: HomeCatalogSection,
    artworkFolder: com.nuvio.app.features.collection.CollectionFolder?,
    modifier: Modifier,
) {
    Box(modifier.background(theme.tint)) {
        val artwork = theme.backgroundArtwork.takeIf { artworkFolder?.heroBackdropUrl.isNullOrBlank() }
        if (artwork != null) {
            Image(
                painter = painterResource(artwork),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                alignment = BandArtworkAlignment,
            )
        } else {
            NuvioAsyncImage(
                model = artworkFolder?.heroBackdropUrl ?: section.items.firstOrNull { !it.banner.isNullOrBlank() }?.banner,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                alignment = BandArtworkAlignment,
            )
        }
        // Faded on the left for the copy, darker at the foot (the frame's two scrims).
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(
            0f to BandInk.copy(alpha = 0.92f),
            0.34f to BandInk.copy(alpha = 0.72f),
            0.62f to BandInk.copy(alpha = 0.25f),
            1f to BandInk.copy(alpha = 0.55f),
        )))
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(
            0.55f to Color.Transparent,
            1f to BandInk.copy(alpha = 0.7f),
        )))
    }
}

@Composable
private fun SeasonalBandCopy(
    theme: SeasonalTheme,
    section: HomeCatalogSection,
    onExplore: (() -> Unit)?,
    modifier: Modifier,
) {
    // Short, as the Calendar writes dates ("30 Nov").
    val until = remember(theme) {
        val end = theme.windowEndIsoDate(CurrentDateProvider.todayIsoDate())
        "${end.takeLast(2).toInt()} ${CalendarPlatform.shortMonthLabel(end)}"
    }
    Column(modifier) {
        Text(
            text = stringResource(Res.string.seasonal_spotlight_until, until).uppercase(),
            style = redesignText(11.sp, FontWeight.SemiBold, theme.accent, letterSpacing = 0.1.em),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.testTag("seasonal_eyebrow"),
        )
        Text(
            text = section.title,
            style = redesignText(34.sp, FontWeight.Bold, lineHeight = 36.sp, letterSpacing = (-0.025).em),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp, bottom = 8.dp),
        )
        if (section.subtitle.isNotBlank()) {
            Text(
                text = section.subtitle,
                style = redesignText(14.5.sp, color = Color(0xFFE3E5E8), lineHeight = 21.sp),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (onExplore != null) {
            Spacer(Modifier.height(20.dp))
            RedesignPillButton(
                label = stringResource(Res.string.seasonal_explore),
                onClick = onExplore,
                primary = true,
                trailing = Icons.AutoMirrored.Rounded.ArrowForward,
            )
        }
    }
}

@Composable
private fun SeasonalBandCards(
    section: HomeCatalogSection,
    listState: LazyListState,
    contentPadding: PaddingValues,
    watchedKeys: Set<String>,
    fullyWatchedSeriesKeys: Set<String>,
    onPosterClick: ((MetaPreview) -> Unit)?,
    onPosterLongClick: ((MetaPreview) -> Unit)?,
    onNext: () -> Unit,
    modifier: Modifier,
) {
    if (section.items.isEmpty()) {
        Box(modifier.height(145.dp).padding(contentPadding), contentAlignment = Alignment.Center) {
            if (section.hasMore) com.nuvio.app.core.ui.NuvioLoadingIndicator(Modifier.size(32.dp))
            else Text(
                stringResource(Res.string.seasonal_no_titles),
                style = redesignText(14.sp, color = RedesignColors.textSecondary),
            )
        }
        return
    }
    Box(modifier) {
        NuvioShelfSection(
            title = "",
            entries = section.items,
            state = listState,
            rowContentPadding = contentPadding,
            itemSpacing = BandCardGap,
            rowModifier = Modifier.fillMaxWidth(),
            key = MetaPreview::stableKey,
        ) { movie ->
            HomePosterCard(
                item = movie,
                useLandscapeBackdropMode = true,
                isWatched = WatchingState.isPosterWatched(watchedKeys, movie, fullyWatchedSeriesKeys),
                onClick = onPosterClick?.let { { it(movie) } },
                onLongClick = onPosterLongClick?.let { { it(movie) } },
                showLandscapeOverlay = true,
                allowPortraitFallback = false,
                alwaysShowLandscapeTitle = true,
                showTitleBelow = false,
                basePosterWidthDp = BandCardBaseWidthDp,
                quickLookRow = section.items,
            )
        }
        // A soft fade over the cards at the tile's edge; it draws only and takes no input.
        Box(
            Modifier.matchParentSize().wrapContentWidth(Alignment.End).width(BandEdgeFade)
                .background(Brush.horizontalGradient(listOf(Color.Transparent, BandInk.copy(alpha = 0.85f)))),
        )
        AnimatedVisibility(
            visible = listState.canScrollForward,
            enter = fadeIn(tween(180)),
            exit = fadeOut(tween(180)),
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 14.dp),
        ) {
            RedesignIconButton(
                contentDescription = stringResource(Res.string.seasonal_next_movies),
                onClick = onNext,
                vector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                size = 40.dp,
            )
        }
    }
}
