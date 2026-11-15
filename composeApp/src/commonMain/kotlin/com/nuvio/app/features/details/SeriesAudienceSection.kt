package com.nuvio.app.features.details

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.nuvio.app.core.ui.ScreenActivityEffect
import com.nuvio.app.core.ui.LocalScreenActive
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

private val retentionColor = Color(0xff42d9b2)
private val rewatchColor = Color(0xffffa869)
private val talkColor = Color(0xff71baff)

private fun seasonLabel(season: Int): String = if (season == 0) "Specials" else "Season $season"

internal class SeriesAudienceState {
    var show by mutableStateOf<Int?>(null)
    var episodes by mutableStateOf<Map<AudienceEpisodeKey, TraktAudienceEpisode>?>(null)
    var mappings by mutableStateOf(emptyMap<AudienceEpisodeKey, MetaVideo>())
    var mappedVideos: List<MetaVideo>? = null
    var showStats by mutableStateOf<TraktAudienceStats?>(null)
    var summaryLoaded by mutableStateOf(false)
    var noMatch by mutableStateOf(false)
    var requestedEpisode by mutableStateOf<AudienceEpisodeKey?>(null)
    var independentSeason by mutableStateOf<Int?>(null)
    var lastDesktopSeason: Int? = null
    var syncedDesktopSeason: Int? = null
    val seasonStats = mutableStateMapOf<Int, Map<AudienceEpisodeKey, TraktAudienceStats?>>()
}

@Composable
internal fun SeriesAudienceSection(
    meta: MetaDetails,
    season: Int,
    progress: List<SeasonWatchedCount>?,
    state: SeriesAudienceState,
    onSeasonSelected: (Int) -> Unit,
    onOpenSeason: (Int) -> Unit,
) {
    var nearViewport by remember(meta.id) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(22.dp)) {
        ProgressCard(progress, season, onOpenSeason)
        if (TraktAudienceRepository.available && !state.noMatch) {
            Box(Modifier.fillMaxWidth().onGloballyPositioned { coordinates ->
                val top = coordinates.positionInRoot().y
                val viewportHeight = coordinates.findRootCoordinates().size.height
                nearViewport = top < viewportHeight + 240 && top + coordinates.size.height > -240
            }) {
                AudienceCards(meta, season, nearViewport, onSeasonSelected, state)
            }
        }
    }
}

@Composable
private fun ProgressCard(counts: List<SeasonWatchedCount>?, season: Int, onOpenSeason: (Int) -> Unit) {
    if (counts?.isEmpty() == true) return
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Your progress", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground)
        Panel {
            val selected = counts?.firstOrNull { it.season == season }
            BoxWithConstraints {
                val wide = maxWidth >= 670.dp
                val summary: @Composable () -> Unit = {
                    Column(Modifier.widthIn(min = 140.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(selected?.let { "${it.watched}/${it.aired}" } ?: "—", style = MaterialTheme.typography.headlineMedium)
                        Text(seasonLabel(season), style = MaterialTheme.typography.labelLarge)
                        Text(if (counts == null) "Loading watch history…" else selected?.let { "${it.watched} of ${it.aired} watched" }
                            ?: "No aired episodes", style = MaterialTheme.typography.bodySmall)
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
                                            .background(if (active) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                                            .semantics { this.selected = active }
                                            .clickable(onClickLabel = "Open ${seasonLabel(count.season)}") { onOpenSeason(count.season) }
                                            .padding(horizontal = 8.dp, vertical = 9.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(if (count.season == 0) "Specials" else "S${count.season}", style = MaterialTheme.typography.labelMedium)
                                        LinearProgressIndicator(
                                            progress = { count.watched.toFloat() / count.aired },
                                            modifier = Modifier.weight(1f).height(5.dp),
                                            trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.22f),
                                        )
                                        Text("${count.watched}/${count.aired}", style = MaterialTheme.typography.labelMedium)
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
) {
    val show = state.show
    val episodes = state.episodes.orEmpty()
    val mappings = state.mappings
    var catalogFailed by remember(meta.id) { mutableStateOf(false) }
    var summaryFailed by remember(meta.id) { mutableStateOf(false) }
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
    ScreenActivityEffect(show, nearViewport, reload) { active ->
        val id = show ?: return@ScreenActivityEffect
        if (!active || !nearViewport || state.summaryLoaded) return@ScreenActivityEffect
        summaryFailed = false
        try {
            state.showStats = TraktAudienceRepository.showStats(id)
            state.summaryLoaded = true
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            summaryFailed = true
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

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.height(48.dp), contentAlignment = Alignment.CenterStart) {
            Text(
                season?.let { "${seasonLabel(it)} by the numbers" } ?: "Season by the numbers",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
        Box(Modifier.height(24.dp)) {
            state.showStats?.let { totals ->
                val watchers = totals.watchers
                val plays = totals.plays
                if (watchers != null && watchers > 0 && plays != null) {
                    Text(
                        "Whole show · ${groupedCount(plays)} plays · ${groupedCount(watchers)} Trakt watchers · ${decimal(plays.toDouble() / watchers)} plays per watcher",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                }
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
        val left: @Composable () -> Unit = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                RetentionCard(seasonKeys, stats, selected, status, catalogLoading || (statsLoading && pending.isNotEmpty()), phase, pick)
                FingerprintCard(seasonKeys, episodes, stats, selected, status, catalogLoading, if (statsLoading) pending else emptySet(), phase, pick)
            }
        }
        val right: @Composable (Boolean) -> Unit = { wide -> RatingsCard(episodes, selected, today, status, wide, catalogLoading, phase, pick) }
        BoxWithConstraints {
            if (maxWidth >= 880.dp) {
                Layout(content = {
                    Box { left() }
                    Box(propagateMinConstraints = true) { right(true) }
                }) { children, constraints ->
                    val gap = 12.dp.roundToPx()
                    val cardWidth = (constraints.maxWidth - gap) / 2
                    val leftStack = children[0].measure(Constraints.fixedWidth(cardWidth))
                    val ratings = children[1].measure(
                        Constraints.fixed(constraints.maxWidth - gap - cardWidth, leftStack.height),
                    )
                    layout(constraints.maxWidth, leftStack.height) {
                        leftStack.placeRelative(0, 0)
                        ratings.placeRelative(cardWidth + gap, 0)
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { left(); right(false) }
            }
        }
        if (catalogFailed || summaryFailed) {
            StatusWithRetry(if (catalogFailed) "Could not load Trakt seasons" else "Could not load whole-show totals") { reload++ }
        }
        if (state.episodes?.isEmpty() == true) {
            Text("No episode data on Trakt", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onBackground)
        }
        if (statsFailed) StatusWithRetry("Some episode stats could not be loaded") { episodeReload++ }
    }
}

@Composable
private fun StatusWithRetry(message: String, retry: () -> Unit) {
    Row(Modifier.semantics { liveRegion = LiveRegionMode.Polite }, verticalAlignment = Alignment.CenterVertically) {
        Text(message, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onBackground)
        TextButton(onClick = retry) { Text("Retry") }
    }
}

@Composable
private fun Panel(
    fixedHeight: Int? = null,
    spacing: Dp = 10.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().then(fixedHeight?.let { Modifier.height(it.dp) } ?: Modifier),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.93f),
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(spacing), content = content)
    }
}

private fun groupedCount(count: Long): String =
    count.toString().reversed().chunked(3).joinToString(",").reversed()

private fun decimal(number: Double): String {
    val tenth = (number * 10).roundToInt()
    return "${tenth / 10}.${kotlin.math.abs(tenth % 10)}"
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun RetentionCard(
    keys: List<AudienceEpisodeKey>,
    stats: Map<AudienceEpisodeKey, TraktAudienceStats>,
    selected: AudienceEpisodeKey?,
    status: String,
    loading: Boolean,
    phase: State<Float>,
    pick: (AudienceEpisodeKey) -> Unit,
) = Panel(fixedHeight = 340, spacing = 6.dp) {
    Text("Retention", style = MaterialTheme.typography.titleMedium)
    val ratios = retentionRatios(keys, stats)
    val first = keys.firstOrNull { it.episode == 1 }
    val lastIndex = ratios.indexOfLast { it != null }
    val last = keys.getOrNull(lastIndex)
    val baseline = stats[first]?.watchers
    val endCount = stats[last]?.watchers
    Column(Modifier.heightIn(min = 72.dp)) {
        when {
            last == null -> Text(status, style = MaterialTheme.typography.bodySmall)
            last == first -> Text("${baseline?.let(::groupedCount)} Trakt watchers on E1")
            else -> {
                val ratio = ratios[lastIndex] ?: 0.0
                Text("${ratio.roundToInt()}%", style = MaterialTheme.typography.headlineMedium)
                Text("E${last.episode} had ${ratio.roundToInt()}% as many Trakt watchers as E1", style = MaterialTheme.typography.bodySmall)
                Text("${baseline?.let(::groupedCount)} on E1 · ${endCount?.let(::groupedCount)} on E${last.episode}", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
    val drop = largestAudienceDrop(ratios)
    Text(
        drop?.let { (index, amount) ->
            "Biggest drop · E${keys[index].episode} to E${keys[index + 1].episode} · ${decimal(amount)} pp"
        } ?: "E1 reference · 100%",
        color = if (drop != null) rewatchColor else MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.labelSmall,
    )
    if (loading && last == null) {
        Canvas(Modifier.fillMaxWidth().height(114.dp)) {
            val points = listOf(0.2f, 0.39f, 0.34f, 0.53f, 0.59f, 0.71f, 0.68f)
            val trace = Path()
            points.forEachIndexed { index, y ->
                val x = size.width * index / points.lastIndex
                if (index == 0) trace.moveTo(x, size.height * y) else trace.lineTo(x, size.height * y)
            }
            drawLine(Color.Gray.copy(alpha = 0.24f), Offset(0f, size.height * .25f), Offset(size.width, size.height * .25f))
            val progress = phase.value
            clipRect(right = size.width * (if (progress < 0) 1f else (progress / .65f).coerceAtMost(1f))) {
                drawPath(trace, retentionColor.copy(alpha = if (progress > .85f) ((1f - progress) / .15f).coerceIn(0f, 1f) else 1f),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
            }
        }
    } else if (keys.isEmpty()) {
        Spacer(Modifier.height(146.dp))
    } else {
        EpisodeExplorer(listOf(keys), selected, pick, { key ->
            val ratio = ratios[keys.indexOf(key)]
            ratio?.let { "${decimal(it)}% of E1 · ${stats[key]?.watchers?.let(::groupedCount)} watchers" } ?: "Retention unavailable"
        }, modifier = Modifier.weight(1f)) { target, cell, previewEpisode ->
            val track = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
            val marker = MaterialTheme.colorScheme.onSurface
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
                    val max = maxOf(100.0, ratios.filterNotNull().maxOrNull() ?: 100.0)
                    val min = minOf(100.0, ratios.filterNotNull().minOrNull() ?: 100.0)
                    fun y(ratio: Double): Float = (size.height * (0.1 + (max + 1 - ratio) / (max - min + 2) * 0.8)).toFloat()
                    fun x(index: Int): Float = (index + 0.5f) * size.width / keys.size
                    drawLine(track, Offset(0f, y(100.0)), Offset(size.width, y(100.0)))
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
                            }, retentionColor.copy(alpha = 0.17f))
                            drawLine(retentionColor, Offset(x0, y(a)), Offset(x1, y(b)), 3.dp.toPx(), StrokeCap.Round)
                        }
                    }
                    val cursorIndex = keys.indexOf(target)
                    if (cursorIndex >= 0) {
                        drawLine(track, Offset(x(cursorIndex), 0f), Offset(x(cursorIndex), size.height))
                    }
                    ratios.forEachIndexed { i, value ->
                        if (value != null && (keys[i] == target || i == 0 || i == lastIndex || i == drop?.first?.plus(1))) {
                            drawCircle(if (keys[i] == target) marker else if (i == drop?.first?.plus(1)) rewatchColor else retentionColor,
                                4.dp.toPx(), Offset(x(i), y(value)))
                        }
                    }
                }
                RetentionEpisodeAxis(keys, cell)
            }
        }
    }
}

@Composable
private fun RetentionEpisodeAxis(
    keys: List<AudienceEpisodeKey>,
    cell: (AudienceEpisodeKey, String) -> Modifier,
) {
    val style = MaterialTheme.typography.labelSmall
    val measurer = rememberTextMeasurer()
    val labels = remember(keys) { keys.map { "E${it.episode}" } }
    val widths = remember(labels, style, measurer) {
        labels.map { measurer.measure(it, style = style, softWrap = false, maxLines = 1).size.width }
    }
    BoxWithConstraints(Modifier.fillMaxWidth().height(22.dp)) {
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

@Composable
private fun FingerprintCard(
    keys: List<AudienceEpisodeKey>,
    episodes: Map<AudienceEpisodeKey, TraktAudienceEpisode>,
    stats: Map<AudienceEpisodeKey, TraktAudienceStats>,
    selected: AudienceEpisodeKey?,
    status: String,
    catalogLoading: Boolean,
    pending: Set<AudienceEpisodeKey>,
    phase: State<Float>,
    pick: (AudienceEpisodeKey) -> Unit,
) = Panel(fixedHeight = 326) {
    Text("Fingerprint" + (selected?.let { " · E${it.episode}" } ?: ""), style = MaterialTheme.typography.titleMedium)
    Text("Retention, Rewatch and Talk are relative to this season.", style = MaterialTheme.typography.bodySmall)
    if (keys.isEmpty()) {
        if (catalogLoading) RippleGrid(phase, 4, Modifier.fillMaxWidth().height(128.dp), 39)
        else Text(status, Modifier.heightIn(min = 155.dp))
        return@Panel
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
    EpisodeExplorer(listOf(keys), selected, pick, { key ->
        val i = keys.indexOf(key)
        listOfNotNull(
            ratings[i]?.let { "Rating $it%" },
            rawRetention[i]?.let { "Retention ${decimal(it)}% of E1" },
            rawRewatch[i]?.let { "${decimal(it)} plays per watcher" },
            rawTalk[i]?.let { "${groupedCount(it.toLong())} comments plus votes" },
        ).joinToString(" · ").ifEmpty { "No stats yet" }
    }) { target, cell, _ ->
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Column(Modifier.width(74.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Spacer(Modifier.height(24.dp))
                listOf("Rating", "Retention", "Rewatch", "Talk").forEach { label ->
                    Box(Modifier.height(28.dp), contentAlignment = Alignment.CenterStart) {
                        Text(label, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            ScrollableEpisodeGrid(keys.size, keys.indexOf(target), 39) { columns ->
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    columns.forEach { index ->
                        val key = keys[index]
                        Column(cell(key, "E${key.episode}"), horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Box(Modifier.height(24.dp), contentAlignment = Alignment.Center) {
                                Text("${key.episode}", style = MaterialTheme.typography.labelSmall)
                            }
                            values.forEachIndexed { row, numbers ->
                                val value = numbers[index]
                                val tint = when (row) {
                                    0 -> ratingColor((value ?: 0) / 10.0)
                                    1 -> retentionColor
                                    2 -> rewatchColor
                                    else -> talkColor
                                }
                                if (value == null && key in pending && row != 0) {
                                    RippleGrid(phase, 1, Modifier.width(39.dp).height(28.dp), 39, index + row)
                                } else MetricTile(value?.let { "$it%" } ?: "—", value?.let {
                                    tint.copy(alpha = if (row == 0) 1f else 0.32f + it / 148f)
                                }, key == target, 39)
                            }
                        }
                    }
                }
            }
        }
    }
    stats[selected]?.watchers?.let { Text("E${selected?.episode} · ${groupedCount(it)} Trakt watchers", style = MaterialTheme.typography.bodySmall) }
}

@Composable
private fun RatingsCard(
    episodes: Map<AudienceEpisodeKey, TraktAudienceEpisode>,
    selected: AudienceEpisodeKey?,
    today: String,
    status: String,
    wide: Boolean,
    loading: Boolean,
    phase: State<Float>,
    pick: (AudienceEpisodeKey) -> Unit,
) = Panel(fixedHeight = if (wide) null else 540) {
    Text("Ratings by episode", style = MaterialTheme.typography.titleMedium)
    Text("Trakt ratings out of 10", style = MaterialTheme.typography.bodySmall)
    val rows = remember(episodes) {
        episodes.keys.groupBy { it.season }.toList().sortedBy { seasonSortKey(it.first) }.map { it.second }
    }
    val ratings = remember(episodes, today) {
        episodes.mapValues { (_, episode) ->
            validRating(episode).takeUnless { episode.firstAired?.take(10)?.let { it > today } == true }
        }
    }
    if (rows.isEmpty()) {
        if (loading) RippleGrid(phase, 20, Modifier.weight(1f).fillMaxWidth(), 32)
        else Text(status, Modifier.heightIn(min = 142.dp))
        Spacer(Modifier.height(48.dp))
        return@Panel
    }
    EpisodeExplorer(rows, selected, pick, { key ->
        when {
            episodes[key]?.firstAired?.take(10)?.let { it > today } == true -> "Unaired"
            ratings[key] == null -> "Unrated"
            else -> "${decimal(ratings.getValue(key) ?: 0.0)} out of 10"
        }
    }, modifier = Modifier.weight(1f, fill = false)) { target, cell, _ ->
        val gridHeight = Modifier.weight(1f, fill = false).then(if (wide) Modifier else Modifier.heightIn(max = 392.dp))
        val vertical = rememberScrollState()
        val targetRow = rows.indexOfFirst { target in it }
        BoxWithConstraints(gridHeight.fillMaxWidth()) {
            val rowHeight = with(LocalDensity.current) { 28.dp.roundToPx() + 4.dp.roundToPx() }
            val viewport = constraints.maxHeight
            val visibleRows by remember(rows.size, rowHeight, viewport, vertical) {
                derivedStateOf { visibleGridIndices(rows.size, rowHeight, vertical.value, viewport) }
            }
            LaunchedEffect(target, targetRow, viewport) {
                if (targetRow >= 0) vertical.revealCell(targetRow, rowHeight, viewport)
            }
            val longestRow = remember(rows) { rows.maxOf { it.size } }
            val labelWidth = 34.dp
            Box(Modifier.verticalScroll(vertical).height(with(LocalDensity.current) {
                (rowHeight * rows.size - 4.dp.roundToPx()).toDp()
            })) {
                Row(
                    Modifier.offset { IntOffset(0, visibleRows.first * rowHeight) },
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Column(Modifier.width(labelWidth), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        visibleRows.forEach { index ->
                            val season = rows[index].first().season
                            Box(Modifier.height(28.dp), contentAlignment = Alignment.CenterStart) {
                                Text(if (season == 0) "SP" else "S$season", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                    ScrollableEpisodeGrid(longestRow, rows.getOrNull(targetRow)?.indexOf(target) ?: -1, 32) { columns ->
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            visibleRows.forEach { index ->
                                val row = rows[index]
                                Row(Modifier.height(28.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    columns.forEach cells@{ column ->
                                        val key = row.getOrNull(column) ?: return@cells
                                        val rating = ratings[key]
                                        Box(cell(key, key.toString())) {
                                            MetricTile(rating?.let(::decimal) ?: "—", rating?.let(::ratingColor), key == target, 32)
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
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Text("6", style = MaterialTheme.typography.labelSmall)
        (60..90 step 5).forEach { Text(" ", Modifier.width(13.dp).height(6.dp).background(ratingColor(it / 10.0))) }
        Text("9+", style = MaterialTheme.typography.labelSmall)
    }
    val rated = remember(ratings) {
        ratings.entries.filter { it.value != null }.sortedWith(compareBy({ it.key.season }, { it.key.episode }))
    }
    val best = rated.maxByOrNull { it.value ?: 0.0 }
    val worst = rated.minByOrNull { it.value ?: 0.0 }
    if (best != null && worst != null) {
        Text("Best · ${best.key} ${decimal(best.value ?: 0.0)}    Worst · ${worst.key} ${decimal(worst.value ?: 0.0)}",
            style = MaterialTheme.typography.bodySmall)
    }
}

private fun validRating(episode: TraktAudienceEpisode?): Double? =
    episode?.rating?.takeIf { it in 1.0..10.0 && episode.votes != 0L }

private fun ratingColor(rating: Double): Color = when {
    rating < 7.2 -> lerp(rewatchColor, Color(0xffffeb85), ((rating - 6) / 1.2).toFloat().coerceIn(0f, 1f))
    rating < 8.2 -> lerp(Color(0xffffeb85), retentionColor, ((rating - 7.2) / 1.0).toFloat().coerceIn(0f, 1f))
    else -> lerp(retentionColor, Color(0xffd4fff5), ((rating - 8.2) / 0.8).toFloat().coerceIn(0f, 1f))
}

@Composable
private fun MetricTile(label: String, color: Color?, active: Boolean, width: Int) {
    val shape = RoundedCornerShape(5.dp)
    val border = if (active) MaterialTheme.colorScheme.onSurface else Color.Transparent
    Box(
        Modifier.width(width.dp).height(28.dp).clip(shape)
            .background(color ?: MaterialTheme.colorScheme.surfaceContainerHighest).border(1.dp, border, shape),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall,
            color = if (color != null && color.alpha > 0.72f) Color(0xff132528) else MaterialTheme.colorScheme.onSurface)
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

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun EpisodeExplorer(
    rows: List<List<AudienceEpisodeKey>>,
    selected: AudienceEpisodeKey?,
    pick: (AudienceEpisodeKey) -> Unit,
    description: (AudienceEpisodeKey) -> String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.(AudienceEpisodeKey?, (AudienceEpisodeKey, String) -> Modifier, (AudienceEpisodeKey?) -> Unit) -> Unit,
) {
    val onPick by rememberUpdatedState(pick)
    var focused by remember { mutableStateOf(false) }
    var cursor by remember(rows, selected) { mutableStateOf(selected?.takeIf { key -> rows.any { key in it } } ?: rows.first().first()) }
    var hover by remember(rows) { mutableStateOf<AudienceEpisodeKey?>(null) }
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
            .focusable(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        content(highlight, { key, label ->
            Modifier.onPointerEvent(PointerEventType.Enter) { hover = key }
                .onPointerEvent(PointerEventType.Exit) { if (hover == key) hover = null }
                .pointerInput(key) { detectTapGestures { cursor = key; onPick(key) } }
                .semantics {
                    contentDescription = "$label · ${description(key)}"
                    this.selected = key == selected
                    onClick { cursor = key; pick(key); true }
                }
        }, { hover = it })
        Text("$preview · ${description(preview)}", style = MaterialTheme.typography.bodySmall,
            maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            modifier = Modifier.height(38.dp))
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
) {
    val base = MaterialTheme.colorScheme.surfaceContainerHighest
    val light = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.20f)
    Canvas(modifier) {
        val tile = cellWidth.dp.toPx()
        val height = 28.dp.toPx()
        val gap = 4.dp.toPx()
        val columns = ((size.width + gap) / (tile + gap)).toInt()
        val rows = ((size.height + gap) / (height + gap)).toInt().coerceAtMost(maxRows)
        val progress = phase.value
        for (row in 0 until rows) for (column in 0 until columns) {
            val delay = ((row + column + phaseOffset) % 12) * .045f
            val pulse = if (progress < 0) 0f else (1f - kotlin.math.abs(progress - delay - .14f) / .14f).coerceIn(0f, 1f)
            val position = Offset(column * (tile + gap), row * (height + gap))
            val box = androidx.compose.ui.geometry.Size(tile, height)
            val corner = androidx.compose.ui.geometry.CornerRadius(5.dp.toPx())
            drawRoundRect(base, position, box, corner)
            if (pulse > 0) drawRoundRect(light, position, box, corner, alpha = pulse)
        }
    }
}
