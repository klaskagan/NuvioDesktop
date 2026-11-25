package com.nuvio.app.features.details

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.nuvio.app.core.ui.LocalScreenActive
import com.nuvio.app.core.ui.containedScroll
import com.nuvio.app.core.ui.NuvioAsyncImage
import com.nuvio.app.core.ui.RedesignColors
import com.nuvio.app.core.ui.RedesignTightTracking
import com.nuvio.app.core.ui.ScreenActivityEffect
import com.nuvio.app.core.ui.redesignText
import com.nuvio.app.features.details.components.BoardKicker
import com.nuvio.app.features.details.components.boardTile
import com.nuvio.app.features.details.components.ratingReference
import com.nuvio.app.features.tracking.TrackingRatings
import com.nuvio.app.features.trakt.*
import com.nuvio.app.navigation.excludeFromHistoryGestures
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.isActive
import kotlin.coroutines.coroutineContext
import kotlin.math.roundToInt

// Your stats palette (Details design, "Your stats" frame).
private val retentionColor = Color(0xff7be0b0)
private val rewatchColor = Color(0xffe8b27a)
private val talkColor = Color(0xff8fb8ff)
private val fingerprintRatingColor = Color(0xffc8e3a0)
private val cellInk = Color(0xff10241a)

private val StatsGap = 14.dp
private val RetentionChartHeight = 196.dp
private val LeftTileMinHeight = 520.dp
/** Ratings by episode keeps room for its header, two season rows and the best/lowest line. */
private val RatingsTileMinHeight = 190.dp
private const val StatsWideMinWidthDp = 880

private fun seasonLabel(season: Int): String = if (season == 0) "Specials" else "Season $season"

private fun episodeCode(key: AudienceEpisodeKey): String = if (key.season == 0) "SP E${key.episode}" else "S${key.season} E${key.episode}"

internal class SeriesAudienceState {
    var show by mutableStateOf<Int?>(null)
    var episodes by mutableStateOf<Map<AudienceEpisodeKey, TraktAudienceEpisode>?>(null)
    var mappings by mutableStateOf(emptyMap<AudienceEpisodeKey, MetaVideo>())
    var mappedVideos: List<MetaVideo>? = null
    var noMatch by mutableStateOf(false)
    var requestedEpisode by mutableStateOf<AudienceEpisodeKey?>(null)
    var independentSeason by mutableStateOf<Int?>(null)
    var lastDesktopSeason: Int? = null
    var syncedDesktopSeason: Int? = null
    val seasonStats = mutableStateMapOf<Int, Map<AudienceEpisodeKey, TraktAudienceStats?>>()
}

/** The episode Next for you offers, with the board's play action. */
internal class StatsNextEpisode(
    val upNext: DetailBoardUpNext,
    val onPlay: (() -> Unit)?,
)

/**
 * Your stats. On the board ([board]), the design's grid: Retention and the fingerprint as one tall
 * tile beside Ratings by episode, Your <title> and Next for you. Elsewhere (the narrow list) the
 * progress card comes first and the audience tiles stack.
 */
@Composable
internal fun SeriesAudienceSection(
    meta: MetaDetails,
    season: Int,
    progress: List<SeasonWatchedCount>?,
    state: SeriesAudienceState,
    onSeasonSelected: (Int) -> Unit,
    onOpenSeason: (Int) -> Unit,
    board: Boolean = false,
    next: StatsNextEpisode? = null,
) {
    var nearViewport by remember(meta.id) { mutableStateOf(false) }
    val audience = TraktAudienceRepository.available && !state.noMatch
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(22.dp)) {
        if (!board) ProgressCard(progress, season, onOpenSeason)
        val personal: List<@Composable (Modifier) -> Unit> = if (!board) emptyList() else listOfNotNull(
            { modifier -> YouTile(meta, progress, modifier) },
            next?.let { episode -> { modifier: Modifier -> NextTile(episode, state, modifier) } },
        )
        if (audience) {
            Box(Modifier.fillMaxWidth().onGloballyPositioned { coordinates ->
                val top = coordinates.positionInRoot().y
                val viewportHeight = coordinates.findRootCoordinates().size.height
                nearViewport = top < viewportHeight + 240 && top + coordinates.size.height > -240
            }) {
                AudienceCards(meta, season, nearViewport, onSeasonSelected, state, personal)
            }
        } else if (personal.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(StatsGap)) {
                personal.forEach { tile -> tile(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun ProgressCard(counts: List<SeasonWatchedCount>?, season: Int, onOpenSeason: (Int) -> Unit) {
    if (counts?.isEmpty() == true) return
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Your progress", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground)
        Column(Modifier.fillMaxWidth().boardTile().padding(horizontal = 20.dp, vertical = 18.dp)) {
            val selected = counts?.firstOrNull { it.season == season }
            BoxWithConstraints {
                val wide = maxWidth >= 670.dp
                val summary: @Composable () -> Unit = {
                    Column(Modifier.widthIn(min = 140.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(selected?.let { "${it.watched}/${it.aired}" } ?: "—", style = redesignText(26.sp, FontWeight.Bold, letterSpacing = RedesignTightTracking))
                        Text(seasonLabel(season), style = redesignText(13.sp, FontWeight.SemiBold, RedesignColors.textSecondary))
                        Text(if (counts == null) "Loading watch history…" else selected?.let { "${it.watched} of ${it.aired} watched" }
                            ?: "No aired episodes", style = redesignText(12.sp, color = RedesignColors.textMuted))
                    }
                }
                val rows: @Composable () -> Unit = {
                    Column(Modifier.heightIn(min = 84.dp, max = 224.dp).verticalScroll(rememberScrollState())) {
                        counts.orEmpty().chunked(if (wide) 2 else 1).forEach { pair ->
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                pair.forEach { count ->
                                    val shape = RoundedCornerShape(8.dp)
                                    val active = count.season == season
                                    Row(
                                        Modifier.weight(1f).clip(shape)
                                            .background(if (active) RedesignColors.fillStrong else Color.Transparent)
                                            .semantics { this.selected = active }
                                            .clickable(onClickLabel = "Open ${seasonLabel(count.season)}") { onOpenSeason(count.season) }
                                            .padding(horizontal = 8.dp, vertical = 9.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(if (count.season == 0) "Specials" else "S${count.season}", style = redesignText(12.5.sp, FontWeight.SemiBold, RedesignColors.textSecondary))
                                        LinearProgressIndicator(
                                            progress = { count.watched.toFloat() / count.aired },
                                            modifier = Modifier.weight(1f).height(3.dp),
                                            color = RedesignColors.textPrimary,
                                            trackColor = Color.White.copy(alpha = 0.28f),
                                        )
                                        Text("${count.watched}/${count.aired}", style = redesignText(12.sp, FontWeight.Medium, RedesignColors.textMuted))
                                    }
                                }
                                if (wide && pair.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
                if (wide) {
                    Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        summary()
                        Box(Modifier.weight(1f)) { rows() }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { summary(); rows() }
                }
            }
        }
    }
}

@Composable
private fun AudienceCards(
    meta: MetaDetails,
    desktopSeason: Int,
    nearViewport: Boolean,
    onSeasonSelected: (Int) -> Unit,
    state: SeriesAudienceState,
    personal: List<@Composable (Modifier) -> Unit>,
) {
    val show = state.show
    val episodes = state.episodes.orEmpty()
    val mappings = state.mappings
    var catalogFailed by remember(meta.id) { mutableStateOf(false) }
    var reload by remember(meta.id) { mutableIntStateOf(0) }
    var episodeReload by remember(meta.id) { mutableIntStateOf(0) }
    ScreenActivityEffect(meta.id, meta.imdbId, meta.videos, nearViewport, reload) { active ->
        if (!active || !nearViewport) return@ScreenActivityEffect
        catalogFailed = false
        try {
            val id = show ?: TraktAudienceRepository.resolveShow(meta)
            if (id == null) {
                state.noMatch = true
                return@ScreenActivityEffect
            }
            state.show = id
            val catalog = state.episodes ?: withContext(Dispatchers.Default) {
                TraktAudienceRepository.seasons(id).episodeMap()
            }
            if (state.mappedVideos !== meta.videos) {
                state.mappings = withContext(Dispatchers.Default) { mapAudienceEpisodes(meta.videos, catalog) }
                state.mappedVideos = meta.videos
            }
            state.episodes = catalog
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            catalogFailed = true
        }
    }
    val linkedSeason = remember(mappings, desktopSeason) {
        mappings.filterValues { it.season == desktopSeason }.keys.map { it.season }.distinct().singleOrNull()
    }
    // Clear the override when a season change comes from the Episodes selector.
    LaunchedEffect(desktopSeason) {
        if (state.lastDesktopSeason != null && state.lastDesktopSeason != desktopSeason &&
            state.syncedDesktopSeason != desktopSeason
        ) {
            state.independentSeason = null
            state.requestedEpisode = null
        }
        state.syncedDesktopSeason = null
        state.lastDesktopSeason = desktopSeason
    }
    val seasonNumbers = remember(episodes) { episodes.keys.map { it.season }.distinct().sortedBy(::seasonSortKey) }
    val season = state.independentSeason ?: linkedSeason ?: seasonNumbers.firstOrNull()
    val seasonKeys = remember(episodes, season) { episodes.keys.filter { it.season == season } }
    val selected = state.requestedEpisode?.takeIf { it.season == season } ?: seasonKeys.firstOrNull()
    val pick: (AudienceEpisodeKey) -> Unit = { key ->
        state.requestedEpisode = key
        state.independentSeason = key.season
        mappings[key]?.season?.takeIf { it != desktopSeason }?.let {
            state.syncedDesktopSeason = it
            onSeasonSelected(it)
        }
    }
    val completed = state.seasonStats[season].orEmpty()
    val stats = remember(completed) { completed.mapNotNull { (key, value) -> value?.let { key to it } }.toMap() }
    var statsFailed by remember(show, season) { mutableStateOf(false) }
    var statsLoading by remember(show, season) { mutableStateOf(false) }
    val today = com.nuvio.app.features.watchprogress.CurrentDateProvider.todayIsoDate()
    val airedKeys = remember(seasonKeys, episodes, today) {
        seasonKeys.filter { key -> episodes[key]?.firstAired?.take(10)?.let { it <= today } != false }
    }
    ScreenActivityEffect(show, season, airedKeys, nearViewport, episodeReload) { active ->
        val id = show ?: return@ScreenActivityEffect
        val seasonNumber = season ?: return@ScreenActivityEffect
        if (!active || !nearViewport || airedKeys.isEmpty()) return@ScreenActivityEffect
        val missing = airedKeys.filter { it !in completed }
        if (missing.isEmpty()) return@ScreenActivityEffect
        statsFailed = false
        statsLoading = true
        try {
            for (batch in missing.chunked(2)) {
                val results = coroutineScope {
                    batch.map { key ->
                        async {
                            try {
                                key to TraktAudienceRepository.episodeStats(id, key)
                            } catch (error: CancellationException) {
                                throw error
                            } catch (error: Exception) {
                                if ((error as? AudienceRequestException)?.status == 429) throw error
                                statsFailed = true
                                null
                            }
                        }
                    }.awaitAll().filterNotNull()
                }
                state.seasonStats[seasonNumber] = state.seasonStats[seasonNumber].orEmpty() + results
            }
        } catch (error: AudienceRequestException) {
            statsFailed = true
        } finally {
            statsLoading = false
        }
    }

    val catalogLoading = state.episodes == null && !catalogFailed
    val pending = airedKeys.toSet() - completed.keys
    val status = when {
        catalogFailed -> "Episode stats unavailable"
        state.episodes == null || statsLoading -> "Loading episode stats…"
        statsFailed -> "Some episode stats could not be loaded"
        else -> "Episode stats unavailable"
    }
    val phase = rememberAudienceLoadingPhase(nearViewport && (catalogLoading || statsLoading))
    // The season's own totals: plays over its episodes, and the most watchers any episode had.
    val eyebrow = remember(season, stats) {
        val label = season?.let(::seasonLabel) ?: "Season"
        val plays = stats.values.mapNotNull { it.plays }.takeIf { it.isNotEmpty() }?.sum()
        val watchers = stats.values.mapNotNull { it.watchers }.maxOrNull()
        listOfNotNull(label, plays?.let { "${groupedCount(it)} plays" }, watchers?.let { "${groupedCount(it)} Trakt watchers" }).joinToString(" · ")
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        BoxWithConstraints {
            val wide = maxWidth >= StatsWideMinWidthDp.dp
            val left: @Composable (Modifier) -> Unit = { modifier ->
                RetentionTile(
                    eyebrow, seasonKeys, episodes, stats, selected, status, catalogLoading,
                    if (statsLoading) pending else emptySet(), phase, pick, modifier,
                )
            }
            val ratings: @Composable (Modifier, Boolean) -> Unit = { modifier, fill ->
                RatingsTile(episodes, selected, today, status, fill, catalogLoading, phase, pick, modifier)
            }
            if (wide) {
                SubcomposeLayout { constraints ->
                    val gap = StatsGap.roundToPx()
                    val leftWidth = ((constraints.maxWidth - gap) * 1.25f / 2.25f).roundToInt()
                    val rightWidth = constraints.maxWidth - gap - leftWidth
                    val width = Constraints(minWidth = rightWidth, maxWidth = rightWidth)
                    // Each tile is composed once: the personal tiles at their own height, the left tile
                    // at least as tall as the right column needs, and Ratings in the height left over.
                    val personalTiles = subcompose("personal") {
                        personal.forEach { tile -> tile(Modifier.fillMaxWidth()) }
                    }.map { it.measure(width) }
                    val personalHeight = personalTiles.sumOf { it.height + gap }
                    val rightMin = personalHeight + RatingsTileMinHeight.roundToPx()
                    val leftTile = subcompose("left") { left(Modifier.fillMaxSize()) }.single().measure(
                        Constraints(minWidth = leftWidth, maxWidth = leftWidth, minHeight = maxOf(LeftTileMinHeight.roundToPx(), rightMin)),
                    )
                    // The right column ends flush with the tall tile.
                    val ratingsTile = subcompose("ratings") { ratings(Modifier.fillMaxSize(), true) }.single()
                        .measure(Constraints.fixed(rightWidth, leftTile.height - personalHeight))
                    layout(constraints.maxWidth, leftTile.height) {
                        leftTile.placeRelative(0, 0)
                        val x = leftWidth + gap
                        ratingsTile.placeRelative(x, 0)
                        var y = ratingsTile.height + gap
                        personalTiles.forEach { tile ->
                            tile.placeRelative(x, y)
                            y += tile.height + gap
                        }
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(StatsGap)) {
                    left(Modifier.fillMaxWidth())
                    ratings(Modifier.fillMaxWidth(), false)
                    personal.forEach { tile -> tile(Modifier.fillMaxWidth()) }
                }
            }
        }
        if (catalogFailed) StatusWithRetry("Could not load Trakt seasons") { reload++ }
        if (state.episodes?.isEmpty() == true) {
            Text("No episode data on Trakt", style = redesignText(13.sp, color = RedesignColors.textSecondary))
        }
        if (statsFailed) StatusWithRetry("Some episode stats could not be loaded") { episodeReload++ }
    }
}

@Composable
private fun StatusWithRetry(message: String, retry: () -> Unit) {
    Row(Modifier.semantics { liveRegion = LiveRegionMode.Polite }, verticalAlignment = Alignment.CenterVertically) {
        Text(message, modifier = Modifier.weight(1f), style = redesignText(13.sp, color = RedesignColors.textSecondary))
        TextButton(onClick = retry) { Text("Retry", style = redesignText(13.sp, FontWeight.SemiBold)) }
    }
}

@Composable
private fun TileTitle(text: String) {
    Text(text, style = redesignText(19.sp, FontWeight.Bold, letterSpacing = RedesignTightTracking), maxLines = 1, overflow = TextOverflow.Ellipsis)
}

@Composable
private fun TileSub(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier = modifier, style = redesignText(13.sp, color = RedesignColors.textSecondary, lineHeight = 18.sp))
}

private fun groupedCount(count: Long): String =
    count.toString().reversed().chunked(3).joinToString(",").reversed()

private fun decimal(number: Double): String {
    val tenth = (number * 10).roundToInt()
    return "${tenth / 10}.${kotlin.math.abs(tenth % 10)}"
}

/** Retention: the share of E1's Trakt watchers each episode kept, then the season's fingerprint. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun RetentionTile(
    eyebrow: String,
    keys: List<AudienceEpisodeKey>,
    episodes: Map<AudienceEpisodeKey, TraktAudienceEpisode>,
    stats: Map<AudienceEpisodeKey, TraktAudienceStats>,
    selected: AudienceEpisodeKey?,
    status: String,
    catalogLoading: Boolean,
    pending: Set<AudienceEpisodeKey>,
    phase: State<Float>,
    pick: (AudienceEpisodeKey) -> Unit,
    modifier: Modifier,
) = Column(modifier.boardTile().testTag("detail_stats_retention").padding(horizontal = 20.dp, vertical = 18.dp)) {
    val ratios = retentionRatios(keys, stats)
    val first = keys.firstOrNull { it.episode == 1 }
    val lastIndex = ratios.indexOfLast { it != null }
    val last = keys.getOrNull(lastIndex)
    val baseline = stats[first]?.watchers
    val endCount = stats[last]?.watchers
    val drop = largestAudienceDrop(ratios)
    val ratio = ratios.getOrNull(lastIndex)
    Row(verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f)) {
            BoardKicker(eyebrow)
            TileTitle("Retention")
        }
        AnimatedContent(
            targetState = ratio?.takeIf { last != first }?.let { "${it.roundToInt()}%" },
            transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(160)) },
            label = "retention_total",
        ) { total ->
            Text(total ?: "", style = redesignText(40.sp, FontWeight.Bold, letterSpacing = (-0.03).em, lineHeight = 40.sp))
        }
    }
    val summary = when {
        last == null -> status
        last == first -> "${baseline?.let(::groupedCount)} Trakt watchers on E1"
        else -> buildString {
            append("E${last.episode} had ${(ratio ?: 0.0).roundToInt()}% as many Trakt watchers as E1")
            if (baseline != null && endCount != null) append(" (${groupedCount(endCount)} of ${groupedCount(baseline)})")
            append('.')
            drop?.let { (index, amount) ->
                append(" Biggest drop: E${keys[index].episode} → E${keys[index + 1].episode}, ${decimal(amount)} points.")
            }
        }
    }
    Crossfade(summary, animationSpec = tween(200), label = "retention_summary") { TileSub(it, Modifier.heightIn(min = 36.dp)) }
    Spacer(Modifier.height(14.dp))
    Crossfade(
        targetState = when {
            keys.isEmpty() && catalogLoading -> 0
            last == null && pending.isNotEmpty() -> 0
            keys.isEmpty() -> 1
            else -> 2
        },
        animationSpec = tween(240),
        label = "retention_chart",
    ) { mode ->
        Box(Modifier.fillMaxWidth().height(RetentionChartHeight)) {
            when (mode) {
                0 -> RetentionLoading(phase)
                2 -> RetentionChart(keys, stats, ratios, drop, lastIndex, selected, pick)
            }
        }
    }
    Spacer(Modifier.height(20.dp))
    Fingerprint(keys, episodes, stats, selected, status, catalogLoading, pending, phase, pick)
}

@Composable
private fun RetentionLoading(phase: State<Float>) {
    Canvas(Modifier.fillMaxSize()) {
        val points = listOf(0.08f, 0.39f, 0.5f, 0.57f, 0.63f, 0.67f, 0.71f, 0.74f, 0.76f)
        val trace = Path()
        points.forEachIndexed { index, y ->
            val x = size.width * index / points.lastIndex
            if (index == 0) trace.moveTo(x, size.height * y) else trace.lineTo(x, size.height * y)
        }
        val progress = phase.value
        clipRect(right = size.width * (if (progress < 0) 1f else (progress / .65f).coerceAtMost(1f))) {
            drawPath(trace, retentionColor.copy(alpha = if (progress > .85f) ((1f - progress) / .15f).coerceIn(0f, 1f) * 0.6f else 0.6f),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun RetentionChart(
    keys: List<AudienceEpisodeKey>,
    stats: Map<AudienceEpisodeKey, TraktAudienceStats>,
    ratios: List<Double?>,
    drop: Pair<Int, Double>?,
    lastIndex: Int,
    selected: AudienceEpisodeKey?,
    pick: (AudienceEpisodeKey) -> Unit,
) {
    EpisodeExplorer(listOf(keys), selected, pick, { key ->
        val ratio = ratios[keys.indexOf(key)]
        ratio?.let { "${decimal(it)}% of E1 · ${stats[key]?.watchers?.let(::groupedCount)} watchers" } ?: "Retention unavailable"
    }, modifier = Modifier.fillMaxSize()) { selectedTarget, cell, previewEpisode, hovered ->
        val target = hovered ?: selectedTarget
        Column(Modifier.fillMaxWidth().weight(1f)) {
            Canvas(
                Modifier.fillMaxWidth().weight(1f)
                    .onPointerEvent(PointerEventType.Move) { event ->
                        val index = (event.changes.first().position.x / size.width * keys.size)
                            .toInt().coerceIn(keys.indices)
                        previewEpisode(keys[index])
                    }
                    .onPointerEvent(PointerEventType.Exit) { previewEpisode(null) }
                    .pointerInput(keys, pick) {
                        detectTapGestures { point ->
                            val index = (point.x / size.width * keys.size).toInt().coerceIn(keys.indices)
                            pick(keys[index])
                        }
                    },
            ) {
                val max = ratios.filterNotNull().maxOrNull() ?: 100.0
                val min = ratios.filterNotNull().minOrNull() ?: 0.0
                val span = (max - min).takeIf { it > 0.0 } ?: 1.0
                val top = 6.dp.toPx()
                val bottom = size.height - 4.dp.toPx()
                fun y(ratio: Double): Float = (top + (max - ratio) / span * (bottom - top - 24.dp.toPx())).toFloat()
                fun x(index: Int): Float = (index + 0.5f) * size.width / keys.size
                ratios.zipWithNext().forEachIndexed { i, (a, b) ->
                    if (a != null && b != null) {
                        val x0 = x(i)
                        val x1 = x(i + 1)
                        drawPath(Path().apply {
                            moveTo(x0, size.height)
                            lineTo(x0, y(a))
                            lineTo(x1, y(b))
                            lineTo(x1, size.height)
                            close()
                        }, retentionColor.copy(alpha = 0.14f))
                        drawLine(retentionColor, Offset(x0, y(a)), Offset(x1, y(b)), 2.dp.toPx(), StrokeCap.Round)
                    }
                }
                val dropEnd = drop?.first?.plus(1)
                ratios.forEachIndexed { i, value ->
                    if (value != null && (keys[i] == target || i == dropEnd)) {
                        drawCircle(if (keys[i] == target) Color.White else rewatchColor, 4.dp.toPx(), Offset(x(i), y(value)))
                    }
                }
            }
            RetentionEpisodeAxis(keys, cell)
        }
    }
}

@Composable
private fun RetentionEpisodeAxis(
    keys: List<AudienceEpisodeKey>,
    cell: (AudienceEpisodeKey, String) -> Modifier,
) {
    val style = redesignText(11.sp, color = RedesignColors.textMuted)
    val measurer = rememberTextMeasurer()
    val labels = remember(keys) { keys.map { "E${it.episode}" } }
    val widths = remember(labels, style, measurer) {
        labels.map { measurer.measure(it, style = style, softWrap = false, maxLines = 1).size.width }
    }
    BoxWithConstraints(Modifier.fillMaxWidth().height(18.dp)) {
        val width = constraints.maxWidth
        val gap = with(LocalDensity.current) { 8.dp.roundToPx() }
        val ticks = remember(keys, widths, width, gap) {
            retentionAxisTicks(keys.map { it.episode }, widths, width, gap)
        }
        ticks.forEach { tick ->
            val text = labels[tick.index]
            Text(
                text,
                modifier = Modifier.align(AbsoluteAlignment.CenterLeft)
                    .absoluteOffset { IntOffset(tick.left, 0) }
                    .then(cell(keys[tick.index], text)),
                style = style,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

/** Fingerprint: rating, retention, rewatch and talk per episode, each relative to this season. */
@Composable
private fun Fingerprint(
    keys: List<AudienceEpisodeKey>,
    episodes: Map<AudienceEpisodeKey, TraktAudienceEpisode>,
    stats: Map<AudienceEpisodeKey, TraktAudienceStats>,
    selected: AudienceEpisodeKey?,
    status: String,
    catalogLoading: Boolean,
    pending: Set<AudienceEpisodeKey>,
    phase: State<Float>,
    pick: (AudienceEpisodeKey) -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        Text(
            "Fingerprint" + (selected?.let { " · E${it.episode}" } ?: ""),
            style = redesignText(19.sp, FontWeight.Bold, letterSpacing = RedesignTightTracking),
            modifier = Modifier.weight(1f),
            maxLines = 1,
        )
        Text("Relative to this season", style = redesignText(13.sp, color = RedesignColors.textSecondary), maxLines = 1)
    }
    Spacer(Modifier.height(14.dp))
    if (keys.isEmpty()) {
        if (catalogLoading) RippleGrid(phase, 5, Modifier.fillMaxWidth().height(136.dp), 34, cellHeight = 24)
        else TileSub(status, Modifier.heightIn(min = 136.dp))
        return
    }
    val rawRetention = retentionRatios(keys, stats)
    val rawRewatch = keys.map { key ->
        stats[key]?.let { s -> s.watchers?.takeIf { it > 0 }?.let { w -> s.plays?.let { (it.toDouble() * 100 / w).roundToInt() / 100.0 } } }
    }
    val rawTalk = keys.map { key -> stats[key]?.let { s -> if (s.comments != null && s.votes != null) s.comments.toDouble() + s.votes else null } }
    val today = com.nuvio.app.features.watchprogress.CurrentDateProvider.todayIsoDate()
    val ratings = keys.map { key ->
        episodes[key]?.takeUnless { it.firstAired?.take(10)?.let { date -> date > today } == true }
            ?.let(::validRating)?.let { (it * 10).roundToInt() }
    }
    val values = listOf(ratings, relativeAudienceValues(rawRetention), relativeAudienceValues(rawRewatch), relativeAudienceValues(rawTalk))
    val tints = listOf(fingerprintRatingColor, retentionColor, rewatchColor, talkColor)
    EpisodeExplorer(listOf(keys), selected, pick, { key ->
        val i = keys.indexOf(key)
        listOfNotNull(
            ratings[i]?.let { "Rating $it%" },
            rawRetention[i]?.let { "Retention ${decimal(it)}% of E1" },
            rawRewatch[i]?.let { "${decimal(it)} plays per watcher" },
            rawTalk[i]?.let { "${groupedCount(it.toLong())} comments plus votes" },
        ).joinToString(" · ").ifEmpty { "No stats yet" }
    }) { target, cell, _, hovered ->
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Column(Modifier.width(70.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Spacer(Modifier.height(20.dp))
                listOf("Rating", "Retention", "Rewatch", "Talk").forEach { label ->
                    Box(Modifier.height(24.dp), contentAlignment = Alignment.CenterStart) {
                        Text(label, style = redesignText(12.sp, FontWeight.SemiBold, RedesignColors.textMuted))
                    }
                }
            }
            BoxWithConstraints(Modifier.weight(1f)) {
                // Cells share the width like the design's grid, down to a minimum, then scroll.
                val cellWidth = ((maxWidth.value + 4) / keys.size - 4).toInt().coerceAtLeast(38)
                ScrollableEpisodeGrid(keys.size, keys.indexOf(target), cellWidth) { columns ->
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        columns.forEach { index ->
                            val key = keys[index]
                            Column(cell(key, "E${key.episode}"), horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Box(Modifier.height(20.dp), contentAlignment = Alignment.Center) {
                                    Text("E${key.episode}", style = redesignText(11.5.sp, FontWeight.Bold, RedesignColors.textMuted))
                                }
                                values.forEachIndexed { row, numbers ->
                                    val value = numbers[index]
                                    if (value == null && key in pending && row != 0) {
                                        RippleGrid(phase, 1, Modifier.width(cellWidth.dp).height(24.dp), cellWidth, index + row, cellHeight = 24)
                                    } else MetricTile(
                                        value?.let { "$it%" } ?: "—",
                                        value?.let { tints[row].copy(alpha = fingerprintAlpha(it)) },
                                        key == (hovered ?: target), cellWidth, 24, 11f,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RatingsTile(
    episodes: Map<AudienceEpisodeKey, TraktAudienceEpisode>,
    selected: AudienceEpisodeKey?,
    today: String,
    status: String,
    fill: Boolean,
    loading: Boolean,
    phase: State<Float>,
    pick: (AudienceEpisodeKey) -> Unit,
    modifier: Modifier,
) = Column(modifier.boardTile().testTag("detail_stats_ratings").padding(horizontal = 20.dp, vertical = 18.dp)) {
    BoardKicker("Trakt ratings out of 10")
    TileTitle("Ratings by episode")
    Spacer(Modifier.height(12.dp))
    val rows = remember(episodes) {
        episodes.keys.groupBy { it.season }.toList().sortedBy { seasonSortKey(it.first) }.map { it.second }
    }
    val ratings = remember(episodes, today) {
        episodes.mapValues { (_, episode) ->
            validRating(episode).takeUnless { episode.firstAired?.take(10)?.let { it > today } == true }
        }
    }
    if (rows.isEmpty()) {
        if (loading) RippleGrid(phase, 6, Modifier.then(if (fill) Modifier.weight(1f) else Modifier.height(124.dp)).fillMaxWidth(), 38)
        else TileSub(status, Modifier.heightIn(min = 60.dp))
        return@Column
    }
    EpisodeExplorer(rows, selected, pick, { key ->
        when {
            episodes[key]?.firstAired?.take(10)?.let { it > today } == true -> "Unaired"
            ratings[key] == null -> "Unrated"
            else -> "${decimal(ratings.getValue(key) ?: 0.0)} out of 10"
        }
    }, modifier = Modifier.weight(1f, fill = false)) { target, cell, _, hovered ->
        val gridHeight = Modifier.weight(1f, fill = false).then(if (fill) Modifier else Modifier.heightIn(max = 392.dp))
        val vertical = rememberScrollState()
        val targetRow = rows.indexOfFirst { target in it }
        BoxWithConstraints(gridHeight.fillMaxWidth()) {
            val rowHeight = with(LocalDensity.current) { 28.dp.roundToPx() + 5.dp.roundToPx() }
            val gapPx = with(LocalDensity.current) { 5.dp.roundToPx() }
            val total = rowHeight * rows.size - gapPx
            // Show whole rows only, so a season row is never cut off at the tile's edge.
            val viewport = if (constraints.maxHeight == Constraints.Infinity) total
                else minOf(total, ((constraints.maxHeight + gapPx) / rowHeight).coerceAtLeast(1) * rowHeight - gapPx)
            val visibleRows by remember(rows.size, rowHeight, viewport, vertical) {
                derivedStateOf { visibleGridIndices(rows.size, rowHeight, vertical.value, viewport) }
            }
            LaunchedEffect(target, targetRow, viewport) {
                if (targetRow >= 0) vertical.revealCell(targetRow, rowHeight, viewport)
            }
            // A wheel or trackpad scroll settles on a row boundary, so rows stay whole.
            LaunchedEffect(vertical, rowHeight) {
                snapshotFlow { vertical.isScrollInProgress }.collect { scrolling ->
                    if (!scrolling) {
                        val row = ((vertical.value + rowHeight / 2) / rowHeight) * rowHeight
                        val snap = row.coerceIn(0, vertical.maxValue)
                        if (snap != vertical.value) vertical.animateScrollTo(snap)
                    }
                }
            }
            val longestRow = remember(rows) { rows.maxOf { it.size } }
            Box(Modifier.height(with(LocalDensity.current) { viewport.toDp() })
                .testTag("detail_stats_ratings_grid")
                // Keep the wheel inside while there are rows to scroll; a grid that fits lets the page scroll.
                .then(if (total > viewport) Modifier.containedScroll() else Modifier)
                .verticalScroll(vertical)) {
              Box(Modifier.height(with(LocalDensity.current) { total.toDp() })) {
                Row(
                    Modifier.offset { IntOffset(0, visibleRows.first * rowHeight) },
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Column(Modifier.width(26.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        visibleRows.forEach { index ->
                            val season = rows[index].first().season
                            Box(Modifier.height(28.dp), contentAlignment = Alignment.CenterStart) {
                                Text(if (season == 0) "SP" else "S$season", style = redesignText(12.sp, FontWeight.SemiBold, RedesignColors.textMuted))
                            }
                        }
                    }
                    ScrollableEpisodeGrid(longestRow, rows.getOrNull(targetRow)?.indexOf(target) ?: -1, 38) { columns ->
                        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            visibleRows.forEach { index ->
                                val row = rows[index]
                                Row(Modifier.height(28.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    columns.forEach cells@{ column ->
                                        val key = row.getOrNull(column) ?: return@cells
                                        val rating = ratings[key]
                                        Box(cell(key, key.toString())) {
                                            MetricTile(rating?.let(::decimal) ?: "—", rating?.let(::audienceRatingColor), key == (hovered ?: target), 38, 28, 11.5f)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
              }
            }
        }
    }
    val rated = remember(ratings) {
        ratings.entries.filter { it.value != null }.sortedWith(compareBy({ it.key.season }, { it.key.episode }))
    }
    val best = rated.maxByOrNull { it.value ?: 0.0 }
    val worst = rated.minByOrNull { it.value ?: 0.0 }
    if (best != null && worst != null) {
        Spacer(Modifier.height(10.dp))
        TileSub("Best ${episodeCode(best.key)} · ${decimal(best.value ?: 0.0)}   Lowest ${episodeCode(worst.key)} · ${decimal(worst.value ?: 0.0)}")
    }
}

/** Your <title>: episodes watched, time watched and your rating, from the tracker Rate saves to. */
@Composable
private fun YouTile(meta: MetaDetails, progress: List<SeasonWatchedCount>?, modifier: Modifier) {
    val totals = remember(progress) { progress?.let { detailBoardProgress(it, currentSeason = null) } }
    val minutes = remember(progress) {
        progress?.filter { it.season > 0 }?.ifEmpty { progress }?.sumOf { it.watchedMinutes } ?: 0
    }
    val reference = remember(meta) { meta.ratingReference() }
    val rating by remember(reference) { TrackingRatings.observe(reference) }
        .collectAsState(remember(reference) { TrackingRatings.current(reference) })
    Column(modifier.boardTile().testTag("detail_stats_you").padding(horizontal = 20.dp, vertical = 18.dp)) {
        BoardKicker("You")
        TileTitle("Your ${meta.name}")
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            YouStat(totals?.let { "${it.watched} of ${it.aired}" } ?: "–", "episodes watched")
            if (minutes > 0) YouStat(formatWatchedMinutes(minutes), "watched")
            if (rating.isAvailable) {
                val score = rating.rating
                YouStat(
                    score?.let { if (it % 1.0 == 0.0) "${it.toInt()}/10" else "${decimal(it)}/10" } ?: "Rate",
                    if (score != null) "your rating" else "not rated yet",
                )
            }
        }
        rating.provider?.let { provider ->
            Spacer(Modifier.height(10.dp))
            TileSub("Synced with ${provider.displayName}")
        }
    }
}

@Composable
private fun YouStat(value: String, label: String) {
    Column {
        AnimatedContent(value, transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(140)) }, label = "you_stat") {
            Text(it, style = redesignText(26.sp, FontWeight.Bold, letterSpacing = RedesignTightTracking), maxLines = 1)
        }
        Text(label, style = redesignText(12.sp, color = RedesignColors.textMuted), maxLines = 1)
    }
}

/** Next for you: the episode the board's play button would start, with its Trakt rating. */
@Composable
private fun NextTile(next: StatsNextEpisode, state: SeriesAudienceState, modifier: Modifier) {
    val upNext = next.upNext
    val episode = upNext.episode
    val key = remember(episode, state.mappings) {
        episode?.let { (season, number) ->
            state.mappings.entries.firstOrNull { it.value.season == season && it.value.episode == number }?.key
                ?: AudienceEpisodeKey(season, number)
        }
    }
    val rating = key?.let { state.episodes?.get(it) }?.let(::validRating)
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val border by animateColorAsState(if (hovered) RedesignColors.hairlineFocused else RedesignColors.hairline, tween(180), label = "next_border")
    val onPlay = next.onPlay
    Column(
        modifier.boardTile(border).testTag("detail_stats_next")
            .then(if (onPlay == null) Modifier else Modifier.hoverable(interaction).clickable(interaction, indication = null, onClickLabel = upNext.actionLabel, onClick = onPlay))
            .padding(horizontal = 20.dp, vertical = 18.dp),
    ) {
        BoardKicker("Next for you")
        TileTitle(listOfNotNull(episode?.let { (s, e) -> "S$s E$e" }, upNext.title).joinToString(" · "))
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            NuvioAsyncImage(
                model = upNext.artwork,
                contentDescription = null,
                modifier = Modifier.size(width = 170.dp, height = 96.dp).clip(RoundedCornerShape(12.dp)).background(RedesignColors.fill),
                contentScale = ContentScale.Crop,
            )
            Column {
                rating?.let { NextFact("★ ${decimal(it)}", "Trakt rating") }
                when {
                    upNext.minutesLeft != null -> NextFact("${upNext.minutesLeft} min", "left to watch")
                    upNext.runtimeMinutes != null -> NextFact("${upNext.runtimeMinutes} min", "runtime")
                }
            }
        }
    }
}

@Composable
private fun NextFact(value: String, label: String) {
    Text(value, style = redesignText(18.sp, FontWeight.Bold), maxLines = 1)
    Text(label, style = redesignText(12.sp, color = RedesignColors.textMuted), maxLines = 1, modifier = Modifier.padding(bottom = 4.dp))
}

private fun validRating(episode: TraktAudienceEpisode?): Double? =
    episode?.rating?.takeIf { it in 1.0..10.0 && episode.votes != 0L }

@Composable
private fun MetricTile(label: String, color: Color?, active: Boolean, width: Int, height: Int = 28, fontSize: Float = 11.5f) {
    val shape = RoundedCornerShape(6.dp)
    val border = if (active) RedesignColors.textPrimary else Color.Transparent
    // Dark ink reads on the strong tints; faint ones keep light text.
    val ink = if (color != null && color.alpha > 0.6f) cellInk else RedesignColors.textSecondary
    Box(
        Modifier.width(width.dp).height(height.dp).clip(shape)
            .background(color ?: RedesignColors.fill).border(1.5.dp, border, shape),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = redesignText(fontSize.sp, FontWeight.Bold, ink), maxLines = 1)
    }
}

private fun visibleGridIndices(count: Int, step: Int, offset: Int, viewport: Int): IntRange =
    (offset / step - 1).coerceAtLeast(0)..((offset + viewport) / step + 1).coerceAtMost(count - 1)

private suspend fun androidx.compose.foundation.ScrollState.revealCell(index: Int, step: Int, viewport: Int) {
    val start = index * step
    if (start < value) scrollTo(start)
    else if (start + step > value + viewport) scrollTo(start + step - viewport)
}

@Composable
private fun ScrollableEpisodeGrid(
    count: Int,
    targetIndex: Int,
    cellWidth: Int,
    content: @Composable (IntRange) -> Unit,
) {
    val scroll = rememberScrollState()
    val density = LocalDensity.current
    val step = with(density) { cellWidth.dp.roundToPx() + 4.dp.roundToPx() }
    val totalWidth = with(density) { (count * step - 4.dp.roundToPx()).coerceAtLeast(0).toDp() }
    BoxWithConstraints {
        val viewport = constraints.maxWidth
        val visible by remember(count, step, viewport, scroll) {
            derivedStateOf { visibleGridIndices(count, step, scroll.value, viewport) }
        }
        LaunchedEffect(targetIndex, viewport) {
            if (targetIndex >= 0) scroll.revealCell(targetIndex, step, viewport)
        }
        val gestureRegion = if (totalWidth > maxWidth) Modifier.excludeFromHistoryGestures() else Modifier
        Box(gestureRegion.horizontalScroll(scroll)) {
            Box(Modifier.width(totalWidth)) {
                Box(Modifier.offset { IntOffset(visible.first * step, 0) }) { content(visible) }
            }
        }
    }
}

/**
 * Keyboard and pointer exploration of an episode grid. The hovered or focused episode's details are
 * its accessibility description; the design shows no caption line.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun EpisodeExplorer(
    rows: List<List<AudienceEpisodeKey>>,
    selected: AudienceEpisodeKey?,
    pick: (AudienceEpisodeKey) -> Unit,
    description: (AudienceEpisodeKey) -> String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.(
        target: AudienceEpisodeKey?,
        cell: (AudienceEpisodeKey, String) -> Modifier,
        previewEpisode: (AudienceEpisodeKey?) -> Unit,
        hovered: AudienceEpisodeKey?,
    ) -> Unit,
) {
    val onPick by rememberUpdatedState(pick)
    var focused by remember { mutableStateOf(false) }
    var cursor by remember(rows, selected) { mutableStateOf(selected?.takeIf { key -> rows.any { key in it } } ?: rows.first().first()) }
    var hover by remember(rows) { mutableStateOf<AudienceEpisodeKey?>(null) }
    // Clicking a cell also focuses the grid, so the arrow keys carry on from it.
    val focus = remember { FocusRequester() }
    // The keyboard cursor or selection: what grids scroll into view. Hover only highlights, so
    // a wheel scroll under a resting pointer never pulls the grid back to the hovered cell.
    val highlight = if (focused) cursor else selected
    val preview = hover ?: highlight ?: cursor
    Column(
        modifier.onFocusChanged { focused = it.isFocused }
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                val rowIndex = rows.indexOfFirst { cursor in it }.coerceAtLeast(0)
                val column = rows[rowIndex].indexOf(cursor).coerceAtLeast(0)
                when (event.key) {
                    Key.DirectionLeft -> cursor = rows[rowIndex][(column - 1).coerceAtLeast(0)]
                    Key.DirectionRight -> cursor = rows[rowIndex][(column + 1).coerceAtMost(rows[rowIndex].lastIndex)]
                    Key.DirectionUp, Key.DirectionDown -> {
                        val offset = if (event.key == Key.DirectionUp) -1 else 1
                        val row = rows[(rowIndex + offset).coerceIn(rows.indices)]
                        cursor = row[column.coerceAtMost(row.lastIndex)]
                    }
                    Key.Enter, Key.Spacebar -> pick(cursor)
                    else -> return@onKeyEvent false
                }
                hover = null
                true
            }
            .semantics {
                contentDescription = "$preview · ${description(preview)}"
                stateDescription = if (preview == selected) "Selected" else "Preview"
            }
            .focusRequester(focus)
            .focusable(),
    ) {
        content(highlight, { key, label ->
            Modifier.onPointerEvent(PointerEventType.Enter) { hover = key }
                .onPointerEvent(PointerEventType.Exit) { if (hover == key) hover = null }
                .pointerInput(key) { detectTapGestures { cursor = key; onPick(key); runCatching { focus.requestFocus() } } }
                .semantics {
                    contentDescription = "$label · ${description(key)}"
                    this.selected = key == selected
                    onClick { cursor = key; pick(key); true }
                }
        }, { hover = it }, hover)
    }
}

@Composable
private fun rememberAudienceLoadingPhase(active: Boolean): State<Float> {
    val phase = remember { mutableFloatStateOf(-1f) }
    val screenActive = LocalScreenActive.current
    LaunchedEffect(active, screenActive) {
        phase.floatValue = -1f
        val scale = coroutineContext[MotionDurationScale]
        if (!active || !screenActive || scale?.scaleFactor == 0f) return@LaunchedEffect
        val start = withFrameNanos { it }
        while (isActive) {
            withFrameNanos { now ->
                val duration = 2_000_000_000.0 * (scale?.scaleFactor ?: 1f)
                phase.floatValue = (((now - start) / duration) % 1.0).toFloat()
            }
        }
    }
    return phase
}

@Composable
private fun RippleGrid(
    phase: State<Float>,
    maxRows: Int,
    modifier: Modifier,
    cellWidth: Int,
    phaseOffset: Int = 0,
    cellHeight: Int = 28,
) {
    val base = RedesignColors.fill
    val light = Color.White.copy(alpha = 0.12f)
    Canvas(modifier) {
        val tile = cellWidth.dp.toPx()
        val height = cellHeight.dp.toPx()
        val gap = 4.dp.toPx()
        val columns = ((size.width + gap) / (tile + gap)).toInt()
        val rows = ((size.height + gap) / (height + gap)).toInt().coerceAtMost(maxRows)
        val progress = phase.value
        for (row in 0 until rows) for (column in 0 until columns) {
            val delay = ((row + column + phaseOffset) % 12) * .045f
            val pulse = if (progress < 0) 0f else (1f - kotlin.math.abs(progress - delay - .14f) / .14f).coerceIn(0f, 1f)
            val position = Offset(column * (tile + gap), row * (height + gap))
            val box = androidx.compose.ui.geometry.Size(tile, height)
            val corner = androidx.compose.ui.geometry.CornerRadius(6.dp.toPx())
            drawRoundRect(base, position, box, corner)
            if (pulse > 0) drawRoundRect(light, position, box, corner, alpha = pulse)
        }
    }
}
