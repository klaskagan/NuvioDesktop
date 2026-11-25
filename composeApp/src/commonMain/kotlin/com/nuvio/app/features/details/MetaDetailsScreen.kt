package com.nuvio.app.features.details

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.ui.geometry.Rect
import com.nuvio.app.features.streams.StreamQuickPickRequest
import com.nuvio.app.features.streams.StreamPreselection
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.snapshotFlow
import com.nuvio.app.LocalDetailTopBarState
import com.nuvio.app.core.ui.BackdropFocusController
import com.nuvio.app.core.ui.DesktopMergedTopBarHeight
import com.nuvio.app.core.ui.DesktopTopBarContentGap
import com.nuvio.app.core.ui.LocalBackdropContentStartInset
import com.nuvio.app.core.ui.LocalShellBackdropMode
import com.nuvio.app.core.ui.RedesignTypography
import com.nuvio.app.core.ui.ShellBackdrop
import com.nuvio.app.core.ui.ShellBackdropMode
import com.nuvio.app.features.details.components.DetailBoardSkeleton
import nuvio.composeapp.generated.resources.detail_board_episode_code
import nuvio.composeapp.generated.resources.detail_board_minutes_left
import nuvio.composeapp.generated.resources.detail_board_up_next
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.Crossfade
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CheckCircleOutline
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistAddCheckCircle
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import com.nuvio.app.core.ui.NuvioLoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.core.ui.NuvioAsyncImage as AsyncImage
import co.touchlab.kermit.Logger
import com.nuvio.app.core.build.AppFeaturePolicy
import com.nuvio.app.core.build.TrailerPlaybackMode
import com.nuvio.app.core.format.formatReleaseDateForDisplay
import com.nuvio.app.core.network.NetworkCondition
import com.nuvio.app.core.network.NetworkStatusRepository
import com.nuvio.app.core.i18n.localizedEpisodeTitle
import com.nuvio.app.core.i18n.localizedSeasonEpisodeCode
import com.nuvio.app.core.ui.ArtworkBackdrop
import com.nuvio.app.core.ui.NuvioTokens
import com.nuvio.app.core.ui.LocalOnBackdrop
import com.nuvio.app.core.ui.NuvioBackButton
import com.nuvio.app.core.ui.NuvioDesktopVerticalScrollbar
import com.nuvio.app.core.ui.LocalVerticalBarPadding
import com.nuvio.app.core.ui.verticalBarWidth
import com.nuvio.app.core.ui.NuvioCardDepthSurface
import com.nuvio.app.core.ui.NuvioPosterZoomActionOverlay
import com.nuvio.app.core.ui.NuvioToastController
import com.nuvio.app.core.ui.PosterZoomAnchor
import com.nuvio.app.core.ui.PosterZoomAnchorHolder
import com.nuvio.app.core.ui.PosterZoomOverlayAction
import com.nuvio.app.core.ui.desktopPageHorizontalPaddingForWidth
import com.nuvio.app.core.ui.nuvioDesktopDragScroll
import com.nuvio.app.core.ui.ScreenActivityEffect
import com.nuvio.app.core.ui.TrackingListPickerDialog
import com.nuvio.app.core.ui.nuvioSafeBottomPadding
import com.nuvio.app.core.ui.rememberArtworkBackdrop
import com.nuvio.app.core.ui.rememberHeroStretchState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import com.nuvio.app.features.details.components.DetailActions
import com.nuvio.app.features.details.components.CommentDetailSheet
import com.nuvio.app.features.details.components.DetailAdditionalInfoSection
import com.nuvio.app.features.details.components.DetailCastSection
import com.nuvio.app.features.details.components.DetailCommentsSection
import com.nuvio.app.features.details.components.CinematicHeaderBackdrop
import com.nuvio.app.features.details.components.DetailFloatingHeader
import com.nuvio.app.features.details.components.DetailHero
import com.nuvio.app.features.details.components.DetailMetaInfo
import com.nuvio.app.features.details.components.DetailPosterRailSection
import com.nuvio.app.features.details.components.DetailProductionSection
import com.nuvio.app.features.details.components.DetailSeriesContent
import com.nuvio.app.features.details.components.DesktopHeroTrailerPreview
import com.nuvio.app.features.details.components.rememberDesktopHeroTrailer
import com.nuvio.app.features.details.components.DesktopDetailBackdrop
import com.nuvio.app.features.details.components.DetailSeriesListEpisode
import com.nuvio.app.features.details.components.DetailSeriesListHeader
import com.nuvio.app.features.details.components.DetailTrailersSection
import com.nuvio.app.features.details.components.EpisodeWatchedActionSheet
import com.nuvio.app.features.details.components.RatingSheet
import com.nuvio.app.features.details.components.formatRating
import com.nuvio.app.features.details.components.ratingReference
import com.nuvio.app.features.details.components.seasonRatingReference
import com.nuvio.app.features.details.components.rememberRatingState
import com.nuvio.app.features.details.components.SeasonWatchedActionSheet
import com.nuvio.app.features.details.components.TabletArtworkBackdropAlignment
import com.nuvio.app.features.details.components.TabletDetailBackdrop
import com.nuvio.app.features.details.components.TabletDetailHero
import com.nuvio.app.features.details.components.TrailerPlayerPopup
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.mdblist.MdbListSettingsRepository
import com.nuvio.app.features.library.LibraryRepository
import com.nuvio.app.features.library.PendingTrackingMembershipRemoval
import com.nuvio.app.features.library.TrackingMembershipRemovalConfirmationHost
import com.nuvio.app.features.library.executeTrackingMembershipOperation
import com.nuvio.app.features.library.showTrackingMembershipRewriteFeedback
import com.nuvio.app.features.library.toLibraryItem
import com.nuvio.app.features.player.PlayerSettingsRepository
import com.nuvio.app.features.shuffle.EpisodeShuffleRepository
import com.nuvio.app.features.shuffle.EpisodeShuffleSheet
import com.nuvio.app.features.shuffle.ShuffleSurface
import com.nuvio.app.features.shuffle.rememberShuffleSave
import com.nuvio.app.features.shuffle.shufflePrimaryAction
import com.nuvio.app.features.profiles.ProfileRepository
import com.nuvio.app.features.streams.rememberPlaybackAvailability
import com.nuvio.app.features.streams.StreamAutoPlayPolicy
import com.nuvio.app.features.tmdb.TmdbSettingsRepository
import com.nuvio.app.features.tmdb.TmdbService
import com.nuvio.app.features.tmdb.originalTmdbImageUrl
import com.nuvio.app.features.trakt.TraktAuthRepository
import com.nuvio.app.features.trakt.TraktCommentReview
import com.nuvio.app.features.trakt.TraktCommentsRepository
import com.nuvio.app.features.trakt.TraktCommentsSettings
import com.nuvio.app.features.trakt.TraktConnectionMode
import com.nuvio.app.features.tracking.TrackingLibraryTab
import com.nuvio.app.features.tracking.TrackingMembershipApplyResult
import com.nuvio.app.features.tracking.toggleTrackingLibraryMembership
import com.nuvio.app.features.tracking.TrackingSettingsRepository
import com.nuvio.app.features.tracking.TrackingProviderId
import com.nuvio.app.features.trailer.TrailerPlaybackRequest
import com.nuvio.app.features.trailer.TrailerPlaybackTarget
import com.nuvio.app.features.tracking.TrackingRatings
import com.nuvio.app.features.trailer.TrailerPlaybackResolver
import com.nuvio.app.features.trailer.TrailerPlaybackSource
import com.nuvio.app.features.watched.WatchedRepository
import com.nuvio.app.features.watched.previousReleasedEpisodesBefore
import com.nuvio.app.features.watched.releasedPlayableEpisodes
import com.nuvio.app.features.watched.releasedEpisodesForSeason
import com.nuvio.app.features.watched.watchedItemKey
import com.nuvio.app.features.watchprogress.CurrentDateProvider
import com.nuvio.app.features.watchprogress.WatchProgressEntry
import com.nuvio.app.features.watchprogress.WatchProgressClock
import com.nuvio.app.features.watchprogress.WatchProgressRepository
import com.nuvio.app.features.watchprogress.buildPlaybackVideoId
import com.nuvio.app.features.watchprogress.ContinueWatchingPreferencesRepository
import com.nuvio.app.features.watching.application.WatchingActions
import com.nuvio.app.features.watching.application.WatchingState
import com.nuvio.app.isDesktop
import com.kmpalette.rememberDominantColorState
import com.kmpalette.extensions.painter.rememberPainterDominantColorState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import com.nuvio.app.core.auth.AuthRepository
import com.nuvio.app.core.auth.AuthState
import com.nuvio.app.features.party.PartyRepository
import com.nuvio.app.features.party.PartyMedia
import com.nuvio.app.features.party.messageRes
import com.nuvio.app.features.handoff.tvHandoffSupported
import com.nuvio.app.features.handoff.detailsTvHandoffRequest
import nuvio.composeapp.generated.resources.streams_resume_from_time
import com.nuvio.app.features.details.components.BoardContextMenu
import com.nuvio.app.features.details.components.BoardMenuAction
import com.nuvio.app.features.details.components.BoardRateControl
import com.nuvio.app.features.tracking.TrackingFavoriteState
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.CheckCircleOutline
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.automirrored.rounded.PlaylistAddCheck
import nuvio.composeapp.generated.resources.detail_board_choose_source
import nuvio.composeapp.generated.resources.detail_board_rate_episode
import nuvio.composeapp.generated.resources.episode_mark_unwatched
import nuvio.composeapp.generated.resources.episode_mark_watched
import nuvio.composeapp.generated.resources.episode_mark_previous_unwatched
import nuvio.composeapp.generated.resources.episode_mark_previous_watched
import nuvio.composeapp.generated.resources.episode_mark_season_unwatched
import nuvio.composeapp.generated.resources.episode_mark_season_watched
import com.nuvio.app.core.ui.RedesignColors
import com.nuvio.app.core.ui.redesignText
import nuvio.composeapp.generated.resources.detail_board_mark_previous_seasons
import com.nuvio.app.features.gallery.GalleryImage
import com.nuvio.app.features.gallery.DetailGalleryRepository
import com.nuvio.app.OverlayEscapeHandler
import com.nuvio.app.features.gallery.KinopoiskSettingsRepository
import com.nuvio.app.features.tracking.TrackingProviderRegistry
import com.nuvio.app.features.tracking.TrackingComments

private val watchedMarkerDiagnosticLog = Logger.withTag("WatchedMarkerDiag")
private const val DetailScrolledBackgroundDefaultMaxAlpha = 0.86f
private const val DetailScrolledBackgroundCinematicMaxAlpha = 0.36f
private const val DetailScrolledBackgroundFadeHeroFraction = 0.75f

internal fun detailScrolledBackgroundProgress(scrollOffsetPx: Float, heroHeightPx: Int): Float {
    if (scrollOffsetPx <= 0f || heroHeightPx <= 0) return 0f
    val fadeDistancePx = heroHeightPx * DetailScrolledBackgroundFadeHeroFraction
    return (scrollOffsetPx / fadeDistancePx).coerceIn(0f, 1f)
}

internal fun detailScrolledBackgroundAlpha(
    scrollOffsetPx: Float,
    heroHeightPx: Int,
    maxAlpha: Float = DetailScrolledBackgroundDefaultMaxAlpha,
): Float {
    return detailScrolledBackgroundProgress(scrollOffsetPx, heroHeightPx) * maxAlpha.coerceIn(0f, 1f)
}

@Composable
@OptIn(ExperimentalSharedTransitionApi::class)
fun MetaDetailsScreen(
    type: String,
    id: String,
    onBack: () -> Unit,
    onPlay: ((type: String, videoId: String, parentMetaId: String, parentMetaType: String, title: String, logo: String?, poster: String?, background: String?, seasonNumber: Int?, episodeNumber: Int?, episodeTitle: String?, episodeThumbnail: String?, pauseDescription: String?, resumePositionMs: Long?) -> Unit)? = null,
    onPlayManually: ((type: String, videoId: String, parentMetaId: String, parentMetaType: String, title: String, logo: String?, poster: String?, background: String?, seasonNumber: Int?, episodeNumber: Int?, episodeTitle: String?, episodeThumbnail: String?, pauseDescription: String?, resumePositionMs: Long?) -> Unit)? = null,
    onDownload: ((type: String, videoId: String, parentMetaId: String, parentMetaType: String, title: String, logo: String?, poster: String?, background: String?, seasonNumber: Int?, episodeNumber: Int?, episodeTitle: String?, episodeThumbnail: String?, pauseDescription: String?, resumePositionMs: Long?) -> Unit)? = null,
    onOpenMeta: ((MetaPreview) -> Unit)? = null,
    onCastClick: ((MetaPerson, String?) -> Unit)? = null,
    onCompanyClick: ((MetaCompany, String) -> Unit)? = null,
    onOpenParty: ((code: String) -> Unit)? = null,
    onManagePairedTvs: (() -> Unit)? = null,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    modifier: Modifier = Modifier,
) {
    val playbackAvailability = rememberPlaybackAvailability()
    val shuffleProfile by remember {
        EpisodeShuffleRepository.ensureLoaded()
        EpisodeShuffleRepository.uiState
    }.collectAsStateWithLifecycle()
    val shuffleProfileId = ProfileRepository.activeProfileId
    var shuffleVisit by remember(type, id, shuffleProfileId) { mutableStateOf(WatchProgressClock.nowEpochMs()) }
    var shuffleWasInactive by remember(type, id, shuffleProfileId) { mutableStateOf(false) }
    var showShuffle by remember(type, id, shuffleProfileId) { mutableStateOf(false) }
    ScreenActivityEffect(type, id, shuffleProfileId) { active ->
        if (active && shuffleWasInactive) shuffleVisit += 1
        shuffleWasInactive = !active
    }
    val uiState by MetaDetailsRepository.uiState.collectAsStateWithLifecycle()
    val displayedMeta = uiState.meta?.takeIf { it.type == type && it.id == id }
        ?: MetaDetailsRepository.peek(type, id)
    val metaScreenSettingsUiState by remember {
        MetaScreenSettingsRepository.ensureLoaded()
        MetaScreenSettingsRepository.uiState
    }.collectAsStateWithLifecycle()
    val mdbListSettings by remember {
        MdbListSettingsRepository.ensureLoaded()
        MdbListSettingsRepository.uiState
    }.collectAsStateWithLifecycle()
    val traktAuthUiState by remember {
        TraktAuthRepository.ensureLoaded()
        TraktAuthRepository.uiState
    }.collectAsStateWithLifecycle()
    val authState by AuthRepository.state.collectAsStateWithLifecycle()
    val trackingSettingsUiState by remember {
        TrackingSettingsRepository.ensureLoaded()
        TrackingSettingsRepository.uiState
    }.collectAsStateWithLifecycle()
    val tmdbSettingsUiState by remember {
        TmdbSettingsRepository.ensureLoaded()
        TmdbSettingsRepository.uiState
    }.collectAsStateWithLifecycle()
    val libraryUiState by remember {
        LibraryRepository.ensureLoaded()
        LibraryRepository.uiState
    }.collectAsStateWithLifecycle()
    val watchedUiState by remember {
        WatchedRepository.ensureLoaded()
        WatchedRepository.uiState
    }.collectAsStateWithLifecycle()
    val fullyWatchedSeriesKeys by WatchedRepository.fullyWatchedSeriesKeys.collectAsStateWithLifecycle()
    val watchProgressUiState by remember {
        WatchProgressRepository.ensureLoaded()
        WatchProgressRepository.uiState
    }.collectAsStateWithLifecycle()
    val progressByVideoId = remember(watchProgressUiState.entries, id) {
        watchProgressUiState.byVideoIdForContent(id)
    }
    val playerSettingsUiState by remember {
        PlayerSettingsRepository.ensureLoaded()
        PlayerSettingsRepository.uiState
    }.collectAsStateWithLifecycle()
    val networkStatusUiState by NetworkStatusRepository.uiState.collectAsStateWithLifecycle()
    var autoLoadAttempted by remember(type, id) { mutableStateOf(false) }
    var observedOfflineState by remember(type, id) { mutableStateOf(false) }
    var selectedEpisodeForActions by remember(type, id) { mutableStateOf<MetaVideo?>(null) }
    // Where the board's episode menu opens: the card it was asked from.
    var episodeMenuAnchor by remember(type, id) { mutableStateOf<Rect?>(null) }
    // The gallery's "Set as background": this title's backdrop for this visit.
    var boardBackgroundOverride by remember(type, id) { mutableStateOf<String?>(null) }
    var selectedEpisodeZoomAnchor by remember(type, id) { mutableStateOf<PosterZoomAnchor?>(null) }
    var episodeToRate by remember(type, id) { mutableStateOf<MetaVideo?>(null) }
    val episodeOverlayHazeState = rememberHazeState()
    var selectedSeasonForActions by remember(type, id) { mutableStateOf<Int?>(null) }
    val commentsEnabled by remember {
        TraktCommentsSettings.ensureLoaded()
        TraktCommentsSettings.enabled
    }.collectAsStateWithLifecycle()
    var comments by remember(type, id) { mutableStateOf<List<TraktCommentReview>>(emptyList()) }
    var commentsCurrentPage by remember(type, id) { mutableIntStateOf(0) }
    var commentsPageCount by remember(type, id) { mutableIntStateOf(0) }
    var isCommentsLoading by remember(type, id) { mutableStateOf(false) }
    var isCommentsLoadingMore by remember(type, id) { mutableStateOf(false) }
    var commentsError by remember(type, id) { mutableStateOf<String?>(null) }
    var selectedComment by remember(type, id) { mutableStateOf<TraktCommentReview?>(null) }
    val detailsScope = rememberCoroutineScope()
    var showLibraryListPicker by remember(type, id) { mutableStateOf(false) }
    var pickerTabs by remember(type, id) { mutableStateOf<List<TrackingLibraryTab>>(emptyList()) }
    var pickerMembership by remember(type, id) { mutableStateOf<Map<String, Boolean>>(emptyMap()) }
    var pickerPending by remember(type, id) { mutableStateOf(false) }
    var pickerError by remember(type, id) { mutableStateOf<String?>(null) }
    var pendingTrackingRemoval by remember(type, id) {
        mutableStateOf<PendingTrackingMembershipRemoval?>(null)
    }
    val trackingListsUpdateFailedMessage = stringResource(Res.string.tracking_lists_update_failed)
    var episodeImdbRatings by remember(type, id) { mutableStateOf<Map<Pair<Int, Int>, Double>>(emptyMap()) }
    var deferredMetaWorkAllowed by remember(type, id) { mutableStateOf(false) }

    LaunchedEffect(
        displayedMeta?.id,
        displayedMeta?.type,
        displayedMeta?.name,
        displayedMeta?.videos,
        watchedUiState.items,
        watchedUiState.isLoaded,
        watchedUiState.hasLoadedRemoteItems,
        fullyWatchedSeriesKeys,
        watchProgressUiState.entries,
        trackingSettingsUiState.watchProgressSource,
    ) {
        val meta = displayedMeta ?: return@LaunchedEffect
        val posterKey = watchedItemKey(meta.type, meta.id)
        val expectedEpisodeKeys = meta.videos.map { episode ->
            watchedItemKey(meta.type, meta.id, episode.season, episode.episode)
        }
        val matchedEpisodeKeys = expectedEpisodeKeys.filter(watchedUiState.watchedKeys::contains)
        val completedProgressMatches = meta.videos.count { episode ->
            val videoId = buildPlaybackVideoId(
                parentMetaId = meta.id,
                seasonNumber = episode.season,
                episodeNumber = episode.episode,
                fallbackVideoId = episode.id,
            )
            progressByVideoId[videoId]?.isEffectivelyCompleted == true
        }
        val directItemKeys = watchedUiState.items
            .asSequence()
            .filter { item -> item.id == meta.id }
            .take(10)
            .joinToString(separator = ",") { item ->
                watchedItemKey(item.type, item.id, item.season, item.episode)
            }
        val titleCandidateKeys = watchedUiState.items
            .asSequence()
            .filter { item -> item.name.equals(meta.name, ignoreCase = true) }
            .take(10)
            .joinToString(separator = ",") { item ->
                watchedItemKey(item.type, item.id, item.season, item.episode)
            }
        watchedMarkerDiagnosticLog.i {
            "marker state requestedSource=${trackingSettingsUiState.watchProgressSource} " +
                "content=${meta.type}:${meta.id} repositoryLoaded=${watchedUiState.isLoaded} " +
                "remoteLoaded=${watchedUiState.hasLoadedRemoteItems} repositoryItems=${watchedUiState.items.size} " +
                "posterKey=$posterKey posterInWatched=${posterKey in watchedUiState.watchedKeys} " +
                "posterInFullyWatched=${posterKey in fullyWatchedSeriesKeys} videos=${meta.videos.size} " +
                "episodeMarkerMatches=${matchedEpisodeKeys.size} completedProgressMatches=$completedProgressMatches " +
                "directItemKeys=[$directItemKeys] titleCandidateKeys=[$titleCandidateKeys] " +
                "expectedEpisodeKeys=[${expectedEpisodeKeys.take(10).joinToString(",")}] " +
                "repositoryKeySample=[${watchedUiState.watchedKeys.take(10).joinToString(",")}]"
        }
    }

    val shouldShowComments = commentsEnabled &&
        traktAuthUiState.mode == TraktConnectionMode.CONNECTED &&
        displayedMeta != null &&
        displayedMeta.type.lowercase().let { it == "movie" || it == "series" || it == "show" || it == "tv" }

    LaunchedEffect(displayedMeta?.id) {
        deferredMetaWorkAllowed = false
        if (displayedMeta != null) {
            delay(250)
            deferredMetaWorkAllowed = true
        }
    }

    LaunchedEffect(displayedMeta?.id, shouldShowComments, deferredMetaWorkAllowed) {
        if (displayedMeta == null || !shouldShowComments) {
            comments = emptyList()
            commentsCurrentPage = 0
            commentsPageCount = 0
            commentsError = null
            return@LaunchedEffect
        }
        if (!deferredMetaWorkAllowed) return@LaunchedEffect
        isCommentsLoading = true
        commentsError = null
        try {
            val result = TraktCommentsRepository.getCommentsPage(displayedMeta, page = 1)
            comments = result.items
            commentsCurrentPage = result.currentPage
            commentsPageCount = result.pageCount
        } catch (e: Exception) {
            commentsError = e.message ?: getString(Res.string.details_comments_load_failed)
        }
        isCommentsLoading = false
    }

    LaunchedEffect(
        displayedMeta?.id,
        displayedMeta?.videos,
        deferredMetaWorkAllowed,
        metaScreenSettingsUiState.episodeRatingsVisibility,
    ) {
        if (!metaScreenSettingsUiState.episodeRatingsVisibility.showRatings) {
            episodeImdbRatings = emptyMap()
            return@LaunchedEffect
        }
        val metaForRatings = displayedMeta
        if (!deferredMetaWorkAllowed) return@LaunchedEffect
        if (metaForRatings == null || !metaForRatings.isSeriesLikeForEpisodeRatings()) {
            episodeImdbRatings = emptyMap()
            return@LaunchedEffect
        }

        val imdbId = extractImdbId(metaForRatings.id) ?: extractImdbId(id) ?: metaForRatings.imdbId
        val tmdbId = extractTmdbId(metaForRatings.id)
            ?: extractTmdbId(id)
            ?: TmdbService.ensureTmdbId(metaForRatings.id, metaForRatings.type, fallbackImdbId = metaForRatings.imdbId)?.toIntOrNull()
            ?: TmdbService.ensureTmdbId(id, type, fallbackImdbId = metaForRatings.imdbId)?.toIntOrNull()

        if (imdbId == null && tmdbId == null) {
            episodeImdbRatings = emptyMap()
            return@LaunchedEffect
        }

        episodeImdbRatings = ImdbEpisodeRatingsRepository.getEpisodeRatings(
            imdbId = imdbId,
            tmdbId = tmdbId,
            addonEpisodes = metaForRatings.videos.map { AddonEpisodeAirDate(it.season, it.episode, it.released) },
        )
    }

    LaunchedEffect(type, id, displayedMeta, uiState.isLoading, autoLoadAttempted) {
        if (!autoLoadAttempted && displayedMeta == null && !uiState.isLoading) {
            autoLoadAttempted = true
            MetaDetailsRepository.load(type, id)
        }
    }

    LaunchedEffect(
        type,
        id,
        displayedMeta?.id,
        uiState.isLoading,
        trackingSettingsUiState.moreLikeThisSource,
        traktAuthUiState.mode,
        tmdbSettingsUiState.enabled,
        tmdbSettingsUiState.useMoreLikeThis,
        tmdbSettingsUiState.language,
        mdbListSettings,
    ) {
        if (displayedMeta != null && !uiState.isLoading) {
            MetaDetailsRepository.load(type, id)
        }
    }

    LaunchedEffect(networkStatusUiState.condition, displayedMeta, uiState.isLoading, type, id) {
        when (networkStatusUiState.condition) {
            NetworkCondition.NoInternet,
            NetworkCondition.ServersUnreachable,
            -> {
                observedOfflineState = true
            }

            NetworkCondition.Online -> {
                if (!observedOfflineState) return@LaunchedEffect
                observedOfflineState = false
                if (displayedMeta == null && !uiState.isLoading) {
                    MetaDetailsRepository.load(type, id)
                }
            }

            NetworkCondition.Unknown,
            NetworkCondition.Checking,
            -> Unit
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (selectedEpisodeZoomAnchor != null) {
                        Modifier.hazeSource(state = episodeOverlayHazeState)
                    } else {
                        Modifier
                    },
                )
                .background(MaterialTheme.colorScheme.background),
        ) {
            // Desktop redesign: wide windows show the board on the ambient backdrop of the title's own
            // artwork. One backdrop sits behind loading, error and content, so it crossfades in.
            val boardLayout = isDesktop && maxWidth >= DetailBoardMinWidth
            val boardBackdropMode = LocalShellBackdropMode.current
            val boardBackdrop = remember(boardLayout, boardBackdropMode == ShellBackdropMode.Solid) {
                if (boardLayout && boardBackdropMode != ShellBackdropMode.Solid) BackdropFocusController() else null
            }
            if (boardBackdrop != null) {
                val boardArt = boardBackgroundOverride ?: displayedMeta?.let { shown -> shown.background?.takeIf { it.isNotBlank() } ?: shown.poster }
                LaunchedEffect(boardBackdrop, boardArt) { boardBackdrop.base = boardArt }
                ShellBackdrop(boardBackdrop, showArtwork = boardBackdropMode == ShellBackdropMode.Ambient)
            }
            val detailTopBarState = LocalDetailTopBarState.current
            // With the merged top bar the board starts under it; the sidebar layout has no bar to clear.
            val boardTopPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() +
                if (detailTopBarState != null) DesktopMergedTopBarHeight + DesktopTopBarContentGap else 16.dp
            val boardStartInset = LocalBackdropContentStartInset.current
            val detailsMaxWidth = maxWidth
            // Loading, error and content crossfade, as Home's hero does; the backdrop above stays put.
            val detailContentState = when {
                displayedMeta == null && uiState.isLoading -> DetailContentState.Loading
                displayedMeta == null && uiState.errorMessage != null -> DetailContentState.Error
                displayedMeta != null -> DetailContentState.Content
                else -> DetailContentState.Empty
            }
            AnimatedContent(
                targetState = detailContentState,
                transitionSpec = { fadeIn(tween(320)) togetherWith fadeOut(tween(200)) },
                modifier = Modifier.fillMaxSize(),
                label = "detail_content_state",
            ) { contentState ->
            Box(Modifier.fillMaxSize()) {
            when (contentState) {
            DetailContentState.Loading -> {
                if (boardLayout) {
                    RedesignTypography {
                        DetailBoardSkeleton(
                            horizontalPadding = desktopPageHorizontalPaddingForWidth((detailsMaxWidth - boardStartInset).value),
                            onBack = onBack,
                            isSeries = type == "series",
                            modifier = Modifier.padding(start = boardStartInset, top = boardTopPadding),
                        )
                    }
                } else {
                    NuvioLoadingIndicator(
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
            }

            DetailContentState.Error -> {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = stringResource(Res.string.details_failed_to_load),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        text = when (networkStatusUiState.condition) {
                            NetworkCondition.NoInternet -> stringResource(Res.string.details_check_connection)
                            NetworkCondition.ServersUnreachable -> stringResource(Res.string.details_servers_unreachable)
                            else -> uiState.errorMessage.orEmpty()
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            NetworkStatusRepository.requestRefresh(force = true)
                            MetaDetailsRepository.load(type, id)
                        },
                    ) {
                        Text(stringResource(Res.string.action_retry))
                    }
                }
            }

            DetailContentState.Content -> {
                val meta = displayedMeta ?: return@Box
                val metaPreview = remember(meta) { meta.toMetaPreview() }
                val todayIsoDate = CurrentDateProvider.todayIsoDate()
                val isSaved = remember(
                    libraryUiState.items,
                    libraryUiState.sections,
                    libraryUiState.sourceMode,
                    meta.id,
                    meta.type,
                ) {
                    LibraryRepository.isSaved(meta.id, meta.type)
                }
                val isWatched = remember(watchedUiState.watchedKeys, fullyWatchedSeriesKeys, metaPreview) {
                    WatchingState.isPosterWatched(
                        watchedKeys = watchedUiState.watchedKeys,
                        item = metaPreview,
                        fullyWatchedSeriesKeys = fullyWatchedSeriesKeys,
                    )
                }
                val openLibraryListPicker = remember(meta) {
                    {
                        val libraryItem = meta.toLibraryItem(savedAtEpochMs = 0L)
                        pickerTabs = LibraryRepository.libraryListTabs(libraryItem)
                        pickerMembership = pickerTabs.associate { it.key to false }
                        pickerPending = true
                        pickerError = null
                        showLibraryListPicker = true
                        detailsScope.launch {
                            runCatching {
                                val snapshot = LibraryRepository.getMembershipSnapshot(libraryItem)
                                val tabs = LibraryRepository.libraryListTabs(libraryItem)
                                pickerTabs = tabs
                                pickerMembership = tabs.associate { tab ->
                                    tab.key to (snapshot[tab.key] == true)
                                }
                            }.onFailure { error ->
                                pickerError = error.message ?: getString(Res.string.trakt_lists_load_failed)
                            }
                            pickerPending = false
                        }
                        Unit
                    }
                }
                val toggleSaved = remember(meta, trackingListsUpdateFailedMessage) {
                    {
                        val item = meta.toLibraryItem(savedAtEpochMs = 0L)
                        detailsScope.launch {
                            val toggleMembership: suspend (Set<TrackingProviderId>) ->
                                TrackingMembershipApplyResult = { confirmedProviders ->
                                LibraryRepository.toggleSaved(
                                    item = item,
                                    confirmedRemovalProviders = confirmedProviders,
                                )
                            }
                            executeTrackingMembershipOperation(
                                operation = { toggleMembership(emptySet()) },
                                onSuccess = { result ->
                                    if (result.requiresRemovalConfirmation) {
                                        pendingTrackingRemoval = PendingTrackingMembershipRemoval(
                                            itemTitle = item.name,
                                            confirmations = result.requiredRemovalConfirmations,
                                            retry = toggleMembership,
                                            onApplied = ::showTrackingMembershipRewriteFeedback,
                                            onFailure = { error ->
                                                NuvioToastController.show(
                                                    error.message ?: trackingListsUpdateFailedMessage,
                                                )
                                            },
                                        )
                                    } else {
                                        showTrackingMembershipRewriteFeedback(result)
                                    }
                                },
                                onFailure = { error ->
                                    NuvioToastController.show(
                                        error.message ?: trackingListsUpdateFailedMessage,
                                    )
                                },
                            )
                        }
                        Unit
                    }
                }
                val toggleWatched = remember(metaPreview) {
                    {
                        detailsScope.launch {
                            WatchingActions.togglePosterWatched(metaPreview)
                        }
                        Unit
                    }
                }
                LaunchedEffect(meta.id, meta.type, watchProgressUiState.hasLoadedRemoteProgress) {
                    if (meta.type.lowercase() in setOf("series", "show", "tv", "tvshow")) {
                        WatchProgressRepository.refreshEpisodeProgress(meta.id)
                    }
                }
                LaunchedEffect(
                    meta.id,
                    meta.type,
                    todayIsoDate,
                    watchedUiState.isLoaded,
                    watchProgressUiState.hasLoadedRemoteProgress,
                    watchedUiState.watchedKeys,
                    watchProgressUiState.entries,
                ) {
                    if (watchedUiState.isLoaded && watchProgressUiState.hasLoadedRemoteProgress) {
                        WatchingActions.reconcileSeriesWatchedState(
                            meta = meta,
                            todayIsoDate = todayIsoDate,
                        )
                    }
                }
                val movieProgress = progressByVideoId[meta.id]
                    ?.takeUnless { it.isCompleted }
                val cwPrefs by ContinueWatchingPreferencesRepository.uiState.collectAsStateWithLifecycle()
                val shuffleSettings = shuffleProfile.settings(meta.id, meta.type)
                val hasShuffleEpisodes = remember(meta.videos) {
                    meta.videos.any { (it.season ?: 0) > 0 && (it.episode ?: 0) > 0 }
                }
                val showShuffleButton = shuffleProfile.available &&
                    meta.type.lowercase() in setOf("series", "tv", "show", "tvshow") &&
                    (shuffleSettings.enabled || hasShuffleEpisodes)
                val saveShuffle = rememberShuffleSave(meta.id, shuffleProfileId, shuffleSettings)
                val seriesAction = remember(watchProgressUiState.entries, watchedUiState.items, meta, todayIsoDate, cwPrefs.upNextFromFurthestEpisode, watchedUiState.watchedKeys, shuffleSettings, shuffleVisit, shuffleProfileId) {
                    if (shuffleSettings.enabled) meta.shufflePrimaryAction(
                        profileId = shuffleProfileId,
                        settings = shuffleSettings,
                        entries = watchProgressUiState.entries,
                        watchedKeys = watchedUiState.watchedKeys,
                        visit = shuffleVisit,
                    ) else {
                        EpisodeShuffleRepository.shuffle.clearSelection(shuffleProfileId, meta.id, ShuffleSurface.DETAIL)
                        meta.seriesPrimaryAction(
                            entries = watchProgressUiState.entries,
                            watchedItems = watchedUiState.items,
                            todayIsoDate = todayIsoDate,
                            preferFurthestEpisode = cwPrefs.upNextFromFurthestEpisode,
                            watchedKeys = watchedUiState.watchedKeys,
                            allowRewatch = true,
                        )
                    }
                }
                val seriesActionState = rememberUpdatedState(seriesAction.takeIf { (meta.type == "series" || meta.videos.any { it.season != null || it.episode != null }) })
                val seriesActionVideo = remember(seriesAction, meta.id, meta.videos) {
                    val action = seriesAction ?: return@remember null
                    meta.videos.firstOrNull { video ->
                        if (action.seasonNumber != null && action.episodeNumber != null) {
                            video.season == action.seasonNumber &&
                                video.episode == action.episodeNumber
                        } else {
                            buildPlaybackVideoId(
                                parentMetaId = meta.id,
                                seasonNumber = video.season,
                                episodeNumber = video.episode,
                                fallbackVideoId = video.id,
                            ) == action.videoId || video.id == action.videoId
                        }
                    }
                }
                val seriesPauseDescription = remember(seriesActionVideo) {
                    seriesActionVideo?.overview
                }
                val seriesStreamVideoId = remember(seriesAction, seriesActionVideo) {
                    val action = seriesAction ?: return@remember null
                    seriesActionVideo?.id?.takeIf { it.isNotBlank() } ?: action.videoId
                }
                val hasEpisodes = meta.videos.any { it.season != null || it.episode != null }
                val episodeListGroupedEpisodes = remember(meta.videos, meta.type) {
                    meta.groupedEpisodesForDisplay()
                }
                val episodeListSeasons = remember(episodeListGroupedEpisodes) {
                    episodeListGroupedEpisodes.keys.sortedBy(::seasonSortKey)
                }
                var selectedEpisodeListSeason by rememberSaveable(meta.id) {
                    mutableStateOf<Int?>(null)
                }
                val defaultEpisodeListSeason = seriesAction?.seasonNumber
                    ?.takeIf { it in episodeListGroupedEpisodes }
                    ?: episodeListSeasons.firstOrNull()
                val currentEpisodeListSeason = selectedEpisodeListSeason
                    ?.takeIf { it in episodeListGroupedEpisodes }
                    ?: defaultEpisodeListSeason
                val audienceState = remember(meta.id, meta.imdbId) { SeriesAudienceState() }
                val seasonProgress = remember(meta, todayIsoDate, watchedUiState.isLoaded, watchedUiState.watchedKeys, progressByVideoId) {
                    if (!isDesktop || meta.type != "series" || !watchedUiState.isLoaded) null
                    else meta.seasonWatchedCounts(todayIsoDate) {
                        isEpisodeWatchedForActions(meta, it, watchedUiState.watchedKeys, progressByVideoId)
                    }
                }
                val hasProductionSection = remember(meta) {
                    meta.productionCompanies.isNotEmpty() || meta.networks.isNotEmpty()
                }
                val hasAdditionalInfoSection = remember(meta) {
                    meta.status != null ||
                        meta.releaseInfo != null ||
                        meta.runtime != null ||
                        meta.ageRating != null ||
                        meta.country != null ||
                        meta.language != null ||
                        meta.hasMovieFinancialDetails ||
                        // The board's Details tab also has genres, awards, links and the overview.
                        meta.genres.isNotEmpty() || !meta.awards.isNullOrBlank() || meta.imdbId != null || !meta.description.isNullOrBlank()
                }
                val hasCollectionSection = remember(meta) {
                    meta.collectionName != null && meta.collectionItems.isNotEmpty()
                }
                val hasMoreLikeThisSection = remember(meta) {
                    meta.moreLikeThis.isNotEmpty()
                }
                val hasTrailersSection = remember(meta) {
                    meta.trailers.isNotEmpty()
                }
                val uriHandler = LocalUriHandler.current
                val trailerPlaybackMode = AppFeaturePolicy.trailerPlaybackMode
                val inAppTrailerPlaybackEnabled = trailerPlaybackMode == TrailerPlaybackMode.IN_APP
                val trailerScope = rememberCoroutineScope()
                var selectedTrailer by remember(meta.id) { mutableStateOf<MetaTrailer?>(null) }
                val preferredTrailerBackend by rememberUpdatedState(metaScreenSettingsUiState.trailerPlayerBackend)
                val trailerRequest = remember(meta.id) { TrailerPlaybackRequest(trailerScope) }
                DisposableEffect(trailerRequest) {
                    onDispose { trailerRequest.cancel() }
                }
                var isLeavingDetails by remember(meta.id) { mutableStateOf(false) }
                val heroTrailerCandidates = remember(meta.trailers) {
                    if (isDesktop) rankDesktopHeroTrailers(meta.trailers) else listOfNotNull(selectHeroTrailer(meta.trailers))
                }
                val heroTrailerPlaybackAvailable = AppFeaturePolicy.heroTrailerPlaybackSupported && inAppTrailerPlaybackEnabled
                // A trailer picked on the board plays in the tile even with hero autoplay off; the
                // setting only stops trailers starting on their own.
                var heroTrailerPicked by remember(meta.id) { mutableStateOf(false) }
                val heroTrailerPlaybackEnabled = heroTrailerPlaybackAvailable &&
                    (metaScreenSettingsUiState.heroTrailerPlayback || heroTrailerPicked)
                var heroTrailerPlaybackSource by remember(meta.id, heroTrailerCandidates) { mutableStateOf<TrailerPlaybackSource?>(null) }
                var desktopHeroAttempt by remember(meta.id, heroTrailerCandidates) { mutableStateOf<DesktopHeroTrailerAttempt?>(null) }
                var heroTrailerReady by remember(meta.id, heroTrailerCandidates) { mutableStateOf(false) }
                var heroTrailerFinished by remember(meta.id, heroTrailerCandidates) { mutableStateOf(false) }
                var completedDesktopTrailer by remember(meta.id, heroTrailerCandidates) { mutableStateOf<MetaTrailer?>(null) }
                var replayDesktopTrailer by remember(meta.id, heroTrailerCandidates) { mutableStateOf<MetaTrailer?>(null) }
                var replayCount by remember(meta.id, heroTrailerCandidates) { mutableIntStateOf(0) }
                // A trailer picked on the board (Trailers tab, Trailer button) that couldn't play in the tile.
                var pickedTrailerFailed by remember(meta.id) { mutableStateOf<MetaTrailer?>(null) }
                var fadingDesktopAttempt by remember(meta.id, heroTrailerCandidates) { mutableStateOf<DesktopHeroTrailerAttempt?>(null) }
                val heroTrailerMuted by HeroTrailerAudioState.muted.collectAsStateWithLifecycle()
                val fadingAttempt = fadingDesktopAttempt
                LaunchedEffect(fadingAttempt) {
                    if (fadingAttempt != null) {
                        delay(NuvioTokens.Motion.sheetEnterMillis.toLong())
                        if (fadingDesktopAttempt === fadingAttempt) fadingDesktopAttempt = null
                    }
                }
                LaunchedEffect(
                    meta.id, heroTrailerPlaybackEnabled, heroTrailerCandidates, deferredMetaWorkAllowed, isLeavingDetails, replayCount,
                ) {
                    val requestedReplay = replayDesktopTrailer
                    replayDesktopTrailer = null
                    heroTrailerPlaybackSource = null
                    desktopHeroAttempt = null
                    completedDesktopTrailer = null
                    fadingDesktopAttempt = null
                    heroTrailerReady = false
                    heroTrailerFinished = false
                    if (!deferredMetaWorkAllowed || !heroTrailerPlaybackEnabled || isLeavingDetails || (heroTrailerCandidates.isEmpty() && requestedReplay == null)) {
                        return@LaunchedEffect
                    }
                    if (isDesktop) {
                        val started = startDesktopHeroTrailer(
                            // A picked trailer plays alone: if it can't, the tile says so rather than
                            // quietly playing a different one.
                            trailers = requestedReplay?.let(::listOf) ?: heroTrailerCandidates,
                            resolve = { trailer ->
                                val renditions = TrailerPlaybackResolver.resolveFromYouTubeUrl(
                                    trailer.youtubePlaybackUrl(),
                                    target = TrailerPlaybackTarget.DESKTOP_DETAILS_HERO,
                                )?.heroRenditions.orEmpty()
                                trailer.logPreviewResolution("hero", meta.name, renditions.isNotEmpty())
                                renditions
                            },
                            tryRendition = { attempt ->
                                heroTrailerReady = false
                                desktopHeroAttempt = attempt
                                var renditionReady = false
                                try {
                                    attempt.ready.await().also { renditionReady = it }
                                } finally {
                                    if (!renditionReady && desktopHeroAttempt === attempt) {
                                        desktopHeroAttempt = null
                                    }
                                }
                            },
                        )
                        if (!started) heroTrailerFinished = true
                        // The picked trailer didn't start (another candidate may have): play it in the pop-up.
                        if (requestedReplay != null && (!started || desktopHeroAttempt?.trailer?.key != requestedReplay.key)) {
                            pickedTrailerFailed = requestedReplay
                        }
                    } else {
                        heroTrailerPlaybackSource = TrailerPlaybackResolver.resolveFromYouTubeUrl(
                            heroTrailerCandidates.first().youtubePlaybackUrl(),
                            target = TrailerPlaybackTarget.FRAME_PREVIEW,
                        )
                        heroTrailerFinished = heroTrailerPlaybackSource == null
                    }
                }
                val onBackFromDetails: () -> Unit = {
                    isLeavingDetails = true
                    heroTrailerReady = false
                    heroTrailerFinished = true
                    onBack()
                }
                val resolveTrailer: (MetaTrailer) -> Unit = remember(meta.id, trailerPlaybackMode, uriHandler) {
                    { trailer ->
                        val youtubeUrl = trailer.youtubePlaybackUrl()
                        when (trailerPlaybackMode) {
                            TrailerPlaybackMode.EXTERNAL -> runCatching { uriHandler.openUri(youtubeUrl) }
                            TrailerPlaybackMode.IN_APP -> {
                                selectedTrailer = trailer
                                trailerRequest.start(youtubeUrl, backend = com.nuvio.app.features.trailer.effectiveTrailerPlayerBackend(
                                    preferredTrailerBackend, com.nuvio.app.features.trailer.YouTubeEmbed.supported,
                                ))
                            }
                        }
                    }
                }
                val primaryVideoId = seriesStreamVideoId ?: seriesAction?.videoId ?: meta.id
                val shufflePoolEmpty = shuffleSettings.enabled && seriesAction == null
                val isPrimaryPlayEnabled = shufflePoolEmpty || playbackAvailability.canPlay(
                    type = meta.type,
                    videoId = primaryVideoId,
                    parentMetaId = meta.id,
                    seasonNumber = seriesAction?.seasonNumber,
                    episodeNumber = seriesAction?.episodeNumber,
                )
                val playText = stringResource(Res.string.action_play)
                val resumeText = stringResource(Res.string.action_resume)
                val chooseEpisodesText = stringResource(Res.string.shuffle_change_selection)
                val playButtonLabel = remember(movieProgress, seriesAction, meta.type, hasEpisodes, playText, resumeText, shufflePoolEmpty, chooseEpisodesText) {
                    when {
                        shufflePoolEmpty -> chooseEpisodesText
                        (meta.type == "series" || hasEpisodes) && seriesAction != null ->
                            seriesAction.label
                        meta.type != "series" && !hasEpisodes && movieProgress != null ->
                            resumeText
                        else -> playText
                    }
                }
                val onPrimaryPlayClick: () -> Unit = {
                    when {
                        shufflePoolEmpty -> showShuffle = true
                        (meta.type == "series" || hasEpisodes) && seriesAction != null -> {
                            onPlay?.invoke(
                                meta.type,
                                seriesStreamVideoId ?: seriesAction.videoId,
                                meta.id,
                                meta.type,
                                meta.name,
                                meta.logo,
                                meta.poster,
                                meta.background,
                                seriesAction.seasonNumber,
                                seriesAction.episodeNumber,
                                seriesAction.episodeTitle,
                                seriesAction.episodeThumbnail,
                                seriesPauseDescription,
                                seriesAction.resumePositionMs,
                            )
                        }

                        else -> {
                            onPlay?.invoke(
                                meta.type,
                                meta.id,
                                meta.id,
                                meta.type,
                                meta.name,
                                meta.logo,
                                meta.poster,
                                meta.background,
                                null,
                                null,
                                null,
                                null,
                                meta.description,
                                movieProgress?.lastPositionMs,
                            )
                        }
                    }
                }
                val manualPlayHandler = onPlayManually
                val showManualPlayOption = manualPlayHandler != null && StreamAutoPlayPolicy.isEffectivelyEnabled(playerSettingsUiState)
                // Opens the source list for what the primary button would play.
                val openPrimarySources: (() -> Unit)? = manualPlayHandler
                    ?.takeIf { !shufflePoolEmpty && playbackAvailability.canStream(meta.type, primaryVideoId) }
                    ?.let { manualPlay ->
                        {
                            when {
                                (meta.type == "series" || hasEpisodes) && seriesAction != null -> {
                                    manualPlay(
                                        meta.type,
                                        seriesStreamVideoId ?: seriesAction.videoId,
                                        meta.id,
                                        meta.type,
                                        meta.name,
                                        meta.logo,
                                        meta.poster,
                                        meta.background,
                                        seriesAction.seasonNumber,
                                        seriesAction.episodeNumber,
                                        seriesAction.episodeTitle,
                                        seriesAction.episodeThumbnail,
                                        seriesPauseDescription,
                                        seriesAction.resumePositionMs,
                                    )
                                }

                                else -> {
                                    manualPlay(
                                        meta.type,
                                        meta.id,
                                        meta.id,
                                        meta.type,
                                        meta.name,
                                        meta.logo,
                                        meta.poster,
                                        meta.background,
                                        null,
                                        null,
                                        null,
                                        null,
                                        meta.description,
                                        movieProgress?.lastPositionMs,
                                    )
                                }
                            }
                        }
                    }
                val onPrimaryPlayLongClick: (() -> Unit)? = openPrimarySources.takeIf { showManualPlayOption }
                val onEpisodePlayClick: (MetaVideo) -> Unit = { video ->
                    val season = video.season
                    val episode = video.episode
                    val playbackVideoId = buildPlaybackVideoId(
                        parentMetaId = meta.id,
                        seasonNumber = season,
                        episodeNumber = episode,
                        fallbackVideoId = video.id,
                    )
                    val streamVideoId = video.id.takeIf { it.isNotBlank() } ?: playbackVideoId
                    val savedProgress = watchProgressUiState.progressForVideo(
                        videoId = streamVideoId,
                        parentMetaId = meta.id,
                        seasonNumber = season,
                        episodeNumber = episode,
                    )
                        ?.takeUnless { it.isCompleted }
                    onPlay?.invoke(
                        meta.type,
                        streamVideoId,
                        meta.id,
                        meta.type,
                        meta.name,
                        meta.logo,
                        meta.poster,
                        meta.background,
                        season,
                        episode,
                        video.title,
                        video.thumbnail,
                        video.overview,
                        savedProgress?.lastPositionMs,
                    )
                }
                val onEpisodeManualPlayClick: (MetaVideo) -> Unit = { video ->
                    val season = video.season
                    val episode = video.episode
                    val playbackVideoId = buildPlaybackVideoId(
                        parentMetaId = meta.id,
                        seasonNumber = season,
                        episodeNumber = episode,
                        fallbackVideoId = video.id,
                    )
                    val streamVideoId = video.id.takeIf { it.isNotBlank() } ?: playbackVideoId
                    val savedProgress = watchProgressUiState.progressForVideo(
                        videoId = streamVideoId,
                        parentMetaId = meta.id,
                        seasonNumber = season,
                        episodeNumber = episode,
                    )
                        ?.takeUnless { it.isCompleted }
                    onPlayManually?.invoke(
                        meta.type,
                        streamVideoId,
                        meta.id,
                        meta.type,
                        meta.name,
                        meta.logo,
                        meta.poster,
                        meta.background,
                        season,
                        episode,
                        video.title,
                        video.thumbnail,
                        video.overview,
                        savedProgress?.lastPositionMs,
                    )
                }
                if (showShuffle && showShuffleButton) {
                    EpisodeShuffleSheet(
                        meta = meta,
                        settings = shuffleSettings,
                        onSave = saveShuffle,
                        watchedKeys = watchedUiState.watchedKeys,
                        progressEntries = watchProgressUiState.entries,
                        blurUnwatchedEpisodes = metaScreenSettingsUiState.blurUnwatchedEpisodes,
                        onDismiss = { showShuffle = false },
                        onPlay = onEpisodePlayClick,
                        onPlayManually = onEpisodeManualPlayClick.takeIf { showManualPlayOption },
                        onStartFromBeginning = { video ->
                            onPlay?.invoke(meta.type, video.id, meta.id, meta.type, meta.name, meta.logo,
                                meta.poster, meta.background, video.season, video.episode, video.title,
                                video.thumbnail, video.overview, 0L)
                        },
                    )
                }
                val listState = rememberLazyListState()
                val heroStretchState = rememberHeroStretchState(listState)
                val density = LocalDensity.current
                val safeAreaTopPx = with(density) {
                    WindowInsets.statusBars
                        .asPaddingValues()
                        .calculateTopPadding()
                        .toPx()
                }
                val heroHeightPx = rememberSaveable(meta.id) { mutableIntStateOf(0) }
                // Keep pixel-by-pixel list state reads out of this composition.
                val detailScrollOffsetPx = remember(listState, heroHeightPx) {
                    {
                        if (listState.firstVisibleItemIndex == 0) {
                            listState.firstVisibleItemScrollOffset.toFloat()
                        } else {
                            heroHeightPx.intValue.toFloat() + listState.firstVisibleItemScrollOffset
                        }
                    }
                }
                val heroScrollOffset = remember(detailScrollOffsetPx) {
                    { detailScrollOffsetPx().toInt() }
                }
                val isHeroCollapsed = remember(listState, heroHeightPx, safeAreaTopPx) {
                    derivedStateOf {
                        if (listState.firstVisibleItemIndex > 0) {
                            true
                        } else {
                            val measuredHeroHeightPx = heroHeightPx.intValue
                            val thresholdPx = (measuredHeroHeightPx - safeAreaTopPx).coerceAtLeast(0f)
                            measuredHeroHeightPx > 0 && detailScrollOffsetPx() > thresholdPx
                        }
                    }
                }
                val heroTrailerSourceUrl = (desktopHeroAttempt?.rendition?.videoUrl ?: heroTrailerPlaybackSource?.videoUrl)
                    ?.takeIf { it.isNotBlank() && heroTrailerPlaybackEnabled && !heroTrailerFinished && !isLeavingDetails }
                val heroTrailerSourceAudioUrl = (desktopHeroAttempt?.rendition?.audioUrl ?: heroTrailerPlaybackSource?.audioUrl)
                    ?.takeIf { heroTrailerSourceUrl != null && it.isNotBlank() }
                val heroTrailerPlayWhenReady = heroTrailerSourceUrl != null &&
                    selectedTrailer == null &&
                    !isLeavingDetails &&
                    !isHeroCollapsed.value
                val attempt = desktopHeroAttempt
                val desktopTrailer = rememberDesktopHeroTrailer(
                    preview = if (attempt != null && heroTrailerPlaybackEnabled && !isLeavingDetails &&
                        (heroTrailerSourceUrl != null || fadingDesktopAttempt === attempt)
                    ) {
                        DesktopHeroTrailerPreview(
                            rendition = attempt.rendition,
                            title = meta.name,
                            watchUrl = attempt.trailer.youtubePlaybackUrl(),
                            visible = heroTrailerReady && !isHeroCollapsed.value,
                            attemptIdentity = attempt,
                        )
                    } else {
                        null
                    },
                    playWhenReady = heroTrailerPlayWhenReady,
                    muted = heroTrailerMuted,
                    onReady = {
                        if (attempt != null && desktopHeroAttempt === attempt && !isLeavingDetails && attempt.markReady()) {
                            heroTrailerReady = true
                        }
                    },
                    onTerminal = { end ->
                        if (attempt != null && desktopHeroAttempt === attempt && !isLeavingDetails) {
                            attempt.finish(end)?.let { result ->
                                heroTrailerReady = false
                                heroTrailerFinished = true
                                if (result == DesktopHeroTrailerEnd.Completed) {
                                    completedDesktopTrailer = attempt.trailer
                                    fadingDesktopAttempt = attempt
                                }
                            }
                        }
                    },
                )
                val headerTarget = if (isHeroCollapsed.value) 1f else 0f
                val headerProgressState = animateFloatAsState(
                    targetValue = headerTarget,
                    animationSpec = tween(
                        durationMillis = if (headerTarget > 0f) 150 else 100,
                        easing = LinearOutSlowInEasing,
                    ),
                    label = "detail_floating_header_progress",
                )
                val headerProgressProvider = remember(headerProgressState) {
                    { headerProgressState.value }
                }
                val animatedShowHeroBackButton by remember(headerProgressState) {
                    derivedStateOf { headerProgressState.value <= 0.05f }
                }
                val showHeroBackButton = if (isDesktop) {
                    !isHeroCollapsed.value
                } else {
                    animatedShowHeroBackButton
                }
                val headerInteractive by remember(headerProgressState) {
                    derivedStateOf { headerProgressState.value > 0.05f }
                }

                val onShuffleClick: (() -> Unit)? = if (showShuffleButton) {
                    {
                        if (shuffleSettings.enabled) saveShuffle(shuffleSettings.copy(enabled = false)) else showShuffle = true
                    }
                } else {
                    null
                }

                // ---- Watch it your way: what the primary button would play, sent to a TV or a party.
                val yourWayIsSeries = (meta.type == "series" || hasEpisodes) && seriesAction != null
                val yourWayVideoId = if (yourWayIsSeries) seriesStreamVideoId ?: seriesAction?.videoId else meta.id
                val yourWayProgress = if (yourWayIsSeries) {
                    watchProgressUiState.progressForVideo(
                        videoId = yourWayVideoId ?: meta.id,
                        parentMetaId = meta.id,
                        seasonNumber = seriesAction?.seasonNumber,
                        episodeNumber = seriesAction?.episodeNumber,
                    )?.takeUnless { it.isCompleted }
                } else {
                    movieProgress?.takeUnless { it.isCompleted }
                }
                val yourWayRuntime = yourWayVideoId?.let { id -> meta.videos.firstOrNull { it.id == id }?.runtime }
                    ?: parseRuntimeMinutes(meta.runtime)
                val tvAccount = authState as? AuthState.Authenticated
                val tvHandoffState = rememberUpdatedState(
                    Triple(yourWayVideoId, yourWayProgress, yourWayRuntime),
                )
                val detailTvHandoff = remember(meta.id, onManagePairedTvs, detailsScope) {
                    onManagePairedTvs?.let { manage ->
                        DetailTvHandoff(detailsScope, manage) { target, sender, accountId ->
                            val (videoId, progress, runtime) = tvHandoffState.value
                            val action = seriesActionState.value
                            detailsTvHandoffRequest(
                                senderDeviceId = sender,
                                targetDeviceId = target,
                                accountId = accountId,
                                profileId = ProfileRepository.activeProfileId,
                                isSeries = action != null,
                                contentId = meta.id,
                                videoId = videoId ?: meta.id,
                                title = meta.name,
                                season = action?.seasonNumber,
                                episode = action?.episodeNumber,
                                savedPositionMs = progress?.lastPositionMs,
                                savedDurationMs = progress?.durationMs,
                                runtimeMinutes = runtime,
                                nowEpochMs = WatchProgressClock.nowEpochMs(),
                            )
                        }
                    }
                }?.takeIf {
                    tvHandoffSupported && tvAccount != null && !tvAccount.isAnonymous && yourWayVideoId != null &&
                        ((yourWayProgress?.durationMs ?: 0L) > 0L || (yourWayRuntime ?: 0) > 0)
                }
                // Download: the Sources sheet in download mode for the up-next episode or the film.
                val downloadYourWay: (() -> Unit)? = onDownload?.takeIf { yourWayVideoId != null && playbackAvailability.canStream(meta.type, yourWayVideoId) }?.let { download ->
                    {
                        val action = seriesActionState.value
                        download(
                            meta.type,
                            yourWayVideoId ?: meta.id,
                            meta.id,
                            meta.type,
                            meta.name,
                            meta.logo,
                            meta.poster,
                            meta.background,
                            action?.seasonNumber,
                            action?.episodeNumber,
                            action?.episodeTitle,
                            action?.episodeThumbnail,
                            if (action != null) seriesPauseDescription else meta.description,
                            null,
                        )
                    }
                }
                // More like this, unwatched: the pool loads once per title and Trakt connection; until it
                // arrives the title's own recommendations stand in, filtered by local watch history.
                val traktConnectedForPool = traktAuthUiState.mode == TraktConnectionMode.CONNECTED
                var recommendationPool by remember(meta.id, traktConnectedForPool) { mutableStateOf<RecommendationPool?>(null) }
                LaunchedEffect(meta.id, traktConnectedForPool, metaScreenSettingsUiState.moreLikeThisUnwatchedOnly, isDesktop) {
                    if (!isDesktop || !metaScreenSettingsUiState.moreLikeThisUnwatchedOnly || recommendationPool != null) return@LaunchedEffect
                    recommendationPool = runCatching {
                        UnwatchedRecommendations.load(meta, traktConnectedForPool, watchedUiState.watchedKeys, ProfileRepository.activeProfileId)
                    }.getOrNull()
                }
                val moreLikeThisUnwatched = remember(recommendationPool, meta, watchedUiState.watchedKeys) {
                    val pool = recommendationPool ?: RecommendationPool(meta.moreLikeThis, setOfNotNull(meta.imdbId), emptySet())
                    UnwatchedRecommendations.unwatched(pool, meta, watchedUiState.watchedKeys)
                }
                // Reviews come from the reviews source (Settings → Tracking), whichever tracker that is.
                val connectedTrackers by TrackingProviderRegistry.connectedProviderIds.collectAsStateWithLifecycle()
                val registeredReviewSources by TrackingComments.registered.collectAsStateWithLifecycle()
                val boardReviewsProvider = remember(trackingSettingsUiState.reviewsSource, connectedTrackers, registeredReviewSources, commentsEnabled, meta.type) {
                    TrackingComments.provider(trackingSettingsUiState.reviewsSource, connectedTrackers)
                        ?.takeIf { commentsEnabled && meta.type.lowercase() in setOf("movie", "series", "show", "tv") }
                }
                // Gallery: TMDB images, episode stills and (with a key) Kinopoisk stills, once per title.
                var galleryImages by remember(meta.id) { mutableStateOf<List<GalleryImage>>(emptyList()) }
                val kinopoiskKey by remember { KinopoiskSettingsRepository.ensureLoaded(); KinopoiskSettingsRepository.apiKey }.collectAsStateWithLifecycle()
                LaunchedEffect(meta.id, meta.videos.size, kinopoiskKey, isDesktop) {
                    if (isDesktop) galleryImages = DetailGalleryRepository.load(meta)
                }
                // Behind the film: Wikidata by IMDb id, once per title; nothing shows until it answers.
                var behindTheFilm by remember(meta.imdbId) { mutableStateOf<BehindTheFilm?>(null) }
                LaunchedEffect(meta.imdbId, isDesktop) {
                    val imdb = meta.imdbId ?: meta.id.takeIf { it.startsWith("tt") } ?: return@LaunchedEffect
                    if (isDesktop) behindTheFilm = BehindTheFilmRepository.load(imdb)
                }
                val partyAvailable by PartyRepository.available.collectAsStateWithLifecycle()
                val startWatchParty: (() -> Unit)? = onOpenParty?.takeIf { partyAvailable && PartyRepository.canUse && yourWayVideoId != null }?.let { openParty ->
                    {
                        detailsScope.launch {
                            // An open party is reopened, never replaced by a second one.
                            PartyRepository.state.value.party?.let { openParty(it.code); return@launch }
                            val action = seriesActionState.value
                            val error = PartyRepository.create(
                                PartyMedia(
                                    type = meta.type,
                                    metaId = meta.id,
                                    metaType = meta.type,
                                    videoId = yourWayVideoId,
                                    season = action?.seasonNumber,
                                    episode = action?.episodeNumber,
                                    title = meta.name,
                                    episodeTitle = action?.episodeTitle,
                                    description = (if (action != null) seriesPauseDescription else meta.description)?.take(500),
                                    poster = meta.poster,
                                    background = meta.background,
                                    logo = meta.logo,
                                ),
                            )
                            when {
                                error != null -> NuvioToastController.show(getString(error.messageRes))
                                else -> PartyRepository.state.value.party?.let { openParty(it.code) }
                            }
                        }
                    }
                }

                val sectionArgs = DetailSectionArgs(
                    isMdbListActive = mdbListSettings.isActive,
                    meta = meta,
                    playButtonLabel = playButtonLabel,
                    isPrimaryPlayEnabled = isPrimaryPlayEnabled,
                    isSaved = isSaved,
                    isWatched = isWatched,
                    onPrimaryPlayClick = onPrimaryPlayClick,
                    onShuffleClick = if (showShuffleButton) ({
                        if (shuffleSettings.enabled) saveShuffle(shuffleSettings.copy(enabled = false)) else showShuffle = true
                    }) else null,
                    shuffleEnabled = shuffleSettings.enabled,
                    onPrimaryPlayLongClick = onPrimaryPlayLongClick,
                    onSaveClick = toggleSaved,
                    onSaveLongClick = openLibraryListPicker,
                    onWatchedClick = toggleWatched,
                    showManualPlayOption = showManualPlayOption,
                    preferredEpisodeSeasonNumber = seriesAction?.seasonNumber,
                    preferredEpisodeNumber = seriesAction?.episodeNumber,
                    hasProductionSection = hasProductionSection,
                    hasTrailersSection = hasTrailersSection,
                    hasEpisodes = hasEpisodes,
                    hasAdditionalInfoSection = hasAdditionalInfoSection,
                    hasCollectionSection = hasCollectionSection,
                    hasMoreLikeThisSection = hasMoreLikeThisSection,
                    shouldShowComments = shouldShowComments,
                    comments = comments,
                    isCommentsLoading = isCommentsLoading,
                    isCommentsLoadingMore = isCommentsLoadingMore,
                    commentsCurrentPage = commentsCurrentPage,
                    commentsPageCount = commentsPageCount,
                    commentsError = commentsError,
                    episodeImdbRatings = episodeImdbRatings,
                    episodeListGroupedEpisodes = episodeListGroupedEpisodes,
                    episodeListSeasons = episodeListSeasons,
                    episodeListCurrentSeason = currentEpisodeListSeason,
                    onEpisodeListSeasonSelect = { selectedEpisodeListSeason = it },
                    detailListState = listState,
                    seriesProgress = seasonProgress,
                    audienceState = audienceState,
                    onRetryComments = {
                        detailsScope.launch {
                            isCommentsLoading = true
                            commentsError = null
                            try {
                                val result = TraktCommentsRepository.getCommentsPage(meta, page = 1, forceRefresh = true)
                                comments = result.items
                                commentsCurrentPage = result.currentPage
                                commentsPageCount = result.pageCount
                            } catch (e: Exception) {
                                commentsError = e.message ?: getString(Res.string.details_comments_load_failed)
                            }
                            isCommentsLoading = false
                        }
                    },
                    onLoadMoreComments = {
                        detailsScope.launch {
                            isCommentsLoadingMore = true
                            try {
                                val nextPage = commentsCurrentPage + 1
                                val result = TraktCommentsRepository.getCommentsPage(meta, page = nextPage)
                                val existingIds = comments.map { it.id }.toSet()
                                val newComments = result.items.filter { it.id !in existingIds }
                                comments = comments + newComments
                                commentsCurrentPage = result.currentPage
                                commentsPageCount = result.pageCount
                            } catch (_: Exception) { }
                            isCommentsLoadingMore = false
                        }
                    },
                    onCommentClick = { review -> selectedComment = review },
                    onTrailerClick = resolveTrailer,
                    progressByVideoId = progressByVideoId,
                    watchedKeys = watchedUiState.watchedKeys,
                    fullyWatchedSeriesKeys = fullyWatchedSeriesKeys,
                    blurUnwatchedEpisodes = metaScreenSettingsUiState.blurUnwatchedEpisodes,
                    onEpisodeClick = onEpisodePlayClick,
                    onEpisodeLongPress = { video ->
                        selectedEpisodeZoomAnchor = PosterZoomAnchorHolder.consume()
                        selectedEpisodeForActions = video
                    },
                    onSeasonLongPress = { season -> selectedSeasonForActions = season },
                    onOpenMeta = onOpenMeta,
                    onCastClick = onCastClick,
                    onCompanyClick = onCompanyClick,
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = animatedVisibilityScope,
                    tvHandoff = detailTvHandoff,
                    onWatchPartyClick = startWatchParty,
                    onDownloadClick = downloadYourWay,
                )

                BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(start = boardStartInset)) {
                    val colorScheme = MaterialTheme.colorScheme
                    val screenMaxWidth = maxWidth
                    val useTabletLayout = !isDesktop && minOf(maxWidth, maxHeight) >= 600.dp
                    val isTablet = useTabletLayout || screenMaxWidth >= 720.dp
                    val isSectionEnabled = { key: MetaScreenSectionKey ->
                        metaScreenSettingsUiState.items.any { it.key == key && it.enabled }
                    }
                    val useDesktopDetailLayout = isDesktop && screenMaxWidth >= 1000.dp
                    // The board is the wide desktop layout.
                    val useBoard = boardLayout
                    val viewportHeight = maxHeight
                    val screenHeight = maxHeight
                    val desktopPageHorizontalPadding = desktopPageHorizontalPaddingForWidth(screenMaxWidth.value)
                    val contentHorizontalPadding = if (isDesktop) {
                        desktopPageHorizontalPadding
                    } else if (isTablet) {
                        32.dp
                    } else {
                        18.dp
                    }
                    val contentMaxWidth = detailTabletContentMaxWidth(screenMaxWidth, isTablet)
                    val backdropUrl = meta.background?.takeIf { it.isNotBlank() } ?: meta.poster
                    val backgroundMode = metaScreenSettingsUiState.backgroundMode
                    val cinematicBackground = backgroundMode == MetaScreenBackgroundMode.Cinematic &&
                        !backdropUrl.isNullOrBlank()
                    val cinematicBackdrop = rememberArtworkBackdrop(
                        imageUrl = backdropUrl.takeIf { cinematicBackground },
                        enabled = deferredMetaWorkAllowed,
                    )
                    val dominantColorEnabled = backgroundMode == MetaScreenBackgroundMode.DominantColor &&
                        deferredMetaWorkAllowed &&
                        !backdropUrl.isNullOrBlank()
                    val adaptiveScrollbarColorEnabled = useDesktopDetailLayout &&
                        deferredMetaWorkAllowed &&
                        !backdropUrl.isNullOrBlank()
                    val backdropColorExtractionEnabled = dominantColorEnabled || adaptiveScrollbarColorEnabled
                    var dominantBackdropPainter by remember(meta.id, backdropUrl) {
                        mutableStateOf<Painter?>(null)
                    }
                    var dominantBackdropImageBitmap by remember(meta.id, backdropUrl) {
                        mutableStateOf<ImageBitmap?>(null)
                    }
                    val dominantImageBitmapColorState = rememberDominantColorState(
                        defaultColor = colorScheme.background,
                        defaultOnColor = colorScheme.onBackground,
                    )
                    val dominantPainterColorState = rememberPainterDominantColorState(
                        defaultColor = colorScheme.background,
                        defaultOnColor = colorScheme.onBackground,
                    )
                    LaunchedEffect(backdropColorExtractionEnabled, dominantBackdropImageBitmap, dominantBackdropPainter) {
                        val imageBitmap = dominantBackdropImageBitmap
                        val painter = dominantBackdropPainter
                        if (backdropColorExtractionEnabled) {
                            when {
                                imageBitmap != null -> runCatching {
                                    dominantImageBitmapColorState.updateFrom(imageBitmap)
                                }
                                painter != null -> runCatching {
                                    dominantPainterColorState.updateFrom(painter)
                                }
                            }
                        }
                    }
                    val extractedDominantColor = if (dominantBackdropImageBitmap != null) {
                        dominantImageBitmapColorState.color
                    } else {
                        dominantPainterColorState.color
                    }
                    val dominantBackdropTargetColor = when {
                        dominantColorEnabled ->
                            dominantBackdropBlendColor(extractedDominantColor, colorScheme.background)
                        else -> colorScheme.background
                    }
                    val dominantBackdropColor by animateColorAsState(
                        targetValue = dominantBackdropTargetColor,
                        animationSpec = tween(
                            durationMillis = 320,
                            easing = LinearOutSlowInEasing,
                        ),
                        label = "detail_dominant_backdrop_color",
                    )

                    // ---- Desktop board state
                    val hasBoardStats = useBoard && meta.type == "series" && currentEpisodeListSeason != null
                    // Tab visibility must see the board-only inputs (gallery, reviews source), which the
                    // shared sectionArgs leave out; the board's own args are built further down.
                    val boardContentArgs = remember(sectionArgs, galleryImages, boardReviewsProvider, behindTheFilm) {
                        sectionArgs.copy(
                            boardStyle = true,
                            gallery = galleryImages,
                            reviewsProvider = boardReviewsProvider,
                            behindTheFilm = behindTheFilm,
                        )
                    }
                    val boardTabs = remember(metaScreenSettingsUiState.items, boardContentArgs, hasBoardStats) {
                        detailBoardTabs(
                            enabledSections = metaScreenSettingsUiState.items.filter { it.enabled }.map { it.key },
                            hasContent = boardContentArgs::hasContent,
                            hasStats = hasBoardStats,
                        )
                    }
                    var selectedBoardTabName by rememberSaveable(meta.id) { mutableStateOf<String?>(null) }
                    val selectedBoardTab = boardTabs.firstOrNull { it.name == selectedBoardTabName }
                        ?: boardTabs.firstOrNull()
                        ?: DetailBoardTab.Details
                    var boardExpanded by remember(meta.id) { mutableStateOf(false) }
                    val boardTrailerShown = heroTrailerSourceUrl != null
                    // A picked trailer plays in the expanded tile: the page glides to the top, and closing
                    // (collapse, Esc, or the clip ending) glides back to where it was picked.
                    var boardPickedTrailer by remember(meta.id) { mutableStateOf<MetaTrailer?>(null) }
                    var boardReturnScroll by remember(meta.id) { mutableStateOf<Pair<Int, Int>?>(null) }
                    val boardScope = rememberCoroutineScope()
                    val boardTrailerPlay: (MetaTrailer) -> Unit = { trailer ->
                        if (!heroTrailerPlaybackAvailable) {
                            // External playback (or no in-app hero player): open the trailer as before.
                            resolveTrailer(trailer)
                        } else {
                            heroTrailerPicked = true
                            if (boardPickedTrailer == null) {
                                boardReturnScroll = listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
                            }
                            boardPickedTrailer = trailer
                            pickedTrailerFailed = null
                            replayDesktopTrailer = trailer
                            replayCount++
                            boardExpanded = true
                            if (heroTrailerMuted) HeroTrailerAudioState.toggleMuted()
                            boardScope.launch { listState.animateScrollToItem(0) }
                        }
                    }
                    // The tile folds when its trailer goes, but not while a picked one is still starting.
                    // A pick that failed keeps the tile open with its message until closed.
                    LaunchedEffect(boardTrailerShown, heroTrailerFinished, pickedTrailerFailed) {
                        if (!boardTrailerShown && pickedTrailerFailed == null && (boardPickedTrailer == null || heroTrailerFinished)) {
                            boardExpanded = false
                        }
                    }
                    LaunchedEffect(boardExpanded) {
                        if (boardExpanded || boardPickedTrailer == null) return@LaunchedEffect
                        boardPickedTrailer = null
                        pickedTrailerFailed = null
                        heroTrailerPicked = false
                        boardReturnScroll?.let { (index, offset) -> listState.animateScrollToItem(index, offset) }
                        boardReturnScroll = null
                    }
                    if (useBoard && boardExpanded) OverlayEscapeHandler { boardExpanded = false }
                    val boardPinned = remember(listState) { derivedStateOf { listState.firstVisibleItemIndex >= 1 } }
                    var boardKeepPinned by remember(meta.id) { mutableStateOf(false) }
                    LaunchedEffect(selectedBoardTab) {
                        if (!boardKeepPinned) return@LaunchedEffect
                        // The new tab's height (and the filler under a short one) settles over a few frames.
                        repeat(4) {
                            listState.scrollToItem(1)
                            withFrameNanos { }
                        }
                        boardKeepPinned = false
                    }
                    if (useBoard && detailTopBarState != null) {
                        // Content never runs under the bar here, but once the board scrolls the bar takes its
                        // pinned surface so it and the tabs row read as one band.
                        LaunchedEffect(listState, detailTopBarState) {
                            snapshotFlow { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0 }
                                .collect { detailTopBarState.scrolled = it }
                        }
                        LaunchedEffect(detailTopBarState, boardBackdrop?.tint) { detailTopBarState.tint = boardBackdrop?.tint }
                    }
                    val boardUpNext = if (useBoard && seriesAction != null && !shufflePoolEmpty) {
                        val entry = watchProgressUiState.progressForVideo(
                            videoId = seriesStreamVideoId ?: seriesAction.videoId,
                            parentMetaId = meta.id,
                            seasonNumber = seriesAction.seasonNumber,
                            episodeNumber = seriesAction.episodeNumber,
                        )?.takeUnless { it.isCompleted }
                        val code = if (seriesAction.seasonNumber != null && seriesAction.episodeNumber != null) {
                            stringResource(Res.string.detail_board_episode_code, seriesAction.seasonNumber, seriesAction.episodeNumber)
                        } else {
                            null
                        }
                        val minutesLeft = entry?.takeIf { it.durationMs > it.lastPositionMs && it.lastPositionMs > 0L }?.let {
                            ((it.durationMs - it.lastPositionMs) / 60_000L).toInt().coerceAtLeast(1)
                        }
                        val left = minutesLeft?.let { stringResource(Res.string.detail_board_minutes_left, it) }
                        DetailBoardUpNext(
                            kicker = listOfNotNull(stringResource(Res.string.detail_board_up_next), code, left).joinToString(" · "),
                            title = seriesAction.episodeTitle?.takeIf { it.isNotBlank() } ?: seriesActionVideo?.title ?: seriesAction.label,
                            artwork = seriesAction.episodeThumbnail?.takeIf { it.isNotBlank() }
                                ?: seriesActionVideo?.thumbnail
                                ?: meta.background
                                ?: meta.poster,
                            progressFraction = entry?.takeIf { it.durationMs > 0L }?.let { it.lastPositionMs.toFloat() / it.durationMs },
                            // The same verb as the primary button, which decides between resuming and playing.
                            actionLabel = if (seriesAction.label.contains(resumeText, ignoreCase = true)) resumeText else playText,
                            episode = seriesAction.seasonNumber?.let { season -> seriesAction.episodeNumber?.let { season to it } },
                            minutesLeft = minutesLeft,
                            runtimeMinutes = seriesActionVideo?.runtime?.takeIf { it > 0 },
                        )
                    } else {
                        null
                    }
                    val boardProgress = remember(seasonProgress, currentEpisodeListSeason) {
                        seasonProgress?.let { detailBoardProgress(it, currentEpisodeListSeason) }
                    }
                    // ---- Quick pick: choose how to play from the board, with the full sheet one click away.
                    var quickPickOpen by remember(meta.id, primaryVideoId) { mutableStateOf(false) }
                    var boardPlayAnchor by remember { mutableStateOf<Rect?>(null) }
                    var pinnedPlayAnchor by remember { mutableStateOf<Rect?>(null) }
                    val quickPickRequest = remember(meta.id, meta.type, primaryVideoId, seriesAction, meta.name) {
                        val code = seriesAction?.takeIf { it.seasonNumber != null && it.episodeNumber != null }
                            ?.let { "S${it.seasonNumber} E${it.episodeNumber}" }
                        StreamQuickPickRequest(
                            type = meta.type,
                            videoId = primaryVideoId,
                            parentMetaId = meta.id,
                            seasonNumber = seriesAction?.seasonNumber,
                            episodeNumber = seriesAction?.episodeNumber,
                            label = listOfNotNull(code, seriesAction?.episodeTitle?.takeIf { it.isNotBlank() }).joinToString(" · ")
                                .ifBlank { meta.name },
                            resumeLine = null,
                        )
                    }
                    // A downloaded copy still plays straight away, as the primary button always did for it.
                    val hasPlayableDownload = remember(meta.id, primaryVideoId, seriesAction) {
                        AppFeaturePolicy.downloadsEnabled && com.nuvio.app.features.downloads.DownloadsRepository.findPlayableDownload(
                            parentMetaId = meta.id,
                            seasonNumber = seriesAction?.seasonNumber,
                            episodeNumber = seriesAction?.episodeNumber,
                            videoId = primaryVideoId,
                        ) != null
                    }
                    val openQuickPick: (() -> Unit)? = openPrimarySources?.takeIf { useBoard }?.let { { quickPickOpen = true } }
                    // An episode card opens its own Quick pick beside the card, under the same rules as
                    // Resume: autoplay on plays, a downloaded copy plays, otherwise choose a source here.
                    var episodePick by remember(meta.id) { mutableStateOf<EpisodeQuickPick?>(null) }
                    val resumeTemplate = stringResource(Res.string.streams_resume_from_time, "{t}")
                    var openEpisodeQuickPick: (MetaVideo, Rect?) -> Unit = { _, _ -> }
                    val onBoardEpisodeClick: (MetaVideo, Rect?) -> Unit = { video, anchor ->
                        val hasDownload = AppFeaturePolicy.downloadsEnabled && com.nuvio.app.features.downloads.DownloadsRepository.findPlayableDownload(
                            parentMetaId = meta.id,
                            seasonNumber = video.season,
                            episodeNumber = video.episode,
                            videoId = video.id,
                        ) != null
                        if (showManualPlayOption || hasDownload || onPlayManually == null) {
                            onEpisodePlayClick(video)
                        } else {
                            openEpisodeQuickPick(video, anchor)
                        }
                    }
                    val primaryResumeLine = quickPickResumeLine(yourWayProgress, resumeTemplate)
                    fun openEpisodeQuickPickImpl(video: MetaVideo, anchor: Rect?) {
                        run {
                            val videoId = video.id.takeIf { it.isNotBlank() } ?: buildPlaybackVideoId(meta.id, video.season, video.episode, video.id)
                            val entry = watchProgressUiState.progressForVideo(videoId = videoId, parentMetaId = meta.id, seasonNumber = video.season, episodeNumber = video.episode)
                                ?.takeUnless { it.isCompleted }
                            val code = if (video.season != null && video.episode != null) "S${video.season} E${video.episode}" else null
                            episodePick = EpisodeQuickPick(
                                video = video,
                                videoId = videoId,
                                anchor = anchor,
                                request = StreamQuickPickRequest(
                                    type = meta.type,
                                    videoId = videoId,
                                    parentMetaId = meta.id,
                                    seasonNumber = video.season,
                                    episodeNumber = video.episode,
                                    label = listOfNotNull(code, video.title.takeIf { it.isNotBlank() }).joinToString(" · ").ifBlank { meta.name },
                                    resumeLine = quickPickResumeLine(entry, resumeTemplate),
                                ),
                            )
                        }
                    }
                    openEpisodeQuickPick = ::openEpisodeQuickPickImpl
                    // With autoplay on, Play keeps starting playback and choosing a source opens Quick pick.
                    // With autoplay off, Play itself opens Quick pick in place of the full list.
                    // Built on boardContentArgs, so the tabs shown and the tabs' content read the same inputs.
                    val boardSectionArgs = (if (openQuickPick == null) boardContentArgs else boardContentArgs.copy(
                        onPrimaryPlayClick = if (showManualPlayOption || hasPlayableDownload) onPrimaryPlayClick else openQuickPick,
                        onPrimaryPlayLongClick = openQuickPick.takeIf { showManualPlayOption },
                    )).copy(
                        onEpisodeCardStyleChange = MetaScreenSettingsRepository::setEpisodeCardStyle,
                        onEpisodeClickAt = onBoardEpisodeClick,
                        onEpisodeMenuAt = { video, anchor ->
                            episodeMenuAnchor = anchor
                            selectedEpisodeForActions = video
                        },
                        moreLikeThisUnwatched = moreLikeThisUnwatched,
                        moreLikeThisUnwatchedOnly = metaScreenSettingsUiState.moreLikeThisUnwatchedOnly,
                        onMoreLikeThisUnwatchedOnlyChange = MetaScreenSettingsRepository::setMoreLikeThisUnwatchedOnly,
                        onSetBackground = { url -> boardBackgroundOverride = url },
                        onTrailerClick = boardTrailerPlay,
                        boardPlayingTrailer = boardPickedTrailer?.takeIf { boardExpanded && pickedTrailerFailed == null },
                    )
                    val boardArgs = DetailBoardArgs(
                        onPlayAnchor = { boardPlayAnchor = it },
                        onPinnedPlayAnchor = { pinnedPlayAnchor = it },
                        horizontalPadding = desktopPageHorizontalPadding,
                        tabs = boardTabs,
                        selectedTab = selectedBoardTab,
                        onSelectTab = { tab ->
                            // A tab picked from the pinned row opens at its top, with the row still pinned.
                            boardKeepPinned = boardPinned.value
                            selectedBoardTabName = tab.name
                        },
                        pinned = { boardPinned.value },
                        expanded = boardExpanded,
                        expandedTitle = boardPickedTrailer?.let { it.displayName?.takeIf(String::isNotBlank) ?: it.name },
                        expandedFailure = pickedTrailerFailed?.takeIf { boardExpanded }?.let { failed ->
                            DetailBoardTrailerFailure(
                                onOpenExternally = { runCatching { uriHandler.openUri(failed.youtubePlaybackUrl()) } },
                                onClose = { boardExpanded = false },
                            )
                        },
                        onToggleExpanded = {
                            boardExpanded = !boardExpanded
                            if (boardExpanded && heroTrailerMuted) HeroTrailerAudioState.toggleMuted()
                        },
                        trailer = desktopTrailer.takeIf { boardTrailerShown },
                        trailerReady = heroTrailerReady,
                        trailerMuted = heroTrailerMuted,
                        onMuteToggle = { HeroTrailerAudioState.toggleMuted() },
                        upNext = boardUpNext,
                        progress = boardProgress,
                        // Hidden with the Episodes section, which its season rows open.
                        showProgress = meta.type == "series" && DetailBoardTab.Episodes in boardTabs &&
                            (seasonProgress == null || boardProgress != null),
                        movieResumePositionMs = movieProgress?.lastPositionMs,
                        isCollectionItemWatched = { item ->
                            WatchingState.isPosterWatched(
                                watchedKeys = watchedUiState.watchedKeys,
                                item = item,
                                fullyWatchedSeriesKeys = fullyWatchedSeriesKeys,
                            )
                        },
                        showOverallRatings = metaScreenSettingsUiState.showOverallRatings,
                        tint = boardBackdrop?.tint,
                        onBack = onBackFromDetails,
                        onHeightChanged = { heroHeightPx.intValue = it },
                        onArtworkLoaded = { dominantBackdropPainter = it },
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .detailsContentReveal(metaScreenSettingsUiState.posterTransitionEnabled),
                    ) {
                        if (useTabletLayout) {
                            TabletDetailBackdrop(
                                meta = meta,
                                cinematic = cinematicBackground,
                                backdropImage = cinematicBackdrop,
                                scrollOffsetPx = detailScrollOffsetPx,
                                heroHeightPx = { heroHeightPx.intValue },
                                heroTrailerSourceUrl = heroTrailerSourceUrl,
                                heroTrailerSourceAudioUrl = heroTrailerSourceAudioUrl,
                                heroTrailerReady = heroTrailerReady,
                                heroTrailerPlayWhenReady = { heroTrailerPlayWhenReady },
                                heroTrailerMuted = heroTrailerMuted,
                                heroGradientColor = dominantBackdropColor.takeIf { dominantColorEnabled },
                                onBackdropLoaded = { painter, imageBitmap ->
                                    dominantBackdropPainter = painter
                                    dominantBackdropImageBitmap = imageBitmap
                                },
                                onHeroTrailerReady = {
                                    if (!heroTrailerFinished) heroTrailerReady = true
                                },
                                onHeroTrailerFinished = {
                                    heroTrailerReady = false
                                    heroTrailerFinished = true
                                },
                            )
                        } else if (!useBoard) when (backgroundMode) {
                            MetaScreenBackgroundMode.Normal -> Unit
                            MetaScreenBackgroundMode.Cinematic -> if (cinematicBackground && !useDesktopDetailLayout) {
                                ArtworkBackdrop(image = cinematicBackdrop)
                            }
                            MetaScreenBackgroundMode.DominantColor -> if (deferredMetaWorkAllowed) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(dominantBackdropColor),
                                )
                            }
                        }
                        CompositionLocalProvider(
                            LocalOnBackdrop provides (backgroundMode.usesBackdropBackground || (useBoard && boardBackdrop != null)),
                        ) {
                            RedesignTypography(enabled = useBoard) {
                            LazyColumn(
                                state = listState,
                                modifier = Modifier
                                    // The board's list starts under the top bar, so its tabs pin right below it.
                                    .padding(top = if (useBoard) boardTopPadding else 0.dp)
                                    .fillMaxSize()
                                    .nestedScroll(heroStretchState.nestedScrollConnection)
                                    .zIndex(1f),
                            ) {
                                if (useBoard) {
                                    detailBoardItems(
                                        settings = metaScreenSettingsUiState,
                                        args = boardSectionArgs,
                                        board = boardArgs,
                                    )
                                } else {
                                    item(
                                        key = "detail-hero",
                                        contentType = "detail-hero",
                                    ) {
                                        if (useTabletLayout) {
                                            TabletDetailHero(
                                                meta = meta,
                                                showOverview = isSectionEnabled(MetaScreenSectionKey.OVERVIEW),
                                                showOverallRatings = metaScreenSettingsUiState.showOverallRatings,
                                                isMdbListActive = mdbListSettings.isActive,
                                                horizontalPadding = contentHorizontalPadding,
                                                heroTrailerSourceUrl = heroTrailerSourceUrl,
                                                heroTrailerReady = heroTrailerReady,
                                                heroTrailerMuted = heroTrailerMuted,
                                                onHeroTrailerMuteToggle = HeroTrailerAudioState::toggleMuted,
                                                onHeightChanged = { heroHeightPx.intValue = it },
                                                actions = if (isSectionEnabled(MetaScreenSectionKey.ACTIONS)) {
                                                    {
                                                        DetailActions(
                                                            playLabel = playButtonLabel,
                                                            playEnabled = isPrimaryPlayEnabled,
                                                            isSaved = isSaved,
                                                            isWatched = isWatched,
                                                            isTablet = true,
                                                            shuffleEnabled = shuffleSettings.enabled,
                                                            onPlayClick = onPrimaryPlayClick,
                                                            onPlayLongClick = if (showManualPlayOption) onPrimaryPlayLongClick else null,
                                                            onShuffleClick = onShuffleClick,
                                                            onWatchedClick = toggleWatched,
                                                            onSaveClick = toggleSaved,
                                                            onSaveLongClick = openLibraryListPicker,
                                                        )
                                                    }
                                                } else {
                                                    null
                                                },
                                            )
                                        } else {
                                            DetailHero(
                                                meta = meta,
                                                isTablet = isTablet,
                                                contentMaxWidth = contentMaxWidth,
                                                viewportHeight = viewportHeight,
                                                scrollOffset = heroScrollOffset,
                                                stretchPx = { heroStretchState.stretchPx },
                                                onHeightChanged = { heroHeightPx.intValue = it },
                                                heroTrailerSourceUrl = heroTrailerSourceUrl,
                                                heroTrailerSourceAudioUrl = heroTrailerSourceAudioUrl,
                                                heroTrailerReady = heroTrailerReady,
                                                heroTrailerContent = desktopTrailer,
                                                heroTrailerPlayWhenReady = { heroTrailerPlayWhenReady },
                                                heroTrailerMuted = heroTrailerMuted,
                                                heroGradientColor = dominantBackdropColor.takeIf { dominantColorEnabled },
                                                onBackdropLoaded = { painter, imageBitmap ->
                                                    dominantBackdropPainter = painter
                                                    dominantBackdropImageBitmap = imageBitmap
                                                },
                                                onHeroTrailerMuteToggle = {
                                                    HeroTrailerAudioState.toggleMuted()
                                                },
                                                onHeroTrailerReady = {
                                                    if (!heroTrailerFinished) {
                                                        heroTrailerReady = true
                                                    }
                                                },
                                                onHeroTrailerEnded = {
                                                    heroTrailerReady = false
                                                    heroTrailerFinished = true
                                                },
                                                onHeroTrailerError = {
                                                    heroTrailerReady = false
                                                    heroTrailerFinished = true
                                                },
                                            )
                                        }
                                    }

                                    configuredMetaSectionItems(
                                        settings = if (useTabletLayout) {
                                            metaScreenSettingsUiState.copy(
                                                items = metaScreenSettingsUiState.items.filterNot { it.key in tabletHeroSectionKeys },
                                            )
                                        } else {
                                            metaScreenSettingsUiState
                                        },
                                        args = sectionArgs.copy(
                                            isMdbListActive = mdbListSettings.enabled && mdbListSettings.hasApiKey,
                                            fullyWatchedSeriesKeys = emptySet(),
                                            onEpisodeLongPress = { video -> selectedEpisodeForActions = video },
                                        ),
                                        isTablet = isTablet,
                                        contentHorizontalPadding = contentHorizontalPadding,
                                        contentMaxWidth = if (isTablet && !useTabletLayout) contentMaxWidth else Dp.Unspecified,
                                    )
                                }

                                item(
                                    key = "detail-bottom-spacer",
                                    contentType = "detail-spacer",
                                ) {
                                    Spacer(modifier = Modifier.height(nuvioSafeBottomPadding(32.dp)))
                                }
                            }
                            }
                        }
                        if (useBoard && (openQuickPick != null && openPrimarySources != null || episodePick != null)) {
                            RedesignTypography {
                                val episodeOpen = episodePick
                                DetailQuickPickOverlay(
                                    open = quickPickOpen || episodeOpen != null,
                                    anchor = episodeOpen?.anchor ?: if (boardPinned.value) pinnedPlayAnchor ?: boardPlayAnchor else boardPlayAnchor,
                                    request = episodeOpen?.request ?: quickPickRequest.copy(resumeLine = primaryResumeLine),
                                    onPick = { stream ->
                                        quickPickOpen = false
                                        episodePick = null
                                        if (episodeOpen != null) {
                                            StreamPreselection.set(episodeOpen.videoId, stream)
                                            onEpisodeManualPlayClick(episodeOpen.video)
                                        } else {
                                            StreamPreselection.set(primaryVideoId, stream)
                                            openPrimarySources?.invoke()
                                        }
                                    },
                                    onAllSources = {
                                        quickPickOpen = false
                                        episodePick = null
                                        if (episodeOpen != null) onEpisodeManualPlayClick(episodeOpen.video) else openPrimarySources?.invoke()
                                    },
                                    onDismiss = {
                                        quickPickOpen = false
                                        episodePick = null
                                    },
                                    modifier = Modifier.zIndex(3f),
                                )
                            }
                        }
                        NuvioDesktopVerticalScrollbar(
                            state = listState,
                            backgroundColor = extractedDominantColor.takeIf { adaptiveScrollbarColorEnabled },
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .fillMaxHeight()
                                .padding(vertical = 8.dp, horizontal = 4.dp)
                                .zIndex(2f),
                        )

                        if (!useDesktopDetailLayout && !useTabletLayout && dominantColorEnabled && heroHeightPx.intValue > 0) {
                            val blendColor = dominantBackdropColor.takeIf { dominantColorEnabled }
                                ?: colorScheme.background
                            Box(
                                modifier = Modifier
                                    .zIndex(0.5f)
                                    .fillMaxWidth()
                                    .height(132.dp)
                                    .graphicsLayer {
                                        translationY = heroHeightPx.intValue.toFloat() - detailScrollOffsetPx()
                                    }
                                    .background(
                                        Brush.verticalGradient(
                                            colors = listOf(
                                                blendColor.copy(alpha = 0.98f),
                                                blendColor.copy(alpha = 0.84f),
                                                blendColor.copy(alpha = 0.52f),
                                                Color.Transparent,
                                            ),
                                        ),
                                    ),
                            )
                        }

                        if (!isDesktop && !useDesktopDetailLayout && showHeroBackButton) {
                            NuvioBackButton(
                                onClick = onBackFromDetails,
                                modifier = Modifier.padding(
                                    start = 12.dp,
                                    top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 8.dp,
                                ).zIndex(2f),
                                containerColor = Color.Transparent,
                                contentColor = MaterialTheme.colorScheme.onBackground,
                            )
                        }

                        if (isDesktop && !useBoard) {
                            NuvioBackButton(
                                onClick = onBackFromDetails,
                                modifier = Modifier
                                    .statusBarsPadding()
                                    .padding(start = desktopPageHorizontalPadding, top = 32.dp)
                                    .zIndex(2f),
                                containerColor = Color.Black.copy(alpha = 0.34f),
                                showContainerOnDesktop = true,
                                contentColor = MaterialTheme.colorScheme.onBackground,
                                buttonSize = 48.dp,
                                iconSize = 24.dp,
                            )
                        }

                        if (!isDesktop) {
                            DetailFloatingHeader(
                                meta = meta,
                                isSaved = isSaved,
                                progressProvider = headerProgressProvider,
                                interactive = headerInteractive,
                                backgroundColor = dominantBackdropColor.takeIf { dominantColorEnabled },
                                background = if (cinematicBackground) {
                                    {
                                        CinematicHeaderBackdrop(
                                            image = cinematicBackdrop,
                                            screenHeight = screenHeight,
                                            alignment = if (useTabletLayout) {
                                                TabletArtworkBackdropAlignment
                                            } else {
                                                Alignment.Center
                                            },
                                        )
                                    }
                                } else {
                                    null
                                },
                                onBack = onBackFromDetails,
                                onToggleSaved = toggleSaved,
                                modifier = Modifier.zIndex(2f),
                            )
                        }

                        selectedEpisodeForActions
                            ?.takeIf { selectedEpisodeZoomAnchor == null }
                            ?.let { selectedEpisode ->
                            val isSelectedEpisodeWatched = remember(meta, selectedEpisode, watchedUiState.watchedKeys, progressByVideoId) {
                                isEpisodeWatchedForActions(
                                    meta = meta,
                                    episode = selectedEpisode,
                                    watchedKeys = watchedUiState.watchedKeys,
                                    progressByVideoId = progressByVideoId,
                                )
                            }
                            val previousEpisodes = remember(meta, selectedEpisode, todayIsoDate) {
                                meta.previousReleasedEpisodesBefore(
                                    target = selectedEpisode,
                                    todayIsoDate = todayIsoDate,
                                )
                            }
                            val seasonEpisodes = remember(meta, selectedEpisode, todayIsoDate) {
                                meta.releasedEpisodesForSeason(
                                    seasonNumber = selectedEpisode.season,
                                    todayIsoDate = todayIsoDate,
                                )
                            }
                            val arePreviousEpisodesWatched = remember(previousEpisodes, watchedUiState.watchedKeys, progressByVideoId) {
                                areEpisodesWatchedForActions(
                                    meta = meta,
                                    episodes = previousEpisodes,
                                    watchedKeys = watchedUiState.watchedKeys,
                                    progressByVideoId = progressByVideoId,
                                )
                            }
                            val isSeasonWatched = remember(seasonEpisodes, watchedUiState.watchedKeys, progressByVideoId) {
                                areEpisodesWatchedForActions(
                                    meta = meta,
                                    episodes = seasonEpisodes,
                                    watchedKeys = watchedUiState.watchedKeys,
                                    progressByVideoId = progressByVideoId,
                                )
                            }
                            val episodeSeasonLabel = selectedEpisode.season?.let {
                                stringResource(Res.string.episodes_season, it)
                            } ?: stringResource(Res.string.episodes_specials)
                            if (useBoard) {
                                val episodeRatingMedia = remember(meta, selectedEpisode) { meta.ratingReference(selectedEpisode) }
                                val episodeRating by rememberRatingState(episodeRatingMedia)
                                val code = listOfNotNull(selectedEpisode.season?.let { "S$it" }, selectedEpisode.episode?.let { "E$it" }).joinToString(" ")
                                BoardContextMenu(
                                    title = listOf(code, selectedEpisode.title).filter { it.isNotBlank() }.joinToString(" · "),
                                    anchor = episodeMenuAnchor,
                                    actions = listOfNotNull(
                                        BoardMenuAction(stringResource(Res.string.detail_board_choose_source), Icons.Rounded.Tune, "detail_episode_menu_choose") {
                                            openEpisodeQuickPick(selectedEpisode, episodeMenuAnchor)
                                        }.takeIf { onPlayManually != null },
                                        BoardMenuAction(
                                            stringResource(if (isSelectedEpisodeWatched) Res.string.episode_mark_unwatched else Res.string.episode_mark_watched),
                                            Icons.Rounded.CheckCircleOutline,
                                            "detail_episode_menu_watched",
                                        ) { WatchingActions.toggleEpisodeWatched(meta = meta, episode = selectedEpisode, isCurrentlyWatched = isSelectedEpisodeWatched) },
                                        BoardMenuAction(
                                            stringResource(if (arePreviousEpisodesWatched) Res.string.episode_mark_previous_unwatched else Res.string.episode_mark_previous_watched),
                                            Icons.Rounded.DoneAll,
                                            "detail_episode_menu_previous",
                                        ) { WatchingActions.togglePreviousEpisodesWatched(meta = meta, episodes = previousEpisodes, areCurrentlyWatched = arePreviousEpisodesWatched) }
                                            .takeIf { previousEpisodes.isNotEmpty() },
                                        BoardMenuAction(
                                            stringResource(if (isSeasonWatched) Res.string.episode_mark_season_unwatched else Res.string.episode_mark_season_watched, episodeSeasonLabel),
                                            Icons.AutoMirrored.Rounded.PlaylistAddCheck,
                                            "detail_episode_menu_season",
                                        ) { WatchingActions.toggleSeasonWatched(meta = meta, episodes = seasonEpisodes, areCurrentlyWatched = isSeasonWatched) },
                                    ),
                                    onDismiss = {
                                        selectedEpisodeForActions = null
                                        episodeMenuAnchor = null
                                    },
                                    footer = if (episodeRating.isAvailable) {
                                        {
                                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                                Text(
                                                    stringResource(Res.string.detail_board_rate_episode, code).uppercase(),
                                                    style = redesignText(11.sp, FontWeight.Medium, RedesignColors.textMuted),
                                                    modifier = Modifier.padding(start = 6.dp),
                                                )
                                                BoardRateControl(
                                                    media = episodeRatingMedia,
                                                    rating = episodeRating,
                                                    favorite = TrackingFavoriteState(),
                                                    onOpenChange = {},
                                                    height = 40.dp,
                                                    alwaysOpen = true,
                                                )
                                            }
                                        }
                                    } else null,
                                )
                            } else EpisodeWatchedActionSheet(
                                episode = selectedEpisode,
                                seasonLabel = selectedEpisode.season?.let {
                                    stringResource(Res.string.episodes_season, it)
                                } ?: stringResource(Res.string.episodes_specials),
                                isEpisodeWatched = isSelectedEpisodeWatched,
                                canMarkPreviousEpisodes = previousEpisodes.isNotEmpty(),
                                arePreviousEpisodesWatched = arePreviousEpisodesWatched,
                                isSeasonWatched = isSeasonWatched,
                                onDismiss = { selectedEpisodeForActions = null },
                                onToggleWatched = {
                                    WatchingActions.toggleEpisodeWatched(
                                        meta = meta,
                                        episode = selectedEpisode,
                                        isCurrentlyWatched = isSelectedEpisodeWatched,
                                    )
                                },
                                onTogglePreviousWatched = {
                                    WatchingActions.togglePreviousEpisodesWatched(
                                        meta = meta,
                                        episodes = previousEpisodes,
                                        areCurrentlyWatched = arePreviousEpisodesWatched,
                                    )
                                },
                                onToggleSeasonWatched = {
                                    WatchingActions.toggleSeasonWatched(
                                        meta = meta,
                                        episodes = seasonEpisodes,
                                        areCurrentlyWatched = isSeasonWatched,
                                    )
                                },
                                showPlayManually = showManualPlayOption,
                                onPlayManually = {
                                    onEpisodeManualPlayClick(selectedEpisode)
                                },
                                ratingMedia = remember(meta, selectedEpisode) { meta.ratingReference(selectedEpisode) },
                            )
                        }

                        selectedSeasonForActions?.let { selectedSeason ->
                            val seasonLabel = selectedSeasonLabel(selectedSeason)
                            val seasonEpisodes = remember(meta, selectedSeason, todayIsoDate) {
                                meta.releasedEpisodesForSeason(
                                    seasonNumber = selectedSeason,
                                    todayIsoDate = todayIsoDate,
                                )
                            }
                            val previousSeasonEpisodes = remember(meta, selectedSeason, todayIsoDate) {
                                val normalizedSelectedSeason = selectedSeason.coerceAtLeast(0)
                                meta.releasedPlayableEpisodes(todayIsoDate)
                                    .filter { episode ->
                                        val season = episode.season?.coerceAtLeast(0) ?: 0
                                        season > 0 && season < normalizedSelectedSeason
                                    }
                            }
                            val isSeasonWatched = remember(seasonEpisodes, watchedUiState.watchedKeys, progressByVideoId) {
                                areEpisodesWatchedForActions(
                                    meta = meta,
                                    episodes = seasonEpisodes,
                                    watchedKeys = watchedUiState.watchedKeys,
                                    progressByVideoId = progressByVideoId,
                                )
                            }
                            val canMarkPreviousSeasons = remember(previousSeasonEpisodes, watchedUiState.watchedKeys, progressByVideoId) {
                                previousSeasonEpisodes.any { episode ->
                                    !isEpisodeWatchedForActions(
                                        meta = meta,
                                        episode = episode,
                                        watchedKeys = watchedUiState.watchedKeys,
                                        progressByVideoId = progressByVideoId,
                                    )
                                }
                            }
                            if (useBoard) {
                                // The menu ends with that season's Rate strip when the rating source rates seasons.
                                val seasonRatingMedia = remember(meta, selectedSeason) { meta.seasonRatingReference(selectedSeason) }
                                val seasonRating by rememberRatingState(seasonRatingMedia)
                                BoardContextMenu(
                                    title = seasonLabel,
                                    anchor = null,
                                    actions = listOfNotNull(
                                        BoardMenuAction(
                                            stringResource(if (isSeasonWatched) Res.string.episode_mark_season_unwatched else Res.string.episode_mark_season_watched, seasonLabel),
                                            Icons.AutoMirrored.Rounded.PlaylistAddCheck,
                                            "detail_season_menu_watched",
                                        ) { WatchingActions.toggleSeasonWatched(meta = meta, episodes = seasonEpisodes, areCurrentlyWatched = isSeasonWatched) },
                                        BoardMenuAction(
                                            stringResource(Res.string.detail_board_mark_previous_seasons),
                                            Icons.Rounded.DoneAll,
                                            "detail_season_menu_previous",
                                        ) { WatchingActions.togglePreviousEpisodesWatched(meta = meta, episodes = previousSeasonEpisodes, areCurrentlyWatched = false) }
                                            .takeIf { canMarkPreviousSeasons },
                                    ),
                                    onDismiss = { selectedSeasonForActions = null },
                                    footer = if (seasonRating.isAvailable) {
                                        {
                                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                                Text(
                                                    stringResource(Res.string.detail_board_rate_episode, "S$selectedSeason").uppercase(),
                                                    style = redesignText(11.sp, FontWeight.Medium, RedesignColors.textMuted),
                                                    modifier = Modifier.padding(start = 6.dp),
                                                )
                                                BoardRateControl(
                                                    media = seasonRatingMedia,
                                                    rating = seasonRating,
                                                    favorite = TrackingFavoriteState(),
                                                    onOpenChange = {},
                                                    height = 40.dp,
                                                    alwaysOpen = true,
                                                )
                                            }
                                        }
                                    } else null,
                                )
                            } else SeasonWatchedActionSheet(
                                seasonLabel = seasonLabel,
                                isSeasonWatched = isSeasonWatched,
                                canMarkPreviousSeasons = canMarkPreviousSeasons,
                                onDismiss = { selectedSeasonForActions = null },
                                onToggleSeasonWatched = {
                                    WatchingActions.toggleSeasonWatched(
                                        meta = meta,
                                        episodes = seasonEpisodes,
                                        areCurrentlyWatched = isSeasonWatched,
                                    )
                                },
                                onMarkPreviousSeasonsWatched = {
                                    WatchingActions.togglePreviousEpisodesWatched(
                                        meta = meta,
                                        episodes = previousSeasonEpisodes,
                                        areCurrentlyWatched = false,
                                    )
                                },
                            )
                        }

                        if (inAppTrailerPlaybackEnabled) {
                            TrailerPlayerPopup(
                                visible = selectedTrailer != null,
                                trailerTitle = selectedTrailer?.displayName ?: selectedTrailer?.name.orEmpty(),
                                trailerType = selectedTrailer?.type.orEmpty(),
                                contentTitle = meta.name,
                                playbackSource = trailerRequest.source,
                                playbackRequestId = trailerRequest.id,
                                backend = trailerRequest.backend,
                                isLoading = trailerRequest.loading,
                                errorMessage = trailerRequest.error,
                                onDismiss = {
                                    trailerRequest.cancel()
                                    selectedTrailer = null
                                },
                                onRetry = selectedTrailer?.let { trailer ->
                                    { trailerRequest.start(trailer.youtubePlaybackUrl(), forceRefresh = true, backend = trailerRequest.backend) }
                                },
                            )
                        }

                        TrackingListPickerDialog(
                            visible = showLibraryListPicker,
                            title = meta.name,
                            tabs = pickerTabs,
                            membership = pickerMembership,
                            isPending = pickerPending,
                            errorMessage = pickerError,
                            onToggle = { listKey ->
                                pickerMembership = toggleTrackingLibraryMembership(
                                    tabs = pickerTabs,
                                    membership = pickerMembership,
                                    key = listKey,
                                )
                            },
                            onDismiss = {
                                if (!pickerPending) {
                                    showLibraryListPicker = false
                                }
                            },
                            onSave = {
                                detailsScope.launch {
                                    pickerPending = true
                                    pickerError = null
                                    val item = meta.toLibraryItem(savedAtEpochMs = 0L)
                                    val desiredMembership = pickerMembership.toMap()
                                    val applyMembership: suspend (Set<TrackingProviderId>) ->
                                        TrackingMembershipApplyResult = { confirmedProviders ->
                                        LibraryRepository.applyMembershipChanges(
                                            item = item,
                                            desiredMembership = desiredMembership,
                                            confirmedRemovalProviders = confirmedProviders,
                                        )
                                    }
                                    val completeMembershipUpdate: suspend (TrackingMembershipApplyResult) -> Unit = { result ->
                                        showTrackingMembershipRewriteFeedback(result)
                                        showLibraryListPicker = false
                                    }
                                    executeTrackingMembershipOperation(
                                        operation = { applyMembership(emptySet()) },
                                        onSuccess = { result ->
                                            if (result.requiresRemovalConfirmation) {
                                                pendingTrackingRemoval = PendingTrackingMembershipRemoval(
                                                    itemTitle = item.name,
                                                    confirmations = result.requiredRemovalConfirmations,
                                                    retry = applyMembership,
                                                    onApplied = completeMembershipUpdate,
                                                    onFailure = { error ->
                                                        pickerError = error.message
                                                            ?: trackingListsUpdateFailedMessage
                                                    },
                                                )
                                            } else {
                                                completeMembershipUpdate(result)
                                            }
                                        },
                                        onFailure = { error ->
                                            pickerError = error.message ?: trackingListsUpdateFailedMessage
                                        },
                                    )
                                    pickerPending = false
                                }
                            },
                        )

                        TrackingMembershipRemovalConfirmationHost(
                            pending = pendingTrackingRemoval,
                            onPendingChange = { pendingTrackingRemoval = it },
                        )

                        selectedComment?.let { comment ->
                            val commentIndex = comments.indexOfFirst { it.id == comment.id }.coerceAtLeast(0)
                            CommentDetailSheet(
                                comment = comment,
                                currentIndex = commentIndex,
                                totalCount = comments.size,
                                canGoBack = commentIndex > 0,
                                canGoForward = commentIndex < comments.size - 1,
                                onPrevious = {
                                    if (commentIndex > 0) {
                                        selectedComment = comments[commentIndex - 1]
                                    }
                                },
                                onNext = {
                                    val nextIndex = commentIndex + 1
                                    if (nextIndex < comments.size) {
                                        selectedComment = comments[nextIndex]
                                    }
                                    if (nextIndex >= comments.size - 3 && commentsCurrentPage < commentsPageCount) {
                                        detailsScope.launch {
                                            isCommentsLoadingMore = true
                                            try {
                                                val nextPage = commentsCurrentPage + 1
                                                val result = TraktCommentsRepository.getCommentsPage(meta, page = nextPage)
                                                val existingIds = comments.map { it.id }.toSet()
                                                val newComments = result.items.filter { it.id !in existingIds }
                                                comments = comments + newComments
                                                commentsCurrentPage = result.currentPage
                                                commentsPageCount = result.pageCount
                                            } catch (_: Exception) { }
                                            isCommentsLoadingMore = false
                                        }
                                    }
                                },
                                onDismiss = { selectedComment = null },
                            )
                        }
                    }
                }
            }
            DetailContentState.Empty -> Unit
            }
            }
        }

        if (displayedMeta == null) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                // The board's loading skeleton carries its own Back control.
                if (isDesktop && maxWidth >= DetailBoardMinWidth && uiState.isLoading) return@BoxWithConstraints
                val loadingBackButtonStartPadding = if (isDesktop) {
                    desktopPageHorizontalPaddingForWidth(maxWidth.value)
                } else {
                    12.dp
                }
                val loadingBackButtonTopPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + if (isDesktop) {
                    32.dp
                } else {
                    8.dp
                }
                NuvioBackButton(
                    onClick = onBack,
                    modifier = Modifier.padding(
                        start = loadingBackButtonStartPadding,
                        top = loadingBackButtonTopPadding,
                    ),
                    containerColor = if (isDesktop) Color.Black.copy(alpha = 0.34f) else Color.Transparent,
                    showContainerOnDesktop = isDesktop,
                    contentColor = MaterialTheme.colorScheme.onBackground,
                    buttonSize = if (isDesktop) 48.dp else 40.dp,
                    iconSize = 24.dp,
                )
            }
        }
        }

        val meta = displayedMeta
        val selectedEpisode = selectedEpisodeForActions
        val zoomAnchor = selectedEpisodeZoomAnchor
        if (meta != null && selectedEpisode != null && zoomAnchor != null) {
            val todayIsoDate = CurrentDateProvider.todayIsoDate()
            val isSelectedEpisodeWatched = remember(meta, selectedEpisode, watchedUiState.watchedKeys, progressByVideoId) {
                isEpisodeWatchedForActions(
                    meta = meta,
                    episode = selectedEpisode,
                    watchedKeys = watchedUiState.watchedKeys,
                    progressByVideoId = progressByVideoId,
                )
            }
            val previousEpisodes = remember(meta, selectedEpisode, todayIsoDate) {
                meta.previousReleasedEpisodesBefore(
                    target = selectedEpisode,
                    todayIsoDate = todayIsoDate,
                )
            }
            val seasonEpisodes = remember(meta, selectedEpisode, todayIsoDate) {
                meta.releasedEpisodesForSeason(
                    seasonNumber = selectedEpisode.season,
                    todayIsoDate = todayIsoDate,
                )
            }
            val arePreviousEpisodesWatched = remember(previousEpisodes, watchedUiState.watchedKeys, progressByVideoId) {
                areEpisodesWatchedForActions(
                    meta = meta,
                    episodes = previousEpisodes,
                    watchedKeys = watchedUiState.watchedKeys,
                    progressByVideoId = progressByVideoId,
                )
            }
            val isSeasonWatched = remember(seasonEpisodes, watchedUiState.watchedKeys, progressByVideoId) {
                areEpisodesWatchedForActions(
                    meta = meta,
                    episodes = seasonEpisodes,
                    watchedKeys = watchedUiState.watchedKeys,
                    progressByVideoId = progressByVideoId,
                )
            }
            val seasonLabel = selectedEpisode.season?.let {
                stringResource(Res.string.episodes_season, it)
            } ?: stringResource(Res.string.episodes_specials)
            val episodeRating = remember(meta, selectedEpisode) {
                TrackingRatings.current(meta.ratingReference(selectedEpisode))
            }
            NuvioPosterZoomActionOverlay(
                imageUrl = zoomAnchor.imageUrl ?: selectedEpisode.thumbnail ?: meta.background ?: meta.poster,
                title = selectedEpisode.title,
                subtitle = localizedSeasonEpisodeCode(selectedEpisode.season, selectedEpisode.episode) ?: seasonLabel,
                isWatched = isSelectedEpisodeWatched,
                blurred = metaScreenSettingsUiState.blurUnwatchedEpisodes && !isSelectedEpisodeWatched,
                depthSurface = NuvioCardDepthSurface.EpisodeCards,
                anchor = zoomAnchor,
                actions = buildList {
                    add(
                        PosterZoomOverlayAction(
                            icon = Icons.Default.CheckCircle,
                            label = if (isSelectedEpisodeWatched) {
                                stringResource(Res.string.episode_mark_unwatched)
                            } else {
                                stringResource(Res.string.episode_mark_watched)
                            },
                            onSelected = {
                                WatchingActions.toggleEpisodeWatched(
                                    meta = meta,
                                    episode = selectedEpisode,
                                    isCurrentlyWatched = isSelectedEpisodeWatched,
                                )
                            },
                        ),
                    )
                    if (previousEpisodes.isNotEmpty()) {
                        add(
                            PosterZoomOverlayAction(
                                icon = Icons.Default.DoneAll,
                                label = if (arePreviousEpisodesWatched) {
                                    stringResource(Res.string.episode_mark_previous_unwatched)
                                } else {
                                    stringResource(Res.string.episode_mark_previous_watched)
                                },
                                onSelected = {
                                    WatchingActions.togglePreviousEpisodesWatched(
                                        meta = meta,
                                        episodes = previousEpisodes,
                                        areCurrentlyWatched = arePreviousEpisodesWatched,
                                    )
                                },
                            ),
                        )
                    }
                    add(
                        PosterZoomOverlayAction(
                            icon = Icons.Default.PlaylistAddCheckCircle,
                            label = if (isSeasonWatched) {
                                stringResource(Res.string.episode_mark_season_unwatched, seasonLabel)
                            } else {
                                stringResource(Res.string.episode_mark_season_watched, seasonLabel)
                            },
                            onSelected = {
                                WatchingActions.toggleSeasonWatched(
                                    meta = meta,
                                    episodes = seasonEpisodes,
                                    areCurrentlyWatched = isSeasonWatched,
                                )
                            },
                        ),
                    )
                    if (onPlayManually != null && StreamAutoPlayPolicy.isEffectivelyEnabled(playerSettingsUiState)) {
                        add(
                            PosterZoomOverlayAction(
                                icon = Icons.Default.PlayArrow,
                                label = stringResource(Res.string.play_manually),
                                onSelected = {
                                    val playbackVideoId = buildPlaybackVideoId(
                                        parentMetaId = meta.id,
                                        seasonNumber = selectedEpisode.season,
                                        episodeNumber = selectedEpisode.episode,
                                        fallbackVideoId = selectedEpisode.id,
                                    )
                                    val streamVideoId = selectedEpisode.id.takeIf { it.isNotBlank() } ?: playbackVideoId
                                    val savedProgress = progressByVideoId[streamVideoId]
                                        ?.takeUnless { it.isCompleted }
                                    onPlayManually.invoke(
                                        meta.type,
                                        streamVideoId,
                                        meta.id,
                                        meta.type,
                                        meta.name,
                                        meta.logo,
                                        meta.poster,
                                        meta.background,
                                        selectedEpisode.season,
                                        selectedEpisode.episode,
                                        selectedEpisode.title,
                                        selectedEpisode.thumbnail,
                                        selectedEpisode.overview,
                                        savedProgress?.lastPositionMs,
                                    )
                                },
                            ),
                        )
                    }
                },
                hazeState = episodeOverlayHazeState,
                onDismissed = {
                    selectedEpisodeForActions = null
                    selectedEpisodeZoomAnchor = null
                },
            )
        }

        val ratedEpisode = episodeToRate
        if (meta != null && ratedEpisode != null) {
            val ratingMedia = remember(meta, ratedEpisode) { meta.ratingReference(ratedEpisode) }
            val ratingState by rememberRatingState(ratingMedia)
            RatingSheet(
                media = ratingMedia,
                title = localizedEpisodeTitle(ratedEpisode.title, ratedEpisode.episode) ?: ratedEpisode.title,
                subtitle = localizedSeasonEpisodeCode(ratedEpisode.season, ratedEpisode.episode),
                state = ratingState,
                onDismiss = { episodeToRate = null },
            )
        }
    }
}

private fun MetaDetails.isSeriesLikeForEpisodeRatings(): Boolean {
    val normalizedType = type.trim().lowercase()
    val hasNumberedEpisodes = videos.any { it.season != null && it.episode != null }
    return hasNumberedEpisodes && normalizedType in setOf("series", "show", "tv", "tvshow")
}

@Composable
private fun selectedSeasonLabel(season: Int): String =
    if (season == 0) {
        stringResource(Res.string.episodes_specials)
    } else {
        stringResource(Res.string.episodes_season, season)
    }

private fun isEpisodeWatchedForActions(
    meta: MetaDetails,
    episode: MetaVideo,
    watchedKeys: Set<String>,
    progressByVideoId: Map<String, WatchProgressEntry>,
): Boolean {
    val episodeVideoId = buildPlaybackVideoId(
        parentMetaId = meta.id,
        seasonNumber = episode.season,
        episodeNumber = episode.episode,
        fallbackVideoId = episode.id,
    )
    return progressByVideoId[episodeVideoId]?.isEffectivelyCompleted == true ||
        WatchingState.isEpisodeWatched(
            watchedKeys = watchedKeys,
            metaType = meta.type,
            metaId = meta.id,
            episode = episode,
        )
}

private fun areEpisodesWatchedForActions(
    meta: MetaDetails,
    episodes: Collection<MetaVideo>,
    watchedKeys: Set<String>,
    progressByVideoId: Map<String, WatchProgressEntry>,
): Boolean = episodes.isNotEmpty() && episodes.all { episode ->
    isEpisodeWatchedForActions(
        meta = meta,
        episode = episode,
        watchedKeys = watchedKeys,
        progressByVideoId = progressByVideoId,
    )
}

private fun extractImdbId(value: String?): String? =
    value
        ?.trim()
        ?.split(':', '/', '?', '&')
        ?.firstOrNull { part -> part.startsWith("tt", ignoreCase = true) }
        ?.takeIf { it.length > 2 }

private fun extractTmdbId(value: String?): Int? {
    val trimmed = value?.trim().orEmpty()
    if (trimmed.isBlank()) return null
    return trimmed
        .takeIf { it.startsWith("tmdb:", ignoreCase = true) }
        ?.substringAfter(':')
        ?.substringBefore(':')
        ?.substringBefore('/')
        ?.toIntOrNull()
}

private fun MetaDetails.toMetaPreview(): MetaPreview =
    MetaPreview(
        id = id,
        type = type,
        name = name,
        poster = poster,
        banner = background,
        logo = logo,
        description = description,
        releaseInfo = releaseInfo,
        imdbRating = imdbRating,
        genres = genres,
    )

/** Desktop windows at least this wide show the Details board. */
internal val DetailBoardMinWidth = 1000.dp

/** What the Details page shows; the states crossfade. */
private enum class DetailContentState { Loading, Error, Content, Empty }

/** An episode's Quick pick on the board, opened beside its card. */
private data class EpisodeQuickPick(
    val video: MetaVideo,
    val videoId: String,
    val anchor: Rect?,
    val request: StreamQuickPickRequest,
)

/** "Resume from 18:22 · 38%" for a Quick pick, or null when nothing is saved. [template] holds "{t}". */
internal fun quickPickResumeLine(entry: WatchProgressEntry?, template: String): String? {
    val position = entry?.lastPositionMs?.takeIf { it > 0L } ?: return null
    val percent = entry.durationMs.takeIf { it > 0L }?.let { (position * 100 / it).toInt() }
    return listOfNotNull(template.replace("{t}", position.formatResumeClock()), percent?.let { "$it%" }).joinToString(" · ")
}

private fun Long.formatResumeClock(): String {
    val total = this / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val sec = total % 60
    fun two(v: Long) = v.toString().padStart(2, '0')
    return if (h > 0) "$h:${two(m)}:${two(sec)}" else "$m:${two(sec)}"
}

private val tabletHeroSectionKeys = setOf(
    MetaScreenSectionKey.ACTIONS,
    MetaScreenSectionKey.OVERVIEW,
)

private fun detailTabletContentMaxWidth(maxWidth: Dp, isTablet: Boolean): Dp =
    if (!isTablet) {
        maxWidth
    } else {
        (maxWidth * 0.6f).coerceIn(520.dp, 680.dp)
    }

private fun dominantBackdropBlendColor(dominantColor: Color, backgroundColor: Color): Color =
    backgroundColor.blendTowards(dominantColor, fraction = 0.42f)

private fun Color.blendTowards(target: Color, fraction: Float): Color {
    val clamped = fraction.coerceIn(0f, 1f)
    return Color(
        red = red + (target.red - red) * clamped,
        green = green + (target.green - green) * clamped,
        blue = blue + (target.blue - blue) * clamped,
        alpha = alpha + (target.alpha - alpha) * clamped,
    )
}
