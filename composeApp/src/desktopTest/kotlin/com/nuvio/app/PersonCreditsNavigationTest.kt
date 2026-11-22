package com.nuvio.app

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.graphics.toAwtImage
import java.io.File
import javax.imageio.ImageIO
import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import kotlin.test.assertContentEquals
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import androidx.navigation3.ui.NavDisplay
import com.nuvio.app.features.details.*
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.tmdb.TmdbSettingsRepository
import com.nuvio.app.navigation.DetailRoute
import com.nuvio.app.navigation.LocalUseNativeNavigation
import com.nuvio.app.navigation.NuvioNavigator
import com.nuvio.app.navigation.PersonDetailRoute
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assume.assumeTrue
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class, ExperimentalSharedTransitionApi::class)
class PersonCreditsNavigationTest {
    @Test fun moviesExpandWithPinnedProfileAndRestoreSortAndScrollAfterOpeningTitle() =
        runDesktopComposeUiTest(width = 1450, height = 900) {
            requireIsolation()
            val actor = actor(62, "Bruce Willis")
            var loadCount = 0
            setContent {
                ActorNavigation(PersonDetailRoute(62, actor.name), PersonDetailLoaders(
                    detail = { _, _ -> loadCount++; actor },
                    cachedKnownFor = { null },
                    knownFor = { emptyList() },
                ))
            }
            waitUntil(timeoutMillis = 10_000) { onAllNodesWithText("Movies").fetchSemanticsNodes().isNotEmpty() }
            // Desktop redesign: the profile is a full-width header at the top of the overview.
            val preview = onNodeWithTag("actor_preview_wide")
            preview.assertIsDisplayed()
            saveActorCapture("wide-overview")
            val beforeScroll = preview.captureToImage().toAwtImage()
            val shelf = onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.HorizontalScrollAxisRange))
            shelf.performSemanticsAction(SemanticsActions.ScrollBy) { it(675f, 0f) }
            waitForIdle()
            onNodeWithText("Fixture Movie 0").assertDoesNotExist()
            val afterScroll = preview.captureToImage().toAwtImage()
            assertContentEquals(
                beforeScroll.getRGB(0, 0, beforeScroll.width, beforeScroll.height, null, 0, beforeScroll.width),
                afterScroll.getRGB(0, 0, afterScroll.width, afterScroll.height, null, 0, afterScroll.width),
                "A scrolling shelf must leave the actor header pixels untouched",
            )
            saveActorCapture("scrolled-shelf")
            onAllNodesWithContentDescription("View All").onFirst().performClick()
            // "View all" keeps who you're browsing on screen with a compact header above the grid.
            onNodeWithTag("actor_header_compact").assertIsDisplayed()
            runOnIdle { assertEquals(1, loadCount) }
            val firstCard = onNode(hasText("Fixture Movie 0") and hasClickAction()).fetchSemanticsNode().boundsInRoot
            val fourthCard = onNode(hasText("Fixture Movie 3") and hasClickAction()).fetchSemanticsNode().boundsInRoot
            assertEquals(firstCard.top, fourthCard.top, "The pane fits four landscape cards in its first row")
            saveActorCapture("wide-catalog")
            onNodeWithText("Most popular").performClick()
            onNodeWithText("Latest").performClick()
            onNodeWithText("Released").assertIsDisplayed()
            val grid = onNodeWithTag("catalog_grid")
            grid.performScrollToIndex(18)
            val poster = onAllNodes(hasText("Fixture Movie 18") and hasClickAction()).onFirst()
            poster.assertIsDisplayed().performClick()
            onNodeWithText("Details for Fixture Movie 18").assertIsDisplayed()
            onNodeWithText("Back to credits").performClick()
            waitUntil(timeoutMillis = 10_000) { onAllNodesWithText("Latest").fetchSemanticsNodes().isNotEmpty() }
            poster.assertIsDisplayed()
            onNodeWithText("Any watch status").performClick()
            onNodeWithText("Watched").performClick()
            onNodeWithText("No credits match these filters.").assertIsDisplayed()
            saveActorCapture("clear-filters")
            onNodeWithContentDescription("Clear filters").performClick()
            onNodeWithText("Latest").assertIsDisplayed()
            onNodeWithContentDescription("Back").performClick()
            onNodeWithText("Movies").assertIsDisplayed()
        }

    @Test fun wideOverviewStartsAtKnownForWhenItLoadsAfterMovies() =
        runDesktopComposeUiTest(width = 1450, height = 900) {
            requireIsolation()
            val actor = actor(62, "Bruce Willis")
            val knownFor = CompletableDeferred<List<MetaPreview>>()
            setContent {
                ActorNavigation(PersonDetailRoute(62, actor.name), PersonDetailLoaders(
                    detail = { _, _ -> actor },
                    cachedKnownFor = { null },
                    knownFor = { knownFor.await() },
                ))
            }
            waitUntil(timeoutMillis = 10_000) { onAllNodesWithText("Movies").fetchSemanticsNodes().isNotEmpty() }
            waitForIdle()
            // Desktop reserves the Known For row (real rail, blank cards) while it loads, so its arrival
            // must not move anything below it: the shift read as motion starting from the top right.
            val movieTopBefore = onAllNodesWithText("Movies").onFirst().fetchSemanticsNode().boundsInRoot.top
            knownFor.complete(listOf(actor.movieCredits.first().copy(name = "Known For fixture")))
            waitUntil(timeoutMillis = 10_000) { onAllNodesWithText("Known For fixture", substring = true).fetchSemanticsNodes().isNotEmpty() }
            waitForIdle()
            onNodeWithText("Known For").assertIsDisplayed()
            val knownTop = onNodeWithText("Known For").fetchSemanticsNode().boundsInRoot.top
            val movieTop = onNodeWithText("Movies").fetchSemanticsNode().boundsInRoot.top
            kotlin.test.assertTrue(knownTop < movieTop)
            assertEquals(movieTopBefore, movieTop, 0.5f, "Rows below Known For must not move when it loads")
            saveActorCapture("known-for-delayed")
        }

    @Test fun narrowCrewFirstProfileShowsInlineMovieAndSeriesCreditsAndRetryWorks() =
        runDesktopComposeUiTest(width = 640, height = 900) {
            requireIsolation()
            val actor = actor(501, "Crew fixture", crewFirst = true)
            val biography = requireNotNull(actor.biography)
            val failedRequest = CompletableDeferred<PersonDetail?>()
            var attempts = 0
            setContent {
                ActorNavigation(PersonDetailRoute(501, actor.name, preferCrew = true), PersonDetailLoaders(
                    detail = { _, preferCrew ->
                        check(preferCrew)
                        if (++attempts == 1) failedRequest.await() else actor
                    },
                    cachedKnownFor = { null },
                    knownFor = { emptyList() },
                ))
            }
            onNodeWithText(actor.name).assertIsDisplayed()
            failedRequest.complete(null)
            waitUntil(timeoutMillis = 10_000) { onAllNodesWithText("Retry").fetchSemanticsNodes().isNotEmpty() }
            onNodeWithText("Retry").performClick()
            waitUntil(timeoutMillis = 10_000) { onAllNodesWithText("Movies").fetchSemanticsNodes().isNotEmpty() }
            val profilePhoto = onNodeWithTag("actor_profile_photo")
            val profileName = onNodeWithText(actor.name)
            val profileBio = onNodeWithText(biography, substring = true)
            val overviewBounds = listOf(profilePhoto, profileName, profileBio).map { it.fetchSemanticsNode().boundsInRoot }
            onAllNodesWithContentDescription("View All").onFirst().performScrollTo().performClick()
            profileBio.assertIsDisplayed()
            assertEquals(overviewBounds, listOf(profilePhoto, profileName, profileBio).map { it.fetchSemanticsNode().boundsInRoot })
            onNodeWithTag("actor_preview_compact").assertDoesNotExist()
            saveActorCapture("narrow-catalog")
            val grid = onNodeWithTag("catalog_grid")
            val profileCenter = onNodeWithText(actor.name).fetchSemanticsNode().boundsInRoot.center.x
            assertEquals(grid.fetchSemanticsNode().boundsInRoot.center.x, profileCenter, 1f)
            val photo = onNodeWithTag("actor_profile_photo")
            val expandedPhotoWidth = photo.fetchSemanticsNode().boundsInRoot.width
            grid.performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 60f) }
            waitForIdle()
            kotlin.test.assertTrue(photo.fetchSemanticsNode().boundsInRoot.width < expandedPhotoWidth)
            saveActorCapture("narrow-catalog-collapsing")
            grid.performScrollToIndex(16)
            onNodeWithText(biography, substring = true).assertDoesNotExist()
            onNodeWithText("Movies").assertDoesNotExist()
            val pinned = onNodeWithTag("catalog_pinned_controls")
            pinned.assertIsDisplayed()
            val pinnedTop = pinned.fetchSemanticsNode().boundsInRoot.top
            val backBottom = onNodeWithContentDescription("Back").fetchSemanticsNode().boundsInRoot.bottom
            kotlin.test.assertTrue(pinnedTop >= backBottom, "Pinned filters must stay below Back")
            onNodeWithText("Most popular").performClick()
            assertEquals(pinnedTop, pinned.fetchSemanticsNode().boundsInRoot.top)
            onNodeWithText("Latest").performClick()
            onNodeWithText("Released").assertIsDisplayed()
            saveActorCapture("narrow-catalog-pinned")
            onNodeWithContentDescription("Back").performClick()
            val overview = onNodeWithTag("actor_overview")
            overview.performScrollToIndex(2)
            val years = onAllNodesWithText("1988")
            years.assertCountEquals(2)
            val firstYearY = years[0].assertIsDisplayed().fetchSemanticsNode().boundsInRoot.center.y
            val secondYearY = years[1].assertIsDisplayed().fetchSemanticsNode().boundsInRoot.center.y
            assertEquals(101f, secondYearY - firstYearY, 1f, "The compact poster still determines the timeline row height")
            onNodeWithText("Series role", substring = true).assertIsDisplayed()
            val role = onNodeWithText("Actor role").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            onNodeWithText("Movie").assertDoesNotExist()
            val seriesType = onNodeWithText("TV Series").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            val episodeCount = onNodeWithText("3 episodes").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            val score = onNodeWithText("84").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            val votes = onNodeWithText("6.9k").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            onAllNodesWithContentDescription("TMDB").assertCountEquals(2)
            kotlin.test.assertTrue(votes.left >= score.right && role.top >= votes.bottom)
            kotlin.test.assertTrue(episodeCount.left > seriesType.right && kotlin.math.abs(episodeCount.center.y - seriesType.center.y) <= 1f)
            val seriesVotes = onNodeWithText("500").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            kotlin.test.assertTrue(seriesType.left > seriesVotes.right)
            onNodeWithTag("actor_preview_compact").assertDoesNotExist()
            onNodeWithText("Acting Timeline").assertIsDisplayed()
            saveActorCapture("inline-timeline")
        }

    private fun ComposeUiTest.saveActorCapture(name: String) {
        val directory = File("build/reports/actor-credits").apply { mkdirs() }
        ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", File(directory, "$name.png"))
    }

    private fun requireIsolation() {
        assumeTrue(System.getProperty("nuvio.test.isolatedHome") == "true")
        assertEquals("com.nuvio.app.core.storage.IsolatedPreferencesFactory", System.getProperty("java.util.prefs.PreferencesFactory"))
        TmdbSettingsRepository.setEnabled(false)
    }

    @Composable
    private fun ActorNavigation(initialRoute: PersonDetailRoute, loaders: PersonDetailLoaders) {
        val stack = rememberNavBackStack(navigationSavedStateConfiguration, initialRoute)
        val navigator = remember(stack) { NuvioNavigator(stack) }
        AppEnvironment {
            CompositionLocalProvider(
                LocalDensity provides Density(1f),
                LocalPersonDetailLoaders provides loaders,
                LocalUseNativeNavigation provides false,
            ) {
                SharedTransitionLayout {
                    NavDisplay(
                        backStack = stack,
                        modifier = Modifier.fillMaxSize(),
                        onBack = { navigator.popBackStack() },
                        entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator<NavKey>()),
                        sharedTransitionScope = this,
                        entryProvider = entryProvider<NavKey> {
                            entry<PersonDetailRoute> { route ->
                                PersonDestination(route, navigator, this@SharedTransitionLayout, LocalNavAnimatedContentScope.current)
                            }
                            entry<DetailRoute> { route ->
                                TextButton(onClick = { navigator.popBackStack() }) {
                                    Text("Details for ${route.title}")
                                    Text("Back to credits")
                                }
                            }
                        },
                    )
                }
            }
        }
    }

    private fun actor(id: Int, name: String, crewFirst: Boolean = false): PersonDetail {
        val movies = List(44) { index ->
            MetaPreview(
                id = "fixture:$index", type = "movie", name = "Fixture Movie $index",
                rawReleaseDate = "${2000 - index}-01-01",
                popularity = (44 - index).toDouble(),
            )
        }
        val timelineMovie = MetaPreview(
            id = "fixture:acting", type = "movie", name = "Fixture Acting Movie", rawReleaseDate = "1988-07-15",
            sortRating = 8.4, voteCount = 6900,
        )
        return PersonDetail(
            tmdbId = id,
            name = name,
            biography = "Biography for $name",
            birthday = null, deathday = null, placeOfBirth = null, profilePhoto = null, knownFor = null,
            movieCredits = if (crewFirst) listOf(MetaPreview("fixture:crew", "movie", "Crew Movie")) + movies else movies,
            tvCredits = emptyList(),
            actingCredits = listOf(
                ActingCredit(timelineMovie, listOf("Actor role")),
                ActingCredit(
                    MetaPreview(
                        "fixture:acting-series", "series", "Fixture Acting Series", rawReleaseDate = "1988-06-15",
                        sortRating = 7.0, voteCount = 500,
                    ),
                    listOf("Series role"),
                    episodeCount = 3,
                ),
            ),
        )
    }
}
