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
) {
    FRESH_START("fresh_start", Res.string.seasonal_fresh_start, Res.string.seasonal_fresh_start_description,
        Color(0xFF244B65), keywords = listOf("self-discovery", "new beginning"),
        backgroundArtwork = Res.drawable.seasonal_fresh_start_background),
    LOVE("love", Res.string.seasonal_love, Res.string.seasonal_love_description,
        Color(0xFF6A2944), genres = "10749",
        backgroundArtwork = Res.drawable.seasonal_love_background),
    WORLDS("worlds", Res.string.seasonal_worlds, Res.string.seasonal_worlds_description,
        Color(0xFF353169), genres = "878|14",
        backgroundArtwork = Res.drawable.seasonal_worlds_background),
    FAMILY("family", Res.string.seasonal_family, Res.string.seasonal_family_description,
        Color(0xFF375D42), genres = "10751", seedMovieIds = listOf(354912, 508965, 12, 862),
        backgroundArtwork = Res.drawable.seasonal_family_background),
    ADVENTURE("adventure", Res.string.seasonal_adventure, Res.string.seasonal_adventure_description,
        Color(0xFF65502C), genres = "12",
        backgroundArtwork = Res.drawable.seasonal_adventure_background),
    SUMMER("summer", Res.string.seasonal_summer, Res.string.seasonal_summer_description,
        Color(0xFF29626A), keywords = listOf("summer", "road trip", "summer vacation"),
        backgroundArtwork = Res.drawable.seasonal_summer_background),
    SCHOOL("school", Res.string.seasonal_school, Res.string.seasonal_school_description,
        Color(0xFF59466A), keywords = listOf("high school", "coming of age", "college"),
        backgroundArtwork = Res.drawable.seasonal_school_background),
    HALLOWEEN("halloween", Res.string.seasonal_halloween, Res.string.seasonal_halloween_description,
        Color(0xFF17484C), genres = "27", seedMovieIds = listOf(1933, 2668, 138843, 14836),
        backgroundArtwork = Res.drawable.seasonal_halloween_background),
    CHRISTMAS("christmas", Res.string.seasonal_christmas, Res.string.seasonal_christmas_description,
        Color(0xFF284C39), keywords = listOf("christmas"), seedMovieIds = listOf(771, 508965, 1581, 10719),
        backgroundArtwork = Res.drawable.seasonal_christmas_background);

    fun filters(keywordIds: List<Int>, today: String) = TmdbCollectionFilters(
        withGenres = genres,
        withKeywords = keywordIds.takeIf { it.isNotEmpty() }?.joinToString("|"),
        voteCountGte = 100,
        voteAverageGte = 6.0,
        releaseDateLte = today,
    )

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
        fun fromId(id: String): SeasonalTheme = entries.first { it.id == id }
    }
}
