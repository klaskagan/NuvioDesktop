package com.nuvio.app

import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.*
import com.nuvio.app.features.catalog.*
import com.nuvio.app.features.details.MetaDetails
import com.nuvio.app.features.details.MetaDetailsRepository
import com.nuvio.app.features.home.*
import com.nuvio.app.features.settings.ThemeSettingsRepository
import com.nuvio.app.features.tmdb.TmdbSettingsRepository
import com.nuvio.app.features.watchprogress.CurrentDateProvider
import com.nuvio.app.features.watchprogress.WatchProgressClock
import com.nuvio.app.navigation.CatalogRoute
import kotlinx.coroutines.runBlocking
import java.io.File
import javax.imageio.ImageIO
import org.junit.Assume.assumeTrue
import org.junit.Test
import kotlin.test.assertTrue

/** Real Home and catalog navigation, with in-memory provider cache fixtures and isolated app data. */
@OptIn(ExperimentalTestApi::class)
class SeasonalCatalogNavigationTest {
    @Test fun christmasNavigationKeepsChristmasArtworkRegardlessOfCurrentMonth() = runDesktopComposeUiTest(width = 1440, height = 1000) {
        requireIsolation()
        prepare()
        val section = runBlocking { SeasonalSpotlightRepository.section(SeasonalTheme.CHRISTMAS) }
        val launchId = CatalogLaunchStore.put(CatalogLaunch(section.title, section.subtitle, section.target))
        val route = CatalogRoute(launchId, section.title, section.subtitle)
        setContent { AppEnvironment { MainAppContent(initialRoute = route, ownsAppRuntime = false, showLaunchOverlay = false) } }
        onNodeWithTag("seasonal_catalog_background_christmas").assertIsDisplayed()
        onNodeWithText("Christmas Together").assertIsDisplayed()
        onNodeWithTag("seasonal_catalog_background_halloween").assertDoesNotExist()
        save("christmas-destination")
    }

    private fun requireIsolation() {
        assumeTrue("Run with -I scripts/desktop-ui-tests.init.gradle", System.getProperty("nuvio.test.isolatedHome") == "true")
    }

    private fun prepare() {
        ThemeSettingsRepository.ensureLoaded()
        HomeCatalogSettingsRepository.setHeroEnabled(false)
        HomeCatalogSettingsRepository.setSeasonalCollectionsEnabled(true)
        TmdbSettingsRepository.setEnabled(false)
        CatalogRepository.clear()
        seedSeasonalPages()
        seedMovieDetails()
    }

    // Populate existing caches rather than adding production-only test overrides or contacting providers.
    @Suppress("UNCHECKED_CAST")
    private fun seedSeasonalPages() {
        val cache = field(SeasonalSpotlightRepository, "cache")
        val entries = field(cache, "entries") as MutableMap<Any, Any>
        entries.clear()
        val keyConstructor = Class.forName("com.nuvio.app.features.home.SeasonalSpotlightRepository\$Key")
            .declaredConstructors.first { it.parameterCount == 4 }.apply { isAccessible = true }
        val valueConstructor = Class.forName("com.nuvio.app.features.home.SeasonalPageCache\$Cached")
            .declaredConstructors.first { it.parameterCount == 2 }.apply { isAccessible = true }
        val settings = TmdbSettingsRepository.snapshot()
        SeasonalTheme.entries.forEach { theme ->
            (1..2).forEach { page ->
                val key = keyConstructor.newInstance(theme.id, settings.language, TmdbSettingsRepository.effectiveApiKey(), page)
                val movies = ((page - 1) * 60 + 1..page * 60).map { MetaPreview("seasonal-fixture-$it", "movie", "Seasonal movie $it") }
                entries[key] = valueConstructor.newInstance(CatalogPage(movies, movies.size, if (page == 1) 2 else null), WatchProgressClock.nowEpochMs())
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun seedMovieDetails() {
        MetaDetailsRepository.clear()
        val entries = field(MetaDetailsRepository, "cachedMetaByRequestKey") as MutableMap<String, Any>
        val constructor = Class.forName("com.nuvio.app.features.details.MetaDetailsRepository\$CachedMetaEntry")
            .declaredConstructors.first { it.parameterCount == 3 }.apply { isAccessible = true }
        (1..120).forEach { id ->
            val meta = MetaDetails("seasonal-fixture-$id", "movie", "Seasonal movie $id", description = "Seasonal movie details fixture", releaseInfo = "2020")
            entries["movie:seasonal-fixture-$id"] = constructor.newInstance(meta, null, null)
        }
    }

    private fun field(owner: Any, name: String): Any = owner.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(owner)

    private fun ComposeUiTest.save(name: String) {
        val directory = File("build/reports/seasonal-catalog").apply { mkdirs() }
        ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", File(directory, "$name.png"))
    }
}
