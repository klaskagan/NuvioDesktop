package com.nuvio.app

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class AppKeyboardShortcutsTest {
    @AfterTest
    fun reset() = AppKeyboardShortcuts.clearOverlayEscapes()

    @Test
    fun innermostLayerTakesEscapeAndReleasingHandsItBack() {
        val events = mutableListOf<String>()
        val list: () -> Boolean = { events += "list"; true }
        val menu: () -> Boolean = { events += "menu"; true }
        AppKeyboardShortcuts.claimOverlayEscape(list)
        AppKeyboardShortcuts.claimOverlayEscape(menu)

        AppKeyboardShortcuts.overlayEscape?.invoke()
        AppKeyboardShortcuts.releaseOverlayEscape(menu)
        AppKeyboardShortcuts.overlayEscape?.invoke()

        // A single slot lost the list's claim once a card menu over it closed.
        assertEquals(listOf("menu", "list"), events)
    }

    @Test
    fun releasingAnOuterLayerKeepsTheInnerOne() {
        val outer: () -> Boolean = { true }
        val inner: () -> Boolean = { true }
        AppKeyboardShortcuts.claimOverlayEscape(outer)
        AppKeyboardShortcuts.claimOverlayEscape(inner)
        AppKeyboardShortcuts.releaseOverlayEscape(outer)
        assertSame(inner, AppKeyboardShortcuts.overlayEscape)
        AppKeyboardShortcuts.releaseOverlayEscape(inner)
        assertNull(AppKeyboardShortcuts.overlayEscape)
    }

    @Test
    fun assigningNullReleasesOnlyTheInnermostClaim() {
        val outer: () -> Boolean = { true }
        val inner: () -> Boolean = { true }
        AppKeyboardShortcuts.overlayEscape = outer
        AppKeyboardShortcuts.overlayEscape = inner
        AppKeyboardShortcuts.overlayEscape = null
        assertSame(outer, AppKeyboardShortcuts.overlayEscape)
    }
}
