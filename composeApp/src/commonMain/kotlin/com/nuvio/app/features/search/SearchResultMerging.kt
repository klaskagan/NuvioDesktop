package com.nuvio.app.features.search

import com.nuvio.app.features.home.HomeCatalogSection
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.home.stableKey
import com.nuvio.app.features.details.PersonDetail
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

internal enum class SearchRelevanceTier {
    Exact,
    Prefix,
    WordBoundary,
    Contains,
    AnyWord,
    Other,
}

private val searchStopWords = setOf("a", "an", "the", "of", "and", "or", "in", "on", "to", "de", "la", "le", "el")

private val searchDiacritics: Map<Char, Char> = buildMap {
    fun map(chars: String, target: Char) = chars.forEach { put(it, target) }
    map("àáâãäåāăą", 'a')
    map("çćĉċč", 'c')
    map("ďđ", 'd')
    map("èéêëēĕėęě", 'e')
    map("ĝğġģ", 'g')
    map("ĥħ", 'h')
    map("ìíîïĩīĭįı", 'i')
    map("ĵ", 'j')
    map("ķ", 'k')
    map("ĺļľŀł", 'l')
    map("ñńņňŉ", 'n')
    map("òóôõöøōŏő", 'o')
    map("ŕŗř", 'r')
    map("śŝşšș", 's')
    map("ţťŧț", 't')
    map("ùúûüũūŭůűų", 'u')
    map("ŵ", 'w')
    map("ýÿŷ", 'y')
    map("źżž", 'z')
}

internal fun normalizeSearchText(value: String): String {
    val builder = StringBuilder(value.length)
    var lastWasSpace = true
    value.lowercase().forEach { raw ->
        val char = searchDiacritics[raw] ?: raw
        if (char.isLetterOrDigit()) {
            builder.append(char)
            lastWasSpace = false
        } else if (char == '\'' || char == '’') {
            // Drop apostrophes so "Ocean's" matches "oceans".
        } else if (!lastWasSpace) {
            builder.append(' ')
            lastWasSpace = true
        }
    }
    return builder.toString().trim()
}

private val leadingArticles = listOf("the ", "a ", "an ")

private fun String.withoutLeadingArticle(): String =
    leadingArticles.firstOrNull { startsWith(it) && length > it.length }?.let { removePrefix(it) } ?: this

internal fun searchRelevanceTier(name: String, query: String): SearchRelevanceTier {
    val title = normalizeSearchText(name)
    val normalizedQuery = normalizeSearchText(query)
    if (title.isEmpty() || normalizedQuery.isEmpty()) return SearchRelevanceTier.Other
    // "The Simpsons" is an exact match for "simpsons", and "The Simpsons Movie" a prefix match.
    val bareTitle = title.withoutLeadingArticle()
    val bareQuery = normalizedQuery.withoutLeadingArticle()
    if (title == normalizedQuery || bareTitle == bareQuery) return SearchRelevanceTier.Exact
    if (title.startsWith(normalizedQuery) || bareTitle.startsWith(bareQuery)) return SearchRelevanceTier.Prefix
    if (" $title".contains(" $normalizedQuery")) return SearchRelevanceTier.WordBoundary
    if (title.contains(normalizedQuery)) return SearchRelevanceTier.Contains
    val titleWords = title.split(' ').toSet()
    val queryWords = normalizedQuery.split(' ').filter { it.isNotEmpty() }
    val meaningfulWords = queryWords.filter { it !in searchStopWords }.ifEmpty { queryWords }
    if (meaningfulWords.all { it in titleWords }) return SearchRelevanceTier.Contains
    return if (meaningfulWords.any { it in titleWords }) SearchRelevanceTier.AnyWord else SearchRelevanceTier.Other
}

private fun normalizedSearchType(type: String): String =
    when (val lower = type.trim().lowercase()) {
        "tv", "show", "shows", "tvshow" -> "series"
        "movies", "film" -> "movie"
        else -> lower
    }

private fun MetaPreview.searchYear(): String? =
    (releaseInfo ?: rawReleaseDate)?.let { Regex("(19|20)\\d{2}").find(it)?.value }

private fun mediaIdKey(type: String, id: String): String? {
    val trimmed = id.trim()
    return when {
        Regex("^tt\\d+$").matches(trimmed) -> "$type:imdb:$trimmed"
        trimmed.startsWith("tmdb:") -> "$type:tmdb:${trimmed.removePrefix("tmdb:").substringBefore(':')}"
        else -> null
    }
}

/** All identity keys for an item; two items sharing any key are the same title. */
internal fun searchDedupKeys(item: MetaPreview): Set<String> {
    val type = normalizedSearchType(item.type)
    val keys = linkedSetOf<String>()
    item.canonicalMediaKey?.trim()?.takeIf { it.isNotEmpty() }?.let { keys += it.lowercase() }
    mediaIdKey(type, item.id)?.let(keys::add)
    item.alternateIds.forEach { alternate -> mediaIdKey(type, alternate)?.let(keys::add) }
    if (keys.isEmpty()) keys += "$type:id:${item.id.trim().lowercase()}"
    val title = normalizeSearchText(item.name)
    val year = item.searchYear()
    if (title.isNotEmpty() && year != null) keys += "$type:title:$title:$year"
    return keys
}

/** Primary key used for lazy item identity in the merged grid. */
internal fun searchDedupKey(item: MetaPreview): String = searchDedupKeys(item).first()

private fun MetaPreview.metadataScore(): Int =
    listOf(poster, banner, landscapePoster, description, releaseInfo, logo, imdbRating)
        .count { !it.isNullOrBlank() } + (if (genres.isNotEmpty()) 1 else 0)

private val richerMetadata: Comparator<MetaPreview> =
    compareByDescending<MetaPreview> { !it.poster.isNullOrBlank() }
        .thenByDescending { it.popularity ?: -1.0 }
        .thenByDescending { it.metadataScore() }

private class SearchGroup(val order: Int, first: MetaPreview) {
    val items = mutableListOf(first)
    val keys = linkedSetOf<String>()
    /** Best position of this title inside any single provider's own list. */
    var providerRank = Int.MAX_VALUE
    /** Items that came from the featured person's filmography rather than an addon search. */
    val creditItems = mutableListOf<MetaPreview>()
    /** Best position in the filmography list, which is already ordered by vote count. */
    var creditRank = Int.MAX_VALUE
    /** Items that came from the franchise (TMDB collection) the query names. */
    val franchiseItems = mutableListOf<MetaPreview>()
}

private class RankedSearchResult(
    val item: MetaPreview,
    val tier: SearchRelevanceTier,
    val isCredit: Boolean,
    val isFranchise: Boolean,
    val providerRank: Int,
    val creditRank: Int,
    val order: Int,
    creditsLeadTitleMatches: Boolean,
) {
    /**
     * 0: exact title matches, 1: the featured person's credits, 2: other title matches. Titles that
     * merely start with the person's name (documentaries about them) land in band 2. When the query
     * only partly matches the person ("vision" → Vision Wei), credits move to band 3, after every
     * title match. Films of a franchise the query names ("james bond" → Dr. No) join band 0.
     */
    val band: Int = when {
        tier == SearchRelevanceTier.Exact || isFranchise -> 0
        isCredit -> if (creditsLeadTitleMatches) 1 else 3
        else -> 2
    }
    val isCreditBand: Boolean get() = band == 1 || band == 3
}

private val rankedSearchOrder: Comparator<RankedSearchResult> =
    compareBy<RankedSearchResult> { it.band }
        .thenBy { if (it.isCreditBand) it.creditRank else 0 }
        .thenBy { if (it.isCreditBand) 0 else it.tier.ordinal }
        .thenByDescending { it.item.popularity ?: -1.0 }
        .thenByDescending { it.item.voteCount ?: -1 }
        .thenBy { if (it.isCreditBand) 0 else it.providerRank }
        .thenBy { it.order }

private val franchiseLedSearchOrder: Comparator<RankedSearchResult> =
    compareBy<RankedSearchResult> { it.band }
        .thenByDescending { if (it.band == 0) it.item.voteCount ?: -1 else 0 }
        .then(rankedSearchOrder)

private val collectionNameSuffix = Regex("\\s+collection$", RegexOption.IGNORE_CASE)

/** "James Bond Collection" is the franchise for "james bond"; "Bond" alone does not name it. */
internal fun isSearchFranchiseMatch(collectionName: String, query: String): Boolean {
    val name = collectionName.trim().replace(collectionNameSuffix, "")
    return name.isNotBlank() && searchRelevanceTier(name, query) == SearchRelevanceTier.Exact
}

/**
 * Merges every provider's search results into one list: duplicates (same IMDb/TMDB id, or same
 * normalized title + year + type) collapse into the entry with the richest metadata, titles that
 * share no query word are dropped, and the rest is ordered by match tier, then
 * popularity, vote count and provider rank.
 *
 * Titles matching only some query words are kept only when no title matches them all (typos).
 * [requireAllQueryWords] drops them even then. Search sets it when the
 * query matches a person, so "leonardo dicaprio" does not keep unrelated "Leonardo …" titles.
 *
 * [credits] is the featured person's filmography, in its own order. Credits skip the word filter.
 * With [creditsLeadTitleMatches] they rank after exact title matches but before other title
 * matches; otherwise after every title match. A credit that duplicates an addon result keeps the
 * addon's entry (and id) so playback sources resolve as usual.
 *
 * [franchise] holds the films of a TMDB collection the query names exactly ("james bond" →
 * James Bond Collection). They skip the word filter and share band 0 with exact title matches,
 * which is then ordered by vote count, so Skyfall leads a little-known film titled "James Bond".
 */
internal fun mergeSearchResults(
    sections: List<HomeCatalogSection>,
    query: String,
    requireAllQueryWords: Boolean = false,
    credits: List<MetaPreview> = emptyList(),
    creditsLeadTitleMatches: Boolean = true,
    franchise: List<MetaPreview> = emptyList(),
): List<MetaPreview> {
    val groups = mutableListOf<SearchGroup>()
    val groupByKey = mutableMapOf<String, SearchGroup>()
    var order = 0

    fun add(item: MetaPreview, providerRank: Int?, creditRank: Int? = null, isFranchise: Boolean = false) {
        val keys = searchDedupKeys(item)
        val matching = keys.mapNotNull(groupByKey::get).distinct()
        val group = if (matching.isEmpty()) {
            SearchGroup(order, item).also(groups::add)
        } else {
            val target = matching.minBy { it.order }
            target.items += item
            matching.filter { it !== target }.forEach { other ->
                target.items += other.items
                target.keys += other.keys
                target.creditItems += other.creditItems
                target.franchiseItems += other.franchiseItems
                target.creditRank = minOf(target.creditRank, other.creditRank)
                target.providerRank = minOf(target.providerRank, other.providerRank)
                groups.remove(other)
            }
            target
        }
        when {
            isFranchise -> group.franchiseItems += item
            providerRank == null -> group.creditItems += item
            else -> group.providerRank = minOf(group.providerRank, providerRank)
        }
        if (creditRank != null) group.creditRank = minOf(group.creditRank, creditRank)
        group.keys += keys
        group.keys.forEach { groupByKey[it] = group }
        order++
    }

    sections.forEach { section -> section.items.forEachIndexed { index, item -> add(item, index) } }
    credits.forEachIndexed { index, item -> add(item, providerRank = null, creditRank = index) }
    franchise.forEach { item -> add(item, providerRank = null, isFranchise = true) }

    val ranked = groups
        .map { group ->
            val addonItems = group.items.filter { item ->
                group.creditItems.none { it === item } && group.franchiseItems.none { it === item }
            }
            val best = addonItems.ifEmpty { group.items }.sortedWith(richerMetadata).first()
            val merged = best.copy(
                popularity = best.popularity ?: group.items.mapNotNull { it.popularity }.maxOrNull(),
                voteCount = best.voteCount ?: group.items.mapNotNull { it.voteCount }.maxOrNull(),
                poster = best.poster ?: group.items.firstNotNullOfOrNull { it.poster },
                banner = best.banner ?: group.items.firstNotNullOfOrNull { it.banner },
                description = best.description ?: group.items.firstNotNullOfOrNull { it.description },
            )
            RankedSearchResult(
                item = merged,
                tier = group.items.minOf { searchRelevanceTier(it.name, query) },
                isCredit = group.creditItems.isNotEmpty(),
                isFranchise = group.franchiseItems.isNotEmpty(),
                providerRank = group.providerRank,
                creditRank = group.creditRank,
                order = group.order,
                creditsLeadTitleMatches = creditsLeadTitleMatches,
            )
        }
    // Partial matches ("Shadow Star Narutaru" for "star wars") are only a fallback for typos: once any
    // title matches every query word, titles sharing just some of the words are dropped.
    val keepPartialMatches = !requireAllQueryWords &&
        ranked.none { !it.isCredit && !it.isFranchise && it.tier < SearchRelevanceTier.AnyWord }
    val minimumDroppedTier = if (keepPartialMatches) SearchRelevanceTier.Other else SearchRelevanceTier.AnyWord
    return ranked
        .filter { it.isCredit || it.isFranchise || it.tier < minimumDroppedTier }
        .sortedWith(if (franchise.isEmpty()) rankedSearchOrder else franchiseLedSearchOrder)
        .map { it.item }
}

private const val MaxSearchPeople = 12
private val searchPeopleDepartments = setOf("acting", "directing")

/**
 * Keeps actors and directors whose name contains every query word (accent-insensitive), ordered by
 * match tier, then TMDB popularity (currently trending people first, by product choice).
 *
 * People without a photo or any known-for title are same-name stubs whose person page is empty,
 * so they are dropped.
 */
internal fun filterSearchPeople(people: List<SearchPerson>, query: String): List<SearchPerson> =
    people
        .filter { it.department?.lowercase() in searchPeopleDepartments }
        .filter { !it.profilePhoto.isNullOrBlank() && it.knownForVotes > 0 }
        .mapNotNull { person ->
            val tier = listOfNotNull(person.name, person.originalName)
                .minOf { searchRelevanceTier(it, query) }
            if (tier >= SearchRelevanceTier.AnyWord) null else person to tier
        }
        .distinctBy { (person, _) -> person.tmdbId }
        .sortedWith(
            compareBy<Pair<SearchPerson, SearchRelevanceTier>> { it.second }
                .thenByDescending { it.first.popularity ?: -1.0 },
        )
        .map { it.first }
        .take(MaxSearchPeople)

private const val MaxSearchFilmography = 50
private val excludedFilmographyGenres = setOf(10763, 10764, 10767) // TMDB TV News, Reality, Talk
private const val DocumentaryGenre = 99
/** TV roles with fewer episodes than this are guest spots, not what the person is known for. */
private const val MinSeriesEpisodes = 3
private val selfCharacterPattern = Regex("^(self|himself|herself|themselves|themself)\\b", RegexOption.IGNORE_CASE)

/**
 * The featured person's titles for the merged Search grid: directors keep only titles they
 * directed; actors drop talk/news/reality shows, documentaries, roles credited only as themselves
 * and TV guest spots. Ordered by vote count (how widely known a title is), then popularity —
 * TMDB popularity alone tracks current trends, so guest spots on trending shows outranked classics.
 */
internal fun filterSearchFilmography(detail: PersonDetail, isDirector: Boolean): List<MetaPreview> {
    val selfOnlyKeys = detail.actingCredits
        .filter { credit -> credit.characters.isNotEmpty() && credit.characters.all { selfCharacterPattern.containsMatchIn(it.trim()) } }
        .mapTo(mutableSetOf()) { it.media.stableKey() }
    val guestSpotKeys = detail.actingCredits
        .filter { credit -> credit.media.type == "series" && (credit.episodeCount ?: Int.MAX_VALUE) < MinSeriesEpisodes }
        .mapTo(mutableSetOf()) { it.media.stableKey() }
    val directed = detail.directedKeys.takeIf { isDirector && it.isNotEmpty() }
    return (detail.movieCredits + detail.tvCredits)
        .asSequence()
        .filter { !it.poster.isNullOrBlank() }
        .filter { directed == null || it.stableKey() in directed }
        .filter { it.stableKey() !in selfOnlyKeys }
        .filter { directed != null || it.stableKey() !in guestSpotKeys }
        .filter { credit ->
            val genres = detail.creditGenreIds[credit.stableKey()].orEmpty()
            genres.none(excludedFilmographyGenres::contains) && (directed != null || DocumentaryGenre !in genres)
        }
        .distinctBy { it.stableKey() }
        .sortedWith(compareByDescending<MetaPreview> { it.voteCount ?: -1 }.thenByDescending { it.popularity ?: -1.0 })
        .take(MaxSearchFilmography)
        .toList()
}

/**
 * Results already on screen keep their order; titles that only late providers returned (not a
 * duplicate of a shown card) are appended at the end.
 */
internal fun appendNewSearchResults(shown: List<MetaPreview>, final: List<MetaPreview>): List<MetaPreview> {
    val shownKeys = shown.flatMapTo(mutableSetOf()) { searchDedupKeys(it) }
    return shown + final.filter { item -> searchDedupKeys(item).none(shownKeys::contains) }
}

internal fun publishLateSearchResults(
    query: String,
    shown: List<MetaPreview>,
    ranked: List<MetaPreview>,
): List<MetaPreview> {
    val combined = appendNewSearchResults(shown, ranked)
    fun isExact(item: MetaPreview) = searchRelevanceTier(item.name, query) == SearchRelevanceTier.Exact
    val leader = ranked.firstOrNull()?.takeIf(::isExact) ?: return combined
    val leaderKeys = searchDedupKeys(leader)
    val previousIndex = combined.indexOfFirst { searchDedupKeys(it).any(leaderKeys::contains) }
    if (previousIndex <= 0) return combined
    return listOf(combined[previousIndex]) + combined.filterIndexed { index, _ -> index != previousIndex }
}

/** Final state of a search once every provider finished (or partial state while loading). */
internal fun resolveSearchUiState(
    query: String,
    sections: List<HomeCatalogSection>,
    people: List<SearchPerson>,
    filmography: List<MetaPreview> = emptyList(),
    franchise: List<MetaPreview> = emptyList(),
    completedProviders: Int,
    failedProviders: Int,
    firstFailure: String?,
    isFinished: Boolean,
    hasPendingAddonManifests: Boolean,
): SearchUiState {
    // filterSearchPeople only keeps people matching every query word, so any person means a name query.
    val results = mergeSearchResults(
        sections = sections,
        query = query,
        requireAllQueryWords = people.isNotEmpty(),
        credits = filmography,
        // Only a full-name query ("sean connery") is clearly about the person; "vision" matching
        // Vision Wei must not push titles like VisionQuest below his whole filmography.
        creditsLeadTitleMatches = people.firstOrNull()?.let { person ->
            listOfNotNull(person.name, person.originalName).any { searchRelevanceTier(it, query) == SearchRelevanceTier.Exact }
        } ?: false,
        franchise = franchise,
    )
    if (!isFinished) {
        return SearchUiState(isLoading = true, results = results, people = people)
    }
    val hasContent = results.isNotEmpty() || people.isNotEmpty()
    val allFailed = completedProviders > 0 && failedProviders == completedProviders
    return SearchUiState(
        isLoading = !hasContent && hasPendingAddonManifests,
        results = results,
        people = people,
        emptyStateReason = when {
            hasContent -> null
            hasPendingAddonManifests -> null
            allFailed -> SearchEmptyStateReason.RequestFailed
            else -> SearchEmptyStateReason.NoResults
        },
        errorMessage = if (allFailed && !hasContent) firstFailure else null,
    )
}

private val searchImdbIdPattern = Regex("^tt\\d+$")

/**
 * Keeps only results something can play, mirroring the Details play button: a title is playable
 * when a source accepts its own id, or its IMDb id (carried in the search payload, or resolved
 * from a `tmdb:` id the way Details does). Unresolved `tmdb:`, `tvdb:` and `mal:` titles would open
 * on "Playback unavailable", so they are dropped. Order is preserved.
 */
internal suspend fun filterPlayableSearchResults(
    results: List<MetaPreview>,
    canPlay: (type: String, id: String) -> Boolean,
    resolveTmdbImdbId: suspend (tmdbId: Int, type: String) -> String?,
): List<MetaPreview> = coroutineScope {
    results.map { item ->
        async {
            val playable = canPlay(item.type, item.id) || run {
                val imdb = item.knownImdbId() ?: item.tmdbNumericId()?.let { resolveTmdbImdbId(it, item.type) }
                imdb != null && canPlay(item.type, imdb)
            }
            item.takeIf { playable }
        }
    }.awaitAll().filterNotNull()
}

/** Replaces the results after filtering; an emptied search without people becomes "No results". */
internal fun SearchUiState.withFilteredResults(filtered: List<MetaPreview>): SearchUiState {
    if (filtered.size == results.size) return this
    val emptied = filtered.isEmpty() && people.isEmpty()
    return copy(
        results = filtered,
        emptyStateReason = if (emptied && emptyStateReason == null && !isLoading) SearchEmptyStateReason.NoResults else emptyStateReason,
    )
}

private fun MetaPreview.knownImdbId(): String? =
    id.takeIf(searchImdbIdPattern::matches) ?: alternateIds.firstOrNull(searchImdbIdPattern::matches)

private fun MetaPreview.tmdbNumericId(): Int? =
    id.takeIf { it.startsWith("tmdb:", ignoreCase = true) }?.substringAfter(':')?.substringBefore(':')?.toIntOrNull()

/**
 * The TMDB id whose IMDb mapping [filterPlayableSearchResults] would need for this item, or null
 * when the item is decided without a lookup. Lets Search start those lookups as results arrive.
 */
internal fun searchResultTmdbLookupId(item: MetaPreview, canPlay: (type: String, id: String) -> Boolean): Int? =
    if (canPlay(item.type, item.id) || item.knownImdbId() != null) null else item.tmdbNumericId()
