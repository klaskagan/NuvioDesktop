package com.nuvio.app.features.search

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runDesktopComposeUiTest
import com.nuvio.app.AppEnvironment
import com.nuvio.app.core.ui.LocalNuvioShelfHeaderStyle
import com.nuvio.app.core.ui.NuvioShelfHeaderStyle
import com.nuvio.app.features.details.components.DetailPosterRailSection
import com.nuvio.app.features.home.MetaPreview
import kotlin.test.Test

/** The streaming service page's rows use Home's editorial header with the service as the eyebrow. */
@OptIn(ExperimentalTestApi::class)
class StreamingRowEyebrowTest {
    private val items = listOf(MetaPreview(id = "tt1", type = "series", name = "Fixture show"))

    @Test
    fun serviceRowsShowTheServiceAsTheEyebrow() = runDesktopComposeUiTest {
        setContent {
            AppEnvironment {
                CompositionLocalProvider(LocalNuvioShelfHeaderStyle provides NuvioShelfHeaderStyle.Editorial) {
                    DetailPosterRailSection(
                        title = "Popular shows",
                        items = items,
                        watchedKeys = emptySet(),
                        eyebrow = "Netflix",
                    )
                }
            }
        }
        onNodeWithText("NETFLIX").assertIsDisplayed()
        onNodeWithText("Popular shows").assertIsDisplayed()
    }

    @Test
    fun theStandardHeaderStaysWithoutAnEyebrow() = runDesktopComposeUiTest {
        setContent {
            AppEnvironment {
                DetailPosterRailSection(title = "Popular shows", items = items, watchedKeys = emptySet(), eyebrow = "Netflix")
            }
        }
        onNodeWithText("Popular shows").assertIsDisplayed()
        onNodeWithText("NETFLIX").assertDoesNotExist()
    }
}
