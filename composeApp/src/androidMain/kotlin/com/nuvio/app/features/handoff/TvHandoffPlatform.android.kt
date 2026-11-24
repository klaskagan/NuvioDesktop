package com.nuvio.app.features.handoff

internal actual fun openTvHandoff(
    manageDevices: Boolean,
    capture: (String, String) -> TvHandoffRequest?, onTransferred: () -> Unit, onResumeHere: () -> Unit,
): () -> Unit = {}

@androidx.compose.runtime.Composable
internal actual fun PairedTvSettings(onBack: () -> Unit) = Unit

internal actual val tvHandoffSupported: Boolean = false
internal actual fun tvHandoffPlaybackView(): TvHandoffPlaybackView = TvHandoffPlaybackView(emptyList())
internal actual fun selectTvHandoffDevice(index: Int) = Unit
internal actual fun observeTvHandoffPlayback(owner: Any, changed: () -> Unit) = Unit
internal actual fun stopObservingTvHandoffPlayback(owner: Any) = Unit
