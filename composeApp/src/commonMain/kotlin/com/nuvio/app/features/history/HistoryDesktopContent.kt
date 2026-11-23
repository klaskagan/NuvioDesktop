package com.nuvio.app.features.history

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
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
import com.nuvio.app.core.ui.*
import com.nuvio.app.features.calendar.CalendarPlatform
import com.nuvio.app.features.home.components.rememberLandscapeMediaArtwork
import com.nuvio.app.features.tracking.TrackingMediaKind
import com.nuvio.app.features.trakt.calendarDatePlusDays
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

internal val HistoryTilePadding = 16.dp
private val HistoryTileRadius = 18.dp
private val HistoryBoardGap = 14.dp
private val HistoryBoardShape = RoundedCornerShape(18.dp)
private val HistoryBoardScrim = Brush.verticalGradient(0.25f to Color.Transparent, 1f to Color(0xEB08090B))

/**
 * One slice of a frosted day tile that spans several LazyColumn items (heading, then card rows).
 * Today's tile is brighter, like Calendar's week tile.
 */
internal fun Modifier.historyDayTile(isFirst: Boolean, isLast: Boolean, highlighted: Boolean): Modifier = drawBehind {
    val r = CornerRadius(HistoryTileRadius.toPx())
    val path = Path().apply {
        addRoundRect(
            RoundRect(
                left = 0f, top = 0f, right = size.width, bottom = size.height,
                topLeftCornerRadius = if (isFirst) r else CornerRadius.Zero,
                topRightCornerRadius = if (isFirst) r else CornerRadius.Zero,
                bottomLeftCornerRadius = if (isLast) r else CornerRadius.Zero,
                bottomRightCornerRadius = if (isLast) r else CornerRadius.Zero,
            ),
        )
    }
    drawPath(path, if (highlighted) RedesignColors.fillStrong else BackdropSurface.card)
}

@Composable
private fun dayKicker(date: String, today: Boolean): String {
    val weekday = CalendarPlatform.dateLabel(date, "EEEE")
    return (if (today) "${stringResource(Res.string.calendar_today)} · $weekday" else weekday).uppercase()
}

/** Tile heading: large day number, weekday kicker over month and year, the watch count. */
@Composable
internal fun HistoryTileHeading(date: String, count: Int, today: Boolean) {
    Row(
        Modifier.fillMaxWidth().padding(start = HistoryTilePadding + 2.dp, end = HistoryTilePadding + 2.dp, top = 16.dp, bottom = 2.dp)
            .testTag("history-day-heading-$date"),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            date.takeLast(2).toInt().toString(),
            style = redesignText(30.sp, FontWeight.Bold, letterSpacing = RedesignTightTracking),
        )
        Column(Modifier.padding(bottom = 4.dp)) {
            Text(
                dayKicker(date, today),
                style = redesignText(11.sp, FontWeight.Medium, if (today) RedesignColors.textPrimary else RedesignColors.textMuted, letterSpacing = 0.1.em),
                maxLines = 1,
            )
            Text(CalendarPlatform.dateLabel(date, "MMMM yyyy"), style = redesignText(13.sp, color = RedesignColors.textSecondary), maxLines = 1)
        }
        Spacer(Modifier.weight(1f))
        Text(
            pluralStringResource(Res.plurals.history_watch_count, count, count),
            style = redesignText(12.5.sp, FontWeight.Medium, RedesignColors.textMuted),
            modifier = Modifier.padding(bottom = 6.dp),
        )
    }
}

/** Numbers for the Board's "This week" tile, counted from loaded events only. */
internal data class HistoryWeekStats(val episodes: Int, val movies: Int, val activeDays: Int)

internal data class HistoryTopShow(val title: String, val poster: String?, val watches: Int)

internal fun historyWeekStats(events: List<com.nuvio.app.features.tracking.TrackingHistoryEvent>, today: String): HistoryWeekStats {
    val start = calendarDatePlusDays(today, -6)
    val week = events.filter { (historyLocalDate(it) ?: today) >= start }
    return HistoryWeekStats(
        episodes = week.count { it.media.kind != TrackingMediaKind.MOVIE },
        movies = week.count { it.media.kind == TrackingMediaKind.MOVIE },
        activeDays = week.mapNotNull(::historyLocalDate).distinct().size,
    )
}

internal fun historyTopShow(events: List<com.nuvio.app.features.tracking.TrackingHistoryEvent>, today: String): HistoryTopShow? {
    val start = calendarDatePlusDays(today, -6)
    val shows = events.filter { it.media.kind != TrackingMediaKind.MOVIE && (historyLocalDate(it) ?: today) >= start }
    val top = shows.groupBy { it.media.catalog?.contentId ?: it.media.ids.imdb ?: it.media.ids.tmdb?.toString() ?: it.media.title }
        .maxByOrNull { it.value.size }?.value ?: return null
    val first = top.first()
    return HistoryTopShow(first.media.title.orEmpty(), first.media.posterUrl, top.size)
}

/**
 * Board: the latest watch as a large artwork tile beside a "This week" tile and the week's most
 * watched show, on Home board's tile shapes. Narrow panes stack the tiles.
 */
@Composable
internal fun HistoryBoard(
    latest: HistoryCard?,
    latestDate: String?,
    today: String,
    stats: HistoryWeekStats?,
    topShow: HistoryTopShow?,
    wide: Boolean,
    onOpen: ((HistoryCard) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    if (wide) {
        Row(modifier.fillMaxWidth().height(264.dp).testTag("history-board")) {
            HistoryLatestTile(latest, latestDate, today, onOpen, Modifier.weight(1f).fillMaxHeight())
            Column(Modifier.padding(start = HistoryBoardGap).width(352.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(HistoryBoardGap)) {
                HistoryStatsTile(stats, Modifier.weight(1f).fillMaxWidth())
                HistoryTopShowTile(topShow, stats != null, Modifier.weight(1f).fillMaxWidth())
            }
        }
    } else {
        Column(modifier.fillMaxWidth().testTag("history-board"), verticalArrangement = Arrangement.spacedBy(HistoryBoardGap)) {
            HistoryLatestTile(latest, latestDate, today, onOpen, Modifier.fillMaxWidth().height(220.dp))
            Row(Modifier.fillMaxWidth().height(124.dp), horizontalArrangement = Arrangement.spacedBy(HistoryBoardGap)) {
                HistoryStatsTile(stats, Modifier.weight(1f).fillMaxHeight())
                HistoryTopShowTile(topShow, stats != null, Modifier.weight(1f).fillMaxHeight())
            }
        }
    }
}

@Composable
private fun HistoryLatestTile(
    card: HistoryCard?,
    date: String?,
    today: String,
    onOpen: ((HistoryCard) -> Unit)?,
    modifier: Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val border by animateColorAsState(if (hovered) RedesignColors.hairlineFocused else RedesignColors.hairline, tween(180), label = "latest_border")
    Box(
        modifier.clip(HistoryBoardShape).background(RedesignColors.fill).border(1.dp, border, HistoryBoardShape)
            .hoverable(interaction)
            .then(if (card != null && onOpen != null) Modifier.clickable(interaction, indication = null, role = Role.Button) { onOpen(card) } else Modifier)
            .testTag("history-board-latest"),
    ) {
        if (card == null) return@Box
        val detail = card.detail
        val lookup = rememberLandscapeMediaArtwork(detail?.id.orEmpty(), detail?.type.orEmpty(), enabled = detail != null)
        val art = lookup.artwork?.let { it.cleanBackdrop ?: it.backdrop } ?: card.preview.landscapePoster ?: card.preview.poster
        androidx.compose.animation.Crossfade(art, animationSpec = tween(260), label = "latest_art", modifier = Modifier.fillMaxSize()) { url ->
            if (url != null) NuvioAsyncImage(model = url, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop, alignment = Alignment.TopCenter)
        }
        Box(Modifier.fillMaxSize().background(HistoryBoardScrim))
        Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(20.dp)) {
            val day = when (date) {
                null -> null
                today -> stringResource(Res.string.calendar_today)
                else -> CalendarPlatform.dayLabel(date)
            }
            Text(
                listOfNotNull(stringResource(Res.string.history_last_watched), day, formatLocalTime(card.event.watchedAtMillis)).joinToString(" · ").uppercase(),
                style = redesignText(11.sp, FontWeight.Medium, RedesignColors.textSecondary, letterSpacing = 0.1.em),
                maxLines = 1,
            )
            Spacer(Modifier.height(4.dp))
            Text(card.preview.name, style = redesignText(26.sp, FontWeight.Bold, letterSpacing = RedesignTightTracking), maxLines = 1, overflow = TextOverflow.Ellipsis)
            val episode = card.event.media.episode
            if (episode != null) {
                val code = episode.season?.let { stringResource(Res.string.history_season_episode, it, episode.number) }
                    ?: stringResource(Res.string.history_episode, episode.number)
                Text(
                    listOfNotNull(code, card.episodeTitle).joinToString(" - "),
                    style = redesignText(13.5.sp, FontWeight.Medium, RedesignColors.textSecondary),
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            } else card.event.media.year?.let {
                Text(it.toString(), style = redesignText(13.5.sp, FontWeight.Medium, RedesignColors.textSecondary))
            }
        }
    }
}

@Composable
private fun HistoryStatsTile(stats: HistoryWeekStats?, modifier: Modifier) {
    Column(
        modifier.clip(HistoryBoardShape).background(RedesignColors.fill).border(1.dp, RedesignColors.hairline, HistoryBoardShape)
            .padding(horizontal = 18.dp, vertical = 14.dp).testTag("history-board-week"),
    ) {
        Text(
            stringResource(Res.string.calendar_this_week).uppercase(),
            style = redesignText(11.sp, FontWeight.Medium, RedesignColors.textMuted, letterSpacing = 0.1.em),
        )
        Spacer(Modifier.weight(1f))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            HistoryStat(stats?.episodes, pluralStringResource(Res.plurals.history_stat_episodes, stats?.episodes ?: 0), Modifier.weight(1f))
            HistoryStat(stats?.movies, pluralStringResource(Res.plurals.history_stat_movies, stats?.movies ?: 0), Modifier.weight(1f))
            HistoryStat(stats?.activeDays, pluralStringResource(Res.plurals.history_stat_days, stats?.activeDays ?: 0), Modifier.weight(1f))
        }
    }
}

@Composable
private fun HistoryStat(value: Int?, label: String, modifier: Modifier) {
    Column(modifier) {
        Text(value?.toString() ?: "–", style = redesignText(30.sp, FontWeight.Bold, letterSpacing = RedesignTightTracking))
        Text(label, style = redesignText(12.sp, color = RedesignColors.textMuted), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun HistoryTopShowTile(show: HistoryTopShow?, loaded: Boolean, modifier: Modifier) {
    Row(
        modifier.clip(HistoryBoardShape).background(RedesignColors.fill).border(1.dp, RedesignColors.hairline, HistoryBoardShape)
            .padding(12.dp).testTag("history-board-top-show"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.fillMaxHeight().aspectRatio(2f / 3f).clip(RoundedCornerShape(9.dp)).background(RedesignColors.fill)) {
            show?.poster?.let { NuvioAsyncImage(model = it, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
        }
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Text(
                stringResource(Res.string.history_most_watched).uppercase(),
                style = redesignText(11.sp, FontWeight.Medium, RedesignColors.textMuted, letterSpacing = 0.1.em),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            if (show != null) {
                Text(show.title, style = redesignText(17.sp, FontWeight.Bold, letterSpacing = RedesignTightTracking), maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(pluralStringResource(Res.plurals.history_watch_count, show.watches, show.watches), style = redesignText(12.5.sp, color = RedesignColors.textSecondary))
            } else if (loaded) {
                Text("–", style = redesignText(17.sp, FontWeight.Bold))
            }
        }
    }
}

/**
 * Loading placeholder with a day tile's shape: a heading line and one row of card slots at the
 * real card width and aspect, so the first page fades in without moving anything.
 */
@Composable
internal fun HistoryDayTileSkeleton(columns: Int, cardWidth: Dp, landscape: Boolean, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(HistoryTileRadius)
    Column(
        modifier.fillMaxWidth().clip(shape).background(BackdropSurface.card)
            .padding(horizontal = HistoryTilePadding, vertical = 16.dp).testTag("history-skeleton"),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(width = 38.dp, height = 30.dp).clip(RoundedCornerShape(8.dp)).background(RedesignColors.fill))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.size(width = 110.dp, height = 10.dp).clip(CircleShape).background(RedesignColors.fill))
                Box(Modifier.size(width = 84.dp, height = 10.dp).clip(CircleShape).background(RedesignColors.fill))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            repeat(columns) {
                Column(Modifier.width(cardWidth), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        Modifier.fillMaxWidth().aspectRatio(if (landscape) 16f / 9f else 2f / 3f)
                            .clip(RoundedCornerShape(12.dp)).background(RedesignColors.fill),
                    )
                    Box(Modifier.fillMaxWidth(0.7f).height(12.dp).clip(CircleShape).background(RedesignColors.fill))
                    Box(Modifier.fillMaxWidth(0.45f).height(10.dp).clip(CircleShape).background(RedesignColors.fill))
                }
            }
        }
    }
}
