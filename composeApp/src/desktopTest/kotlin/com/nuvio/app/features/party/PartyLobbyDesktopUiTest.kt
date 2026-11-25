package com.nuvio.app.features.party

import androidx.compose.ui.test.*
import com.nuvio.app.AppEnvironment
import kotlin.test.assertEquals
import org.junit.Test

/** The desktop party lobby (Details design, Party lobby): cards for the episode, code, members and settings. */
@OptIn(ExperimentalTestApi::class)
class PartyLobbyDesktopUiTest {
    private fun party(memberId: String) = ActiveParty(
        id = "p", code = "NUV482", memberId = memberId, hostMemberId = "host",
        members = listOf(PartyMember("host", "Victor"), PartyMember("guest", "Sam")), presence = emptyMap(),
        media = PartyMedia(type = "series", metaId = "tt11280740", metaType = "series", season = 2, episode = 3, title = "Severance", episodeTitle = "Who Is Alive?"),
        maxMembers = 8, connected = true, hostOnly = true,
    )

    @Test
    fun theHostSeesSettingsAndEndsForEveryone() = runDesktopComposeUiTest(width = 1360, height = 850) {
        val watched = mutableListOf<PartyMedia>()
        setContent { AppEnvironment { PartyLobbyDesktopContent(party("host"), 0, {}, { watched += it }) } }
        onNodeWithText("S2 E3 · Who Is Alive?", substring = true).assertExists()
        onNodeWithText("NUV 482").assertExists()
        onNodeWithText("IN THE PARTY · 2 OF 8").assertExists()
        onNodeWithTag("party_lobby_host_only").assertIsOn()
        onNodeWithTag("party_lobby_end").assertExists()
        onNodeWithTag("party_lobby_leave").assertDoesNotExist()
        onNodeWithTag("party_lobby_watch").performClick()
        assertEquals(1, watched.size)
    }

    @Test
    fun aGuestJoinsTheHostAndCanOnlyLeave() = runDesktopComposeUiTest(width = 1360, height = 850) {
        setContent { AppEnvironment { PartyLobbyDesktopContent(party("guest"), 0, {}, {}) } }
        onNodeWithText("You join where the host is").assertExists()
        onNodeWithTag("party_lobby_host_only").assertDoesNotExist()
        onNodeWithTag("party_lobby_leave").assertExists()
        onNodeWithTag("party_lobby_end").assertDoesNotExist()
    }

    @Test
    fun endingAPartyAloneOrLeavingGoesBack() = runDesktopComposeUiTest(width = 1360, height = 850) {
        var backs = 0
        val alone = party("host").copy(members = listOf(PartyMember("host", "Victor")))
        setContent { AppEnvironment { PartyLobbyDesktopContent(alone, 0, { backs++ }, {}) } }
        // Alone there is nobody to warn: End returns to Details at once, not to the Join page.
        onNodeWithTag("party_lobby_end").performClick()
        assertEquals(1, backs)
    }

    @Test
    fun leavingAPartyGoesBack() = runDesktopComposeUiTest(width = 1360, height = 850) {
        var backs = 0
        setContent { AppEnvironment { PartyLobbyDesktopContent(party("guest"), 0, { backs++ }, {}) } }
        onNodeWithTag("party_lobby_leave").performClick()
        assertEquals(1, backs)
    }
}
