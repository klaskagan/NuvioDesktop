package com.nuvio.app.core.format

import kotlin.test.Test
import kotlin.test.assertEquals

class CompactUsdDisplayTest {
    @Test
    fun roundsToOneDecimalWithAUnit() {
        assertEquals("$190M", formatCompactUsd(190_000_000L, "en-US"))
        assertEquals("$714.8M", formatCompactUsd(714_844_358L, "en-US"))
        assertEquals("$2.9B", formatCompactUsd(2_923_706_026L, "en-US"))
    }

    @Test
    fun keepsSmallAmountsWhole() {
        assertEquals("$950,000", formatCompactUsd(950_000L, "en-US"))
    }
}
