package com.nuvio.app.features.home

import com.nuvio.app.features.catalog.CatalogPage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class SeasonalSpotlightTest {
    @Test
    fun `calendar covers the year and switches at local month boundaries`() {
        assertEquals(listOf(
            SeasonalTheme.FRESH_START, SeasonalTheme.LOVE, SeasonalTheme.WORLDS,
            SeasonalTheme.FAMILY, SeasonalTheme.ADVENTURE, SeasonalTheme.SUMMER,
            SeasonalTheme.SUMMER, SeasonalTheme.SUMMER, SeasonalTheme.SCHOOL,
            SeasonalTheme.HALLOWEEN, SeasonalTheme.HALLOWEEN, SeasonalTheme.CHRISTMAS,
        ), (1..12).map(SeasonalTheme::forMonth))
        assertEquals(SeasonalTheme.HALLOWEEN, SeasonalTheme.forDate("2026-11-30"))
        assertEquals(SeasonalTheme.CHRISTMAS, SeasonalTheme.forDate("2026-12-01"))
        assertEquals(SeasonalTheme.FRESH_START, SeasonalTheme.forDate("2027-01-01"))
        assertEquals(SeasonalTheme.LOVE, SeasonalTheme.forDate("2028-02-29"))
        assertTrue(SeasonalTheme.entries.all { it.backgroundArtwork != null }, "Every season needs dedicated background artwork")
    }

    @Test
    fun `keyword filters preserve relevance and exclude future releases`() {
        val filters = SeasonalTheme.CHRISTMAS.filters(listOf(42, 81), "2026-12-01")
        assertEquals("42|81", filters.withKeywords)
        assertEquals("2026-12-01", filters.releaseDateLte)
        assertEquals("27", SeasonalTheme.HALLOWEEN.filters(emptyList(), "2026-10-21").withGenres)
        assertEquals("10751", SeasonalTheme.FAMILY.filters(emptyList(), "2026-04-01").withGenres)
    }

    private fun page(id: String) = CatalogPage(listOf(MetaPreview(id, "movie", id)), 1, 2)

    @Test
    fun `home and explore share cached data but theme and language keys stay separate`() = runTest {
        var time = 0L
        var requests = 0
        val cache = SeasonalPageCache<String>(now = { time }, ttlMs = 100) { key -> requests++; page(key) }
        assertEquals(cache.get("halloween-en"), cache.get("halloween-en"))
        assertEquals(1, requests)
        cache.get("christmas-en")
        cache.get("christmas-fr")
        assertEquals(3, requests)
        time = 100
        cache.get("halloween-en")
        assertEquals(4, requests)
    }

    @Test
    fun `refresh failure retains prior data and cancellation is never swallowed`() = runTest {
        var time = 0L
        var failure: Exception? = null
        val cache = SeasonalPageCache<String>(now = { time }, ttlMs = 10) {
            failure?.let { throw it }
            page(it)
        }
        val first = cache.get("halloween")
        time = 10
        failure = IllegalStateException("offline")
        assertEquals(first, cache.get("halloween"))
        assertFailsWith<IllegalStateException> { cache.get("christmas") }
        failure = CancellationException("home hidden")
        assertFailsWith<CancellationException> { cache.get("halloween") }
    }

    @Test
    fun `empty results can be retried and cache stays bounded`() = runTest {
        var requests = 0
        val cache = SeasonalPageCache<String>(capacity = 1) {
            requests++
            if (requests == 1) CatalogPage(emptyList(), 0, null) else page(it)
        }
        assertTrue(cache.get("a").items.isEmpty())
        assertTrue(cache.get("a").items.isNotEmpty())
        cache.get("b")
        cache.get("a")
        assertEquals(4, requests)
    }
}
