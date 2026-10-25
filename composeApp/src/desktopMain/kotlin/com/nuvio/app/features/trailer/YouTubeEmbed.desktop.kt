package com.nuvio.app.features.trailer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.nuvio.app.core.ui.NuvioModalBottomSheet
import com.nuvio.app.core.ui.NuvioLoadingIndicator
import com.nuvio.app.core.ui.nuvioSafeBottomPadding
import com.nuvio.app.features.player.FullscreenPlayerDialog
import com.nuvio.app.features.player.desktop.*
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import javax.swing.SwingUtilities
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource

internal actual object YouTubeEmbed {
    actual val supported = DesktopHostOs.current == DesktopHostOs.MACOS

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable actual fun Popup(
        videoUrl: String?, title: String, type: String, contentTitle: String,
        loading: Boolean, error: String?, onDismiss: () -> Unit, onRetry: (() -> Unit)?,
    ) {
        var retry by remember(videoUrl) { mutableIntStateOf(0) }
        var expanded by remember { mutableStateOf(false) }
        val id = videoUrl?.let(::youtubeEmbedVideoId)
        val session = remember(id, retry) { id?.let { YouTubeSession(it, controls = true, muted = false, startMs = 0) } }
        com.nuvio.app.features.player.EnterImmersivePlayerMode(keepScreenAwake = session?.isPlaying == true)
        DisposableEffect(session) { onDispose { session?.close() } }
        val latestDismiss by rememberUpdatedState(onDismiss)
        SideEffect { session?.onEscape = { if (expanded) expanded = false else latestDismiss() } }
        val header: @Composable () -> Unit = {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(type.ifBlank { stringResource(Res.string.detail_tab_trailer) }, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
                    Text(listOf(title, contentTitle).filter { it.isNotBlank() }.joinToString(" • "), maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(if (expanded) Icons.Rounded.FullscreenExit else Icons.Rounded.Fullscreen, stringResource(if (expanded) Res.string.trailer_exit_fullscreen else Res.string.trailer_enter_fullscreen), tint = MaterialTheme.colorScheme.onSurface)
                }
                IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, stringResource(Res.string.trailer_close), tint = MaterialTheme.colorScheme.onSurface) }
            }
        }
        val content: @Composable (Modifier) -> Unit = { modifier ->
            Box(modifier.background(Color.Black), contentAlignment = Alignment.Center) {
                val failure = error ?: session?.error
                if (failure != null) Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                    Text(stringResource(Res.string.trailer_unable_to_play), color = Color.White)
                    Text(failure, color = Color.White.copy(alpha = .7f))
                    TextButton(onClick = { retry++; onRetry?.invoke() }) { Text(stringResource(Res.string.action_retry)) }
                } else if (session != null) YouTubeSurface(session, Modifier.fillMaxSize(), cornerRadius = if (expanded) 0.0 else 18.0)
                else if (loading) NuvioLoadingIndicator()
            }
        }
        if (expanded) FullscreenPlayerDialog(onDismiss = { expanded = false }) {
            Column(Modifier.fillMaxSize().zIndex(3f).background(MaterialTheme.colorScheme.surface)) {
                header()
                content(Modifier.fillMaxWidth().weight(1f))
            }
        } else NuvioModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            Column(Modifier.fillMaxWidth().padding(bottom = nuvioSafeBottomPadding(14.dp))) {
                header()
                content(Modifier.fillMaxWidth().padding(horizontal = 16.dp).aspectRatio(16f / 9f))
            }
        }
    }
}

@Composable
internal fun YouTubeSurface(session: YouTubeSession, modifier: Modifier, cornerRadius: Double = 18.0) {
    val host = remember { NativePlayerHost(embedded = true) }
    var mounted by remember { mutableStateOf(false) }
    SideEffect { host.cornerRadius = cornerRadius; host.updateEmbeddedBounds() }
    DisposableEffect(host, session) {
        host.onFirstFullSizePaint = { mounted = true }
        host.onBoundsChanged = { session.layout(host) }
        onDispose { session.hide(host); host.onBoundsChanged = null }
    }
    SwingPanel(modifier = modifier, background = Color.Black, factory = { host }, update = { it.updateEmbeddedBounds() })
    LaunchedEffect(mounted) {
        if (mounted) {
            host.updateEmbeddedBounds()
            runCatching { AwtNativeViewResolver.resolveNativeViewPointer(host) }
                .onSuccess { session.attach(host, it) }
                .onFailure { session.fail("YouTube player could not attach to this window.") }
        }
    }
    LaunchedEffect(session) {
        delay(25_000)
        if (!session.ready && !session.autoplayBlocked && session.error == null) session.fail("YouTube did not start. Please retry.")
    }
}

internal class YouTubeSession(val videoId: String, val controls: Boolean, muted: Boolean, val startMs: Long) {
    var ready by mutableStateOf(false); private set
    var isPlaying by mutableStateOf(false); private set
    var autoplayBlocked by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set
    var positionMs by mutableStateOf(0L); private set
    var onEscape: () -> Unit = {}
    @Volatile private var active = true
    @Volatile internal var handle = 0L; private set
    private var host: NativePlayerHost? = null
    private var parent = 0L
    private var creating = false
    private var desiredPlay = true
    private var desiredMuted = muted
    private val sink = NativePlayerEventSink { type, value ->
        SwingUtilities.invokeLater {
            if (active) when (type) {
                "state" -> { isPlaying = value == 1.0; if (isPlaying) ready = true }
                "time" -> positionMs = (value * 1_000).toLong()
                "error" -> fail(when (value.toInt()) {
                    101, 150 -> "This uploader does not allow embedded playback."
                    100 -> "This video is private or unavailable."
                    153 -> "YouTube could not identify this app."
                    else -> "YouTube playback failed (${value.toInt()})."
                })
                "escape" -> onEscape()
                "blocked" -> autoplayBlocked = true // Keep YouTube's play button available.
            }
        }
    }
    suspend fun attach(newHost: NativePlayerHost, pointer: Long) {
        host = newHost; parent = pointer
        if (handle != 0L) { layout(newHost); return }
        if (creating || !active) return
        creating = true
        val bounds = newHost.embeddedBounds
        try {
            withContext(NonCancellable) {
            val created = withContext(Dispatchers.IO) {
                NativePlayerBridge.ensureLoaded()
                YouTubeNativeBridge.create(pointer, bounds, videoId, controls, desiredMuted, startMs / 1_000.0, sink)
            }
            if (!active) YouTubeNativeBridge.close(created)
            else { handle = created; host?.let(::layout); sendConfiguration() }
            }
        } catch (failure: Exception) { fail("YouTube player could not open (${failure.javaClass.simpleName}).") }
        finally { creating = false }
    }
    fun configure(play: Boolean, muted: Boolean) {
        if (desiredPlay == play && desiredMuted == muted) return
        desiredPlay = play; desiredMuted = muted; sendConfiguration()
    }
    private fun sendConfiguration() {
        if (active && handle != 0L) YouTubeNativeBridge.configure(handle, desiredPlay, desiredMuted)
    }
    fun layout(candidate: NativePlayerHost) {
        if (active && host === candidate && handle != 0L) YouTubeNativeBridge.layout(handle, parent, candidate.embeddedBounds, candidate.isShowing && error == null)
    }
    fun hide(candidate: NativePlayerHost) {
        if (host === candidate && handle != 0L) YouTubeNativeBridge.layout(handle, parent, candidate.embeddedBounds, false)
    }
    fun fail(message: String) {
        if (!active) return
        isPlaying = false
        error = message
        // Hiding alone allows a late iframe startup to play audio behind Retry.
        // Retire the entire session; retry creates a fresh lifetime and WebView.
        close()
    }
    fun close() {
        active = false
        val old = handle; handle = 0
        if (old != 0L) YouTubeNativeBridge.close(old)
    }
}

internal object YouTubeNativeBridge {
    external fun create(parent: Long, bounds: DoubleArray, videoId: String, controls: Boolean, muted: Boolean, startSeconds: Double, sink: NativePlayerEventSink): Long
    external fun layout(handle: Long, parent: Long, bounds: DoubleArray, visible: Boolean)
    external fun configure(handle: Long, play: Boolean, muted: Boolean)
    external fun close(handle: Long)
    external fun bounds(handle: Long): DoubleArray
}
