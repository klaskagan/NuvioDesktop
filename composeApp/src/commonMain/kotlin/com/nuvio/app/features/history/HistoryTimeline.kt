package com.nuvio.app.features.history

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.nuvio.app.features.tracking.TrackingHistoryRemover
import com.nuvio.app.features.tracking.TrackingMediaKind
import com.nuvio.app.features.tracking.TrackingHistoryEvent
import com.nuvio.app.features.tracking.TrackingHistorySource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

internal class HistoryTimeline(private val source: TrackingHistorySource, private val kind: TrackingMediaKind? = null) {
    var events by mutableStateOf(emptyList<TrackingHistoryEvent>())
        private set
    var loaded by mutableStateOf(false)
        private set
    var loading by mutableStateOf(false)
        private set
    var failed by mutableStateOf(false)
        private set
    var nextCursor by mutableStateOf<String?>(null)
        private set
    private var failedCursor: String? = null
    private var requestGeneration = 0L

    var removing by mutableStateOf(false)
        private set
    var removalFailed by mutableStateOf(false)
        private set
    private val removedIds = mutableSetOf<String>()

    suspend fun remove(eventId: String) {
        val remover = source.provider as? TrackingHistoryRemover ?: return
        if (removing || source.provider.account.value != source.account) return
        removing = true
        removalFailed = false
        try {
            remover.removeEvent(source.account.profileId, eventId)
            currentCoroutineContext().ensureActive()
            if (source.provider.account.value != source.account) return
            removedIds += eventId
            events = events.filterNot { it.id == eventId }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            if (source.provider.account.value == source.account) removalFailed = true
        } finally {
            removing = false
        }
    }

    suspend fun retry() = load(failedCursor)

    suspend fun load(cursor: String? = null) {
        if (source.provider.account.value != source.account) return
        val generation = ++requestGeneration
        loading = true
        failed = false
        try {
            var page = source.provider.loadFilteredPage(source.account.profileId, cursor, kind)
            val visited = mutableSetOf<String?>(cursor)
            while (page.events.isEmpty() && page.nextCursor != null && visited.add(page.nextCursor)) {
                currentCoroutineContext().ensureActive()
                if (source.provider.account.value != source.account) return
                page = source.provider.loadFilteredPage(source.account.profileId, page.nextCursor, kind)
            }
            currentCoroutineContext().ensureActive()
            if (generation != requestGeneration || source.provider.account.value != source.account) return

            val previous = if (cursor == null) emptyList() else events
            events = (previous + page.events).filterNot { it.id in removedIds }.distinctBy { it.id }.sortedByDescending { it.watchedAtMillis }
            nextCursor = page.nextCursor?.takeUnless { it == cursor }
            loaded = true
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            currentCoroutineContext().ensureActive()
            if (generation == requestGeneration && source.provider.account.value == source.account) {
                failedCursor = cursor
                failed = true
            }
        } finally {
            if (generation == requestGeneration) loading = false
        }
    }
}
