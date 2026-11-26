package com.nuvio.app.features.details

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.nuvio.app.core.ui.DesktopBackdropVerticalBias
import com.nuvio.app.core.ui.DesktopMergedTopBarHeight
import com.nuvio.app.core.ui.DesktopTopBarContentGap
import com.nuvio.app.core.ui.NuvioAsyncImage
import com.nuvio.app.core.ui.NuvioBackButton
import com.nuvio.app.core.ui.NuvioDesktopVerticalScrollbar
import com.nuvio.app.core.ui.PinnedBarBackground
import com.nuvio.app.core.ui.RedesignColors
import com.nuvio.app.core.ui.RedesignIconButton
import com.nuvio.app.core.ui.RedesignOnLight
import com.nuvio.app.core.ui.RedesignPillButton
import com.nuvio.app.core.ui.RedesignPillGroup
import com.nuvio.app.core.ui.RedesignSwitchPill
import com.nuvio.app.core.ui.RedesignTabs
import com.nuvio.app.core.ui.RedesignTightTracking
import com.nuvio.app.core.ui.desktopPageHorizontalPaddingForWidth
import com.nuvio.app.core.ui.redesignText
import com.nuvio.app.features.details.components.BoardActionPill
import com.nuvio.app.features.details.components.BoardAnchoredPopover
import com.nuvio.app.features.details.components.BoardKicker
import com.nuvio.app.features.details.components.BoardPopoverPanel
import com.nuvio.app.features.details.components.BoardPopoverPlacement
import com.nuvio.app.features.details.components.BoardProgressBar
import com.nuvio.app.features.details.components.DetailBoard
import com.nuvio.app.features.details.components.DetailBoardTabsHeight
import com.nuvio.app.features.details.components.FactGrid
import com.nuvio.app.features.details.components.FactsTile
import com.nuvio.app.features.details.components.GalleryViewer
import com.nuvio.app.features.details.components.boardTile
import com.nuvio.app.features.gallery.GalleryImage
import com.nuvio.app.features.gallery.GalleryKind
import com.nuvio.app.features.gallery.GallerySource
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.home.components.HomePosterCard
import com.nuvio.app.features.home.stableKey
import com.nuvio.app.features.library.LibraryRepository
import com.nuvio.app.features.library.toLibraryItem
import com.nuvio.app.features.tracking.TrackingFavorites
import com.nuvio.app.features.watched.WatchedItem
import com.nuvio.app.features.watching.application.WatchingState
import com.nuvio.app.features.watchprogress.CurrentDateProvider
import com.nuvio.app.features.watchprogress.WatchProgressClock
import com.nuvio.app.LocalDetailTopBarState
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.action_back
import nuvio.composeapp.generated.resources.detail_board_unwatched_only
import nuvio.composeapp.generated.resources.person_board_coming_soon
import nuvio.composeapp.generated.resources.person_board_start_with
import nuvio.composeapp.generated.resources.person_board_open
import nuvio.composeapp.generated.resources.person_board_movie
import nuvio.composeapp.generated.resources.person_board_as_role
import nuvio.composeapp.generated.resources.detail_facts_about
import nuvio.composeapp.generated.resources.detail_facts_links
import nuvio.composeapp.generated.resources.person_board_also_known_as
import nuvio.composeapp.generated.resources.person_board_back_to
import nuvio.composeapp.generated.resources.person_board_career
import nuvio.composeapp.generated.resources.person_board_departments
import nuvio.composeapp.generated.resources.person_board_died
import nuvio.composeapp.generated.resources.person_board_height
import nuvio.composeapp.generated.resources.person_board_in_title
import nuvio.composeapp.generated.resources.person_board_kicker
import nuvio.composeapp.generated.resources.person_board_known_for
import nuvio.composeapp.generated.resources.person_board_last_watched
import nuvio.composeapp.generated.resources.person_board_links_subtitle
import nuvio.composeapp.generated.resources.person_board_more
import nuvio.composeapp.generated.resources.person_board_most_episodes
import nuvio.composeapp.generated.resources.person_board_none_watched
import nuvio.composeapp.generated.resources.person_board_open_on
import nuvio.composeapp.generated.resources.person_board_personal
import nuvio.composeapp.generated.resources.person_board_portraits
import nuvio.composeapp.generated.resources.person_board_save
import nuvio.composeapp.generated.resources.person_board_saved
import nuvio.composeapp.generated.resources.person_board_show_more
import nuvio.composeapp.generated.resources.person_board_sort_newest
import nuvio.composeapp.generated.resources.person_board_sort_popular
import nuvio.composeapp.generated.resources.person_board_sort_top_rated
import nuvio.composeapp.generated.resources.person_board_tab_career
import nuvio.composeapp.generated.resources.person_board_tab_photos
import nuvio.composeapp.generated.resources.person_board_tagged_stills
import nuvio.composeapp.generated.resources.person_board_watched_line
import nuvio.composeapp.generated.resources.person_board_watched_of
import nuvio.composeapp.generated.resources.person_board_you_and
import nuvio.composeapp.generated.resources.person_detail_biography
import nuvio.composeapp.generated.resources.person_detail_born
import nuvio.composeapp.generated.resources.person_detail_credits
import nuvio.composeapp.generated.resources.person_detail_place_of_birth
import nuvio.composeapp.generated.resources.person_favorite
import nuvio.composeapp.generated.resources.person_known_for_shelf
import nuvio.composeapp.generated.resources.person_no_matching_credits
import nuvio.composeapp.generated.resources.person_upcoming
import nuvio.composeapp.generated.resources.person_credit_episode_count
import nuvio.composeapp.generated.resources.seasonal_movies
import nuvio.composeapp.generated.resources.seasonal_series
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** The tabs under the person board (design doc "Person · from Details"). */
internal enum class PersonBoardTab { KnownFor, Movies, Series, Career, Photos, About }

private val PersonPortraitShape = RoundedCornerShape(12.dp)
private const val PersonBoardFillKey = "person-board-fill"
private val PersonBoardBottomPadding = 48.dp
private val PersonCardWidth = 132

/** The tabs this person has content for, in the design's order; About always shows. */
internal fun personBoardTabs(person: PersonDetail, knownFor: List<MetaPreview>?): List<PersonBoardTab> = buildList {
    if (knownFor == null || knownFor.isNotEmpty()) add(PersonBoardTab.KnownFor)
    if (person.movieCredits.isNotEmpty()) add(PersonBoardTab.Movies)
    if (person.tvCredits.isNotEmpty()) add(PersonBoardTab.Series)
    if (person.career().isNotEmpty()) add(PersonBoardTab.Career)
    if (person.taggedImages.isNotEmpty() || person.portraits.isNotEmpty()) add(PersonBoardTab.Photos)
    add(PersonBoardTab.About)
}

/** Distinct titles (films and series) the person worked on, without appearances as themselves. */
internal fun personBoardTitles(person: PersonDetail): List<MetaPreview> {
    val selfOnly = person.actingCredits.filter { it.isSelfOnly() }.map { it.media.stableKey() }.toSet()
    val worked = person.actingCredits.filterNot { it.isSelfOnly() }.map { it.media.stableKey() }.toSet() +
        person.careerCredits.filterKeys { it != CareerDepartment.Self && it != CareerDepartment.Acting }
            .values.flatten().map { it.media.stableKey() }
    return (person.movieCredits + person.tvCredits).distinctBy { it.stableKey() }
        .filter { it.stableKey() in worked || it.stableKey() !in selfOnly }
}

/** The person's nearest title coming out within six months (Coming soon), or null. */
internal fun personComingSoon(person: PersonDetail, today: LocalDate): MetaPreview? {
    val horizon = today.plus(kotlinx.datetime.DatePeriod(months = 6))
    return upcomingPersonCredits(person, today).firstOrNull { (it.creditDate() ?: return@firstOrNull false) <= horizon }
}

/**
 * Start with: a released title of theirs you haven't started. Their Known for list comes first (it
 * is what they are known for); then their titles by popularity, leaving out series they only guested
 * in (under three episodes), which once picked a single Law & Order episode for Adam Scott.
 */
internal fun personStartWith(
    knownFor: List<MetaPreview>?,
    titles: List<MetaPreview>,
    credits: List<ActingCredit>,
    today: LocalDate,
    isStarted: (MetaPreview) -> Boolean,
): MetaPreview? {
    val episodes = credits.associate { it.media.stableKey() to (it.episodeCount ?: 0) }
    val substantial = titles
        .filter { it.type != "series" || (episodes[it.stableKey()] ?: 0) >= 3 }
        .sortedByDescending { it.popularity ?: 0.0 }
    return (knownFor.orEmpty() + substantial).distinctBy { it.stableKey() }
        .firstOrNull { (it.creditDate() ?: return@firstOrNull false) <= today && !isStarted(it) }
}

/**
 * Series that aren't really "theirs": guest spots (under three episodes) and appearances as
 * themselves. The backdrop and the You tile's Last line skip them; they still count as watched titles.
 */
internal fun personGuestSeriesKeys(credits: List<ActingCredit>): Set<String> =
    credits.filter { it.media.type == "series" && ((it.episodeCount ?: 0) < 3 || it.isSelfOnly()) }
        .map { it.media.stableKey() }
        .toSet() - credits.filter { it.media.type == "series" && (it.episodeCount ?: 0) >= 3 && !it.isSelfOnly() }
        .map { it.media.stableKey() }.toSet()

/** Whether you have watched any of [title] (a whole film, or any episode of a series). */
internal fun personTitleStarted(title: MetaPreview, watchedItems: List<WatchedItem>): Boolean {
    val name = title.name.trim().lowercase()
    return watchedItems.any { it.id == title.id || it.name.trim().lowercase() == name }
}

/** "2022 · Mark Scout" (or "19 episodes · Mark Scout" for a series) under a poster card. */
internal fun personCardLine(media: MetaPreview, credit: ActingCredit?, episodesLabel: (Int) -> String): String? {
    val lead = if (media.type == "series") credit?.episodeCount?.let(episodesLabel) else null
    val year = media.creditDate()?.year?.toString() ?: media.releaseInfo?.take(4)
    val role = credit?.characters?.firstOrNull()?.takeIf { it.isNotBlank() }
    return listOfNotNull(lead ?: year, role).joinToString(" · ").takeIf { it.isNotBlank() }
}

/**
 * The desktop person page as a Details board: the featured tile (portrait, name, facts, biography,
 * Favorite and ⋯) on the artwork of the person's best-known title, a column of personal tiles,
 * and tabs that pin with the name once the board scrolls away.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalSharedTransitionApi::class)
@Composable
internal fun PersonBoardPage(
    person: PersonDetail,
    knownFor: List<MetaPreview>?,
    fromTitle: PersonTitleContext?,
    watchedKeys: Set<String>,
    fullyWatchedSeriesKeys: Set<String>,
    watchedItems: List<WatchedItem>,
    tint: Color?,
    initialProfilePhoto: String?,
    avatarTransitionKey: String,
    sharedTransitionScope: SharedTransitionScope?,
    animatedVisibilityScope: AnimatedVisibilityScope?,
    onOpenMeta: (MetaPreview) -> Unit,
    onBack: () -> Unit,
) {
    val tabs = remember(person, knownFor) { personBoardTabs(person, knownFor) }
    var selectedName by rememberSaveable(person.tmdbId) { mutableStateOf(PersonBoardTab.KnownFor.name) }
    val selected = tabs.firstOrNull { it.name == selectedName } ?: tabs.first()
    val list = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val pinned by remember(list) { derivedStateOf { list.firstVisibleItemIndex >= 1 } }
    PersonTopBarScrollReport { list.firstVisibleItemIndex > 0 || list.firstVisibleItemScrollOffset > 0 }
    val selectTab: (PersonBoardTab) -> Unit = { tab ->
        selectedName = tab.name
        // A tab picked from the pinned row keeps the row at the top.
        if (pinned) scope.launch { list.scrollToItem(1) }
    }
    val favorite by remember(person.tmdbId) { TrackingFavorites.observePerson(person.tmdbId) }
        .collectAsState(TrackingFavorites.currentPerson(person.tmdbId))
    LaunchedEffect(person.tmdbId, favorite.provider) { if (favorite.provider != null) TrackingFavorites.refreshPerson(person.tmdbId) }
    val onFavorite: (() -> Unit)? = if (favorite.isAvailable) {
        { scope.launch { TrackingFavorites.setPersonFavorite(person.tmdbId, !favorite.isFavorite) } }
    } else null
    val today = remember { LocalDate.parse(CurrentDateProvider.todayIsoDate()) }
    val topBar = LocalDetailTopBarState.current
    val listTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + if (topBar != null) DesktopMergedTopBarHeight else 0.dp
    val boardGap = if (topBar != null) DesktopTopBarContentGap else 16.dp

    BoxWithConstraints(Modifier.fillMaxSize().testTag("person_board_page")) {
        val padding = desktopPageHorizontalPaddingForWidth(maxWidth.value)
        // The list starts at the bar's bottom edge (as Details' board list does), not under it with
        // top content padding: a sticky header sticks to the list's own top, so with padding the
        // pinned tabs slid up behind the merged top bar once a long tab (Career) scrolled on.
        LazyColumn(state = list, modifier = Modifier.fillMaxSize().padding(top = listTop), contentPadding = PaddingValues(bottom = PersonBoardBottomPadding)) {
            item(key = "person-board", contentType = "person-board") {
                PersonBoard(
                    person = person,
                    knownFor = knownFor,
                    fromTitle = fromTitle,
                    watchedKeys = watchedKeys,
                    fullyWatchedSeriesKeys = fullyWatchedSeriesKeys,
                    watchedItems = watchedItems,
                    today = today,
                    favorite = favorite.isFavorite,
                    onFavorite = onFavorite,
                    initialProfilePhoto = initialProfilePhoto,
                    avatarTransitionKey = avatarTransitionKey,
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = animatedVisibilityScope,
                    onShowAbout = { selectTab(PersonBoardTab.About) },
                    onOpenMeta = onOpenMeta,
                    onBack = onBack,
                    modifier = Modifier.padding(top = boardGap).padding(horizontal = padding),
                )
            }
            stickyHeader(key = "person-board-tabs", contentType = "person-board-tabs") {
                PersonTabsBar(
                    person = person,
                    tabs = tabs,
                    selected = selected,
                    onSelect = selectTab,
                    pinned = pinned,
                    favorite = favorite.isFavorite,
                    onFavorite = onFavorite,
                    onBack = onBack,
                    horizontalPadding = padding,
                    tint = tint,
                )
            }
            item(key = "person-board-gap", contentType = "spacer") { Spacer(Modifier.height(20.dp)) }
            when (selected) {
                PersonBoardTab.Career -> {
                    val career = person.career()
                    val careerTabs = careerTabs(person)
                    personCareerTimelineBoard(
                        person = person,
                        career = career,
                        tabs = careerTabs,
                        today = today,
                        padding = padding,
                        watchedKeys = watchedKeys,
                        fullyWatchedSeriesKeys = fullyWatchedSeriesKeys,
                        onOpenMeta = onOpenMeta,
                    )
                }
                else -> item(key = "person-tab-${selected.name}", contentType = "person-tab-${selected.name}") {
                    Box(Modifier.animateItem(fadeInSpec = tween(240), fadeOutSpec = tween(140), placementSpec = null).fillMaxWidth().padding(horizontal = padding)) {
                        when (selected) {
                            PersonBoardTab.KnownFor -> PersonKnownForTab(person, knownFor, watchedKeys, fullyWatchedSeriesKeys, onOpenMeta)
                            PersonBoardTab.Movies -> PersonCreditsTab(person, person.movieCredits, today, watchedKeys, fullyWatchedSeriesKeys, onOpenMeta)
                            PersonBoardTab.Series -> PersonCreditsTab(person, person.tvCredits, today, watchedKeys, fullyWatchedSeriesKeys, onOpenMeta)
                            PersonBoardTab.Photos -> PersonPhotosTab(person)
                            PersonBoardTab.About -> PersonAboutTab(person)
                            PersonBoardTab.Career -> Unit
                        }
                    }
                }
            }
            // Room under a short tab, so the pinned row can stay at the top whatever is selected
            // (Details' filler: only as tall as the tab leaves the viewport empty).
            item(key = PersonBoardFillKey, contentType = "spacer") {
                val density = androidx.compose.ui.platform.LocalDensity.current
                val tabsPx = with(density) { DetailBoardTabsHeight.roundToPx() }
                val fillPx by remember(list, tabsPx) {
                    derivedStateOf {
                        val info = list.layoutInfo
                        detailBoardFillPx(
                            visible = info.visibleItemsInfo.map { DetailBoardVisibleItem(it.index, it.size, it.key == PersonBoardFillKey) },
                            // The list's own padding is scroll room too: leave it out, or the tab overshoots under the row.
                            viewportPx = info.viewportEndOffset - info.viewportStartOffset - with(density) { PersonBoardBottomPadding.roundToPx() },
                            tabsPx = tabsPx,
                            firstContentIndex = 2,
                        )
                    }
                }
                // Never zero: a zero-height last item is never "visible", so the filler would never grow
                // (seen in the app: the board could not scroll away under a short tab).
                Spacer(Modifier.height(with(density) { fillPx.toDp() }.coerceAtLeast(1.dp)))
            }
        }
        NuvioDesktopVerticalScrollbar(
            state = list,
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(top = listTop, bottom = 8.dp, end = 4.dp),
        )
    }
}

// ─── The board ───────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun PersonBoard(
    person: PersonDetail,
    knownFor: List<MetaPreview>?,
    fromTitle: PersonTitleContext?,
    watchedKeys: Set<String>,
    fullyWatchedSeriesKeys: Set<String>,
    watchedItems: List<WatchedItem>,
    today: LocalDate,
    favorite: Boolean,
    onFavorite: (() -> Unit)?,
    initialProfilePhoto: String?,
    avatarTransitionKey: String,
    sharedTransitionScope: SharedTransitionScope?,
    animatedVisibilityScope: AnimatedVisibilityScope?,
    onShowAbout: () -> Unit,
    onOpenMeta: (MetaPreview) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val titles = remember(person) { personBoardTitles(person) }
    val watched = remember(titles, watchedKeys, fullyWatchedSeriesKeys) {
        titles.filter { WatchingState.isPosterWatched(watchedKeys, it, fullyWatchedSeriesKeys) }
    }
    val last = remember(titles, watchedItems) {
        val guest = personGuestSeriesKeys(person.actingCredits)
        personLastWatched(titles.filterNot { it.stableKey() in guest }, watchedItems)
    }
    val soon = remember(person, today) { personComingSoon(person, today) }
    val start = remember(knownFor, titles, today, watchedKeys, fullyWatchedSeriesKeys, watchedItems) {
        personStartWith(knownFor, titles, person.actingCredits, today) {
            WatchingState.isPosterWatched(watchedKeys, it, fullyWatchedSeriesKeys) || personTitleStarted(it, watchedItems)
        }
    }
    DetailBoard(
        expanded = false,
        hasSide = true,
        modifier = modifier,
        side = {
            // The In … tile sizes to its content; You and … takes the spare height, so the column ends flush.
            if (fromTitle != null) {
                PersonInTitleTile(fromTitle, onBack, Modifier.fillMaxWidth())
            }
            PersonYouTile(
                name = person.name,
                watched = watched.size,
                total = titles.size,
                last = last,
                showArtwork = true,
                modifier = Modifier.fillMaxWidth().weight(1f),
            )
            // Coming soon only for a release within six months; Start with is something to watch today.
            // Beside an In … tile there is room for one of them, and a near release wins.
            val extra = listOfNotNull(soon?.let { it to true }, start?.let { it to false }).take(if (fromTitle != null) 1 else 2)
            extra.forEach { (media, upcoming) ->
                PersonTitleTile(person, media, upcoming, onOpenMeta, Modifier.fillMaxWidth().height(132.dp))
            }
        },
    ) {
        PersonFeatureTile(
            person = person,
            favorite = favorite,
            onFavorite = onFavorite,
            initialProfilePhoto = initialProfilePhoto,
            avatarTransitionKey = avatarTransitionKey,
            sharedTransitionScope = sharedTransitionScope,
            animatedVisibilityScope = animatedVisibilityScope,
            onShowAbout = onShowAbout,
            onBack = onBack,
        )
    }
}

/** The watched entry of theirs most recently marked, with a label ("Severance S2 E3"). */
internal fun personLastWatched(titles: List<MetaPreview>, watchedItems: List<WatchedItem>): Pair<MetaPreview, String>? {
    val byName = titles.associateBy { it.name.trim().lowercase() to (it.type == "series") }
    val ids = titles.associateBy { it.id }
    return watchedItems.sortedByDescending { it.markedAtEpochMs }.firstNotNullOfOrNull { item ->
        val series = item.type == "series" || item.season != null
        val title = ids[item.id] ?: byName[item.name.trim().lowercase() to series] ?: return@firstNotNullOfOrNull null
        val label = if (item.season != null && item.episode != null) "${title.name} S${item.season} E${item.episode}" else title.name
        title to label
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun BoxScope.PersonFeatureTile(
    person: PersonDetail,
    favorite: Boolean,
    onFavorite: (() -> Unit)?,
    initialProfilePhoto: String?,
    avatarTransitionKey: String,
    sharedTransitionScope: SharedTransitionScope?,
    animatedVisibilityScope: AnimatedVisibilityScope?,
    onShowAbout: () -> Unit,
    onBack: () -> Unit,
) {
    personBackdropArt(person)?.let { art ->
        NuvioAsyncImage(
            model = art,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            alignment = BiasAlignment(0.2f, DesktopBackdropVerticalBias),
            contentScale = ContentScale.Crop,
        )
    }
    Box(
        Modifier.fillMaxSize().background(
            Brush.horizontalGradient(0f to Color(0xE6060708), 0.42f to Color(0xA8060708), 0.74f to Color.Transparent),
        ),
    )
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.6f to Color.Transparent, 1f to Color(0x99060708))))
    NuvioBackButton(
        onClick = onBack,
        modifier = Modifier.align(Alignment.TopStart).padding(14.dp),
        containerColor = Color.Black.copy(alpha = 0.34f),
        showContainerOnDesktop = true,
        contentColor = RedesignColors.textPrimary,
        buttonSize = 48.dp,
        iconSize = 24.dp,
    )
    Row(
        Modifier.align(Alignment.BottomStart).padding(start = 28.dp, end = 28.dp, bottom = 24.dp).testTag("person_board_featured"),
        verticalAlignment = Alignment.Bottom,
    ) {
        PersonPortrait(person, initialProfilePhoto, avatarTransitionKey, sharedTransitionScope, animatedVisibilityScope, Modifier.size(width = 150.dp, height = 225.dp))
        Spacer(Modifier.width(26.dp))
        Column(Modifier.widthIn(max = 640.dp)) {
            val department = person.knownFor?.let(::careerDepartmentOf)?.let { careerDepartmentLabel(it) } ?: person.knownFor
            val credits = remember(person) { (person.movieCredits + person.tvCredits).distinctBy { it.stableKey() }.size }
            if (department != null) BoardKicker(stringResource(Res.string.person_board_kicker, department, credits), RedesignColors.textSecondary)
            Spacer(Modifier.height(6.dp))
            Text(
                person.name,
                style = redesignText(44.sp, FontWeight.ExtraBold, lineHeight = 46.sp, letterSpacing = RedesignTightTracking),
                maxLines = 2,
                modifier = Modifier.testTag("person_board_name"),
            )
            val facts = personBoardFacts(person)
            if (facts.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    facts.joinToString("  ·  "),
                    style = redesignText(14.sp, FontWeight.SemiBold, RedesignColors.textSecondary),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            person.biography?.takeIf { it.isNotBlank() }?.let { bio ->
                Spacer(Modifier.height(10.dp))
                Text(
                    bio,
                    style = redesignText(14.5.sp, color = Color(0xFFDFE3E7), lineHeight = 21.sp),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 600.dp),
                )
                val interaction = remember { MutableInteractionSource() }
                val hovered by interaction.collectIsHoveredAsState()
                Text(
                    stringResource(Res.string.person_board_show_more),
                    style = redesignText(13.sp, FontWeight.SemiBold, if (hovered) RedesignColors.textPrimary else RedesignColors.textSecondary),
                    modifier = Modifier.padding(top = 4.dp).hoverable(interaction)
                        .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onShowAbout)
                        .testTag("person_board_show_more"),
                )
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                if (onFavorite != null) {
                    BoardActionPill(
                        label = stringResource(Res.string.person_favorite),
                        icon = if (favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        onClick = onFavorite,
                        active = favorite,
                        modifier = Modifier.testTag("person_board_favorite"),
                    )
                }
                PersonLinksAction(person)
            }
        }
    }
}

/** "Born 3 April 1973 · 53", the birthplace (or the death date) for the featured tile. */
@Composable
private fun personBoardFacts(person: PersonDetail): List<String> = buildList {
    person.birthday?.let { birthday ->
        val date = formatDateForDisplay(birthday) ?: birthday
        val age = calculateAge(birthday, person.deathday)
        add(listOfNotNull("${stringResource(Res.string.person_detail_born)} $date", age?.toString()).joinToString(" · "))
    }
    person.deathday?.let { add("${stringResource(Res.string.person_board_died)} ${formatDateForDisplay(it) ?: it}") }
    person.placeOfBirth?.takeIf { it.isNotBlank() }?.let { add(it.split(",").take(2).joinToString(",").trim()) }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun PersonPortrait(
    person: PersonDetail,
    initialProfilePhoto: String?,
    avatarTransitionKey: String,
    sharedTransitionScope: SharedTransitionScope?,
    animatedVisibilityScope: AnimatedVisibilityScope?,
    modifier: Modifier,
) {
    val shared = if (sharedTransitionScope != null && animatedVisibilityScope != null) {
        with(sharedTransitionScope) {
            Modifier.sharedElement(rememberSharedContentState(key = avatarTransitionKey), animatedVisibilityScope = animatedVisibilityScope)
        }
    } else Modifier
    val url = person.profilePhoto?.takeIf { it.isNotBlank() } ?: initialProfilePhoto
    Box(
        modifier.then(shared).clip(PersonPortraitShape).background(Color(0xFF1B1D22)).border(1.dp, RedesignColors.hairline, PersonPortraitShape),
        contentAlignment = Alignment.Center,
    ) {
        if (url != null) {
            AsyncImage(model = url, contentDescription = person.name, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Text(personInitials(person.name), style = redesignText(30.sp, FontWeight.Bold, RedesignColors.textSecondary))
        }
    }
}

internal fun personInitials(name: String): String =
    name.trim().split(" ").filter { it.isNotBlank() }.take(2).mapNotNull { it.firstOrNull()?.uppercase() }.joinToString("").ifBlank { "?" }

/** ⋯ on the featured tile: open the person on TMDB, IMDb and Wikidata. */
@Composable
private fun PersonLinksAction(person: PersonDetail) {
    val state = remember { MutableTransitionState(false) }
    var bounds by remember { mutableStateOf(Rect.Zero) }
    val links = personLinks(person, wikidataId = null)
    BoardActionPill(
        label = stringResource(Res.string.person_board_more),
        icon = Icons.Rounded.MoreHoriz,
        onClick = { state.targetState = !state.targetState },
        iconOnly = true,
        modifier = Modifier.testTag("person_board_more").onGloballyPositioned { bounds = it.boundsInWindow() },
    )
    val dismiss = { state.targetState = false }
    BoardAnchoredPopover(state, bounds, bounds, BoardPopoverPlacement.Under, dismiss) {
        BoardPopoverPanel(person.name, stringResource(Res.string.person_board_links_subtitle), dismiss, 300.dp, "person_board_links") {
            val uri = LocalUriHandler.current
            links.forEach { (label, url) ->
                val interaction = remember(label) { MutableInteractionSource() }
                val hovered by interaction.collectIsHoveredAsState()
                val fill by animateColorAsState(if (hovered) RedesignColors.fillStrong else Color.Transparent, tween(160), label = "person_link_row")
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(fill).hoverable(interaction)
                        .clickable(interactionSource = interaction, indication = null, role = Role.Button) { uri.openUri(url); dismiss() }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null, tint = RedesignColors.textSecondary, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(Res.string.person_board_open_on, label), style = redesignText(13.5.sp, FontWeight.SemiBold))
                }
            }
        }
    }
}

internal fun personLinks(person: PersonDetail, wikidataId: String?): List<Pair<String, String>> = buildList {
    add("TMDB" to "https://www.themoviedb.org/person/${person.tmdbId}")
    person.imdbId?.let { add("IMDb" to "https://www.imdb.com/name/$it/") }
    wikidataId?.let { add("Wikidata" to "https://www.wikidata.org/wiki/$it") }
}

// ─── Side tiles ──────────────────────────────────────────────────────────────────────────────

@Composable
private fun PersonInTitleTile(context: PersonTitleContext, onBack: () -> Unit, modifier: Modifier) {
    Column(modifier.boardTile().testTag("person_board_in_title").padding(horizontal = 16.dp, vertical = 14.dp)) {
        BoardKicker(stringResource(Res.string.person_board_in_title, context.title))
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.Top) {
            Box(Modifier.size(width = 56.dp, height = 84.dp).clip(RoundedCornerShape(8.dp)).background(RedesignColors.fill).border(1.dp, RedesignColors.hairline, RoundedCornerShape(8.dp))) {
                NuvioAsyncImage(model = context.artwork, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                context.role?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = redesignText(15.sp, FontWeight.Bold), maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                context.episodeCount?.takeIf { context.isSeries && it > 0 }?.let { count ->
                    Text(pluralStringResource(Res.plurals.person_credit_episode_count, count, count), style = redesignText(12.5.sp, color = RedesignColors.textMuted), maxLines = 1)
                }
                Spacer(Modifier.height(10.dp))
                RedesignPillButton(
                    label = stringResource(Res.string.person_board_back_to, context.title),
                    onClick = onBack,
                    leading = Icons.AutoMirrored.Rounded.ArrowBack,
                    modifier = Modifier.testTag("person_board_back_to_title"),
                )
            }
        }
    }
}

@Composable
private fun PersonYouTile(
    name: String,
    watched: Int,
    total: Int,
    last: Pair<MetaPreview, String>?,
    showArtwork: Boolean,
    modifier: Modifier,
) {
    Column(modifier.boardTile().testTag("person_board_you").padding(horizontal = 16.dp, vertical = 14.dp)) {
        BoardKicker(stringResource(Res.string.person_board_you_and, name))
        Spacer(Modifier.height(4.dp))
        Text(stringResource(Res.string.person_board_watched_of, watched, total), style = redesignText(24.sp, FontWeight.Bold, letterSpacing = RedesignTightTracking), maxLines = 1)
        Text(stringResource(Res.string.person_board_watched_line), style = redesignText(12.5.sp, color = RedesignColors.textSecondary), maxLines = 1)
        Spacer(Modifier.height(10.dp))
        BoardProgressBar(if (total > 0) watched.toFloat() / total else 0f, Modifier.fillMaxWidth())
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (showArtwork && last != null) {
                Box(Modifier.size(width = 44.dp, height = 66.dp).clip(RoundedCornerShape(7.dp)).background(RedesignColors.fill)) {
                    NuvioAsyncImage(model = last.first.poster, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                }
                Spacer(Modifier.width(12.dp))
            }
            Text(
                last?.let { stringResource(Res.string.person_board_last_watched, it.second) } ?: stringResource(Res.string.person_board_none_watched),
                style = redesignText(12.5.sp, color = RedesignColors.textMuted),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * Coming soon (a release within six months, with its date and Save to list) or Start with (their
 * most popular title you haven't watched, with Open).
 */
@Composable
private fun PersonTitleTile(person: PersonDetail, media: MetaPreview, upcoming: Boolean, onOpenMeta: (MetaPreview) -> Unit, modifier: Modifier) {
    val library by LibraryRepository.uiState.collectAsState()
    val saved = remember(library, media.id) { LibraryRepository.isSaved(media.id, media.type) }
    val scope = rememberCoroutineScope()
    val role = remember(person, media) { person.actingCredits.firstOrNull { it.media.stableKey() == media.stableKey() }?.characters?.firstOrNull() }
    Column(modifier.boardTile().testTag(if (upcoming) "person_board_coming_soon" else "person_board_start_with").padding(horizontal = 16.dp, vertical = 14.dp)) {
        BoardKicker(stringResource(if (upcoming) Res.string.person_board_coming_soon else Res.string.person_board_start_with))
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.Top) {
            val interaction = remember { MutableInteractionSource() }
            Box(
                Modifier.size(width = 56.dp, height = 84.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF1B1D22))
                    .border(1.dp, RedesignColors.hairline, RoundedCornerShape(8.dp))
                    .clickable(interactionSource = interaction, indication = null, role = Role.Button) { onOpenMeta(media) },
                contentAlignment = Alignment.Center,
            ) {
                if (media.poster != null) {
                    NuvioAsyncImage(model = media.poster, contentDescription = media.name, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                } else {
                    Text(personInitials(media.name), style = redesignText(13.sp, FontWeight.Bold, RedesignColors.textSecondary))
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(media.name, style = redesignText(15.sp, FontWeight.Bold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                // A release shows its full date; a title to start with, its year.
                val date = if (upcoming) media.rawReleaseDate?.let { formatDateForDisplay(it) ?: it } else media.creditDate()?.year?.toString()
                val line = listOfNotNull(date, role).joinToString(" · ")
                if (line.isNotBlank()) Text(line, style = redesignText(12.5.sp, color = RedesignColors.textMuted), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(8.dp))
                if (upcoming) {
                    RedesignPillButton(
                        label = stringResource(if (saved) Res.string.person_board_saved else Res.string.person_board_save),
                        onClick = { scope.launch { LibraryRepository.toggleSaved(media.toLibraryItem(WatchProgressClock.nowEpochMs())) } },
                        active = saved,
                        modifier = Modifier.testTag("person_board_coming_save"),
                    )
                } else {
                    RedesignPillButton(
                        label = stringResource(Res.string.person_board_open),
                        onClick = { onOpenMeta(media) },
                        modifier = Modifier.testTag("person_board_start_open"),
                    )
                }
            }
        }
    }
}

// ─── Tabs row ────────────────────────────────────────────────────────────────────────────────

@Composable
private fun PersonBoardTab.label(): String = stringResource(
    when (this) {
        PersonBoardTab.KnownFor -> Res.string.person_known_for_shelf
        PersonBoardTab.Movies -> Res.string.seasonal_movies
        PersonBoardTab.Series -> Res.string.seasonal_series
        PersonBoardTab.Career -> Res.string.person_board_tab_career
        PersonBoardTab.Photos -> Res.string.person_board_tab_photos
        PersonBoardTab.About -> Res.string.detail_facts_about
    },
)

internal fun personBoardTabCount(tab: PersonBoardTab, person: PersonDetail, knownFor: List<MetaPreview>?): Int? = when (tab) {
    PersonBoardTab.KnownFor -> knownFor?.size
    PersonBoardTab.Movies -> person.movieCredits.distinctBy { it.stableKey() }.size
    PersonBoardTab.Series -> person.tvCredits.distinctBy { it.stableKey() }.size
    PersonBoardTab.Career -> person.career().values.sumOf { it.size }
    PersonBoardTab.Photos -> maxOf(person.taggedImageTotal, person.taggedImages.size) + person.portraits.size
    PersonBoardTab.About -> null
}

/**
 * The tabs under the board. Pinned, it takes the shared pinned-bar surface and shows Back, the
 * small portrait with the name, and Favorite, as Details pins Back, the logo and Play.
 */
@Composable
private fun PersonTabsBar(
    person: PersonDetail,
    tabs: List<PersonBoardTab>,
    selected: PersonBoardTab,
    onSelect: (PersonBoardTab) -> Unit,
    pinned: Boolean,
    favorite: Boolean,
    onFavorite: (() -> Unit)?,
    onBack: () -> Unit,
    horizontalPadding: Dp,
    tint: Color?,
) {
    Box(Modifier.fillMaxWidth().height(DetailBoardTabsHeight).testTag("person_board_tabs")) {
        PinnedBarBackground(scrolled = pinned, hazeState = null, tint = tint)
        val hairline by animateColorAsState(if (pinned) Color.Transparent else RedesignColors.hairline, tween(220), label = "person_tabs_hairline")
        Box(Modifier.align(Alignment.BottomStart).padding(horizontal = horizontalPadding).fillMaxWidth().height(1.dp).background(hairline))
        Row(Modifier.fillMaxSize().padding(horizontal = horizontalPadding), verticalAlignment = Alignment.CenterVertically) {
            AnimatedVisibility(
                visible = pinned,
                enter = fadeIn(tween(220)) + expandHorizontally(tween(360, easing = FastOutSlowInEasing), expandFrom = Alignment.Start),
                exit = fadeOut(tween(140)) + shrinkHorizontally(tween(360, easing = FastOutSlowInEasing), shrinkTowards = Alignment.Start),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RedesignIconButton(
                        contentDescription = stringResource(Res.string.action_back),
                        onClick = onBack,
                        vector = Icons.AutoMirrored.Rounded.ArrowBack,
                        size = 40.dp,
                        modifier = Modifier.testTag("person_board_pinned_back"),
                    )
                    Spacer(Modifier.width(14.dp))
                    Box(Modifier.size(width = 28.dp, height = 42.dp).clip(RoundedCornerShape(6.dp)).background(RedesignColors.fill)) {
                        person.profilePhoto?.let { AsyncImage(model = it, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(person.name, style = redesignText(15.sp, FontWeight.Bold), maxLines = 1, modifier = Modifier.widthIn(max = 220.dp))
                    Spacer(Modifier.width(24.dp))
                }
            }
            RedesignTabs(
                items = tabs,
                selected = selected,
                label = { it.label() },
                onSelect = onSelect,
                count = { tab -> personBoardTabCount(tab, person, null).takeIf { tab != PersonBoardTab.KnownFor } },
                modifier = Modifier.align(Alignment.Bottom),
            )
            Spacer(Modifier.weight(1f))
            if (onFavorite != null) {
                AnimatedVisibility(visible = pinned, enter = fadeIn(tween(220)), exit = fadeOut(tween(140))) {
                    RedesignPillButton(
                        label = stringResource(Res.string.person_favorite),
                        onClick = onFavorite,
                        active = favorite,
                        leading = if (favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        modifier = Modifier.testTag("person_board_pinned_favorite"),
                    )
                }
            }
        }
    }
}

// ─── Known for, Movies, Series ───────────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PersonPosterGrid(
    person: PersonDetail,
    items: List<MetaPreview>,
    today: LocalDate,
    watchedKeys: Set<String>,
    fullyWatchedSeriesKeys: Set<String>,
    onOpenMeta: (MetaPreview) -> Unit,
) {
    val creditsByKey = remember(person) { person.actingCredits.associateBy { it.media.stableKey() } }
    val crewByKey = remember(person) {
        person.careerCredits.filterKeys { it != CareerDepartment.Acting && it != CareerDepartment.Self }.values.flatten()
            .associateBy { it.media.stableKey() }
    }
    FlowRow(
        Modifier.fillMaxWidth().testTag("person_board_grid"),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        items.forEach { media ->
            val key = media.stableKey()
            val credit = creditsByKey[key] ?: crewByKey[key]
            val episodes = credit?.episodeCount
            val episodesLabel = episodes?.let { pluralStringResource(Res.plurals.person_credit_episode_count, it, it) }
            val line = personCardLine(media, credit) { episodesLabel.orEmpty() }
            // Title and role under the poster (design frame). The card's own labels follow the global
            // "hide labels" setting, which would drop the role, so the board draws them itself.
            Column(Modifier.width(PersonCardWidth.dp)) {
                HomePosterCard(
                    item = media,
                    basePosterWidthDp = PersonCardWidth,
                    respectGlobalPosterStyle = false,
                    showTitleBelow = false,
                    isWatched = WatchingState.isPosterWatched(watchedKeys, media, fullyWatchedSeriesKeys),
                    onClick = { onOpenMeta(media) },
                )
                Spacer(Modifier.height(8.dp))
                Text(media.name, style = redesignText(13.5.sp, FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                line?.let { Text(it, style = redesignText(12.sp, color = RedesignColors.textMuted), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.testTag("person_board_card_line")) }
            }
        }
    }
}

@Composable
private fun PersonKnownForTab(
    person: PersonDetail,
    knownFor: List<MetaPreview>?,
    watchedKeys: Set<String>,
    fullyWatchedSeriesKeys: Set<String>,
    onOpenMeta: (MetaPreview) -> Unit,
) {
    var unwatchedOnly by rememberSaveable(person.tmdbId) { mutableStateOf(false) }
    val today = remember { LocalDate.parse(CurrentDateProvider.todayIsoDate()) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.weight(1f))
            RedesignSwitchPill(
                label = stringResource(Res.string.detail_board_unwatched_only),
                checked = unwatchedOnly,
                onCheckedChange = { unwatchedOnly = it },
                modifier = Modifier.testTag("person_board_unwatched_only"),
            )
        }
        if (knownFor == null) {
            PersonPosterSkeletonRow()
        } else {
            val shown = remember(knownFor, unwatchedOnly, watchedKeys) {
                if (unwatchedOnly) knownFor.filterNot { WatchingState.isPosterWatched(watchedKeys, it, fullyWatchedSeriesKeys) } else knownFor
            }
            PersonPosterGrid(person, shown, today, watchedKeys, fullyWatchedSeriesKeys, onOpenMeta)
        }
    }
}

private enum class PersonGridSort(val sort: PersonCreditSort) { Newest(PersonCreditSort.NewestYear), Popular(PersonCreditSort.Popularity), TopRated(PersonCreditSort.HighestRated) }

@Composable
private fun PersonCreditsTab(
    person: PersonDetail,
    credits: List<MetaPreview>,
    today: LocalDate,
    watchedKeys: Set<String>,
    fullyWatchedSeriesKeys: Set<String>,
    onOpenMeta: (MetaPreview) -> Unit,
) {
    var sortName by rememberSaveable(person.tmdbId, credits.size) { mutableStateOf(PersonGridSort.Newest.name) }
    var upcomingOnly by rememberSaveable(person.tmdbId, credits.size) { mutableStateOf(false) }
    var unwatchedOnly by rememberSaveable(person.tmdbId, credits.size) { mutableStateOf(false) }
    val sort = PersonGridSort.valueOf(sortName)
    val upcomingCount = remember(credits, today) { credits.distinctBy { it.stableKey() }.count { (it.creditDate() ?: LocalDate(1, 1, 1)) > today } }
    val items = remember(credits, sort, upcomingOnly, unwatchedOnly, watchedKeys, fullyWatchedSeriesKeys, today) {
        filterPersonCredits(
            credits,
            PersonCreditQuery(
                sort = sort.sort,
                release = if (upcomingOnly) PersonReleaseFilter.Upcoming else PersonReleaseFilter.All,
                watched = if (unwatchedOnly) PersonWatchedFilter.Unwatched else PersonWatchedFilter.All,
            ),
            today,
            watchedKeys = watchedKeys,
            fullyWatchedSeriesKeys = fullyWatchedSeriesKeys,
        )
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            RedesignPillGroup(
                options = PersonGridSort.entries,
                selected = sort,
                label = {
                    stringResource(
                        when (it) {
                            PersonGridSort.Newest -> Res.string.person_board_sort_newest
                            PersonGridSort.Popular -> Res.string.person_board_sort_popular
                            PersonGridSort.TopRated -> Res.string.person_board_sort_top_rated
                        },
                    )
                },
                onSelect = { sortName = it.name },
                optionTag = { "person_board_sort_${it.name}" },
            )
            Spacer(Modifier.weight(1f))
            if (upcomingCount > 0) {
                RedesignPillButton(
                    label = "${stringResource(Res.string.person_upcoming)}  $upcomingCount",
                    onClick = { upcomingOnly = !upcomingOnly },
                    active = upcomingOnly,
                    modifier = Modifier.testTag("person_board_upcoming"),
                )
            }
            RedesignSwitchPill(
                label = stringResource(Res.string.detail_board_unwatched_only),
                checked = unwatchedOnly,
                onCheckedChange = { unwatchedOnly = it },
                modifier = Modifier.testTag("person_board_credits_unwatched"),
            )
        }
        AnimatedContent(targetState = items, transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(160)) }, label = "person_credits_grid") { shown ->
            if (shown.isEmpty()) {
                Text(stringResource(Res.string.person_no_matching_credits), style = redesignText(14.sp, color = RedesignColors.textSecondary), modifier = Modifier.padding(vertical = 24.dp))
            } else {
                PersonPosterGrid(person, shown, today, watchedKeys, fullyWatchedSeriesKeys, onOpenMeta)
            }
        }
    }
}

@Composable
private fun PersonPosterSkeletonRow() {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        repeat(8) {
            Column {
                Box(Modifier.width(PersonCardWidth.dp).height((PersonCardWidth * 1.5f).dp).clip(RoundedCornerShape(12.dp)).background(RedesignColors.fill))
                Spacer(Modifier.height(10.dp))
                Box(Modifier.width(100.dp).height(10.dp).clip(CircleShape).background(RedesignColors.fill))
            }
        }
    }
}

// ─── Career ──────────────────────────────────────────────────────────────────────────────────

/** Career as the board shows it: department pills with counts, the year range, then the timeline. */
private fun androidx.compose.foundation.lazy.LazyListScope.personCareerTimelineBoard(
    person: PersonDetail,
    career: Map<CareerDepartment, List<ActingCredit>>,
    tabs: List<CareerDepartment>,
    today: LocalDate,
    padding: Dp,
    watchedKeys: Set<String>,
    fullyWatchedSeriesKeys: Set<String>,
    onOpenMeta: (MetaPreview) -> Unit,
) {
    item(key = "person-career-board", contentType = "person-career") {
        PersonCareerBoard(person, career, tabs, today, padding, watchedKeys, fullyWatchedSeriesKeys, onOpenMeta)
    }
}

@Composable
private fun PersonCareerBoard(
    person: PersonDetail,
    career: Map<CareerDepartment, List<ActingCredit>>,
    tabs: List<CareerDepartment>,
    today: LocalDate,
    padding: Dp,
    watchedKeys: Set<String>,
    fullyWatchedSeriesKeys: Set<String>,
    onOpenMeta: (MetaPreview) -> Unit,
) {
    var departmentName by rememberSaveable(person.tmdbId) { mutableStateOf<String?>(null) }
    val department = tabs.firstOrNull { it.name == departmentName } ?: tabs.firstOrNull() ?: return
    val credits = remember(career, department, today) { personActingTimeline(career.getValue(department), today).values.flatten() }
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = padding).padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (tabs.size > 1) {
                RedesignPillGroup(
                    options = tabs,
                    selected = department,
                    label = { careerDepartmentLabel(it) },
                    onSelect = { departmentName = it.name },
                    optionTag = { "person_career_${it.name}" },
                    trailing = { career[it]?.size?.toString() },
                )
            }
            Spacer(Modifier.weight(1f))
            val span = remember(credits, today) { careerYearSpan(credits, today, person.careerFloorYear()) }
            if (span != null) {
                Text(
                    if (span.first == span.second) "${span.first}" else "${span.first} – ${span.second}",
                    style = redesignText(13.sp, FontWeight.SemiBold, RedesignColors.textSecondary),
                    modifier = Modifier.clip(CircleShape).background(RedesignColors.fill).border(1.dp, RedesignColors.hairline, CircleShape)
                        .padding(horizontal = 14.dp, vertical = 8.dp).testTag("person_career_span"),
                )
            }
        }
        AnimatedContent(targetState = department to credits, transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(160)) }, label = "person_career_rows") { (shownDepartment, rows) ->
            // Design frame "Person · Career": the year on the left, a hairline between years, and per
            // credit a small poster, the title, "as Role · Movie" (or the episode count, or the year
            // when it is upcoming) and ✓ on the right when watched. A row is shaded on hover and when watched (user, 26 Nov).
            Column(Modifier.widthIn(max = 1100.dp).padding(horizontal = padding).testTag("person_career_rows")) {
                val groups = remember(rows, today) {
                    rows.fold(mutableListOf<Pair<ActingPeriod, MutableList<ActingCredit>>>()) { acc, credit ->
                        val period = credit.periodOf(today)
                        if (acc.lastOrNull()?.first == period) acc.last().second += credit else acc += period to mutableListOf(credit)
                        acc
                    }
                }
                groups.forEach { (period, credits) ->
                    Row(Modifier.fillMaxWidth().drawBehind { drawLine(RedesignColors.hairline, androidx.compose.ui.geometry.Offset(0f, 0f), androidx.compose.ui.geometry.Offset(size.width, 0f), 1.dp.toPx()) }.padding(vertical = 10.dp)) {
                        Text(
                            when (period) {
                                is ActingPeriod.Year -> period.value.toString()
                                ActingPeriod.Upcoming -> stringResource(Res.string.person_upcoming)
                                else -> "—"
                            },
                            style = redesignText(15.sp, FontWeight.Bold),
                            modifier = Modifier.width(110.dp).padding(top = 10.dp),
                        )
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            credits.forEach { credit ->
                                PersonCareerBoardRow(
                                    credit = credit,
                                    upcoming = period == ActingPeriod.Upcoming,
                                    // "as Mark Scout" for a role played; a crew job reads as itself ("Director").
                                    acting = shownDepartment == CareerDepartment.Acting || shownDepartment == CareerDepartment.Self,
                                    watched = WatchingState.isPosterWatched(watchedKeys, credit.media, fullyWatchedSeriesKeys),
                                    onOpen = { onOpenMeta(credit.media) },
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
private fun PersonCareerBoardRow(credit: ActingCredit, upcoming: Boolean, acting: Boolean, watched: Boolean, onOpen: () -> Unit) {
    val media = credit.media
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val fill by animateColorAsState(
        when {
            hovered -> RedesignColors.fillStrong
            watched -> Color.White.copy(alpha = 0.05f)
            else -> Color.Transparent
        },
        tween(160),
        label = "person_career_row",
    )
    val episodes = credit.episodeCount?.takeIf { media.type == "series" && it > 0 }?.let { pluralStringResource(Res.plurals.person_credit_episode_count, it, it) }
    val kind = when {
        upcoming -> media.creditDate()?.year?.toString()
        episodes != null -> episodes
        media.type == "series" -> stringResource(Res.string.seasonal_series)
        else -> stringResource(Res.string.person_board_movie)
    }
    val role = credit.characters.takeIf { it.isNotEmpty() }?.joinToString(" · ")?.let { if (acting) stringResource(Res.string.person_board_as_role, it) else it }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(fill).hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onOpen)
            .padding(horizontal = 10.dp, vertical = 6.dp).testTag("person_career_row"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(width = 40.dp, height = 60.dp).clip(RoundedCornerShape(7.dp)).background(Color(0xFF1B1D22)).border(1.dp, RedesignColors.hairline, RoundedCornerShape(7.dp))) {
            media.poster?.let { NuvioAsyncImage(model = it, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(media.name, style = redesignText(14.5.sp, FontWeight.Bold), maxLines = 1, overflow = TextOverflow.Ellipsis)
            val line = listOfNotNull(role, kind).joinToString(" · ")
            if (line.isNotBlank()) Text(line, style = redesignText(12.5.sp, color = RedesignColors.textMuted), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (watched) {
            Box(
                Modifier.padding(start = 12.dp).size(22.dp).clip(CircleShape).background(RedesignColors.textPrimary).testTag("person_career_watched"),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.Check, contentDescription = null, tint = RedesignOnLight, modifier = Modifier.size(14.dp)) }
        }
    }
}

// ─── Photos ──────────────────────────────────────────────────────────────────────────────────

private enum class PersonPhotoKind { Stills, Portraits }

/** Photos: tagged stills first (what people open most), then portraits; a tile opens the Gallery viewer. */
@Composable
private fun PersonPhotosTab(person: PersonDetail) {
    val kinds = buildList {
        if (person.taggedImages.isNotEmpty()) add(PersonPhotoKind.Stills)
        if (person.portraits.isNotEmpty()) add(PersonPhotoKind.Portraits)
    }
    var kindName by rememberSaveable(person.tmdbId) { mutableStateOf<String?>(null) }
    val kind = kinds.firstOrNull { it.name == kindName } ?: kinds.firstOrNull() ?: return
    var viewer by remember { mutableStateOf<Int?>(null) }
    val images = remember(person, kind) {
        if (kind == PersonPhotoKind.Stills) {
            person.taggedImages.map { GalleryImage(it.url, it.thumbnail, if (it.aspectRatio < 1f) GalleryKind.Poster else GalleryKind.Still, GallerySource.Tmdb, caption = it.title) }
        } else {
            person.portraits.map { GalleryImage(it, it.replace("/original/", "/w500/"), GalleryKind.Poster, GallerySource.Tmdb) }
        }
    }
    Column(Modifier.fillMaxWidth().testTag("person_board_photos"), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (kinds.size > 1) {
            RedesignPillGroup(
                options = kinds,
                selected = kind,
                label = { stringResource(if (it == PersonPhotoKind.Stills) Res.string.person_board_tagged_stills else Res.string.person_board_portraits) },
                onSelect = { kindName = it.name },
                optionTag = { "person_photos_${it.name}" },
                trailing = { if (it == PersonPhotoKind.Stills) maxOf(person.taggedImageTotal, person.taggedImages.size).toString() else person.portraits.size.toString() },
            )
        }
        AnimatedContent(targetState = images, transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(160)) }, label = "person_photos_grid") { shown ->
            val columns = if (kind == PersonPhotoKind.Stills) 4 else 8
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val gap = 14.dp
                val tile = (maxWidth - gap * (columns - 1)) / columns
                Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                    shown.chunked(columns).forEachIndexed { rowIndex, row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                            row.forEachIndexed { column, image ->
                                PersonPhotoTile(image, kind == PersonPhotoKind.Stills, Modifier.width(tile)) { viewer = rowIndex * columns + column }
                            }
                        }
                    }
                }
            }
        }
    }
    viewer?.let { start ->
        GalleryViewer(images = images, start = start.coerceIn(0, (images.size - 1).coerceAtLeast(0)), title = person.name, onSetBackground = null, onClose = { viewer = null })
    }
}

@Composable
private fun PersonPhotoTile(image: GalleryImage, still: Boolean, modifier: Modifier, onOpen: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val border by animateColorAsState(if (hovered) RedesignColors.hairlineFocused else RedesignColors.hairline, tween(160), label = "person_photo_tile")
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier.aspectRatio(if (still) 16f / 9f else 2f / 3f).clip(shape).background(RedesignColors.fill).border(1.dp, border, shape)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onOpen)
            .testTag("person_photo_tile"),
    ) {
        NuvioAsyncImage(model = image.thumbnail, contentDescription = image.caption, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        image.caption?.takeIf { still }?.let { caption ->
            Text(
                caption,
                style = redesignText(11.5.sp, FontWeight.SemiBold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.BottomStart).padding(8.dp).clip(RoundedCornerShape(7.dp))
                    .background(Color(0xB808090B)).padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
    }
}

// ─── About ───────────────────────────────────────────────────────────────────────────────────

@Composable
private fun PersonAboutTab(person: PersonDetail) {
    val wikidata by produceState<PersonWikidata?>(null, person.tmdbId) { value = PersonWikidataRepository.load(person.tmdbId) }
    val region = remember { androidx.compose.ui.text.intl.Locale.current.region }
    val personal = buildList {
        person.birthday?.let { birthday ->
            val age = calculateAge(birthday, person.deathday)
            add(stringResource(Res.string.person_detail_born) to listOfNotNull(formatDateForDisplay(birthday) ?: birthday, age?.let { "($it)" }).joinToString(" "))
        }
        person.deathday?.let { add(stringResource(Res.string.person_board_died) to (formatDateForDisplay(it) ?: it)) }
        person.placeOfBirth?.takeIf { it.isNotBlank() }?.let { add(stringResource(Res.string.person_detail_place_of_birth) to it) }
        person.knownFor?.let { known -> add(stringResource(Res.string.person_board_known_for) to (careerDepartmentLabel(careerDepartmentOf(known)))) }
        wikidata?.heightCm?.let { add(stringResource(Res.string.person_board_height) to formatPersonHeight(it, region)) }
        person.alsoKnownAs.take(2).takeIf { it.isNotEmpty() }?.let { add(stringResource(Res.string.person_board_also_known_as) to it.joinToString(", ")) }
    }
    val career = person.career()
    val mostEpisodes = remember(person) {
        person.actingCredits.filterNot { it.isSelfOnly() }.filter { (it.episodeCount ?: 0) > 0 }.maxByOrNull { it.episodeCount ?: 0 }?.media?.name
    }
    val careerFacts = buildList {
        add(stringResource(Res.string.person_detail_credits) to (person.movieCredits + person.tvCredits).distinctBy { it.stableKey() }.size.toString())
        val departments = career.keys.filter { it != CareerDepartment.Self }.map { careerDepartmentLabel(it) }
        if (departments.isNotEmpty()) add(stringResource(Res.string.person_board_departments) to departments.joinToString(", "))
        mostEpisodes?.let { add(stringResource(Res.string.person_board_most_episodes) to it) }
    }
    val ids = buildList {
        add("TMDB" to person.tmdbId.toString())
        person.imdbId?.let { add("IMDb" to it) }
        wikidata?.wikidataId?.let { add("Wikidata" to it) }
    }
    val links = personLinks(person, wikidata?.wikidataId)
    Column(Modifier.fillMaxWidth().testTag("person_board_about"), verticalArrangement = Arrangement.spacedBy(22.dp)) {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            if (personal.isNotEmpty()) FactsTile(stringResource(Res.string.person_board_personal), Modifier.weight(1f).testTag("person_board_personal")) { FactGrid(personal) }
            FactsTile(stringResource(Res.string.person_board_career), Modifier.weight(1f)) { FactGrid(careerFacts) }
            FactsTile(stringResource(Res.string.detail_facts_links), Modifier.weight(1f)) {
                FactGrid(ids)
                val uri = LocalUriHandler.current
                @OptIn(ExperimentalLayoutApi::class)
                FlowRow(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    links.forEach { (label, url) ->
                        RedesignPillButton(label = "$label ↗", onClick = { uri.openUri(url) }, modifier = Modifier.testTag("person_board_link_$label"))
                    }
                }
            }
        }
        person.biography?.takeIf { it.isNotBlank() }?.let { bio ->
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(Res.string.person_detail_biography), style = redesignText(18.sp, FontWeight.Bold))
                Text(bio, style = redesignText(14.5.sp, color = RedesignColors.textSecondary, lineHeight = 22.sp), modifier = Modifier.widthIn(max = 900.dp).testTag("person_board_bio"))
            }
        }
    }
}

// ─── Loading ─────────────────────────────────────────────────────────────────────────────────

/** The board while the person loads: the real geometry with blank content, so nothing moves. */
@Composable
internal fun PersonBoardSkeleton(
    personName: String,
    profilePhoto: String?,
    onBack: () -> Unit,
) {
    val topBar = LocalDetailTopBarState.current
    val listTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + if (topBar != null) DesktopMergedTopBarHeight else 0.dp
    val boardGap = if (topBar != null) DesktopTopBarContentGap else 16.dp
    BoxWithConstraints(Modifier.fillMaxSize().testTag("person_board_skeleton")) {
        val padding = desktopPageHorizontalPaddingForWidth(maxWidth.value)
        Column(Modifier.fillMaxWidth().padding(top = listTop)) {
            DetailBoard(
                expanded = false,
                hasSide = true,
                modifier = Modifier.padding(top = boardGap).padding(horizontal = padding),
                side = {
                    Box(Modifier.weight(1f).fillMaxWidth().boardTile())
                    Box(Modifier.height(136.dp).fillMaxWidth().boardTile())
                    Box(Modifier.height(132.dp).fillMaxWidth().boardTile())
                },
            ) {
                NuvioBackButton(
                    onClick = onBack,
                    modifier = Modifier.align(Alignment.TopStart).padding(14.dp),
                    containerColor = Color.Black.copy(alpha = 0.34f),
                    showContainerOnDesktop = true,
                    contentColor = RedesignColors.textPrimary,
                    buttonSize = 48.dp,
                    iconSize = 24.dp,
                )
                Row(Modifier.align(Alignment.BottomStart).padding(start = 28.dp, bottom = 24.dp), verticalAlignment = Alignment.Bottom) {
                    Box(Modifier.size(width = 150.dp, height = 225.dp).clip(PersonPortraitShape).background(RedesignColors.fill)) {
                        profilePhoto?.let { AsyncImage(model = it, contentDescription = personName, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                    }
                    Spacer(Modifier.width(26.dp))
                    Column {
                        SkeletonPill(130.dp, 11.dp)
                        Spacer(Modifier.height(12.dp))
                        Text(personName, style = redesignText(44.sp, FontWeight.ExtraBold, lineHeight = 46.sp, letterSpacing = RedesignTightTracking), maxLines = 2)
                        Spacer(Modifier.height(16.dp))
                        SkeletonPill(380.dp, 13.dp)
                        Spacer(Modifier.height(16.dp))
                        SkeletonPill(560.dp, 12.dp)
                        Spacer(Modifier.height(9.dp))
                        SkeletonPill(520.dp, 12.dp)
                        Spacer(Modifier.height(9.dp))
                        SkeletonPill(300.dp, 12.dp)
                        Spacer(Modifier.height(22.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            SkeletonPill(128.dp, 44.dp)
                            SkeletonPill(44.dp, 44.dp)
                        }
                    }
                }
            }
            Box(Modifier.fillMaxWidth().height(DetailBoardTabsHeight).padding(horizontal = padding)) {
                Row(Modifier.align(Alignment.CenterStart), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                    listOf(80.dp, 70.dp, 64.dp, 64.dp, 64.dp, 52.dp).forEach { SkeletonPill(it, 12.dp) }
                }
                Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(1.dp).background(RedesignColors.hairline))
            }
            Box(Modifier.padding(start = padding, top = 20.dp)) { PersonPosterSkeletonRow() }
        }
    }
}

@Composable
private fun SkeletonPill(width: Dp, height: Dp) {
    Box(Modifier.width(width).height(height).clip(RoundedCornerShape(height / 2)).background(RedesignColors.fill))
}
