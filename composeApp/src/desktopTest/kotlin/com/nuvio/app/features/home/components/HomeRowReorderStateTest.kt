package com.nuvio.app.features.home.components

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HomeRowReorderStateTest {
    private val rows = listOf("a", "b", "c", "d")

    @Test
    fun stepsMoveThePickedUpRowOnePlaceAndStopAtTheEnds() {
        val state = HomeRowReorderState()
        state.start("b", rows)

        assertTrue(state.step(1))
        assertEquals(listOf("a", "c", "b", "d"), state.order)
        assertTrue(state.step(1))
        assertFalse(state.canStep(1))
        assertFalse(state.step(1))
        assertEquals(listOf("a", "c", "d", "b"), state.order)
        assertEquals(2, state.keyboardSteps)
    }

    @Test
    fun dragOntoARowTakesItsPlace() {
        val state = HomeRowReorderState()
        state.start("d", rows)

        assertTrue(state.moveOnto("a"))

        assertEquals(listOf("d", "a", "b", "c"), state.order)
    }

    @Test
    fun arrangeFillsOnlyTheReorderableSlots() {
        val state = HomeRowReorderState()
        // "seasonal" and "hidden" are not part of the move; they keep their slots.
        val drawn = listOf("pinned", "a", "seasonal", "b", "hidden", "c")
        state.start("c", listOf("a", "b", "c"))
        state.step(-1)
        state.step(-1)

        assertEquals(listOf("pinned", "c", "seasonal", "a", "hidden", "b"), state.arrange(drawn) { it })
    }

    @Test
    fun cancelPutsEveryRowBackWithoutSaving() {
        val state = HomeRowReorderState()
        state.start("a", rows)
        state.step(1)

        state.finish(save = false)

        assertFalse(state.active)
        assertEquals(emptyList(), state.order)
        assertEquals(rows, state.arrange(rows) { it })
    }

    @Test
    fun aRowTheTabDoesNotShowCannotBePickedUp() {
        val state = HomeRowReorderState()
        state.start("x", rows)

        assertFalse(state.active)
    }
}
