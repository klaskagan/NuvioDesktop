package com.nuvio.app.features.trakt

import com.nuvio.app.features.addons.RawHttpResponse
import com.nuvio.app.features.tracking.TrackingLibraryTabKind
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TraktLibraryListsTest {
    @Test
    fun `discovers smart and liked lists with pagination and preserves owned lists`() = runBlocking {
        val requests = mutableListOf<String>()
        val tabs = fetchTraktLibraryListTabs { url ->
            requests += url
            val body = when {
                url.endsWith("/users/me/lists") -> """[{"name":"Mine","ids":{"trakt":1}}]"""
                url.endsWith("/users/me/smart-lists") -> """[
                    {"name":"Whodunits","ids":{"trakt":1,"slug":"whodunits"}},
                    {"name":"Time Twisters","ids":{"trakt":3,"slug":"time-twisters"}}
                ]"""
                url.contains("page=1&") -> """[
                    {"list":{"name":"Mine","ids":{"trakt":1}}},
                    {"list":{"name":"Alien Invasion","ids":{"trakt":4},"user":{"username":"another-user"}}}
                ]"""
                url.contains("page=2&") -> """[{"list":{"name":"Time Travel & Time Loops","ids":{"trakt":5}}}]"""
                else -> error("Unexpected request $url")
            }
            RawHttpResponse(200, "OK", url, body, mapOf("X-Pagination-Page-Count" to "2"))
        }
        assertEquals(listOf("Mine", "Whodunits", "Time Twisters", "Alien Invasion", "Time Travel & Time Loops"), tabs.map { it.title })
        assertEquals(listOf(TraktListType.PERSONAL, TraktListType.SMART, TraktListType.SMART, TraktListType.LIKED, TraktListType.LIKED), tabs.map { it.type })
        assertEquals(4, requests.size)
        assertEquals("trakt:list:1", tabs.first().key)
    }

    @Test
    fun `browsing lists are excluded from save destinations`() {
        for (kind in listOf(TraktListType.SMART, TraktListType.LIKED)) {
            val tab = TraktListTab("test", "Test", kind).toTrackingLibraryTab()
            assertFalse(tab.isMembershipDestination)
            assertEquals(TrackingLibraryTabKind.EXTERNAL, tab.kind)
        }
        for (kind in listOf(TraktListType.WATCHLIST, TraktListType.PERSONAL)) {
            assertTrue(TraktListTab("test", "Test", kind).toTrackingLibraryTab().isMembershipDestination)
        }
    }

    @Test
    fun `item routes distinguish smart lists from public liked and owned lists`() {
        fun tab(kind: TraktListType) = TraktListTab("test", "Test", kind, traktListId = 42, slug = "title")
        assertEquals("https://api.trakt.tv/smart-lists/42/items", traktLibraryListItemsUrl(tab(TraktListType.SMART)))
        assertEquals("https://api.trakt.tv/lists/42/items", traktLibraryListItemsUrl(tab(TraktListType.LIKED)))
        assertEquals("https://api.trakt.tv/users/me/lists/42/items", traktLibraryListItemsUrl(tab(TraktListType.PERSONAL)))
    }

    @Test
    fun `empty and incomplete lists do not create unusable dropdown entries`() = runBlocking {
        val tabs = fetchTraktLibraryListTabs { url ->
            val body = if (url.endsWith("/smart-lists")) """[{"name":"Incomplete"}]""" else "[]"
            RawHttpResponse(200, "OK", url, body, emptyMap())
        }
        assertTrue(tabs.isEmpty())
    }
}
