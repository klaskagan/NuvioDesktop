package com.nuvio.app.features.go3

import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.home.PosterShape
import com.nuvio.app.features.search.DiscoverCatalogOption
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal enum class Go3CatalogSource(val key: String, val title: String, val categoryId: String? = null) {
    LIVE("live", "Go3 Live Events"),
    EPL("epl", "Go3 Premier League", "6886315"),
    UCL("ucl", "Go3 Champions League", "6886205"),
    UEL("uel", "Go3 Europa League", "6886318"),
    UECL("uecl", "Go3 Conference League", "6886320"),
    LA_LIGA("laliga", "Go3 La Liga", "8272112"),
    BUNDESLIGA("bundesliga", "Go3 Bundesliga", "7934060"),
    LIGUE_1("ligue1", "Go3 Ligue 1", "7498236"),
    SERIE_A("seriea", "Go3 Serie A", "7495922"),
    FA_CUP("facup", "Go3 FA Cup", "6886370"),
}

private val go3CompetitionPrefix = Regex(
    """^(?:UEFA (?:Champions|Europa(?: Conference)?|Conference) League|UCL|(?:English )?Premier League|La\s*Liga|Bundesliga|Bundeslyga|Ligue 1|Serie A|Italijos lyga: Serija A|(?:Emirates )?FA (?:Cup|Taurė))\s*(?:[.:]\s*|[-–—]\s+)""",
    RegexOption.IGNORE_CASE,
)

data class Go3Event(val id: String, val title: String, val url: String, val artwork: String?, val state: String, val schedule: String) {
    val playable get() = state == "live" || state == "replay"

    fun preview() = MetaPreview(id=id, type="sport", name=title.replace(go3CompetitionPrefix, "").ifBlank { title }, poster=artwork, landscapePoster=artwork,
        posterShape=PosterShape.Landscape, description=schedule, status=state)
}
object Go3Catalog {
    var loader: (suspend (String) -> List<Go3Event>)? = null
    var programmeLoader: (suspend (String) -> Go3Event)? = null
    var accountAction: ((String) -> Boolean)? = null
    private val mutex = Mutex()
    private data class Snapshot(val loadedAt: Long, val events: List<Go3Event>, val stale: Boolean = false)
    private val snapshots = MutableStateFlow<Map<String, Snapshot>>(emptyMap())
    private val cache get() = snapshots.value
    private val known = MutableStateFlow<Map<String, Go3Event>>(emptyMap())
    private val _status = MutableStateFlow("Sign in through the official Go3 window. Your session stays on this Mac.")
    val status = _status.asStateFlow()
    fun action(action: String) { _status.value = if(accountAction?.invoke(action)==true) {
        if(action=="signout") "Local Go3 session reset requested." else "Go3 window opened. Complete sign-in there if required."
    } else "Go3 player unavailable. Check the desktop runtime configuration." }
    fun sources(): List<DiscoverCatalogOption> = if (loader == null) emptyList() else
        Go3CatalogSource.entries.map { source ->
            DiscoverCatalogOption(
                key = "go3:${source.key}", addonName = "Go3", manifestUrl = "",
                type = "sport", catalogId = source.key, catalogName = source.title,
                genreOptions = listOf("Live", "Upcoming", "Replay"),
            )
        }
    suspend fun page(kind: String, filter: String?, refresh: Boolean): List<MetaPreview> = mutex.withLock {
        val now=kotlin.time.Clock.System.now().toEpochMilliseconds()
        val cached=cache[kind]
        var stale=false
        val rows=if(!refresh && cached!=null && !cached.stale && now-cached.loadedAt<120_000) cached.events else {
            val loaded=try { requireNotNull(loader) { "Go3 is available on macOS desktop only." }(kind) }
            catch(error: Exception) {
                if(error is kotlinx.coroutines.CancellationException || cached==null) throw error
                stale=true
                _status.value="Go3 refresh failed. Showing previously loaded listings; availability may have changed."
                snapshots.value = cache + (kind to cached.copy(stale = true))
                cached.events
            }
            if(!stale) snapshots.value = snapshots.value + (kind to Snapshot(now, loaded))
            known.value = known.value + loaded.associateBy { it.id }
            loaded
        }
        if(!stale) _status.value="Listings updated."
        rows.filter { filter==null || it.state.equals(filter,true) }.sortedBy { when(it.state){"live"->0;"upcoming"->1;"replay"->2;else->3} }.map { it.preview().let { preview -> if(stale)preview.copy(status="stale") else preview } }
    }
    fun isStale(kind: String): Boolean = cache[kind]?.let {
        it.stale || kotlin.time.Clock.System.now().toEpochMilliseconds()-it.loadedAt>=120_000
    } ?: true
    fun filters(kind: String): List<String> {
        val states=cache[kind]?.events?.map {it.state}?.toSet().orEmpty()
        return listOf("Live","Upcoming","Replay").filter {it.lowercase() in states}
    }
    fun cachedPage(kind: String, filter: String?): List<MetaPreview>? = cache[kind]?.events?.let {rows ->
        rows.filter {filter==null || it.state.equals(filter,true)}.sortedBy {when(it.state){"live"->0;"upcoming"->1;else->2}}.map {if(isStale(kind))it.preview().copy(status="stale") else it.preview()}
    }
    fun find(id: String): Go3Event? = known.value[id]
}
