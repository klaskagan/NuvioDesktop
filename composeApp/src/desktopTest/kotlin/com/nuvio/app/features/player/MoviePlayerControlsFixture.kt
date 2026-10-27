package com.nuvio.app.features.player

import androidx.compose.runtime.Composable
import com.nuvio.app.AppEnvironment
import com.nuvio.app.features.details.MetaDetails
import com.nuvio.app.features.details.MetaDetailsRepository
import com.nuvio.app.features.profiles.ProfileRepository
import java.io.File

/** Uses the actual movie screen, including its production PlatformPlayerSurface caller. */
internal object MoviePlayerControlsFixture {
    fun seedMetadata(recommendations: List<com.nuvio.app.features.home.MetaPreview> = emptyList()) {
        check(System.getProperty("nuvio.test.isolatedHome") == "true")
        check(System.getProperty("java.util.prefs.PreferencesFactory") == "com.nuvio.app.core.storage.IsolatedPreferencesFactory")
        val meta = MetaDetails("movie-controls-fixture", "movie", "Movie controls regression", moreLikeThis = recommendations)
        val field = MetaDetailsRepository.javaClass.getDeclaredField("cachedMetaByRequestKey").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST") val cache = field.get(MetaDetailsRepository) as MutableMap<String, Any>
        val ctor = Class.forName("com.nuvio.app.features.details.MetaDetailsRepository\$CachedMetaEntry")
            .declaredConstructors.first { it.parameterCount == 3 }.apply { isAccessible = true }
        cache["movie:${meta.id}"] = ctor.newInstance(meta, null, null)
    }

    @Composable fun Content(file: File, onBack: () -> Unit = {}, onOpenMetaDetails: ((com.nuvio.app.features.home.MetaPreview) -> Unit)? = null) {
        AppEnvironment {
            PlayerScreen(
                profileId = ProfileRepository.activeProfileId,
                title = "Movie controls regression", sourceUrl = file.toURI().toString(),
                providerName = "Local fixture", streamTitle = "Ordinary MP4", streamSubtitle = null,
                parentMetaId = "movie-controls-fixture", parentMetaType = "movie", contentType = "movie",
                onBack = { release -> release(onBack, {}) },
                onOpenMetaDetails = onOpenMetaDetails,
            )
        }
    }
}
