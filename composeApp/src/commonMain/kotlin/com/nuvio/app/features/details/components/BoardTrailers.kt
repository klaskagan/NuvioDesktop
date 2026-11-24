package com.nuvio.app.features.details.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nuvio.app.core.ui.NuvioAsyncImage
import com.nuvio.app.core.ui.RedesignColors
import com.nuvio.app.core.ui.RedesignPillGroup
import com.nuvio.app.core.ui.redesignText
import com.nuvio.app.features.details.MetaTrailer
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.detail_gallery_all
import nuvio.composeapp.generated.resources.detail_gallery_behind_the_scenes
import nuvio.composeapp.generated.resources.detail_trailers_clips
import nuvio.composeapp.generated.resources.detail_trailers_now_playing
import nuvio.composeapp.generated.resources.detail_trailers_teasers
import nuvio.composeapp.generated.resources.detail_trailers_trailers
import org.jetbrains.compose.resources.stringResource

/** What a video is, which is also its filter on the Trailers tab. */
internal enum class TrailerKind { Trailer, Teaser, BehindTheScenes, Clip }

/** TMDB video types grouped into the tab's filters; anything unknown is a clip. */
internal fun trailerKind(trailer: MetaTrailer): TrailerKind = when (trailer.type.trim().lowercase()) {
    "trailer" -> TrailerKind.Trailer
    "teaser" -> TrailerKind.Teaser
    "behind the scenes", "featurette", "bloopers", "making of" -> TrailerKind.BehindTheScenes
    else -> TrailerKind.Clip
}

/** Kinds present in [trailers], in the tab's order. */
internal fun trailerKinds(trailers: List<MetaTrailer>): List<TrailerKind> =
    TrailerKind.entries.filter { kind -> trailers.any { trailerKind(it) == kind } }

/** The videos a filter shows ([kind] null is All), keeping the title's own order. */
internal fun trailersVisible(trailers: List<MetaTrailer>, kind: TrailerKind?): List<MetaTrailer> =
    trailers.distinctBy { it.key }.filter { kind == null || trailerKind(it) == kind }

@Composable
private fun trailerKindLabel(kind: TrailerKind?): String = stringResource(
    when (kind) {
        null -> Res.string.detail_gallery_all
        TrailerKind.Trailer -> Res.string.detail_trailers_trailers
        TrailerKind.Teaser -> Res.string.detail_trailers_teasers
        TrailerKind.BehindTheScenes -> Res.string.detail_gallery_behind_the_scenes
        TrailerKind.Clip -> Res.string.detail_trailers_clips
    },
)

private val TrailerShape = RoundedCornerShape(12.dp)
private const val TrailerColumns = 4

/**
 * The board's Trailers tab (design frame "Trailers"): filter pills with counts and a grid of
 * cards. A card plays in the expanded featured tile through [onPlay]; the one playing has a ring.
 */
@Composable
internal fun BoardTrailers(
    trailers: List<MetaTrailer>,
    playing: MetaTrailer?,
    onPlay: (MetaTrailer) -> Unit,
    modifier: Modifier = Modifier,
) {
    val kinds = remember(trailers) { trailerKinds(trailers) }
    var kindName by rememberSaveable { mutableStateOf<String?>(null) }
    val kind = kinds.firstOrNull { it.name == kindName }
    val visible = remember(trailers, kind) { trailersVisible(trailers, kind) }
    Column(modifier.fillMaxWidth().testTag("detail_board_trailers"), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // The tab already names the list: the filter sits on the left with no title (design frame "Trailers").
        if (kinds.size > 1) {
            RedesignPillGroup(
                options = listOf<TrailerKind?>(null) + kinds,
                selected = kind,
                label = { trailerKindLabel(it) },
                onSelect = { kindName = it?.name },
                optionTag = { "detail_trailers_filter_${it?.name ?: "All"}" },
                trailing = { option -> trailersVisible(trailers, option).size.toString() },
            )
        }
        AnimatedContent(targetState = visible, transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(160)) }, label = "trailers_grid") { shown ->
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val columnGap = 16.dp
                val card = (maxWidth - columnGap * (TrailerColumns - 1)) / TrailerColumns
                Column(verticalArrangement = Arrangement.spacedBy(22.dp)) {
                    shown.chunked(TrailerColumns).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(columnGap)) {
                            row.forEach { trailer ->
                                TrailerTile(
                                    trailer = trailer,
                                    playing = playing?.key == trailer.key,
                                    modifier = Modifier.width(card),
                                    onPlay = { onPlay(trailer) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TrailerTile(trailer: MetaTrailer, playing: Boolean, modifier: Modifier, onPlay: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val ring by animateColorAsState(
        when {
            playing -> RedesignColors.textPrimary
            hovered -> RedesignColors.hairlineFocused
            else -> RedesignColors.hairline
        },
        tween(160),
        label = "trailer_ring",
    )
    val playScale by animateFloatAsState(if (hovered) 1.08f else 1f, tween(160), label = "trailer_play")
    val title = trailer.displayName?.takeIf { it.isNotBlank() } ?: trailer.name
    Column(
        modifier
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onPlay)
            .testTag("detail_trailer_card"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(TrailerShape).background(RedesignColors.fill)
                .border(if (playing) 2.dp else 1.dp, ring, TrailerShape),
        ) {
            NuvioAsyncImage(
                model = "https://img.youtube.com/vi/${trailer.key}/hqdefault.jpg",
                contentDescription = title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            Box(
                Modifier.align(Alignment.Center).size(44.dp).graphicsLayer { scaleX = playScale; scaleY = playScale }
                    .clip(CircleShape).background(Color(0x8C08090B)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = RedesignColors.textPrimary, modifier = Modifier.size(26.dp))
            }
            val badgeAlpha by animateFloatAsState(if (playing) 1f else 0f, tween(200), label = "trailer_now_playing")
            if (playing || badgeAlpha > 0f) {
                Text(
                    stringResource(Res.string.detail_trailers_now_playing),
                    style = redesignText(11.sp, FontWeight.Bold, Color(0xFF0B0C0E)),
                    modifier = Modifier.align(Alignment.TopStart).padding(8.dp).graphicsLayer { alpha = badgeAlpha }
                        .clip(CircleShape).background(RedesignColors.textPrimary)
                        .padding(horizontal = 8.dp, vertical = 3.dp).testTag("detail_trailer_now_playing"),
                )
            }
        }
        Text(title, style = redesignText(15.sp, FontWeight.Bold), maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(
            listOfNotNull(trailer.type.takeIf { it.isNotBlank() }, trailer.site.takeIf { it.isNotBlank() }).joinToString(" · "),
            style = redesignText(12.sp, FontWeight.Medium, RedesignColors.textMuted),
            maxLines = 1,
        )
    }
}
