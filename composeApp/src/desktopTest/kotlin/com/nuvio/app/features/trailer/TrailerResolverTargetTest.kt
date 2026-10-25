package com.nuvio.app.features.trailer

import kotlinx.coroutines.runBlocking
import kotlin.test.*
import kotlin.time.TimeSource

class TrailerResolverTargetTest {
    @Test fun cacheSeparatesRendererTargetsAndRefreshOnlyInvalidatesTheRequestedTarget() = runBlocking {
        check(System.getProperty("nuvio.test.isolatedHome") == "true")
        val owner = TrailerPlaybackResolver
        val cache = owner.javaClass.getDeclaredField("playbackCache").apply { isAccessible = true }
        val old = cache.get(owner)
        val entry = Class.forName("com.nuvio.app.features.trailer.TrailerPlaybackResolver\$CachedPlaybackSource")
            .declaredConstructors.single().apply { isAccessible = true }
        val url = "invalid"
        val native = TrailerPlaybackSource("native")
        val preview = TrailerPlaybackSource("preview")
        try {
            cache.set(owner, mapOf(
                "${TrailerPlaybackTarget.NATIVE_PLAYER}:$url" to entry.newInstance(native, TimeSource.Monotonic.markNow()),
                "${TrailerPlaybackTarget.FRAME_PREVIEW}:$url" to entry.newInstance(preview, TimeSource.Monotonic.markNow()),
            ))
            assertEquals(native, owner.resolveFromYouTubeUrl(url))
            assertEquals(preview, owner.resolveFromYouTubeUrl(url, target = TrailerPlaybackTarget.FRAME_PREVIEW))
            assertNull(owner.resolveFromYouTubeUrl(url, forceRefresh = true))
            assertEquals(preview, owner.resolveFromYouTubeUrl(url, target = TrailerPlaybackTarget.FRAME_PREVIEW))
        } finally { cache.set(owner, old) }
    }
}
