package com.nuvio.app.features.player.desktop

import java.awt.Canvas
import java.awt.Color
import java.awt.Cursor
import java.awt.Graphics
import java.awt.Point
import java.awt.Toolkit
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.MouseEvent
import java.awt.event.MouseMotionAdapter
import java.awt.image.BufferedImage

internal class NativePlayerHost(val embedded: Boolean = false) : Canvas() {
    var onBoundsChanged: (() -> Unit)? = null
    var presentationFullscreen: Boolean = false
    var cornerRadius: Double = 18.0
    @Volatile var embeddedBounds: DoubleArray = doubleArrayOf(0.0, 0.0, 1.0, 1.0, 18.0)
        private set
    var onPeerReady: (() -> Unit)? = null
    var onDisplayableChanged: ((Boolean) -> Unit)? = null
    var onFirstPaint: (() -> Unit)? = null
    var onFirstFullSizePaint: (() -> Unit)? = null
    var onCursorActivity: (() -> Unit)? = null
    private var firstPaintNotified = false
    private var firstFullSizePaintNotified = false
    private var controlsVisible = true
    private var cursorVisible = true

    private companion object {
        val hiddenCursor: Cursor by lazy {
            val image = BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB)
            Toolkit.getDefaultToolkit().createCustomCursor(image, Point(0, 0), "nuvio-hidden-cursor")
        }
    }

    init {
        if (embedded && DesktopHostOs.current == DesktopHostOs.MACOS) {
            addComponentListener(object : ComponentAdapter() {
                override fun componentResized(event: ComponentEvent) = updateEmbeddedBounds()
                override fun componentMoved(event: ComponentEvent) = updateEmbeddedBounds()
                override fun componentShown(event: ComponentEvent) = updateEmbeddedBounds()
                override fun componentHidden(event: ComponentEvent) = updateEmbeddedBounds()
            })
            addHierarchyBoundsListener(object : java.awt.event.HierarchyBoundsAdapter() {
                override fun ancestorMoved(event: java.awt.event.HierarchyEvent) = updateEmbeddedBounds()
                override fun ancestorResized(event: java.awt.event.HierarchyEvent) = updateEmbeddedBounds()
            })
        }
        background = Color.BLACK
        ignoreRepaint = false
        addMouseMotionListener(object : MouseMotionAdapter() {
            override fun mouseMoved(event: MouseEvent) {
                noteCursorActivity()
            }

            override fun mouseDragged(event: MouseEvent) {
                noteCursorActivity()
            }
        })
        // On Linux/XWayland a heavyweight Canvas embedded in a Compose SwingPanel is not
        // guaranteed an expose-driven paint() when it is first laid out, so the paint()-based
        // first-full-size-paint signal (which unlocks the native attach) can never fire and
        // playback silently never starts. componentResized fires reliably on layout, so use it
        // to drive the same signal. Linux-only to keep macOS/Windows behaviour byte-identical.
        if (DesktopHostOs.current == DesktopHostOs.LINUX) {
            addComponentListener(object : ComponentAdapter() {
                override fun componentResized(event: ComponentEvent) {
                    repaint()
                    notifyFirstPaints()
                }

                override fun componentShown(event: ComponentEvent) {
                    repaint()
                    notifyFirstPaints()
                }
            })
        }
    }

    fun updateEmbeddedBounds() {
        if (!embedded) return
        val window = javax.swing.SwingUtilities.getWindowAncestor(this) ?: return
        val origin = javax.swing.SwingUtilities.convertPoint(this, 0, 0, window)
        embeddedBounds = if (presentationFullscreen) doubleArrayOf(
            0.0, 0.0,
            (window.width - window.insets.left - window.insets.right).toDouble(),
            (window.height - window.insets.top - window.insets.bottom).toDouble(), 0.0,
        ) else doubleArrayOf(
            (origin.x - window.insets.left).toDouble(), (origin.y - window.insets.top).toDouble(),
            width.toDouble(), height.toDouble(), cornerRadius,
        )
        onBoundsChanged?.invoke()
    }

    private fun notifyFirstPaints() {
        if (!firstPaintNotified) {
            firstPaintNotified = true
            onFirstPaint?.invoke()
        }
        if (!firstFullSizePaintNotified && width > 1 && height > 1) {
            firstFullSizePaintNotified = true
            onFirstFullSizePaint?.invoke()
        }
    }

    fun setControlsVisible(visible: Boolean) {
        controlsVisible = visible
        setCursorVisible(visible)
    }

    fun noteCursorActivity() {
        onCursorActivity?.invoke()
    }

    fun resetCursorVisibility() {
        controlsVisible = true
        setCursorVisible(true)
    }

    private fun setCursorVisible(visible: Boolean) {
        if (cursorVisible == visible) return
        cursorVisible = visible
        cursor = if (visible) Cursor.getDefaultCursor() else hiddenCursor
    }

    override fun update(graphics: Graphics) {
        paint(graphics)
    }

    override fun paint(graphics: Graphics) {
        graphics.color = Color.BLACK
        graphics.fillRect(0, 0, width, height)
        updateEmbeddedBounds()
        notifyFirstPaints()
    }

    override fun addNotify() {
        super.addNotify()
        onDisplayableChanged?.invoke(true)
        repaint()
        onPeerReady?.invoke()
    }

    override fun removeNotify() {
        onDisplayableChanged?.invoke(false)
        firstPaintNotified = false
        firstFullSizePaintNotified = false
        onPeerReady = null
        onFirstPaint = null
        onFirstFullSizePaint = null
        resetCursorVisibility()
        super.removeNotify()
    }
}
