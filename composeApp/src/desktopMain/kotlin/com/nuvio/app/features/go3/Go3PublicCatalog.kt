package com.nuvio.app.features.go3

import com.nuvio.app.Go3Playback
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Same public programme feed as NuvioTV. This client never uses the Go3 browser session. */
internal class Go3PublicCatalog(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .followRedirects(false)
        .followSslRedirects(false)
        .callTimeout(12, TimeUnit.SECONDS)
        .build(),
    private val base: HttpUrl = "https://go3.lt/api/".toHttpUrl(),
    private val now: () -> Instant = Instant::now,
) {
    suspend fun load(kind: String): List<Go3Event> {
        val source = Go3CatalogSource.entries.single { it.key == kind }
        val at = now()
        val category = source.categoryId
        val events = linkedMapOf<String, Go3Event>()
        if (category == null) {
            val rows = read("products/sections/7998051")["elements"] as? JsonArray ?: throw unavailable()
            merge(events, rows, at)
            return events.values.toList()
        }
        var offset = 0
        repeat(10) {
            val root = read("products/lives/programmes/catalog") {
                addQueryParameter("categoryId[]", category)
                addQueryParameter("firstResult", offset.toString())
                addQueryParameter("maxResults", "100")
                addQueryParameter("sort", "since")
                addQueryParameter("order", "DESC")
            }
            val rows = root["items"] as? JsonArray ?: throw unavailable()
            merge(events, rows, at)
            offset += rows.size
            val total = (root["meta"] as? JsonObject)?.text("totalCount")?.toIntOrNull()
            if (rows.isEmpty() || (total?.let { offset >= it } ?: (rows.size < 100))) {
                return events.values.toList()
            }
        }
        throw unavailable()
    }

    suspend fun programme(id: String): Go3Event {
        val programmeId = id.removePrefix("go3:programme:")
        require(id != programmeId && digits.matches(programmeId))
        val programme = decodeProgramme(read("products/lives/programmes/$programmeId"), now())
        return programme?.takeIf { it.id == id } ?: throw unavailable()
    }

    private suspend fun read(path: String, parameters: HttpUrl.Builder.() -> Unit = {}): JsonObject {
        val url = requireNotNull(base.resolve(path)).newBuilder()
            .addQueryParameter("platform", "ANDROID_TV")
            .addQueryParameter("lang", "LT")
            .addQueryParameter("tenant", "OM_LT")
            .apply(parameters)
            .build()
        val request = Request.Builder().url(url).header("Accept", "application/json").build()
        return suspendCancellableCoroutine { continuation ->
            val call = client.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    continuation.resumeWithException(unavailable())
                }

                override fun onResponse(call: Call, response: Response) {
                    val result = runCatching {
                        response.use {
                            if (!it.isSuccessful) throw unavailable()
                            val source = it.body?.source() ?: throw unavailable()
                            if (source.request(2_000_001L)) throw unavailable()
                            Json.parseToJsonElement(source.readUtf8()) as? JsonObject ?: throw unavailable()
                        }
                    }
                    result.fold(continuation::resume) { continuation.resumeWithException(unavailable()) }
                }
            })
        }
    }

    private fun merge(events: MutableMap<String, Go3Event>, rows: JsonArray, at: Instant) {
        for (row in rows.filterIsInstance<JsonObject>()) {
            val event = decodeProgramme(row["item"] as? JsonObject ?: row, at) ?: continue
            events[event.id] = event.copy(artwork = event.artwork ?: events[event.id]?.artwork)
        }
    }

    private companion object {
        val digits = Regex("[0-9]+")
        val programmeId = Regex(",programme-([0-9]+)(?:/|$)")
        val artParameters = setOf("dsth", "dstw", "srcmode", "quality", "type", "srcx", "srcy", "srcw", "srch")

        fun unavailable() = IOException("Go3 listings unavailable. Retry.")
        fun JsonObject.text(key: String) = (get(key) as? JsonPrimitive)?.contentOrNull.orEmpty()

        fun decodeProgramme(item: JsonObject, at: Instant): Go3Event? {
            if (item.text("type") != "PROGRAMME") return null
            val id = item.text("id").takeIf(digits::matches) ?: return null
            val title = item.text("title").filterNot(Char::isISOControl).trim().take(250)
            if (title.isBlank()) return null
            val canonical = item.text("canonicalUrl").ifBlank { item.text("url") }
            val url = (if (canonical.isBlank()) fallbackUrl(item, id) else canonical.toHttpUrlOrNull())
                ?.takeIf { it.username.isEmpty() && it.password.isEmpty() }
                ?.newBuilder()?.query(null)?.fragment(null)?.build()?.toString() ?: return null
            if (!Go3Playback.isWatchUrl(url) || programmeId.find(url)?.groupValues?.get(1) != id) return null

            fun time(key: String) = runCatching { Instant.parse(item.text(key)) }.getOrNull()
            val start = time("since")
            val end = time("till")
            val catchupEnd = time("catchupTill")
            val state = when {
                start != null && start > at -> "upcoming"
                start != null && end != null && at < end -> "live"
                end != null && at >= end && catchupEnd != null && at < catchupEnd &&
                    digits.matches(item.text("programRecordingId")) -> "replay"
                else -> "listed"
            }
            val images = (item["images"] as? JsonObject)?.get("16x9") as? JsonArray
            val artwork = images.orEmpty().asSequence().filterIsInstance<JsonObject>()
                .flatMap { sequenceOf(it.text("mainUrl"), it.text("miniUrl")) }
                .mapNotNull(::artworkUrl).firstOrNull()
            val schedule = start?.let {
                DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
                    .withZone(ZoneId.systemDefault()).format(it)
            }.orEmpty()
            return Go3Event("go3:programme:$id", title, url, artwork, state, schedule)
        }

        fun fallbackUrl(item: JsonObject, id: String): HttpUrl? {
            val live = item["live"] as? JsonObject ?: return null
            val channel = live.text("id").takeIf(digits::matches) ?: return null
            val channelSlug = live.text("slug").takeIf { it.isNotBlank() && '/' !in it } ?: return null
            val slug = item.text("slug").takeIf { it.isNotBlank() && '/' !in it } ?: return null
            return "https://go3.lt/".toHttpUrl().newBuilder()
                .addPathSegment("live_tv")
                .addPathSegment("$channelSlug,live-$channel")
                .addPathSegment("$slug,programme-$id")
                .build()
        }

        fun artworkUrl(raw: String): String? {
            val url = (if (raw.startsWith("//")) "https:$raw" else raw).toHttpUrlOrNull() ?: return null
            if (!url.isHttps || url.host != "static3.go3.tv" || url.port != 443 ||
                url.username.isNotEmpty() || url.password.isNotEmpty() || !url.encodedPath.startsWith("/scale/go3/") ||
                url.queryParameterNames.any { it !in artParameters } || "no-image" in raw) return null
            return url.newBuilder().fragment(null).build().toString()
        }
    }
}
