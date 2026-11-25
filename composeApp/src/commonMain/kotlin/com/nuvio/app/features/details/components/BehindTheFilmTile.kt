package com.nuvio.app.features.details.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.nuvio.app.core.ui.RedesignColors
import com.nuvio.app.core.ui.RedesignTileShape
import com.nuvio.app.core.ui.redesignText
import com.nuvio.app.features.details.BehindTheFilm
import com.nuvio.app.features.details.BehindTheFilmPerson
import com.nuvio.app.features.details.BehindTheFilmWork
import com.nuvio.app.features.details.MetaPerson
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.detail_behind_based_on
import nuvio.composeapp.generated.resources.detail_behind_cinematography
import nuvio.composeapp.generated.resources.detail_behind_directed_by
import nuvio.composeapp.generated.resources.detail_behind_film
import nuvio.composeapp.generated.resources.detail_behind_filmed_in
import nuvio.composeapp.generated.resources.detail_behind_music
import nuvio.composeapp.generated.resources.detail_behind_show
import org.jetbrains.compose.resources.stringResource

/**
 * The Details tab's "Behind the film" tile (Wikidata). A column shows only when Wikidata has that
 * field; people open their page; "Wikidata ↗" opens the item in the browser.
 */
@Composable
internal fun BehindTheFilmTile(
    info: BehindTheFilm,
    isSeries: Boolean,
    onPerson: ((MetaPerson) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val uriHandler = LocalUriHandler.current
    Column(
        modifier.fillMaxWidth().clip(RedesignTileShape).background(RedesignColors.fill).border(1.dp, RedesignColors.hairline, RedesignTileShape)
            .padding(horizontal = 20.dp, vertical = 18.dp).testTag("detail_behind_the_film"),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(if (isSeries) Res.string.detail_behind_show else Res.string.detail_behind_film).uppercase(),
                style = redesignText(11.sp, FontWeight.Medium, RedesignColors.textMuted, letterSpacing = 0.1.em),
                modifier = Modifier.weight(1f),
            )
            val linkInteraction = remember { MutableInteractionSource() }
            val hovered by linkInteraction.collectIsHoveredAsState()
            val ink by animateColorAsState(if (hovered) RedesignColors.textPrimary else RedesignColors.textSecondary, tween(140), label = "wikidata_link")
            Text(
                "Wikidata ↗",
                style = redesignText(12.5.sp, FontWeight.SemiBold, ink),
                modifier = Modifier.hoverable(linkInteraction)
                    .clickable(interactionSource = linkInteraction, indication = null, role = Role.Button) { uriHandler.openUri(info.url) }
                    .testTag("detail_behind_wikidata"),
            )
        }
        // One equal column per field Wikidata has, as the design's grid.
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            val column = Modifier.weight(1f)
            if (info.basedOn.isNotEmpty()) WorksColumn(stringResource(Res.string.detail_behind_based_on), info.basedOn, column)
            // Films already name their director on the featured tile.
            if (isSeries && info.directedBy.isNotEmpty()) PeopleColumn(stringResource(Res.string.detail_behind_directed_by), info.directedBy, "Director", onPerson, column)
            if (info.music.isNotEmpty()) PeopleColumn(stringResource(Res.string.detail_behind_music), info.music, "Composer", onPerson, column)
            if (info.cinematography.isNotEmpty()) PeopleColumn(stringResource(Res.string.detail_behind_cinematography), info.cinematography, "Cinematographer", onPerson, column)
            if (info.filmedIn.isNotEmpty()) PlacesColumn(stringResource(Res.string.detail_behind_filmed_in), info.filmedIn, column)
        }
    }
}

@Composable
private fun ColumnLabel(text: String) {
    Text(text, style = redesignText(12.sp, color = RedesignColors.textMuted))
}

/** "Dune" in italics, with "novel · Frank Herbert, 1965" muted under it. */
@Composable
private fun WorksColumn(label: String, works: List<BehindTheFilmWork>, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        ColumnLabel(label)
        works.forEach { work ->
            Text(work.title, style = redesignText(14.sp, FontWeight.SemiBold, lineHeight = 19.sp).copy(fontStyle = FontStyle.Italic))
            work.detail?.let { Text(it, style = redesignText(12.sp, FontWeight.Medium, RedesignColors.textMuted, lineHeight = 16.sp)) }
        }
    }
}

/** Filming places as small chips. They are plain facts, so they don't click. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlacesColumn(label: String, places: List<String>, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        ColumnLabel(label)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            places.forEach { place ->
                Text(
                    place,
                    style = redesignText(12.sp, FontWeight.SemiBold),
                    maxLines = 1,
                    modifier = Modifier.clip(CircleShape).background(RedesignColors.fill)
                        .border(1.dp, RedesignColors.hairline, CircleShape)
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                        .testTag("detail_behind_place"),
                )
            }
        }
    }
}

@Composable
private fun PeopleColumn(label: String, people: List<BehindTheFilmPerson>, role: String, onPerson: ((MetaPerson) -> Unit)?, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        ColumnLabel(label)
        people.forEach { person ->
            val interaction = remember(person.name) { MutableInteractionSource() }
            val hovered by interaction.collectIsHoveredAsState()
            val open = onPerson?.takeIf { (person.tmdbId ?: 0) > 0 }
            val ink by animateColorAsState(if (hovered && open != null) RedesignColors.textPrimary else androidx.compose.ui.graphics.Color(0xFFDFE3E7), tween(140), label = "behind_person")
            Text(
                person.name,
                style = redesignText(14.sp, FontWeight.SemiBold, ink, lineHeight = 19.sp),
                modifier = Modifier.hoverable(interaction).then(
                    if (open != null) Modifier.clickable(interactionSource = interaction, indication = null, role = Role.Button) {
                        open(MetaPerson(name = person.name, role = role, tmdbId = person.tmdbId))
                    } else Modifier,
                ),
            )
        }
    }
}
