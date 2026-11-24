package com.nuvio.app.features.details

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
import com.nuvio.app.features.details.components.LocalDetailsArtworkText
import com.nuvio.app.features.details.components.DesktopDetailBackdrop
import com.nuvio.app.features.details.components.DetailSeriesListEpisode
import com.nuvio.app.features.details.components.DetailSeriesListHeader
import com.nuvio.app.features.details.components.DetailTrailersSection
import com.nuvio.app.features.details.components.EpisodeWatchedActionSheet
import com.nuvio.app.features.details.components.RatingSheet
import com.nuvio.app.features.details.components.formatRating
import com.nuvio.app.features.details.components.ratingReference
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
import com.nuvio.app.features.details.components.BoardCastGrid
import com.nuvio.app.features.details.components.BoardMoreLikeThis
import com.nuvio.app.features.details.components.BehindTheFilmTile
import com.nuvio.app.features.details.components.BoardDetailsFacts
import com.nuvio.app.features.details.components.BoardGallery
import com.nuvio.app.features.details.components.BoardTrailers
import com.nuvio.app.features.gallery.GalleryImage
import com.nuvio.app.features.gallery.GalleryMinimumImages
import com.nuvio.app.features.tracking.TrackingCommentsProvider
import com.nuvio.app.features.details.components.BoardReviews

/**
 * Everything the Details sections read and call, gathered once by [MetaDetailsScreen]. Every layout
 * (phone, tablet, the desktop board) renders its sections from the same bundle.
 */
internal data class DetailSectionArgs(
    val isMdbListActive: Boolean,
    val meta: MetaDetails,
    val playButtonLabel: String,
    val isPrimaryPlayEnabled: Boolean,
    val isSaved: Boolean,
    val isWatched: Boolean,
    val onPrimaryPlayClick: () -> Unit,
    val onShuffleClick: (() -> Unit)?,
    val shuffleEnabled: Boolean,
    val onPrimaryPlayLongClick: (() -> Unit)?,
    val onSaveClick: () -> Unit,
    val onSaveLongClick: (() -> Unit)?,
    val onWatchedClick: () -> Unit,
    val showManualPlayOption: Boolean,
    val preferredEpisodeSeasonNumber: Int?,
    val preferredEpisodeNumber: Int?,
    val hasProductionSection: Boolean,
    val hasTrailersSection: Boolean,
    val hasEpisodes: Boolean,
    val hasAdditionalInfoSection: Boolean,
    val hasCollectionSection: Boolean,
    val hasMoreLikeThisSection: Boolean,
    val shouldShowComments: Boolean,
    val comments: List<TraktCommentReview>,
    val isCommentsLoading: Boolean,
    val isCommentsLoadingMore: Boolean,
    val commentsCurrentPage: Int,
    val commentsPageCount: Int,
    val commentsError: String?,
    val episodeImdbRatings: Map<Pair<Int, Int>, Double>,
    val episodeListGroupedEpisodes: Map<Int, List<MetaVideo>>,
    val episodeListSeasons: List<Int>,
    val episodeListCurrentSeason: Int?,
    val onEpisodeListSeasonSelect: (Int) -> Unit,
    val detailListState: LazyListState,
    val seriesProgress: List<SeasonWatchedCount>?,
    val audienceState: SeriesAudienceState,
    val onRetryComments: () -> Unit,
    val onLoadMoreComments: () -> Unit,
    val onCommentClick: (TraktCommentReview) -> Unit,
    val onTrailerClick: (MetaTrailer) -> Unit,
    val progressByVideoId: Map<String, WatchProgressEntry>,
    val watchedKeys: Set<String>,
    val fullyWatchedSeriesKeys: Set<String> = emptySet(),
    val blurUnwatchedEpisodes: Boolean,
    val onEpisodeClick: (MetaVideo) -> Unit,
    val onEpisodeLongPress: (MetaVideo) -> Unit,
    val onSeasonLongPress: (Int) -> Unit,
    val onOpenMeta: ((MetaPreview) -> Unit)?,
    val onCastClick: ((MetaPerson, String?) -> Unit)?,
    val onCompanyClick: ((MetaCompany, String) -> Unit)?,
    val sharedTransitionScope: SharedTransitionScope?,
    val animatedVisibilityScope: AnimatedVisibilityScope?,
    // Watch it your way. Null hides the action.
    val tvHandoff: DetailTvHandoff? = null,
    val onWatchPartyClick: (() -> Unit)? = null,
    val onDownloadClick: (() -> Unit)? = null,
    // The desktop board's Episodes tab: season pills, the Row / List switch, and episode clicks with
    // the card's bounds so Quick pick opens beside it.
    val boardStyle: Boolean = false,
    val onEpisodeCardStyleChange: ((MetaEpisodeCardStyle) -> Unit)? = null,
    val onEpisodeClickAt: ((MetaVideo, androidx.compose.ui.geometry.Rect?) -> Unit)? = null,
    val onEpisodeMenuAt: ((MetaVideo, androidx.compose.ui.geometry.Rect?) -> Unit)? = null,
    // More like this on the board: the unwatched titles (shared fill logic) and the remembered switch.
    val moreLikeThisUnwatched: List<MetaPreview>? = null,
    val moreLikeThisUnwatchedOnly: Boolean = true,
    val onMoreLikeThisUnwatchedOnlyChange: ((Boolean) -> Unit)? = null,
    /** The Details tab's "Behind the film" tile (Wikidata), once looked up. */
    val behindTheFilm: BehindTheFilm? = null,
    /** The gallery's images (desktop board); the tab shows from [GalleryMinimumImages]. */
    val gallery: List<GalleryImage> = emptyList(),
    /** The trailer playing in the board's expanded featured tile, ringed on the Trailers tab. */
    val boardPlayingTrailer: MetaTrailer? = null,
    val onSetBackground: ((String) -> Unit)? = null,
    /** The board's reviews source; Reviews shows its own loading, likes, reader and composer. */
    val reviewsProvider: TrackingCommentsProvider? = null,
) {
    /** Whether [key] has anything to show for this title. */
    fun hasContent(key: MetaScreenSectionKey): Boolean =
        metaSectionHasContent(
            key = key,
            meta = meta,
            hasProductionSection = hasProductionSection,
            hasTrailersSection = hasTrailersSection,
            hasEpisodes = hasEpisodes,
            hasAdditionalInfoSection = hasAdditionalInfoSection,
            hasCollectionSection = hasCollectionSection,
            hasMoreLikeThisSection = hasMoreLikeThisSection,
            shouldShowComments = shouldShowComments,
            comments = comments,
            isCommentsLoading = isCommentsLoading,
            commentsError = commentsError,
            galleryCount = gallery.size,
            reviewsReady = boardStyle && reviewsProvider != null,
        )
}

/**
 * Adds the sections enabled in [settings], in their configured order. [showHeaders] is off where a
 * tab already names the section; [showAudience] is off where the audience cards have their own
 * place; [animateItems] fades sections in and out when the set changes (a tab switch).
 */
@OptIn(ExperimentalSharedTransitionApi::class)
internal fun LazyListScope.configuredMetaSectionItems(
    settings: MetaScreenSettingsUiState,
    args: DetailSectionArgs,
    isTablet: Boolean,
    contentHorizontalPadding: Dp,
    contentMaxWidth: Dp,
    showHeaders: Boolean = true,
    showAudience: Boolean = true,
    animateItems: Boolean = false,
) {
    with(args) {
        val enabledItems = settings.items.filter { it.enabled }
        fun sectionHasContent(key: MetaScreenSectionKey): Boolean =
            metaSectionHasContent(
                key = key,
                meta = meta,
                hasProductionSection = hasProductionSection,
                hasTrailersSection = hasTrailersSection,
                hasEpisodes = hasEpisodes,
                hasAdditionalInfoSection = hasAdditionalInfoSection,
                hasCollectionSection = hasCollectionSection,
                hasMoreLikeThisSection = hasMoreLikeThisSection,
                shouldShowComments = shouldShowComments,
                comments = comments,
                isCommentsLoading = isCommentsLoading,
                commentsError = commentsError,
                galleryCount = gallery.size,
                reviewsReady = boardStyle && reviewsProvider != null,
            )

        val showSeriesAudience = showAudience && isDesktop && meta.type == "series" && episodeListCurrentSeason != null
        val audienceCards: @Composable (onOpenSeason: (Int) -> Unit) -> Unit = { onOpenSeason ->
            if (showSeriesAudience) {
                SeriesAudienceSection(
                    meta = meta,
                    season = requireNotNull(episodeListCurrentSeason),
                    progress = seriesProgress,
                    state = audienceState,
                    onSeasonSelected = onEpisodeListSeasonSelect,
                    onOpenSeason = onOpenSeason,
                )
            }
        }

        fun addSectionItem(
            key: String,
            sectionItems: List<MetaScreenSectionItem>,
            forceTabLayout: Boolean = settings.tabLayout,
        ) {
            item(key = key) {
                DetailSectionContainer(
                    modifier = if (animateItems) Modifier.animateItem() else Modifier,
                    horizontalPadding = contentHorizontalPadding,
                    contentMaxWidth = contentMaxWidth,
                ) {
                    ConfiguredMetaSections(
                        settings = settings.copy(
                            items = sectionItems,
                            tabLayout = forceTabLayout,
                        ),
                        args = args,
                        isTablet = isTablet,
                        horizontalScrollPadding = contentHorizontalPadding,
                        controlledEpisodeSeason = episodeListCurrentSeason.takeIf { isDesktop },
                        onControlledSeasonSelect = onEpisodeListSeasonSelect.takeIf { isDesktop },
                        seriesAudience = {
                            audienceCards { season ->
                                val index = detailListState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key }?.index
                                onEpisodeListSeasonSelect(season)
                                if (index != null) detailListState.requestScrollToItem(index)
                            }
                        },
                        showHeaders = showHeaders,
                    )
                }
            }
        }

        fun addLazyEpisodeListItems(key: String) {
            val currentSeason = episodeListCurrentSeason ?: return
            val episodes = episodeListGroupedEpisodes[currentSeason].orEmpty()
            if (episodes.isEmpty()) return

            item(
                key = "$key-header",
                contentType = "detail-episode-header",
            ) {
                DetailSectionContainer(
                    modifier = if (animateItems) Modifier.animateItem() else Modifier,
                    horizontalPadding = contentHorizontalPadding,
                    contentMaxWidth = contentMaxWidth,
                    bottomPadding = if (isDesktop) 8.dp else 12.dp,
                ) {
                    DetailSeriesListHeader(
                        meta = meta,
                        groupedEpisodes = episodeListGroupedEpisodes,
                        seasons = episodeListSeasons,
                        currentSeason = currentSeason,
                        horizontalScrollPadding = contentHorizontalPadding,
                        onSeasonSelect = onEpisodeListSeasonSelect,
                        onSeasonLongPress = onSeasonLongPress,
                        boardStyle = boardStyle,
                        watchedKeys = watchedKeys,
                        progressByVideoId = progressByVideoId,
                        episodeCardStyle = settings.episodeCardStyle,
                        onEpisodeCardStyleChange = onEpisodeCardStyleChange,
                    )
                }
            }
            itemsIndexed(
                items = episodes,
                key = { index, episode ->
                    "$key-episode-$currentSeason-${episode.episode}-${episode.id}-$index"
                },
                contentType = { _, _ -> "detail-episode" },
            ) { index, episode ->
                DetailSectionContainer(
                    modifier = if (animateItems) Modifier.animateItem() else Modifier,
                    horizontalPadding = contentHorizontalPadding,
                    contentMaxWidth = contentMaxWidth,
                    bottomPadding = if (index == episodes.lastIndex) 20.dp else 12.dp,
                ) {
                    DetailSeriesListEpisode(
                        meta = meta,
                        episode = episode,
                        progressByVideoId = progressByVideoId,
                        watchedKeys = watchedKeys,
                        episodeRatings = episodeImdbRatings,
                        episodeRatingsVisibility = settings.episodeRatingsVisibility,
                        blurUnwatchedEpisodes = blurUnwatchedEpisodes,
                        onEpisodeClick = onEpisodeClick,
                        onEpisodeLongPress = onEpisodeLongPress,
                        onEpisodeClickAt = onEpisodeClickAt,
                        onEpisodeLongPressAt = onEpisodeMenuAt,
                    )
                }
            }
            if (showSeriesAudience) {
                val audienceKey = "$key-audience"
                item(key = audienceKey, contentType = "detail-series-audience") {
                    DetailSectionContainer(
                        modifier = if (animateItems) Modifier.animateItem() else Modifier,
                        horizontalPadding = contentHorizontalPadding,
                        contentMaxWidth = contentMaxWidth,
                    ) {
                        audienceCards { season ->
                            val audienceIndex = detailListState.layoutInfo.visibleItemsInfo
                                .firstOrNull { it.key == audienceKey }?.index
                            onEpisodeListSeasonSelect(season)
                            if (audienceIndex != null) detailListState.requestScrollToItem(audienceIndex - episodes.size - 1)
                        }
                    }
                }
            }
        }

        fun addStandaloneSection(
            section: MetaScreenSectionItem,
            key: String,
            forceTabLayout: Boolean = false,
        ) {
            if (section.key == MetaScreenSectionKey.EPISODES && settings.episodeCardStyle == MetaEpisodeCardStyle.List) {
                addLazyEpisodeListItems(key)
            } else {
                addSectionItem(
                    key = key,
                    sectionItems = listOf(section),
                    forceTabLayout = forceTabLayout,
                )
            }
        }

        if (!settings.tabLayout) {
            enabledItems
                .filter { sectionHasContent(it.key) }
                .forEach { section ->
                    addStandaloneSection(
                        section = section,
                        key = "detail-section-${section.key.name}",
                    )
                }
            return
        }

        val processedGroups = mutableSetOf<Int>()
        enabledItems.forEach { section ->
            val groupId = section.tabGroupForRendering(settings.episodeCardStyle)
            if (groupId == null) {
                if (sectionHasContent(section.key)) {
                    addStandaloneSection(
                        section = section,
                        key = "detail-section-${section.key.name}",
                        forceTabLayout = true,
                    )
                }
            } else if (groupId !in processedGroups) {
                processedGroups.add(groupId)
                val groupMembers = enabledItems.filter { item ->
                    item.tabGroupForRendering(settings.episodeCardStyle) == groupId && sectionHasContent(item.key)
                }
                if (groupMembers.isNotEmpty()) {
                    if (groupMembers.size == 1) {
                        addStandaloneSection(
                            section = groupMembers.single(),
                            key = "detail-section-group-$groupId",
                        )
                    } else {
                        addSectionItem(
                            key = "detail-section-group-$groupId",
                            sectionItems = groupMembers,
                            forceTabLayout = true,
                        )
                    }
                }
            }
        }
    }
}


@Composable
private fun DetailSectionContainer(
    modifier: Modifier = Modifier,
    horizontalPadding: Dp,
    contentMaxWidth: Dp,
    bottomPadding: Dp = 20.dp,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPadding)
            .padding(bottom = bottomPadding),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (contentMaxWidth == Dp.Unspecified) {
                        Modifier
                    } else {
                        Modifier.widthIn(max = contentMaxWidth)
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            CompositionLocalProvider(LocalVerticalBarPadding provides PaddingValues(), content)
        }
    }
}

private fun metaSectionHasContent(
    key: MetaScreenSectionKey,
    meta: MetaDetails,
    hasProductionSection: Boolean,
    hasTrailersSection: Boolean,
    hasEpisodes: Boolean,
    hasAdditionalInfoSection: Boolean,
    hasCollectionSection: Boolean,
    hasMoreLikeThisSection: Boolean,
    shouldShowComments: Boolean,
    comments: List<TraktCommentReview>,
    isCommentsLoading: Boolean,
    commentsError: String?,
    galleryCount: Int = 0,
    reviewsReady: Boolean = false,
): Boolean =
    when (key) {
        MetaScreenSectionKey.ACTIONS -> true
        MetaScreenSectionKey.OVERVIEW -> true
        MetaScreenSectionKey.PRODUCTION -> hasProductionSection
        MetaScreenSectionKey.CAST -> meta.cast.isNotEmpty()
        MetaScreenSectionKey.COMMENTS -> reviewsReady || shouldShowComments && (isCommentsLoading || comments.isNotEmpty() || !commentsError.isNullOrBlank())
        MetaScreenSectionKey.TRAILERS -> hasTrailersSection
        MetaScreenSectionKey.EPISODES -> hasEpisodes
        MetaScreenSectionKey.DETAILS -> hasAdditionalInfoSection
        MetaScreenSectionKey.COLLECTION -> !hasEpisodes && hasCollectionSection
        MetaScreenSectionKey.MORE_LIKE_THIS -> hasMoreLikeThisSection
        MetaScreenSectionKey.GALLERY -> galleryCount >= GalleryMinimumImages
    }

/** Renders the sections enabled in [settings] one after another, or grouped into tabs. */
@Composable
@OptIn(ExperimentalSharedTransitionApi::class)
internal fun ConfiguredMetaSections(
    settings: MetaScreenSettingsUiState,
    args: DetailSectionArgs,
    isTablet: Boolean,
    horizontalScrollPadding: Dp,
    controlledEpisodeSeason: Int?,
    onControlledSeasonSelect: ((Int) -> Unit)?,
    seriesAudience: @Composable () -> Unit,
    showHeaders: Boolean = true,
) {
    with(args) {
        val enabledItems = settings.items.filter { it.enabled }

        // Helper to check if a section actually has content to show
        val sectionHasContent: (MetaScreenSectionKey) -> Boolean = { key ->
            when (key) {
                MetaScreenSectionKey.ACTIONS -> true
                MetaScreenSectionKey.OVERVIEW -> true
                MetaScreenSectionKey.PRODUCTION -> hasProductionSection
                MetaScreenSectionKey.CAST -> meta.cast.isNotEmpty()
                MetaScreenSectionKey.COMMENTS -> shouldShowComments && (isCommentsLoading || comments.isNotEmpty() || !commentsError.isNullOrBlank())
                MetaScreenSectionKey.TRAILERS -> hasTrailersSection
                MetaScreenSectionKey.EPISODES -> hasEpisodes
                MetaScreenSectionKey.DETAILS -> hasAdditionalInfoSection
                MetaScreenSectionKey.COLLECTION -> !hasEpisodes && hasCollectionSection
                MetaScreenSectionKey.MORE_LIKE_THIS -> hasMoreLikeThisSection
                MetaScreenSectionKey.GALLERY -> gallery.size >= GalleryMinimumImages
            }
        }

        @Composable
        fun RenderSection(key: MetaScreenSectionKey, showHeader: Boolean = true) {
            when (key) {
                MetaScreenSectionKey.ACTIONS -> {
                    DetailActions(
                        playLabel = playButtonLabel,
                        playEnabled = isPrimaryPlayEnabled,
                        isSaved = isSaved,
                        isWatched = isWatched,
                        isTablet = isTablet,
                        shuffleEnabled = shuffleEnabled,
                        onPlayClick = onPrimaryPlayClick,
                        onPlayLongClick = if (showManualPlayOption) onPrimaryPlayLongClick else null,
                        onShuffleClick = onShuffleClick,
                        onWatchedClick = onWatchedClick,
                        onSaveClick = onSaveClick,
                        onSaveLongClick = onSaveLongClick,
                    )
                }
                MetaScreenSectionKey.OVERVIEW -> {
                    DetailMetaInfo(
                        meta = meta,
                        showOverallRatings = settings.showOverallRatings,
                        isMdbListActive = isMdbListActive,
                        horizontalScrollPadding = horizontalScrollPadding,
                    )
                }
                MetaScreenSectionKey.PRODUCTION -> {
                    if (hasProductionSection) {
                        DetailProductionSection(meta = meta, showHeader = showHeader, onCompanyClick = onCompanyClick)
                    }
                }
                MetaScreenSectionKey.CAST -> if (boardStyle) {
                    BoardCastGrid(cast = meta.cast, isSeries = meta.type == "series" || hasEpisodes, onCastClick = onCastClick)
                } else {
                    DetailCastSection(
                        cast = meta.cast,
                        showHeader = showHeader,
                        horizontalScrollPadding = horizontalScrollPadding,
                        onCastClick = onCastClick,
                        sharedTransitionScope = sharedTransitionScope,
                        animatedVisibilityScope = animatedVisibilityScope,
                    )
                }
                MetaScreenSectionKey.COMMENTS -> if (boardStyle && reviewsProvider != null) {
                    BoardReviews(
                        meta = meta,
                        provider = reviewsProvider,
                        episodes = remember(meta.videos) { meta.videos.mapNotNull { v -> v.season?.let { s -> v.episode?.let { e -> s to e } } } },
                    )
                } else {
                    if (shouldShowComments && (isCommentsLoading || comments.isNotEmpty() || !commentsError.isNullOrBlank())) {
                        DetailCommentsSection(
                            comments = comments,
                            isLoading = isCommentsLoading,
                            isLoadingMore = isCommentsLoadingMore,
                            canLoadMore = commentsCurrentPage < commentsPageCount,
                            error = commentsError,
                            onRetry = onRetryComments,
                            onLoadMore = onLoadMoreComments,
                            onCommentClick = onCommentClick,
                            showHeader = showHeader,
                            horizontalScrollPadding = horizontalScrollPadding,
                        )
                    }
                }
                MetaScreenSectionKey.TRAILERS -> {
                    if (hasTrailersSection && boardStyle) {
                        BoardTrailers(trailers = meta.trailers, playing = boardPlayingTrailer, onPlay = onTrailerClick)
                    } else if (hasTrailersSection) {
                        DetailTrailersSection(
                            trailers = meta.trailers,
                            onTrailerClick = onTrailerClick,
                            showHeader = showHeader,
                            horizontalScrollPadding = horizontalScrollPadding,
                        )
                    }
                }
                MetaScreenSectionKey.EPISODES -> {
                    if (hasEpisodes) {
                        Column {
                            DetailSeriesContent(
                                meta = meta,
                                showHeader = showHeader,
                                horizontalScrollPadding = horizontalScrollPadding,
                                preferredSeasonNumber = preferredEpisodeSeasonNumber,
                                preferredEpisodeNumber = preferredEpisodeNumber,
                                controlledSeason = controlledEpisodeSeason,
                                onControlledSeasonSelect = onControlledSeasonSelect,
                                episodeCardStyle = settings.episodeCardStyle,
                                progressByVideoId = progressByVideoId,
                                watchedKeys = watchedKeys,
                                episodeRatings = episodeImdbRatings,
                                episodeRatingsVisibility = settings.episodeRatingsVisibility,
                                blurUnwatchedEpisodes = blurUnwatchedEpisodes,
                                onEpisodeClick = onEpisodeClick,
                                onEpisodeLongPress = onEpisodeLongPress,
                                onSeasonLongPress = onSeasonLongPress,
                                boardStyle = boardStyle,
                                onEpisodeCardStyleChange = onEpisodeCardStyleChange,
                                onEpisodeClickAt = onEpisodeClickAt,
                                onEpisodeLongPressAt = onEpisodeMenuAt,
                            )
                            if (isDesktop && meta.type == "series") {
                                Box(Modifier.padding(top = 18.dp)) { seriesAudience() }
                            }
                        }
                    }
                }
                MetaScreenSectionKey.DETAILS -> {
                    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                        if (boardStyle) {
                            BoardDetailsFacts(meta = meta, isSeries = meta.type == "series" || hasEpisodes)
                        } else if (hasAdditionalInfoSection) {
                            DetailAdditionalInfoSection(meta = meta, showHeader = showHeader)
                        }
                        // Under About / More / Links on the board, once Wikidata has answered.
                        val behind = behindTheFilm
                        if (boardStyle && behind != null) {
                            BehindTheFilmTile(
                                info = behind,
                                isSeries = meta.type == "series" || hasEpisodes,
                                onPerson = onCastClick?.let { open -> { person -> open(person, null) } },
                            )
                        }
                    }
                }
                MetaScreenSectionKey.COLLECTION -> {
                    if (!hasEpisodes && hasCollectionSection) {
                        DetailPosterRailSection(
                            title = meta.collectionName.orEmpty(),
                            items = meta.collectionItems,
                            watchedKeys = watchedKeys,
                            fullyWatchedSeriesKeys = fullyWatchedSeriesKeys,
                            showHeader = showHeader,
                            horizontalScrollPadding = horizontalScrollPadding,
                            onPosterClick = onOpenMeta,
                        )
                    }
                }
                MetaScreenSectionKey.GALLERY -> {
                    if (boardStyle && gallery.size >= GalleryMinimumImages) {
                        BoardGallery(images = gallery, title = meta.name, onSetBackground = onSetBackground)
                    }
                }
                MetaScreenSectionKey.MORE_LIKE_THIS -> {
                    if (hasMoreLikeThisSection) {
                        val sourceLabel = when (meta.moreLikeThisSource) {
                            MoreLikeThisSource.TMDB -> stringResource(Res.string.detail_more_like_this_powered_by_tmdb)
                            MoreLikeThisSource.TRAKT -> stringResource(Res.string.detail_more_like_this_powered_by_trakt)
                            MoreLikeThisSource.SIMKL -> stringResource(Res.string.detail_more_like_this_powered_by_simkl)
                            null -> null
                        }
                        if (boardStyle && onMoreLikeThisUnwatchedOnlyChange != null) {
                            BoardMoreLikeThis(
                                items = if (moreLikeThisUnwatchedOnly) moreLikeThisUnwatched.orEmpty() else meta.moreLikeThis,
                                unwatchedOnly = moreLikeThisUnwatchedOnly,
                                onUnwatchedOnlyChange = onMoreLikeThisUnwatchedOnlyChange,
                                watchedKeys = watchedKeys,
                                fullyWatchedSeriesKeys = fullyWatchedSeriesKeys,
                                sourceLabel = sourceLabel,
                                horizontalScrollPadding = horizontalScrollPadding,
                                onPosterClick = onOpenMeta,
                            )
                        } else DetailPosterRailSection(
                            title = stringResource(Res.string.details_more_like_this),
                            items = meta.moreLikeThis,
                            watchedKeys = watchedKeys,
                            fullyWatchedSeriesKeys = fullyWatchedSeriesKeys,
                            showHeader = showHeader,
                            horizontalScrollPadding = horizontalScrollPadding,
                            sourceLabel = sourceLabel,
                            onPosterClick = onOpenMeta,
                        )
                    }
                }
            }
        }

        if (!settings.tabLayout) {
            // Standard mode: render sections individually in order
            enabledItems.forEach { section -> RenderSection(section.key, showHeader = showHeaders) }
        } else {
            // Tab layout mode: group sections by tabGroup, render grouped ones as tabs
            val processedGroups = mutableSetOf<Int>()

            enabledItems.forEach { section ->
                val groupId = section.tabGroup
                if (groupId == null) {
                    // Standalone section
                    RenderSection(section.key)
                } else if (groupId !in processedGroups) {
                    // First encounter of this group — render the whole tabbed group
                    processedGroups.add(groupId)
                    val groupMembers = enabledItems
                        .filter { it.tabGroup == groupId && sectionHasContent(it.key) }
                    if (groupMembers.isEmpty()) return@forEach
                    if (groupMembers.size == 1) {
                        // Only one member with content — render standalone
                        RenderSection(groupMembers.first().key)
                    } else {
                        TabbedSectionGroup(
                            tabs = groupMembers.map { it.key to it.title },
                        ) { activeKey ->
                            RenderSection(activeKey, showHeader = false)
                        }
                    }
                }
                // else: already processed as part of group, skip
            }
        }
    }
}


@Composable
private fun TabbedSectionGroup(
    tabs: List<Pair<MetaScreenSectionKey, String>>,
    content: @Composable (MetaScreenSectionKey) -> Unit,
) {
    if (tabs.isEmpty()) return

    var selectedIndex by remember { mutableIntStateOf(0) }
    val clampedIndex = selectedIndex.coerceIn(0, tabs.lastIndex)
    if (clampedIndex != selectedIndex) selectedIndex = clampedIndex

    val headerColor = MaterialTheme.colorScheme.onBackground

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Tab row using the same style as DetailSectionTitle
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val titleSize = if (maxWidth >= 720.dp) 22.sp else 20.sp
            val headerStyle = MaterialTheme.typography.titleLarge.copy(
                fontSize = titleSize,
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                tabs.forEachIndexed { index, (_, title) ->
                    if (index > 0) {
                        Text(
                            text = "|",
                            style = headerStyle,
                            color = headerColor.copy(alpha = 0.45f),
                            modifier = Modifier.padding(horizontal = 10.dp),
                        )
                    }

                    Text(
                        text = title,
                        style = headerStyle,
                        color = if (index == selectedIndex) {
                            headerColor
                        } else {
                            headerColor.copy(alpha = 0.55f)
                        },
                        maxLines = 1,
                        modifier = Modifier
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) { selectedIndex = index },
                    )
                }
            }
        }

        // Content with crossfade
        Crossfade(
            targetState = tabs[selectedIndex].first,
            animationSpec = tween(durationMillis = 200),
            label = "tabbedSectionCrossfade",
        ) { activeKey ->
            content(activeKey)
        }
    }
}
