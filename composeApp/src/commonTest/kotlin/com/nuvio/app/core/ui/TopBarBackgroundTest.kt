package com.nuvio.app.core.ui

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import androidx.compose.ui.graphics.luminance

class TopBarBackgroundTest {
    private val tint = Color(0xFF203040)

    @Test
    fun everyOptionButAlwaysSolidIsClearAtThePageTop() {
        TopBarBackground.entries.filter { it != TopBarBackground.AlwaysSolid }.forEach {
            assertEquals(Color.Transparent, topBarSurfaceColor(it, scrolled = false, tint = tint), it.name)
            assertNull(topBarSurfaceBlur(it, scrolled = false), it.name)
        }
        assertEquals(PinnedBarInk, topBarSurfaceColor(TopBarBackground.AlwaysSolid, scrolled = false, tint = null))
    }

    @Test
    fun alwaysSolidKeepsAMidPageBandClearUntilItPins() {
        assertEquals(Color.Transparent, topBarSurfaceColor(TopBarBackground.AlwaysSolid, scrolled = false, tint = null, restsUnderBar = false))
        assertEquals(PinnedBarInk, topBarSurfaceColor(TopBarBackground.AlwaysSolid, scrolled = true, tint = null, restsUnderBar = false))
    }

    @Test
    fun solidOptionsHideWhatScrollsUnderThem() {
        for (option in listOf(TopBarBackground.Solid, TopBarBackground.AlwaysSolid)) {
            assertEquals(1f, topBarSurfaceColor(option, scrolled = true, tint = tint).alpha, option.name)
            assertNull(topBarSurfaceBlur(option, scrolled = true), option.name)
        }
    }

    @Test
    fun tintedUsesTheBackdropColourAndFallsBackToSolid() {
        assertEquals(tint, topBarSurfaceColor(TopBarBackground.Tinted, scrolled = true, tint = tint))
        assertEquals(PinnedBarInk, topBarSurfaceColor(TopBarBackground.Tinted, scrolled = true, tint = null))
    }

    @Test
    fun tintMatchesTheScrimmedArtworkAndKeepsWhiteTextReadable() {
        val dark = Color(0.20f, 0.12f, 0.08f)
        val tint = pinnedTintFromAverage(dark)
        assertEquals(0.12f, tint.red, 0.01f)
        assertEquals(0.072f, tint.green, 0.01f)
        assertEquals(0.048f, tint.blue, 0.01f)
        // A bright, snowy top strip is darkened (same hue) so the bar's white labels still stand out.
        val bright = pinnedTintFromAverage(Color(0.9f, 0.92f, 0.95f))
        assertTrue(bright.luminance() <= 0.121f)
        assertTrue(bright.blue > bright.red)
    }

    @Test
    fun frostedAndTranslucentBlurWhatIsBehind() {
        assertNotNull(topBarSurfaceBlur(TopBarBackground.Frosted, scrolled = true))
        assertNotNull(topBarSurfaceBlur(TopBarBackground.Translucent, scrolled = true))
        assertEquals(BackdropSurface.stickyHeader, topBarSurfaceColor(TopBarBackground.Translucent, scrolled = true, tint = tint))
    }

    @Test
    fun unknownStoredNamesFallBackToSolid() {
        assertEquals(TopBarBackground.Solid, TopBarBackground.fromName(null))
        assertEquals(TopBarBackground.Solid, TopBarBackground.fromName("glass"))
        assertEquals(TopBarBackground.Tinted, TopBarBackground.fromName("tinted"))
    }
}
