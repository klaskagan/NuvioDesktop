package com.nuvio.app.features.details.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.ViewAgenda
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nuvio.app.core.ui.NuvioAsyncImage
import com.nuvio.app.core.ui.RedesignColors
import com.nuvio.app.core.ui.RedesignOnLight
import com.nuvio.app.core.ui.RedesignPillButton
import com.nuvio.app.core.ui.RedesignPillGroup
import com.nuvio.app.core.ui.RedesignSwitchPill
import com.nuvio.app.core.ui.redesignText
import com.nuvio.app.features.details.MoreLikeThisSource
import com.nuvio.app.features.home.MetaPreview
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.collections_editor_tmdb_series
import nuvio.composeapp.generated.resources.detail_board_more_backdrops
import nuvio.composeapp.generated.resources.detail_board_more_posters
import nuvio.composeapp.generated.resources.detail_board_seen_everything
import nuvio.composeapp.generated.resources.detail_board_show_watched
import nuvio.composeapp.generated.resources.detail_board_unwatched_only
import nuvio.composeapp.generated.resources.trakt_more_like_this_source_simkl
import nuvio.composeapp.generated.resources.trakt_more_like_this_source_tmdb
import nuvio.composeapp.generated.resources.trakt_more_like_this_source_trakt
import org.jetbrains.compose.resources.stringResource

/** What the board's More like this shows: the chosen source's titles, with and without watched ones. */
internal data class BoardMoreLikeThisState(
    /** Unwatched titles from the chosen source (Unwatched only on). */
    val unwatched: List<MetaPreview>,
    /** Every title from the chosen source (Unwatched only off). */
    val all: List<MetaPreview>,
    /** Sources the user can pick (TMDB, Trakt when connected…); the pills hide when there is one. */
    val sources: List<MoreLikeThisSource>,
    val source: MoreLikeThisSource?,
    /** The chosen source's titles are still loading. */
    val loading: Boolean,
    val unwatchedOnly: Boolean,
    val posters: Boolean,
    val isWatched: (MetaPreview) -> Boolean,
    val onSourceChange: (MoreLikeThisSource) -> Unit,
    val onUnwatchedOnlyChange: (Boolean) -> Unit,
    val onPostersChange: (Boolean) -> Unit,
)

private val PosterTileWidth = 132.dp
private val PosterGap = 14.dp
private const val WideColumns = 4
private val WideGap = 16.dp
private val CardShape = RoundedCornerShape(12.dp)

/**
 * The board's More like this (design frame "More like this"): the source pills on the left, then
 * "Unwatched only" (a switch, on by default and remembered) and the Posters ↔ wide toggle on the
 * right, over a wrapping grid of 2:3 posters with the title and "year · Series" under each, or wide
 * backdrop cards. When everything is watched it says so, with Show watched.
 */
@Composable
internal fun BoardMoreLikeThis(
    state: BoardMoreLikeThisState,
    onPosterClick: ((MetaPreview) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth().testTag("detail_board_more_like_this"), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // The tab already names it: sources on the left, the switch and layout on the right.
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val selected = state.source
            if (state.sources.size > 1 && selected != null) {
                RedesignPillGroup(
                    options = state.sources,
                    selected = selected,
                    label = { sourceLabel(it) },
                    onSelect = state.onSourceChange,
                    optionTag = { "detail_board_more_source_${it.name}" },
                )
            }
            Spacer(Modifier.weight(1f))
            RedesignSwitchPill(
                label = stringResource(Res.string.detail_board_unwatched_only),
                checked = state.unwatchedOnly,
                onCheckedChange = state.onUnwatchedOnlyChange,
                modifier = Modifier.testTag("detail_board_unwatched_only"),
            )
            RedesignPillButton(
                label = stringResource(if (state.posters) Res.string.detail_board_more_posters else Res.string.detail_board_more_backdrops),
                onClick = { state.onPostersChange(!state.posters) },
                leading = if (state.posters) Icons.Rounded.GridView else Icons.Rounded.ViewAgenda,
                modifier = Modifier.testTag("detail_board_more_layout"),
            )
        }
        val items = if (state.unwatchedOnly) state.unwatched else state.all
        val shown = when {
            state.loading && items.isEmpty() -> MoreLikeThisView.Loading
            items.isEmpty() -> MoreLikeThisView.Empty
            state.posters -> MoreLikeThisView.Posters
            else -> MoreLikeThisView.Wide
        }
        AnimatedContent(
            targetState = Triple(shown, state.source, items),
            transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(160)) },
            contentKey = { (view, source, _) -> view to source },
            label = "more_like_this_view",
        ) { (view, _, titles) ->
            when (view) {
                MoreLikeThisView.Loading -> PosterGrid(List(9) { null }, state, onPosterClick)
                MoreLikeThisView.Empty -> Column(
                    Modifier.fillMaxWidth().padding(vertical = 24.dp).testTag("detail_board_seen_everything"),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(stringResource(Res.string.detail_board_seen_everything), style = redesignText(15.sp, FontWeight.SemiBold, RedesignColors.textSecondary))
                    if (state.unwatchedOnly) {
                        Spacer(Modifier.height(12.dp))
                        RedesignPillButton(label = stringResource(Res.string.detail_board_show_watched), onClick = { state.onUnwatchedOnlyChange(false) })
                    }
                }
                MoreLikeThisView.Posters -> PosterGrid(titles, state, onPosterClick)
                MoreLikeThisView.Wide -> WideGrid(titles, state, onPosterClick)
            }
        }
    }
}

private enum class MoreLikeThisView { Loading, Empty, Posters, Wide }

@Composable
private fun sourceLabel(source: MoreLikeThisSource): String = stringResource(
    when (source) {
        MoreLikeThisSource.TMDB -> Res.string.trakt_more_like_this_source_tmdb
        MoreLikeThisSource.TRAKT -> Res.string.trakt_more_like_this_source_trakt
        MoreLikeThisSource.SIMKL -> Res.string.trakt_more_like_this_source_simkl
    },
)

/** Posters as many across as fit at [PosterTileWidth]; a null entry is a loading placeholder. */
@Composable
private fun PosterGrid(items: List<MetaPreview?>, state: BoardMoreLikeThisState, onClick: ((MetaPreview) -> Unit)?) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns = ((maxWidth + PosterGap) / (PosterTileWidth + PosterGap)).toInt().coerceAtLeast(1)
        val tile = (maxWidth - PosterGap * (columns - 1)) / columns
        Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
            items.chunked(columns).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(PosterGap)) {
                    row.forEach { item ->
                        if (item == null) {
                            Box(Modifier.width(tile).aspectRatio(2f / 3f).clip(CardShape).background(RedesignColors.fill))
                        } else {
                            RecommendationCard(item, wide = false, state, Modifier.width(tile), onClick)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WideGrid(items: List<MetaPreview>, state: BoardMoreLikeThisState, onClick: ((MetaPreview) -> Unit)?) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val tile = (maxWidth - WideGap * (WideColumns - 1)) / WideColumns
        Column(verticalArrangement = Arrangement.spacedBy(22.dp)) {
            items.chunked(WideColumns).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(WideGap)) {
                    row.forEach { item -> RecommendationCard(item, wide = true, state, Modifier.width(tile), onClick) }
                }
            }
        }
    }
}

/** One title: the image (2:3 poster or 16:9 backdrop), its name (wraps, never cut) and "year · Series". */
@Composable
private fun RecommendationCard(
    item: MetaPreview,
    wide: Boolean,
    state: BoardMoreLikeThisState,
    modifier: Modifier,
    onClick: ((MetaPreview) -> Unit)?,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val border by animateColorAsState(if (hovered) RedesignColors.hairlineFocused else RedesignColors.hairline, tween(160), label = "recommendation_border")
    // Only reachable with Unwatched only off: watched titles keep the poster ✓.
    val watched = !state.unwatchedOnly && state.isWatched(item)
    Column(
        modifier.hoverable(interaction)
            .then(if (onClick != null) Modifier.clickable(interactionSource = interaction, indication = null, role = Role.Button) { onClick(item) } else Modifier)
            .testTag(if (wide) "detail_board_more_wide_card" else "detail_board_more_poster_card"),
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(if (wide) 16f / 9f else 2f / 3f).clip(CardShape).background(RedesignColors.fill).border(1.dp, border, CardShape)) {
            // The clean artwork: the frame's cards carry no overlay badges (custom poster patterns keep the original in raw*).
            val image = if (wide) {
                item.banner ?: item.rawLandscapePosterUrl ?: item.landscapePoster ?: item.rawPosterUrl ?: item.poster
            } else {
                item.rawPosterUrl ?: item.poster ?: item.banner
            }
            NuvioAsyncImage(model = image, contentDescription = item.name, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            if (watched) {
                Box(
                    Modifier.align(Alignment.TopEnd).padding(8.dp).size(22.dp).clip(CircleShape).background(RedesignColors.textPrimary)
                        .testTag("detail_board_more_watched"),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.Check, contentDescription = null, tint = RedesignOnLight, modifier = Modifier.size(15.dp)) }
            }
        }
        Spacer(Modifier.height(if (wide) 10.dp else 8.dp))
        Text(item.name, style = redesignText(if (wide) 14.sp else 13.sp, FontWeight.SemiBold, lineHeight = 17.sp), softWrap = true)
        recommendationLine(item)?.let { line ->
            Text(line, style = redesignText(12.sp, color = RedesignColors.textMuted, lineHeight = 16.sp), modifier = Modifier.padding(top = 2.dp))
        }
    }
}

/** "2023 · Series" for a series, the year alone for a film (the frames' card lines). */
@Composable
private fun recommendationLine(item: MetaPreview): String? {
    val year = (item.releaseInfo ?: item.rawReleaseDate)?.take(4)?.takeIf { it.length == 4 && it.all(Char::isDigit) }
    val series = item.type == "series"
    val seriesLabel = stringResource(Res.string.collections_editor_tmdb_series)
    return when {
        year != null && series -> "$year · $seriesLabel"
        year != null -> year
        series -> seriesLabel
        else -> null
    }
}
