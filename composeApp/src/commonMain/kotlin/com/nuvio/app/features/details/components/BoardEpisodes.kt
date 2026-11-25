package com.nuvio.app.features.details.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nuvio.app.core.format.formatReleaseDateForDisplay
import com.nuvio.app.core.i18n.localizedEpisodeTitle
import com.nuvio.app.core.ui.NuvioAsyncImage as AsyncImage
import com.nuvio.app.core.ui.NuvioProgressBar
import com.nuvio.app.core.ui.RedesignColors
import com.nuvio.app.core.ui.nuvioFocusBorder
import com.nuvio.app.core.ui.posterCardClickable
import com.nuvio.app.core.ui.rememberAnimatedArtworkAnimation
import com.nuvio.app.core.ui.secondaryClick
import com.nuvio.app.features.details.EpisodeRatingsVisibility
import com.nuvio.app.features.details.MetaVideo
import com.nuvio.app.features.details.audienceRatingColor
import com.nuvio.app.features.watchprogress.WatchProgressEntry
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.detail_board_minutes_left
import nuvio.composeapp.generated.resources.episode_mark_unwatched
import nuvio.composeapp.generated.resources.episode_mark_watched
import nuvio.composeapp.generated.resources.episodes_cd_watched
import org.jetbrains.compose.resources.stringResource

/** Gap between the board's Row episode cards, and how many fit across (the Series board frame). */
internal val BoardEpisodeCardGap = 14.dp
private const val BoardEpisodeCardsAcross = 5

/** Width of one Row card so [BoardEpisodeCardsAcross] fill [containerWidth], within sensible bounds. */
internal fun boardEpisodeCardWidth(containerWidth: Dp): Dp =
    ((containerWidth - BoardEpisodeCardGap * (BoardEpisodeCardsAcross - 1)) / BoardEpisodeCardsAcross)
        .coerceIn(220.dp, 320.dp)

/** What both board episode layouts show for one episode. */
internal class BoardEpisodeInfo(
    val video: MetaVideo,
    val imageUrl: String?,
    val isWatched: Boolean,
    val isCurrent: Boolean,
    val progressEntry: WatchProgressEntry?,
    val ratingValue: Double?,
    val blurArtwork: Boolean,
) {
    val title: String get() = localizedEpisodeTitle(video.title, video.episode) ?: video.title
    /** Trakt's entries hold only a percentage (no position or duration), so this reads the fraction. */
    val inProgress: WatchProgressEntry? get() = progressEntry?.takeIf { it.progressFraction > 0f && !it.isEffectivelyCompleted }
}

@Composable
internal fun rememberBoardEpisodeInfo(
    video: MetaVideo,
    fallbackImage: String?,
    progressEntry: WatchProgressEntry?,
    rating: Double?,
    isWatched: Boolean,
    isCurrent: Boolean,
    episodeRatingsVisibility: EpisodeRatingsVisibility,
    blurUnwatchedEpisodes: Boolean,
): BoardEpisodeInfo = remember(video, fallbackImage, progressEntry, rating, isWatched, isCurrent, episodeRatingsVisibility, blurUnwatchedEpisodes) {
    BoardEpisodeInfo(
        video = video,
        imageUrl = video.thumbnail ?: fallbackImage,
        isWatched = isWatched,
        isCurrent = isCurrent,
        progressEntry = progressEntry,
        ratingValue = rating?.takeIf { it > 0.0 && episodeRatingsVisibility.showRating(isWatched) },
        blurArtwork = blurUnwatchedEpisodes && !isWatched,
    )
}

/** "17 Jan 2025 · 50 min", plus "· 31 min left" for an episode in progress when [withLeft]. */
@Composable
private fun BoardEpisodeInfo.metaLine(withLeft: Boolean): String {
    val date = remember(video.released) { video.released?.let { formatReleaseDateForDisplay(it) } }
    val runtime = remember(video.runtime) { video.runtime?.takeIf { it > 0 }?.let(::formatEpisodeRuntime) }
    val left = inProgress?.takeIf { withLeft }?.let { entry ->
        val totalMinutes = entry.durationMs.takeIf { it > 0L }?.let { it / 60_000.0 } ?: video.runtime?.toDouble()
        val minutes = totalMinutes?.let { (it * (1f - entry.progressFraction)).toInt() }
        minutes?.takeIf { it > 0 }?.let { stringResource(Res.string.detail_board_minutes_left, it) }
    }
    return listOfNotNull(date, runtime, left).joinToString(" · ")
}

@Composable
private fun BoardEpisodeRating(value: Double, fontSize: androidx.compose.ui.unit.TextUnit) {
    Text(
        text = "★ ${formatEpisodeRating(value)}",
        style = TextStyle(fontSize = fontSize, fontWeight = FontWeight.Bold),
        color = audienceRatingColor(value),
        maxLines = 1,
    )
}

@Composable
private fun BoardEpisodeArtwork(info: BoardEpisodeInfo, modifier: Modifier = Modifier) {
    Box(modifier.background(RedesignColors.fill)) {
        info.imageUrl?.let { url ->
            AsyncImage(
                model = url,
                contentDescription = info.video.title,
                modifier = Modifier.fillMaxSize().then(if (info.blurArtwork) Modifier.blur(18.dp) else Modifier),
                contentScale = ContentScale.Crop,
                desktopAnimation = rememberAnimatedArtworkAnimation(),
            )
        }
    }
}

@Composable
private fun BoardEpisodeProgress(entry: WatchProgressEntry, modifier: Modifier = Modifier) {
    NuvioProgressBar(
        progress = entry.progressFraction,
        modifier = modifier,
        height = 3.dp,
        trackColor = Color.White.copy(alpha = 0.25f),
        fillColor = Color.White,
    )
}

/**
 * The board's Row episode card (Series board frame): a 16:9 still with the episode code and a
 * watched ✓, then title, two lines of synopsis, and date · runtime with the ★ rating under it. The
 * current (up-next) episode has a white ring and its progress on the still instead of the ✓.
 */
@Composable
internal fun BoardEpisodeCard(
    info: BoardEpisodeInfo,
    width: Dp,
    onClick: (() -> Unit)?,
    onLongPress: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val stillShape = RoundedCornerShape(14.dp)
    val ringWidth by animateDpAsState(if (info.isCurrent) 2.dp else 1.dp, tween(220), label = "board_episode_ring")
    val ringColor by animateColorAsState(if (info.isCurrent) Color.White else RedesignColors.hairline, tween(220), label = "board_episode_ring_color")
    Column(
        modifier = modifier
            .width(width)
            .testTag("detail_board_episode_${info.video.id}")
            .posterCardClickable(onClick = onClick, onLongClick = onLongPress, zoomCornerRadius = 14.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(stillShape)
                .border(ringWidth, ringColor, stillShape)
                .nuvioFocusBorder(stillShape)
                .testTag("detail_board_episode_still_${info.video.id}"),
        ) {
            BoardEpisodeArtwork(info, Modifier.fillMaxSize())
            Text(
                text = info.video.episodeBadge(),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(10.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xB308090B))
                    .padding(horizontal = 7.dp, vertical = 2.dp),
                style = TextStyle(fontSize = 11.5.sp, fontWeight = FontWeight.Bold),
                color = RedesignColors.textPrimary,
                maxLines = 1,
            )
            androidx.compose.animation.AnimatedVisibility(
                visible = info.isWatched && !info.isCurrent,
                enter = fadeIn(tween(200)) + scaleIn(tween(200), initialScale = 0.7f),
                exit = fadeOut(tween(160)) + scaleOut(tween(160), targetScale = 0.7f),
                modifier = Modifier.align(Alignment.TopEnd).padding(9.dp),
            ) {
                BoardWatchedDot(size = 24.dp, iconSize = 13.dp)
            }
            EpisodeAvailabilityBadge(
                video = info.video,
                modifier = Modifier.align(Alignment.BottomStart).padding(start = 10.dp, bottom = if (info.inProgress != null) 20.dp else 10.dp),
            )
            info.inProgress?.takeIf { info.isCurrent || !info.isWatched }?.let { entry ->
                BoardEpisodeProgress(entry, Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(start = 10.dp, end = 10.dp, bottom = 9.dp))
            }
        }
        Spacer(Modifier.height(9.dp))
        Text(
            text = info.title,
            style = TextStyle(fontSize = 14.5.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.15).sp),
            color = RedesignColors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = info.video.overview.orEmpty(),
            modifier = Modifier.padding(top = 2.dp, bottom = 6.dp).height(36.dp),
            style = TextStyle(fontSize = 12.8.sp, lineHeight = 18.sp),
            color = RedesignColors.textSecondary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = info.metaLine(withLeft = false),
                modifier = Modifier.weight(1f),
                style = TextStyle(fontSize = 12.sp),
                color = RedesignColors.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            info.ratingValue?.let { BoardEpisodeRating(it, 12.sp) }
        }
    }
}

/**
 * The board's List row (Episodes as a list frame): a small still, "3 · Title" with the ★ rating,
 * one line of synopsis, date · runtime (· minutes left), and a round button at the end. The
 * current episode sits on a lighter fill and its button plays; the others mark watched.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun BoardEpisodeRow(
    info: BoardEpisodeInfo,
    onClick: (() -> Unit)?,
    onLongPress: (() -> Unit)?,
    onWatchedToggle: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(14.dp)
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val background by animateColorAsState(
        when {
            info.isCurrent -> RedesignColors.fillStrong
            hovered -> RedesignColors.fill
            else -> Color.Transparent
        },
        tween(200),
        label = "board_episode_row_bg",
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .testTag("detail_board_episode_${info.video.id}")
            .clip(shape)
            .background(background)
            .nuvioFocusBorder(shape)
            .hoverable(interaction)
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                enabled = onClick != null || onLongPress != null,
                onClick = { onClick?.invoke() },
                onLongClick = onLongPress,
            )
            .secondaryClick(onLongPress)
            .padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(150.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(10.dp))) {
            BoardEpisodeArtwork(info, Modifier.fillMaxSize())
            EpisodeAvailabilityBadge(video = info.video, modifier = Modifier.align(Alignment.TopStart).padding(6.dp))
            info.inProgress?.takeIf { info.isCurrent || !info.isWatched }?.let { entry ->
                BoardEpisodeProgress(entry, Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(start = 8.dp, end = 8.dp, bottom = 6.dp))
            }
        }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = info.video.episode?.let { "$it · ${info.title}" } ?: info.title,
                    modifier = Modifier.weight(1f),
                    style = TextStyle(fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold),
                    color = RedesignColors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                info.ratingValue?.let {
                    Spacer(Modifier.width(10.dp))
                    BoardEpisodeRating(it, 12.5.sp)
                }
            }
            info.video.overview?.takeIf { it.isNotBlank() }?.let { overview ->
                Text(
                    text = overview,
                    modifier = Modifier.padding(top = 1.dp, bottom = 2.dp),
                    style = TextStyle(fontSize = 12.8.sp),
                    color = RedesignColors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = info.metaLine(withLeft = true),
                style = TextStyle(fontSize = 12.sp),
                color = RedesignColors.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        BoardEpisodeEndButton(info = info, onPlay = onClick, onWatchedToggle = onWatchedToggle)
    }
}

/** The List row's round button: ▶ on the current episode, otherwise a filled (watched) or outlined ✓. */
@Composable
private fun BoardEpisodeEndButton(info: BoardEpisodeInfo, onPlay: (() -> Unit)?, onWatchedToggle: (() -> Unit)?) {
    val filled = info.isCurrent || info.isWatched
    val fill by animateColorAsState(if (filled) RedesignColors.textPrimary else Color.Transparent, tween(200), label = "board_episode_button_fill")
    val tint by animateColorAsState(if (filled) Color(0xFF0A0B0D) else RedesignColors.textSecondary, tween(200), label = "board_episode_button_tint")
    val action = if (info.isCurrent) onPlay else onWatchedToggle
    val description = when {
        info.isCurrent -> info.title
        info.isWatched -> stringResource(Res.string.episode_mark_unwatched)
        else -> stringResource(Res.string.episode_mark_watched)
    }
    Box(
        Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(fill)
            .border(1.dp, if (filled) Color.Transparent else RedesignColors.hairline, CircleShape)
            .then(if (action != null) Modifier.clickable(onClick = action) else Modifier)
            .testTag("detail_board_episode_watched_${info.video.id}"),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (info.isCurrent) Icons.Rounded.PlayArrow else Icons.Rounded.Check,
            contentDescription = description,
            tint = tint,
            modifier = Modifier.size(if (info.isCurrent) 16.dp else 15.dp),
        )
    }
}

@Composable
private fun BoardWatchedDot(size: Dp, iconSize: Dp) {
    Box(
        Modifier.size(size).clip(CircleShape).background(RedesignColors.textPrimary),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Rounded.Check,
            contentDescription = stringResource(Res.string.episodes_cd_watched),
            tint = Color(0xFF0A0B0D),
            modifier = Modifier.size(iconSize),
        )
    }
}
