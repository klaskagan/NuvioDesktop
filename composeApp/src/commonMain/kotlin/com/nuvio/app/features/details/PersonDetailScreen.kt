package com.nuvio.app.features.details

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.background
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.widthIn
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.ui.unit.em
import com.nuvio.app.core.ui.BackdropFocusController
import com.nuvio.app.core.ui.RedesignColors
import com.nuvio.app.core.ui.RedesignTightTracking
import com.nuvio.app.core.ui.RedesignTypography
import com.nuvio.app.core.ui.ShellBackdrop
import com.nuvio.app.core.ui.NavLayer
import com.nuvio.app.core.ui.NavSide
import com.nuvio.app.core.ui.NavTransitionPreview
import com.nuvio.app.core.ui.NavTransitionStyle
import com.nuvio.app.core.ui.navTransitionLayer
import com.nuvio.app.core.ui.redesignText
import nuvio.composeapp.generated.resources.person_favorite
import androidx.compose.foundation.clickable
import androidx.compose.ui.semantics.Role
import com.nuvio.app.features.details.components.InlineTmdbRating
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.platform.testTag
import com.nuvio.app.core.ui.NuvioShelfSectionHeader
import com.nuvio.app.core.ui.NuvioViewAllPillSize
import com.nuvio.app.core.ui.PlatformBackHandler
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.features.home.PosterShape
import com.nuvio.app.features.home.components.HomePosterCard
import com.nuvio.app.features.home.stableKey
import com.nuvio.app.features.watching.application.WatchingState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed

import kotlinx.datetime.LocalDate
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.isDesktop
import com.nuvio.app.core.ui.NuvioAsyncImage as AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import com.nuvio.app.core.i18n.localizedShortMonthName
import com.nuvio.app.core.ui.NuvioBackButton
import com.nuvio.app.core.ui.NuvioDesktopVerticalScrollbar
import com.nuvio.app.core.ui.desktopPageHorizontalPaddingForWidth
import com.nuvio.app.core.ui.SkeletonPosterRow
import com.nuvio.app.core.ui.landscapePosterHeightForWidth
import com.nuvio.app.core.ui.landscapePosterWidth
import com.nuvio.app.core.ui.rememberPosterCardStyleUiState
import com.nuvio.app.core.ui.skeleton
import com.nuvio.app.features.details.components.DetailPosterRailSection
import com.nuvio.app.features.details.components.ExpandableDescription
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.tmdb.TmdbMetadataService
import com.nuvio.app.core.poster.withCustomPosterUrls
import com.nuvio.app.features.watched.WatchedRepository
import com.nuvio.app.features.watchprogress.CurrentDateProvider
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import com.nuvio.app.navigation.LocalUseNativeNavigation

private sealed interface PersonDetailUiState {
    data object Loading : PersonDetailUiState
    data class Success(val personDetail: PersonDetail) : PersonDetailUiState
    data class Error(val message: String) : PersonDetailUiState
}

internal class PersonDetailLoaders(
    val detail: suspend (Int, Boolean) -> PersonDetail? = TmdbMetadataService::fetchPersonDetail,
    val knownFor: suspend (PersonDetail) -> List<MetaPreview> = TmdbMetadataService::fetchPersonKnownFor,
    val cachedKnownFor: (Int) -> List<MetaPreview>? = TmdbMetadataService::cachedPersonKnownFor,
)

internal val LocalPersonDetailLoaders = staticCompositionLocalOf { PersonDetailLoaders() }

@Composable
@OptIn(ExperimentalSharedTransitionApi::class)
fun PersonDetailScreen(
    personId: Int,
    personName: String,
    initialProfilePhoto: String? = null,
    avatarTransitionKey: String? = null,
    preferCrew: Boolean = false,
    onBack: () -> Unit,
    onOpenMeta: (MetaPreview) -> Unit,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    modifier: Modifier = Modifier,
    initialSubview: PersonSubview? = null,
    onOpenNativeSubview: ((PersonSubview) -> Unit)? = null,
) {
    val loaders = LocalPersonDetailLoaders.current
    var localSubview by rememberSaveable(personId, preferCrew) { mutableStateOf<String?>(null) }
    val subview = initialSubview ?: localSubview?.let(PersonSubview::valueOf)
    val goBack = { if (localSubview != null) localSubview = null else onBack() }
    PlatformBackHandler(enabled = localSubview != null, onBack = goBack)
    var retry by remember(personId, preferCrew) { mutableStateOf(0) }
    var uiState by remember(personId, preferCrew) { mutableStateOf<PersonDetailUiState>(PersonDetailUiState.Loading) }
    val watchedUiState by remember {
        WatchedRepository.ensureLoaded()
        WatchedRepository.uiState
    }.collectAsStateWithLifecycle()
    val fullyWatchedSeriesKeys by WatchedRepository.fullyWatchedSeriesKeys.collectAsStateWithLifecycle()
    val resolvedAvatarTransitionKey = avatarTransitionKey ?: castAvatarSharedTransitionKey(personId)

    LaunchedEffect(personId, preferCrew, retry, loaders) {
        uiState = PersonDetailUiState.Loading
        val detail = loaders.detail(personId, preferCrew)
        uiState = if (detail != null) {
            val pattern = com.nuvio.app.core.poster.CustomPosterUrlRepository.let { repo ->
                repo.ensureLoaded()
                repo.patternForScreen(com.nuvio.app.core.poster.CustomPosterScreen.DETAILS)
            }
            PersonDetailUiState.Success(detail.withCustomPosterUrls(pattern))
        } else {
            PersonDetailUiState.Error(getString(Res.string.person_load_failed, personName))
        }
    }

    // Desktop redesign: ambient backdrop from the person's most popular credit, Inter type, and
    // a crossfade between loading, error and content instead of a hard swap.
    val backdrop = remember { if (isDesktop) BackdropFocusController() else null }
    val backdropArt = (uiState as? PersonDetailUiState.Success)?.personDetail?.let(::personBackdropArt)
    LaunchedEffect(backdrop, backdropArt) { backdrop?.base = backdropArt }
    Box(
        modifier = modifier
            .fillMaxSize()
            .then(if (backdrop == null) Modifier.background(MaterialTheme.colorScheme.background) else Modifier),
    ) {
        // Layers animate with the shared desktop nav transition (navTransitionLayer): Home hero's
        // layered parallax or a full slide, while the nav entry itself switches instantly.
        if (backdrop != null) {
            Box(Modifier.fillMaxSize().navTransitionLayer(NavLayer.Background, NavSide.Above)) {
                ShellBackdrop(backdrop)
            }
        }
        val slide = Modifier.navTransitionLayer(NavLayer.Content, NavSide.Above)
        Box(Modifier.fillMaxSize().then(slide)) {
        RedesignTypography(enabled = isDesktop) {
        AnimatedContent(
            targetState = uiState,
            contentKey = { it::class },
            transitionSpec = { fadeIn(tween(260)) togetherWith fadeOut(tween(180)) },
            label = "person_detail_state",
        ) { state ->
        when (state) {
            is PersonDetailUiState.Loading -> PersonDetailSkeleton(
                personId = personId,
                personName = personName,
                profilePhoto = initialProfilePhoto,
                avatarTransitionKey = resolvedAvatarTransitionKey,
                sharedTransitionScope = sharedTransitionScope,
                animatedVisibilityScope = animatedVisibilityScope,
            )
            is PersonDetailUiState.Error -> PersonDetailError(
                message = state.message,
                onRetry = { retry++ },
            )
            is PersonDetailUiState.Success -> {
                val person = state.personDetail
                val pattern = com.nuvio.app.core.poster.CustomPosterUrlRepository
                    .patternForScreen(com.nuvio.app.core.poster.CustomPosterScreen.DETAILS)
                var knownFor by remember(person, loaders, pattern) {
                    mutableStateOf(loaders.cachedKnownFor(person.tmdbId)?.withCustomPosterUrls(pattern))
                }
                LaunchedEffect(person, loaders, pattern) {
                    if (knownFor == null) knownFor = loaders.knownFor(person).withCustomPosterUrls(pattern)
                }
                PersonDetailContent(
                    person = person,
                    knownFor = knownFor,
                    subview = subview,
                    onOpenSubview = onOpenNativeSubview ?: { localSubview = it.name },
                    watchedKeys = watchedUiState.watchedKeys,
                    fullyWatchedSeriesKeys = fullyWatchedSeriesKeys,
                    onOpenMeta = onOpenMeta,
                    onBack = goBack,
                    initialProfilePhoto = initialProfilePhoto,
                    avatarTransitionKey = resolvedAvatarTransitionKey,
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = animatedVisibilityScope,
                )
            }
        }
        }
        }
        }


        if (isDesktop) {
            // TEMPORARY: compare the two transition styles; remove once one is chosen.
            NavTransitionStyleToggle(Modifier.align(Alignment.TopEnd).windowInsetsPadding(WindowInsets.statusBars).padding(top = 36.dp, end = 40.dp))
        }
        if (!LocalUseNativeNavigation.current) {
            if (isDesktop) {
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    NuvioBackButton(
                        onClick = goBack,
                        modifier = Modifier
                            .windowInsetsPadding(WindowInsets.statusBars)
                            .padding(
                                start = desktopPageHorizontalPaddingForWidth(maxWidth.value),
                                top = 32.dp,
                            )
                            .align(Alignment.TopStart),
                        containerColor = Color.Transparent,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        buttonSize = 48.dp,
                        iconSize = 24.dp,
                    )
                }
            } else {
                IconButton(
                    onClick = goBack,
                    modifier = Modifier
                        .windowInsetsPadding(WindowInsets.statusBars)
                        .padding(start = 4.dp, top = 4.dp)
                        .align(Alignment.TopStart),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = stringResource(Res.string.action_back),
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalSharedTransitionApi::class)
private fun PersonDetailContent(
    person: PersonDetail,
    knownFor: List<MetaPreview>?,
    subview: PersonSubview?,
    onOpenSubview: (PersonSubview) -> Unit,
    watchedKeys: Set<String>,
    fullyWatchedSeriesKeys: Set<String>,
    onOpenMeta: (MetaPreview) -> Unit,
    onBack: () -> Unit,
    initialProfilePhoto: String?,
    avatarTransitionKey: String,
    sharedTransitionScope: SharedTransitionScope?,
    animatedVisibilityScope: AnimatedVisibilityScope?,
) {
    val accent = MaterialTheme.colorScheme.primary
    val background = MaterialTheme.colorScheme.background
    val gradient = remember(accent, background) {
        Brush.verticalGradient(
            0.0f to accent.copy(alpha = 0.18f),
            0.15f to accent.copy(alpha = 0.10f),
            0.30f to accent.copy(alpha = 0.04f),
            0.50f to Color.Transparent,
        )
    }
    val today = LocalDate.parse(CurrentDateProvider.todayIsoDate())
    val sections = remember(person, today) {
        listOf(
            PersonSubview.Movies to filterPersonCredits(person.movieCredits, PersonCreditQuery(), today),
            PersonSubview.Series to filterPersonCredits(person.tvCredits, PersonCreditQuery(), today),
            PersonSubview.Upcoming to upcomingPersonCredits(person, today),
        ).filter { it.second.isNotEmpty() }
    }
    val timeline = remember(person.actingCredits, today) { personActingTimeline(person.actingCredits, today).values.flatten() }
    val wideList = rememberLazyListState()
    val compactList = rememberLazyListState()
    val paneState = rememberSaveableStateHolder()

    BoxWithConstraints(
        Modifier.fillMaxSize().then(if (isDesktop) Modifier else Modifier.background(background).background(gradient)),
    ) {
        val wide = maxWidth >= PERSON_DETAIL_WIDE_LAYOUT_MIN_WIDTH
        val padding = if (wide) 40.dp else 20.dp
        val list = if (wide) wideList else compactList
        val topPadding = if (wide) 34.dp else 48.dp
        val controlsTopInset = when {
            wide || LocalUseNativeNavigation.current -> 0.dp
            isDesktop -> 36.dp
            else -> 8.dp
        }
        // Desktop redesign: a full-width header scrolls with the page instead of a pinned side pane.
        val redesignHeader = wide && isDesktop
        val sidebar = wide && !isDesktop
        Row(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars).padding(top = if (redesignHeader) 0.dp else topPadding)) {
            if (sidebar) {
                PersonIdentitySidebar(
                    person, initialProfilePhoto, avatarTransitionKey, sharedTransitionScope, animatedVisibilityScope,
                    Modifier.width(PERSON_DETAIL_WIDE_SIDEBAR_WIDTH).fillMaxHeight().testTag("actor_preview_wide")
                        .then(if (isDesktop) Modifier.background(Color.White.copy(alpha = 0.035f)) else Modifier),
                )
                Box(
                    Modifier.width(1.dp).fillMaxHeight().background(
                        if (isDesktop) RedesignColors.hairline else MaterialTheme.colorScheme.outline.copy(alpha = 0.30f),
                    ),
                )
            }
            Column(Modifier.weight(1f).fillMaxHeight().clipToBounds()) {
                if (subview != null) {
                    paneState.SaveableStateProvider(subview.name) {
                        PersonExpandedContent(
                            person, subview, knownFor, padding,
                            profileHeader = when {
                                redesignHeader -> {
                                    { _ ->
                                        PersonCompactHeader(
                                            person, initialProfilePhoto, avatarTransitionKey,
                                            Modifier.padding(top = PERSON_REDESIGN_TOP_INSET).testTag("actor_header_compact"),
                                        )
                                    }
                                }
                                !wide -> {
                                    { grid ->
                                        HeroSection(
                                            person, initialProfilePhoto, avatarTransitionKey, horizontalPadding = 0.dp,
                                            scrollOffset = {
                                                heroScrollOffset(grid.firstVisibleItemIndex, grid.firstVisibleItemScrollOffset)
                                            },
                                        )
                                    }
                                }
                                else -> null
                            },
                            controlsTopInset = controlsTopInset,
                            watchedKeys = watchedKeys,
                            fullyWatchedSeriesKeys = fullyWatchedSeriesKeys,
                            onOpenMeta = onOpenMeta,
                            onBack = onBack,
                        )
                    }
                } else {
                    paneState.SaveableStateProvider(if (wide) "wide-overview" else "compact-overview") {
                        PersonOverview(
                            person, knownFor, sections, timeline, today, list, wide, padding, initialProfilePhoto, avatarTransitionKey,
                            sharedTransitionScope, animatedVisibilityScope, watchedKeys, fullyWatchedSeriesKeys,
                            onOpenSubview, onOpenMeta,
                            header = if (redesignHeader) {
                                {
                                    PersonRedesignHeader(
                                        person, initialProfilePhoto, avatarTransitionKey, sharedTransitionScope, animatedVisibilityScope,
                                        Modifier.padding(horizontal = padding).padding(top = PERSON_REDESIGN_TOP_INSET, bottom = 28.dp)
                                            .testTag("actor_preview_wide"),
                                    )
                                }
                            } else null,
                        )
                    }
                }
            }
        }
    }
}

private val PERSON_DETAIL_WIDE_LAYOUT_MIN_WIDTH = 900.dp
private val PERSON_DETAIL_WIDE_SIDEBAR_WIDTH = 392.dp
/** Clears the 48dp desktop back button (top 32dp) before the redesigned header starts. */
private val PERSON_REDESIGN_TOP_INSET = 96.dp
private const val HERO_COLLAPSE_SCROLL_RANGE = 220
private fun heroScrollOffset(firstItem: Int, offset: Int): Int =
    if (firstItem > 0) HERO_COLLAPSE_SCROLL_RANGE else offset
private const val HAPTIC_TRIGGER_SCROLL_THRESHOLD_PX = 56

@Composable
@OptIn(ExperimentalSharedTransitionApi::class)
private fun PersonOverview(
    person: PersonDetail,
    knownFor: List<MetaPreview>?,
    sections: List<Pair<PersonSubview, List<MetaPreview>>>,
    timeline: List<ActingCredit>,
    today: LocalDate,
    list: LazyListState,
    wide: Boolean,
    padding: Dp,
    fallbackProfilePhoto: String?,
    avatarTransitionKey: String,
    sharedTransitionScope: SharedTransitionScope?,
    animatedVisibilityScope: AnimatedVisibilityScope?,
    watchedKeys: Set<String>,
    fullyWatchedSeriesKeys: Set<String>,
    onOpenSubview: (PersonSubview) -> Unit,
    onOpenMeta: (MetaPreview) -> Unit,
    header: (@Composable () -> Unit)? = null,
) {
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = list,
            modifier = Modifier.fillMaxSize().testTag("actor_overview"),
            contentPadding = PaddingValues(bottom = 40.dp),
        ) {
            if (header != null) {
                item("actor-header") { header() }
            }
            if (!wide) {
                item("actor-hero") {
                    HeroSection(
                        person, fallbackProfilePhoto, avatarTransitionKey, sharedTransitionScope, animatedVisibilityScope,
                        scrollOffset = { heroScrollOffset(list.firstVisibleItemIndex, list.firstVisibleItemScrollOffset) },
                    )
                }
            }
            if (knownFor == null || knownFor.isNotEmpty()) {
                item("shelf:KnownFor") {
                    Crossfade(knownFor, animationSpec = tween(320), label = "known_for") { knownFor ->
                    if (knownFor == null) {
                        // Keep this key at the top while the curated shelf loads. A prepended item
                        // would leave LazyColumn anchored to Movies after the response arrives.
                        // On desktop it reserves the row's height, so arriving data doesn't push the
                        // rows below down (that shift read as motion starting from the top right).
                        if (isDesktop) KnownForPlaceholder(padding) else Spacer(Modifier.height(1.dp))
                    } else {
                        DetailPosterRailSection(
                            title = stringResource(Res.string.person_known_for_shelf),
                            items = knownFor,
                            modifier = Modifier.padding(top = if (wide) 0.dp else 24.dp),
                            watchedKeys = watchedKeys,
                            fullyWatchedSeriesKeys = fullyWatchedSeriesKeys,
                            headerHorizontalPadding = padding,
                            onPosterClick = onOpenMeta,
                            onViewAllClick = { onOpenSubview(PersonSubview.KnownFor) },
                            viewAllPillSize = NuvioViewAllPillSize.Compact,
                        )
                    }
                    }
                }
            }
            items(sections, key = { "shelf:${it.first.name}" }) { (section, credits) ->
                DetailPosterRailSection(
                    title = stringResource(section.titleResource),
                    items = credits,
                    modifier = Modifier.animateItem(fadeInSpec = tween(220), placementSpec = tween(260))
                        .padding(top = if (wide && knownFor.isNullOrEmpty() && section == sections.first().first) 0.dp else 24.dp),
                    watchedKeys = watchedKeys,
                    fullyWatchedSeriesKeys = fullyWatchedSeriesKeys,
                    headerHorizontalPadding = padding,
                    onPosterClick = onOpenMeta,
                    onViewAllClick = { onOpenSubview(section) },
                    viewAllPillSize = NuvioViewAllPillSize.Compact,
                )
            }
            if (timeline.isNotEmpty()) {
                item("timeline-heading") {
                    NuvioShelfSectionHeader(
                        stringResource(Res.string.person_acting_timeline),
                        Modifier.padding(start = padding, end = padding, top = 24.dp),
                        viewAllPillSize = NuvioViewAllPillSize.Compact,
                    )
                }
            }
            itemsIndexed(timeline, key = { _, credit -> credit.media.stableKey() }) { index, credit ->
                val media = credit.media
                val open = { onOpenMeta(media) }
                val lineColor = MaterialTheme.colorScheme.outline
                val dotColor = MaterialTheme.nuvio.colors.textMuted
                val yearWidth = 44.dp
                val gap = 8.dp
                val markerWidth = 10.dp
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = padding)
                        .drawBehind {
                            val fromStart = (yearWidth + gap + markerWidth / 2).toPx()
                            val x = if (layoutDirection == LayoutDirection.Ltr) fromStart else size.width - fromStart
                            val mid = size.height / 2
                            drawLine(
                                lineColor, Offset(x, if (index == 0) mid else 0f),
                                Offset(x, if (index == timeline.lastIndex) mid else size.height), 1.dp.toPx(),
                            )
                            drawCircle(dotColor, 3.dp.toPx(), Offset(x, mid))
                        }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(gap),
                ) {
                    val date = media.creditDate()
                    Text(
                        date?.year?.toString() ?: "—",
                        Modifier.width(yearWidth),
                        textAlign = TextAlign.End,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.nuvio.colors.textMuted,
                    )
                    Spacer(Modifier.width(markerWidth))
                    HomePosterCard(
                        item = media, basePosterWidthDp = 56, respectGlobalPosterStyle = false,
                        enhanceLandscapeTitles = false, showTitleBelow = false, placeholderTitle = "",
                        isWatched = WatchingState.isPosterWatched(watchedKeys, media, fullyWatchedSeriesKeys),
                        onClick = open,
                    )
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            media.name,
                            Modifier.clickable(role = Role.Button, onClick = open).padding(vertical = 2.dp),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.nuvio.colors.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        val type = when (credit.kind) {
                            ActingCreditKind.Movie -> null
                            ActingCreditKind.TvMovie -> stringResource(Res.string.person_credit_tv_movie)
                            ActingCreditKind.Video -> stringResource(Res.string.person_credit_video)
                            ActingCreditKind.TvSeries -> stringResource(Res.string.person_credit_tv_series)
                        }
                        val upcoming = date != null && date > today
                        if (media.sortRating != null || type != null || upcoming) {
                            val muted = MaterialTheme.nuvio.colors.textMuted
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                media.sortRating?.let { InlineTmdbRating(it, media.voteCount) }
                                if (media.sortRating != null && type != null) {
                                    Box(Modifier.size(3.dp).background(muted, CircleShape))
                                }
                                val label = listOfNotNull(
                                    type,
                                    if (upcoming) stringResource(Res.string.person_upcoming) else null,
                                ).joinToString(" · ")
                                if (label.isNotEmpty()) {
                                    Text(
                                        label,
                                        modifier = if (credit.episodeCount != null) Modifier.weight(1f) else Modifier,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = muted,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                credit.episodeCount?.let { count ->
                                    Text(
                                        if (credit.episodeCountIsMinimum) {
                                            stringResource(Res.string.person_credit_episode_minimum, count)
                                        } else {
                                            pluralStringResource(Res.plurals.person_credit_episode_count, count, count)
                                        },
                                        style = MaterialTheme.typography.labelMedium,
                                        color = muted,
                                        maxLines = 1,
                                    )
                                }
                            }
                        }
                        if (credit.characters.isNotEmpty()) {
                            Text(
                                credit.characters.joinToString(" · "),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.nuvio.colors.textMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
        NuvioDesktopVerticalScrollbar(
            state = list,
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(vertical = 8.dp, horizontal = 4.dp),
        )
    }
}

@Composable
@OptIn(ExperimentalSharedTransitionApi::class)
internal fun PersonIdentitySidebar(
    person: PersonDetail,
    fallbackProfilePhoto: String?,
    avatarTransitionKey: String,
    sharedTransitionScope: SharedTransitionScope?,
    animatedVisibilityScope: AnimatedVisibilityScope?,
    modifier: Modifier = Modifier,
) {
    val accentColor = MaterialTheme.colorScheme.primary
    val avatarUrl = person.profilePhoto?.takeIf { it.isNotBlank() } ?: fallbackProfilePhoto
    val platformContext = LocalPlatformContext.current
    val avatarRequest = if (!avatarUrl.isNullOrBlank()) {
        remember(platformContext, avatarUrl, avatarTransitionKey) {
            ImageRequest.Builder(platformContext)
                .data(avatarUrl)
                .memoryCacheKey(avatarTransitionKey)
                .placeholderMemoryCacheKey(avatarTransitionKey)
                .diskCacheKey(avatarUrl)
                .build()
        }
    } else {
        null
    }
    val avatarSharedElementModifier = if (sharedTransitionScope != null && animatedVisibilityScope != null) {
        with(sharedTransitionScope) {
            Modifier.sharedElement(
                sharedContentState = rememberSharedContentState(key = avatarTransitionKey),
                animatedVisibilityScope = animatedVisibilityScope,
            )
        }
    } else {
        Modifier
    }
    val credits = remember(person.movieCredits, person.tvCredits) {
        (person.movieCredits + person.tvCredits).distinctBy { it.id }
    }
    val creditSummary = remember(credits) {
        buildCreditSummary(credits)
    }
    val scrollState = rememberScrollState()

    Box(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(
                    start = 40.dp,
                    end = 36.dp,
                    top = if (isDesktop) 72.dp else 40.dp,
                    bottom = 42.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            Box(
                modifier = Modifier.size(162.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clip(CircleShape)
                        .background(accentColor.copy(alpha = 0.14f)),
                )
                Box(
                    modifier = Modifier
                        .then(avatarSharedElementModifier)
                        .size(148.dp)
                        .clip(CircleShape)
                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.40f), CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    if (!avatarUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = avatarRequest ?: avatarUrl,
                            contentDescription = person.name,
                            modifier = Modifier.matchParentSize(),
                            contentScale = ContentScale.Crop,
                        )
                    } else {
                        Text(
                            text = person.name.initials(),
                            style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (isDesktop) {
                PersonRedesignIdentity(person, creditSummary)
            } else {
            Column(verticalArrangement = Arrangement.spacedBy(11.dp)) {
                Text(
                    text = person.name,
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-0.5).sp,
                        lineHeight = 34.sp,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(15.dp)) {
                person.birthday?.let { birthday ->
                    PersonSidebarFact(
                        label = stringResource(Res.string.person_detail_born),
                        value = personBirthLine(birthday = birthday, deathday = person.deathday),
                    )
                }
                person.placeOfBirth?.takeIf { it.isNotBlank() }?.let { place ->
                    PersonSidebarFact(label = stringResource(Res.string.person_detail_place_of_birth), value = place)
                }
                if (creditSummary.isNotBlank()) {
                    PersonSidebarFact(label = stringResource(Res.string.person_detail_credits), value = creditSummary)
                }
            }
            }

            person.biography?.takeIf { it.isNotBlank() }?.let { biography ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.30f)),
                )
                Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    SidebarLabel(text = stringResource(Res.string.person_detail_biography))
                    ExpandableDescription(
                        text = biography,
                        style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        collapsedMaxLines = 12,
                    )
                }
            }
        }
        NuvioDesktopVerticalScrollbar(
            state = scrollState,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .padding(vertical = 8.dp, horizontal = 4.dp),
        )
    }
}

@Composable
private fun PersonSidebarFact(
    label: String,
    value: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        SidebarLabel(text = label)
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SidebarLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall.copy(
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.0.sp,
        ),
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.70f),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
@OptIn(ExperimentalSharedTransitionApi::class)
private fun HeroSection(
    person: PersonDetail,
    fallbackProfilePhoto: String? = null,
    avatarTransitionKey: String,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    horizontalPadding: Dp = 20.dp,
    scrollOffset: () -> Int,
) {
    val offset = scrollOffset()
    val collapseProgress = (offset.toFloat() / HERO_COLLAPSE_SCROLL_RANGE).coerceIn(0f, 1f)
    val haptic = LocalHapticFeedback.current
    var hapticPlayed by rememberSaveable(person.tmdbId) { mutableStateOf(false) }
    val shouldPlay = offset >= HAPTIC_TRIGGER_SCROLL_THRESHOLD_PX
    LaunchedEffect(shouldPlay) {
        if (shouldPlay && !hapticPlayed) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            hapticPlayed = true
        }
    }
    val avatarSize = lerp(140.dp, 72.dp, collapseProgress)
    val heroScale = 1f - (collapseProgress * 0.12f)
    val heroAlpha = 1f - (collapseProgress * 0.35f)
    val avatarUrl = person.profilePhoto?.takeIf { it.isNotBlank() } ?: fallbackProfilePhoto
    val avatarCacheKey = avatarTransitionKey
    val platformContext = LocalPlatformContext.current
    val avatarRequest = if (!avatarUrl.isNullOrBlank()) {
        remember(platformContext, avatarUrl, avatarCacheKey) {
            ImageRequest.Builder(platformContext)
                .data(avatarUrl)
                .memoryCacheKey(avatarCacheKey)
                .placeholderMemoryCacheKey(avatarCacheKey)
                .diskCacheKey(avatarUrl)
                .build()
        }
    } else {
        null
    }
    val avatarSharedElementModifier = if (sharedTransitionScope != null && animatedVisibilityScope != null) {
        with(sharedTransitionScope) {
            Modifier.sharedElement(
                sharedContentState = rememberSharedContentState(
                    key = avatarTransitionKey,
                ),
                animatedVisibilityScope = animatedVisibilityScope,
            )
        }
    } else {
        Modifier
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPadding)
            .graphicsLayer {
                scaleX = heroScale
                scaleY = heroScale
                alpha = heroAlpha
                translationY = -(collapseProgress * 40f)
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Profile Photo
        Box(
            modifier = Modifier
                .then(avatarSharedElementModifier)
                .size(avatarSize)
                .testTag("actor_profile_photo")
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            if (!avatarUrl.isNullOrBlank()) {
                AsyncImage(
                    model = avatarRequest ?: avatarUrl,
                    contentDescription = person.name,
                    modifier = Modifier.matchParentSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Text(
                    text = person.name.firstOrNull()?.uppercase() ?: "?",
                    style = MaterialTheme.typography.displayMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Name
        Text(
            text = person.name,
            style = MaterialTheme.typography.headlineSmall.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.5).sp,
            ),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Personal info
        val infoItems = buildList {
            person.birthday?.let { bday ->
                val age = calculateAge(bday, person.deathday)
                val ageStr = if (age != null) stringResource(Res.string.person_age, age) else ""
                val bdayDisplay = formatDateForDisplay(bday) ?: bday
                val deathDisplay = person.deathday?.let { formatDateForDisplay(it) ?: it }
                val line = if (deathDisplay != null) {
                    buildString {
                        append(stringResource(Res.string.person_born, bdayDisplay, ""))
                        append(" — ")
                        append(stringResource(Res.string.person_died, deathDisplay))
                        append(ageStr)
                    }
                } else {
                    stringResource(Res.string.person_born, bdayDisplay, ageStr)
                }
                add(line)
            }
            person.placeOfBirth?.let { add(it) }
            person.knownFor?.let { add(stringResource(Res.string.person_known_for, it)) }
        }
        if (infoItems.isNotEmpty()) {
            infoItems.forEach { info ->
                Text(
                    text = info,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(2.dp))
            }
        }

        // Biography
        person.biography?.let { bio ->
            Spacer(modifier = Modifier.height(12.dp))
            ExpandableDescription(
                text = bio,
                style = MaterialTheme.typography.bodyMedium.copy(
                    lineHeight = 20.sp,
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                collapsedMaxLines = 8,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

// ─── Loading / Error States ───

@Composable
@OptIn(ExperimentalSharedTransitionApi::class)
private fun PersonDetailSkeleton(
    personId: Int,
    personName: String,
    profilePhoto: String? = null,
    avatarTransitionKey: String,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
) {
    val posterCardStyle = rememberPosterCardStyleUiState()
    val isLandscapeShelfMode = posterCardStyle.catalogLandscapeModeEnabled
    val skeletonPosterWidth = if (isLandscapeShelfMode) {
        landscapePosterWidth(posterCardStyle.widthDp)
    } else {
        posterCardStyle.widthDp.dp
    }
    val skeletonPosterHeight = if (isLandscapeShelfMode) {
        landscapePosterHeightForWidth(skeletonPosterWidth)
    } else {
        posterCardStyle.heightDp.dp
    }
    val accentColor = MaterialTheme.colorScheme.primary
    val avatarCacheKey = avatarTransitionKey
    val platformContext = LocalPlatformContext.current
    val avatarRequest = if (!profilePhoto.isNullOrBlank()) {
        remember(platformContext, profilePhoto, avatarCacheKey) {
            ImageRequest.Builder(platformContext)
                .data(profilePhoto)
                .memoryCacheKey(avatarCacheKey)
                .placeholderMemoryCacheKey(avatarCacheKey)
                .diskCacheKey(profilePhoto)
                .build()
        }
    } else {
        null
    }
    val accentGradient = remember(accentColor) {
        Brush.verticalGradient(
            colorStops = arrayOf(
                0.0f to accentColor.copy(alpha = 0.18f),
                0.15f to accentColor.copy(alpha = 0.10f),
                0.30f to accentColor.copy(alpha = 0.04f),
                0.50f to Color.Transparent,
            ),
        )
    }
    val avatarSharedElementModifier = if (sharedTransitionScope != null && animatedVisibilityScope != null) {
        with(sharedTransitionScope) {
            Modifier.sharedElement(
                sharedContentState = rememberSharedContentState(
                    key = avatarTransitionKey,
                ),
                animatedVisibilityScope = animatedVisibilityScope,
            )
        }
    } else {
        Modifier
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(if (isDesktop) Modifier else Modifier.background(MaterialTheme.colorScheme.background)),
    ) {
        if (!isDesktop) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(accentGradient),
            )
        }

        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            if (maxWidth >= PERSON_DETAIL_WIDE_LAYOUT_MIN_WIDTH && isDesktop) {
                // Mirrors PersonRedesignHeader so loading → content is a crossfade, not a re-layout.
                DesktopPersonDetailSkeleton(
                    personName = personName,
                    avatarRequest = avatarRequest ?: profilePhoto,
                    avatarSharedElementModifier = Modifier,
                    skeletonPosterWidth = skeletonPosterWidth,
                    skeletonPosterHeight = skeletonPosterHeight,
                    skeletonPosterCornerRadius = posterCardStyle.cornerRadiusDp.dp,
                    showPosterLabels = !isLandscapeShelfMode && !posterCardStyle.hideLabelsEnabled,
                )
            } else if (maxWidth >= PERSON_DETAIL_WIDE_LAYOUT_MIN_WIDTH) {
                WidePersonDetailSkeleton(
                    personName = personName,
                    profilePhoto = profilePhoto,
                    avatarRequest = avatarRequest,
                    avatarSharedElementModifier = avatarSharedElementModifier,
                    skeletonPosterWidth = skeletonPosterWidth,
                    skeletonPosterHeight = skeletonPosterHeight,
                    skeletonPosterCornerRadius = posterCardStyle.cornerRadiusDp.dp,
                    showPosterLabels = !isLandscapeShelfMode && !posterCardStyle.hideLabelsEnabled,
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .windowInsetsPadding(WindowInsets.statusBars)
                        .padding(top = 48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            modifier = Modifier
                                .then(avatarSharedElementModifier)
                                .size(140.dp)
                                .skeleton(CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (!profilePhoto.isNullOrBlank()) {
                                AsyncImage(
                                    model = avatarRequest ?: profilePhoto,
                                    contentDescription = personName,
                                    modifier = Modifier.matchParentSize(),
                                    contentScale = ContentScale.Crop,
                                )
                            } else {
                                Text(
                                    text = personName.firstOrNull()?.uppercase() ?: "?",
                                    style = MaterialTheme.typography.displayMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = personName,
                            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        SkeletonLine(
                            widthFraction = 0.58f,
                            height = 14.dp,
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        SkeletonLine(
                            widthFraction = 0.42f,
                            height = 14.dp,
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        SkeletonLine(
                            widthFraction = 0.34f,
                            height = 14.dp,
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        listOf(1.0f, 0.96f, 0.92f, 0.98f, 0.88f, 0.94f, 0.82f, 0.74f).forEachIndexed { index, widthFraction ->
                            SkeletonLine(
                                widthFraction = widthFraction,
                                height = 16.dp,
                            )
                            if (index != 7) {
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .width(120.dp)
                                .height(18.dp)
                                .skeleton(RoundedCornerShape(4.dp)),
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    SkeletonPosterRow(
                        width = skeletonPosterWidth,
                        height = skeletonPosterHeight,
                        cornerRadius = posterCardStyle.cornerRadiusDp.dp,
                        horizontalPadding = 20.dp,
                        showLabels = !isLandscapeShelfMode && !posterCardStyle.hideLabelsEnabled,
                    )

                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }
}

@Composable
private fun WidePersonDetailSkeleton(
    personName: String,
    profilePhoto: String?,
    avatarRequest: ImageRequest?,
    avatarSharedElementModifier: Modifier,
    skeletonPosterWidth: Dp,
    skeletonPosterHeight: Dp,
    skeletonPosterCornerRadius: Dp,
    showPosterLabels: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(top = 34.dp),
    ) {
        Column(
            modifier = Modifier
                .width(PERSON_DETAIL_WIDE_SIDEBAR_WIDTH)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .padding(start = 40.dp, end = 36.dp, top = 40.dp, bottom = 42.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            Box(
                modifier = Modifier.size(162.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                )
                Box(
                    modifier = Modifier
                        .then(avatarSharedElementModifier)
                        .size(148.dp)
                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.40f), CircleShape)
                        .skeleton(CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    if (!profilePhoto.isNullOrBlank()) {
                        AsyncImage(
                            model = avatarRequest ?: profilePhoto,
                            contentDescription = personName,
                            modifier = Modifier.matchParentSize(),
                            contentScale = ContentScale.Crop,
                        )
                    } else {
                        Text(
                            text = personName.initials(),
                            style = MaterialTheme.typography.displayMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Text(
                text = personName,
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.5).sp,
                    lineHeight = 34.sp,
                ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )

            Column(verticalArrangement = Arrangement.spacedBy(15.dp)) {
                repeat(3) {
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        SkeletonLine(widthFraction = 0.34f, height = 10.dp)
                        SkeletonLine(widthFraction = if (it == 1) 0.88f else 0.58f, height = 14.dp)
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.30f)),
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SkeletonLine(
                    widthFraction = 0.32f,
                    height = 10.dp,
                )
                listOf(0.96f, 1f, 0.92f, 0.98f, 0.84f, 0.90f).forEach { widthFraction ->
                    SkeletonLine(widthFraction = widthFraction, height = 16.dp)
                }
            }
        }

        Box(
            modifier = Modifier
                .width(1.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.30f)),
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .padding(start = 40.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(34.dp),
        ) {
            repeat(3) {
                WideSkeletonPosterRail(
                    skeletonPosterWidth = skeletonPosterWidth,
                    skeletonPosterHeight = skeletonPosterHeight,
                    skeletonPosterCornerRadius = skeletonPosterCornerRadius,
                    showPosterLabels = showPosterLabels,
                )
            }
        }
    }
}

@Composable
private fun WideSkeletonPosterRail(
    skeletonPosterWidth: Dp,
    skeletonPosterHeight: Dp,
    skeletonPosterCornerRadius: Dp,
    showPosterLabels: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(15.dp)) {
        Box(
            modifier = Modifier
                .width(120.dp)
                .height(18.dp)
                .skeleton(RoundedCornerShape(4.dp)),
        )

        SkeletonPosterRow(
            width = skeletonPosterWidth,
            height = skeletonPosterHeight,
            cornerRadius = skeletonPosterCornerRadius,
            showLabels = showPosterLabels,
        )
    }
}

@Composable
private fun SkeletonLine(
    widthFraction: Float,
    height: Dp,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth(widthFraction)
            .height(height)
            .skeleton(RoundedCornerShape(4.dp)),
    )
}

@Composable
private fun PersonDetailError(
    message: String,
    onRetry: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(if (isDesktop) Modifier else Modifier.background(MaterialTheme.colorScheme.background)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(Res.string.person_something_wrong),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) {
                Text(stringResource(Res.string.action_retry))
            }
        }
    }
}

// ─── Utility ───

private fun String.initials(): String {
    val parts = trim()
        .split(" ")
        .filter { it.isNotBlank() }
    return parts
        .take(2)
        .mapNotNull { it.firstOrNull()?.uppercase() }
        .joinToString("")
        .ifBlank { firstOrNull()?.uppercase() ?: "?" }
}

private fun buildCreditSummary(credits: List<MetaPreview>): String {
    if (credits.isEmpty()) return ""
    val firstYear = credits
        .mapNotNull { it.rawReleaseDate?.take(4)?.toIntOrNull() }
        .minOrNull()
    return buildString {
        append(credits.size)
        append(if (credits.size == 1) " title" else " titles")
        if (firstYear != null) {
            append(" · since ")
            append(firstYear)
        }
    }
}

private fun personBirthLine(birthday: String, deathday: String?): String {
    val birthdayDisplay = formatDateForDisplay(birthday) ?: birthday
    val deathDisplay = deathday?.let { formatDateForDisplay(it) ?: it }
    val age = calculateAge(birthday, deathday)
    return buildString {
        append(birthdayDisplay)
        if (deathDisplay != null) {
            append(" · died ")
            append(deathDisplay)
        }
        if (age != null) {
            append(" · ")
            append(age)
            append(" years")
        }
    }
}

private fun calculateAge(birthday: String, deathday: String?): Int? {
    val birthParts = birthday.split("-").mapNotNull { it.toIntOrNull() }
    if (birthParts.size < 3) return null
    val birthYear = birthParts[0]
    val birthMonth = birthParts[1]
    val birthDay = birthParts[2]

    val endParts = deathday?.split("-")?.mapNotNull { it.toIntOrNull() }
    // Use a rough current date approximation for KMP compatibility
    val endYear: Int
    val endMonth: Int
    val endDay: Int
    if (endParts != null && endParts.size >= 3) {
        endYear = endParts[0]
        endMonth = endParts[1]
        endDay = endParts[2]
    } else {
        // Approximate current date — this is good enough for age display
        endYear = 2026
        endMonth = 4
        endDay = 3
    }

    var age = endYear - birthYear
    if (endMonth < birthMonth || (endMonth == birthMonth && endDay < birthDay)) {
        age--
    }
    return age.takeIf { it >= 0 }
}

private fun formatDateForDisplay(date: String): String? {
    val parts = date.split("-").mapNotNull { it.toIntOrNull() }
    if (parts.size < 3) return null
    val month = parts[1]
    val day = parts[2]
    val year = parts[0]
    return if (month in 1..12) {
        "${localizedShortMonthName(month)} $day, $year"
    } else {
        null
    }
}


/** Backdrop art for the person page: the banner of their most popular credit. */
internal fun personBackdropArt(person: PersonDetail): String? =
    (person.movieCredits + person.tvCredits)
        .filter { !it.banner.isNullOrBlank() }
        .maxByOrNull { it.popularity ?: -1.0 }
        ?.banner

/** Desktop identity block: department eyebrow, large name, fact pills and the Favorite placeholder. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PersonRedesignIdentity(person: PersonDetail, creditSummary: String, nameSize: androidx.compose.ui.unit.TextUnit = 34.sp) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            person.knownFor?.takeIf { it.isNotBlank() }?.let { department ->
                Text(
                    department.uppercase(),
                    style = redesignText(12.sp, FontWeight.SemiBold, RedesignColors.textSecondary, letterSpacing = 0.08.em),
                )
            }
            Text(
                person.name,
                style = redesignText(nameSize, FontWeight.ExtraBold, lineHeight = nameSize * 1.12f, letterSpacing = RedesignTightTracking),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
        val pills = listOfNotNull(
            person.birthday?.let { personBirthLine(birthday = it, deathday = person.deathday) },
            person.placeOfBirth?.takeIf { it.isNotBlank() },
            creditSummary.takeIf { it.isNotBlank() },
        )
        if (pills.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                pills.forEach { pill ->
                    Text(
                        pill,
                        style = redesignText(12.sp, FontWeight.Medium, RedesignColors.textSecondary),
                        modifier = Modifier.clip(RoundedCornerShape(999.dp)).background(RedesignColors.fill)
                            .border(1.dp, RedesignColors.hairline, RoundedCornerShape(999.dp))
                            .padding(horizontal = 11.dp, vertical = 6.dp),
                    )
                }
            }
        }
        // Placeholder: favouriting people isn't built yet, so the button intentionally does nothing.
        Row(
            Modifier.clip(RoundedCornerShape(999.dp)).background(RedesignColors.fillStrong)
                .border(1.dp, RedesignColors.hairline, RoundedCornerShape(999.dp))
                .clickable(role = Role.Button) {}
                .testTag("person_favorite")
                .padding(horizontal = 16.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.FavoriteBorder, contentDescription = null, tint = RedesignColors.textPrimary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(7.dp))
            Text(stringResource(Res.string.person_favorite), style = redesignText(14.sp, FontWeight.SemiBold))
        }
    }
}



@Composable
private fun PersonAvatarImage(person: PersonDetail, fallbackProfilePhoto: String?, avatarTransitionKey: String, modifier: Modifier) {
    val avatarUrl = person.profilePhoto?.takeIf { it.isNotBlank() } ?: fallbackProfilePhoto
    val platformContext = LocalPlatformContext.current
    val request = avatarUrl?.takeIf { it.isNotBlank() }?.let { url ->
        remember(platformContext, url, avatarTransitionKey) {
            ImageRequest.Builder(platformContext).data(url).memoryCacheKey(avatarTransitionKey)
                .placeholderMemoryCacheKey(avatarTransitionKey).diskCacheKey(url).build()
        }
    }
    Box(
        modifier.clip(CircleShape).background(Color(0xFF24262B)).border(2.dp, Color.White.copy(alpha = 0.16f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (request != null) {
            AsyncImage(model = request, contentDescription = person.name, modifier = Modifier.matchParentSize(), contentScale = ContentScale.Crop)
        } else {
            Text(person.name.initials(), style = redesignText(28.sp, FontWeight.Bold, RedesignColors.textSecondary))
        }
    }
}

/** Desktop redesign header: avatar, eyebrow, name, fact pills, Favorite, then the bio. Scrolls with the page. */
@Composable
@OptIn(ExperimentalSharedTransitionApi::class)
private fun PersonRedesignHeader(
    person: PersonDetail,
    fallbackProfilePhoto: String?,
    avatarTransitionKey: String,
    sharedTransitionScope: SharedTransitionScope?,
    animatedVisibilityScope: AnimatedVisibilityScope?,
    modifier: Modifier = Modifier,
) {
    val credits = remember(person.movieCredits, person.tvCredits) { (person.movieCredits + person.tvCredits).distinctBy { it.id } }
    val creditSummary = remember(credits) { buildCreditSummary(credits) }
    Column(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PersonAvatarImage(
                person, fallbackProfilePhoto, avatarTransitionKey,
                // No shared-element flight here: the avatar moves with the content layer, like hero text.
                Modifier.size(150.dp),
            )
            Spacer(Modifier.width(28.dp))
            Box(Modifier.weight(1f)) { PersonRedesignIdentity(person, creditSummary, nameSize = 40.sp) }
        }
        person.biography?.takeIf { it.isNotBlank() }?.let { biography ->
            Spacer(Modifier.height(20.dp))
            Box(Modifier.widthIn(max = 860.dp)) {
                ExpandableDescription(
                    text = biography,
                    style = redesignText(15.sp, color = RedesignColors.textSecondary, lineHeight = 23.sp),
                    color = RedesignColors.textSecondary,
                    collapsedMaxLines = 4,
                )
            }
        }
    }
}

/** Compact identity above a "View all" grid on desktop. */
@Composable
private fun PersonCompactHeader(person: PersonDetail, fallbackProfilePhoto: String?, avatarTransitionKey: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(bottom = 18.dp), verticalAlignment = Alignment.CenterVertically) {
        PersonAvatarImage(person, fallbackProfilePhoto, avatarTransitionKey, Modifier.size(56.dp))
        Spacer(Modifier.width(14.dp))
        Column {
            person.knownFor?.takeIf { it.isNotBlank() }?.let {
                Text(it.uppercase(), style = redesignText(11.sp, FontWeight.SemiBold, RedesignColors.textSecondary, letterSpacing = 0.08.em))
            }
            Text(person.name, style = redesignText(22.sp, FontWeight.Bold, letterSpacing = RedesignTightTracking), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}


@Composable
private fun DesktopPersonDetailSkeleton(
    personName: String,
    avatarRequest: Any?,
    avatarSharedElementModifier: Modifier,
    skeletonPosterWidth: Dp,
    skeletonPosterHeight: Dp,
    skeletonPosterCornerRadius: Dp,
    showPosterLabels: Boolean,
) {
    Column(
        Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars)
            .padding(top = PERSON_REDESIGN_TOP_INSET),
    ) {
        Row(Modifier.padding(horizontal = 40.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                avatarSharedElementModifier.size(150.dp).clip(CircleShape).skeleton(CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (avatarRequest != null) {
                    AsyncImage(model = avatarRequest, contentDescription = personName, modifier = Modifier.matchParentSize(), contentScale = ContentScale.Crop)
                }
            }
            Spacer(Modifier.width(28.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.width(90.dp).height(12.dp).skeleton())
                Text(personName, style = redesignText(40.sp, FontWeight.ExtraBold, letterSpacing = RedesignTightTracking), maxLines = 2)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(130.dp, 150.dp, 120.dp).forEach { Box(Modifier.width(it).height(26.dp).skeleton(RoundedCornerShape(999.dp))) }
                }
                Box(Modifier.width(110.dp).height(36.dp).skeleton(RoundedCornerShape(999.dp)))
            }
        }
        Column(Modifier.padding(horizontal = 40.dp).padding(top = 22.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(1f, 0.94f, 0.6f).forEach { Box(Modifier.widthIn(max = 860.dp).fillMaxWidth(it).height(14.dp).skeleton()) }
        }
        repeat(2) {
            Box(Modifier.padding(start = 40.dp, top = 36.dp, bottom = 12.dp).width(140.dp).height(18.dp).skeleton())
            SkeletonPosterRow(
                width = skeletonPosterWidth,
                height = skeletonPosterHeight,
                cornerRadius = skeletonPosterCornerRadius,
                showLabels = showPosterLabels,
                horizontalPadding = 40.dp,
            )
        }
    }
}


/** TEMPORARY review control: picks the style used the next time a person page opens or closes. */
@Composable
private fun NavTransitionStyleToggle(modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(999.dp)
    Row(
        modifier.clip(shape).background(Color.Black.copy(alpha = 0.45f)).border(1.dp, RedesignColors.hairline, shape).padding(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        listOf(NavTransitionStyle.HeroParallax to "Hero carousel", NavTransitionStyle.FullSlide to "Full slide").forEach { (style, label) ->
            val selected = NavTransitionPreview.style == style
            Text(
                label,
                style = redesignText(12.sp, FontWeight.SemiBold, if (selected) Color(0xFF0E0F12) else RedesignColors.textSecondary),
                modifier = Modifier.clip(shape).background(if (selected) RedesignColors.textPrimary else Color.Transparent)
                    .clickable { NavTransitionPreview.style = style }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
}


/** Desktop stand-in for the Known For row while it loads: title bar plus a poster row of the same size. */
@Composable
private fun KnownForPlaceholder(horizontalPadding: Dp) {
    val style = rememberPosterCardStyleUiState()
    val landscape = style.catalogLandscapeModeEnabled
    val width = if (landscape) landscapePosterWidth(style.widthDp) else style.widthDp.dp
    val height = if (landscape) landscapePosterHeightForWidth(width) else style.heightDp.dp
    Column {
        Box(Modifier.padding(start = horizontalPadding, bottom = 12.dp).width(120.dp).height(20.dp).skeleton())
        SkeletonPosterRow(
            width = width,
            height = height,
            cornerRadius = style.cornerRadiusDp.dp,
            showLabels = !landscape && !style.hideLabelsEnabled,
            horizontalPadding = horizontalPadding,
        )
    }
}
