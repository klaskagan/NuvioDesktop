package com.nuvio.app.features.search

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The rows of a tag page (a streaming service or any other discovery tag), in their default order.
 * [ServiceCollections] is a slot: it expands to the service's own rows (Marvel, Pixar…) and takes
 * no space on a page that has none, so one order works for every service.
 */
internal enum class StreamingRowId {
    Top10Shows, Top10Movies,
    OriginalsShows, OriginalsMovies,
    ServiceCollections,
    PopularShows, PopularMovies,
    TopRatedShows, TopRatedMovies,
    NewShows, NewMovies,
}

internal fun SearchShelfKey.rowId(): StreamingRowId = when (sort) {
    SearchShelfSort.Originals -> if (television) StreamingRowId.OriginalsShows else StreamingRowId.OriginalsMovies
    SearchShelfSort.Popular -> if (television) StreamingRowId.PopularShows else StreamingRowId.PopularMovies
    SearchShelfSort.TopRated -> if (television) StreamingRowId.TopRatedShows else StreamingRowId.TopRatedMovies
    SearchShelfSort.New -> if (television) StreamingRowId.NewShows else StreamingRowId.NewMovies
}

/** A combined movies-and-shows chart sits where the shows chart would. */
internal fun SearchTopTenCatalog.rowId(): StreamingRowId =
    if (television == false) StreamingRowId.Top10Movies else StreamingRowId.Top10Shows

/** The user's row order and hidden rows, shared by every tag page. */
internal data class StreamingRowLayout(
    val order: List<StreamingRowId> = StreamingRowId.entries,
    val hidden: Set<StreamingRowId> = emptySet(),
) {
    val visibleOrder: List<StreamingRowId> get() = order.filterNot(hidden::contains)

    fun moved(from: Int, to: Int): StreamingRowLayout {
        if (from == to || from !in order.indices || to !in order.indices) return this
        return copy(order = order.toMutableList().apply { add(to, removeAt(from)) })
    }

    fun withHidden(id: StreamingRowId, isHidden: Boolean): StreamingRowLayout =
        copy(hidden = if (isHidden) hidden + id else hidden - id)

    fun encode(): String = json.encodeToString(Stored.serializer(), Stored(order.map { it.name }, hidden.map { it.name }))

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        @Serializable
        private data class Stored(val order: List<String> = emptyList(), val hidden: List<String> = emptyList())

        /**
         * Ids this build doesn't know are dropped; rows added since the layout was saved are
         * inserted after the row that precedes them by default, so they never go missing.
         */
        fun decode(payload: String?): StreamingRowLayout {
            val stored = payload?.takeIf { it.isNotBlank() }
                ?.let { runCatching { json.decodeFromString(Stored.serializer(), it) }.getOrNull() }
                ?: return StreamingRowLayout()
            val byName = StreamingRowId.entries.associateBy { it.name }
            val order = stored.order.mapNotNull(byName::get).distinct().toMutableList()
            StreamingRowId.entries.forEachIndexed { index, id ->
                if (id in order) return@forEachIndexed
                val previous = StreamingRowId.entries.take(index).lastOrNull { it in order }
                order.add(if (previous == null) 0 else order.indexOf(previous) + 1, id)
            }
            return StreamingRowLayout(order, stored.hidden.mapNotNull(byName::get).toSet())
        }
    }
}

/** Profile-scoped, device-local row layout. */
internal object StreamingRowLayoutRepository {
    private val _layout = MutableStateFlow(StreamingRowLayout())
    val layout: StateFlow<StreamingRowLayout> = _layout.asStateFlow()
    private var loaded = false

    fun ensureLoaded() {
        if (loaded) return
        loaded = true
        _layout.value = StreamingRowLayout.decode(SearchPreferencesStorage.loadTagRowLayout())
    }

    fun onProfileChanged() {
        loaded = false
        ensureLoaded()
    }

    fun moveByIndex(from: Int, to: Int) = update { it.moved(from, to) }

    fun setHidden(id: StreamingRowId, hidden: Boolean) = update { it.withHidden(id, hidden) }

    fun reset() = update { StreamingRowLayout() }

    private fun update(change: (StreamingRowLayout) -> StreamingRowLayout) {
        ensureLoaded()
        val next = change(_layout.value)
        if (next == _layout.value) return
        _layout.value = next
        SearchPreferencesStorage.saveTagRowLayout(next.encode())
    }
}
