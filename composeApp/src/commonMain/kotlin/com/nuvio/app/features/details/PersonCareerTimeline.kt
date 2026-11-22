package com.nuvio.app.features.details

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nuvio.app.core.ui.NuvioShelfSectionHeader
import com.nuvio.app.core.ui.NuvioViewAllPillSize
import com.nuvio.app.core.ui.RedesignColors
import com.nuvio.app.core.ui.RedesignTabs
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.core.ui.redesignText
import com.nuvio.app.features.details.components.InlineTmdbRating
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.home.components.HomePosterCard
import com.nuvio.app.features.home.stableKey
import com.nuvio.app.features.watching.application.WatchingState
import kotlinx.datetime.LocalDate
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.person_career_acting
import nuvio.composeapp.generated.resources.person_career_creator
import nuvio.composeapp.generated.resources.person_career_crew
import nuvio.composeapp.generated.resources.person_career_directing
import nuvio.composeapp.generated.resources.person_career_production
import nuvio.composeapp.generated.resources.person_career_self
import nuvio.composeapp.generated.resources.person_career_summary
import nuvio.composeapp.generated.resources.person_career_summary_one_year
import nuvio.composeapp.generated.resources.person_career_timeline
import nuvio.composeapp.generated.resources.person_career_title_count
import nuvio.composeapp.generated.resources.person_career_writing
import nuvio.composeapp.generated.resources.person_credit_episode_count
import nuvio.composeapp.generated.resources.person_credit_episode_minimum
import nuvio.composeapp.generated.resources.person_credit_tv_movie
import nuvio.composeapp.generated.resources.person_credit_tv_series
import nuvio.composeapp.generated.resources.person_credit_video
import nuvio.composeapp.generated.resources.person_upcoming
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

@Composable
private fun CareerDepartment.label(): String = stringResource(
    when (this) {
        CareerDepartment.Acting -> Res.string.person_career_acting
        CareerDepartment.Directing -> Res.string.person_career_directing
        CareerDepartment.Writing -> Res.string.person_career_writing
        CareerDepartment.Production -> Res.string.person_career_production
        CareerDepartment.Creator -> Res.string.person_career_creator
        CareerDepartment.Crew -> Res.string.person_career_crew
        CareerDepartment.Self -> Res.string.person_career_self
    },
)

private fun ActingCredit.period(today: LocalDate): ActingPeriod {
    val date = media.creditDate()
    return when {
        date == null -> ActingPeriod.Unknown
        date > today -> ActingPeriod.Upcoming
        else -> ActingPeriod.Year(date.year)
    }
}

/**
 * The Person page's career timeline: a heading with the tab's span and count, one tab per
 * department the person has credits in, then one row per title under a single label per year.
 * [credits] is the selected tab's list, newest first. [redesign] is the wide desktop style.
 */
internal fun LazyListScope.personCareerTimeline(
    tabs: List<CareerDepartment>,
    selected: CareerDepartment?,
    counts: Map<CareerDepartment, Int>,
    credits: List<ActingCredit>,
    today: LocalDate,
    padding: Dp,
    redesign: Boolean,
    watchedKeys: Set<String>,
    fullyWatchedSeriesKeys: Set<String>,
    onSelect: (CareerDepartment) -> Unit,
    onOpenMeta: (MetaPreview) -> Unit,
) {
    if (selected == null) return
    item("timeline-heading") {
        Column(Modifier.fillMaxWidth().padding(top = 24.dp)) {
            NuvioShelfSectionHeader(
                stringResource(Res.string.person_career_timeline),
                Modifier.padding(horizontal = padding),
                viewAllPillSize = NuvioViewAllPillSize.Compact,
            )
            val muted = if (redesign) RedesignColors.textMuted else MaterialTheme.nuvio.colors.textMuted
            val titles = pluralStringResource(Res.plurals.person_career_title_count, credits.size, credits.size)
            val span = remember(credits, today) { careerYearSpan(credits, today) }
            Text(
                when {
                    span == null -> titles
                    span.first == span.second -> stringResource(Res.string.person_career_summary_one_year, span.first, titles)
                    else -> stringResource(Res.string.person_career_summary, span.first, span.second, titles)
                },
                Modifier.padding(horizontal = padding).padding(top = 4.dp).testTag("person_career_summary"),
                style = MaterialTheme.typography.bodyMedium,
                color = muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (tabs.size > 1) {
                RedesignTabs(
                    items = tabs,
                    selected = selected,
                    label = { it.label() },
                    onSelect = onSelect,
                    modifier = Modifier.padding(horizontal = padding).padding(top = 10.dp).testTag("person_career_tabs"),
                    count = { counts[it] },
                    selectedColor = if (redesign) RedesignColors.textPrimary else MaterialTheme.nuvio.colors.textPrimary,
                    hoverColor = if (redesign) RedesignColors.textSecondary else MaterialTheme.nuvio.colors.textPrimary,
                    mutedColor = muted,
                )
            }
            Spacer(Modifier.padding(top = 8.dp))
        }
    }
    // The department is part of the key, so switching tabs fades rows out and in instead of
    // sliding one title between two lists.
    itemsIndexed(credits, key = { _, credit -> "timeline:${selected.name}:${credit.media.stableKey()}" }) { index, credit ->
        val period = credit.period(today)
        CareerRow(
            credit = credit,
            period = period,
            showPeriod = index == 0 || credits[index - 1].period(today) != period,
            first = index == 0,
            last = index == credits.lastIndex,
            padding = padding,
            redesign = redesign,
            isWatched = WatchingState.isPosterWatched(watchedKeys, credit.media, fullyWatchedSeriesKeys),
            onOpen = { onOpenMeta(credit.media) },
            modifier = Modifier.animateItem(fadeInSpec = tween(220), fadeOutSpec = tween(140), placementSpec = tween(260)),
        )
    }
}

@Composable
private fun CareerRow(
    credit: ActingCredit,
    period: ActingPeriod,
    showPeriod: Boolean,
    first: Boolean,
    last: Boolean,
    padding: Dp,
    redesign: Boolean,
    isWatched: Boolean,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val media = credit.media
    val muted = if (redesign) RedesignColors.textMuted else MaterialTheme.nuvio.colors.textMuted
    val lineColor = if (redesign) RedesignColors.hairline else MaterialTheme.colorScheme.outline
    val periodWidth = if (redesign) 76.dp else 64.dp
    val gap = 8.dp
    val markerWidth = 10.dp
    val interaction = remember { MutableInteractionSource() }
    val currentOnOpen by rememberUpdatedState(onOpen)
    val hovered by interaction.collectIsHoveredAsState()
    val hoverFill by animateColorAsState(
        if (redesign && hovered) RedesignColors.fill else Color.Transparent, tween(160), label = "career_row_hover",
    )
    Row(
        modifier.fillMaxWidth().padding(horizontal = padding)
            .drawBehind {
                val fromStart = (periodWidth + gap + markerWidth / 2).toPx()
                val x = if (layoutDirection == LayoutDirection.Ltr) fromStart else size.width - fromStart
                val mid = size.height / 2
                drawLine(
                    lineColor, Offset(x, if (first) mid else 0f), Offset(x, if (last) mid else size.height), 1.dp.toPx(),
                )
                // One dot per year: it marks where the label's group starts.
                if (showPeriod) drawCircle(muted, 3.dp.toPx(), Offset(x, mid))
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(gap),
    ) {
        val periodLabel = when {
            !showPeriod -> ""
            period is ActingPeriod.Year -> period.value.toString()
            period is ActingPeriod.Upcoming -> stringResource(Res.string.person_upcoming)
            else -> "—"
        }
        Text(
            periodLabel,
            Modifier.width(periodWidth),
            textAlign = TextAlign.End,
            style = when {
                !redesign -> MaterialTheme.typography.labelMedium
                period is ActingPeriod.Year -> redesignText(20.sp, FontWeight.SemiBold, RedesignColors.textSecondary)
                else -> redesignText(13.sp, FontWeight.SemiBold, RedesignColors.textSecondary)
            },
            color = if (redesign) RedesignColors.textSecondary else muted,
            maxLines = 1,
        )
        Spacer(Modifier.width(markerWidth))
        Row(
            Modifier.weight(1f).padding(vertical = 2.dp).clip(RoundedCornerShape(12.dp)).background(hoverFill)
                .hoverable(interaction)
                // The whole row opens the title for the pointer. The title text stays the accessible
                // button, so the row's rating, type and role remain separate readable nodes.
                .pointerInput(Unit) { detectTapGestures(onTap = { currentOnOpen() }) }
                .padding(horizontal = if (redesign) 10.dp else 0.dp, vertical = if (redesign) 8.dp else 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (redesign) 14.dp else gap),
        ) {
            HomePosterCard(
                item = media, basePosterWidthDp = if (redesign) 72 else 56, respectGlobalPosterStyle = false,
                enhanceLandscapeTitles = false, showTitleBelow = false, placeholderTitle = "",
                isWatched = isWatched,
                onClick = onOpen,
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(if (redesign) 4.dp else 2.dp)) {
                Text(
                    media.name,
                    Modifier.clickable(interactionSource = null, indication = null, role = Role.Button, onClick = onOpen),
                    style = if (redesign) redesignText(15.sp, FontWeight.SemiBold) else MaterialTheme.typography.titleSmall,
                    color = if (redesign) RedesignColors.textPrimary else MaterialTheme.nuvio.colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val type = when (credit.kind) {
                    ActingCreditKind.Movie -> null
                    ActingCreditKind.TvMovie -> stringResource(Res.string.person_credit_tv_movie)
                    ActingCreditKind.Video -> stringResource(Res.string.person_credit_video)
                    ActingCreditKind.TvSeries -> stringResource(Res.string.person_credit_tv_series)
                }
                val episodes = credit.episodeCount?.let { count ->
                    if (credit.episodeCountIsMinimum) {
                        stringResource(Res.string.person_credit_episode_minimum, count)
                    } else {
                        pluralStringResource(Res.plurals.person_credit_episode_count, count, count)
                    }
                }
                if (media.sortRating != null || type != null || episodes != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        media.sortRating?.let { InlineTmdbRating(it, media.voteCount) }
                        listOfNotNull(type, episodes).forEachIndexed { i, text ->
                            if (i > 0 || media.sortRating != null) {
                                Box(Modifier.size(3.dp).background(muted, CircleShape))
                            }
                            Text(text, style = MaterialTheme.typography.bodyMedium, color = muted, maxLines = 1)
                        }
                    }
                }
                // Characters for cast, jobs for crew.
                if (credit.characters.isNotEmpty()) {
                    Text(
                        credit.characters.joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (redesign) RedesignColors.textSecondary else muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
