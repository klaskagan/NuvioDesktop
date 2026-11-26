package com.nuvio.app.features.home.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.ui.unit.lerp
import nuvio.composeapp.generated.resources.detail_board_collapse_trailer
import nuvio.composeapp.generated.resources.detail_board_expand_trailer
import nuvio.composeapp.generated.resources.detail_mute_trailer
import nuvio.composeapp.generated.resources.detail_unmute_trailer
import nuvio.composeapp.generated.resources.home_poster_trailer_unavailable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import com.nuvio.app.features.search.SearchPreferencesStorage
import com.nuvio.app.features.search.SearchStreamingCountry
import com.nuvio.app.features.tmdb.TmdbMetadataService
import androidx.compose.animation.core.Animatable
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.ContentPlayAction
import com.nuvio.app.MediaKeysHandler
import com.nuvio.app.OverlayEscapeHandler
import com.nuvio.app.core.build.AppFeaturePolicy
import com.nuvio.app.core.build.TrailerPlaybackMode
import com.nuvio.app.core.format.formatReleaseDateForDisplay
import com.nuvio.app.core.ui.NuvioAsyncImage
import com.nuvio.app.core.ui.NuvioDesktopAnimation
import com.nuvio.app.core.ui.NuvioTokens
import com.nuvio.app.core.ui.PosterZoomOverlayPortal
import com.nuvio.app.core.ui.RedesignColors
import com.nuvio.app.core.ui.RedesignIconButton
import com.nuvio.app.core.ui.displayRatingText
import com.nuvio.app.core.ui.redesignText
import com.nuvio.app.core.ui.rememberPosterCardStyleUiState
import com.nuvio.app.features.details.MetaDetails
import com.nuvio.app.features.details.SeriesPrimaryAction
import com.nuvio.app.features.details.components.BoardPlayButton
import com.nuvio.app.features.details.components.RatingsRow
import com.nuvio.app.features.details.components.rememberRatingState
import com.nuvio.app.features.details.seriesPrimaryAction
import com.nuvio.app.features.home.HomeHeroMetaStore
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.home.stableKey
import com.nuvio.app.features.mdblist.MdbListSettingsRepository
import com.nuvio.app.features.tracking.buildTrackingMediaReference
import com.nuvio.app.features.trailer.TrailerPlaybackSource
import com.nuvio.app.features.watched.WatchedRepository
import com.nuvio.app.features.watching.application.WatchingState
import com.nuvio.app.features.watchprogress.ContinueWatchingPreferencesRepository
import com.nuvio.app.features.watchprogress.CurrentDateProvider
import com.nuvio.app.features.watchprogress.WatchProgressRepository
import dev.chrisbanes.haze.HazeInputScale
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.action_play
import nuvio.composeapp.generated.resources.action_resume
import nuvio.composeapp.generated.resources.detail_board_episode_code
import nuvio.composeapp.generated.resources.detail_board_minutes_left
import nuvio.composeapp.generated.resources.hero_add_to_library
import nuvio.composeapp.generated.resources.hero_mark_unwatched
import nuvio.composeapp.generated.resources.hero_mark_watched
import nuvio.composeapp.generated.resources.hero_remove_from_library
import nuvio.composeapp.generated.resources.home_quick_look_mute
import nuvio.composeapp.generated.resources.home_quick_look_next_episode
import nuvio.composeapp.generated.resources.home_quick_look_rating_on
import nuvio.composeapp.generated.resources.home_quick_look_runtime_minutes
import nuvio.composeapp.generated.resources.home_quick_look_seasons
import nuvio.composeapp.generated.resources.home_quick_look_unmute
import nuvio.composeapp.generated.resources.home_quick_look_watch_on
import nuvio.composeapp.generated.resources.home_quick_look_your_rating
import nuvio.composeapp.generated.resources.home_view_details
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** What Quick look shows: a poster, the row it sits in (for ← →) and how that poster opens Details. */
internal class QuickLookTarget(
    val item: MetaPreview,
    val row: List<MetaPreview>,
    val onOpenDetails: (() -> Unit)?,
)

/** The items of the row a poster sits in, so Quick look's ← → can walk it. */
internal val LocalPosterQuickLookRow = staticCompositionLocalOf<List<MetaPreview>?> { null }

/**
 * "Poster hover · Quick look": the hovered poster (for Space), the open Quick look and the poster
 * trailer the strip's Play trailer opened. One host ([PosterQuickLookHost]) draws them.
 */
internal object PosterQuickLook {
    var hovered by mutableStateOf<QuickLookTarget?>(null)
        private set
    var open by mutableStateOf<QuickLookTarget?>(null)
        private set

    /**
     * Quick look's trailer grown in place into a large 16:9 tile (the Details board's expanded
     * trailer, for a poster). The same player keeps playing through expand and collapse.
     */
    var expanded by mutableStateOf(false)
        private set

    /** Shared by Quick look and its expanded trailer; M toggles it. Quick look plays with sound. */
    var muted by mutableStateOf(false)

    fun hover(target: QuickLookTarget) {
        hovered = target
    }

    fun unhover(target: QuickLookTarget) {
        if (hovered === target) hovered = null
    }

    fun open(target: QuickLookTarget) {
        if (open == null) muted = false
        expanded = false
        open = target
    }

    /** Play trailer on a poster: Quick look opens with its trailer already expanded. */
    fun playTrailer(target: QuickLookTarget) {
        if (open == null) muted = false
        open = target
        expanded = true
    }

    fun expand() {
        if (open != null) expanded = true
    }

    fun collapse() {
        expanded = false
    }

    /** Esc steps back one layer: the expanded trailer to Quick look, then Quick look closes. */
    fun back() {
        if (expanded) collapse() else close()
    }

    fun close() {
        open = null
        expanded = false
    }

    /** ← → : the neighbouring poster of the row, opened by its id (it has no click of its own here). */
    fun move(delta: Int) {
        val current = open ?: return
        if (expanded) return
        val index = current.row.indexOfFirst { it.stableKey() == current.item.stableKey() }
        if (index < 0) return
        val next = current.row.getOrNull(index + delta) ?: return
        open = QuickLookTarget(next, current.row, onOpenDetails = null)
    }
}

private val PanelBackground = Color(0xFF151618)
private val PanelWidth = 820.dp
private val PanelTop = 104.dp
private val TrailerHeight = 330.dp
private val SideColumnWidth = 250.dp
private const val QuickLookEnterMillis = 260
/** Home's board timing, so expanding here feels like expanding the Details trailer. */
private const val ExpandMillis = 420
private const val QuickLookExitMillis = 200

/**
 * Draws Quick look over the shell (dimmed, softly blurred Home) and the strip's trailer popup.
 * [onPlay] starts playback (absent: no Play button); [openMeta] opens Details for a poster that
 * Quick look reached with ← → .
 */
@Composable
internal fun PosterQuickLookHost(onPlay: ContentPlayAction?, openMeta: (MetaPreview) -> Unit) {

    val open = PosterQuickLook.open
    var shown by remember { mutableStateOf<QuickLookTarget?>(null) }
    val progress = remember { Animatable(0f) }
    LaunchedEffect(open) {
        if (open != null) {
            shown = open
            progress.animateTo(1f, tween(QuickLookEnterMillis, easing = NuvioTokens.Motion.decelerate))
        } else if (shown != null) {
            progress.animateTo(0f, tween(QuickLookExitMillis, easing = NuvioTokens.Motion.standard))
            shown = null
        }
    }
    val target = shown ?: return
    // Esc and Space step back one layer: the expanded trailer to Quick look, then Quick look closes.
    OverlayEscapeHandler(enabled = open != null) { PosterQuickLook.back() }
    MediaKeysHandler(enabled = open != null) { key ->
        when (key) {
            Key.DirectionLeft -> { PosterQuickLook.move(-1); true }
            Key.DirectionRight -> { PosterQuickLook.move(1); true }
            Key.Spacebar -> { PosterQuickLook.back(); true }
            Key.M -> { PosterQuickLook.muted = !PosterQuickLook.muted; true }
            else -> false
        }
    }
    val openDetails: (QuickLookTarget) -> Unit = { t ->
        PosterQuickLook.close()
        t.onOpenDetails?.invoke() ?: openMeta(t.item)
    }
    PosterZoomOverlayPortal { haze ->
        QuickLookLayer(
            target = open ?: target,
            progress = progress.value,
            haze = haze,
            onPlay = onPlay?.let { play -> { args: QuickLookPlay -> PosterQuickLook.close(); args.invoke(play) } },
            onOpenDetails = openDetails,
        )
    }
}

@Composable
private fun QuickLookLayer(
    target: QuickLookTarget,
    progress: Float,
    haze: HazeState,
    onPlay: ((QuickLookPlay) -> Unit)?,
    onOpenDetails: (QuickLookTarget) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize().testTag("poster_quick_look")) {
        Box(
            Modifier.fillMaxSize().hazeEffect(state = haze) {
                blurRadius = 10.dp
                inputScale = HazeInputScale.Auto
                noiseFactor = 0f
                alpha = progress
            },
        )
        Box(
            Modifier.fillMaxSize()
                .background(Color.Black.copy(alpha = 0.6f * progress))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { PosterQuickLook.close() },
        )
        // Expanded, the panel becomes a large 16:9 trailer tile centred in the window; the same
        // player keeps playing while it grows and shrinks (Details board's expanded trailer).
        val expanded = PosterQuickLook.expanded
        val expandedWidth = min(maxWidth - 96.dp, (maxHeight - 120.dp) * 16f / 9f)
        val width by animateDpAsState(
            if (expanded) expandedWidth else min(PanelWidth, maxWidth - 48.dp),
            tween(ExpandMillis, easing = FastOutSlowInEasing),
            label = "quick_look_width",
        )
        val top by animateDpAsState(
            if (expanded) (maxHeight - expandedWidth * 9f / 16f) / 2 else min(PanelTop, (maxHeight * 0.12f)),
            tween(ExpandMillis, easing = FastOutSlowInEasing),
            label = "quick_look_top",
        )
        val shape = RoundedCornerShape(20.dp)
        Box(
            Modifier.align(Alignment.TopCenter).padding(top = top, bottom = 24.dp).width(width)
                .heightIn(max = maxHeight - top - 24.dp)
                .graphicsLayer {
                    alpha = progress
                    val scale = 0.96f + 0.04f * progress
                    scaleX = scale
                    scaleY = scale
                    translationY = (1f - progress) * 18.dp.toPx()
                }
                .clip(shape)
                .background(PanelBackground)
                .border(1.dp, RedesignColors.hairline, shape)
                // The panel swallows clicks so only the scrim closes Quick look.
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .testTag("poster_quick_look_panel"),
        ) {
            AnimatedContent(
                targetState = target,
                contentKey = { it.item.stableKey() },
                transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(160)) },
                label = "quick_look_item",
            ) { shownTarget ->
                QuickLookPanel(shownTarget, expanded, onPlay, onOpenDetails)
            }
        }
    }
}

/** A resolved Play / Resume press, applied to the shell's [ContentPlayAction]. */
private fun interface QuickLookPlay {
    fun invoke(play: ContentPlayAction)
}

@Composable
private fun QuickLookPanel(
    target: QuickLookTarget,
    expanded: Boolean,
    onPlay: ((QuickLookPlay) -> Unit)?,
    onOpenDetails: (QuickLookTarget) -> Unit,
) {
    val item = target.item
    val store = remember(item.stableKey()) { HomeHeroMetaStore() }
    val mdbList by remember {
        MdbListSettingsRepository.ensureLoaded()
        MdbListSettingsRepository.uiState
    }.collectAsStateWithLifecycle()
    LaunchedEffect(store, mdbList.isActive) {
        store.load(listOf(item), mdbList.takeIf { it.isActive })
    }
    val metas by store.metas.collectAsStateWithLifecycle()
    val heroMeta = metas[item.stableKey()]
    val details = heroMeta?.details
    val ratings = heroMeta?.ratings.orEmpty()

    val watched by remember {
        WatchedRepository.ensureLoaded()
        WatchedRepository.uiState
    }.collectAsStateWithLifecycle()
    val progress by remember {
        WatchProgressRepository.ensureLoaded()
        WatchProgressRepository.uiState
    }.collectAsStateWithLifecycle()
    val cwPrefs by ContinueWatchingPreferencesRepository.uiState.collectAsStateWithLifecycle()
    val isWatched = WatchingState.isPosterWatched(watchedKeys = watched.watchedKeys, item = item)
    val isSeries = item.type.lowercase() in SeriesTypes || details?.videos?.any { (it.episode ?: 0) > 0 } == true
    val seriesAction: SeriesPrimaryAction? = remember(details, progress.entries, watched.items, watched.watchedKeys, cwPrefs.upNextFromFurthestEpisode) {
        details?.takeIf { isSeries }?.seriesPrimaryAction(
            entries = progress.entries,
            watchedItems = watched.items,
            todayIsoDate = CurrentDateProvider.todayIsoDate(),
            preferFurthestEpisode = cwPrefs.upNextFromFurthestEpisode,
            watchedKeys = watched.watchedKeys,
            allowRewatch = true,
        )
    }
    val movieProgress = remember(progress.entries, item.id) {
        progress.byVideoIdForContent(item.id)[item.id]?.takeUnless { it.isCompleted }
    }

    val expandProgress by animateFloatAsState(
        if (expanded) 1f else 0f,
        tween(ExpandMillis, easing = FastOutSlowInEasing),
        label = "quick_look_expand",
    )
    Box(Modifier.fillMaxWidth()) {
    // The body sits on the title's own artwork, blurred and dimmed: the same image the trailer area
    // is loading (and the row's poster until it has), so it costs no extra load and no wait.
    Box(Modifier.matchParentSize()) {
        HoverBackdrop(item, details, Modifier.fillMaxSize().blur(BodyBlur))
        Box(Modifier.fillMaxSize().background(PanelBackground.copy(alpha = BodyScrimAlpha)))
    }
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        QuickLookTrailer(item, details, expanded, expandProgress, onTitleClick = { onOpenDetails(target) })
        // The info folds away under the growing trailer and unfolds as it shrinks back.
        AnimatedVisibility(
            visible = !expanded,
            enter = fadeIn(tween(240, delayMillis = 120)) + expandVertically(tween(ExpandMillis, easing = FastOutSlowInEasing)),
            exit = fadeOut(tween(160)) + shrinkVertically(tween(ExpandMillis, easing = FastOutSlowInEasing)),
        ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 24.dp, top = 14.dp, end = 24.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            // Nothing in Quick look moves while it loads: every part that arrives later has a fixed
            // slot from the first frame (a skeleton until then), and its content fades in there.
            Column(Modifier.weight(1f)) {
                QuickLookMetaLine(item, details)
                QuickLookRatings(item, details, ratings, mdbList.isActive)
                val overview = (item.description?.takeIf { it.isNotBlank() } ?: details?.description)?.takeIf { it.isNotBlank() }
                SlotCrossfade(ready = overview != null || details != null, modifier = Modifier.fillMaxWidth().height(OverviewHeight)) { ready ->
                    if (ready) {
                        Text(
                            overview.orEmpty(),
                            style = redesignText(14.sp, FontWeight.Normal, Color(0xFFDFE2E5)).copy(lineHeight = 21.sp),
                            maxLines = OverviewLines,
                            overflow = TextOverflow.Ellipsis,
                        )
                    } else {
                        Column(Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                            SkeletonLine(0.95f)
                            SkeletonLine(0.88f)
                            SkeletonLine(0.6f)
                        }
                    }
                }
                Spacer(Modifier.height(18.dp))
                QuickLookActions(
                    target = target,
                    details = details,
                    isSeries = isSeries,
                    seriesAction = seriesAction,
                    movieResumeMs = movieProgress?.lastPositionMs,
                    isWatched = isWatched,
                    onPlay = onPlay,
                    onOpenDetails = onOpenDetails,
                )
            }
            QuickLookSide(item, details, seriesAction, progress)
        }
        }
    }
    }
}

private val BodyBlur = 48.dp
private const val BodyScrimAlpha = 0.55f

private val SeriesTypes = setOf("series", "tv", "show", "tvshow")

/**
 * Quick look's trailer, with the Details board's controls: mute and Fullscreen on a dark plate in
 * the corner. Fullscreen grows this same player in place into the expanded tile (playback carries
 * on); Exit fullscreen, Esc or Space bring Quick look back. When Preview Trailers is off nothing
 * plays by itself, and Fullscreen starts the trailer: expanding is a request to watch it.
 */
@Composable
private fun QuickLookTrailer(
    item: MetaPreview,
    details: MetaDetails?,
    expanded: Boolean,
    expandProgress: Float,
    onTitleClick: () -> Unit,
) {
    val style = rememberPosterCardStyleUiState()
    val inApp = AppFeaturePolicy.trailerPlaybackMode == TrailerPlaybackMode.IN_APP
    var started by remember(item.stableKey()) { mutableStateOf(false) }
    if (inApp && (style.hoverPreviewTrailerEnabled || expanded)) started = true
    var source by remember(item.stableKey()) { mutableStateOf<TrailerPlaybackSource?>(null) }
    var resolved by remember(item.stableKey()) { mutableStateOf(false) }
    LaunchedEffect(item.stableKey(), started) {
        if (!started || resolved) return@LaunchedEffect
        source = resolveHomePosterHoverTrailerPlaybackSource(item)
        resolved = true
    }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val height = lerp(TrailerHeight, maxWidth * 9f / 16f, expandProgress)
        // Clicking the picture toggles Fullscreen, as on a video; the plate and the title take their own clicks.
        Box(
            Modifier.fillMaxWidth().height(height)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    enabled = inApp,
                ) { if (expanded) PosterQuickLook.collapse() else PosterQuickLook.expand() }
                .testTag("poster_quick_look_trailer"),
        ) {
            // The picture fades out over its lower part into the panel's blurred artwork (which runs
            // under the whole panel), so there is no seam between the trailer and the info. The fade
            // goes as the trailer expands and the info folds away.
            Box(
                Modifier.matchParentSize()
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .drawWithContent {
                        drawContent()
                        val fade = 1f - expandProgress
                        if (fade > 0f) {
                            drawRect(
                                Brush.verticalGradient(0.5f to Color.Black, 1f to Color.Black.copy(alpha = 1f - fade)),
                                blendMode = BlendMode.DstIn,
                            )
                        }
                    },
            ) {
                HoverBackdrop(item, details, Modifier.fillMaxSize())
                if (started) {
                    HomePosterHoverTrailer(
                        playbackSource = source,
                        soundEnabled = !PosterQuickLook.muted,
                        // Played on request (expanded) it starts from the top; by itself, at the set start.
                        startPositionSeconds = if (style.hoverPreviewTrailerEnabled) style.hoverPreviewTrailerStartSeconds else 0,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            if (expanded && resolved && source == null) {
                Text(
                    stringResource(Res.string.home_poster_trailer_unavailable),
                    modifier = Modifier.align(Alignment.Center)
                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    style = redesignText(15.sp, FontWeight.SemiBold),
                )
            }
            if (inApp) {
                Row(
                    // The Details board's plate: it keeps the frosted buttons readable over a bright frame.
                    Modifier.align(Alignment.TopEnd).padding(12.dp)
                        .clip(CircleShape).background(Color.Black.copy(alpha = 0.34f)).padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (started) {
                        RedesignIconButton(
                            contentDescription = stringResource(if (PosterQuickLook.muted) Res.string.detail_unmute_trailer else Res.string.detail_mute_trailer),
                            onClick = { PosterQuickLook.muted = !PosterQuickLook.muted },
                            vector = if (PosterQuickLook.muted) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp,
                            size = 40.dp,
                            modifier = Modifier.testTag("poster_quick_look_mute"),
                        )
                    }
                    RedesignIconButton(
                        contentDescription = stringResource(if (expanded) Res.string.detail_board_collapse_trailer else Res.string.detail_board_expand_trailer),
                        onClick = { if (expanded) PosterQuickLook.collapse() else PosterQuickLook.expand() },
                        vector = if (expanded) Icons.Rounded.FullscreenExit else Icons.Rounded.Fullscreen,
                        size = 40.dp,
                        modifier = Modifier.testTag("poster_quick_look_expand"),
                    )
                }
            }
            // The title is decided while Quick look opens: the row's logo, or the details' logo if it
            // arrives during the opening animation; after that it stays as it is (no text → logo swap).
            var logoFailed by remember(item.stableKey()) { mutableStateOf(false) }
            var logoChoice by remember(item.stableKey()) { mutableStateOf(item.logo?.takeIf { it.isNotBlank() }) }
            var logoLocked by remember(item.stableKey()) { mutableStateOf(logoChoice != null) }
            LaunchedEffect(item.stableKey()) {
                kotlinx.coroutines.delay(QuickLookEnterMillis.toLong())
                logoLocked = true
            }
            if (!logoLocked && logoChoice == null) details?.logo?.takeIf { it.isNotBlank() }?.let { logoChoice = it }
            val logo = logoChoice?.takeIf { !logoFailed }
            // The title is the link to Details (hand cursor, a slight lift on hover); it fades with
            // the info while expanded and takes no clicks then.
            val titleInteraction = remember { MutableInteractionSource() }
            val titleHovered by titleInteraction.collectIsHoveredAsState()
            val titleScale by animateFloatAsState(if (titleHovered && !expanded) 1.03f else 1f, tween(160), label = "quick_look_title")
            Box(
                Modifier.align(Alignment.BottomStart).padding(start = 24.dp, end = 24.dp, bottom = 28.dp)
                    .graphicsLayer {
                        alpha = 1f - expandProgress
                        scaleX = titleScale
                        scaleY = titleScale
                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 1f)
                    }
                    .pointerHoverIcon(PointerIcon.Hand)
                    .hoverable(titleInteraction)
                    .clickable(interactionSource = titleInteraction, indication = null, enabled = !expanded, onClick = onTitleClick)
                    .testTag("poster_quick_look_title"),
            ) {
                if (logo != null) {
                    NuvioAsyncImage(
                        model = logo,
                        contentDescription = item.name,
                        modifier = Modifier.width(300.dp).height(72.dp),
                        alignment = Alignment.BottomStart,
                        contentScale = ContentScale.Fit,
                        onError = { logoFailed = true },
                    )
                } else {
                    Text(
                        item.name,
                        style = redesignText(44.sp, FontWeight.Bold).copy(letterSpacing = (-0.9).sp, lineHeight = 46.sp),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickLookMetaLine(item: MetaPreview, details: MetaDetails?) {
    val isSeries = item.type.lowercase() in SeriesTypes
    val seasons = details?.videos?.mapNotNull { it.season }?.filter { it > 0 }?.distinct()?.size ?: 0
    val parts = buildList {
        // One title, so the singular: "Movie", not the catalog label "Movies".
        add(if (item.type.equals("movie", ignoreCase = true)) com.nuvio.app.core.i18n.localizedMovieTypeLabel() else com.nuvio.app.core.i18n.localizedMediaTypeLabel(item.type))
        // The row's own values first: the details only fill what the item lacks, so the line is
        // never rewritten in front of the user (it read "2025- · Drama · Crime", then changed).
        (item.releaseInfo?.takeIf { it.isNotBlank() } ?: details?.releaseInfo)?.takeIf { it.isNotBlank() }?.let(::formatReleaseDateForDisplay)?.takeIf { it.isNotBlank() }?.let(::add)
        (item.genres.takeIf { it.isNotEmpty() } ?: details?.genres.orEmpty()).take(2).joinToString(" · ").takeIf { it.isNotBlank() }?.let(::add)
    }
    val seasonsText = if (isSeries && seasons > 0) pluralStringResource(Res.plurals.home_quick_look_seasons, seasons, seasons) else null
    Text(
        (parts + listOfNotNull(seasonsText)).joinToString("  ·  "),
        style = redesignText(14.sp, FontWeight.Medium, RedesignColors.textSecondary),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun QuickLookRatings(item: MetaPreview, details: MetaDetails?, ratings: List<com.nuvio.app.features.details.MetaExternalRating>, mdbListActive: Boolean) {
    // A fixed slot the badges fill (or the IMDb score, or nothing): its height never changes.
    val imdb = displayRatingText(item.imdbRating ?: details?.imdbRating)
    val ready = details != null || (mdbListActive && ratings.isNotEmpty())
    SlotCrossfade(ready = ready, modifier = Modifier.padding(top = 10.dp, bottom = 12.dp).fillMaxWidth().height(RatingsHeight)) { isReady ->
        when {
            !isReady -> Row(horizontalArrangement = Arrangement.spacedBy(15.dp)) {
                repeat(5) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(Modifier.width(52.dp).height(18.dp).clip(RoundedCornerShape(5.dp)).background(RedesignColors.fill))
                        Box(Modifier.width(30.dp).height(11.dp).clip(RoundedCornerShape(4.dp)).background(RedesignColors.fill))
                    }
                }
            }
            mdbListActive && ratings.isNotEmpty() -> RatingsRow(ratings = ratings, maxItems = 6)
            imdb != null -> Row(verticalAlignment = Alignment.CenterVertically) {
                Text("IMDb", style = redesignText(14.5.sp, FontWeight.Bold, RedesignColors.imdb))
                Spacer(Modifier.width(6.dp))
                Text(imdb, style = redesignText(14.5.sp, FontWeight.Bold))
            }
            else -> Unit
        }
    }
}

/** A fixed-size slot that shows a skeleton until [ready], then crossfades to its content. */
@Composable
private fun SlotCrossfade(ready: Boolean, modifier: Modifier, content: @Composable (Boolean) -> Unit) {
    androidx.compose.animation.Crossfade(targetState = ready, modifier = modifier, animationSpec = tween(220), label = "quick_look_slot") { isReady ->
        Box(Modifier.fillMaxSize()) { content(isReady) }
    }
}

private val RatingsHeight = 44.dp
private val PlayPillMinWidth = 184.dp
private const val OverviewLines = 3
private val OverviewHeight = 63.dp

@Composable
private fun QuickLookActions(
    target: QuickLookTarget,
    details: MetaDetails?,
    isSeries: Boolean,
    seriesAction: SeriesPrimaryAction?,
    movieResumeMs: Long?,
    isWatched: Boolean,
    onPlay: ((QuickLookPlay) -> Unit)?,
    onOpenDetails: (QuickLookTarget) -> Unit,
) {
    val item = target.item
    val playText = stringResource(Res.string.action_play)
    val resumeText = stringResource(Res.string.action_resume)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (onPlay != null) {
            val label = when {
                isSeries && seriesAction != null -> seriesAction.label
                !isSeries && movieResumeMs != null -> resumeText
                else -> playText
            }
            // A series needs its episodes to know what plays. While the details load the pill reads
            // Play and ignores clicks; only details with nothing playable make it "unavailable".
            val enabled = !isSeries || seriesAction != null || details == null
            BoardPlayButton(
                label = label,
                enabled = enabled,
                onLongClick = null,
                // Wide enough for "Resume S2 E4" or "Next Up • S2 E2", so the label arriving with
                // the details never pushes Library, Watched and View Details.
                modifier = Modifier.widthIn(min = PlayPillMinWidth).testTag("poster_quick_look_play"),
                onClick = {
                    val meta = details
                    if (isSeries && seriesAction != null && meta != null) {
                        onPlay { play ->
                            play(
                                meta.type, seriesAction.videoId, meta.id, meta.type, meta.name, meta.logo, meta.poster, meta.background,
                                seriesAction.seasonNumber, seriesAction.episodeNumber, seriesAction.episodeTitle, seriesAction.episodeThumbnail,
                                null, seriesAction.resumePositionMs,
                            )
                        }
                    } else if (!isSeries) {
                        onPlay { play ->
                            play(
                                item.type, item.id, item.id, item.type, item.name, item.logo ?: meta?.logo, item.poster, item.banner ?: meta?.background,
                                null, null, null, null, meta?.description ?: item.description, movieResumeMs,
                            )
                        }
                    }
                },
            )
        }
        WithPosterLibraryToggle(item) { isSaved, toggle ->
            RedesignIconButton(
                contentDescription = stringResource(if (isSaved) Res.string.hero_remove_from_library else Res.string.hero_add_to_library),
                onClick = toggle,
                vector = if (isSaved) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                size = 44.dp,
            )
        }
        RedesignIconButton(
            contentDescription = stringResource(if (isWatched) Res.string.hero_mark_unwatched else Res.string.hero_mark_watched),
            onClick = rememberPosterWatchedToggle(item),
            vector = if (isWatched) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
            size = 44.dp,
        )
        ViewDetailsLink { onOpenDetails(target) }
    }
}

@Composable
private fun ViewDetailsLink(onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val color = if (hovered) RedesignColors.textPrimary else RedesignColors.textSecondary
    Row(
        Modifier.padding(start = 6.dp).clip(RoundedCornerShape(8.dp)).hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 6.dp)
            .testTag("poster_quick_look_details"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(Res.string.home_view_details), style = redesignText(13.sp, FontWeight.SemiBold, color))
        Spacer(Modifier.width(4.dp))
        Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
    }
}

@Composable
private fun QuickLookSide(
    item: MetaPreview,
    details: MetaDetails?,
    seriesAction: SeriesPrimaryAction?,
    progress: com.nuvio.app.features.watchprogress.WatchProgressUiState,
) {
    val episode = seriesAction?.takeIf { it.seasonNumber != null && it.episodeNumber != null }
    // Where it streams in the user's streaming country (Search's setting, else the device's), from
    // TMDB. Only when TMDB can't say does a series fall back to the networks that aired it.
    var providers by remember(item.stableKey()) { mutableStateOf<List<String>?>(null) }
    var providersResolved by remember(item.stableKey()) { mutableStateOf(false) }
    LaunchedEffect(item.stableKey()) {
        val region = (SearchStreamingCountry.override.value ?: SearchPreferencesStorage.deviceRegion() ?: "US").uppercase()
        providers = try {
            TmdbMetadataService.streamingProviders(item.id, item.type, region)
        } catch (error: Throwable) {
            if (error is kotlinx.coroutines.CancellationException) throw error
            null
        }
        providersResolved = true
    }
    val networks = providers
        ?: details?.networks.orEmpty().map { it.name }.filter { it.isNotBlank() }.distinct().takeIf { providersResolved }.orEmpty()
    val reference = remember(item.stableKey(), details?.imdbId) {
        val base = buildTrackingMediaReference(contentType = item.type, parentMetaId = item.id, title = item.name, releaseInfo = item.releaseInfo)
        details?.imdbId?.let { base.copy(ids = base.ids.mergeMissing(com.nuvio.app.features.tracking.TrackingExternalIds(imdb = it))) } ?: base
    }
    val rating by rememberRatingState(reference)
    val userRating = rating.rating?.takeIf { rating.isAvailable }
    // The column is always there, at its width, with a fixed slot per fact: Next episode (series),
    // Watch on, then Your rating at the foot (adding it moves nothing). Slots show a skeleton
    // until their data arrives and keep their size if it turns out empty.
    val isSeries = item.type.lowercase() in SeriesTypes || details?.videos?.any { (it.episode ?: 0) > 0 } == true
    Column(Modifier.width(SideColumnWidth)) {
        if (isSeries) {
            SlotCrossfade(ready = details != null, modifier = Modifier.fillMaxWidth().height(NextEpisodeSlotHeight)) { ready ->
                if (!ready) {
                    SideSkeleton(thumbnail = true)
                } else if (episode != null) {
                    Column {
                        Eyebrow(stringResource(Res.string.home_quick_look_next_episode))
                        NextEpisodeRow(item, details, episode, progress)
                    }
                }
            }
        }
        SlotCrossfade(ready = providersResolved, modifier = Modifier.fillMaxWidth().height(WatchOnSlotHeight)) { ready ->
            if (!ready) {
                SideSkeleton(thumbnail = false)
            } else if (networks.isNotEmpty()) {
                Column {
                    Eyebrow(stringResource(Res.string.home_quick_look_watch_on))
                    Text(
                        networks.take(3).joinToString(" · "),
                        style = redesignText(13.sp, FontWeight.Normal, RedesignColors.textSecondary),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        androidx.compose.animation.AnimatedVisibility(visible = userRating != null, enter = fadeIn(tween(220)), exit = fadeOut(tween(160))) {
            Column {
                Eyebrow(stringResource(Res.string.home_quick_look_your_rating))
                val value = userRating?.let { if (it % 1.0 == 0.0) it.toInt().toString() else it.toString() }.orEmpty()
                Text(
                    stringResource(Res.string.home_quick_look_rating_on, value, rating.provider?.displayName.orEmpty()),
                    style = redesignText(13.sp, FontWeight.Normal, RedesignColors.textSecondary),
                )
            }
        }
    }
}

@Composable
private fun NextEpisodeRow(item: MetaPreview, details: MetaDetails?, episode: SeriesPrimaryAction, progress: com.nuvio.app.features.watchprogress.WatchProgressUiState) {
    val video = details?.videos?.firstOrNull { it.id == episode.videoId }
    val entry = progress.progressForVideo(episode.videoId, item.id, episode.seasonNumber, episode.episodeNumber)?.takeUnless { it.isCompleted }
    val runtimeMinutes = entry?.durationMs?.takeIf { it > 0L }?.let { (it / 60_000L).toInt() } ?: video?.runtime
    val minutesLeft = entry?.takeIf { it.progressFraction > 0f }?.let { e ->
        runtimeMinutes?.let { total -> (total * (1f - e.progressFraction)).toInt().coerceAtLeast(1) }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        val still = episode.episodeThumbnail ?: video?.thumbnail
        Box(Modifier.width(96.dp).height(54.dp).clip(RoundedCornerShape(8.dp)).background(RedesignColors.fill)) {
            if (!still.isNullOrBlank()) {
                NuvioAsyncImage(model = still, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            }
        }
        Column {
            val code = stringResource(Res.string.detail_board_episode_code, episode.seasonNumber ?: 0, episode.episodeNumber ?: 0)
            val title = (episode.episodeTitle ?: video?.title)?.takeIf { it.isNotBlank() }
            Text(
                listOfNotNull(code, title).joinToString(" · "),
                style = redesignText(13.sp, FontWeight.SemiBold),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val left = minutesLeft?.let { stringResource(Res.string.detail_board_minutes_left, it) }
            val runtime = runtimeMinutes?.let { stringResource(Res.string.home_quick_look_runtime_minutes, it) }
            listOfNotNull(left, runtime).joinToString(" · ").takeIf { it.isNotBlank() }?.let {
                Text(it, style = redesignText(12.5.sp, FontWeight.Normal, RedesignColors.textMuted), maxLines = 1)
            }
        }
    }
}

@Composable
private fun SideSkeleton(thumbnail: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.width(90.dp).height(10.dp).clip(RoundedCornerShape(4.dp)).background(RedesignColors.fill))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (thumbnail) Box(Modifier.width(96.dp).height(54.dp).clip(RoundedCornerShape(8.dp)).background(RedesignColors.fill))
            Column(Modifier.padding(top = 2.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Box(Modifier.width(if (thumbnail) 120.dp else 150.dp).height(12.dp).clip(RoundedCornerShape(4.dp)).background(RedesignColors.fill))
                if (thumbnail) Box(Modifier.width(70.dp).height(10.dp).clip(RoundedCornerShape(4.dp)).background(RedesignColors.fill))
            }
        }
    }
}

private val NextEpisodeSlotHeight = 100.dp
private val WatchOnSlotHeight = 70.dp

@Composable
private fun Eyebrow(text: String) {
    Text(
        text.uppercase(),
        modifier = Modifier.padding(bottom = 6.dp),
        style = redesignText(11.sp, FontWeight.SemiBold, RedesignColors.textMuted).copy(letterSpacing = 1.1.sp),
    )
}

@Composable
private fun SkeletonLine(fraction: Float) {
    Box(Modifier.fillMaxWidth(fraction).height(12.dp).clip(RoundedCornerShape(6.dp)).background(RedesignColors.fill))
}
