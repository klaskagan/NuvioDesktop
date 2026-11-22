package com.nuvio.app.features.details

import com.nuvio.app.features.watching.application.WatchingState
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.home.stableKey
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

@Serializable
enum class PersonSubview { KnownFor, Movies, Series, Upcoming }

internal enum class PersonCreditSort { Popularity, Latest, HighestRated, LowestRated, NewestYear, OldestYear }
internal enum class PersonReleaseFilter { All, Released, Upcoming }
internal enum class PersonWatchedFilter { All, Watched, Unwatched }

internal data class PersonCreditQuery(
    val sort: PersonCreditSort = PersonCreditSort.Popularity,
    val release: PersonReleaseFilter = PersonReleaseFilter.All,
    val watched: PersonWatchedFilter = PersonWatchedFilter.All,
    val genreId: Int? = null,
) {
    fun withSort(value: PersonCreditSort) =
        copy(sort = value, release = if (value == PersonCreditSort.Latest) PersonReleaseFilter.Released else release)

    fun withRelease(value: PersonReleaseFilter) = copy(
        release = value,
        sort = if (sort == PersonCreditSort.Latest && value != PersonReleaseFilter.Released) {
            PersonCreditSort.NewestYear
        } else sort,
    )

    fun clearFilters() = copy(
        release = if (sort == PersonCreditSort.Latest) PersonReleaseFilter.Released else PersonReleaseFilter.All,
        watched = PersonWatchedFilter.All,
        genreId = null,
    )
}

internal fun MetaPreview.creditDate(): LocalDate? = rawReleaseDate?.let {
    try { LocalDate.parse(it) } catch (_: IllegalArgumentException) { null }
}

private val titleAndId = compareBy<MetaPreview>({ it.name.lowercase() }, { it.stableKey() })

private fun <T : Comparable<T>> creditOrder(descending: Boolean, value: (MetaPreview) -> T?): Comparator<MetaPreview> {
    val order: Comparator<T> = if (descending) reverseOrder() else naturalOrder()
    return compareBy(nullsLast(order), value).then(titleAndId)
}

internal fun filterPersonCredits(
    credits: List<MetaPreview>,
    query: PersonCreditQuery,
    today: LocalDate,
    genreIds: Map<String, Set<Int>> = emptyMap(),
    watchedKeys: Set<String> = emptySet(),
    fullyWatchedSeriesKeys: Set<String> = emptySet(),
): List<MetaPreview> {
    val comparator = when (query.sort) {
        PersonCreditSort.Popularity -> creditOrder(true) { it.popularity }
        PersonCreditSort.Latest -> creditOrder(true) { it.creditDate() }
        PersonCreditSort.HighestRated -> creditOrder(true) { it.sortRating }
        PersonCreditSort.LowestRated -> creditOrder(false) { it.sortRating }
        PersonCreditSort.NewestYear -> creditOrder(true) { it.creditDate()?.year }
        PersonCreditSort.OldestYear -> creditOrder(false) { it.creditDate()?.year }
    }
    return credits.distinctBy { it.stableKey() }.filter { item ->
        val date = item.creditDate()
        val releaseMatches = when {
            query.sort == PersonCreditSort.Latest || query.release == PersonReleaseFilter.Released ->
                date != null && date <= today
            query.release == PersonReleaseFilter.Upcoming -> date != null && date > today
            else -> true
        }
        val watchedMatches = query.watched == PersonWatchedFilter.All ||
            WatchingState.isPosterWatched(watchedKeys, item, fullyWatchedSeriesKeys) ==
            (query.watched == PersonWatchedFilter.Watched)
        releaseMatches && watchedMatches &&
            (query.genreId == null || query.genreId in genreIds[item.stableKey()].orEmpty())
    }.sortedWith(comparator)
}

internal fun upcomingPersonCredits(person: PersonDetail, today: LocalDate): List<MetaPreview> =
    (person.movieCredits + person.tvCredits).distinctBy { it.stableKey() }
        .filter { it.creditDate()?.let { date -> date > today } == true }
        .sortedWith(creditOrder(false) { it.creditDate() })

private val selfCharacterPattern = Regex("^(self|himself|herself|themselves|themself)\\b", RegexOption.IGNORE_CASE)

/** True when every role on the title is the person appearing as themselves (documentaries, award shows). */
internal fun ActingCredit.isSelfOnly(): Boolean =
    characters.isNotEmpty() && characters.all { selfCharacterPattern.containsMatchIn(it.trim()) }

internal fun careerDepartmentOf(tmdbDepartment: String?): CareerDepartment =
    when (tmdbDepartment?.trim()?.lowercase()) {
        "acting", "actors" -> CareerDepartment.Acting
        "directing" -> CareerDepartment.Directing
        "writing" -> CareerDepartment.Writing
        "production" -> CareerDepartment.Production
        "creator" -> CareerDepartment.Creator
        else -> CareerDepartment.Crew
    }

/** Timeline credits per department; a person built without them (older callers) is all acting. */
internal fun PersonDetail.career(): Map<CareerDepartment, List<ActingCredit>> =
    careerCredits.ifEmpty {
        val (self, acting) = actingCredits.partition { it.isSelfOnly() }
        mapOf(CareerDepartment.Acting to acting, CareerDepartment.Self to self)
    }.filterValues { it.isNotEmpty() }

/** Tab order: the department the person is known for, then the others by size, appearances as self last. */
internal fun careerTabs(person: PersonDetail): List<CareerDepartment> {
    val career = person.career()
    val main = person.knownFor?.let(::careerDepartmentOf)
    return career.keys.sortedWith(
        compareBy<CareerDepartment>(
            { it != main },
            { it == CareerDepartment.Self },
            { -career.getValue(it).size },
            { it.ordinal },
        ),
    )
}

/** First and last released year among [credits], for the tab's summary line. */
internal fun careerYearSpan(credits: List<ActingCredit>, today: LocalDate): Pair<Int, Int>? {
    val years = credits.mapNotNull { it.media.creditDate()?.takeIf { date -> date <= today }?.year }
    return if (years.isEmpty()) null else years.min() to years.max()
}

internal sealed interface ActingPeriod {
    data object Upcoming : ActingPeriod
    data class Year(val value: Int) : ActingPeriod
    data object Unknown : ActingPeriod
}

internal fun personActingTimeline(
    credits: List<ActingCredit>,
    today: LocalDate,
): Map<ActingPeriod, List<ActingCredit>> = credits
    .sortedWith(compareBy(creditOrder(true) { it.creditDate() }) { it.media })
    .groupBy {
        val date = it.media.creditDate()
        when {
            date == null -> ActingPeriod.Unknown
            date > today -> ActingPeriod.Upcoming
            else -> ActingPeriod.Year(date.year)
        }
    }
