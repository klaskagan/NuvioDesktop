package com.nuvio.app.features.home.components

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PreparedArtworkStorePinningTest {
    @Test fun preparedMetadataCannotRevealLaterGridRowBeforeEarlierImagesAreReady() {
        val first = com.nuvio.app.features.home.MetaPreview("first", "movie", "First")
        val second = com.nuvio.app.features.home.MetaPreview("second", "movie", "Second")
        val store = PreparedArtworkStore()
        store.rows(listOf(ArtworkPrefetchRow("first-row", listOf(first)), ArtworkPrefetchRow("second-row", listOf(second))))
        store.visibleRows = listOf("first-row", "second-row")
        store.complete("movie:first", LandscapeMediaArtworkState())
        store.complete("movie:second", LandscapeMediaArtworkState())
        val gate = LandscapeRowRevealState(setOf("movie:first"))
        store.revealRows(mapOf("first-row" to gate))
        assertFalse(store.mayReveal("movie:second"), "Metadata readiness does not imply the image painter is ready")
        gate.report("movie:first", true)
        assertTrue(store.mayReveal("movie:second"))
    }

    @Test fun paginationKeepsRevealedPartialRowsVisible() {
        val gate = LandscapeRowRevealState(setOf("movie:first"))
        gate.report("movie:first", true)
        assertTrue(gate.revealed)
        gate.updateExpectedKeys(setOf("movie:first", "movie:appended"))
        assertTrue(gate.revealed, "Appending the next page must not hide existing artwork")
        val pending = LandscapeRowRevealState(setOf("movie:first", "movie:removed"))
        pending.report("movie:first", true)
        pending.updateExpectedKeys(setOf("movie:first"))
        assertTrue(pending.revealed, "Removed members cannot hold a pending row forever")
    }

    @Test fun offscreenHorizontalLookaheadCannotBlockFollowingVisibleRow() {
        val first = (0..6).map { com.nuvio.app.features.home.MetaPreview("cohort-$it", "movie", "Cohort $it") }
        val second = com.nuvio.app.features.home.MetaPreview("next", "movie", "Next")
        val store = PreparedArtworkStore()
        store.rows(listOf(ArtworkPrefetchRow("first", first), ArtworkPrefetchRow("second", listOf(second))))
        val keys = (first + second).map { "${it.type}:${it.id}" }
        store.manage(keys.toSet()); store.plan(keys)
        store.visibleRows = listOf("first", "second")
        keys.filterNot { it == "movie:cohort-6" }.forEach { store.complete(it, LandscapeMediaArtworkState()) }
        assertFalse(store.mayReveal("movie:next"))
        store.visibleCohort(keys.take(6))
        assertTrue(store.mayReveal("movie:next"), "Only the actual six visible cards may hold the next row; seventh is prefetch")
    }

    @Test fun adaptiveWideGridKeepsAllPlannedResultsReadyBeyondHistoryCapacity() {
        val keys = (0 until 200).map { "movie:$it" }
        val store = PreparedArtworkStore(historyCapacity = 128)
        store.manage(keys.toSet())
        store.plan(keys)
        keys.forEach { store.complete(it, LandscapeMediaArtworkState()) }
        assertTrue(keys.all { store[it]?.isLoading == false }, "Still-planned results must not revert to pending after 128 completions")
        val current = keys.takeLast(4)
        store.plan(current)
        assertTrue(current.all { store[it]?.isLoading == false }, "The current viewport remains ready after the lookahead shrinks")
        assertEquals(128, keys.count { store[it]?.isLoading == false }, "Old, unplanned results are bounded once they can be evicted")
        assertTrue(store[keys.first()]?.isLoading == true, "An evicted known offscreen item waits for its next viewport plan")
    }

    @Test fun childShelfKeepsReadyParentSlotsWhileAddingItsOwnHorizontalLookahead() {
        val parent = PreparedArtworkStore()
        parent.manage(setOf("shared"))
        parent.plan(listOf("shared"))
        parent.complete("shared", LandscapeMediaArtworkState())
        val child = PreparedArtworkStore(parent)
        child.manage(setOf("shared", "extra"))
        child.plan(listOf("extra"))
        assertFalse(child["shared"]!!.isLoading)
        assertTrue(child["extra"]!!.isLoading)
        child.complete("extra", LandscapeMediaArtworkState())
        assertFalse(child["shared"]!!.isLoading)
        assertFalse(child["extra"]!!.isLoading)
    }
}
