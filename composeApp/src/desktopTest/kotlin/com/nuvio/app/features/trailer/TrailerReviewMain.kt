package com.nuvio.app.features.trailer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.nuvio.app.AppEnvironment
import com.nuvio.app.core.ui.PosterCardStyleRepository
import com.nuvio.app.features.details.*
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.home.components.HomePosterHoverPreview

/** Standalone QA bundle entry point. Never usable with a real profile or real macOS preferences. */
fun main() {
    check(System.getProperty("nuvio.test.isolatedHome") == "true")
    check(System.getProperty("java.util.prefs.PreferencesFactory") == "com.nuvio.app.core.storage.IsolatedPreferencesFactory")
    MetaScreenSettingsRepository.setHeroTrailerPlayback(true)
    PosterCardStyleRepository.setHoverPreviewEnabled(true)
    PosterCardStyleRepository.setHoverPreviewTrailerEnabled(true)
    PosterCardStyleRepository.setHoverPreviewTrailerSoundEnabled(false)
    PosterCardStyleRepository.setHoverPreviewTrailerStartSeconds(0)
    val primaryVideo = System.getenv("NUVIO_QA_VIDEO")?.takeIf { it.matches(Regex("[A-Za-z0-9_-]{11}")) } ?: "R5jcEBVBEbQ"
    val names = listOf("Official Trailer" to primaryVideo, "BTS" to "Wf0UgltketI", "Who Found That?" to "WBpIxTMjYsc")
    val trailers = names.map { (name, key) -> MetaTrailer(key, key, name, "YouTube", type = if (name == "BTS") "Behind the Scenes" else if (name.startsWith("Who")) "Clip" else "Trailer", official = true) }
    val meta = MetaDetails("trailer-qa-fixture", "movie", "Hope", releaseInfo = "2026", trailers = trailers)
    val field = MetaDetailsRepository.javaClass.getDeclaredField("cachedMetaByRequestKey").apply { isAccessible = true }
    @Suppress("UNCHECKED_CAST") val cache = field.get(MetaDetailsRepository) as MutableMap<String, Any>
    val ctor = Class.forName("com.nuvio.app.features.details.MetaDetailsRepository\$CachedMetaEntry").declaredConstructors.first { it.parameterCount == 3 }.apply { isAccessible = true }
    cache["movie:${meta.id}"] = ctor.newInstance(meta, null, null)
    application {
        Window(onCloseRequest = ::exitApplication, title = "Nuvio Trailer QA", state = rememberWindowState(width = 1280.dp, height = (System.getenv("NUVIO_QA_HEIGHT")?.toIntOrNull() ?: 1000).dp)) {
            AppEnvironment {
                var cards by remember { mutableStateOf(false) }
                Column(Modifier.fillMaxSize().background(Color(0xff182b3d))) {
                    Row {
                        Button(onClick = { cards = false }) { Text("Details and hero") }
                        Button(onClick = { cards = true }) { Text("Hover preview") }
                    }
                    if (!cards) MetaDetailsScreen("movie", meta.id, onBack = {}, modifier = Modifier.weight(1f))
                    else Box(Modifier.fillMaxSize().padding(100.dp)) {
                        HomePosterHoverPreview(item = MetaPreview(meta.id, "movie", "Hope"), isWatched = false, onClick = {}, onLongClick = null) { modifier ->
                            Box(modifier.width(240.dp).height(160.dp).background(Color.DarkGray)) { Text("Hover Hope", color = Color.White) }
                        }
                    }
                }
            }
        }
    }
}
