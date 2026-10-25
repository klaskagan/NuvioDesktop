package com.nuvio.app.features.trailer

import com.nuvio.app.features.player.desktop.NativePlayerEventSink
import javax.swing.SwingUtilities
import kotlin.test.*

class YouTubeNavigationTest {
    private val id = "R5jcEBVBEbQ"
    @Test fun titleLogoAndChannelsOpenInTheBrowser() {
        val watch = "https://www.youtube.com/watch?v=$id"
        assertEquals(YouTubeLinkDestination.Browser(watch), routeYouTubeLink(watch, id))
        assertIs<YouTubeLinkDestination.Browser>(routeYouTubeLink("https://www.youtube.com/@WarnerBros", id))
        assertIs<YouTubeLinkDestination.Browser>(routeYouTubeLink("https://www.youtube.com/redirect?q=https%3A%2F%2Fexample.com", id))
    }
    @Test fun recommendationsLoadInTheSamePlayerButExplicitExternalActionsDoNot() {
        val url = "https://www.youtube.com/watch?v=EXeTwQWrcwY"
        assertEquals(YouTubeLinkDestination.Embedded("EXeTwQWrcwY"), routeYouTubeLink(url, id))
        assertEquals(YouTubeLinkDestination.Browser(url), routeYouTubeLink(url, id, external = true))
        assertEquals(YouTubeLinkDestination.Embedded(id), routeYouTubeLink("https://youtu.be/$id", id, recommendation = true))
        assertEquals(YouTubeLinkDestination.Embedded("EXeTwQWrcwY"), routeYouTubeLink("https://m.youtube.com/shorts/EXeTwQWrcwY", id))
        assertIs<YouTubeLinkDestination.Browser>(routeYouTubeLink("https://i.ytimg.com/vi/$id/hqdefault.jpg", id))
    }
    @Test fun unsafeSchemesAndLookalikeHostsCannotBecomeEmbeddedCommands() {
        for (url in listOf("javascript:alert(1)", "file:///tmp/video", "data:text/html,hi", "https://user:secret@youtube.com/watch?v=$id", "not a URL")) {
            assertEquals(YouTubeLinkDestination.Ignore, routeYouTubeLink(url, id))
        }
        assertIs<YouTubeLinkDestination.Browser>(routeYouTubeLink("https://youtube.com.example.com/watch?v=EXeTwQWrcwY", id))
    }
    @Test fun browserFallbackUsesTheCurrentRecommendationEvenAfterFailure() {
        val opened = mutableListOf<String>()
        val session = YouTubeSession(id, true, true, 0, openBrowser = { opened += it })
        deliver(session, "video:EXeTwQWrcwY")
        deliver(session, "title:Recommended video")
        assertEquals("EXeTwQWrcwY", session.currentVideoId)
        assertEquals("Recommended video", session.currentTitle)
        session.fail("Embedding disabled")
        session.openInYouTube()
        assertEquals(listOf("https://www.youtube.com/watch?v=EXeTwQWrcwY"), opened)
        deliver(session, "link:https://www.youtube.com/@late")
        assertEquals(1, opened.size)
    }
    @Test fun gestureEventsPreserveEscapeAndDoNotReopenClosedSessions() {
        val session = YouTubeSession(id, true, true, 0)
        var expanded = false
        var escaped = 0
        session.onToggleFullscreen = { expanded = !expanded }
        session.onEscape = { escaped++ }
        deliver(session, "toggleFullscreen"); assertTrue(expanded)
        deliver(session, "escape"); assertEquals(1, escaped)
        session.close()
        deliver(session, "toggleFullscreen"); deliver(session, "escape")
        assertTrue(expanded); assertEquals(1, escaped)
    }
    private fun deliver(session: YouTubeSession, type: String) {
        val sink = session.javaClass.getDeclaredField("sink").apply { isAccessible = true }.get(session) as NativePlayerEventSink
        sink.onPlayerEvent(type, 0.0)
        SwingUtilities.invokeAndWait { }
    }
}
