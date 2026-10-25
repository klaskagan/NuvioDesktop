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
import androidx.compose.ui.platform.LocalWindowInfo
import java.awt.KeyboardFocusManager
import java.awt.KeyEventDispatcher
import java.awt.event.KeyEvent
import java.awt.Desktop
import java.net.URI
import androidx.compose.ui.zIndex
import com.nuvio.app.core.ui.NuvioModalBottomSheet
import com.nuvio.app.core.ui.NuvioLoadingIndicator
import com.nuvio.app.core.ui.nuvioSafeBottomPadding
import com.nuvio.app.features.player.FullscreenPlayerDialog
import com.nuvio.app.features.player.desktop.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
        val initialId = videoUrl?.let(::youtubeEmbedVideoId)
        var retryId by remember(videoUrl) { mutableStateOf<String?>(null) }
        val id = retryId ?: initialId
        val session = remember(id, retry) { id?.let { YouTubeSession(it, controls = true, muted = false, startMs = 0) } }
        com.nuvio.app.features.player.EnterImmersivePlayerMode(keepScreenAwake = session?.isPlaying == true)
        DisposableEffect(session) { onDispose { session?.close() } }
        val latestDismiss by rememberUpdatedState(onDismiss)
        val escape by rememberUpdatedState({ if (expanded) expanded = false else latestDismiss() })
        val keyboard = remember { YouTubeEscapeSession() }
        DisposableEffect(keyboard) { onDispose { keyboard.close() } }
        SideEffect {
            keyboard.onEscape = { escape() }
            session?.onWindowAttached = keyboard::attach
            session?.onEscape = { escape() }
            session?.onToggleFullscreen = { expanded = !expanded }
        }
        val header: @Composable () -> Unit = {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(if (session != null && session.currentVideoId != initialId) "YouTube" else type.ifBlank { stringResource(Res.string.detail_tab_trailer) }, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
                    Text(if (session != null && session.currentVideoId != initialId) session.currentTitle ?: "YouTube" else listOf(title, contentTitle).filter { it.isNotBlank() }.joinToString(" • "), maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(if (expanded) Icons.Rounded.FullscreenExit else Icons.Rounded.Fullscreen, stringResource(if (expanded) Res.string.trailer_exit_fullscreen else Res.string.trailer_enter_fullscreen), tint = MaterialTheme.colorScheme.onSurface)
                }
                IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, stringResource(Res.string.trailer_close), tint = MaterialTheme.colorScheme.onSurface) }
            }
        }
        val content: @Composable (Modifier) -> Unit = { modifier ->
            Box(modifier.background(Color.Black), contentAlignment = Alignment.Center) {
                YouTubeEscapeHandler(escape)
                val failure = error ?: session?.error
                if (failure != null) Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                    Text(stringResource(Res.string.trailer_unable_to_play), color = Color.White)
                    Text(failure, color = Color.White.copy(alpha = .7f))
                    if (session != null) TextButton(onClick = session::openInYouTube) { Text(stringResource(Res.string.trailer_open_youtube)) }
                    TextButton(onClick = { retryId = session?.currentVideoId; retry++; onRetry?.invoke() }) { Text(stringResource(Res.string.action_retry)) }
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

/** Resolve focus in the owning presentation: a bottom sheet has its own window. */
@Composable
private fun YouTubeEscapeHandler(onEscape: () -> Unit) {
    val escape by rememberUpdatedState(onEscape)
    val windowInfo = LocalWindowInfo.current
    DisposableEffect(windowInfo) {
        val focusManager = KeyboardFocusManager.getCurrentKeyboardFocusManager()
        val dispatcher = KeyEventDispatcher { event ->
            if (windowInfo.isWindowFocused && event.id == KeyEvent.KEY_PRESSED && event.keyCode == KeyEvent.VK_ESCAPE) {
                escape(); true
            } else false
        }
        focusManager.addKeyEventDispatcher(dispatcher)
        onDispose { focusManager.removeKeyEventDispatcher(dispatcher) }
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
    LaunchedEffect(session, session.currentVideoId) {
        delay(25_000)
        if (!session.ready && !session.autoplayBlocked && session.error == null) session.fail("YouTube did not start. Please retry.")
    }
}

/** Presentation lifetime outlives a failed/retried WebView. Owns no native view. */
internal class YouTubeEscapeSession {
    var onEscape: () -> Unit = {}
    @Volatile private var active = true
    private var handle = 0L
    private var parent = 0L
    private val mutex = Mutex()
    private val sink = NativePlayerEventSink { type, _ ->
        SwingUtilities.invokeLater { if (active && type == "escape") onEscape() }
    }
    suspend fun attach(pointer: Long) = mutex.withLock {
        if (!active || handle != 0L && parent == pointer) return@withLock
        withContext(NonCancellable) {
            val created = withContext(Dispatchers.IO) {
                NativePlayerBridge.ensureLoaded()
                YouTubeNativeBridge.createEscapeMonitor(pointer, sink)
            }
            if (!active) YouTubeNativeBridge.close(created)
            else {
                val old = handle; handle = created; parent = pointer
                if (old != 0L) YouTubeNativeBridge.close(old)
            }
        }
    }
    fun close() {
        active = false
        val old = handle; handle = 0L
        if (old != 0L) YouTubeNativeBridge.close(old)
    }
}

internal class YouTubeSession(
    val videoId: String, val controls: Boolean, muted: Boolean, val startMs: Long,
    private val openBrowser: (String) -> Unit = { Desktop.getDesktop().browse(URI(it)) },
) {
    var currentVideoId by mutableStateOf(videoId); private set
    var currentTitle by mutableStateOf<String?>(null); private set
    var ready by mutableStateOf(false); private set
    var isPlaying by mutableStateOf(false); private set
    var autoplayBlocked by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set
    var positionMs by mutableStateOf(0L); private set
    var onWindowAttached: suspend (Long) -> Unit = {}
    var onEscape: () -> Unit = {}
    var onToggleFullscreen: () -> Unit = {}
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
                "state" -> { isPlaying = value == 1.0; if (isPlaying || value == 2.0) ready = true }
                "time" -> positionMs = (value * 1_000).toLong()
                "error" -> fail(when (value.toInt()) {
                    101, 150 -> "This uploader does not allow embedded playback."
                    100 -> "This video is private or unavailable."
                    153 -> "YouTube could not identify this app."
                    else -> "YouTube playback failed (${value.toInt()})."
                })
                "escape" -> onEscape()
                "toggleFullscreen" -> onToggleFullscreen()
                "blocked" -> autoplayBlocked = true // Keep YouTube's play button available.
                else -> when {
                    type.startsWith("link:") -> navigate(type.removePrefix("link:"), value.toInt())
                    type.startsWith("video:") -> {
                        val next = type.removePrefix("video:").takeIf { it.matches(Regex("[A-Za-z0-9_-]{11}")) }
                        if (next != null && next != currentVideoId) {
                            currentVideoId = next; currentTitle = null; ready = false; positionMs = 0
                        }
                    }
                    type.startsWith("title:") -> currentTitle = type.removePrefix("title:")
                }
            }
        }
    }
    suspend fun attach(newHost: NativePlayerHost, pointer: Long) {
        host = newHost; parent = pointer
        onWindowAttached(pointer)
        if (handle != 0L) { layout(newHost); return }
        if (creating || !active) return
        creating = true
        val bounds = newHost.embeddedBounds
        try {
            withContext(NonCancellable) {
            val created = withContext(Dispatchers.IO) {
                NativePlayerBridge.ensureLoaded()
                YouTubeNativeBridge.create(pointer, bounds, videoId, controls, desiredMuted, startMs / 1_000.0,
                    checkNotNull(YouTubeNativeBridge.javaClass.getResourceAsStream("/player-ui/youtube-interactions.js")).bufferedReader().use { it.readText() }, sink)
            }
            if (!active) YouTubeNativeBridge.close(created)
            else { handle = created; host?.let(::layout); sendConfiguration() }
            }
        } catch (failure: Exception) { fail("YouTube player could not open (${failure.javaClass.simpleName}).") }
        finally { creating = false }
    }
    internal fun navigate(url: String, mode: Int = 0) {
        when (val destination = routeYouTubeLink(url, currentVideoId, recommendation = mode == 2, external = mode == 1)) {
            is YouTubeLinkDestination.Embedded -> {
                if (active && handle != 0L) YouTubeNativeBridge.loadVideo(handle, destination.videoId)
            }
            is YouTubeLinkDestination.Browser -> {
                // The iframe's own controls can change play state independently
                // of the last Kotlin intent. Every browser action must pause.
                desiredPlay = false
                if (active && handle != 0L) YouTubeNativeBridge.pause(handle)
                openBrowser(destination.url)
            }
            YouTubeLinkDestination.Ignore -> Unit
        }
    }
    fun openInYouTube() { navigate("https://www.youtube.com/watch?v=$currentVideoId", mode = 1) }
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
    external fun createEscapeMonitor(parent: Long, sink: NativePlayerEventSink): Long
    external fun create(parent: Long, bounds: DoubleArray, videoId: String, controls: Boolean, muted: Boolean, startSeconds: Double, interactionsScript: String, sink: NativePlayerEventSink): Long
    external fun layout(handle: Long, parent: Long, bounds: DoubleArray, visible: Boolean)
    external fun configure(handle: Long, play: Boolean, muted: Boolean)
    external fun loadVideo(handle: Long, videoId: String)
    external fun pause(handle: Long)
    external fun close(handle: Long)
    /** Test fixture only: caller must use isolated test storage. */
    external fun downloadFixture(handle: Long, sourceUrl: String, destinationPath: String)
    external fun bounds(handle: Long): DoubleArray
}
