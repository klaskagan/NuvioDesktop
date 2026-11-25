package com.nuvio.app.core.format

import kotlin.test.Test
import kotlin.test.assertEquals

class RegionDisplayTest {
    @Test
    fun namesTwoLetterCodesAndKeepsNames() {
        assertEquals("United States, United Kingdom", formatCountriesForDisplay("US, gb", "en-US"))
        assertEquals("United States", formatCountriesForDisplay("United States", "en-US"))
    }
}
