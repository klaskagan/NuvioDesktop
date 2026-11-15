package com.nuvio.app.core.format

import kotlin.test.Test
import kotlin.test.assertEquals

class WholeUsdDisplayTest {
    @Test
    fun usesPlainDollarSignInCanadianEnglish() {
        assertEquals("$2,923,706,026", formatWholeUsd(2_923_706_026L, "en-CA"))
    }
}
