package com.nuvio.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalWindowInfo
import java.nio.file.Files
import javax.swing.SwingUtilities

@Composable
internal actual fun PlatformHistoryGesturePhaseObserver(onPhase: (HistoryGesturePhase) -> Unit) {
    val latest by rememberUpdatedState(onPhase)
    val focused = LocalWindowInfo.current.isWindowFocused
    DisposableEffect(focused) {
        val unsubscribe = if (focused) MacosHistoryGestureBridge.subscribe { latest(it) } else null
        onDispose {
            unsubscribe?.invoke()
            latest(HistoryGesturePhase.Cancelled)
        }
    }
}

/** Local AppKit monitor observes phases only; Compose continues to route/scroll all wheel events. */
internal object MacosHistoryGestureBridge {
    private val available: Boolean by lazy {
        if (!System.getProperty("os.name").contains("mac", ignoreCase = true)) false
        else runCatching {
            val arch = when (System.getProperty("os.arch").lowercase()) {
                "aarch64", "arm64" -> "arm64"
                else -> "x86_64"
            }
            val resource = "/nuvio/native/macos/$arch/libhistory_gestures.dylib"
            val directory = Files.createTempDirectory("nuvio-history-gestures").toFile().apply { deleteOnExit() }
            val library = directory.resolve("libhistory_gestures.dylib").apply { deleteOnExit() }
            checkNotNull(javaClass.getResourceAsStream(resource)) { "Missing $resource" }.use { input ->
                library.outputStream().use { input.copyTo(it) }
            }
            System.load(library.absolutePath)
            true
        }.getOrElse {
            System.err.println("Native history gesture phases unavailable: ${it.message}")
            false
        }
    }

    fun subscribe(onPhase: (HistoryGesturePhase) -> Unit): (() -> Unit)? {
        if (!available) return null
        val callback = PhaseCallback(onPhase)
        val token = installNative(callback)
        if (token == 0L) return null
        return {
            callback.active = false
            uninstallNative(token)
        }
    }

    internal class PhaseCallback(private val handler: (HistoryGesturePhase) -> Unit) {
        @Volatile var active = true
        @Suppress("unused") // Called through JNI on AppKit's main thread.
        fun onPhase(value: Int) {
            val phase = HistoryGesturePhase.entries.getOrNull(value) ?: return
            SwingUtilities.invokeLater { if (active) handler(phase) }
        }
    }

    private external fun installNative(callback: PhaseCallback): Long
    private external fun uninstallNative(token: Long)
}
