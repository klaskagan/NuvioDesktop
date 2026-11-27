package com.nuvio.app.features.home.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.nuvio.app.core.ui.NuvioAsyncImage
import com.nuvio.app.core.format.formatLocalTime
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.core.ui.NuvioPosterCard
import com.nuvio.app.core.ui.NuvioPosterShape
import com.nuvio.app.core.ui.NuvioShelfSection
import com.nuvio.app.core.ui.NuvioViewAllPillSize
import com.nuvio.app.core.ui.RedesignColors
import com.nuvio.app.core.ui.RedesignIconButton
import com.nuvio.app.core.ui.RedesignPillButton
import com.nuvio.app.core.ui.displayRatingText
import com.nuvio.app.core.ui.posterCardClickable
import com.nuvio.app.core.ui.nuvioDesktopDragScroll
import com.nuvio.app.core.ui.redesignText
import com.nuvio.app.features.calendar.CalendarPlatform
import com.nuvio.app.features.collection.CollectionFolder
import com.nuvio.app.features.home.HomeCatalogSection
import com.nuvio.app.features.home.HomeRowStyle
import com.nuvio.app.features.home.HomeRowStyleNeed
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.home.stableKey
import com.nuvio.app.features.home.upcomingDate
import com.nuvio.app.features.home.nextEpisodeLine
import com.nuvio.app.features.watching.application.WatchingState
import com.nuvio.app.features.watchprogress.CurrentDateProvider
import com.nuvio.app.isDesktop
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource

/**
 * What each Home row can fill (Row style picker), published by the row as it draws so the picker
 * opened from a poster, the ⋯ menu or Settings offers only the styles that row can show.
 */
internal object HomeRowSupplies {
    private val supplies = mutableStateMapOf<String, Set<HomeRowStyleNeed>>()

    fun of(key: String): Set<HomeRowStyleNeed>? = supplies[key]

    fun publish(key: String, value: Set<HomeRowStyleNeed>) {
        if (supplies[key] != value) supplies[key] = value
    }
}

/** The Row description being typed in the picker, shown live in the row before it is saved. */
internal object RowDescriptionDrafts {
    private val drafts = mutableStateMapOf<String, String>()

    fun of(key: String): String? = drafts[key]

    fun update(key: String, text: String) {
        drafts[key] = text
    }

    fun clear(key: String) {
        drafts.remove(key)
    }
}

@Composable
internal fun PublishRowSupplies(key: String?, supplies: Set<HomeRowStyleNeed>) {
    if (key == null) return
    SideEffect { HomeRowSupplies.publish(key, supplies) }
}

/** The row's description: the draft being typed, else the saved line. */
@Composable
internal fun rowDescription(key: String?, saved: String?): String? =
    (key?.let { RowDescriptionDrafts.of(it) } ?: saved)?.takeIf { it.isNotBlank() }

/** How a catalog row's titles open and are marked, shared by every styled layout. */
internal class StyledRowActions(
    val watchedKeys: Set<String>,
    val fullyWatchedSeriesKeys: Set<String>,
    val onPosterClick: ((MetaPreview) -> Unit)?,
    val onPosterLongClick: ((MetaPreview) -> Unit)?,
) {
    fun watched(item: MetaPreview) = WatchingState.isPosterWatched(watchedKeys, item, fullyWatchedSeriesKeys)
    fun open(item: MetaPreview): (() -> Unit)? = onPosterClick?.let { { it(item) } }
    fun actions(item: MetaPreview): (() -> Unit)? = onPosterLongClick?.let { { it(item) } }
}

// ---------------------------------------------------------------------------------------------
// Catalog rows
// ---------------------------------------------------------------------------------------------

/**
 * A catalog row in one of the styles with its own layout ([HomeRowStyle.hasOwnLayout]); the
 * Home design frames "Row styles · Compact list" … "Row styles · Collage banner".
 */
@Composable
internal fun HomeStyledCatalogRow(
    section: HomeCatalogSection,
    entries: List<MetaPreview>,
    rowStyle: HomeRowStyle,
    eyebrow: String?,
    description: String?,
    sectionPadding: Dp,
    onViewAllClick: (() -> Unit)?,
    actions: StyledRowActions,
    modifier: Modifier = Modifier,
) {
    val header = rowHeaderSpec(section.title, eyebrow, sectionPadding, onViewAllClick)
    val tile: @Composable (MetaPreview, TileSpec) -> Unit = { item, spec -> TitleTile(item, spec, actions) }
    Box(modifier.fillMaxWidth().testTag("home_row_styled_${rowStyle.id}")) {
        when (rowStyle) {
            HomeRowStyle.CompactList -> CompactListLayout(
                header, entries, MetaPreview::stableKey, tile,
                title = { it.name }, meta = { titleMetaLine(it, withGenre = false) },
                onRowClick = { actions.open(it) }, onRowLongClick = { actions.actions(it) },
            )
            HomeRowStyle.Mosaic -> MosaicLayout(header, entries, MetaPreview::stableKey, rank = { it.catalogRank }, tile = tile)
            HomeRowStyle.LogoWall -> LogoWallLayout(header, entries, MetaPreview::stableKey, tile)
            HomeRowStyle.Spotlight -> SpotlightLayout(header, entries, actions)
            HomeRowStyle.CalendarStrip -> CalendarStripLayout(header, entries, actions)
            HomeRowStyle.InfoCards -> InfoCardsLayout(header, entries, actions)
            HomeRowStyle.EditorialBand -> EditorialBandLayout(
                header = header,
                eyebrow = listOfNotNull(stringResource(Res.string.home_row_curated), eyebrow).joinToString(" · "),
                description = description,
                backdrop = entries.firstNotNullOfOrNull { it.banner ?: it.landscapePoster } ?: entries.firstOrNull()?.poster,
                entries = entries,
                key = MetaPreview::stableKey,
                tile = tile,
            )
            HomeRowStyle.CollageBanner -> CollageBannerLayout(header, section, entries)
            else -> Unit
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Collection rows (folder tiles)
// ---------------------------------------------------------------------------------------------

/** A collection row in Compact list, Mosaic, Logo wall or Editorial band, with folder tiles. */
@Composable
internal fun HomeStyledCollectionRow(
    title: String,
    folders: List<CollectionFolder>,
    rowStyle: HomeRowStyle,
    description: String?,
    sectionPadding: Dp,
    keyOf: (CollectionFolder) -> Any,
    onFolderClick: ((CollectionFolder) -> Unit)?,
    onFolderLongClick: ((CollectionFolder) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val header = rowHeaderSpec(title, null, sectionPadding, null)
    val tile: @Composable (CollectionFolder, TileSpec) -> Unit = { folder, spec ->
        FolderTile(folder, spec, onFolderClick?.let { { it(folder) } }, onFolderLongClick?.let { { it(folder) } })
    }
    Box(modifier.fillMaxWidth().testTag("home_row_styled_${rowStyle.id}")) {
        when (rowStyle) {
            HomeRowStyle.CompactList -> CompactListLayout(
                header, folders, keyOf, tile, title = { it.title }, meta = { null },
                onRowClick = { folder -> onFolderClick?.let { { it(folder) } } },
                onRowLongClick = { folder -> onFolderLongClick?.let { { it(folder) } } },
            )
            HomeRowStyle.Mosaic -> MosaicLayout(header, folders, keyOf, rank = { null }, tile = tile)
            HomeRowStyle.LogoWall -> LogoWallLayout(header, folders, keyOf, tile)
            HomeRowStyle.EditorialBand -> EditorialBandLayout(
                header = header,
                eyebrow = stringResource(Res.string.home_row_curated),
                description = description,
                backdrop = folders.firstNotNullOfOrNull { it.heroBackdropUrl?.takeIf(String::isNotBlank) ?: it.coverImageUrl?.takeIf(String::isNotBlank) },
                entries = folders,
                key = keyOf,
                tile = tile,
            )
            else -> Unit
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Shared pieces
// ---------------------------------------------------------------------------------------------

/** The editorial header the strip styles keep: eyebrow, title, ⋯ and ›. */
internal class RowHeaderSpec(
    val title: String,
    val eyebrow: String?,
    val sectionPadding: Dp,
    val onViewAllClick: (() -> Unit)?,
    val header: HomeRowHeader?,
)

@Composable
private fun rowHeaderSpec(title: String, eyebrow: String?, sectionPadding: Dp, onViewAllClick: (() -> Unit)?) =
    RowHeaderSpec(title, eyebrow, sectionPadding, onViewAllClick, LocalHomeRowHeader.current)

@Composable
private fun <U> StyledShelf(
    spec: RowHeaderSpec,
    units: List<U>,
    key: (U) -> Any,
    spacing: Dp,
    rowModifier: Modifier = Modifier,
    rowStartInset: Dp = 0.dp,
    content: @Composable (U) -> Unit,
) {
    val h = spec.header
    NuvioShelfSection(
        title = spec.title,
        entries = units,
        rowModifier = rowModifier,
        headerHorizontalPadding = spec.sectionPadding,
        rowContentPadding = PaddingValues(start = spec.sectionPadding + rowStartInset, end = spec.sectionPadding),
        itemSpacing = spacing,
        onViewAllClick = spec.onViewAllClick,
        onTitleClick = spec.onViewAllClick?.takeIf { isDesktop },
        viewAllPillSize = NuvioViewAllPillSize.Compact,
        eyebrow = spec.eyebrow,
        key = key,
        headerModifier = h?.modifier ?: Modifier,
        rowOverlay = h?.rowOverlay,
        headerLeading = h?.leading,
        headerActions = h?.actions,
        showViewAll = h?.showViewAll ?: true,
        itemContent = content,
    )
}

/** A tile's size and look; landscape tiles use the title's backdrop, poster tiles its poster. */
internal data class TileSpec(
    val width: Dp,
    val height: Dp,
    val landscape: Boolean,
    val corner: Dp = 12.dp,
    /** Logo wall: the clear logo centred on the darkened backdrop. */
    val logoWall: Boolean = false,
)

/** Landscape art for a title: supplied backdrop, else looked up, else null while looking. */
@Composable
private fun rememberTitleLandscapeArt(item: MetaPreview): String? {
    val supplied = (item.banner ?: item.landscapePoster)?.takeIf { it.isNotBlank() }
    val lookup = rememberLandscapeMediaArtwork(item.id, item.type, enabled = supplied == null && isTitleArtworkMedia(item.type))
    return supplied ?: lookup.artwork?.let { it.cleanBackdrop ?: it.backdrop } ?: item.poster.takeIf { !lookup.isLoading }
}

@Composable
private fun TitleTile(item: MetaPreview, spec: TileSpec, actions: StyledRowActions) {
    val watched = actions.watched(item)
    val art = if (spec.landscape) rememberTitleLandscapeArt(item) else item.poster
    val logoInteraction = remember { MutableInteractionSource() }
    val hovered by logoInteraction.collectIsHoveredAsState()
    HomePosterHoverPreview(
        item = item,
        isWatched = watched,
        onClick = actions.open(item),
        onLongClick = actions.actions(item),
        allowInlineExpand = false,
    ) { hoverModifier, hoverOverlay ->
        NuvioPosterCard(
            title = item.name,
            imageUrl = art,
            fallbackImageUrl = if (spec.landscape) item.poster else item.rawPosterUrl,
            modifier = Modifier.width(spec.width).hoverable(logoInteraction).then(hoverModifier),
            fillWidth = true,
            shape = if (spec.landscape) NuvioPosterShape.Landscape else NuvioPosterShape.Poster,
            artworkHeight = spec.height,
            artworkCornerRadius = spec.corner,
            showTitleBelow = false,
            isWatched = watched,
            onClick = actions.open(item),
            onLongClick = actions.actions(item),
            artworkOverlay = { shape ->
                if (spec.logoWall) LogoOverlay(item.logo, hovered)
                hoverOverlay?.invoke(this, shape)
            },
        )
    }
}

@Composable
private fun FolderTile(folder: CollectionFolder, spec: TileSpec, onClick: (() -> Unit)?, onLongClick: (() -> Unit)?) {
    val shape = RoundedCornerShape(spec.corner)
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val ring by animateColorAsState(if (hovered) Color.White.copy(alpha = 0.85f) else RedesignColors.hairline, tween(160), label = "folder_tile_ring")
    val art = if (spec.logoWall || spec.landscape) {
        folder.heroBackdropUrl?.takeIf { it.isNotBlank() } ?: folder.coverImageUrl
    } else {
        folder.coverImageUrl ?: folder.heroBackdropUrl
    }
    Box(
        Modifier.size(spec.width, spec.height).clip(shape).background(RedesignColors.fill)
            .border(if (hovered) 2.dp else 1.dp, ring, shape)
            .hoverable(interaction)
            .posterCardClickable(onClick, onLongClick, zoomImageUrl = art, zoomCornerRadius = spec.corner)
            .testTag("home_styled_folder_tile"),
    ) {
        NuvioAsyncImage(model = art, contentDescription = folder.title, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        if (spec.logoWall) {
            LogoOverlay(folder.titleLogoUrl, hovered)
        } else if (!folder.hideTitle && art.isNullOrBlank()) {
            Text(
                folder.title,
                style = redesignText(14.sp, FontWeight.SemiBold),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.Center).padding(10.dp),
            )
        }
    }
}

/** "Row styles · Logo wall": the backdrop darkened to 55 % and the clear logo centred, in white. */
@Composable
private fun BoxScope.LogoOverlay(logo: String?, hovered: Boolean) {
    if (logo.isNullOrBlank()) return
    val dim by animateFloatAsState(if (hovered) 0f else 0.45f, tween(200), label = "logo_wall_dim")
    Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = dim)))
    BoxWithConstraints(Modifier.matchParentSize()) {
        NuvioAsyncImage(
            model = logo,
            contentDescription = null,
            modifier = Modifier.align(Alignment.Center)
                .padding(horizontal = maxWidth * 0.15f, vertical = maxHeight * 0.28f)
                .fillMaxSize(),
            contentScale = ContentScale.Fit,
            colorFilter = ColorFilter.tint(Color.White, BlendMode.SrcIn),
        )
    }
}

private val MetaInk = RedesignColors.textMuted

private fun mediaTypeLabel(type: String): String = when (type.lowercase()) {
    "movie" -> "Movie"
    "series", "tv", "show" -> "Series"
    else -> type.replaceFirstChar { it.uppercase() }
}

/** "Movie · 2026 · ★ 8.4", or with the first genre after the year. */
internal fun titleMetaLine(item: MetaPreview, withGenre: Boolean): String = listOfNotNull(
    mediaTypeLabel(item.type).takeIf { it.isNotBlank() },
    item.releaseInfo?.takeIf { it.isNotBlank() }?.take(4),
    item.genres.firstOrNull()?.takeIf { withGenre },
    displayRatingText(item.imdbRating)?.let { "★ $it" },
).joinToString(" · ")

// ---------------------------------------------------------------------------------------------
// Compact list: columns of three 112 × 63 thumbs with title and meta, 400 dp wide.
// ---------------------------------------------------------------------------------------------

private val CompactRowWidth = 400.dp

@Composable
private fun <T> CompactListLayout(
    header: RowHeaderSpec,
    entries: List<T>,
    key: (T) -> Any,
    tile: @Composable (T, TileSpec) -> Unit,
    title: (T) -> String,
    meta: (T) -> String?,
    onRowClick: (T) -> (() -> Unit)?,
    onRowLongClick: (T) -> (() -> Unit)?,
) {
    val columns = remember(entries) { entries.chunked(3) }
    StyledShelf(header, columns, key = { column -> key(column.first()) }, spacing = 24.dp) { column ->
        Column(Modifier.width(CompactRowWidth), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            column.forEach { entry ->
                val interaction = remember { MutableInteractionSource() }
                val hovered by interaction.collectIsHoveredAsState()
                val fill by animateColorAsState(Color.White.copy(alpha = if (hovered) 0.06f else 0f), tween(140), label = "compact_row_fill")
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(fill)
                        .hoverable(interaction)
                        .posterCardClickable(onRowClick(entry), onRowLongClick(entry))
                        .padding(6.dp)
                        .testTag("home_compact_row"),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    tile(entry, TileSpec(112.dp, 63.dp, landscape = true, corner = 8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(title(entry), style = redesignText(14.5.sp, FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        meta(entry)?.takeIf { it.isNotBlank() }?.let {
                            Text(it, style = redesignText(12.5.sp, color = MetaInk), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Mosaic: one 200 × 296 poster then a 2 × 2 of 95 × 143, repeating.
// ---------------------------------------------------------------------------------------------

/** Groups of five; the big slot goes to the group's best rank, else its first title. */
internal fun <T> mosaicGroups(entries: List<T>, rank: (T) -> Int?): List<List<T>> =
    entries.chunked(5).map { group ->
        val best = group.withIndex().filter { (rank(it.value) ?: 0) > 0 }.minByOrNull { rank(it.value)!! }?.index ?: 0
        listOf(group[best]) + group.filterIndexed { index, _ -> index != best }
    }

@Composable
private fun <T> MosaicLayout(
    header: RowHeaderSpec,
    entries: List<T>,
    key: (T) -> Any,
    rank: (T) -> Int?,
    tile: @Composable (T, TileSpec) -> Unit,
) {
    val groups = remember(entries) { mosaicGroups(entries, rank) }
    StyledShelf(header, groups, key = { group -> key(group.first()) }, spacing = 10.dp) { group ->
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.testTag("home_mosaic_group")) {
            tile(group.first(), TileSpec(200.dp, 296.dp, landscape = false))
            val small = group.drop(1)
            if (small.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    small.chunked(2).forEach { pair ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            pair.forEach { tile(it, TileSpec(95.dp, 143.dp, landscape = false, corner = 10.dp)) }
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Logo wall: 310 × 174 backdrops with the clear logo.
// ---------------------------------------------------------------------------------------------

@Composable
private fun <T> LogoWallLayout(header: RowHeaderSpec, entries: List<T>, key: (T) -> Any, tile: @Composable (T, TileSpec) -> Unit) {
    StyledShelf(header, entries, key, spacing = 14.dp) { entry ->
        tile(entry, TileSpec(310.dp, 174.dp, landscape = true, logoWall = true))
    }
}

// ---------------------------------------------------------------------------------------------
// Spotlight: the first title at 600 × 338, then 214 × 120 cards two to a column beside it.
// ---------------------------------------------------------------------------------------------

private val SpotlightWidth = 600.dp
private val SpotlightHeight = 338.dp
private val SpotlightGap = 14.dp

@Composable
private fun SpotlightLayout(header: RowHeaderSpec, entries: List<MetaPreview>, actions: StyledRowActions) {
    val lead = entries.firstOrNull() ?: return
    val columns = remember(entries) { entries.drop(1).chunked(2) }
    Box {
        StyledShelf(
            spec = header,
            units = columns,
            key = { column -> column.first().stableKey() },
            spacing = SpotlightGap,
            rowStartInset = SpotlightWidth + SpotlightGap,
        ) { column ->
            Column(Modifier.height(SpotlightHeight), verticalArrangement = Arrangement.spacedBy(SpotlightGap)) {
                column.forEach { TitleTile(it, TileSpec(214.dp, 120.dp, landscape = true), actions) }
            }
        }
        // The spotlight stays on the first title; the block scrolls under it.
        HomeRowLeadCard(
            item = lead,
            width = SpotlightWidth,
            height = SpotlightHeight,
            onClick = actions.open(lead),
            onLongClick = actions.actions(lead),
            spotlight = true,
            modifier = Modifier.align(Alignment.BottomStart).padding(start = header.sectionPadding),
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Calendar strip: day headers over 240 × 135 cards; days with nothing are skipped.
// ---------------------------------------------------------------------------------------------

internal data class CalendarDay(val date: String, val items: List<MetaPreview>)

/** The row's upcoming titles grouped by day, in date order; titles without a date are left out. */
internal fun calendarDays(entries: List<MetaPreview>, today: String): List<CalendarDay> =
    entries.mapNotNull { item -> item.upcomingDate(today)?.let { it to item } }
        .groupBy({ it.first }, { it.second })
        .map { (date, items) -> CalendarDay(date, items) }
        .sortedBy { it.date }

/**
 * The day header: "Tonight" (with "Wed 25" beside it) for today, the weekday and date within the
 * coming week ("Thu 26"), and the month too beyond it ("Fri 25 Dec"), so a later date never reads
 * as this week.
 */
internal fun calendarDayLabel(date: String, today: String, weekday: (String) -> String, month: (String) -> String): String {
    val base = "${weekday(date)} ${date.takeLast(2).toInt()}"
    val daysAway = isoDaysBetween(today, date)
    return if (daysAway in 0..6) base else "$base ${month(date)}"
}

private fun isoDaysBetween(from: String, to: String): Long =
    com.nuvio.app.core.time.isoEpochDay(to) - com.nuvio.app.core.time.isoEpochDay(from)

/**
 * The air time of [item] on [date], from its own calendar card or the Trakt calendar (matched by
 * IMDb / TMDB / Trakt id); null when no source knows it.
 */
internal fun calendarAirTime(item: MetaPreview, date: String, cards: List<com.nuvio.app.features.trakt.CalendarCard>): Long? {
    item.calendarCard?.releaseEpochMs?.let { return it }
    val ids = (setOf(item.id) + item.alternateIds).map { it.lowercase() }.toSet()
    return cards.firstOrNull { card ->
        card.localDate == date && card.media.let { media ->
            media.id.lowercase() in ids ||
                media.imdbId?.lowercase() in ids ||
                media.tmdbId?.let { "tmdb:$it" } in ids ||
                media.traktId?.let { "trakt:$it" } in ids
        }
    }?.releaseEpochMs
}

@Composable
private fun CalendarStripLayout(header: RowHeaderSpec, entries: List<MetaPreview>, actions: StyledRowActions) {
    val today = remember { CurrentDateProvider.todayIsoDate() }
    val days = remember(entries, today) { calendarDays(entries, today) }
    val traktCalendar by com.nuvio.app.features.trakt.TraktCalendarRepository.uiState.collectAsStateWithLifecycle()
    val tonight = stringResource(Res.string.home_row_tonight)
    val todayLabel = stringResource(Res.string.calendar_today)
    val tomorrowLabel = stringResource(Res.string.calendar_tomorrow)
    val movieLabel = stringResource(Res.string.calendar_movie)
    StyledShelf(header, days, key = { it.date }, spacing = 26.dp) { day ->
        val dayLabel = calendarDayLabel(day.date, today, CalendarPlatform::weekdayLabel, CalendarPlatform::shortMonthLabel)
        val daysAway = isoDaysBetween(today, day.date).toInt()
        val inDays = stringResource(Res.string.calendar_in_days, daysAway)
        // The header's hairline spans the day's cards.
        Column(Modifier.width(IntrinsicSize.Max).testTag("home_calendar_day")) {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (day.date == today) tonight else dayLabel, style = redesignText(15.sp, FontWeight.Bold), maxLines = 1)
                if (day.date == today) Text(dayLabel, style = redesignText(12.5.sp, color = MetaInk), maxLines = 1)
            }
            Box(Modifier.padding(top = 8.dp, bottom = 10.dp).fillMaxWidth().height(1.dp).background(RedesignColors.hairline).testTag("home_calendar_day_rule"))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                day.items.forEach { item ->
                    Column(Modifier.width(240.dp)) {
                        Box {
                            TitleTile(item, TileSpec(240.dp, 135.dp, landscape = true), actions)
                            // The air time when known; otherwise "Movie", or how far away the day is.
                            val chip = calendarAirTime(item, day.date, traktCalendar.cards)?.let(::formatLocalTime)
                                ?: movieLabel.takeIf { item.type.equals("movie", ignoreCase = true) }
                                ?: when (daysAway) {
                                    0 -> todayLabel
                                    1 -> tomorrowLabel
                                    else -> inDays
                                }
                            Text(
                                chip,
                                style = redesignText(12.sp, FontWeight.Bold),
                                maxLines = 1,
                                modifier = Modifier.padding(8.dp).clip(RoundedCornerShape(999.dp))
                                    .background(Color(0xB80A0A0B)).padding(horizontal = 9.dp, vertical = 3.dp)
                                    .testTag("home_calendar_chip"),
                            )
                        }
                        Text(item.name, style = redesignText(14.sp, FontWeight.Bold), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp))
                        Text(
                            item.calendarCard?.episodeLabel ?: item.nextEpisodeLine() ?: titleMetaLine(item, withGenre = false),
                            style = redesignText(12.5.sp, color = MetaInk),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Info cards: 420 × 200, poster left, then title, meta, overview, Trailer and Add to library.
// ---------------------------------------------------------------------------------------------

@Composable
private fun InfoCardsLayout(header: RowHeaderSpec, entries: List<MetaPreview>, actions: StyledRowActions) {
    StyledShelf(header, entries, MetaPreview::stableKey, spacing = 14.dp) { item -> InfoCard(item, entries, actions) }
}

@Composable
private fun InfoCard(item: MetaPreview, row: List<MetaPreview>, actions: StyledRowActions) {
    val shape = RoundedCornerShape(14.dp)
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val border by animateColorAsState(if (hovered) RedesignColors.hairlineFocused else RedesignColors.hairline, tween(160), label = "info_card_border")
    Row(
        Modifier.size(420.dp, 200.dp).clip(shape).background(Color.White.copy(alpha = 0.05f)).border(1.dp, border, shape)
            .hoverable(interaction)
            .posterCardClickable(actions.open(item), actions.actions(item), zoomImageUrl = item.poster, zoomCornerRadius = 14.dp)
            .testTag("home_info_card"),
    ) {
        NuvioAsyncImage(model = item.poster, contentDescription = item.name, modifier = Modifier.size(135.dp, 200.dp), contentScale = ContentScale.Crop)
        Column(Modifier.fillMaxHeight().padding(horizontal = 16.dp, vertical = 14.dp)) {
            Text(item.name, style = redesignText(16.sp, FontWeight.Bold, letterSpacing = (-0.01).em), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                titleMetaLine(item, withGenre = true),
                style = redesignText(12.5.sp, color = MetaInk),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 3.dp, bottom = 8.dp),
            )
            Text(
                item.description.orEmpty(),
                style = redesignText(13.sp, color = RedesignColors.textSecondary, lineHeight = 19.sp),
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                val target = remember(item, row) { QuickLookTarget(item, row, onOpenDetails = actions.open(item)) }
                RedesignPillButton(
                    label = stringResource(Res.string.home_row_trailer),
                    onClick = { PosterQuickLook.playTrailer(target) },
                    leading = Icons.Rounded.PlayArrow,
                    primary = true,
                    modifier = Modifier.testTag("home_info_card_trailer"),
                )
                WithPosterLibraryToggle(item) { isSaved, toggle ->
                    RedesignIconButton(
                        contentDescription = item.name,
                        onClick = toggle,
                        vector = if (isSaved) Icons.Rounded.Check else Icons.Rounded.Add,
                        size = 32.dp,
                        modifier = Modifier.testTag("home_info_card_library"),
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Editorial band: a 280 dp tinted panel, copy on the left, posters on the right.
// ---------------------------------------------------------------------------------------------

private val BandShape = RoundedCornerShape(18.dp)
private val BandInk = Color(0xFF080706)
private val EditorialAccent = Color(0xFFE0B48A)

@Composable
private fun <T> EditorialBandLayout(
    header: RowHeaderSpec,
    eyebrow: String,
    description: String?,
    backdrop: String?,
    entries: List<T>,
    key: (T) -> Any,
    tile: @Composable (T, TileSpec) -> Unit,
) {
    BandFrame(header, height = 280.dp, tag = "home_editorial_band", onClick = null) {
        NuvioAsyncImage(
            model = backdrop,
            contentDescription = null,
            modifier = Modifier.fillMaxSize().graphicsLayer { scaleX = 1.2f; scaleY = 1.2f; alpha = 0.55f }.blur(30.dp),
            contentScale = ContentScale.Crop,
        )
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to BandInk.copy(alpha = 0.9f), 0.4f to BandInk.copy(alpha = 0.55f), 1f to BandInk.copy(alpha = 0.3f))))
        Column(Modifier.align(Alignment.CenterStart).padding(start = 26.dp).width(300.dp)) {
            Text(eyebrow.uppercase(), style = redesignText(11.sp, FontWeight.SemiBold, EditorialAccent, letterSpacing = 0.1.em), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                header.title,
                style = redesignText(28.sp, FontWeight.Bold, lineHeight = 31.sp, letterSpacing = (-0.02).em),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 6.dp, bottom = 8.dp),
            )
            Crossfade(description, animationSpec = tween(200), label = "editorial_description") { line ->
                if (line != null) {
                    Text(
                        line,
                        style = redesignText(14.sp, color = Color(0xFFE3E5E8), lineHeight = 20.sp),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("home_editorial_description"),
                    )
                }
            }
            header.onViewAllClick?.let { seeAll ->
                Spacer(Modifier.height(18.dp))
                RedesignPillButton(
                    label = stringResource(Res.string.search_see_all),
                    onClick = seeAll,
                    primary = true,
                    trailing = Icons.AutoMirrored.Rounded.ArrowForward,
                )
            }
        }
        val state = rememberLazyListState()
        LazyRow(
            state = state,
            // Excluded from the back swipe like every shelf: scrolling the posters must not go back.
            modifier = Modifier.padding(start = 350.dp, top = 32.dp).fillMaxWidth().nuvioDesktopDragScroll(state),
            contentPadding = PaddingValues(start = 4.dp, end = 26.dp, top = 4.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            items(entries, key = { key(it) }) { tile(it, TileSpec(146.dp, 216.dp, landscape = false)) }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Collage banner: a 230 dp banner of tilted backdrops with the row's name, size and Open.
// ---------------------------------------------------------------------------------------------

@Composable
private fun CollageBannerLayout(header: RowHeaderSpec, section: HomeCatalogSection, entries: List<MetaPreview>) {
    BandFrame(header, height = 230.dp, tag = "home_collage_banner", onClick = header.onViewAllClick) {
        Box(Modifier.fillMaxSize().background(RedesignColors.fill))
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val start = maxWidth * 0.32f
            entries.take(6).forEachIndexed { index, item ->
                Box(
                    Modifier.offset(x = start + 150.dp * index, y = (-24).dp)
                        .size(240.dp, 300.dp)
                        .graphicsLayer { rotationZ = -12f }
                        .clip(RoundedCornerShape(14.dp)),
                ) {
                    NuvioAsyncImage(model = rememberTitleLandscapeArt(item), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                }
            }
        }
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to BandInk.copy(alpha = 0.9f), 0.45f to BandInk.copy(alpha = 0.5f), 1f to BandInk.copy(alpha = 0.15f))))
        Column(Modifier.align(Alignment.CenterStart).padding(start = 26.dp)) {
            val count = maxOf(section.availableItemCount, entries.size)
            Text(
                listOf(section.addonName, stringResource(Res.string.home_row_titles_count, count.toString())).filter { it.isNotBlank() }.joinToString(" · ").uppercase(),
                style = redesignText(11.sp, FontWeight.SemiBold, Color(0xFFC9CDD1), letterSpacing = 0.1.em),
                maxLines = 1,
            )
            Text(
                header.title,
                style = redesignText(32.sp, FontWeight.Bold, letterSpacing = (-0.02).em),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 6.dp, bottom = 16.dp),
            )
            header.onViewAllClick?.let { open ->
                RedesignPillButton(
                    label = stringResource(Res.string.home_row_open),
                    onClick = open,
                    primary = true,
                    trailing = Icons.AutoMirrored.Rounded.ArrowForward,
                    modifier = Modifier.testTag("home_collage_open"),
                )
            }
        }
    }
}

/**
 * The band styles have no shelf header, so the row's ⋯ (and, while it is moved, ↑ ↓ Done) sit in
 * the band's top-right corner, and the reorder drag surface covers the band.
 */
@Composable
private fun BandFrame(
    header: RowHeaderSpec,
    height: Dp,
    tag: String,
    onClick: (() -> Unit)?,
    content: @Composable BoxScope.() -> Unit,
) {
    val h = header.header
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val border by animateColorAsState(if (hovered && onClick != null) RedesignColors.hairlineFocused else RedesignColors.hairline, tween(160), label = "band_border")
    Box(
        Modifier.padding(horizontal = header.sectionPadding).fillMaxWidth().height(height)
            .then(h?.modifier ?: Modifier)
            .clip(BandShape).border(1.dp, border, BandShape)
            .hoverable(interaction)
            .then(if (onClick != null) Modifier.clickable(interactionSource = interaction, indication = null, onClick = onClick) else Modifier)
            .testTag(tag),
    ) {
        content()
        val actions = h?.actions
        val leading = h?.leading
        if (actions != null || leading != null) {
            Row(
                Modifier.align(Alignment.TopEnd).padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                leading?.invoke()
                actions?.invoke(this)
            }
        }
        h?.rowOverlay?.invoke(this)
    }
}
