package com.nuvio.app.features.player

import androidx.compose.ui.test.*
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import com.nuvio.app.AppEnvironment
import org.junit.Test
import kotlin.test.*
import java.io.File
import javax.imageio.ImageIO
import androidx.compose.ui.graphics.toAwtImage

@OptIn(ExperimentalTestApi::class)
class FootballLineupsUiTest {
    @Test fun originalLineupShowsBenchAndClosesWithoutProviderRequests() = runDesktopComposeUiTest(width = 1200, height = 850) {
        check(System.getProperty("nuvio.test.isolatedHome") == "true")
        val session = FootballLineupSession("Home vs Away 2025-01-03")
        session.selected.value = FootballMatch("m_fixture", "Home", "Away", "Cup", "2025-01-03")
        session.teams.value = listOf(FootballTeam("Home", "4-4-2", "Coach",
            listOf(FootballPlayer("Starter", "1", pitchX = 0.5f, pitchY = 0.1f)), listOf(FootballPlayer("Reserve", "12", null))))
        var closed = false
        setContent {
            AppEnvironment {
                FootballLineupsPanel(Modifier.fillMaxSize(), { closed = true }, session)
            }
        }
        onNodeWithText("Starter").assertIsDisplayed()
        onNodeWithText("Original bench").assertIsDisplayed()
        onNodeWithText("12  Reserve").assertIsDisplayed()
        val screenshot = File("build/reports/football-lineups/pitch.png").apply { parentFile.mkdirs() }
        ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", screenshot)
        onNodeWithText("Close lineups").performClick()
        runOnIdle { assertTrue(closed) }
        onNodeWithText("Find match").assertDoesNotExist()
        onNodeWithText("Change match").assertDoesNotExist()
    }
}
