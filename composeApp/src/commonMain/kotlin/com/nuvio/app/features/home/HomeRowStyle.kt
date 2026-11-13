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
