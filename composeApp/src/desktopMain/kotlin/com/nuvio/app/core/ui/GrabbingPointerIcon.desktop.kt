package com.nuvio.app.core.ui

import androidx.compose.ui.input.pointer.PointerIcon
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Cursor
import java.awt.Point
import java.awt.RenderingHints
import java.awt.Toolkit
import java.awt.geom.Area
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage

/**
 * A closed hand, drawn here because AWT has no grabbing cursor: its MOVE_CURSOR is a plain arrow
 * on macOS. Falls back to the move cursor where custom cursors aren't supported.
 */
internal actual val GrabbingPointerIcon: PointerIcon by lazy {
    runCatching { PointerIcon(closedHandCursor()) }
        .getOrElse { PointerIcon(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR)) }
}

private fun closedHandCursor(): Cursor {
    val toolkit = Toolkit.getDefaultToolkit()
    val best = toolkit.getBestCursorSize(32, 32)
    val size = if (best.width > 0) best.width else 32
    val image = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
    val g = image.createGraphics()
    try {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        val s = size / 32f
        g.scale(s.toDouble(), s.toDouble())
        // A fist seen from the back: four knuckles over the palm, the thumb tucked in on the left.
        val hand = Area(RoundRectangle2D.Float(8f, 12f, 17f, 13f, 7f, 7f))
        for (i in 0 until 4) {
            hand.add(Area(RoundRectangle2D.Float(9f + i * 4f, 9f, 4.4f, 7f, 4.4f, 4.4f)))
        }
        hand.add(Area(RoundRectangle2D.Float(5.5f, 14f, 6f, 5f, 5f, 5f)))
        g.color = Color.WHITE
        g.fill(hand)
        g.color = Color.BLACK
        g.stroke = BasicStroke(1.3f)
        g.draw(hand)
        // Finger creases.
        for (i in 1 until 4) {
            val x = 9f + i * 4f
            g.draw(java.awt.geom.Line2D.Float(x, 11f, x, 14.5f))
        }
    } finally {
        g.dispose()
    }
    return toolkit.createCustomCursor(image, Point(size / 2, size / 2), "closed-hand")
}
