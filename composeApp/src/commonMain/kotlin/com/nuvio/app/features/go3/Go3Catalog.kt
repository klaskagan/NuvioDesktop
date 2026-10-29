package com.nuvio.app.features.go3

import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.home.PosterShape
import com.nuvio.app.features.search.DiscoverCatalogOption
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class Go3Event(val id: String, val title: String, val url: String, val artwork: String?, val state: String, val schedule: String) {
    fun preview() = MetaPreview(id=id, type="sport", name=title, poster=artwork, landscapePoster=artwork,
        posterShape=PosterShape.Landscape, description=schedule, status=state)
}
object Go3Catalog {
    var loader: (suspend (String) -> List<Go3Event>)? = null
    var accountAction: ((String) -> Boolean)? = null
    private val mutex = Mutex()
    private val snapshots = MutableStateFlow<Map<String, Pair<Long, List<Go3Event>>>>(emptyMap())
    private val cache get() = snapshots.value
    private val known = MutableStateFlow<Map<String, Go3Event>>(emptyMap())
    private val _status = MutableStateFlow("Sign in through the official Go3 window. Your session stays on this Mac.")
    val status = _status.asStateFlow()
    fun action(action: String) { _status.value = if(accountAction?.invoke(action)==true) {
        if(action=="signout") "Local Go3 session reset requested." else "Go3 window opened. Complete sign-in there if required."
    } else "Go3 player unavailable. Check the desktop runtime configuration." }
    fun sources(): List<DiscoverCatalogOption> = if(loader==null) emptyList() else listOf("live" to "Go3 Live Events", "epl" to "Go3 Premier League").map { (id,name) ->
        DiscoverCatalogOption(key="go3:$id", addonName="Go3", manifestUrl="", type="sport", catalogId=id, catalogName=name,
            genreOptions=if(id=="epl")listOf("Live", "Upcoming", "Replay") else listOf("Live", "Upcoming"))
    }
    suspend fun page(kind: String, filter: String?, refresh: Boolean): List<MetaPreview> = mutex.withLock {
        val now=kotlin.time.Clock.System.now().toEpochMilliseconds()
        val cached=cache[kind]
        var stale=false
        val rows=if(!refresh && cached!=null && now-cached.first<120_000) cached.second else {
            val loaded=try { requireNotNull(loader) { "Go3 is available on macOS desktop only." }(kind) }
            catch(error: Exception) {
                if(error is kotlinx.coroutines.CancellationException || cached==null) throw error
                stale=true
                _status.value="Go3 refresh failed. Showing previously loaded listings; availability may have changed."
                cached.second
            }
            if(!stale) snapshots.value = snapshots.value + (kind to (now to loaded))
            known.value = known.value + loaded.associateBy { it.id }
            loaded
        }
        if(!stale) _status.value="Listings updated."
        rows.filter { filter==null || it.state.equals(filter,true) }.sortedBy { when(it.state){"live"->0;"upcoming"->1;"replay"->2;else->3} }.map { it.preview().let { preview -> if(stale)preview.copy(status="stale") else preview } }
    }
    fun isStale(kind: String): Boolean = cache[kind]?.let {
        kotlin.time.Clock.System.now().toEpochMilliseconds()-it.first>=120_000
    } ?: true
    fun filters(kind: String): List<String> {
        val states=cache[kind]?.second?.map {it.state}?.toSet().orEmpty()
        return listOf("Live","Upcoming","Replay").filter {it.lowercase() in states}
    }
    fun cachedPage(kind: String, filter: String?): List<MetaPreview>? = cache[kind]?.second?.let {rows ->
        rows.filter {filter==null || it.state.equals(filter,true)}.sortedBy {when(it.state){"live"->0;"upcoming"->1;else->2}}.map {if(isStale(kind))it.preview().copy(status="stale") else it.preview()}
    }
    fun find(id: String): Go3Event? = known.value[id]
}
