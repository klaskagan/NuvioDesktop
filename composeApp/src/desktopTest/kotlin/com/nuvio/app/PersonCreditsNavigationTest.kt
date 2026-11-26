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
    // Desktop at Details' board width: the person board (design doc "Person page"). The narrow test
    // below keeps the header-and-rails layout, which mobile and small windows still use.
    @Test fun wideOpensAsBoardAndMoviesTabFiltersAndKeepsItsPlaceAfterOpeningTitle() =
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
            waitUntil(timeoutMillis = 10_000) { onAllNodesWithTag("person_board_page").fetchSemanticsNodes().isNotEmpty() }
            onNodeWithTag("detail_board_featured").assertIsDisplayed()
            onNodeWithTag("person_board_name").assertIsDisplayed()
            onNodeWithTag("person_board_you").assertIsDisplayed()
            // No tracker that keeps favorite people is connected, so there is no Favorite button.
            onNodeWithTag("person_board_favorite").assertDoesNotExist()
            // Known for is empty, so the first tab is Movies, newest first.
            onNodeWithText("Movies").assertIsSelected()
            onNodeWithText("Newest").assertIsSelected()
            onAllNodes(hasText("Fixture Movie 0") and hasClickAction()).onFirst().assertIsDisplayed()
            saveActorCapture("board-movies")
            onNodeWithText("Popular").performClick()
            onNodeWithText("Popular").assertIsSelected()
            runOnIdle { assertEquals(1, loadCount) }
            val poster = onAllNodes(hasText("Fixture Movie 0") and hasClickAction()).onFirst()
            poster.performClick()
            onNodeWithText("Details for Fixture Movie 0").assertIsDisplayed()
            onNodeWithText("Back to credits").performClick()
            waitUntil(timeoutMillis = 10_000) { onAllNodesWithTag("person_board_page").fetchSemanticsNodes().isNotEmpty() }
            // Coming back keeps the tab and its sort (the person comes from the service's cache again).
            onNodeWithText("Movies").assertIsSelected()
            onNodeWithText("Popular").assertIsSelected()
            onNodeWithTag("person_board_credits_unwatched").performClick()
            onAllNodes(hasText("Fixture Movie 0") and hasClickAction()).onFirst().assertIsDisplayed()
            onNodeWithText("About").performClick()
            onNodeWithTag("person_board_about").assertIsDisplayed()
            onNodeWithTag("person_board_bio").assertTextEquals("Biography for Bruce Willis")
            saveActorCapture("board-about")
        }

    @Test fun knownForTabHoldsItsPlaceWhileItLoads() =
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
            waitUntil(timeoutMillis = 10_000) { onAllNodesWithTag("person_board_tabs").fetchSemanticsNodes().isNotEmpty() }
            waitForIdle()
            onNodeWithText("Known For").assertIsSelected()
            val moviesTabBefore = onNodeWithText("Movies").fetchSemanticsNode().boundsInRoot
            knownFor.complete(listOf(actor.movieCredits.first().copy(name = "Known For fixture")))
            waitUntil(timeoutMillis = 10_000) { onAllNodesWithText("Known For fixture", substring = true).fetchSemanticsNodes().isNotEmpty() }
            waitForIdle()
            assertEquals(moviesTabBefore, onNodeWithText("Movies").fetchSemanticsNode().boundsInRoot, "The tabs must not move when Known for loads")
            saveActorCapture("board-known-for")
        }

    @Test fun shortTabStillScrollsTheBoardAwayAndPinsTheTabs() =
        runDesktopComposeUiTest(width = 1450, height = 900) {
            requireIsolation()
            val actor = actor(62, "Bruce Willis")
            setContent {
                ActorNavigation(PersonDetailRoute(62, actor.name), PersonDetailLoaders(detail = { _, _ -> actor }, cachedKnownFor = { null }, knownFor = { emptyList() }))
            }
            waitUntil(timeoutMillis = 10_000) { onAllNodesWithTag("person_board_tabs").fetchSemanticsNodes().isNotEmpty() }
            // About is shorter than the window; the filler under it still lets the board scroll away.
            onNodeWithText("About").performClick()
            waitForIdle()
            repeat(6) {
                onNodeWithTag("person_board_page").performMouseInput { scroll(400f) }
                waitForIdle()
            }
            onNodeWithTag("person_board_pinned_back").assertIsDisplayed()
            val tabsTop = onNodeWithTag("person_board_tabs").fetchSemanticsNode().boundsInRoot.top
            kotlin.test.assertTrue(tabsTop < 80f, "The tabs row pins at the top, was at $tabsTop")
            // ...and the short tab stops right under it rather than running under the row.
            val aboutTop = onNodeWithTag("person_board_about").fetchSemanticsNode().boundsInRoot.top
            val tabsBottom = onNodeWithTag("person_board_tabs").fetchSemanticsNode().boundsInRoot.bottom
            kotlin.test.assertTrue(aboutTop >= tabsBottom - 1f, "About starts under the pinned row ($aboutTop < $tabsBottom)")
            saveActorCapture("board-pinned")
        }

    @Test fun longCareerKeepsTheTabsPinned() =
        runDesktopComposeUiTest(width = 1450, height = 900) {
            requireIsolation()
            val base = actor(62, "Bruce Willis")
            val long = base.actingCredits + List(60) { i ->
                ActingCredit(MetaPreview("fixture:career-$i", "movie", "Career Movie $i", rawReleaseDate = "${2020 - i}-01-01"), listOf("Role $i"))
            }
            val person = base.copy(actingCredits = long, careerCredits = mapOf(CareerDepartment.Acting to long))
            setContent {
                // Under the merged top bar, as in the app: the pinned row must stay below the bar.
                CompositionLocalProvider(LocalDetailTopBarState provides remember { DetailTopBarState() }) {
                    ActorNavigation(PersonDetailRoute(62, person.name), PersonDetailLoaders(detail = { _, _ -> person }, cachedKnownFor = { null }, knownFor = { emptyList() }))
                }
            }
            waitUntil(timeoutMillis = 10_000) { onAllNodesWithTag("person_board_tabs").fetchSemanticsNodes().isNotEmpty() }
            onNodeWithText("Career").performClick()
            waitForIdle()
            repeat(8) {
                onNodeWithTag("person_board_page").performMouseInput { scroll(600f) }
                waitForIdle()
            }
            saveActorCapture("board-career-pinned")
            onNodeWithTag("person_board_pinned_back").assertIsDisplayed()
            val tabsTop = onNodeWithTag("person_board_tabs").fetchSemanticsNode().boundsInRoot.top
            // Pinned right under the 56 dp merged bar, not behind it.
            kotlin.test.assertTrue(tabsTop in 55f..60f, "The tabs row pins under the top bar over a long career, was at $tabsTop")
        }

    @Test fun openedFromATitleShowsTheRoleThereAndBackReturns() =
        runDesktopComposeUiTest(width = 1450, height = 900) {
            requireIsolation()
            val actor = actor(62, "Bruce Willis")
            setContent {
                ActorNavigation(
                    PersonDetailRoute(62, actor.name, fromTitle = PersonTitleContext("Moonlighting", null, "David Addison", 66, isSeries = true)),
                    PersonDetailLoaders(detail = { _, _ -> actor }, cachedKnownFor = { null }, knownFor = { emptyList() }),
                )
            }
            waitUntil(timeoutMillis = 10_000) { onAllNodesWithTag("person_board_in_title").fetchSemanticsNodes().isNotEmpty() }
            onNodeWithText("In Moonlighting", substring = true, ignoreCase = true).assertIsDisplayed()
            onNodeWithText("David Addison").assertIsDisplayed()
            onNodeWithText("66 episodes").assertIsDisplayed()
            onNodeWithText("Back to Moonlighting").assertIsDisplayed()
            saveActorCapture("board-from-title")
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
            // Both credits are from 1988: the year labels its group once, not every row.
            onAllNodesWithText("1988").assertCountEquals(1)
            val firstRowY = onNodeWithText("Fixture Acting Movie").assertIsDisplayed().fetchSemanticsNode().boundsInRoot.center.y
            val secondRowY = onNodeWithText("Fixture Acting Series").assertIsDisplayed().fetchSemanticsNode().boundsInRoot.center.y
            assertEquals(101f, secondRowY - firstRowY, 1f, "The compact poster still determines the timeline row height")
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
            onNodeWithText("Career").assertIsDisplayed()
            // One department: no tab row.
            onNodeWithTag("person_career_tabs").assertDoesNotExist()
            saveActorCapture("inline-timeline")
        }

    @Test fun directorCareerOpensOnDirectingAndSwitchesToActingInPlace() =
        runDesktopComposeUiTest(width = 1450, height = 900) {
            requireIsolation()
            val base = actor(525, "Director fixture")
            val directed = MetaPreview("fixture:directed", "movie", "Fixture Directed Movie", rawReleaseDate = "2010-07-16")
            val director = base.copy(
                knownFor = "Directing",
                careerCredits = mapOf(
                    CareerDepartment.Acting to base.actingCredits,
                    CareerDepartment.Directing to listOf(ActingCredit(directed, listOf("Director"))),
                    CareerDepartment.Writing to listOf(
                        ActingCredit(directed, listOf("Screenplay", "Story")),
                        ActingCredit(MetaPreview("fixture:written", "movie", "Fixture Written Movie", rawReleaseDate = "2006-01-01"), listOf("Writer")),
                    ),
                ),
            )
            setContent {
                ActorNavigation(PersonDetailRoute(525, director.name), PersonDetailLoaders(
                    detail = { _, _ -> director },
                    cachedKnownFor = { null },
                    knownFor = { emptyList() },
                ))
            }
            waitUntil(timeoutMillis = 10_000) { onAllNodesWithTag("person_board_tabs").fetchSemanticsNodes().isNotEmpty() }
            onNodeWithText("Career").performClick()
            waitForIdle()
            // The department the person is known for comes first and is selected, not Acting.
            onNodeWithTag("person_career_Directing").assertIsSelected()
            onNodeWithText("Fixture Directed Movie").assertIsDisplayed()
            onNodeWithTag("person_career_span").assertTextEquals("2010")
            onNodeWithText("Fixture Acting Movie").assertDoesNotExist()
            val pillsTop = onNodeWithTag("person_career_Directing").fetchSemanticsNode().boundsInRoot.top
            saveActorCapture("board-director-career")
            onNodeWithTag("person_career_Acting").performClick()
            waitForIdle()
            onNodeWithTag("person_career_Acting").assertIsSelected()
            onNodeWithText("as Actor role", substring = true).assertIsDisplayed()
            onNodeWithText("Fixture Directed Movie").assertDoesNotExist()
            assertEquals(pillsTop, onNodeWithTag("person_career_Acting").fetchSemanticsNode().boundsInRoot.top, "Switching departments must not move the pills")
            onNodeWithTag("person_career_Writing").performClick()
            waitForIdle()
            onNodeWithText("Screenplay · Story · Movie").assertIsDisplayed()
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
