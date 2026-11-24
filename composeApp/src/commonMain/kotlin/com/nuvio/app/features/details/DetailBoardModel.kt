package com.nuvio.app.features.details

/**
 * The tabs under the desktop Details board. Each tab shows one or more of the configurable
 * sections; [Stats] is the series audience cards, which have no section of their own.
 *
 * Adding a tab: add an entry with the sections it owns. Adding a section to a tab: list its key
 * here. Order and visibility still come from Settings → Detail Page.
 */
internal enum class DetailBoardTab(val sections: List<MetaScreenSectionKey>) {
    Episodes(listOf(MetaScreenSectionKey.EPISODES)),
    Stats(emptyList()),
    Cast(listOf(MetaScreenSectionKey.CAST, MetaScreenSectionKey.PRODUCTION)),
    Reviews(listOf(MetaScreenSectionKey.COMMENTS)),
    Trailers(listOf(MetaScreenSectionKey.TRAILERS)),
    Gallery(listOf(MetaScreenSectionKey.GALLERY)),
    More(listOf(MetaScreenSectionKey.COLLECTION, MetaScreenSectionKey.MORE_LIKE_THIS)),
    Details(listOf(MetaScreenSectionKey.DETAILS)),
}

/**
 * The tabs to show, in the order their first section has in [enabledSections] (the user's
 * configured order). A tab appears when one of its sections is enabled and has content. Your
 * stats follows Episodes and is hidden with it, as the audience cards always were.
 */
internal fun detailBoardTabs(
    enabledSections: List<MetaScreenSectionKey>,
    hasContent: (MetaScreenSectionKey) -> Boolean,
    hasStats: Boolean,
): List<DetailBoardTab> {
    val ordered = DetailBoardTab.entries
        .filter { it != DetailBoardTab.Stats }
        .mapNotNull { tab ->
            val first = enabledSections.indexOfFirst { it in tab.sections && hasContent(it) }
            if (first < 0) null else first to tab
        }
        .sortedBy { it.first }
        .map { it.second }
    val episodes = ordered.indexOf(DetailBoardTab.Episodes)
    if (!hasStats || episodes < 0) return ordered
    val afterEpisodes = episodes + 1
    return ordered.toMutableList().apply { add(afterEpisodes, DetailBoardTab.Stats) }
}

/** The sections [tab] renders, in configured order. */
internal fun detailBoardTabSections(
    tab: DetailBoardTab,
    items: List<MetaScreenSectionItem>,
    hasContent: (MetaScreenSectionKey) -> Boolean,
): List<MetaScreenSectionItem> =
    items.filter { it.enabled && it.key in tab.sections && hasContent(it.key) }

/** Watched and aired totals for the progress tile, with the seasons it has room to list. */
internal data class DetailBoardProgress(
    val watched: Int,
    val aired: Int,
    val seasons: List<SeasonWatchedCount>,
)

/**
 * Totals over every numbered season (specials count only when they are all there is), and up to
 * [maxRows] seasons around [currentSeason] so the season being watched is always listed.
 */
internal fun detailBoardProgress(
    counts: List<SeasonWatchedCount>,
    currentSeason: Int?,
    maxRows: Int = 3,
): DetailBoardProgress? {
    val numbered = counts.filter { it.season > 0 }.ifEmpty { counts }.filter { it.aired > 0 }
    if (numbered.isEmpty()) return null
    val current = numbered.indexOfFirst { it.season == currentSeason }.coerceAtLeast(0)
    val start = (current - maxRows / 2).coerceIn(0, (numbered.size - maxRows).coerceAtLeast(0))
    return DetailBoardProgress(
        watched = numbered.sumOf { it.watched },
        aired = numbered.sumOf { it.aired },
        seasons = numbered.drop(start).take(maxRows),
    )
}

/** Sizes of the board list's visible items, as [detailBoardFillPx] needs them. */
internal data class DetailBoardVisibleItem(val index: Int, val sizePx: Int, val isFill: Boolean)

/**
 * Height of the filler under a tab's sections, so the pinned tabs row can stay pinned when a tab
 * is shorter than the window (otherwise switching to it drops the row back down the page).
 *
 * The filler only grows once every section of the tab is on screen: from the gap under the tabs
 * ([firstContentIndex]) to the filler itself. A longer tab needs none.
 */
internal fun detailBoardFillPx(
    visible: List<DetailBoardVisibleItem>,
    viewportPx: Int,
    tabsPx: Int,
    firstContentIndex: Int,
): Int {
    if (visible.none { it.isFill } || visible.none { it.index == firstContentIndex }) return 0
    val content = visible.filter { it.index >= firstContentIndex && !it.isFill }.sumOf { it.sizePx }
    return (viewportPx - tabsPx - content).coerceAtLeast(0)
}

/** Milliseconds of a film still to play: its runtime less where it was left. Null without a runtime. */
internal fun detailBoardRemainingMs(runtime: String?, resumePositionMs: Long?): Long? {
    val minutes = parseRuntimeMinutes(runtime)?.takeIf { it > 0 } ?: return null
    return (minutes * 60_000L - (resumePositionMs ?: 0L)).coerceAtLeast(0L)
}
