package com.nuvio.app.features.handoff

/** Settings manages pairings; playback captures only when a saved TV is selected. */
internal expect fun openTvHandoff(
    manageDevices: Boolean = false,
    capture: (targetDeviceId: String, senderDeviceId: String) -> TvHandoffRequest?,
    onTransferred: () -> Unit,
    onResumeHere: () -> Unit,
): () -> Unit

/** Embedded in the existing Settings navigation surface. */
@androidx.compose.runtime.Composable
internal expect fun PairedTvSettings(onBack: () -> Unit)
