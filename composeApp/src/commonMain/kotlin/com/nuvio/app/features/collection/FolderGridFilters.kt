package com.nuvio.app.features.collection

import com.nuvio.app.features.home.MetaPreview

/** Sort order of a folder or See all grid (Home design, "Pinned bar · spec"). */
internal enum class PageGridSort { CatalogOrder, Rating, Newest, AToZ }

internal enum class PageGridWatchStatus { Unwatched, Watched }

internal enum class PageGridType { Movies, Shows }

/** The pinned bar's menus. Nulls are "Any"; the bar only offers what the items can answer. */
internal data class PageGridFilters(
    val sort: PageGridSort = PageGridSort.CatalogOrder,
    val type: PageGridType? = null,
    val genre: String? = null,
    /** First year of a decade: 2020 for the 2020s. */
    val decade: Int? = null,
    val minRating: Int? = null,
    val watchStatus: PageGridWatchStatus? = null,
) {
    /** Menus set other than Sort; Clear shows while this is above zero or Sort is not the catalog's. */
    val activeCount: Int
        get() = listOfNotNull(type, genre, decade, minRating, watchStatus).size

    val isSet: Boolean
        get() = activeCount > 0 || sort != PageGridSort.CatalogOrder
}

/** What the loaded items can be filtered by, so menus the addon can't answer stay hidden. */
internal data class PageGridFacets(
    val genres: List<String>,
    val decades: List<Int>,
    val hasRatings: Boolean,
    val mixesTypes: Boolean,
)

internal fun MetaPreview.releaseYear(): Int? =
    (rawReleaseDate ?: releaseInfo)?.let { Regex("""(19|20)\d{2}""").find(it)?.value?.toIntOrNull() }

internal fun MetaPreview.ratingValue(): Double? =
    imdbRating?.toDoubleOrNull() ?: sortRating

private fun MetaPreview.isShow(): Boolean =
    type.equals("series", ignoreCase = true) || type.equals("tv", ignoreCase = true) || type.equals("show", ignoreCase = true)

internal fun pageGridFacets(items: List<MetaPreview>): PageGridFacets = PageGridFacets(
    genres = items.flatMap { it.genres }.filter { it.isNotBlank() }
        .groupingBy { it }.eachCount().entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
        .map { it.key },
    decades = items.mapNotNull { it.releaseYear()?.let { year -> year / 10 * 10 } }.distinct().sortedDescending(),
    hasRatings = items.any { it.ratingValue() != null },
    mixesTypes = items.any { it.isShow() } && items.any { !it.isShow() },
)

/** Applies the pinned bar's menus to a grid, keeping the catalog's order unless a sort is picked. */
internal fun applyPageGridFilters(
    items: List<MetaPreview>,
    filters: PageGridFilters,
    isWatched: (MetaPreview) -> Boolean,
): List<MetaPreview> {
    val filtered = items.filter { item ->
        (filters.type == null || (filters.type == PageGridType.Shows) == item.isShow()) &&
            (filters.genre == null || item.genres.any { it.equals(filters.genre, ignoreCase = true) }) &&
            (filters.decade == null || item.releaseYear()?.let { it / 10 * 10 } == filters.decade) &&
            (filters.minRating == null || (item.ratingValue() ?: 0.0) >= filters.minRating) &&
            when (filters.watchStatus) {
                null -> true
                PageGridWatchStatus.Watched -> isWatched(item)
                PageGridWatchStatus.Unwatched -> !isWatched(item)
            }
    }
    return when (filters.sort) {
        PageGridSort.CatalogOrder -> filtered
        PageGridSort.Rating -> filtered.sortedByDescending { it.ratingValue() ?: -1.0 }
        PageGridSort.Newest -> filtered.sortedByDescending { it.rawReleaseDate ?: it.releaseYear()?.toString() ?: "" }
        PageGridSort.AToZ -> filtered.sortedBy { it.name.lowercase() }
    }
}
