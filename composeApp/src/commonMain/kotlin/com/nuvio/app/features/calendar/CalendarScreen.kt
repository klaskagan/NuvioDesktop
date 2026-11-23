package com.nuvio.app.features.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.animation.Crossfade
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.font.FontWeight
import com.nuvio.app.isDesktop
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
    // Desktop redesign: Calendar sits on the shell's ambient backdrop, steered by the next release
    // in the visible range. Visited tabs stay composed, so only the active screen may steer it.
    val backdropController = LocalBackdropFocusController.current.takeIf { isDesktop }
    val focusCards = when (view) {
        CalendarView.Month -> groups[selected].orEmpty().ifEmpty { cards }
        else -> cards
    }
    val backdropArt = (focusCards.firstOrNull { it.localDate >= today } ?: focusCards.firstOrNull())
        ?.let { it.media.landscape ?: it.episodes.firstOrNull()?.screenshot ?: it.media.poster }
    ScreenActivityEffect(backdropController, backdropArt) { active ->
        if (active) backdropController?.base = backdropArt
    }
    val headerCount = when (view) {
        CalendarView.Day -> groups[selected].orEmpty().size
        CalendarView.Week -> calendarWeekStart(selected, firstWeekday).let { start -> cards.count { it.localDate >= start && it.localDate < calendarDatePlusDays(start, 7) } }
        CalendarView.Month -> cards.count { it.localDate.startsWith(selected.take(7)) }
    }
    val screenTitle = stringResource(Res.string.calendar_screen_title)
    val kicker = if (rangeLoading) screenTitle else "$screenTitle · ${pluralStringResource(Res.plurals.calendar_release_count, headerCount, headerCount)}"
    val subtitle = when (view) {
        CalendarView.Month -> null
        CalendarView.Day -> CalendarPlatform.dayLabel(selected)
        // The list's own week headings say This week / Next week; the header gives the range.
        CalendarView.Week -> calendarWeekStart(selected, firstWeekday).let { start -> calendarWeekRangeLabel(start, calendarDatePlusDays(start, 6)) }
    }
    CompositionLocalProvider(LocalPosterCardStyleOverride provides style, LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
      RedesignTypography(enabled = isDesktop) {
        BoxWithConstraints(Modifier.fillMaxSize()
            .then(if (backdropController == null) Modifier.background(MaterialTheme.colorScheme.background) else Modifier)) {
        val gutter = desktopPageHorizontalPaddingForWidth(maxWidth.value)
        Column(Modifier.fillMaxSize()
            .padding(top = ((topChromePadding ?: 16.dp) - 4.dp).coerceAtLeast(8.dp))
            .padding(horizontal = gutter).testTag("calendar-screen")) {
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
                title = CalendarPlatform.monthLabel(selected),
                subtitle = subtitle,
                kicker = kicker,
            )
            if (state.problem == CalendarProblem.NETWORK || state.problem == CalendarProblem.AUTHENTICATION) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(if (state.problem == CalendarProblem.AUTHENTICATION) Res.string.calendar_auth_expired else if (state.partial) Res.string.calendar_partial else Res.string.calendar_error), modifier = Modifier.weight(1f))
                    TextButton(onClick = { TraktCalendarRepository.expireCache(); revision++ }) { Text(stringResource(Res.string.action_retry)) }
                }
            }
            val shape = if (style.catalogLandscapeModeEnabled) PosterShape.Landscape else PosterShape.Poster
            val dates = range.dates
            Spacer(Modifier.height(12.dp))
            // Views crossfade instead of swapping in one frame.
            Crossfade(targetState = view, animationSpec = tween(260), modifier = Modifier.weight(1f), label = "calendar_view") { shownView ->
            if (shownView == CalendarView.Month) {
                CalendarMonthBody(selected, today, groups, { selected = it }, shape, onPosterClick, firstWeekday, rangeLoading) { move(it) }
            } else {
                    BoxWithConstraints(Modifier.fillMaxSize()
                        .agendaScrollLoading(viewState, enabled = view != CalendarView.Month, loading = state.loading,
                            onEarlier = { extend(true) }, onLater = { extend(false) })
                        .nestedScroll(scrollLoader)) {
                        val columnWidth = (maxWidth / 7).coerceAtLeast(if (shape == PosterShape.Landscape) 152.dp else 132.dp)
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
                        val listState = if (shownView == CalendarView.Day) dayState else weekState
                        val pageModifier = if (shownView == CalendarView.Week) Modifier.then(if (totalWidth > maxWidth) Modifier.excludeFromHistoryGestures() else Modifier).horizontalScroll(weekHorizontalState).width(totalWidth) else Modifier.fillMaxWidth()
                        LazyColumn(state = listState, modifier = pageModifier.fillMaxHeight().testTag("calendar-agenda"), verticalArrangement = Arrangement.spacedBy(if (shownView == CalendarView.Week) 28.dp else 12.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                            if (shownView == CalendarView.Day) {
                                if (groups.isEmpty()) item(key = "empty") {
                                    Column(Modifier.fillParentMaxHeight()) {
                                        if (!rangeLoading) Text(stringResource(Res.string.calendar_range_empty), Modifier.padding(vertical = 24.dp), style = redesignText(15.sp, color = RedesignColors.textSecondary))
                                    }
                                }
                                else {
                                    items(groups.keys.toList(), key = { it }) { date ->
                                        Column(Modifier.animateItem()) {
                                            DaySectionHeading(date, groups[date].orEmpty().size, date == today)
                                            CalendarShelf(groups[date].orEmpty(), onPosterClick)
                                        }
                                    }
                                }
                            } else {
                                items(dates.chunked(7), key = { it.first() }) { week ->
                                    val members = week.flatMap { groups[it].orEmpty() }
                                    Column(Modifier.animateItem()) {
                                        Row(anchoredHeading.padding(bottom = 12.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                            Text(when (calendarRelativeWeek(week.first(), today, firstWeekday)) {
                                                0 -> stringResource(Res.string.calendar_this_week)
                                                1 -> stringResource(Res.string.calendar_next_week)
                                                else -> calendarWeekRangeLabel(week.first(), week.last())
                                            }, style = redesignText(20.sp, FontWeight.Bold, letterSpacing = RedesignTightTracking))
                                            if (!rangeLoading || members.isNotEmpty()) ReleaseCount(members.size, Modifier.padding(bottom = 3.dp))
                                        }
                                        CalendarWeekColumns(week, today, columnWidth) { index, date ->
                                            Column(Modifier.fillMaxWidth().testTag("calendar-day-$date"), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                                DayNumber(date, date == today, date == selected) { selected = date }
                                                groups[date].orEmpty().forEach { card -> key(card.key) { CalendarMediaCard(card, Modifier.fillMaxWidth(), onPosterClick = onPosterClick) } }
                                                if (groups[date].isNullOrEmpty()) {
                                                    Box(Modifier.fillMaxWidth().height(40.dp).padding(horizontal = 4.dp)) {
                                                        if (!rangeLoading) Text(stringResource(Res.string.calendar_week_day_empty), style = redesignText(12.5.sp, color = RedesignColors.textMuted))
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
    cornerRadiusDp = if (landscape) style.cornerRadiusDp.coerceAtMost(12) else style.cornerRadiusDp,
)

/**
 * Date picker shared by Calendar and History, in the redesign's frosted style: month header with
 * round arrows, weekday kickers, a six-row grid whose month slides with the direction of travel,
 * the chosen date as a light pill, today as a ring, and a Today shortcut. Esc/outside dismiss.
 */
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
    var direction by remember { mutableIntStateOf(0) }
    fun step(delta: Int) { direction = delta; pickerDate = calendarMoveMonth(pickerDate, delta) }
    val shown = remember { androidx.compose.animation.core.MutableTransitionState(false).apply { targetState = true } }
    androidx.compose.ui.window.Dialog(onDismissRequest = dismiss) {
        RedesignTypography(enabled = isDesktop) {
            androidx.compose.animation.AnimatedVisibility(
                visibleState = shown,
                enter = androidx.compose.animation.fadeIn(tween(180)) + androidx.compose.animation.scaleIn(tween(220), initialScale = 0.96f),
            ) {
                val panelShape = RoundedCornerShape(22.dp)
                Column(
                    Modifier.width(372.dp).clip(panelShape).background(Color(0xFF15171B).copy(alpha = 0.97f))
                        .border(1.dp, RedesignColors.hairline, panelShape).padding(horizontal = 18.dp, vertical = 18.dp)
                        .testTag("$tagPrefix-date-picker"),
                ) {
                    Row(Modifier.fillMaxWidth().padding(start = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.animation.AnimatedContent(
                            targetState = pickerDate.take(7),
                            transitionSpec = { androidx.compose.animation.fadeIn(tween(200)) togetherWith androidx.compose.animation.fadeOut(tween(140)) },
                            modifier = Modifier.weight(1f),
                            label = "picker_month_title",
                        ) { month ->
                            val date = "$month-01"
                            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(CalendarPlatform.dateLabel(date, "MMMM"), style = redesignText(20.sp, FontWeight.Bold, letterSpacing = RedesignTightTracking))
                                Text(CalendarPlatform.dateLabel(date, "yyyy"), style = redesignText(20.sp, color = RedesignColors.textMuted, letterSpacing = RedesignTightTracking))
                            }
                        }
                        RedesignIconButton(stringResource(Res.string.calendar_previous), { step(-1) }, vector = Icons.AutoMirrored.Filled.KeyboardArrowLeft, size = 32.dp)
                        Spacer(Modifier.width(8.dp))
                        RedesignIconButton(stringResource(Res.string.calendar_next), { step(1) }, vector = Icons.AutoMirrored.Filled.KeyboardArrowRight, size = 32.dp)
                    }
                    Spacer(Modifier.height(16.dp))
                    val pickerDates = calendarMonthRange(pickerDate, firstWeekday).dates
                    Row(Modifier.fillMaxWidth()) {
                        pickerDates.take(7).forEach {
                            Text(CalendarPlatform.weekdayLabel(it).uppercase(), Modifier.weight(1f), textAlign = TextAlign.Center,
                                style = redesignText(11.sp, FontWeight.Medium, RedesignColors.textMuted, letterSpacing = 0.1.em), maxLines = 1)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    androidx.compose.animation.AnimatedContent(
                        targetState = pickerDate.take(7),
                        transitionSpec = {
                            val sign = if (direction >= 0) 1 else -1
                            (androidx.compose.animation.slideInHorizontally(tween(240)) { sign * it / 6 } + androidx.compose.animation.fadeIn(tween(240))) togetherWith
                                (androidx.compose.animation.slideOutHorizontally(tween(240)) { -sign * it / 6 } + androidx.compose.animation.fadeOut(tween(160)))
                        },
                        label = "picker_month_grid",
                    ) { month ->
                        val dates = calendarMonthGridRange("$month-01", firstWeekday).dates
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            dates.chunked(7).forEach { week ->
                                Row(Modifier.fillMaxWidth()) {
                                    week.forEach { date ->
                                        PickerDay(date, inMonth = date.startsWith(month), isToday = date == today, isSelected = date == initialDate,
                                            tag = "$tagPrefix-picker-$date", modifier = Modifier.weight(1f)) { select(date) }
                                    }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        RedesignPillButton(stringResource(Res.string.calendar_today), { select(today) }, Modifier.testTag("$tagPrefix-picker-today"))
                    }
                }
            }
        }
    }
}

@Composable
private fun PickerDay(date: String, inMonth: Boolean, isToday: Boolean, isSelected: Boolean, tag: String, modifier: Modifier, onClick: () -> Unit) {
    val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val fill by androidx.compose.animation.animateColorAsState(
        when { isSelected -> RedesignColors.textPrimary; hovered -> RedesignColors.fillStrong; else -> Color.Transparent }, tween(160), label = "picker_day_fill",
    )
    val ring = if (isToday && !isSelected) RedesignColors.hairlineFocused else Color.Transparent
    Box(modifier.height(42.dp).testTag(tag).hoverable(interaction).clickable(interaction, indication = null, onClick = onClick), contentAlignment = Alignment.Center) {
        Text(date.takeLast(2).toInt().toString(), textAlign = TextAlign.Center,
            style = redesignText(14.sp, if (isSelected || isToday) FontWeight.Bold else FontWeight.Medium, when {
                isSelected -> RedesignOnLight
                !inMonth -> RedesignColors.textMuted.copy(alpha = 0.55f)
                else -> RedesignColors.textPrimary
            }),
            modifier = Modifier.size(36.dp).clip(CircleShape).background(fill).border(1.dp, ring, CircleShape).wrapContentSize())
    }
}

internal fun calendarToday() = EpisodeReleaseDatePlatform.localIsoDateAtEpochMs(EpisodeReleaseDatePlatform.nowEpochMs())!!
@Composable private fun ReleaseCount(count: Int, modifier: Modifier = Modifier) {
    Text(pluralStringResource(Res.plurals.calendar_release_count, count, count), modifier = modifier, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.nuvio.colors.textMuted)
}

/** Day agenda heading (shared with History): day number, weekday over month, a Today pill, the count. */
@Composable internal fun DaySectionHeading(date: String, count: Int, today: Boolean, watchHistory: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 4.dp).testTag("calendar-day-heading-$date"), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(date.takeLast(2).toInt().toString(), style = redesignText(30.sp, FontWeight.Bold, letterSpacing = RedesignTightTracking),
            modifier = Modifier.widthIn(min = 40.dp))
        Column {
            Text(CalendarPlatform.dateLabel(date, "EEEE"), style = redesignText(16.sp, FontWeight.SemiBold))
            Text(CalendarPlatform.dateLabel(date, "MMMM"), style = redesignText(12.sp, color = RedesignColors.textMuted))
        }
        if (today) TodayPill(Modifier.testTag("calendar-today"))
        Spacer(Modifier.weight(1f))
        if (watchHistory) Text(
            pluralStringResource(Res.plurals.history_watch_count, count, count),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.nuvio.colors.textMuted,
        ) else ReleaseCount(count)
    }
}

@Composable private fun TodayPill(modifier: Modifier = Modifier) {
    Text(stringResource(Res.string.calendar_today), style = redesignText(11.5.sp, FontWeight.Bold, RedesignOnLight),
        modifier = modifier.clip(CircleShape).background(RedesignColors.textPrimary).padding(horizontal = 9.dp, vertical = 3.dp))
}

@Composable private fun DateHeading(date: String, count: Int?) {
    Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(CalendarPlatform.dayLabel(date), style = redesignText(17.sp, FontWeight.Bold, letterSpacing = RedesignTightTracking))
        if (count != null) ReleaseCount(count)
    }
}
@Composable private fun WeekdayHeader(dates: List<String>) { Row(Modifier.fillMaxWidth()) { dates.forEach { Text(CalendarPlatform.weekdayLabel(it), Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelMedium) } } }

/** Week column heading: large day number with a weekday kicker ("TODAY · MON" on the current date). */
@Composable private fun DayNumber(date: String, today: Boolean, selected: Boolean, onClick: () -> Unit) {
    val weekday = CalendarPlatform.weekdayLabel(date)
    val kicker = if (today) "${stringResource(Res.string.calendar_today)} · $weekday" else weekday
    val color by androidx.compose.animation.animateColorAsState(
        if (today || selected) RedesignColors.textPrimary else RedesignColors.textSecondary, tween(180), label = "week_day_number",
    )
    Row(
        Modifier.testTag(if (today) "calendar-today" else "calendar-day-label-$date")
            .clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick).padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(date.takeLast(2).toInt().toString(), style = redesignText(26.sp, FontWeight.Bold, color, letterSpacing = RedesignTightTracking))
        Text(kicker.uppercase(), style = redesignText(11.sp, FontWeight.Medium, if (today) RedesignColors.textSecondary else RedesignColors.textMuted, letterSpacing = 0.1.em),
            modifier = Modifier.padding(bottom = 5.dp), maxLines = 1)
    }
}

private val WeekColumnGap = 12.dp
private val WeekColumnPadding = 10.dp

/**
 * Seven frosted day tiles of equal height. The tiles are drawn behind the measured columns, so
 * every day matches the tallest one without intrinsic measurement of the artwork cards.
 */
@Composable
private fun CalendarWeekColumns(
    week: List<String>,
    today: String,
    columnWidth: Dp,
    content: @Composable (Int, String) -> Unit,
) {
    val todayIndex = week.indexOf(today)
    val density = LocalDensity.current
    Layout(
        content = { week.forEachIndexed { index, date -> Box { content(index, date) } } },
        modifier = Modifier.drawBehind {
            val radius = with(density) { 18.dp.toPx() }
            val half = with(density) { (WeekColumnGap / 2).toPx() }
            val stroke = with(density) { 1.dp.toPx() }
            val column = with(density) { columnWidth.toPx() }
            week.indices.forEach { index ->
                val current = index == todayIndex
                val left = index * column + half
                val tileSize = androidx.compose.ui.geometry.Size(column - half * 2, size.height)
                drawRoundRect(
                    color = if (current) RedesignColors.fillStrong else Color.White.copy(alpha = 0.05f),
                    topLeft = Offset(left, 0f), size = tileSize,
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius),
                )
                drawRoundRect(
                    color = if (current) Color.White.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.08f),
                    topLeft = Offset(left + stroke / 2, stroke / 2),
                    size = androidx.compose.ui.geometry.Size(tileSize.width - stroke, tileSize.height - stroke),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(stroke),
                )
            }
        },
    ) { measurables, constraints ->
        val column = columnWidth.roundToPx()
        val inset = (WeekColumnGap / 2 + WeekColumnPadding).roundToPx()
        val childWidth = (column - inset * 2).coerceAtLeast(0)
        val placeables = measurables.map { it.measure(Constraints.fixedWidth(childWidth)) }
        val vertical = WeekColumnPadding.roundToPx()
        val height = maxOf((placeables.maxOfOrNull { it.height } ?: 0) + vertical * 2, 160.dp.roundToPx())
        layout(column * week.size, height) {
            placeables.forEachIndexed { index, placeable -> placeable.place(index * column + inset, vertical) }
        }
    }
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

/**
 * Month: the six-row grid in a frosted tile. On wide panes the selected day's releases sit in a
 * side column (Home board's 352 dp column); narrower panes keep them in a shelf below the grid.
 */
@Composable private fun CalendarMonthBody(selected: String, today: String, groups: Map<String, List<CalendarCard>>, select: (String) -> Unit, shape: PosterShape, onPosterClick: ((MetaPreview) -> Unit)?, firstWeekday: Int, loading: Boolean, move: (Int) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val sideColumn = maxWidth >= HomeBoardSideColumnMinWidth
        if (sideColumn) {
            Row(Modifier.fillMaxSize().padding(bottom = 24.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                MonthGridTile(selected, today, groups, select, shape, firstWeekday, move, Modifier.weight(1f).fillMaxHeight())
                CalendarSelectedDayColumn(selected, groups, loading, onPosterClick, Modifier.width(CalendarSideColumnWidth).fillMaxHeight())
            }
        } else {
            val available = maxHeight
            val weekCount = 6
            val shelfHeight = (available * .46f).coerceIn(220.dp, 340.dp).coerceAtMost(available * .6f)
            val cellHeight = ((available - shelfHeight - 26.dp - 6.dp * (weekCount - 1)) / weekCount).coerceAtLeast(40.dp)
            val gridWidth = (cellHeight * 1.75f * 7 + 24.dp).coerceAtMost(maxWidth).coerceAtLeast(maxWidth.coerceAtMost(644.dp))
            val artHeight = (shelfHeight - 112.dp).coerceAtLeast(36.dp)
            val cardWidth = if (shape == PosterShape.Landscape) artHeight * 1.77f else artHeight * (2f / 3f)
            Column(Modifier.fillMaxSize()) {
                MonthGridTile(selected, today, groups, select, shape, firstWeekday, move, Modifier.weight(1f).width(gridWidth).align(Alignment.CenterHorizontally))
                Column(Modifier.fillMaxWidth().height(shelfHeight).testTag("calendar-selected-day")) {
                    val selectedCards = groups[selected].orEmpty()
                    DateHeading(selected, selectedCards.size.takeIf { selectedCards.isNotEmpty() || !loading })
                    if (selectedCards.isNotEmpty()) CalendarShelf(selectedCards, onPosterClick, cardWidth)
                    else if (!loading) Text(stringResource(Res.string.calendar_day_empty), Modifier.padding(vertical = 12.dp), style = redesignText(14.sp, color = RedesignColors.textSecondary))
                }
            }
        }
    }
}

private val CalendarSideColumnWidth = 352.dp

@Composable private fun MonthGridTile(selected: String, today: String, groups: Map<String, List<CalendarCard>>, select: (String) -> Unit, shape: PosterShape, firstWeekday: Int, move: (Int) -> Unit, modifier: Modifier) {
    Box(modifier.clip(RedesignTileShape).background(RedesignColors.fill).border(1.dp, RedesignColors.hairline, RedesignTileShape).padding(12.dp)) {
        CalendarMonthPager(selected, firstWeekday, Modifier.fillMaxSize(), move) { pageDates, interactive, pageMonth ->
            Column(Modifier.fillMaxSize().then(if (interactive) Modifier else Modifier.clearAndSetSemantics {})) {
                Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    pageDates.take(7).forEach {
                        Text(CalendarPlatform.weekdayLabel(it).uppercase(), Modifier.weight(1f).padding(start = 8.dp),
                            style = redesignText(11.sp, FontWeight.Medium, RedesignColors.textMuted, letterSpacing = 0.1.em), maxLines = 1)
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    pageDates.chunked(7).forEach { week ->
                        MonthWeek(week, pageMonth, selected, today, groups, if (interactive) select else { _ -> }, shape, Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable private fun CalendarSelectedDayColumn(selected: String, groups: Map<String, List<CalendarCard>>, loading: Boolean, onPosterClick: ((MetaPreview) -> Unit)?, modifier: Modifier) {
    Column(modifier.clip(RedesignTileShape).background(RedesignColors.fill).border(1.dp, RedesignColors.hairline, RedesignTileShape)
        .padding(horizontal = 10.dp, vertical = 16.dp).testTag("calendar-selected-day")) {
        val selectedCards = groups[selected].orEmpty()
        Column(Modifier.padding(horizontal = 8.dp)) {
            Text(CalendarPlatform.dateLabel(selected, "EEEE").uppercase(), style = redesignText(11.sp, FontWeight.Medium, RedesignColors.textMuted, letterSpacing = 0.1.em))
            Spacer(Modifier.height(4.dp))
            Text(CalendarPlatform.dateLabel(selected, "d MMMM"), style = redesignText(22.sp, FontWeight.Bold, letterSpacing = RedesignTightTracking), maxLines = 1)
            Box(Modifier.height(18.dp)) {
                if (selectedCards.isNotEmpty() || !loading) ReleaseCount(selectedCards.size, Modifier.padding(top = 2.dp))
            }
        }
        Spacer(Modifier.height(10.dp))
        // Changing the selected date crossfades the list instead of swapping it in one frame.
        Crossfade(targetState = selected, animationSpec = tween(220), modifier = Modifier.weight(1f), label = "calendar_selected_day") { date ->
            val dayCards = groups[date].orEmpty()
            if (dayCards.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(horizontal = 8.dp)) {
                    if (!loading) Text(stringResource(Res.string.calendar_day_empty), style = redesignText(14.sp, color = RedesignColors.textSecondary))
                }
            } else {
                LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    items(dayCards, key = { it.key }) { card ->
                        CalendarSideRow(card, onPosterClick, Modifier.animateItem())
                    }
                }
            }
        }
    }
}

@Composable private fun CalendarSideRow(card: CalendarCard, onPosterClick: ((MetaPreview) -> Unit)?, modifier: Modifier = Modifier) {
    val style = rememberPosterCardStyleUiState()
    val item = card.toPreview()
    val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val fill by androidx.compose.animation.animateColorAsState(if (hovered) RedesignColors.fill else Color.Transparent, tween(160), label = "calendar_side_row")
    val time = card.releaseEpochMs?.let { start ->
        formatLocalTime(start) + (card.lastReleaseEpochMs?.takeIf { it != start }?.let { "–${formatLocalTime(it)}" } ?: "")
    }
    val tag = when {
        card.premiere -> stringResource(Res.string.calendar_premiere)
        card.finale -> stringResource(Res.string.calendar_finale)
        else -> null
    }
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(fill)
            .hoverable(interaction)
            .then(if (onPosterClick != null) Modifier.clickable(interactionSource = interaction, indication = null) { onPosterClick(item) } else Modifier)
            .padding(6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CompositionLocalProvider(LocalPosterCardStyleOverride provides style.copy(catalogLandscapeModeEnabled = true, cornerRadiusDp = 10)) {
            CalendarMediaCard(card, Modifier.width(120.dp), compact = true, onPosterClick = onPosterClick)
        }
        Column(Modifier.weight(1f)) {
            Text(card.media.title, style = redesignText(14.sp, FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
            val episodeTitle = card.episodes.singleOrNull()?.title
            val detail = listOfNotNull(card.episodeLabel ?: calendarMovieLabel(card.media.year), episodeTitle).joinToString("  ")
            if (detail.isNotBlank()) Text(detail, style = redesignText(12.sp, color = RedesignColors.textMuted), maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (time != null || tag != null) Text(listOfNotNull(time, tag).joinToString(" · "), style = redesignText(12.sp, color = RedesignColors.textSecondary), modifier = Modifier.padding(top = 4.dp), maxLines = 1)
        }
    }
}

@Composable private fun MonthWeek(dates: List<String>, month: String, selected: String, today: String, groups: Map<String, List<CalendarCard>>, select: (String) -> Unit, shape: PosterShape, modifier: Modifier) {
    val style = rememberPosterCardStyleUiState()
    val cellShape = RoundedCornerShape(12.dp)
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        dates.forEach { date ->
            if (!date.startsWith(month)) { Spacer(Modifier.weight(1f).fillMaxHeight()); return@forEach }
            val isSelected = date == selected
            val hasReleases = !groups[date].isNullOrEmpty()
            val background by androidx.compose.animation.animateColorAsState(
                when { isSelected -> RedesignColors.fillStrong; hasReleases -> Color.White.copy(alpha = 0.04f); else -> Color.Transparent },
                tween(180), label = "calendar_cell",
            )
            val border by androidx.compose.animation.animateColorAsState(
                if (isSelected) Color.White.copy(alpha = 0.45f) else Color.Transparent, tween(180), label = "calendar_cell_border",
            )
            Column(Modifier.weight(1f).fillMaxHeight()
                .clip(cellShape).background(background, cellShape).border(1.dp, border, cellShape)
                .clickable { select(date) }.padding(6.dp).testTag("calendar-date-$date").semantics { contentDescription = CalendarPlatform.dayLabel(date); this.selected = isSelected }) {
                val isToday = date == today
                Text(if (date.endsWith("-01")) "1 ${CalendarPlatform.shortMonthLabel(date)}" else date.takeLast(2).toInt().toString(),
                    style = redesignText(13.sp, FontWeight.SemiBold, if (isToday) RedesignOnLight else if (isSelected) RedesignColors.textPrimary else RedesignColors.textSecondary),
                    modifier = Modifier.clip(CircleShape).background(if (isToday) RedesignColors.textPrimary else Color.Transparent).padding(horizontal = 7.dp, vertical = 1.dp))
                val thumbnails = groups[date].orEmpty().take(3)
                val extra = groups[date].orEmpty().size - thumbnails.size
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                    val ratio = if (shape == PosterShape.Landscape) 1.77f else 2f / 3f
                    val countWidth = if (extra > 0) 24.dp else 0.dp
                    val width = ((maxWidth - countWidth - 6.dp) / 3).coerceAtLeast(1.dp).coerceAtMost(maxHeight.coerceAtLeast(1.dp) * ratio)
                    CompositionLocalProvider(LocalPosterCardStyleOverride provides style.copy(cornerRadiusDp = 5)) {
                        Row(Modifier.align(Alignment.BottomStart), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.Bottom) {
                            thumbnails.forEach { card -> CalendarMediaCard(card, Modifier.width(width), compact = true, onPosterClick = { select(date) }) }
                            if (extra > 0) Text("+$extra", style = redesignText(11.sp, FontWeight.SemiBold, RedesignColors.textSecondary))
                        }
                    }
                }
            }
        }
    }
}
