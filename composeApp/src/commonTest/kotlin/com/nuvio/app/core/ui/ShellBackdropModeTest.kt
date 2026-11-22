package com.nuvio.app.core.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class ShellBackdropModeTest {
    @Test
    fun ambientIsTheDefaultAndUnknownValuesFallBackToIt() {
        assertEquals(ShellBackdropMode.Ambient, ShellBackdropMode.Default)
        assertEquals(ShellBackdropMode.Ambient, ShellBackdropMode.fromName(null))
        assertEquals(ShellBackdropMode.Ambient, ShellBackdropMode.fromName("aurora"))
        assertEquals(ShellBackdropMode.Mesh, ShellBackdropMode.fromName("Mesh"))
        assertEquals(ShellBackdropMode.Solid, ShellBackdropMode.fromName("solid"))
    }
}
