package com.nuvio.app.features.details

import com.nuvio.app.features.trailer.youtubeEmbedVideoId
import kotlin.test.*

class YouTubeEmbedVideoIdTest {
    @Test fun acceptsOnlySupportedYouTubeIdentifiers() {
        assertEquals("R5jcEBVBEbQ", youtubeEmbedVideoId("R5jcEBVBEbQ"))
        assertEquals("R5jcEBVBEbQ", youtubeEmbedVideoId("https://www.youtube.com/watch?v=R5jcEBVBEbQ&t=4"))
        assertEquals("R5jcEBVBEbQ", youtubeEmbedVideoId("https://youtu.be/R5jcEBVBEbQ?feature=share"))
        assertNull(youtubeEmbedVideoId("https://example.com/watch?v=R5jcEBVBEbQ"))
        assertNull(youtubeEmbedVideoId("https://www.youtube.com/watch?v=';alert(1)"))
        assertNull(youtubeEmbedVideoId(""))
    }
}
