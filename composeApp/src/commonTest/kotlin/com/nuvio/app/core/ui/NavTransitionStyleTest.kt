package com.nuvio.app.core.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class NavTransitionStyleTest {
    @Test
    fun heroCarouselIsTheDefaultAndUnknownValuesFallBackToIt() {
        assertEquals(NavTransitionStyle.HeroParallax, NavTransitionStyle.Default)
        assertEquals(NavTransitionStyle.HeroParallax, NavTransitionStyle.fromName(null))
        assertEquals(NavTransitionStyle.HeroParallax, NavTransitionStyle.fromName("cube"))
        assertEquals(NavTransitionStyle.FullSlide, NavTransitionStyle.fromName("FullSlide"))
        assertEquals(NavTransitionStyle.FullSlide, NavTransitionStyle.fromName("fullslide"))
    }
}
