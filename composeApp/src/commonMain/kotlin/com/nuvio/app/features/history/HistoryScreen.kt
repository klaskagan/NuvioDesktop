package com.nuvio.app.features.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.DeleteOutline
import com.nuvio.app.features.tracking.TrackingHistoryRemover
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nuvio.app.core.format.formatLocalTime
import com.nuvio.app.core.time.EpisodeReleaseDatePlatform
import com.nuvio.app.core.ui.*
import com.nuvio.app.features.calendar.*
import com.nuvio.app.features.details.MetaDetails
import com.nuvio.app.features.details.MetaDetailsRepository
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.home.PosterShape
import com.nuvio.app.features.home.components.artworkFixedGridColumns
import com.nuvio.app.features.home.components.calendarMovieLabel
import com.nuvio.app.features.tracking.TrackingHistoryEvent
import com.nuvio.app.features.tracking.TrackingHistorySource
import com.nuvio.app.features.tracking.TrackingMediaKind
import com.nuvio.app.features.tracking.TrackingMediaReference
import com.nuvio.app.features.watchprogress.findWatchProgressEpisode
import com.nuvio.app.isDesktop
import com.nuvio.app.features.home.components.HomeBoardSideColumnMinWidth
import com.nuvio.app.features.trakt.calendarDatePlusDays
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.graphicsLayer
import org.jetbrains.compose.resources.pluralStringResource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource

internal val LocalHistorySource = staticCompositionLocalOf<TrackingHistorySource?> { null }

@Composable
internal fun HistoryScreen(topChromePadding: Dp?, onPosterClick: ((MetaPreview) -> Unit)?) {
    val source = LocalHistorySource.current ?: return
    key(source) {
        HistoryAgenda(source, topChromePadding, onPosterClick)
    }
}

@Composable
private fun HistoryAgenda(
    source: TrackingHistorySource,
    topChromePadding: Dp?,
    onPosterClick: ((MetaPreview) -> Unit)?,
) {
    var filter by rememberSaveable { mutableStateOf(CalendarFilter.All) }
    val timeline = remember(source, filter) { HistoryTimeline(source, when (filter) {
        CalendarFilter.All -> null
        CalendarFilter.Movies -> TrackingMediaKind.MOVIE
        CalendarFilter.Shows -> TrackingMediaKind.SHOW
    }) }
    val scope = rememberCoroutineScope()
    var actionCard by remember(timeline) { mutableStateOf<HistoryCard?>(null) }
    var actionAnchor by remember(timeline) { mutableStateOf<PosterZoomAnchor?>(null) }
    val active = LocalScreenActive.current
    val today = calendarToday()
    val list = rememberLazyListState()
    val globalStyle = rememberPosterCardStyleUiState()
    val metadata = remember { mutableStateMapOf<Pair<String, String>, MetaDetails?>() }
    val artworkRequests = remember { Semaphore(4) }
    val unknownTitle = stringResource(Res.string.history_unknown_title)
    var pickerDate by remember { mutableStateOf<String?>(null) }
    var seekDate by remember { mutableStateOf<String?>(null) }
    var request by remember(timeline) { mutableStateOf<suspend () -> Unit>({ timeline.load() }) }
    LaunchedEffect(active, timeline, request) {
        if (active) request()
    }
    LaunchedEffect(active) {
        if (!active) {
            actionCard = null
            actionAnchor = null
            seekDate = null
            request = { timeline.load() }
        }
    }

    val liveGroups = remember(timeline.events, filter) {
        timeline.events.filter { event ->
            when (filter) {
                CalendarFilter.All -> true
                CalendarFilter.Shows -> event.media.kind != TrackingMediaKind.MOVIE
                CalendarFilter.Movies -> event.media.kind == TrackingMediaKind.MOVIE
            }
        }.groupBy { historyLocalDate(it) ?: today }
    }
    // A refetch (filter change, profile return) keeps the last loaded days on screen, dimmed and
    // inert, until the new timeline answers, instead of flashing an empty page.
    var retained by remember { mutableStateOf<Map<String, List<TrackingHistoryEvent>>>(emptyMap()) }
    LaunchedEffect(timeline.loaded, liveGroups) { if (timeline.loaded) retained = liveGroups }
    val stale = isDesktop && !timeline.loaded && retained.isNotEmpty()
    val groups = if (stale) retained else liveGroups
    val listAlpha by animateFloatAsState(if (stale) 0.45f else 1f, tween(220), label = "history_stale")
    val style = calendarAgendaPosterStyle(globalStyle, globalStyle.historyLandscapeModeEnabled)
    // Desktop redesign: History sits on the shell's ambient backdrop (steered by the newest watch in
    // view) with Home's margins, a board of the latest watch and the week, then one frosted tile
    // per day. Mobile keeps the original agenda.
    val redesign = isDesktop
    val backdropController = LocalBackdropFocusController.current.takeIf { isDesktop }
    CompositionLocalProvider(
        LocalPosterCardStyleOverride provides style,
        LocalContentColor provides MaterialTheme.colorScheme.onBackground,
    ) {
      RedesignTypography(enabled = isDesktop) {
       BoxWithConstraints(
            Modifier.fillMaxSize()
                .then(if (backdropController == null) Modifier.background(MaterialTheme.colorScheme.background) else Modifier),
       ) {
        val gutter = if (isDesktop) desktopPageHorizontalPaddingForWidth(maxWidth.value) else 24.dp
        val topPadding = if (isDesktop) ((topChromePadding ?: 16.dp) - 4.dp).coerceAtLeast(8.dp)
            else ((topChromePadding ?: 16.dp) - 20.dp).coerceAtLeast(8.dp)
        BoxWithConstraints(
            Modifier.fillMaxSize().padding(top = topPadding).padding(horizontal = gutter).testTag("history-screen"),
        ) {
            val landscape = style.catalogLandscapeModeEnabled
            val minCardWidth = minOf(maxWidth, if (landscape) 224.dp else 142.dp)
            val gridWidth = (maxWidth - if (redesign) HistoryTilePadding * 2 else 0.dp).coerceAtLeast(minCardWidth)
            val widthPx = with(LocalDensity.current) { gridWidth.roundToPx() }
            val columns = artworkFixedGridColumns(widthPx, 0.dp, minCardWidth)
            // Desktop rows fill the tile: the cards share the spare width instead of leaving a gap.
            val cardWidth = if (redesign) ((gridWidth - HistoryCardSpacing * (columns - 1)) / columns).coerceAtLeast(minCardWidth) else minCardWidth
            val wideBoard = maxWidth >= HomeBoardSideColumnMinWidth
            val rowsByDate = remember(groups, columns) { groups.mapValues { (_, events) -> events.chunked(columns) } }
            val cardRows = remember(rowsByDate, unknownTitle, metadata.toMap()) {
                rowsByDate.mapValues { (_, rows) ->
                    rows.map { row -> row.map { event -> event.toHistoryCard(unknownTitle, metadata) } }
                }
            }
            val metadataTargets = remember(rowsByDate) {
                rowsByDate.flatMap { (date, rows) ->
                    rows.map { row ->
                        historyRowKey(date, row.first().id) to row.mapNotNull { event ->
                            val episode = event.media.episode
                            if (episode == null || !event.episodeScreenshot.isNullOrBlank() && !episode.title.isNullOrBlank()) {
                                null
                            } else {
                                event.media.historyPreview(event.media.title.orEmpty())?.let { it.type to it.id }
                            }
                        }
                    }
                }.toMap()
            }
            LaunchedEffect(active, metadataTargets) {
                if (!active) return@LaunchedEffect
                snapshotFlow { list.layoutInfo.visibleItemsInfo.flatMap { metadataTargets[it.key.toString()].orEmpty() }.distinct() }
                    .collectLatest { targets ->
                        coroutineScope {
                            targets.filterNot(metadata::containsKey).forEach { (type, id) ->
                                launch {
                                    metadata[type to id] = artworkRequests.withPermit {
                                        try {
                                            MetaDetailsRepository.fetch(type, id)
                                        } catch (error: CancellationException) {
                                            throw error
                                        } catch (_: Exception) {
                                            null
                                        }
                                    }
                                }
                            }
                        }
                    }
            }
            // Desktop leads with the board item; each day is one heading item plus its rows.
            val leadItems = if (redesign) 1 else 0
            val dayIndices = remember(rowsByDate, leadItems) {
                rowsByDate.values.runningFold(leadItems) { index, rows -> index + 1 + rows.size }.dropLast(1)
            }
            val dates = groups.keys.toList()
            val visibleDate by remember {
                derivedStateOf { list.layoutInfo.visibleItemsInfo.firstOrNull()?.key?.toString()?.substringBefore(':') }
            }
            val selected = seekDate ?: visibleDate?.takeIf { it in groups } ?: dates.firstOrNull() ?: today

            fun scrollToDate(date: String) {
                val index = dates.indexOfFirst { it <= date }.takeIf { it >= 0 } ?: dates.lastIndex
                if (index >= 0) list.requestScrollToItem(dayIndices[index])
            }

            // Older dates may require more pages. Browse to the nearest watch on or before
            // the chosen day, keeping the existing rows visible while history loads.
            LaunchedEffect(seekDate, dayIndices, dates, timeline.loading, timeline.failed, timeline.nextCursor) {
                val date = seekDate ?: return@LaunchedEffect
                if (timeline.loading || timeline.failed) return@LaunchedEffect
                val cursor = timeline.nextCursor
                val oldestLoaded = timeline.events.lastOrNull()?.let(::historyLocalDate)
                if (cursor != null && (oldestLoaded == null || oldestLoaded > date)) {
                    request = { timeline.load(cursor) }
                } else {
                    scrollToDate(date)
                    seekDate = null
                }
            }

            // The newest watch on the day in view steers the shell backdrop, only while active.
            val focusEvent = groups[selected]?.firstOrNull() ?: timeline.events.firstOrNull()
            val backdropArt = focusEvent?.let { it.landscapeArtwork ?: it.episodeScreenshot ?: it.media.posterUrl }
            ScreenActivityEffect(backdropController, backdropArt) { isActive ->
                if (isActive) backdropController?.base = backdropArt
            }
            val oldestLoaded = timeline.events.lastOrNull()?.let(::historyLocalDate)
            fun coveredFrom(date: String) = timeline.loaded && (timeline.nextCursor == null || oldestLoaded != null && oldestLoaded < date)
            val month = selected.take(7)
            val monthCount = groups.filterKeys { it.startsWith(month) }.values.sumOf { it.size }
            val screenTitle = stringResource(Res.string.history_title)
            val kicker = if (coveredFrom("$month-01")) "$screenTitle · ${pluralStringResource(Res.plurals.history_watch_count, monthCount, monthCount)}" else screenTitle

            Column(Modifier.fillMaxSize()) {
                CalendarAgendaToolbar(
                    selected = selected,
                    filter = filter,
                    landscape = globalStyle.historyLandscapeModeEnabled,
                    loading = !timeline.loaded && !timeline.failed || timeline.loading,
                    openPicker = { pickerDate = selected },
                    goToday = {
                        seekDate = null
                        list.requestScrollToItem(0)
                    },
                    selectFilter = {
                        if (filter != it) {
                            seekDate = null
                            pickerDate = null
                            filter = it
                            list.requestScrollToItem(0)
                        }
                    },
                    toggleOrientation = {
                        PosterCardStyleRepository.setHistoryLandscapeModeEnabled(!globalStyle.historyLandscapeModeEnabled)
                    },
                    tagPrefix = "history",
                    title = if (redesign) CalendarPlatform.monthLabel(selected) else null,
                    subtitle = if (redesign && selected in groups) CalendarPlatform.dayLabel(selected) else null,
                    kicker = if (redesign) kicker else null,
                )
                if (timeline.removalFailed) Text(stringResource(Res.string.history_remove_failed), Modifier.padding(vertical = 8.dp))
                if (timeline.failed) Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(Res.string.history_load_failed), Modifier.weight(1f))
                    TextButton(onClick = { request = { timeline.retry() } }) {
                        Text(stringResource(Res.string.history_retry))
                    }
                }
                if (redesign) Spacer(Modifier.height(12.dp))
                val openCard = onPosterClick.takeUnless { stale }
                val onActions: ((HistoryCard) -> Unit)? = if (timeline.removing || stale) null else { card ->
                    actionAnchor = PosterZoomAnchorHolder.consume()
                    actionCard = card
                }
                LazyColumn(
                    state = list,
                    modifier = Modifier.fillMaxWidth().weight(1f).testTag("history-agenda")
                        .graphicsLayer { alpha = listAlpha }
                        .agendaScrollLoading(
                            list,
                            enabled = active && timeline.loaded && !timeline.failed && seekDate == null && timeline.nextCursor != null,
                            loading = timeline.loading,
                            onLater = {
                                timeline.nextCursor?.let { cursor -> request = { timeline.load(cursor) } }
                            },
                        ),
                    contentPadding = PaddingValues(bottom = 24.dp),
                ) {
                    if (redesign) item(key = "board") {
                        val newest = cardRows.values.firstOrNull()?.firstOrNull()?.firstOrNull()
                        val covered = !stale && coveredFrom(calendarDatePlusDays(today, -6))
                        HistoryBoard(
                            latest = newest,
                            latestDate = newest?.let { historyLocalDate(it.event) },
                            today = today,
                            stats = if (covered) historyWeekStats(groups.values.flatten(), today) else null,
                            topShow = if (covered) historyTopShow(groups.values.flatten(), today) else null,
                            wide = wideBoard,
                            onOpen = openCard?.let { open -> { card -> card.detail?.let(open) } },
                            modifier = Modifier.padding(bottom = 10.dp).animateItem(),
                        )
                    }
                    if (redesign && groups.isEmpty() && !timeline.loaded && !timeline.failed) item(key = "skeleton") {
                        Column(Modifier.animateItem(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            repeat(2) { HistoryDayTileSkeleton(columns, cardWidth, landscape, Modifier.padding(top = 14.dp)) }
                        }
                    }
                    if (groups.isEmpty() && timeline.loaded && !timeline.loading) item(key = "empty") {
                        Text(
                            stringResource(Res.string.history_empty),
                            Modifier.padding(vertical = 24.dp).animateItem(),
                            style = if (redesign) redesignText(15.sp, color = RedesignColors.textSecondary) else LocalTextStyle.current,
                        )
                    }
                    cardRows.forEach { (date, rows) ->
                        val count = groups.getValue(date).size
                        val isToday = date == today
                        if (!redesign) {
                            item(key = date) { DaySectionHeading(date, count, isToday, watchHistory = true) }
                            itemsIndexed(rows, key = { _, row -> historyRowKey(date, row.first().event.id) }) { index, row ->
                                HistoryMediaRow(row, cardWidth, if (index == rows.lastIndex) 28.dp else 8.dp, openCard, onActions)
                            }
                        } else {
                            item(key = date) {
                                Box(Modifier.animateItem().padding(top = 14.dp).historyDayTile(isFirst = true, isLast = false, highlighted = isToday)) {
                                    HistoryTileHeading(date, count, isToday)
                                }
                            }
                            itemsIndexed(rows, key = { _, row -> historyRowKey(date, row.first().event.id) }) { index, row ->
                                val last = index == rows.lastIndex
                                Box(Modifier.animateItem().historyDayTile(isFirst = false, isLast = last, highlighted = isToday)) {
                                    HistoryMediaRow(
                                        row, cardWidth, if (last) 16.dp else 4.dp, openCard, onActions,
                                        Modifier.padding(horizontal = HistoryTilePadding),
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
        actionCard?.takeIf { active }?.let { card ->
            PosterZoomOverlayPortal { haze ->
            NuvioPosterZoomActionOverlay(
                imageUrl = actionAnchor?.imageUrl ?: card.preview.poster,
                title = card.preview.name,
                subtitle = card.episodeTitle,
                anchor = actionAnchor,
                hazeState = haze,
                onDismissed = { actionCard = null; actionAnchor = null },
                actions = buildList {
                    if (card.detail != null && onPosterClick != null) add(PosterZoomOverlayAction(
                        icon = Icons.Default.Info,
                        label = stringResource(Res.string.cw_action_go_to_details),
                        onSelected = { onPosterClick(card.detail) },
                    ))
                    if (source.provider is TrackingHistoryRemover) add(PosterZoomOverlayAction(
                        icon = Icons.Default.DeleteOutline,
                        label = stringResource(Res.string.history_remove),
                        isDestructive = true,
                        exitAnimation = PosterZoomOverlayExitAnimation.COLLAPSE,
                        onSelected = { scope.launch { timeline.remove(card.event.id) } },
                    ))
                },
            )
        }
        }
        pickerDate?.let { initialDate ->
            CalendarAgendaDatePicker(initialDate, today, CalendarPlatform.firstWeekday(), { pickerDate = null }, "history") {
                pickerDate = null
                seekDate = it
            }
        }
    }
}

internal fun historyLocalDate(event: TrackingHistoryEvent): String? =
    EpisodeReleaseDatePlatform.localIsoDateAtEpochMs(event.watchedAtMillis)

@Composable
private fun HistoryMediaRow(
    cards: List<HistoryCard>,
    cardWidth: Dp,
    bottomPadding: Dp,
    onPosterClick: ((MetaPreview) -> Unit)?,
    onActions: ((HistoryCard) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val landscape = rememberPosterCardStyleUiState().catalogLandscapeModeEnabled
    Row(
        modifier = modifier.fillMaxWidth().padding(top = 8.dp, bottom = bottomPadding),
        horizontalArrangement = Arrangement.spacedBy(HistoryCardSpacing),
    ) {
        cards.forEach { card ->
            key(card.event.id) {
                val episode = card.event.media.episode
                val detailLine = if (episode == null) calendarMovieLabel(card.event.media.year) else {
                    val number = episode.season?.let { stringResource(Res.string.history_season_episode, it, episode.number) }
                        ?: stringResource(Res.string.history_episode, episode.number)
                    listOfNotNull(number, card.episodeTitle?.takeIf(String::isNotBlank)).joinToString(" - ")
                }
                CalendarAgendaMediaCard(
                    item = card.preview,
                    imageUrl = if (landscape) card.preview.landscapePoster else card.preview.poster,
                    fallbackImageUrl = card.preview.poster,
                    detail = detailLine,
                    badge = formatLocalTime(card.event.watchedAtMillis),
                    cardWidth = cardWidth,
                    modifier = Modifier.testTag("history-card-${card.event.id}"),
                    onLongClick = onActions?.let { { it(card) } },
                    onClick = card.detail?.let { preview -> onPosterClick?.let { { it(preview) } } },
                )
            }
        }
    }
}

private val HistoryCardSpacing = 12.dp

private fun historyRowKey(date: String, firstEventId: String) = "$date:$firstEventId"

private fun TrackingHistoryEvent.toHistoryCard(
    unknownTitle: String,
    metadata: Map<Pair<String, String>, MetaDetails?>,
): HistoryCard {
    val title = media.title?.takeIf(String::isNotBlank) ?: unknownTitle
    val detail = media.historyPreview(title)
    val episode = media.episode
    val video = detail?.let { metadata[it.type to it.id] }?.findWatchProgressEpisode(episode?.season, episode?.number)
    val screenshot = episodeScreenshot?.takeIf(String::isNotBlank) ?: video?.thumbnail?.takeIf(String::isNotBlank)
    val poster = if (episode == null) media.posterUrl else screenshot
    val preview = MetaPreview(
        id = id,
        type = if (media.kind == TrackingMediaKind.MOVIE) "movie" else "series",
        name = title,
        poster = poster,
        landscapePoster = if (episode == null) landscapeArtwork ?: poster else screenshot,
    )
    return HistoryCard(this, preview, detail, episode?.title?.takeIf(String::isNotBlank) ?: video?.title)
}

internal data class HistoryCard(
    val event: TrackingHistoryEvent,
    val preview: MetaPreview,
    val detail: MetaPreview?,
    val episodeTitle: String?,
)

private fun TrackingMediaReference.historyPreview(title: String): MetaPreview? {
    val id = catalog?.contentId?.takeIf(String::isNotBlank)
        ?: ids.imdb?.takeIf { it.startsWith("tt") }
        ?: ids.tmdb?.let { "tmdb:$it" }
        ?: return null
    return MetaPreview(
        id = id,
        type = catalog?.contentType ?: if (kind == TrackingMediaKind.MOVIE) "movie" else "series",
        name = title,
        poster = posterUrl,
        releaseInfo = year?.toString(),
    )
}
