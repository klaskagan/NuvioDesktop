package com.nuvio.app.features.trailer

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runDesktopComposeUiTest
import com.nuvio.app.features.details.components.ArtworkAccentLayers
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class DesktopArtworkAccentTest {
    @Test
    fun preservesArtworkColorsAndBlursTheBoundary() = runDesktopComposeUiTest(width = 1000, height = 600) {
        val artwork = object : Painter() {
            override val intrinsicSize = Size(1000f, 600f)

            override fun DrawScope.onDraw() {
                drawRect(Color.Blue)
                drawRect(Color.Red, size = Size(size.width / 2f, size.height))
            }
        }
        val loadedArtwork = mutableStateOf<Painter?>(null)
        val fallback = Color(0xFF202020)
        setContent {
            ArtworkAccentLayers(loadedArtwork.value, if (loadedArtwork.value != null) 1f else 0f, fallback)
        }
        val empty = onRoot().captureToImage().toPixelMap()
        assertTrue(empty[empty.width / 2, empty.height / 2].red in 0.12f..0.13f)

        runOnIdle { loadedArtwork.value = artwork }
        val image = onRoot().captureToImage().toPixelMap()
        val lowerY = image.height * 2 / 3
        val left = image[image.width / 4, lowerY]
        val right = image[image.width * 3 / 4, lowerY]
        val boundary = image[image.width / 2, lowerY]
        val glow = image[image.width / 4, image.height / 5]
        assertTrue(left.red > left.blue && right.blue > right.red)
        assertTrue(boundary.red > 0.08f && boundary.blue > 0.08f)
        assertTrue(glow.red > left.red)
    }
}
