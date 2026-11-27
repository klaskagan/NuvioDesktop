package com.nuvio.app.features.home.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.nuvio.app.core.format.formatLocalTime
import com.nuvio.app.core.i18n.localizedEpisodeTitle
import com.nuvio.app.core.ui.NuvioAsyncImage
import com.nuvio.app.core.ui.PosterZoomAnchor
import com.nuvio.app.core.ui.PosterZoomAnchorHolder
import com.nuvio.app.core.ui.RedesignColors
import com.nuvio.app.core.ui.posterCardClickable
import com.nuvio.app.core.ui.redesignText
import com.nuvio.app.core.ui.rememberAnimatedArtworkAnimation
import com.nuvio.app.core.ui.rememberPosterLiftSource
import com.nuvio.app.features.cloud.cloudLibraryDisplayArtworkUrl
import com.nuvio.app.features.watchprogress.ContinueWatchingItem
import com.nuvio.app.features.watchprogress.CurrentDateProvider
import com.nuvio.app.features.watchprogress.WatchProgressClock
import com.nuvio.app.features.watchprogress.computeAirDateBadgeText
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.cw_continue_watching_more
import nuvio.composeapp.generated.resources.cw_continue_watching_resume
import nuvio.composeapp.generated.resources.cw_ends_at
import nuvio.composeapp.generated.resources.home_continue_watching_hours_minutes_left
import nuvio.composeapp.generated.resources.home_continue_watching_minutes_left
import nuvio.composeapp.generated.resources.home_continue_watching_up_next
import nuvio.composeapp.generated.resources.home_continue_watching_watched
import nuvio.composeapp.generated.resources.media_movie
import nuvio.composeapp.generated.resources.streams_episode_badge
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

// Desktop Continue Watching cards (Home design, "Continue Watching cards"). Sizes are dp at the
// design's 1360 dp window: Card 300 × 169, Poster 146 × 216, Wide 440 × 132.
internal val CwCardWidth = 300.dp
internal val CwCardStillHeight = 169.dp
internal val CwWideWidth = 440.dp
internal val CwWideHeight = 132.dp
private val CwWideStillWidth = 200.dp
internal val CwRowGap = 16.dp
/** Title (9 dp gap, 15 sp) plus the meta line under a Card or Poster still. */
internal val CwTextBlockHeight = 46.dp
private val CwStillShape = RoundedCornerShape(14.dp)
private val CwPosterShape = RoundedCornerShape(12.dp)
private val CwRingColor = Color.White.copy(alpha = 0.85f)
private val CwInk = Color(0xFFF3F4F6)
private val CwOnInk = Color(0xFF0B0C0E)

/** Card and Poster: the still (16:9 or 2:3) with the bar inside its foot, the text under it. */
@Composable
internal fun DesktopContinueWatchingCard(
    item: ContinueWatchingItem,
    poster: Boolean,
    posterWidth: Dp,
    artworkUrl: String?,
    fallbackUrl: String?,
    blurred: Boolean,
    onClick: (() -> Unit)?,
    onLongClick: (() -> Unit)?,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val source = rememberPosterLiftSource(artworkUrl)
    val shape = if (poster) CwPosterShape else CwStillShape
    val radius = if (poster) 12.dp else 14.dp
    val width = if (poster) posterWidth else CwCardWidth
    Column(Modifier.width(width).testTag("cw_card")) {
        Box(
            Modifier.fillMaxWidth()
                .aspectRatio(if (poster) 146f / 216f else 16f / 9f)
                .hoverable(interaction)
                .posterCardClickable(
                    onClick = onClick,
                    onLongClick = onLongClick,
                    zoomImageUrl = artworkUrl,
                    zoomCornerRadius = radius,
                    hoverScaleEnabled = false,
                    liftSource = source,
                ),
        ) {
            Box(Modifier.fillMaxSize().clip(shape).background(Color(0xFF1B1C1F)).border(1.dp, RedesignColors.hairline, shape)) {
                CwArtwork(artworkUrl, fallbackUrl, blurred, item.title, if (item.isCloudLibraryItemForCards()) ContentScale.Fit else ContentScale.Crop)
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.62f to Color.Transparent, 1f to Color(0xB3060607))))
                if (item.progressFraction > 0f) {
                    CwProgressBar(item.progressFraction, Modifier.align(Alignment.BottomStart).padding(start = 12.dp, end = 12.dp, bottom = 11.dp))
                }
                androidx.compose.animation.AnimatedVisibility(visible = hovered && onClick != null, Modifier.align(Alignment.BottomStart), enter = fadeIn(tween(140)), exit = fadeOut(tween(120))) {
                    CwResumePill(onClick, Modifier.padding(start = 12.dp, bottom = 22.dp))
                }
                androidx.compose.animation.AnimatedVisibility(visible = hovered && onLongClick != null, Modifier.align(Alignment.TopEnd), enter = fadeIn(tween(140)), exit = fadeOut(tween(120))) {
                    CwMoreButton(Modifier.padding(10.dp)) {
                        // ⋯ opens the same menu as right-click, anchored to this card.
                        source.bounds?.let { PosterZoomAnchorHolder.stash(PosterZoomAnchor(it, artworkUrl, radius).also { a -> a.source = source }) }
                        onLongClick?.invoke()
                    }
                }
            }
            CwHoverRing(hovered, shape)
        }
        Text(
            item.title,
            style = redesignText(15.sp, FontWeight.SemiBold, letterSpacing = (-0.01).em),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 9.dp),
        )
        Row(Modifier.fillMaxWidth().padding(top = 1.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                cwMetaLine(item),
                style = redesignText(13.sp, color = RedesignColors.textMuted),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(cwRightLabel(item), style = redesignText(13.sp, color = RedesignColors.textMuted), maxLines = 1)
        }
    }
}

/** Wide: 440 × 132, the still on the left (bar inside), the S E, title, two lines of plot and ▶. */
@Composable
internal fun DesktopContinueWatchingWideCard(
    item: ContinueWatchingItem,
    artworkUrl: String?,
    fallbackUrl: String?,
    blurred: Boolean,
    onClick: (() -> Unit)?,
    onLongClick: (() -> Unit)?,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val fill by animateColorAsState(Color.White.copy(alpha = if (hovered) 0.08f else 0.05f), tween(140), label = "cw_wide_fill")
    Box(
        Modifier.width(CwWideWidth).height(CwWideHeight).testTag("cw_card")
            .hoverable(interaction)
            .posterCardClickable(
                onClick = onClick,
                onLongClick = onLongClick,
                zoomImageUrl = artworkUrl,
                zoomCornerRadius = 14.dp,
                hoverScaleEnabled = false,
            ),
    ) {
        Row(Modifier.fillMaxSize().clip(CwStillShape).background(fill).border(1.dp, RedesignColors.hairline, CwStillShape)) {
            Box(Modifier.width(CwWideStillWidth).fillMaxHeight().background(Color(0xFF1B1C1F))) {
                CwArtwork(artworkUrl, fallbackUrl, blurred, item.title, if (item.isCloudLibraryItemForCards()) ContentScale.Fit else ContentScale.Crop)
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.6f to Color.Transparent, 1f to Color(0x99060607))))
                if (item.progressFraction > 0f) {
                    CwProgressBar(item.progressFraction, Modifier.align(Alignment.BottomStart).padding(start = 10.dp, end = 10.dp, bottom = 9.dp))
                }
            }
            Column(Modifier.weight(1f).fillMaxHeight().padding(horizontal = 14.dp, vertical = 12.dp)) {
                Text(
                    cwWideKicker(item).uppercase(),
                    style = redesignText(11.sp, FontWeight.SemiBold, RedesignColors.textMuted, letterSpacing = 0.08.em),
                    maxLines = 1,
                )
                Text(
                    item.title,
                    style = redesignText(15.5.sp, FontWeight.SemiBold, letterSpacing = (-0.01).em),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 1.dp),
                )
                cwWidePlot(item)?.let { plot ->
                    Text(
                        plot,
                        style = redesignText(13.sp, color = RedesignColors.textSecondary, lineHeight = 17.5.sp),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                Spacer(Modifier.weight(1f))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val left = cwRightLabel(item)
                    val ends = cwEndsAt(item)
                    Text(
                        listOfNotNull(left, ends?.let { stringResource(Res.string.cw_ends_at, it) }).joinToString("  ·  "),
                        style = redesignText(12.5.sp, color = RedesignColors.textMuted),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (onClick != null) {
                        Box(
                            Modifier.size(30.dp).clip(CircleShape).background(CwInk)
                                .clickable(role = Role.Button, onClick = onClick),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Rounded.PlayArrow, stringResource(Res.string.cw_continue_watching_resume), tint = CwOnInk, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
        CwHoverRing(hovered, CwStillShape)
    }
}

@Composable
private fun BoxScope.CwHoverRing(hovered: Boolean, shape: Shape) {
    val ring by animateColorAsState(if (hovered) CwRingColor else Color.Transparent, tween(140), label = "cw_ring")
    Box(Modifier.matchParentSize().border(2.dp, ring, shape))
}

@Composable
private fun CwArtwork(url: String?, fallbackUrl: String?, blurred: Boolean, title: String, scale: ContentScale) {
    if (url == null) return
    NuvioAsyncImage(
        model = continuewatchingImageModel(cloudLibraryDisplayArtworkUrl(url), fallbackUrl),
        contentDescription = title,
        modifier = Modifier.fillMaxSize().then(if (blurred) Modifier.blur(18.dp) else Modifier),
        contentScale = scale,
        desktopAnimation = rememberAnimatedArtworkAnimation(),
    )
}

@Composable
private fun CwProgressBar(fraction: Float, modifier: Modifier) {
    Box(modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = 0.28f))) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0.02f, 1f)).fillMaxHeight().background(CwInk))
    }
}

@Composable
private fun CwResumePill(onClick: (() -> Unit)?, modifier: Modifier) {
    Row(
        modifier.height(30.dp).clip(CircleShape).background(CwInk)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(start = 9.dp, end = 13.dp)
            .testTag("cw_resume"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Icon(Icons.Rounded.PlayArrow, null, tint = CwOnInk, modifier = Modifier.size(16.dp))
        Text(stringResource(Res.string.cw_continue_watching_resume), style = redesignText(13.sp, FontWeight.Bold, CwOnInk))
    }
}

@Composable
private fun CwMoreButton(modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.size(32.dp).clip(CircleShape).background(Color(0x990A0A0B)).border(1.dp, RedesignColors.hairline, CircleShape)
            .clickable(role = Role.Button, onClick = onClick).testTag("cw_more"),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Rounded.MoreHoriz, stringResource(Res.string.cw_continue_watching_more), tint = Color(0xFFE9EAEC), modifier = Modifier.size(18.dp))
    }
}

/** “S1 E3 · Episode name” for an episode, “Movie” for a film. */
@Composable
private fun cwMetaLine(item: ContinueWatchingItem): String {
    val season = item.seasonNumber
    val episode = item.episodeNumber
    if (season == null || episode == null) return stringResource(Res.string.media_movie)
    val code = stringResource(Res.string.streams_episode_badge, season, episode)
    return listOfNotNull(code, localizedEpisodeTitle(item.episodeTitle, item.episodeNumber)).joinToString(" · ")
}

/** Wide's small caps line: S E, or Movie · year. */
@Composable
private fun cwWideKicker(item: ContinueWatchingItem): String {
    val season = item.seasonNumber
    val episode = item.episodeNumber
    if (season != null && episode != null) return stringResource(Res.string.streams_episode_badge, season, episode)
    val year = item.released?.take(4)?.takeIf { it.length == 4 && it.all(Char::isDigit) }
    return listOfNotNull(stringResource(Res.string.media_movie), year).joinToString(" · ")
}

/** Wide's two lines: the episode title, then its synopsis. */
@Composable
private fun cwWidePlot(item: ContinueWatchingItem): String? {
    val episodeTitle = localizedEpisodeTitle(item.episodeTitle, item.episodeNumber)
    val plot = item.pauseDescription?.trim()?.takeIf { it.isNotBlank() }
    return when {
        episodeTitle != null && plot != null -> "$episodeTitle. $plot"
        else -> episodeTitle ?: plot
    }
}

/** Time left (“22m left”, “2h 31m left”); Trakt progress with no runtime shows “n% watched”. */
@Composable
private fun cwRightLabel(item: ContinueWatchingItem): String {
    if (item.progressFraction > 0f) {
        val remaining = cwRemainingMs(item)
            ?: return stringResource(Res.string.home_continue_watching_watched, "${(item.progressFraction * 100f).roundToInt().coerceIn(1, 99)}%")
        val minutes = (remaining / 60_000L).coerceAtLeast(1L)
        return if (minutes >= 60L) stringResource(Res.string.home_continue_watching_hours_minutes_left, minutes / 60L, minutes % 60L)
        else stringResource(Res.string.home_continue_watching_minutes_left, minutes)
    }
    return computeAirDateBadgeText(item.released, CurrentDateProvider.todayIsoDate())
        ?: stringResource(Res.string.home_continue_watching_up_next)
}

private fun cwRemainingMs(item: ContinueWatchingItem): Long? {
    if (item.durationMs <= 0L) return null
    val position = if (item.resumePositionMs > 0L) item.resumePositionMs else (item.durationMs * item.progressFraction.coerceIn(0f, 1f)).toLong()
    return (item.durationMs - position).coerceAtLeast(0L)
}

@Composable
private fun cwEndsAt(item: ContinueWatchingItem): String? {
    val remaining = cwRemainingMs(item) ?: return null
    if (item.progressFraction <= 0f) return null
    return remember(remaining / 60_000L) { formatLocalTime(WatchProgressClock.nowEpochMs() + remaining) }
}

private fun ContinueWatchingItem.isCloudLibraryItemForCards(): Boolean =
    parentMetaType.equals(com.nuvio.app.features.cloud.CloudLibraryContentType, ignoreCase = true)

/** Only titles you are partway through; next episodes and new seasons belong to the board's Up next. */
internal fun ContinueWatchingItem.isInProgressForContinueWatchingRow(): Boolean =
    !isNextUp && !isReleaseAlert && progressFraction > 0f
