package com.nuvio.app.core.ui

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertNull

/** Service logos sit in transparent canvases; the trim finds the artwork so it draws at full height. */
class TrimmedLogoTest {
    private fun canvas(width: Int, height: Int, opaque: (Int, Int) -> Boolean) =
        IntArray(width * height) { i -> if (opaque(i % width, i / width)) 0xFFFFFFFF.toInt() else 0x00FFFFFF }

    @Test fun findsTheArtworkInsideATransparentCanvas() {
        val pixels = canvas(10, 8) { x, y -> x in 2..6 && y in 3..4 }
        assertContentEquals(intArrayOf(2, 3, 7, 5), opaqueBounds(pixels, 10, 8))
    }

    @Test fun ignoresNearlyTransparentHaze() {
        val pixels = canvas(4, 4) { x, y -> x == 1 && y == 1 }.also { it[15] = 0x05000000 }
        assertContentEquals(intArrayOf(1, 1, 2, 2), opaqueBounds(pixels, 4, 4))
    }

    @Test fun anEmptyImageHasNoBounds() {
        assertNull(opaqueBounds(canvas(3, 3) { _, _ -> false }, 3, 3))
    }
}
