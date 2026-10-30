package com.nuvio.app.features.player.desktop

import com.nuvio.app.features.player.PlayerControlsState

/** Seed the hidden document with its own target; native visibility does not await JS evaluation. */
internal fun bufferedQualityPreparationControls(state: PlayerControlsState, height: Int): PlayerControlsState =
    bufferedQualityPromotionControls(state, height).copy(controlsVisible = false)

/** Refresh the target at promotion, without relying on that asynchronous refresh to prevent a flash. */
internal fun bufferedQualityPromotionControls(state: PlayerControlsState, height: Int): PlayerControlsState =
    state.copy(qualityRequestedHeight = height, qualityBusy = true)
