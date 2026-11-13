package com.nuvio.app.features.player.desktop

import com.nuvio.app.features.player.PlayerControlsState
import kotlin.test.*

class BufferedQualityControlsTest {
    @Test fun promotionPublishesTargetEvenWhenMainControlsStillDescribePreviousStream() {
        val previous = PlayerControlsState(qualityRequestedHeight = 720, qualityActualHeight = 720, qualityBusy = false)
        val promoted = bufferedQualityPromotionControls(previous, 1080)
        assertEquals(1080, promoted.qualityRequestedHeight)
        assertTrue(promoted.qualityBusy)
        // Actual dimensions must remain honest until a new decoder snapshot arrives.
        assertEquals(720, promoted.qualityActualHeight)
    }
    @Test fun aDowngradeAlsoReplacesAnOlderPendingTarget() {
        val previous = PlayerControlsState(qualityRequestedHeight = 2160, qualityActualHeight = 1080, qualityBusy = true)
        val promoted = bufferedQualityPromotionControls(previous, 720)
        assertEquals(720, promoted.qualityRequestedHeight)
        assertTrue(promoted.qualityBusy)
    }
    @Test fun delayedJavascriptRefreshCannotExposeOutgoingQualityAtPromotion() {
        val previous = PlayerControlsState(
            qualityRequestedHeight = 720, qualityActualHeight = 720,
            qualityBusy = false, controlsVisible = true,
        )
        val staging = DelayedControlsDocument(bufferedQualityPreparationControls(previous, 1080))
        assertFalse(staging.applied.controlsVisible)
        assertEquals(1080, staging.applied.qualityRequestedHeight)
        assertTrue(staging.applied.qualityBusy)

        // WKWebView evaluates asynchronously: native visibility can change before
        // the promotion refresh has reached the document, even when queued first.
        staging.enqueue(bufferedQualityPromotionControls(previous, 1080))
        staging.revealAndPaint()
        staging.completeJavascriptAndPaint()
        staging.enqueue(previous.copy(qualityRequestedHeight = 1080, qualityActualHeight = 1080))
        staging.completeJavascriptAndPaint()

        assertEquals(listOf(1080, 1080, 1080), staging.painted.map { it.qualityRequestedHeight })
        assertEquals(listOf(true, true, false), staging.painted.map { it.qualityBusy })
        assertEquals(listOf(720, 720, 1080), staging.painted.map { it.qualityActualHeight })
    }

    @Test fun preparationDoesNotInheritAnObsoletePendingDowngradeOrVisibleChrome() {
        val obsolete = PlayerControlsState(
            qualityRequestedHeight = 2160, qualityActualHeight = 1080,
            qualityBusy = true, controlsVisible = true,
        )
        val prepared = bufferedQualityPreparationControls(obsolete, 720)
        assertEquals(720, prepared.qualityRequestedHeight)
        assertTrue(prepared.qualityBusy)
        assertFalse(prepared.controlsVisible)
        assertEquals(1080, prepared.qualityActualHeight)
    }

    private class DelayedControlsDocument(var applied: PlayerControlsState) {
        private val queued = ArrayDeque<PlayerControlsState>()
        val painted = mutableListOf<PlayerControlsState>()
        private var visible = false
        fun enqueue(state: PlayerControlsState) { queued.addLast(state) }
        fun revealAndPaint() { visible = true; painted += applied }
        fun completeJavascriptAndPaint() {
            applied = queued.removeFirst()
            if (visible) painted += applied
        }
    }

}
