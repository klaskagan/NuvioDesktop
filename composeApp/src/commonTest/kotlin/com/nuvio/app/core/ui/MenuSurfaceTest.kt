package com.nuvio.app.core.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** "Dropdown · spec" in the Home design: the menu surface made from the backdrop's top strip. */
class MenuSurfaceTest {
    private fun assertColor(expected: Long, actual: Color) {
        val e = Color(expected)
        listOf(e.red to actual.red, e.green to actual.green, e.blue to actual.blue).forEach { (want, got) ->
            assertTrue(abs(want - got) <= 2f / 255f, "expected ${e.toHex()}, got ${actual.toHex()}")
        }
    }

    private fun Color.toHex() = "#" + listOf(red, green, blue).joinToString("") { (it * 255).toInt().toString(16).padStart(2, '0') }

    @Test
    fun warmArtworkGivesTheDesignsWarmBrown() {
        // Dune's top strip; the design doc draws its menus in #3C231D.
        assertColor(0xFF3C231D, menuSurfaceFromAverage(Color(0.3958f, 0.1620f, 0.1089f)))
    }

    @Test
    fun brightArtworkIsDarkenedSoGreyRowsStayReadable() {
        // Severance's near-white strip still yields a dark surface (#3E3F3D in the design doc).
        val surface = menuSurfaceFromAverage(Color(0.7679f, 0.8007f, 0.7539f))
        assertColor(0xFF3E3F3D, surface)
        // Secondary text (#B8BEC5) keeps at least 5.5:1 against it.
        val text = Color(0xFFB8BEC5).luminance()
        assertTrue((text + 0.05f) / (surface.luminance() + 0.05f) >= 5.5f)
    }

    @Test
    fun theSurfaceIsAlwaysOpaque() {
        listOf(Color.Black, Color.White, Color(0.2f, 0.5f, 0.9f)).forEach {
            assertEquals(1f, menuSurfaceFromAverage(it).alpha)
        }
    }

    @Test
    fun blackArtworkIsJustThePillsWhiteFill() {
        val surface = menuSurfaceFromAverage(Color.Black)
        assertTrue(abs(surface.red - 0.07f) < 0.001f && surface.red == surface.green && surface.green == surface.blue)
    }
}
