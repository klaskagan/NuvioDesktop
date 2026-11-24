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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nuvio.app.core.ui.NuvioAsyncImage
import com.nuvio.app.core.ui.RedesignColors
import com.nuvio.app.core.ui.RedesignPillGroup
import com.nuvio.app.core.ui.redesignText
import com.nuvio.app.features.details.MetaPerson
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.detail_board_cast_all
import nuvio.composeapp.generated.resources.detail_board_cast_cast
import nuvio.composeapp.generated.resources.detail_board_cast_crew
import nuvio.composeapp.generated.resources.detail_board_cast_eps
import nuvio.composeapp.generated.resources.person_role_creator
import nuvio.composeapp.generated.resources.person_role_director
import nuvio.composeapp.generated.resources.person_role_writer
import nuvio.composeapp.generated.resources.settings_meta_cast
import org.jetbrains.compose.resources.stringResource

internal enum class CastFilter { All, Cast, Crew }

private const val CastColumns = 8
private val CastGap = 16.dp
private val PortraitShape = RoundedCornerShape(12.dp)

/** Splits [people] by role: creators, directors and writers are crew; everyone else is cast. */
internal fun castFilterPeople(people: List<MetaPerson>, filter: CastFilter, crewRoles: Set<String>): List<MetaPerson> {
    val roles = crewRoles.map { it.lowercase() }.toSet()
    fun isCrew(person: MetaPerson) = person.role?.lowercase() in roles
    return when (filter) {
        CastFilter.All -> people
        CastFilter.Cast -> people.filterNot(::isCrew)
        CastFilter.Crew -> people.filter(::isCrew)
    }
}

/**
 * The board's Cast & crew tab: portrait tiles the way Trakt shows actors — 2:3 headshots with 12 dp
 * corners, eight to a row, name and role left-aligned underneath. Names and roles wrap to a second
 * line and are never cut; a row is as tall as its tallest tile. Series show an "N eps." badge.
 * People without a photo get their initials.
 */
@Composable
internal fun BoardCastGrid(
    cast: List<MetaPerson>,
    isSeries: Boolean,
    onCastClick: ((MetaPerson, String?) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    if (cast.isEmpty()) return
    val crewRoles = setOf(
        stringResource(Res.string.person_role_creator), stringResource(Res.string.person_role_director),
        stringResource(Res.string.person_role_writer), "Creator", "Director", "Writer",
    )
    var filter by rememberSaveable { mutableStateOf(CastFilter.All) }
    val hasCrew = remember(cast, crewRoles) { castFilterPeople(cast, CastFilter.Crew, crewRoles).isNotEmpty() }
    Column(modifier.fillMaxWidth().testTag("detail_board_cast"), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(Res.string.settings_meta_cast), style = redesignText(18.sp, FontWeight.Bold), modifier = Modifier.weight(1f))
            if (hasCrew) {
                RedesignPillGroup(
                    options = CastFilter.entries.toList(),
                    selected = filter,
                    label = {
                        stringResource(
                            when (it) {
                                CastFilter.All -> Res.string.detail_board_cast_all
                                CastFilter.Cast -> Res.string.detail_board_cast_cast
                                CastFilter.Crew -> Res.string.detail_board_cast_crew
                            },
                        )
                    },
                    onSelect = { filter = it },
                    optionTag = { "detail_board_cast_filter_${it.name}" },
                )
            }
        }
        AnimatedContent(
            targetState = filter,
            transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(160)) },
            label = "cast_filter",
        ) { shown ->
            val people = remember(cast, shown, crewRoles) { castFilterPeople(cast, shown, crewRoles) }
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val tileWidth = (maxWidth - CastGap * (CastColumns - 1)) / CastColumns
                Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    people.chunked(CastColumns).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(CastGap)) {
                            row.forEach { person ->
                                PortraitTile(person, isSeries, Modifier.width(tileWidth), onCastClick)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PortraitTile(
    person: MetaPerson,
    isSeries: Boolean,
    modifier: Modifier,
    onCastClick: ((MetaPerson, String?) -> Unit)?,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val border by animateColorAsState(if (hovered) RedesignColors.hairlineFocused else RedesignColors.hairline, tween(160), label = "portrait_border")
    val click = onCastClick?.takeIf { (person.tmdbId ?: 0) > 0 }
    Column(
        modifier.hoverable(interaction)
            .then(if (click != null) Modifier.clickable(interactionSource = interaction, indication = null, role = Role.Button) { click(person, null) } else Modifier)
            .testTag("detail_board_cast_tile"),
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(PortraitShape).background(RedesignColors.fill).border(1.dp, border, PortraitShape)) {
            if (!person.photo.isNullOrBlank()) {
                NuvioAsyncImage(model = person.photo, contentDescription = person.name, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else {
                Text(
                    person.name.portraitInitials(),
                    style = redesignText(26.sp, FontWeight.Bold, RedesignColors.textSecondary),
                    modifier = Modifier.align(Alignment.Center),
                )
            }
            val episodes = person.episodeCount?.takeIf { isSeries && it > 0 }
            if (episodes != null) {
                Text(
                    stringResource(Res.string.detail_board_cast_eps, episodes),
                    style = redesignText(11.sp, FontWeight.SemiBold, Color.White),
                    modifier = Modifier.align(Alignment.BottomStart).padding(6.dp).clip(RoundedCornerShape(6.dp))
                        .background(Color.Black.copy(alpha = 0.62f)).padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        // Never cut: a long name wraps onto more lines, and the row grows to its tallest tile.
        Text(person.name, style = redesignText(13.sp, FontWeight.SemiBold, lineHeight = 17.sp), softWrap = true)
        person.role?.takeIf { it.isNotBlank() }?.let { role ->
            Text(role, style = redesignText(12.sp, color = RedesignColors.textMuted, lineHeight = 16.sp), softWrap = true, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

private fun String.portraitInitials(): String {
    val parts = trim().split(" ").filter { it.isNotBlank() }
    return when {
        parts.size >= 2 -> "${parts.first().first().uppercaseChar()}${parts.last().first().uppercaseChar()}"
        parts.size == 1 -> parts.first().take(2).uppercase()
        else -> ""
    }
}
