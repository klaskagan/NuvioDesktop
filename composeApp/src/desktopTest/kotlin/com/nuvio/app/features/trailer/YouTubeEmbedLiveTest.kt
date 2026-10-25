package com.nuvio.app.features.trailer

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.unit.dp
import com.nuvio.app.core.ui.NuvioTheme
import org.junit.Assume.assumeTrue
import org.junit.Test
import javax.swing.SwingUtilities

class YouTubeEmbedLiveTest {
    @Test fun embeddedPlayerRetainsSessionAcrossPresentationChanges() {
        assumeTrue(System.getenv("NUVIO_YOUTUBE_LIVE_TEST") == "1")
        check(System.getProperty("nuvio.test.isolatedHome") == "true")
        val ids = listOf("R5jcEBVBEbQ", "EXeTwQWrcwY", "0WWzgGyAH6Y") +
            if (System.getenv("NUVIO_YOUTUBE_LIVE_ALL") == "1") listOf(
                "5gHMar_DZ3Y", "hScuBtStpS0", "sMdU59uUb68", "Ls-wVxqGge4",
                "OFaJzL83y0Q", "ODnHr2Emnf0", "Wf0UgltketI", "Rp6ZwGPp4XE", "WBpIxTMjYsc",
            ) else emptyList()
        val failures = mutableListOf<String>()
        for (id in ids) runCatching { verifySession(id) }
            .onFailure { failures += "$id: ${it.message}" }
        check(failures.isEmpty()) { failures.joinToString("\n") }
    }

    private fun verifySession(id: String) {
        val session = YouTubeSession(id, controls = true, muted = true, startMs = 0)
        var expanded by mutableStateOf(false)
        lateinit var window: ComposeWindow
        SwingUtilities.invokeAndWait {
            window = ComposeWindow().apply {
                setSize(1280, 900)
                setContent { NuvioTheme {
                    Box(Modifier.fillMaxSize()) { key(expanded) {
                        YouTubeSurface(session, if (expanded) Modifier.fillMaxSize() else Modifier.size(620.dp, 350.dp))
                    } }
                } }
                isVisible = true
            }
        }
        try {
            await("$id startup") { session.error != null || session.ready && session.positionMs > 1_000 }
            check(session.error == null) { "$id: ${session.error}" }
            val handle = session.handle
            val bounds = YouTubeNativeBridge.bounds(handle)
            check(bounds[2] in 300.0..700.0 && bounds[3] in 200.0..500.0) { "Unbounded embedded player: ${bounds.toList()}" }
            session.configure(false, true)
            Thread.sleep(750)
            val paused = session.positionMs
            SwingUtilities.invokeAndWait { expanded = true }
            await("expand") { YouTubeNativeBridge.bounds(handle)[2] > 900 }
            Thread.sleep(750)
            check(session.handle == handle) { "Presentation recreated the YouTube player" }
            check(kotlin.math.abs(session.positionMs - paused) < 500) { "Paused position was lost" }
            SwingUtilities.invokeAndWait { expanded = false }
            await("collapse") { YouTubeNativeBridge.bounds(handle)[2] < 700 }
            session.configure(true, true)
            await("resume") { session.positionMs > paused + 750 }
            println("YouTube embed verified: $id, same session, bounded overlay, pause/expand/collapse/resume")
        } finally {
            SwingUtilities.invokeAndWait { session.close(); window.dispose() }
        }
    }

    private fun await(label: String, predicate: () -> Boolean) {
        val deadline = System.nanoTime() + 40_000_000_000L
        while (System.nanoTime() < deadline) {
            if (predicate()) return
            Thread.sleep(50)
        }
        error("Timed out: $label")
    }
}
