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
    Round("round", PosterShape.Square),
    CompactList("compact-list", PosterShape.Landscape),
    Mosaic("mosaic", PosterShape.Poster),
    Spotlight("spotlight", PosterShape.Landscape),
    LogoWall("logo-wall", PosterShape.Landscape),
    CalendarStrip("calendar-strip", PosterShape.Landscape),
    InfoCards("info-cards", PosterShape.Poster),
    EditorialBand("editorial-band", PosterShape.Poster),
    CollageBanner("collage-banner", PosterShape.Landscape);

    internal val isLandscapeSize: Boolean
        get() = this == LandscapeSmall || this == LandscapeMedium || this == LandscapeLarge

    /** The styles approved 25 Nov, each with its own row layout ([HomeRowStyleLayouts]). */
    internal val hasOwnLayout: Boolean
        get() = ordinal >= CompactList.ordinal

    /** Where a row's own layout can't be drawn (mobile), the plain style it shows instead. */
    internal val plainFallback: HomeRowStyle
        get() = when {
            !hasOwnLayout -> this
            posterShape == PosterShape.Landscape -> LandscapeMedium
            else -> Posters
        }

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
internal enum class HomeRowStyleNeed {
    /** The row is made of titles (not folder tiles) and can open See all. */
    Titles,
    AirDates,
    Logos,
    Overviews,
}

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
    Round("round", HomeRowStyle.Round),
    CompactList("compact-list", HomeRowStyle.CompactList),
    Mosaic("mosaic", HomeRowStyle.Mosaic),
    Spotlight("spotlight", HomeRowStyle.Spotlight, setOf(HomeRowStyleNeed.Titles)),
    LogoWall("logo-wall", HomeRowStyle.LogoWall, setOf(HomeRowStyleNeed.Logos)),
    CalendarStrip("calendar-strip", HomeRowStyle.CalendarStrip, setOf(HomeRowStyleNeed.Titles, HomeRowStyleNeed.AirDates)),
    InfoCards("info-cards", HomeRowStyle.InfoCards, setOf(HomeRowStyleNeed.Titles, HomeRowStyleNeed.Overviews)),
    // Always offered: its line is the user's to write, inline in the picker.
    EditorialBand("editorial-band", HomeRowStyle.EditorialBand),
    CollageBanner("collage-banner", HomeRowStyle.CollageBanner, setOf(HomeRowStyleNeed.Titles));

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

/** Share of a row's items that must have something before a style that needs it is offered. */
private const val SUPPLY_SHARE = 0.6f

private fun <T> List<T>.mostHave(has: (T) -> Boolean): Boolean =
    isNotEmpty() && count(has) >= size * SUPPLY_SHARE

/** What a catalog row of [items] can fill; [today] is an ISO date (yyyy-MM-dd). */
internal fun homeRowStyleSupplies(items: List<MetaPreview>, today: String): Set<HomeRowStyleNeed> = buildSet {
    add(HomeRowStyleNeed.Titles)
    if (items.mostHave { !it.logo.isNullOrBlank() }) add(HomeRowStyleNeed.Logos)
    if (items.mostHave { !it.description.isNullOrBlank() }) add(HomeRowStyleNeed.Overviews)
    if (items.mostHave { item -> item.calendarCard != null || item.upcomingDate(today) != null }) add(HomeRowStyleNeed.AirDates)
}

/** What a collection row (folder tiles) can fill: logos when most folders have a title logo. */
internal fun collectionRowStyleSupplies(folders: List<com.nuvio.app.features.collection.CollectionFolder>): Set<HomeRowStyleNeed> =
    if (folders.mostHave { !it.titleLogoUrl.isNullOrBlank() }) setOf(HomeRowStyleNeed.Logos) else emptySet()

/** The title's release or air date when it is [today] or later (ISO yyyy-MM-dd), else null. */
internal fun MetaPreview.upcomingDate(today: String): String? {
    // "Airing Next" catalogs carry the show's premiere as its release date and the next episode's
    // air date only in the description ("Next Episode: S02E05 — Title · Airs 2026-11-30").
    val airs = description?.let { AIRS_DATE.find(it)?.groupValues?.get(1) }
    val date = calendarCard?.localDate ?: airs ?: rawReleaseDate?.take(10)?.takeIf { ISO_DATE.matches(it) } ?: return null
    return date.takeIf { it >= today }
}

/** "S2 E5 · Out Of The Past" from an Airing Next description, else null. */
internal fun MetaPreview.nextEpisodeLine(): String? {
    val match = description?.let { NEXT_EPISODE.find(it) } ?: return null
    val (season, episode, title) = match.destructured
    return "S${season.toInt()} E${episode.toInt()}" + title.trim().takeIf { it.isNotEmpty() }?.let { " · $it" }.orEmpty()
}

private val ISO_DATE = Regex("""\d{4}-\d{2}-\d{2}""")
private val AIRS_DATE = Regex("""\bAirs:?\s+(\d{4}-\d{2}-\d{2})""", RegexOption.IGNORE_CASE)
private val NEXT_EPISODE = Regex("""S(\d{1,2})\s*E(\d{1,3})\s*(?:[—–-]\s*([^·\n]*))?""", RegexOption.IGNORE_CASE)
