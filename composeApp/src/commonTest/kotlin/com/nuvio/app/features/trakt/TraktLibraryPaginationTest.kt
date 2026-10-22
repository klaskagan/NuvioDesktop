package com.nuvio.app.features.trakt

import com.nuvio.app.features.addons.RawHttpResponse
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class TraktLibraryPaginationTest {
    @Test
    fun `server capped pages include newer movies after the first page`() = runBlocking {
        val requestedPages = mutableListOf<Int>()
        val movies = (1..1068).map { "movie-$it" }
        val result = fetchTraktLibraryPages(
            fetchPage = { page ->
                requestedPages += page
                response(movies.chunked(250)[page - 1], mapOf("X-Pagination-Page-Count" to "5"))
            },
            decodeItems = ::decode,
        )
        assertEquals(movies, result)
        assertEquals(listOf(1, 2, 3, 4, 5), requestedPages)
    }

    @Test
    fun `missing pagination headers do not truncate the list`() = runBlocking {
        val pages = listOf(listOf("older movie"), listOf("newer movie"), emptyList())
        val requestedPages = mutableListOf<Int>()
        val result = fetchTraktLibraryPages(
            fetchPage = { page ->
                requestedPages += page
                response(pages[page - 1])
            },
            decodeItems = ::decode,
        )
        assertEquals(listOf("older movie", "newer movie"), result)
        assertEquals(listOf(1, 2, 3), requestedPages)
    }

    @Test
    fun `empty page terminates even when page count is stale`() = runBlocking {
        val result = fetchTraktLibraryPages(
            fetchPage = { page ->
                check(page == 1)
                response(emptyList(), mapOf("x-pagination-page-count" to "5"))
            },
            decodeItems = ::decode,
        )
        assertEquals(emptyList(), result)
    }

    private fun response(items: List<String>, headers: Map<String, String> = emptyMap()) =
        RawHttpResponse(200, "OK", "https://api.trakt.tv", items.joinToString("\n"), headers)

    private fun decode(body: String): List<String> =
        if (body.isEmpty()) emptyList() else body.lines()
}
