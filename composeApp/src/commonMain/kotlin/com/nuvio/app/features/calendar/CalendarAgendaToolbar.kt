package com.nuvio.app.features.calendar

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nuvio.app.core.ui.NuvioDropdownChip
import com.nuvio.app.core.ui.NuvioDropdownOption
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun CalendarAgendaToolbar(
    selected: String,
    filter: CalendarFilter,
    landscape: Boolean,
    loading: Boolean,
    openPicker: () -> Unit,
    goToday: () -> Unit,
    selectFilter: (CalendarFilter) -> Unit,
    toggleOrientation: () -> Unit,
    tagPrefix: String = "calendar",
    previousMonth: (() -> Unit)? = null,
    nextMonth: (() -> Unit)? = null,
    view: CalendarView? = null,
    selectView: (CalendarView) -> Unit = {},
) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val labelStyle = MaterialTheme.typography.labelLarge
    val monthLabel = CalendarPlatform.monthLabel(selected) + " ▾"
    val todayLabel = stringResource(Res.string.calendar_today)
    val filterLabels = CalendarFilter.entries.map { filterLabel(it) }
    val viewLabels = if (view == null) emptyList() else CalendarView.entries.map { viewLabel(it) }
    fun labelWidth(label: String): Dp = with(density) { measurer.measure(label, labelStyle).size.width.toDp() }
    val monthWidth = (1..12).maxOf { month ->
        labelWidth(CalendarPlatform.monthLabel("${selected.take(4)}-${month.toString().padStart(2, '0')}-01") + " ▾").value
    }.dp + 32.dp
    // Measure the localized labels so a larger font or longer translation also reflows.
    val filterWidth = filterLabels.sumOf { (labelWidth(it) + 32.dp).value.toDouble() }.toFloat().dp + 12.dp
    val viewWidth = viewLabels.sumOf { (labelWidth(it) + 32.dp).value.toDouble() }.toFloat().dp + 12.dp
    val wideWidth = monthWidth + 96.dp + labelWidth(todayLabel) + 32.dp + filterWidth + viewWidth + 96.dp

    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val compact = maxWidth < wideWidth
        Column(Modifier.fillMaxWidth().testTag("$tagPrefix-toolbar")) {
            if (compact) {
                Row(
                    Modifier.fillMaxWidth().height(52.dp).testTag("$tagPrefix-toolbar-primary"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    AgendaDateButton(monthLabel, openPicker, tagPrefix, Modifier.weight(1f), compact = true)
                    TextButton(onClick = goToday, modifier = Modifier.testTag("$tagPrefix-today-button")) { Text(todayLabel) }
                    AgendaOrientationButton(landscape, toggleOrientation, tagPrefix)
                }
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(bottom = 8.dp)
                        .testTag("$tagPrefix-toolbar-secondary"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (view != null) {
                        val title = stringResource(Res.string.calendar_view_label)
                        NuvioDropdownChip(
                            title = title,
                            label = viewLabel(view),
                            selectedKey = view.name,
                            options = CalendarView.entries.map { NuvioDropdownOption(it.name, viewLabel(it)) },
                            onSelected = { selectView(CalendarView.valueOf(it.key)) },
                            modifier = Modifier.testTag("$tagPrefix-view-picker").semantics(mergeDescendants = true) {
                                contentDescription = title
                            },
                        )
                    }
                    AgendaFilterSegments(filter, selectFilter, tagPrefix, Modifier.weight(1f))
                }
            } else {
                Row(
                    Modifier.fillMaxWidth().height(52.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    AgendaDateButton(monthLabel, openPicker, tagPrefix, Modifier.width(monthWidth))
                    AgendaMonthArrow(previousMonth, previous = true)
                    TextButton(onClick = goToday, modifier = Modifier.testTag("$tagPrefix-today-button")) { Text(todayLabel) }
                    AgendaMonthArrow(nextMonth, previous = false)
                    if (view != null) {
                        Spacer(Modifier.width(8.dp))
                        CalendarView.entries.forEach { option ->
                            FilterChip(view == option, { selectView(option) },
                                label = { Text(viewLabel(option)) },
                                modifier = Modifier.testTag("$tagPrefix-view-${option.name}"))
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    AgendaFilterSegments(filter, selectFilter, tagPrefix, Modifier.width(filterWidth.coerceAtLeast(240.dp)))
                    Spacer(Modifier.weight(1f))
                    AgendaOrientationButton(landscape, toggleOrientation, tagPrefix)
                }
            }
            Box(Modifier.fillMaxWidth().height(4.dp).testTag("$tagPrefix-loading-strip")) {
                if (loading) LinearProgressIndicator(Modifier.fillMaxSize().testTag("$tagPrefix-loading"))
            }
        }
    }
}

@Composable
private fun AgendaDateButton(
    label: String,
    onClick: () -> Unit,
    tagPrefix: String,
    modifier: Modifier,
    compact: Boolean = false,
) {
    TextButton(
        onClick = onClick,
        modifier = modifier.testTag("$tagPrefix-date-picker-button"),
        contentPadding = PaddingValues(horizontal = if (compact) 8.dp else 16.dp),
    ) {
        Text(label, modifier = if (compact) Modifier.fillMaxWidth() else Modifier, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun AgendaOrientationButton(landscape: Boolean, onClick: () -> Unit, tagPrefix: String) {
    IconButton(onClick = onClick,
        modifier = Modifier.testTag("$tagPrefix-landscape").semantics {
            stateDescription = if (landscape) "Landscape" else "Portrait"
        }) {
        Icon(
            painterResource(if (landscape) Res.drawable.calendar_posters_landscape else Res.drawable.calendar_posters_portrait),
            stringResource(if (landscape) Res.string.calendar_orientation_landscape else Res.string.calendar_orientation_portrait),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AgendaMonthArrow(action: (() -> Unit)?, previous: Boolean) {
    Box(Modifier.size(48.dp)) {
        if (action != null) IconButton(onClick = action) {
            Icon(
                if (previous) Icons.AutoMirrored.Filled.KeyboardArrowLeft else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                stringResource(if (previous) Res.string.calendar_previous else Res.string.calendar_next),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AgendaFilterSegments(
    filter: CalendarFilter,
    selectFilter: (CalendarFilter) -> Unit,
    tagPrefix: String,
    modifier: Modifier,
) {
    SingleChoiceSegmentedButtonRow(modifier) {
        CalendarFilter.entries.forEachIndexed { index, option ->
            SegmentedButton(
                selected = filter == option,
                onClick = { selectFilter(option) },
                shape = SegmentedButtonDefaults.itemShape(index, CalendarFilter.entries.size),
                icon = {},
                contentPadding = PaddingValues(0.dp),
                modifier = Modifier.weight(1f).testTag("$tagPrefix-filter-${option.name}"),
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    activeContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            ) {
                Text(filterLabel(option), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun viewLabel(view: CalendarView) = stringResource(when (view) {
    CalendarView.Day -> Res.string.calendar_view_day
    CalendarView.Week -> Res.string.calendar_view_week
    CalendarView.Month -> Res.string.calendar_view_month
})

@Composable
private fun filterLabel(filter: CalendarFilter) = stringResource(when (filter) {
    CalendarFilter.All -> Res.string.calendar_filter_all
    CalendarFilter.Shows -> Res.string.calendar_filter_shows
    CalendarFilter.Movies -> Res.string.calendar_filter_movies
})
