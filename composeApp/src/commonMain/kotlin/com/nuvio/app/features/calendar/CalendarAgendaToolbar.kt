package com.nuvio.app.features.calendar

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.nuvio.app.core.ui.NuvioDropdownChip
import com.nuvio.app.core.ui.NuvioDropdownOption
import com.nuvio.app.core.ui.RedesignColors
import com.nuvio.app.core.ui.RedesignIconButton
import com.nuvio.app.core.ui.RedesignPillButton
import com.nuvio.app.core.ui.RedesignKicker
import com.nuvio.app.core.ui.RedesignPillGroup
import com.nuvio.app.core.ui.RedesignTightTracking
import com.nuvio.app.core.ui.redesignText
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Header for the Calendar and History agendas on the redesign shell: an optional kicker and large
 * title (which opens the date picker), then ‹ Today ›, the view and media-filter pill groups and the
 * orientation button. Controls keep their positions across views; narrow panes wrap to two rows.
 */
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
    title: String? = null,
    subtitle: String? = null,
    kicker: String? = null,
) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val pillStyle = redesignText(13.sp, FontWeight.SemiBold)
    val titleStyle = redesignText(34.sp, FontWeight.Bold, letterSpacing = RedesignTightTracking)
    val monthLabel = CalendarPlatform.monthLabel(selected)
    val todayLabel = stringResource(Res.string.calendar_today)
    val filterLabels = CalendarFilter.entries.map { filterLabel(it) }
    val viewLabels = if (view == null) emptyList() else CalendarView.entries.map { viewLabel(it) }
    fun labelWidth(label: String, style: androidx.compose.ui.text.TextStyle = pillStyle): Dp =
        with(density) { measurer.measure(label, style).size.width.toDp() }
    // Measure the localized labels so a larger font or longer translation also reflows.
    val filterWidth = filterLabels.sumOf { (labelWidth(it) + 28.dp).value.toDouble() }.toFloat().dp + 8.dp
    val viewWidth = if (view == null) 0.dp else viewLabels.sumOf { (labelWidth(it) + 28.dp).value.toDouble() }.toFloat().dp + 8.dp
    // The title is a month label; size its button for the widest month so neither a month nor a
    // view change moves it (or the controls after it).
    val titleWidth = if (title == null) 0.dp else maxOf(
        labelWidth(title, titleStyle).value,
        (1..12).maxOf { month -> labelWidth(CalendarPlatform.monthLabel("${selected.take(4)}-${month.toString().padStart(2, '0')}-01"), titleStyle).value },
    ).dp + 2.dp
    val leadWidth = if (title != null) titleWidth.coerceAtMost(200.dp) + 30.dp
    else labelWidth(monthLabel) + 60.dp
    val wideWidth = leadWidth + 36.dp * 2 + labelWidth(todayLabel) + 32.dp + filterWidth + viewWidth + 36.dp + 12.dp * 6 + 24.dp

    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val compact = maxWidth < wideWidth
        Column(Modifier.fillMaxWidth().testTag("$tagPrefix-toolbar")) {
            if (compact) {
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("$tagPrefix-toolbar-primary"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AgendaLead(title, subtitle, kicker, monthLabel, openPicker, tagPrefix, Modifier.weight(1f), compact = true)
                    RedesignPillButton(todayLabel, goToday, Modifier.testTag("$tagPrefix-today-button"))
                    AgendaOrientationButton(landscape, toggleOrientation, tagPrefix)
                }
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(top = 6.dp, bottom = 8.dp)
                        .testTag("$tagPrefix-toolbar-secondary"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (view != null) {
                        val pickerTitle = stringResource(Res.string.calendar_view_label)
                        NuvioDropdownChip(
                            title = pickerTitle,
                            label = viewLabel(view),
                            selectedKey = view.name,
                            options = CalendarView.entries.map { NuvioDropdownOption(it.name, viewLabel(it)) },
                            onSelected = { selectView(CalendarView.valueOf(it.key)) },
                            modifier = Modifier.testTag("$tagPrefix-view-picker").semantics(mergeDescendants = true) {
                                contentDescription = pickerTitle
                            },
                        )
                    }
                    AgendaFilterPills(filter, selectFilter, tagPrefix, Modifier.weight(1f), fill = true)
                }
            } else {
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(bottom = 8.dp),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    AgendaLead(title, subtitle, kicker, monthLabel, openPicker, tagPrefix, Modifier.weight(1f), titleWidth = titleWidth)
                    AgendaArrow(previousMonth, previous = true)
                    RedesignPillButton(todayLabel, goToday, Modifier.testTag("$tagPrefix-today-button"))
                    AgendaArrow(nextMonth, previous = false)
                    if (view != null) {
                        Spacer(Modifier.width(2.dp))
                        RedesignPillGroup(
                            options = CalendarView.entries,
                            selected = view,
                            label = { viewLabel(it) },
                            onSelect = selectView,
                            optionTag = { "$tagPrefix-view-${it.name}" },
                        )
                    }
                    AgendaFilterPills(filter, selectFilter, tagPrefix, Modifier)
                    AgendaOrientationButton(landscape, toggleOrientation, tagPrefix)
                }
            }
            Box(Modifier.fillMaxWidth().height(4.dp).testTag("$tagPrefix-loading-strip")) {
                if (loading) LinearProgressIndicator(
                    Modifier.fillMaxSize().clip(RoundedCornerShape(2.dp)).testTag("$tagPrefix-loading"),
                    color = RedesignColors.textSecondary,
                    trackColor = RedesignColors.fill,
                )
            }
        }
    }
}

/**
 * Left side of the header. With a [title] it is the page's kicker + large title (the title opens
 * the date picker); without one (History) it is a month pill that opens the picker.
 */
@Composable
private fun AgendaLead(
    title: String?,
    subtitle: String?,
    kicker: String?,
    monthLabel: String,
    openPicker: () -> Unit,
    tagPrefix: String,
    modifier: Modifier,
    compact: Boolean = false,
    titleWidth: Dp = Dp.Unspecified,
) {
    if (title == null) {
        Box(modifier, contentAlignment = Alignment.CenterStart) {
            RedesignPillButton(
                monthLabel,
                openPicker,
                Modifier.testTag("$tagPrefix-date-picker-button"),
                trailing = Icons.Rounded.KeyboardArrowDown,
            )
        }
        return
    }
    Column(modifier) {
        if (kicker != null) {
            RedesignKicker(kicker)
            Spacer(Modifier.height(4.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // Only the title opens the picker, so its bounds stay put while the subtitle changes.
            Row(
                Modifier.then(if (titleWidth != Dp.Unspecified && !compact) Modifier else Modifier.weight(1f, fill = false))
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Button, onClick = openPicker)
                    .testTag("$tagPrefix-date-picker-button"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                AnimatedContent(
                    targetState = title,
                    transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(160)) },
                    modifier = if (titleWidth != Dp.Unspecified && !compact) Modifier.width(titleWidth) else Modifier.weight(1f, fill = false),
                    label = "agenda_title",
                ) { shown ->
                    Text(
                        shown,
                        style = redesignText(if (compact) 24.sp else 34.sp, FontWeight.Bold, letterSpacing = RedesignTightTracking),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = null, tint = RedesignColors.textMuted, modifier = Modifier.padding(top = 4.dp).size(22.dp))
            }
            if (subtitle != null && !compact) {
                // The subtitle yields space (and ellipsizes) before the fixed-width title would move.
                AnimatedContent(
                    targetState = subtitle,
                    transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(160)) },
                    modifier = Modifier.weight(1f, fill = false),
                    label = "agenda_subtitle",
                ) { shown ->
                    Text(shown, style = redesignText(15.sp, color = RedesignColors.textSecondary), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
                }
            }
        }
    }
}

@Composable
private fun AgendaOrientationButton(landscape: Boolean, onClick: () -> Unit, tagPrefix: String) {
    RedesignIconButton(
        contentDescription = stringResource(if (landscape) Res.string.calendar_orientation_landscape else Res.string.calendar_orientation_portrait),
        onClick = onClick,
        painter = painterResource(if (landscape) Res.drawable.calendar_posters_landscape else Res.drawable.calendar_posters_portrait),
        modifier = Modifier.testTag("$tagPrefix-landscape").semantics {
            stateDescription = if (landscape) "Landscape" else "Portrait"
        },
    )
}

/** Reserved slot even when [action] is null, so the controls after it never shift. */
@Composable
private fun AgendaArrow(action: (() -> Unit)?, previous: Boolean) {
    RedesignIconButton(
        contentDescription = stringResource(if (previous) Res.string.calendar_previous else Res.string.calendar_next),
        onClick = action,
        vector = if (previous) Icons.AutoMirrored.Rounded.KeyboardArrowLeft else Icons.AutoMirrored.Rounded.KeyboardArrowRight,
    )
}

@Composable
private fun AgendaFilterPills(
    filter: CalendarFilter,
    selectFilter: (CalendarFilter) -> Unit,
    tagPrefix: String,
    modifier: Modifier,
    fill: Boolean = false,
) {
    RedesignPillGroup(
        options = CalendarFilter.entries,
        selected = filter,
        label = { filterLabel(it) },
        onSelect = selectFilter,
        modifier = modifier,
        optionTag = { "$tagPrefix-filter-${it.name}" },
        fill = fill,
    )
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
