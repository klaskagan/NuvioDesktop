package com.nuvio.app.features.handoff

internal actual fun openTvHandoff(
    manageDevices: Boolean,
    capture: (String, String) -> TvHandoffRequest?, onTransferred: () -> Unit, onResumeHere: () -> Unit,
): () -> Unit = {}

@androidx.compose.runtime.Composable
internal actual fun PairedTvSettings(onBack: () -> Unit) = Unit
