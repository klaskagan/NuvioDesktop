package com.nuvio.app.features.collection

import com.nuvio.app.features.catalog.CatalogPage
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.home.stableKey
import com.nuvio.app.features.watched.WatchedRepository
import com.nuvio.app.features.watching.application.WatchingState
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlin.random.Random
import nuvio.composeapp.generated.resources.seasonal_sort_unavailable

enum class SeasonalSort { SOURCE, HIGHEST_RATED, LOWEST_RATED, POPULARITY, NEWEST, OLDEST, TITLE, TITLE_DESC, SHUFFLED }

data class SeasonalGridQuery(
    val sort: SeasonalSort = SeasonalSort.SOURCE,
    val mediaType: String = "all",
    val genreId: Int? = null,
    val genreName: String? = null,
    val year: Int? = null,
    val minimumRating: Double? = null,
    val watched: Boolean? = null,
)

data class SeasonalGridState(
    val query: SeasonalGridQuery = SeasonalGridQuery(),
    val items: List<MetaPreview> = emptyList(),
    val isLoading: Boolean = false,
    val hasMore: Boolean = true,
    val error: String? = null,
)

/** A merge of server-ordered streams. Refill an exhausted head BEFORE choosing the next result.
 * Sorting the union of just the fetched pages would violate order at subsequent page boundaries.
 */
internal class SeasonalGridPager(
    sources: List<CollectionSource>,
    private val query: SeasonalGridQuery,
    private val load: suspend (CollectionSource, Int) -> CatalogPage = ::resolveSeasonalSource,
    private val watched: (MetaPreview) -> Boolean = { WatchingState.isPosterWatched(
        WatchedRepository.uiState.value.watchedKeys, it, WatchedRepository.fullyWatchedSeriesKeys.value) },
    seed: Long = Random.nextLong(),
) {
    private class Cursor(val source: CollectionSource, var next: Int? = 1) {
        var buffer = emptyList<MetaPreview>()
        var index = 0
        val seenPages = mutableSetOf<Int>()
        fun head() = buffer.getOrNull(index)
    }
    private val random = Random(seed)
    private val cursors = sources.flatMap { it.mediaSources() }.flatMap { expandGenreAlternatives(it, query) }.filter { source ->
        (query.year == null || source.filters?.year == null || source.filters.year == query.year) &&
        (query.mediaType == "all" || source.mediaType.let { if (it.equals("tv", true)) "series" else it?.lowercase() } == query.mediaType ||
            source.type == query.mediaType)
    }.map { Cursor(requestSource(it, query)) }
    private val seen = mutableSetOf<String>()
    private var turn = 0
    private val comparator = comparator(query.sort)
    val hasMore: Boolean get() = cursors.any { it.head() != null || it.next != null }

    suspend fun nextPage(size: Int = 40): List<MetaPreview> {
        require(size > 0)
        val snapshots = cursors.map { listOf(it.next, it.buffer, it.index, it.seenPages.toSet()) }
        val priorSeen = seen.toSet()
        val priorTurn = turn
        try {
        if (comparator != null && cursors.any { !supportsServerSort(it.source, query.sort) }) {
            error(org.jetbrains.compose.resources.getString(nuvio.composeapp.generated.resources.Res.string.seasonal_sort_unavailable))
        }
        val output = mutableListOf<MetaPreview>()
        while (output.size < size) {
            coroutineScope { cursors.filter { it.head() == null && it.next != null }.map { cursor ->
                async { refill(cursor) }
            }.awaitAll() }
            val candidates = cursors.filter { it.head() != null }
            if (candidates.isEmpty()) break
            val chosen = when {
                comparator != null -> candidates.minWith { a, b -> comparator.compare(a.head()!!, b.head()!!) }
                query.sort == SeasonalSort.SHUFFLED -> candidates[random.nextInt(candidates.size)]
                else -> { val next = candidates[turn % candidates.size]; turn++; next }
            }
            val item = chosen.head()!!
            chosen.index++
            if (seen.add(item.seasonalIdentity()) && matches(item)) output += item
        }
        return output
        } catch (error: Throwable) {
            cursors.zip(snapshots).forEach { (cursor, snapshot) ->
                cursor.next = snapshot[0] as Int?
                @Suppress("UNCHECKED_CAST")
                cursor.buffer = snapshot[1] as List<MetaPreview>
                cursor.index = snapshot[2] as Int
                cursor.seenPages.clear()
                @Suppress("UNCHECKED_CAST")
                cursor.seenPages.addAll(snapshot[3] as Set<Int>)
            }
            seen.clear(); seen.addAll(priorSeen); turn = priorTurn
            throw error
        }
    }

    private suspend fun refill(cursor: Cursor) {
        while (cursor.head() == null && cursor.next != null) {
            val pageNumber = cursor.next!!
            check(pageNumber !in cursor.seenPages) { "Catalog pagination did not advance" }
            val result = load(cursor.source, pageNumber)
            cursor.seenPages += pageNumber
            cursor.buffer = result.items.filter { item ->
                query.genreName == null || (cursor.source.isTmdb && supportsServerSort(cursor.source)) || item.genres.any { it.replace('-', ' ').equals(query.genreName, true) }
            }
            cursor.index = 0
            cursor.next = result.nextSkip
        }
    }

    private fun matches(item: MetaPreview): Boolean {
        if (query.mediaType != "all" && item.type != query.mediaType) return false
        if (query.year != null && (item.rawReleaseDate ?: item.releaseInfo)?.take(4)?.toIntOrNull() != query.year) return false
        if (query.minimumRating != null && (item.sortRating ?: item.imdbRating?.toDoubleOrNull() ?: -1.0) < query.minimumRating) return false
        // TMDB discovery applies genres on the server; external sources use their supplied metadata.
        if (query.watched != null && watched(item) != query.watched) return false
        return true
    }

    companion object {
        fun supportsServerSort(source: CollectionSource, sort: SeasonalSort = SeasonalSort.HIGHEST_RATED): Boolean =
            (source.isTmdb && source.tmdbSourceType in setOf("DISCOVER", "COMPANY", "NETWORK")) ||
            (source.isTrakt && sort in setOf(SeasonalSort.HIGHEST_RATED, SeasonalSort.LOWEST_RATED,
                SeasonalSort.NEWEST, SeasonalSort.OLDEST, SeasonalSort.TITLE, SeasonalSort.TITLE_DESC))

        private fun expandGenreAlternatives(source: CollectionSource, query: SeasonalGridQuery): List<CollectionSource> {
            val genres = source.filters?.withGenres ?: return listOf(source)
            if (query.genreId == null || '|' !in genres) return listOf(source)
            val combinations = genres.split(',').fold(listOf(emptyList<String>())) { prior, group ->
                prior.flatMap { prefix -> group.split('|').map { prefix + it } }
            }
            return combinations.map { source.copy(filters = source.filters.copy(withGenres = it.joinToString(","))) }
        }

        fun requestSource(source: CollectionSource, query: SeasonalGridQuery): CollectionSource {
            if (source.isTrakt) {
                val sortBy = when (query.sort) {
                    SeasonalSort.HIGHEST_RATED, SeasonalSort.LOWEST_RATED -> "percentage"
                    SeasonalSort.NEWEST, SeasonalSort.OLDEST -> "released"
                    SeasonalSort.TITLE, SeasonalSort.TITLE_DESC -> "title"
                    else -> source.sortBy
                }
                val ascending = query.sort in setOf(SeasonalSort.LOWEST_RATED, SeasonalSort.OLDEST, SeasonalSort.TITLE)
                return source.copy(sortBy = sortBy, sortHow = if (query.sort in setOf(SeasonalSort.SOURCE, SeasonalSort.SHUFFLED)) source.sortHow else if (ascending) "asc" else "desc")
            }
            if (!supportsServerSort(source)) return source
            val filters = source.filters ?: TmdbCollectionFilters()
            val sort = when (query.sort) {
                SeasonalSort.HIGHEST_RATED -> "vote_average.desc"
                SeasonalSort.LOWEST_RATED -> "vote_average.asc"
                SeasonalSort.POPULARITY -> "popularity.desc"
                SeasonalSort.NEWEST -> "primary_release_date.desc"
                SeasonalSort.OLDEST -> "primary_release_date.asc"
                SeasonalSort.TITLE -> if (source.mediaType.equals("tv", true)) "name.asc" else "title.asc"
                SeasonalSort.TITLE_DESC -> if (source.mediaType.equals("tv", true)) "name.desc" else "title.desc"
                else -> source.sortBy
            }
            val tv = source.mediaType.equals("tv", true)
            val genre = query.genreId?.let { id -> if (!tv) id else when (id) {
                12, 28 -> 10759
                14, 878 -> 10765
                27, 53, 10402 -> null
                10749 -> 18
                else -> id
            } }
            val keyword = if (tv) when (query.genreId) {
                27 -> "horror"; 53 -> "thriller"; 10402 -> "music"; 10749 -> "romance"; else -> null
            } else null
            return source.copy(sortBy = sort, seasonalKeywords = source.seasonalKeywords + listOfNotNull(keyword), filters = filters.copy(
                withGenres = genre?.let { id -> listOfNotNull(filters.withGenres, id.toString()).joinToString(",") } ?: filters.withGenres,
                year = query.year ?: filters.year,
                voteAverageGte = query.minimumRating?.let { maxOf(it, filters.voteAverageGte ?: 0.0) } ?: filters.voteAverageGte))
        }

        fun comparator(sort: SeasonalSort): Comparator<MetaPreview>? = when (sort) {
            SeasonalSort.HIGHEST_RATED -> compareByDescending { it.sortRating ?: it.imdbRating?.toDoubleOrNull() ?: -1.0 }
            SeasonalSort.LOWEST_RATED -> compareBy { it.sortRating ?: it.imdbRating?.toDoubleOrNull() ?: Double.MAX_VALUE }
            SeasonalSort.POPULARITY -> compareByDescending { it.popularity ?: -1.0 }
            SeasonalSort.NEWEST -> compareByDescending { it.rawReleaseDate.orEmpty() }
            SeasonalSort.OLDEST -> compareBy { it.rawReleaseDate ?: "9999" }
            SeasonalSort.TITLE -> compareBy { it.name.lowercase() }
            SeasonalSort.TITLE_DESC -> compareByDescending { it.name.lowercase() }
            else -> null
        }
    }
}
