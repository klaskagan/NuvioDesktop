package com.nuvio.app.features.home

import kotlin.test.Test
import kotlin.test.assertEquals

class SeasonalThemeWindowTest {
    @Test fun halloweenRunsUntilTheEndOfNovember() {
        assertEquals("2026-11-30", SeasonalTheme.HALLOWEEN.windowEndIsoDate("2026-10-03"))
        assertEquals("2026-11-30", SeasonalTheme.HALLOWEEN.windowEndIsoDate("2026-11-26"))
    }

    @Test fun summerSpansThreeMonths() {
        assertEquals("2026-08-31", SeasonalTheme.SUMMER.windowEndIsoDate("2026-07-14"))
    }

    @Test fun singleMonthThemesEndOnTheirLastDay() {
        assertEquals("2026-12-31", SeasonalTheme.CHRISTMAS.windowEndIsoDate("2026-12-01"))
        assertEquals("2028-02-29", SeasonalTheme.LOVE.windowEndIsoDate("2028-02-10"))
        assertEquals("2027-02-28", SeasonalTheme.LOVE.windowEndIsoDate("2027-02-10"))
    }
}
