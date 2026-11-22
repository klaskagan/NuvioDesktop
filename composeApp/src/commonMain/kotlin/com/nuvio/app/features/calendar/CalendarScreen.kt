package com.nuvio.app.features.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.input.nestedscroll.*
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.*
import com.nuvio.app.core.format.formatLocalTime
import com.nuvio.app.core.format.formatReleaseDateForDisplay
import com.nuvio.app.core.time.EpisodeReleaseDatePlatform
import com.nuvio.app.core.time.isoEpochDay
import com.nuvio.app.core.ui.*
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.home.PosterShape
import com.nuvio.app.features.home.stableKey
import com.nuvio.app.features.home.components.*
import com.nuvio.app.features.trakt.*
import com.nuvio.app.navigation.excludeFromHistoryGestures
import com.nuvio.app.features.watched.WatchedRepository
import com.nuvio.app.features.watching.application.WatchingState
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource

@Composable
internal fun CalendarScreen(
    topChromePadding: Dp? = null,
    onPosterClick: ((MetaPreview) -> Unit)? = null,
    repository: CalendarRepository? = null,
    initialDate: String? = null,
) {
    val session = remember { repository ?: TraktCalendarRepository.createSession() }
    DisposableEffect(session) { onDispose { if (repository == null) TraktCalendarRepository.releaseSession(session) } }
    val state by session.state.collectAsState()
    val auth by TraktAuthRepository.uiState.collectAsState()
    val globalStyle = rememberPosterCardStyleUiState()
    var today by remember { mutableStateOf(calendarToday()) }
    var region by remember { mutableStateOf(CalendarPlatform.localeKey() + EpisodeReleaseDatePlatform.timezoneId()) }
    var selected by rememberSaveable { mutableStateOf(initialDate ?: today) }
    var viewName by rememberSaveable { mutableStateOf(CalendarView.Week.name) }
    var filterName by rememberSaveable { mutableStateOf(CalendarFilter.All.name) }
    val view = CalendarView.valueOf(viewName)
    val filter = CalendarFilter.valueOf(filterName)
    val firstWeekday = remember(region) { CalendarPlatform.firstWeekday() }

    var rangeStart by rememberSaveable { mutableStateOf(calendarWeekStart(initialDate ?: today, firstWeekday)) }
    var rangeDays by rememberSaveable { mutableIntStateOf(28) }
    var revision by remember { mutableIntStateOf(0) }
    var loadedRange by remember { mutableStateOf<CalendarRange?>(null) }
    var picker by remember { mutableStateOf(false) }
    var pickerDate by remember { mutableStateOf(selected) }
    val dayState = rememberLazyListState()
    val weekState = rememberLazyListState()
    val monthState = rememberLazyListState()
    val weekHorizontalState = rememberScrollState()

    val range = if (view == CalendarView.Month) calendarMonthRange(selected, firstWeekday) else CalendarRange(rangeStart, rangeDays)
    val rangeLoading = state.loading || loadedRange != range
    val cards = remember(state.cards, filter, range) { calendarFiltered(state.cards, filter).filter { it.localDate >= range.start && it.localDate < calendarDatePlusDays(range.start, range.days) } }
    val groups = remember(cards) { calendarDayGroups(cards, CalendarFilter.All) }
    val viewState = when (view) { CalendarView.Day -> dayState; CalendarView.Week -> weekState; CalendarView.Month -> monthState }
    var revealRequest by remember { mutableIntStateOf(0) }
    var pendingReveal by remember { mutableStateOf(true) }
    var prependAnchor by remember { mutableStateOf<Pair<String, Int>?>(null) }
    var prependReady by remember { mutableStateOf(false) }
    fun reveal(date: String) {
        selected = date
        prependAnchor = null
        prependReady = false
        rangeStart = calendarWeekStart(date, firstWeekday)
        rangeDays = 28
        pendingReveal = true
        revealRequest++
    }
    fun move(delta: Int) = reveal(when (view) {
        CalendarView.Day -> calendarDatePlusDays(selected, delta)
        CalendarView.Week -> calendarDatePlusDays(selected, delta * 7)
        CalendarView.Month -> calendarMoveMonth(selected, delta)
    })
    fun extend(earlier: Boolean) {
        // Bound retained presentation to twelve weeks. Shift the opposite edge when full.
        if (earlier) {
            if (view == CalendarView.Day && groups.isNotEmpty()) {
                val item = dayState.layoutInfo.visibleItemsInfo.firstOrNull()
                prependAnchor = (item?.key as? String)?.let { it to dayState.firstVisibleItemScrollOffset }
                prependReady = false
            }
            rangeStart = calendarDatePlusDays(rangeStart, -14)
            rangeDays = (rangeDays + 14).coerceAtMost(84)
        } else if (rangeDays < 84) rangeDays += 14
        else rangeStart = calendarDatePlusDays(rangeStart, 14)
        if (view == CalendarView.Day && groups.isEmpty()) {
            selected = if (earlier) rangeStart else calendarDatePlusDays(rangeStart, rangeDays - 14)
        }
    }
    ScreenActivityEffect(session, range, auth.calendarAccountKey, revision) { active ->
        if (!active) return@ScreenActivityEffect
        var firstLoad = true
        while (isActive) {
            today = calendarToday()
            region = CalendarPlatform.localeKey() + EpisodeReleaseDatePlatform.timezoneId()
            session.load(range.days, force = firstLoad && revision > 0, startDate = range.start)
            loadedRange = range
            if (prependAnchor != null) prependReady = true
            firstLoad = false
            delay(30_000)
        }
    }
    LaunchedEffect(revealRequest, viewName, cards, state.loading, loadedRange, range) {
        if (!pendingReveal || state.loading || loadedRange != range) return@LaunchedEffect
        val index = when (view) {
            CalendarView.Month -> 0
            CalendarView.Week -> ((isoEpochDay(selected) - isoEpochDay(range.start)) / 7).toInt().coerceAtLeast(0)
            CalendarView.Day -> groups.keys.indexOfFirst { it >= selected }.let { if (it < 0) 0 else it }
        }
        snapshotFlow { viewState.layoutInfo.totalItemsCount }.first { it > index }
        viewState.requestScrollToItem(index)
        pendingReveal = false
    }
    LaunchedEffect(prependReady, cards) {
        if (!prependReady) return@LaunchedEffect
        prependAnchor?.let { (key, offset) ->
            val index = groups.keys.indexOf(key)
            if (index >= 0) dayState.requestScrollToItem(index, offset)
        }
        prependAnchor = null
        prependReady = false
    }
    LaunchedEffect(firstWeekday) {
        rangeStart = calendarWeekStart(rangeStart, firstWeekday)
    }
    val latestLoading by rememberUpdatedState(state.loading)
    val latestView by rememberUpdatedState(view)
    val latestList by rememberUpdatedState(viewState)
    val latestSelection by rememberUpdatedState<(String) -> Unit> { selected = it }
    val scrollLoader = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput || latestView == CalendarView.Month || latestLoading) return Offset.Zero
                val list = latestList
                if (consumed.y != 0f) {
                    val visible = list.layoutInfo.visibleItemsInfo.firstOrNull { item ->
                        (item.key as? String)?.let { it.length == 10 && it[4] == '-' } == true
                    }?.key as? String
                    if (visible != null) latestSelection(visible)
                }
                return Offset.Zero
            }
        }
    }
    val style = calendarAgendaPosterStyle(globalStyle)
    CompositionLocalProvider(LocalPosterCardStyleOverride provides style, LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .padding(top = ((topChromePadding ?: 16.dp) - 20.dp).coerceAtLeast(8.dp))
            .padding(horizontal = 24.dp).testTag("calendar-screen")) {
            CalendarAgendaToolbar(selected, filter, globalStyle.calendarLandscapeModeEnabled, rangeLoading,
                openPicker = { pickerDate = selected; picker = true },
                goToday = { reveal(today) },
                selectFilter = { filterName = it.name; pendingReveal = true; revealRequest++ },
                toggleOrientation = { PosterCardStyleRepository.setCalendarLandscapeModeEnabled(!globalStyle.calendarLandscapeModeEnabled) },
                previousMonth = if (view == CalendarView.Month) ({ move(-1) }) else null,
                nextMonth = if (view == CalendarView.Month) ({ move(1) }) else null,
                view = view,
                selectView = { option ->
                    if (view != option) {
                        if (option != CalendarView.Month) reveal(selected)
                        viewName = option.name
                        pendingReveal = true
                    }
                },
            )
            if (state.problem == CalendarProblem.NETWORK || state.problem == CalendarProblem.AUTHENTICATION) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(if (state.problem == CalendarProblem.AUTHENTICATION) Res.string.calendar_auth_expired else if (state.partial) Res.string.calendar_partial else Res.string.calendar_error), modifier = Modifier.weight(1f))
                    TextButton(onClick = { TraktCalendarRepository.expireCache(); revision++ }) { Text(stringResource(Res.string.action_retry)) }
                }
            }
            val shape = if (style.catalogLandscapeModeEnabled) PosterShape.Landscape else PosterShape.Poster
            val dates = range.dates
            if (view == CalendarView.Month) {
                CalendarMonthBody(selected, today, groups, { selected = it }, shape, onPosterClick, firstWeekday, rangeLoading) { move(it) }
            } else {
                    BoxWithConstraints(Modifier.weight(1f)
                        .agendaScrollLoading(viewState, enabled = view != CalendarView.Month, loading = state.loading,
                            onEarlier = { extend(true) }, onLater = { extend(false) })
                        .nestedScroll(scrollLoader)) {
                        val columnWidth = (maxWidth / 7).coerceAtLeast(if (shape == PosterShape.Landscape) 190.dp else 142.dp)
                        val totalWidth = columnWidth * 7
                        val anchoredHeading = Modifier.width(maxWidth).offset { IntOffset(weekHorizontalState.value, 0) }
                        val density = LocalDensity.current
                        LaunchedEffect(view, revealRequest, columnWidth) {
                            if (view == CalendarView.Week) {
                                if (totalWidth > maxWidth) snapshotFlow { weekHorizontalState.maxValue }.first { it > 0 && it != Int.MAX_VALUE }
                                val dayIndex = (isoEpochDay(selected) - isoEpochDay(calendarWeekStart(selected, firstWeekday))).toInt()
                                val target = with(density) { ((columnWidth * (dayIndex + 1)) - maxWidth).roundToPx().coerceAtLeast(0) }
                                weekHorizontalState.scrollTo(target)
                            }
                        }
                        val pageModifier = if (view == CalendarView.Week) Modifier.then(if (totalWidth > maxWidth) Modifier.excludeFromHistoryGestures() else Modifier).horizontalScroll(weekHorizontalState).width(totalWidth) else Modifier.fillMaxWidth()
                        LazyColumn(state = viewState, modifier = pageModifier.fillMaxHeight().testTag("calendar-agenda"), verticalArrangement = Arrangement.spacedBy(20.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                            if (view == CalendarView.Day) {
                                if (groups.isEmpty()) item(key = "empty") {
                                    Column(Modifier.fillParentMaxHeight()) {
                                        if (!rangeLoading) Text(stringResource(Res.string.calendar_range_empty), Modifier.padding(vertical = 24.dp))
                                    }
                                }
                                else {
                                    items(groups.keys.toList(), key = { it }) { date ->
                                        Column {
                                            DaySectionHeading(date, groups[date].orEmpty().size, date == today)
                                            CalendarShelf(groups[date].orEmpty(), onPosterClick)
                                        }
                                    }
                                }
                            } else {
                                items(dates.chunked(7), key = { it.first() }) { week ->
                                    val members = week.flatMap { groups[it].orEmpty() }
                                    Column {
                                        Row(anchoredHeading.padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                            Text(when (calendarRelativeWeek(week.first(), today, firstWeekday)) {
                                                0 -> stringResource(Res.string.calendar_this_week)
                                                1 -> stringResource(Res.string.calendar_next_week)
                                                else -> calendarWeekRangeLabel(week.first(), week.last())
                                            }, style = MaterialTheme.typography.titleMedium)
                                            ReleaseCount(members.size)
                                        }
                                            Row {
                                                week.forEach { date ->
                                                    Column(Modifier.width(columnWidth).padding(horizontal = 6.dp).testTag("calendar-day-$date"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                                        DayNumber(date, date == today, date == selected) { selected = date }
                                                        groups[date].orEmpty().forEach { card -> key(card.key) { CalendarMediaCard(card, Modifier.fillMaxWidth(), onPosterClick = onPosterClick) } }
                                                        if (groups[date].isNullOrEmpty()) Spacer(Modifier.height(64.dp))
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
        if (picker) CalendarAgendaDatePicker(pickerDate, today, firstWeekday, dismiss = { picker = false }) {
            reveal(it)
            picker = false
        }
    }
}

internal fun calendarAgendaPosterStyle(style: PosterCardStyleUiState, landscape: Boolean = style.calendarLandscapeModeEnabled) = style.copy(
    catalogLandscapeModeEnabled = landscape,
    hideLabelsEnabled = false,
    cornerRadiusDp = if (landscape) style.cornerRadiusDp.coerceAtMost(8) else style.cornerRadiusDp,
)

@Composable
internal fun CalendarAgendaDatePicker(
    initialDate: String,
    today: String,
    firstWeekday: Int,
    dismiss: () -> Unit,
    tagPrefix: String = "calendar",
    select: (String) -> Unit,
) {
    var pickerDate by remember(initialDate) { mutableStateOf(initialDate) }
    AlertDialog(onDismissRequest = dismiss, text = {
        Column(Modifier.testTag("$tagPrefix-date-picker")) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { pickerDate = calendarMoveMonth(pickerDate, -1) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, stringResource(Res.string.calendar_previous)) }
                Text(CalendarPlatform.monthLabel(pickerDate), Modifier.weight(1f))
                IconButton(onClick = { pickerDate = calendarMoveMonth(pickerDate, 1) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, stringResource(Res.string.calendar_next)) }
            }
            val pickerDates = calendarMonthRange(pickerDate, firstWeekday).dates
            WeekdayHeader(pickerDates.take(7))
            pickerDates.chunked(7).forEach { week -> Row { week.forEach { date ->
                Box(Modifier.weight(1f).height(40.dp).testTag("$tagPrefix-picker-$date").clickable { select(date) }, contentAlignment = Alignment.Center) {
                    Text(date.takeLast(2).toInt().toString(), textAlign = TextAlign.Center,
                        color = if (date == today) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(32.dp).background(if (date == today) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface.copy(alpha = 0f), CircleShape).wrapContentSize())
                }
            } } }
        }
    }, confirmButton = {})
}

internal fun calendarToday() = EpisodeReleaseDatePlatform.localIsoDateAtEpochMs(EpisodeReleaseDatePlatform.nowEpochMs())!!
@Composable private fun ReleaseCount(count: Int) {
    Text(pluralStringResource(Res.plurals.calendar_release_count, count, count), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.nuvio.colors.textMuted)
}
@Composable internal fun DaySectionHeading(date: String, count: Int, today: Boolean, watchHistory: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp).testTag("calendar-day-heading-$date"), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.background(if (today) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface.copy(alpha = 0f), RoundedCornerShape(16.dp))
            .padding(end = if (today) 12.dp else 0.dp).then(if (today) Modifier.testTag("calendar-today") else Modifier),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(date.takeLast(2).toInt().toString(), style = MaterialTheme.typography.headlineMedium,
                color = if (today) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.nuvio.colors.textPrimary,
                modifier = Modifier.size(56.dp).background(if (today) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp)).wrapContentSize())
            Column {
                Text(CalendarPlatform.dateLabel(date, "EEEE"), style = MaterialTheme.typography.titleMedium,
                    color = if (today) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.nuvio.colors.textPrimary)
                Text(CalendarPlatform.dateLabel(date, "MMMM"), style = MaterialTheme.typography.labelSmall,
                    color = if (today) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = .7f) else MaterialTheme.nuvio.colors.textMuted)
            }
        }
        Spacer(Modifier.weight(1f))
        if (watchHistory) Text(
            pluralStringResource(Res.plurals.history_watch_count, count, count),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.nuvio.colors.textMuted,
        ) else ReleaseCount(count)
    }
}
@Composable private fun DateHeading(date: String, count: Int?) {
    Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(CalendarPlatform.dayLabel(date), style = MaterialTheme.typography.titleMedium)
        if (count != null) ReleaseCount(count)
    }
}
@Composable private fun WeekdayHeader(dates: List<String>) { Row(Modifier.fillMaxWidth()) { dates.forEach { Text(CalendarPlatform.weekdayLabel(it), Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelMedium) } } }
@Composable private fun DayNumber(date: String, today: Boolean, selected: Boolean, onClick: () -> Unit) {
    Text("${CalendarPlatform.weekdayLabel(date)} ${date.takeLast(2).toInt()}", style = MaterialTheme.typography.titleSmall,
        color = if (today) MaterialTheme.colorScheme.onPrimaryContainer else if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.testTag(if (today) "calendar-today" else "calendar-day-label-$date").clickable(onClick = onClick)
            .background(if (today) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface.copy(alpha = 0f), RoundedCornerShape(8.dp)).padding(8.dp))
}

@Composable
private fun CalendarShelf(cards: List<CalendarCard>, onPosterClick: ((MetaPreview) -> Unit)?, cardWidth: Dp? = null) {
    CalendarAgendaShelf(cards, { it.key }) { card ->
        CalendarMediaCard(card, cardWidth = cardWidth, onPosterClick = onPosterClick)
    }
}

@Composable
internal fun <T> CalendarAgendaShelf(
    entries: List<T>,
    entryKey: (T) -> String,
    content: @Composable (T) -> Unit,
) {
    NuvioShelfSection(title = "", entries = entries, state = rememberLazyListState(), key = entryKey,
        itemSpacing = 12.dp, rowContentPadding = PaddingValues(vertical = 8.dp)) { entry ->
        content(entry)
    }
}

@Composable private fun CalendarMediaCard(card: CalendarCard, modifier: Modifier = Modifier, compact: Boolean = false, cardWidth: Dp? = null, onPosterClick: ((MetaPreview) -> Unit)?) {
    val style = rememberPosterCardStyleUiState()
    val item = card.toPreview()
    val watched by WatchedRepository.uiState.collectAsState()
    val full by WatchedRepository.fullyWatchedSeriesKeys.collectAsState()
    val badge = listOfNotNull(card.releaseEpochMs?.let { start ->
        formatLocalTime(start) + (card.lastReleaseEpochMs?.takeIf { it != start }?.let { "–${formatLocalTime(it)}" } ?: "")
    }, if (card.premiere) stringResource(Res.string.calendar_premiere) else null,
        if (card.finale) stringResource(Res.string.calendar_finale) else null).joinToString(" · ").ifBlank { null }
    CalendarAgendaMediaCard(
        item = item,
        imageUrl = if (style.catalogLandscapeModeEnabled) card.media.landscape ?: card.episodes.firstOrNull()?.screenshot ?: card.media.poster else card.media.poster,
        fallbackImageUrl = card.media.poster,
        modifier = modifier.testTag("calendar-card-${card.key}"),
        compact = compact,
        cardWidth = cardWidth,
        detail = card.episodeLabel ?: calendarMovieLabel(card.media.year),
        badge = badge,
        isWatched = !compact && WatchingState.isPosterWatched(watched.watchedKeys, item, full),
        onClick = onPosterClick?.let { { it(item) } },
    )
}

@Composable
internal fun CalendarAgendaMediaCard(
    item: MetaPreview,
    imageUrl: String?,
    fallbackImageUrl: String?,
    detail: String?,
    badge: String?,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    cardWidth: Dp? = null,
    isWatched: Boolean = false,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
) {
    val style = rememberPosterCardStyleUiState()
    NuvioPosterCard(title = item.name,
        imageUrl = imageUrl,
        fallbackImageUrl = fallbackImageUrl,
        modifier = if (compact) modifier else modifier.width(cardWidth ?: if (style.catalogLandscapeModeEnabled) 224.dp else 142.dp),
        fillWidth = true,
        shape = if (style.catalogLandscapeModeEnabled) NuvioPosterShape.Landscape else NuvioPosterShape.Poster,
        detailLine = if (compact) null else detail,
        artworkBadge = if (compact) null else badge,
        requiredLabels = true, showTitleBelow = !compact,
        isWatched = isWatched,
        onClick = onClick, onLongClick = onLongClick)
}

/** The grid and selected shelf share the viewport instead of sharing a vertical scroller. */
@Composable private fun CalendarMonthBody(selected: String, today: String, groups: Map<String, List<CalendarCard>>, select: (String) -> Unit, shape: PosterShape, onPosterClick: ((MetaPreview) -> Unit)?, firstWeekday: Int, loading: Boolean, move: (Int) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth().fillMaxHeight()) {
        val available = maxHeight
        val weekCount = 6
        val shelfHeight = (available * .46f).coerceIn(220.dp, 340.dp).coerceAtMost(available * .6f)
        val cellHeight = ((available - shelfHeight - 26.dp - 6.dp * (weekCount - 1)) / weekCount).coerceAtLeast(40.dp)
        val gridWidth = (cellHeight * 1.75f * 7 + 24.dp).coerceAtMost(maxWidth).coerceAtLeast(maxWidth.coerceAtMost(644.dp))
        val artHeight = (shelfHeight - 112.dp).coerceAtLeast(36.dp)
        val cardWidth = if (shape == PosterShape.Landscape) artHeight * 1.77f else artHeight * (2f / 3f)
        Column(Modifier.fillMaxSize()) {
            CalendarMonthPager(selected, firstWeekday, Modifier.weight(1f).width(gridWidth).align(Alignment.CenterHorizontally), move) { pageDates, interactive, pageMonth ->
                Column(Modifier.fillMaxSize().then(if (interactive) Modifier else Modifier.clearAndSetSemantics {})) {
                    WeekdayHeader(pageDates.take(7))
                    Spacer(Modifier.height(6.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            pageDates.chunked(7).forEach { week ->
                                    MonthWeek(week, pageMonth, selected, today, groups, if (interactive) select else { _ -> }, shape, Modifier.weight(1f))
                            }
                        }
                }
            }
            Column(Modifier.fillMaxWidth().height(shelfHeight).testTag("calendar-selected-day")) {
                val selectedCards = groups[selected].orEmpty()
                DateHeading(selected, selectedCards.size.takeIf { selectedCards.isNotEmpty() || !loading })
                if (selectedCards.isNotEmpty()) CalendarShelf(selectedCards, onPosterClick, cardWidth)
                else if (!loading) Text(stringResource(Res.string.calendar_day_empty), Modifier.padding(vertical = 12.dp))
            }
        }
    }
}


@Composable private fun MonthWeek(dates: List<String>, month: String, selected: String, today: String, groups: Map<String, List<CalendarCard>>, select: (String) -> Unit, shape: PosterShape, modifier: Modifier) {
    val style = rememberPosterCardStyleUiState()
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        dates.forEach { date ->
            if (!date.startsWith(month)) { Spacer(Modifier.weight(1f).fillMaxHeight()); return@forEach }
            Column(Modifier.weight(1f).fillMaxHeight()
                .border(1.dp, if (date == selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                .background(if (date == selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = .3f) else MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                .clip(RoundedCornerShape(8.dp)).clickable { select(date) }.padding(5.dp).testTag("calendar-date-$date").semantics { contentDescription = CalendarPlatform.dayLabel(date); this.selected = date == selected }) {
                Text(if (date.endsWith("-01")) "1 ${CalendarPlatform.shortMonthLabel(date)}" else date.takeLast(2).toInt().toString(), style = MaterialTheme.typography.labelMedium, color = if (date == today) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.background(if (date == today) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface.copy(alpha = 0f), CircleShape).padding(horizontal = 4.dp))
                val thumbnails = groups[date].orEmpty().take(3)
                val extra = groups[date].orEmpty().size - thumbnails.size
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                    val ratio = if (shape == PosterShape.Landscape) 1.77f else 2f / 3f
                    val countWidth = if (extra > 0) 24.dp else 0.dp
                    val width = ((maxWidth - countWidth - 6.dp) / 3).coerceAtLeast(1.dp).coerceAtMost(maxHeight.coerceAtLeast(1.dp) * ratio)
                    CompositionLocalProvider(LocalPosterCardStyleOverride provides style.copy(cornerRadiusDp = 4)) {
                        Row(Modifier.align(Alignment.BottomStart), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.Bottom) {
                            thumbnails.forEach { card -> CalendarMediaCard(card, Modifier.width(width), compact = true, onPosterClick = { select(date) }) }
                            if (extra > 0) Text("+$extra", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}
