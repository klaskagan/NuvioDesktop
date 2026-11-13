package com.nuvio.app.features.go3

import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class Go3PublicCatalogTest {
    @Test
    fun loadsAllSonySourcesWithTheirPublicCategoryIds() = runBlocking {
        val categories = linkedMapOf(
            "live" to null, "epl" to "6886315", "ucl" to "6886205", "uel" to "6886318",
            "uecl" to "6886320", "laliga" to "8272112", "bundesliga" to "7934060",
            "ligue1" to "7498236", "seriea" to "7495922", "facup" to "6886370",
        )
        assertEquals(categories.keys.toList(), Go3CatalogSource.entries.map { it.key })
        for ((kind, category) in categories) {
            lateinit var captured: Request
            val catalog = catalog { request ->
                captured = request
                if (category == null) """{"elements":[{"item":${programme(1)}}]}"""
                else """{"items":[${programme(1)}]}"""
            }
            assertEquals("go3:programme:1", catalog.load(kind).single().id)
            assertEquals("go3.lt", captured.url.host)
            assertEquals("LT", captured.url.queryParameter("lang"))
            assertEquals("OM_LT", captured.url.queryParameter("tenant"))
            assertNull(captured.header("Authorization"))
            assertNull(captured.header("Cookie"))
            assertEquals(category, captured.url.queryParameter("categoryId[]"))
            val path = if (category == null) "products/sections/7998051" else "products/lives/programmes/catalog"
            assertEquals("/api/$path", captured.url.encodedPath)
        }
    }

    @Test
    fun paginatesAndKeepsExactProgrammeArtworkAndAvailability() = runBlocking {
        val offsets = mutableListOf<String?>()
        val catalog = catalog { request ->
            val offset = request.url.queryParameter("firstResult")
            offsets += offset
            val rows = if (offset == "0") {
                listOf(programme(1, artwork = true)) + List(99) { """{"type":"LIVE","id":"1"}""" }
            } else {
                listOf(programme(1), programme(2, recording = ""), programme(3, canonicalId = 9),
                    programme(4, start = "2026-11-09T14:00:00Z"))
            }
            """{"items":[${rows.joinToString(",")}],"meta":{"totalCount":104}}"""
        }

        val (replay, listed, upcoming) = catalog.load("uecl")
        assertEquals(listOf<String?>("0", "100"), offsets)
        assertEquals("https://go3.lt/lt/live_tv/extra,live-99/match,programme-1", replay.url)
        assertEquals("https://static3.go3.tv/scale/go3/fixture?dstw=400", replay.artwork)
        assertEquals("Home – Away", replay.preview().name)
        assertTrue(replay.playable)
        assertEquals("listed", listed.state)
        assertFalse(listed.playable)
        assertEquals("upcoming", upcoming.state)
        assertFalse(upcoming.playable)
    }

    @Test
    fun detailReadsTheExactProgramme() = runBlocking {
        lateinit var captured: Request
        val catalog = catalog { request ->
            captured = request
            programme(1)
        }
        assertEquals("replay", catalog.programme("go3:programme:1").state)
        assertEquals("/api/products/lives/programmes/1", captured.url.encodedPath)
    }

    private fun catalog(body: (Request) -> String) = Go3PublicCatalog(
        client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
                .code(200).message("OK").body(body(request).toResponseBody("application/json".toMediaType())).build()
        }.build(),
        now = { Instant.parse("2026-11-08T16:00:00Z") },
    )

    private fun programme(
        id: Int,
        canonicalId: Int = id,
        artwork: Boolean = false,
        recording: String = "1234",
        start: String = "2026-11-08T14:00:00Z",
    ) = """
        {"type":"PROGRAMME","id":$id,"title":"UEFA Conference League. Home – Away",
         "canonicalUrl":"https://go3.lt/lt/live_tv/extra,live-99/match,programme-$canonicalId",
         "since":"$start","till":"2026-11-08T15:00:00Z","catchupTill":"2026-11-15T15:00:00Z",
         "programRecordingId":"$recording","images":{"16x9":[
           {"mainUrl":"https://example.com/cover","miniUrl":"${if (artwork) "//static3.go3.tv/scale/go3/fixture?dstw=400" else ""}"}
         ]}}
    """.trimIndent()
}
