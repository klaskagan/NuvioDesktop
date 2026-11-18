package com.nuvio.app.core.ui

import androidx.compose.runtime.*
import dev.chrisbanes.haze.HazeState

/** Feature panes publish here; the app shell renders above navigation using its blur source. */
internal class PosterZoomOverlayHostState {
    var content by mutableStateOf<(@Composable (HazeState) -> Unit)?>(null)
        private set
    private var owner: Any? = null

    fun show(owner: Any, content: @Composable (HazeState) -> Unit) {
        this.owner = owner
        this.content = content
    }

    fun clear(owner: Any) {
        if (this.owner === owner) {
            this.owner = null
            content = null
        }
    }
}

internal val LocalPosterZoomOverlayHost = staticCompositionLocalOf<PosterZoomOverlayHostState?> { null }

@Composable
internal fun PosterZoomOverlayPortal(content: @Composable (HazeState) -> Unit) {
    val host = checkNotNull(LocalPosterZoomOverlayHost.current) { "Poster actions require the app shell overlay host" }
    val owner = remember { Any() }
    val currentContent by rememberUpdatedState(content)
    DisposableEffect(host, owner) {
        host.show(owner) { haze -> currentContent(haze) }
        onDispose { host.clear(owner) }
    }
}
