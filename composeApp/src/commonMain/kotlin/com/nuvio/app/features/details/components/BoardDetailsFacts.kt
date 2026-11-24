package com.nuvio.app.features.details.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.core.format.formatReleaseDateForDisplay
import com.nuvio.app.core.format.formatWholeUsd
import com.nuvio.app.core.i18n.localizedMediaStatusLabel
import com.nuvio.app.core.ui.RedesignColors
import com.nuvio.app.core.ui.RedesignPillButton
import com.nuvio.app.core.ui.RedesignTileShape
import com.nuvio.app.core.ui.redesignText
import com.nuvio.app.features.details.MetaDetails
import com.nuvio.app.features.details.formatRuntimeForDisplay
import com.nuvio.app.features.tmdb.TmdbService
import com.nuvio.app.features.tracking.TrackingProviderRegistry
import com.nuvio.app.features.tracking.TrackingTitleLinks
import com.nuvio.app.features.tracking.imdbIdOrNull
import com.nuvio.app.features.tracking.tmdbIdOrNull
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.detail_board_created_by
import nuvio.composeapp.generated.resources.detail_board_directed_by
import nuvio.composeapp.generated.resources.detail_facts_about
import nuvio.composeapp.generated.resources.detail_facts_awards
import nuvio.composeapp.generated.resources.detail_facts_first_aired
import nuvio.composeapp.generated.resources.detail_facts_genres
import nuvio.composeapp.generated.resources.detail_facts_links
import nuvio.composeapp.generated.resources.detail_facts_more
import nuvio.composeapp.generated.resources.detail_facts_network
import nuvio.composeapp.generated.resources.detail_facts_released
import nuvio.composeapp.generated.resources.detail_facts_seasons_value
import nuvio.composeapp.generated.resources.details_budget
import nuvio.composeapp.generated.resources.details_certification
import nuvio.composeapp.generated.resources.details_origin_country
import nuvio.composeapp.generated.resources.details_original_language
import nuvio.composeapp.generated.resources.details_revenue
import nuvio.composeapp.generated.resources.details_runtime
import nuvio.composeapp.generated.resources.details_seasons
import nuvio.composeapp.generated.resources.details_status
import nuvio.composeapp.generated.resources.person_role_creator
import nuvio.composeapp.generated.resources.settings_meta_overview
import nuvio.composeapp.generated.resources.settings_meta_production
import org.jetbrains.compose.resources.stringResource

/**
 * The board's Details tab (design doc, tab frame "Details"): About, More and Links side by side,
 * then the full overview. A fact shows only when the metadata has it; a tile with none hides. Links
 * always offer IMDb and TMDB when the ids are known, then each connected tracker's page for the
 * title (Trakt, WeTrakr), which appear once the tracker has answered. Links open in the browser.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun BoardDetailsFacts(meta: MetaDetails, isSeries: Boolean, modifier: Modifier = Modifier) {
    val creatorRole = stringResource(Res.string.person_role_creator)
    val about = buildList {
        if (isSeries && meta.networks.isNotEmpty()) add(stringResource(Res.string.detail_facts_network) to meta.networks.joinToString(", ") { it.name })
        val makers = if (isSeries) {
            meta.cast.filter { it.role.equals(creatorRole, true) || it.role.equals("Creator", true) }.map { it.name }.distinct()
        } else meta.director
        if (makers.isNotEmpty()) {
            add(stringResource(if (isSeries) Res.string.detail_board_created_by else Res.string.detail_board_directed_by) to makers.take(3).joinToString(", "))
        }
        meta.status?.let { add(stringResource(Res.string.details_status) to localizedMediaStatusLabel(it, isSeries)) }
        meta.releaseInfo?.let {
            add(stringResource(if (isSeries) Res.string.detail_facts_first_aired else Res.string.detail_facts_released) to formatReleaseDateForDisplay(it))
        }
        if (isSeries) {
            val episodes = meta.videos.filter { (it.season ?: 0) > 0 }
            val seasons = episodes.mapNotNull { it.season }.distinct().size
            if (seasons > 0) add(stringResource(Res.string.details_seasons) to stringResource(Res.string.detail_facts_seasons_value, seasons, episodes.size))
        }
        formatRuntimeForDisplay(meta.runtime)?.let { add(stringResource(Res.string.details_runtime) to it) }
    }
    val localeTag = LocalLocale.current.toLanguageTag()
    val more = buildList {
        if (meta.genres.isNotEmpty()) add(stringResource(Res.string.detail_facts_genres) to meta.genres.joinToString(", "))
        meta.ageRating?.let { add(stringResource(Res.string.details_certification) to it) }
        meta.country?.let { add(stringResource(Res.string.details_origin_country) to it) }
        meta.language?.let { add(stringResource(Res.string.details_original_language) to it.uppercase()) }
        if (meta.productionCompanies.isNotEmpty()) add(stringResource(Res.string.settings_meta_production) to meta.productionCompanies.take(3).joinToString(", ") { it.name })
        meta.awards?.takeIf { it.isNotBlank() }?.let { add(stringResource(Res.string.detail_facts_awards) to it) }
        if (meta.hasMovieFinancialDetails) {
            meta.budgetUsd?.takeIf { it > 0 }?.let { add(stringResource(Res.string.details_budget) to formatWholeUsd(it, localeTag)) }
            meta.revenueUsd?.takeIf { it > 0 }?.let { add(stringResource(Res.string.details_revenue) to formatWholeUsd(it, localeTag)) }
        }
    }
    val imdb = remember(meta) { meta.imdbIdOrNull() }
    var tmdb by remember(meta) { mutableStateOf(meta.tmdbIdOrNull()) }
    LaunchedEffect(meta.id) {
        if (tmdb == null) tmdb = runCatching { TmdbService.ensureTmdbId(meta.id, meta.type, fallbackImdbId = imdb) }.getOrNull()
    }
    val connected by TrackingProviderRegistry.connectedProviderIds.collectAsStateWithLifecycle()
    var trackerLinks by remember(meta.id) { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    LaunchedEffect(meta.id, connected) {
        trackerLinks = TrackingTitleLinks.connected(connected).mapNotNull { provider ->
            runCatching { provider.url(meta) }.getOrNull()?.let { provider.label to it }
        }
    }
    val tmdbType = if (isSeries) "tv" else "movie"
    val links = buildList {
        imdb?.let { add("IMDb" to "https://www.imdb.com/title/$it/") }
        tmdb?.let { add("TMDB" to "https://www.themoviedb.org/$tmdbType/$it") }
        addAll(trackerLinks)
    }
    val ids = buildList {
        imdb?.let { add("IMDb" to it) }
        tmdb?.let { add("TMDB" to it) }
    }

    Column(modifier.fillMaxWidth().testTag("detail_board_facts"), verticalArrangement = Arrangement.spacedBy(22.dp)) {
        val tiles = listOfNotNull(
            (stringResource(Res.string.detail_facts_about) to about).takeIf { about.isNotEmpty() },
            (stringResource(Res.string.detail_facts_more) to more).takeIf { more.isNotEmpty() },
        )
        if (tiles.isNotEmpty() || links.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                tiles.forEach { (title, facts) ->
                    FactsTile(title, Modifier.weight(1f)) { facts.forEach { (label, value) -> Fact(label, value) } }
                }
                if (links.isNotEmpty()) {
                    FactsTile(stringResource(Res.string.detail_facts_links), Modifier.weight(1f).testTag("detail_board_links")) {
                        ids.forEach { (label, value) -> Fact(label, value) }
                        val uriHandler = LocalUriHandler.current
                        FlowRow(
                            Modifier.padding(top = 6.dp).animateContentSize(tween(200)),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            links.forEach { (label, url) ->
                                // Tracker links arrive after their lookup; they fade in rather than pop.
                                val shown = remember(label) { MutableTransitionState(false).apply { targetState = true } }
                                AnimatedVisibility(visibleState = shown, enter = fadeIn(tween(220))) {
                                    RedesignPillButton(
                                        label = "$label ↗",
                                        onClick = { uriHandler.openUri(url) },
                                        modifier = Modifier.testTag("detail_board_link_$label"),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        meta.description?.takeIf { it.isNotBlank() }?.let { overview ->
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(Res.string.settings_meta_overview), style = redesignText(18.sp, FontWeight.Bold))
                Text(
                    overview,
                    style = redesignText(14.5.sp, color = RedesignColors.textSecondary, lineHeight = 22.sp),
                    modifier = Modifier.widthIn(max = 900.dp),
                )
            }
        }
    }
}

@Composable
private fun FactsTile(title: String, modifier: Modifier, content: @Composable () -> Unit) {
    Column(
        modifier.fillMaxHeight().clip(RedesignTileShape).background(RedesignColors.fill).border(1.dp, RedesignColors.hairline, RedesignTileShape)
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(title.uppercase(), style = redesignText(11.sp, FontWeight.Medium, RedesignColors.textMuted, letterSpacing = 0.1.em))
        content()
    }
}

@Composable
private fun Fact(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = redesignText(12.sp, color = RedesignColors.textMuted))
        // Never cut: a long value wraps.
        Text(value, style = redesignText(14.sp, FontWeight.SemiBold, lineHeight = 19.sp))
    }
}
