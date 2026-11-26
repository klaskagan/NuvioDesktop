package com.nuvio.app.features.home

import androidx.compose.ui.graphics.Color
import com.nuvio.app.features.collection.TmdbCollectionFilters
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource

/** Editorial windows use the device's local month, independent of the year. */
enum class SeasonalTheme(
    val id: String,
    val title: StringResource,
    val description: StringResource,
    val tint: Color,
    val genres: String? = null,
    val keywords: List<String> = emptyList(),
    val seedMovieIds: List<Int> = emptyList(),
    val backgroundArtwork: DrawableResource? = null,
    /** The eyebrow's warm accent over the theme's artwork (Home band, Explore). */
    val accent: Color = Color(0xFFE0B48A),
) {
    FRESH_START("fresh_start", Res.string.seasonal_fresh_start, Res.string.seasonal_fresh_start_description,
        Color(0xFF244B65), keywords = listOf("self-discovery", "new beginning"),
        backgroundArtwork = Res.drawable.seasonal_fresh_start_background, accent = Color(0xFFF2C98E)),
    LOVE("love", Res.string.seasonal_love, Res.string.seasonal_love_description,
        Color(0xFF6A2944), genres = "10749",
        backgroundArtwork = Res.drawable.seasonal_love_background, accent = Color(0xFFF2A9B8)),
    WORLDS("worlds", Res.string.seasonal_worlds, Res.string.seasonal_worlds_description,
        Color(0xFF353169), genres = "878|14",
        backgroundArtwork = Res.drawable.seasonal_worlds_background, accent = Color(0xFFD9B98F)),
    FAMILY("family", Res.string.seasonal_family, Res.string.seasonal_family_description,
        Color(0xFF375D42), genres = "10751", seedMovieIds = listOf(354912, 508965, 12, 862),
        backgroundArtwork = Res.drawable.seasonal_family_background, accent = Color(0xFFE8C98A)),
    ADVENTURE("adventure", Res.string.seasonal_adventure, Res.string.seasonal_adventure_description,
        Color(0xFF65502C), genres = "12",
        backgroundArtwork = Res.drawable.seasonal_adventure_background, accent = Color(0xFFE9BF83)),
    SUMMER("summer", Res.string.seasonal_summer, Res.string.seasonal_summer_description,
        Color(0xFF29626A), keywords = listOf("summer", "road trip", "summer vacation"),
        backgroundArtwork = Res.drawable.seasonal_summer_background, accent = Color(0xFFF4C47E)),
    SCHOOL("school", Res.string.seasonal_school, Res.string.seasonal_school_description,
        Color(0xFF59466A), keywords = listOf("high school", "coming of age", "college"),
        backgroundArtwork = Res.drawable.seasonal_school_background, accent = Color(0xFFE2BC94)),
    HALLOWEEN("halloween", Res.string.seasonal_halloween, Res.string.seasonal_halloween_description,
        Color(0xFF17484C), genres = "27", seedMovieIds = listOf(1933, 2668, 138843, 14836),
        backgroundArtwork = Res.drawable.seasonal_halloween_background, accent = Color(0xFFE0B48A)),
    CHRISTMAS("christmas", Res.string.seasonal_christmas, Res.string.seasonal_christmas_description,
        Color(0xFF284C39), keywords = listOf("christmas"), seedMovieIds = listOf(771, 508965, 1581, 10719),
        backgroundArtwork = Res.drawable.seasonal_christmas_background, accent = Color(0xFFE9C08C));

    fun filters(keywordIds: List<Int>, today: String) = TmdbCollectionFilters(
        withGenres = genres,
        withKeywords = keywordIds.takeIf { it.isNotEmpty() }?.joinToString("|"),
        voteCountGte = 100,
        voteAverageGte = 6.0,
        releaseDateLte = today,
    )

    /**
     * The last day of this theme's run of months that contains [todayIso] (Halloween in October →
     * 30 Nov), as an ISO date; the theme's last month when today is outside it.
     */
    fun windowEndIsoDate(todayIso: String): String {
        val year = todayIso.substring(0, 4).toInt()
        val today = todayIso.substring(5, 7).toInt()
        val months = (1..12).filter { forMonth(it) == this }
        var end = if (today in months) today else months.last()
        while (end < 12 && forMonth(end + 1) == this) end++
        val day = daysInMonth(year, end)
        return "$year-${end.toString().padStart(2, '0')}-${day.toString().padStart(2, '0')}"
    }

    companion object {
        fun forMonth(month: Int): SeasonalTheme = when (month) {
            1 -> FRESH_START
            2 -> LOVE
            3 -> WORLDS
            4 -> FAMILY
            5 -> ADVENTURE
            in 6..8 -> SUMMER
            9 -> SCHOOL
            10, 11 -> HALLOWEEN
            12 -> CHRISTMAS
            else -> error("Invalid month: $month")
        }

        fun forDate(isoDate: String): SeasonalTheme = forMonth(isoDate.substring(5, 7).toInt())

        private fun daysInMonth(year: Int, month: Int): Int = when (month) {
            2 -> if ((year % 4 == 0 && year % 100 != 0) || year % 400 == 0) 29 else 28
            4, 6, 9, 11 -> 30
            else -> 31
        }
        fun fromId(id: String): SeasonalTheme = entries.first { it.id == id }
    }
}
