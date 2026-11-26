package com.nuvio.app.features.home

import com.nuvio.app.features.collection.SeasonalCollection
import com.nuvio.app.features.trakt.TRAKT_CALENDAR_KEY

enum class HomeRowStyle(
    val id: String,
    internal val posterShape: PosterShape? = null,
    internal val landscapeWidthFactor: Float = 1f,
) {
    Automatic("automatic"),
    Posters("posters", PosterShape.Poster),
    LandscapeSmall("landscape-small", PosterShape.Landscape, 360f / 480f),
    LandscapeMedium("landscape-medium", PosterShape.Landscape),
    LandscapeLarge("landscape-large", PosterShape.Landscape, 640f / 480f),
    BannerWithTitle("banner-with-title", PosterShape.Landscape, 384f / 480f),
    NumberedRanking("numbered-ranking", PosterShape.Poster),
    Round("round", PosterShape.Square);

    internal val isLandscapeSize: Boolean
        get() = posterShape == PosterShape.Landscape && this != BannerWithTitle

    internal val enhancesLandscapeArtwork: Boolean
        get() = this == Automatic || isLandscapeSize

    internal fun artworkItem(item: MetaPreview): MetaPreview =
        if (this == BannerWithTitle) item.copy(logo = null, banner = item.banner ?: item.landscapePoster) else item

    companion object {
        internal fun fromId(id: String?): HomeRowStyle = entries.firstOrNull { it.id == id } ?: Automatic
    }
}

internal fun supportsHomeRowStyle(key: String): Boolean =
    key != TRAKT_CALENDAR_KEY && key != SeasonalCollection.HOME_KEY

/** What a row has to supply before the Row style picker offers a style that depends on it. */
internal enum class HomeRowStyleNeed { AirDates, Logos, Overviews, Description }

/**
 * One cell of the Row style picker. Landscape is a single cell; its size is the inline
 * Small / Medium / Large switch. [needs] lists what the row must supply for the style to fill.
 */
internal enum class HomeRowStyleChoice(
    val id: String,
    val applied: HomeRowStyle,
    val needs: Set<HomeRowStyleNeed> = emptySet(),
) {
    Automatic("automatic", HomeRowStyle.Automatic),
    Posters("posters", HomeRowStyle.Posters),
    Landscape("landscape", HomeRowStyle.LandscapeMedium),
    BannerWithTitle("banner-with-title", HomeRowStyle.BannerWithTitle),
    NumberedRanking("numbered-ranking", HomeRowStyle.NumberedRanking),
    Round("round", HomeRowStyle.Round);

    internal fun matches(style: HomeRowStyle): Boolean =
        if (this == Landscape) style.isLandscapeSize else style == applied
}

/**
 * The picker's choices for [key]: none for rows that can't change style, otherwise every style
 * whose [HomeRowStyleChoice.needs] the row can supply ([rowSupplies]).
 */
internal fun homeRowStyleChoices(
    key: String,
    rowSupplies: Set<HomeRowStyleNeed> = emptySet(),
): List<HomeRowStyleChoice> =
    if (!supportsHomeRowStyle(key)) emptyList()
    else HomeRowStyleChoice.entries.filter { rowSupplies.containsAll(it.needs) }
