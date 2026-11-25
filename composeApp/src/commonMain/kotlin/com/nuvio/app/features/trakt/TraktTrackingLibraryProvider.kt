package com.nuvio.app.features.trakt

import com.nuvio.app.features.library.LibraryItem
import com.nuvio.app.features.library.LibrarySection
import com.nuvio.app.features.tracking.TrackingLibraryProvider
import com.nuvio.app.features.tracking.TrackingLibrarySnapshot
import com.nuvio.app.features.tracking.TrackingLibraryTab
import com.nuvio.app.features.tracking.TrackingLibraryTabKind
import com.nuvio.app.features.tracking.TrackingMembershipResolution
import com.nuvio.app.features.tracking.TrackingProviderId
import com.nuvio.app.features.tracking.TrackingRefreshIntent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

object TraktTrackingLibraryProvider : TrackingLibraryProvider {
    override val providerId: TrackingProviderId = TrackingProviderId.TRAKT
    override val changes: Flow<Unit> = TraktLibraryRepository.uiState.map { Unit }
    override val connectionRefreshIntent: TrackingRefreshIntent = TrackingRefreshIntent.INVALIDATED

    override fun ensureLoaded() = TraktLibraryRepository.ensureLoaded()

    override fun prepare() = TraktLibraryRepository.preloadListTabsAsync()

    override fun onProfileChanged() = TraktLibraryRepository.onProfileChanged()

    override fun clearLocalState() = TraktLibraryRepository.clearLocalState()

    override suspend fun refresh(intent: TrackingRefreshIntent) = when (intent) {
        TrackingRefreshIntent.AUTOMATIC -> TraktLibraryRepository.ensureFresh()
        TrackingRefreshIntent.USER_INITIATED,
        TrackingRefreshIntent.INVALIDATED,
        -> TraktLibraryRepository.refreshNow()
    }

    override fun snapshot(): TrackingLibrarySnapshot {
        val state = TraktLibraryRepository.uiState.value
        return TrackingLibrarySnapshot(
            items = state.allItems,
            sections = state.listTabs.map { tab ->
                LibrarySection(
                    type = tab.key,
                    displayTitle = tab.title,
                    items = state.entriesByList[tab.key].orEmpty(),
                )
            },
            tabs = state.listTabs.map { tab ->
                tab.toTrackingLibraryTab(peopleOnly = tab.holdsOnlyPeople(state.entriesByList[tab.key].orEmpty().size))
            },
            hasLoaded = state.hasLoaded,
            isLoading = state.isLoading,
            errorMessage = state.errorMessage,
        )
    }

    override fun contains(contentId: String, contentType: String?): Boolean {
        if (contentType != null) return TraktLibraryRepository.isInAnyList(contentId, contentType)
        val item = find(contentId) ?: return false
        return TraktLibraryRepository.isInAnyList(item.id, item.type)
    }

    // Library adds to the watchlist only, so a title in another list (For Nuvio) isn't "in Library".
    override fun isSavedByDefault(contentId: String, contentType: String): Boolean =
        TraktLibraryRepository.isInWatchlist(contentId, contentType)

    override fun find(contentId: String): LibraryItem? =
        TraktLibraryRepository.uiState.value.allItems.firstOrNull { item -> item.id == contentId }

    override suspend fun membership(item: LibraryItem): Map<String, Boolean> =
        TraktLibraryRepository.getMembershipSnapshot(item).listMembership

    override fun toggledDefaultMembership(
        currentMembership: Map<String, Boolean>,
    ): Map<String, Boolean> {
        val watchlistKey = snapshot().tabs
            .firstOrNull { tab -> tab.kind == TrackingLibraryTabKind.WATCHLIST }
            ?.key
            ?: return currentMembership
        return toggledTraktWatchlistMembership(currentMembership, watchlistKey)
    }

    override suspend fun applyMembership(
        profileId: Int,
        item: LibraryItem,
        desiredMembership: Map<String, Boolean>,
        destructiveRemovalConfirmed: Boolean,
    ): TrackingMembershipResolution? {
        TraktLibraryRepository.applyMembershipChanges(
            item = item,
            changes = TraktMembershipChanges(desiredMembership = desiredMembership),
        )
        return null
    }
}

internal fun toggledTraktWatchlistMembership(
    currentMembership: Map<String, Boolean>,
    watchlistKey: String,
): Map<String, Boolean> = currentMembership.toMutableMap().apply {
    this[watchlistKey] = currentMembership[watchlistKey] != true
}

/**
 * A personal list with items but no films or shows (Trakt's Favorite People) holds only people,
 * so no title can be saved to it.
 */
internal fun TraktListTab.holdsOnlyPeople(titleCount: Int): Boolean =
    type == TraktListType.PERSONAL && hasPeople && titleCount == 0

internal fun TraktListTab.toTrackingLibraryTab(peopleOnly: Boolean = false): TrackingLibraryTab =
    TrackingLibraryTab(
        key = key,
        title = title,
        providerId = TrackingProviderId.TRAKT,
        isMembershipDestination = isWritable && !peopleOnly,
        kind = when (type) {
            TraktListType.WATCHLIST -> TrackingLibraryTabKind.WATCHLIST
            TraktListType.PERSONAL -> TrackingLibraryTabKind.PERSONAL
            TraktListType.SMART, TraktListType.LIKED -> TrackingLibraryTabKind.EXTERNAL
        },
    )
