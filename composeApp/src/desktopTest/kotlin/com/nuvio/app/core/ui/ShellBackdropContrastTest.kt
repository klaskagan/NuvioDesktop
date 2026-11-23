package com.nuvio.app.core.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Bright artwork used to wash out the muted top-bar and header labels (Calendar over The Simpsons:
 * Search / Settings nearly invisible). The backdrop now dims bright art and darkens the chrome band.
 */
class ShellBackdropContrastTest {
    private fun bitmap(top: Color, bottom: Color = top, width: Int = 40, height: Int = 60): ImageBitmap {
        val image = java.awt.image.BufferedImage(width, height, java.awt.image.BufferedImage.TYPE_INT_ARGB)
        val split = (height * 0.4f).toInt()
        for (y in 0 until height) for (x in 0 until width) image.setRGB(x, y, (if (y < split) top else bottom).toArgb())
        return image.toComposeImageBitmap()
    }

    private fun contrast(a: Color, b: Color): Double {
        val la = relativeLuminance(a)
        val lb = relativeLuminance(b)
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    /** sRGB "black at [alpha]" layers composite multiplicatively over the artwork. */
    private fun Color.darkened(vararg alphas: Float): Color {
        val keep = alphas.fold(1f) { acc, a -> acc * (1f - a) }
        return Color(red * keep, green * keep, blue * keep)
    }

    @Test fun darkArtIsNotDimmedAndBrightArtIsCapped() {
        assertEquals(0f, backdropDimFor(0f))
        assertEquals(0f, backdropDimFor(0.25f))
        assertEquals(BackdropMaxBrightDim, backdropDimFor(0.7f))
        assertEquals(BackdropMaxBrightDim, backdropDimFor(1f))
        val steps = (0..20).map { backdropDimFor(it / 20f) }
        assertTrue(steps.zipWithNext().all { (a, b) -> b >= a }, "dim must grow with brightness: $steps")
    }

    @Test fun luminanceRanksBrightAboveDarkAndWeightsTheChromeArea() {
        val white = artworkLuminance(bitmap(Color.White))
        val dark = artworkLuminance(bitmap(Color(0xFF101418)))
        assertTrue(white > 0.9f && dark < 0.05f, "white=$white dark=$dark")
        val brightTop = artworkLuminance(bitmap(Color.White, Color.Black))
        val brightBottom = artworkLuminance(bitmap(Color.Black, Color.White))
        assertTrue(brightTop > brightBottom, "top rows must weigh more: top=$brightTop bottom=$brightBottom")
    }

    @Test fun mutedLabelsStayReadableOverBrightArtwork() {
        val art = Color(0xFFE8D9A0) // light yellow, like a blurred Simpsons poster
        val dim = backdropDimFor(artworkLuminance(bitmap(art)))
        val before = art.darkened(0.40f) // the old fixed scrim at the top edge
        val after = art.darkened(0.40f, dim, 0.35f) // scrim, brightness dim, chrome band
        assertTrue(contrast(RedesignColors.textMuted, before) < 1.5, "fixture no longer reproduces the washed-out bar")
        val ratio = contrast(RedesignColors.textMuted, after)
        assertTrue(ratio >= 3.0, "textMuted over bright art: contrast $ratio")
    }
}
