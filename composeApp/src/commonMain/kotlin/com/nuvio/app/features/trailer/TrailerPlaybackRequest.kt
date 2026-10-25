package com.nuvio.app.features.trailer

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.trailer_no_playable_stream
import org.jetbrains.compose.resources.getString

/** Owns one selection's resolution; dismissed or superseded requests cannot publish results. */
internal class TrailerPlaybackRequest(
    private val scope: CoroutineScope,
    private val resolve: suspend (String, Boolean) -> TrailerPlaybackSource? = { url, refresh ->
        TrailerPlaybackResolver.resolveFromYouTubeUrl(url, refresh)
    },
    private val unavailableMessage: suspend () -> String = { getString(Res.string.trailer_no_playable_stream) },
) {
    var backend by mutableStateOf(TrailerPlayerBackend.Native)
        private set
    var source by mutableStateOf<TrailerPlaybackSource?>(null)
        private set
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var id by mutableIntStateOf(0)
        private set
    private var job: Job? = null

    fun start(url: String, forceRefresh: Boolean = false, backend: TrailerPlayerBackend = TrailerPlayerBackend.Native) {
        cancel()
        this.backend = backend
        loading = true
        val requestId = id
        job = scope.launch {
            val result = try {
                if (backend == TrailerPlayerBackend.YouTube) {
                    youtubeEmbedVideoId(url)?.let { TrailerPlaybackSource("https://www.youtube.com/watch?v=$it") }
                } else withTimeoutOrNull(35_000L) { resolve(url, forceRefresh) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
            val message = if (result == null) unavailableMessage() else null
            ensureActive()
            if (requestId != id) return@launch
            source = result
            error = message
            loading = false
        }
    }

    fun cancel() {
        id += 1
        job?.cancel()
        job = null
        source = null
        loading = false
        error = null
    }
}
