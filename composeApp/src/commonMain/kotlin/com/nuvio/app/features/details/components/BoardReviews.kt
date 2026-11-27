package com.nuvio.app.features.details.components

import com.nuvio.app.core.ui.LocalRedesignMenuSurface
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.nuvio.app.OverlayEscapeHandler
import com.nuvio.app.core.ui.NuvioToastController
import com.nuvio.app.core.ui.RedesignColors
import com.nuvio.app.core.ui.RedesignDanger
import com.nuvio.app.core.ui.RedesignIconButton
import com.nuvio.app.core.ui.RedesignMenuSurface
import com.nuvio.app.core.ui.RedesignOnLight
import com.nuvio.app.core.ui.RedesignPillButton
import com.nuvio.app.core.ui.RedesignPillGroup
import com.nuvio.app.core.ui.RedesignSwitchPill
import com.nuvio.app.core.format.formatReleaseDateForDisplay
import com.nuvio.app.core.ui.RedesignTileShape
import com.nuvio.app.core.ui.redesignText
import com.nuvio.app.features.details.MetaDetails
import com.nuvio.app.features.tracking.TrackingComment
import com.nuvio.app.features.tracking.TrackingCommentPostResult
import com.nuvio.app.features.tracking.TrackingCommentScope
import com.nuvio.app.features.tracking.TrackingComments
import com.nuvio.app.features.tracking.TrackingCommentsProvider
import com.nuvio.app.features.tracking.TrackingCommentsSort
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.action_cancel
import nuvio.composeapp.generated.resources.action_retry
import nuvio.composeapp.generated.resources.detail_comments_badge_review
import nuvio.composeapp.generated.resources.detail_comments_empty
import nuvio.composeapp.generated.resources.detail_reviews_count_of
import nuvio.composeapp.generated.resources.detail_reviews_failed
import nuvio.composeapp.generated.resources.detail_reviews_from
import nuvio.composeapp.generated.resources.detail_reviews_hide_spoilers
import nuvio.composeapp.generated.resources.detail_reviews_load_more
import nuvio.composeapp.generated.resources.detail_reviews_most_liked
import nuvio.composeapp.generated.resources.detail_reviews_newest
import nuvio.composeapp.generated.resources.detail_reviews_open_on
import nuvio.composeapp.generated.resources.detail_reviews_post
import nuvio.composeapp.generated.resources.detail_reviews_post_failed
import nuvio.composeapp.generated.resources.detail_reviews_posted
import nuvio.composeapp.generated.resources.detail_reviews_reveal
import nuvio.composeapp.generated.resources.detail_reviews_scope_episode
import nuvio.composeapp.generated.resources.detail_reviews_scope_season
import nuvio.composeapp.generated.resources.detail_reviews_scope_title
import nuvio.composeapp.generated.resources.detail_reviews_spoiler
import nuvio.composeapp.generated.resources.detail_reviews_too_short
import nuvio.composeapp.generated.resources.detail_reviews_words_comment
import nuvio.composeapp.generated.resources.detail_reviews_words_needed
import nuvio.composeapp.generated.resources.detail_reviews_words_review
import nuvio.composeapp.generated.resources.detail_reviews_write
import nuvio.composeapp.generated.resources.detail_reviews_write_placeholder
import nuvio.composeapp.generated.resources.detail_reviews_write_title
import nuvio.composeapp.generated.resources.detail_reviews_likes
import nuvio.composeapp.generated.resources.detail_reviews_only
import nuvio.composeapp.generated.resources.detail_reviews_reader_hint
import nuvio.composeapp.generated.resources.detail_reviews_your_rating
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

private const val ReviewColumns = 3
private val ReviewCardHeight = 196.dp

/** Optimistic like: the heart and count change at once; a refusal puts them back. */
internal fun TrackingComment.withLiked(liked: Boolean): TrackingComment =
    if (likedByMe == liked) this else copy(likedByMe = liked, likes = (likes + if (liked) 1 else -1).coerceAtLeast(0))

/**
 * The board's Reviews tab, from the reviews source (Settings → Tracking). Sort by Most liked or
 * Newest, Hide spoilers, Load more in place; a heart per card when the source has likes; a card
 * opens the frosted reader; "Write a review" opens the composer when the source can post. Nothing
 * here names a tracker except as the source's own name.
 */
@Composable
internal fun BoardReviews(
    meta: MetaDetails,
    provider: TrackingCommentsProvider,
    episodes: List<Pair<Int, Int>>,
    modifier: Modifier = Modifier,
    /** The episode the board is on (its play target), which the composer offers as Season / episode. */
    currentEpisode: Pair<Int, Int>? = null,
) {
    val scope = rememberCoroutineScope()
    var view by remember(meta.id, provider.providerId) { mutableStateOf(ReviewsView.MostLiked) }
    // Reviews only reads the Most liked pages, so switching between them keeps what is loaded.
    val sort = if (view == ReviewsView.Newest) TrackingCommentsSort.Newest else TrackingCommentsSort.MostLiked
    val comments = remember(meta.id, provider.providerId, sort) { mutableStateListOf<TrackingComment>() }
    var page by remember(meta.id, provider.providerId, sort) { mutableIntStateOf(0) }
    var pageCount by remember(meta.id, provider.providerId, sort) { mutableIntStateOf(1) }
    var total by remember(meta.id, provider.providerId, sort) { mutableIntStateOf(0) }
    var loading by remember(meta.id, provider.providerId, sort) { mutableStateOf(true) }
    var failed by remember(meta.id, provider.providerId, sort) { mutableStateOf(false) }
    var hideSpoilers by remember { mutableStateOf(true) }
    val shown = if (view == ReviewsView.ReviewsOnly) comments.filter { it.review } else comments.toList()
    var readerIndex by remember { mutableStateOf<Int?>(null) }
    var composing by remember { mutableStateOf(false) }
    var reloadToken by remember { mutableIntStateOf(0) }

    suspend fun loadNext() {
        loading = true
        failed = false
        try {
            val next = provider.page(meta, sort, page + 1)
            val seen = comments.map { it.id }.toSet()
            comments += next.items.filter { it.id !in seen }
            page = next.page
            pageCount = next.pageCount
            total = next.itemCount
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            failed = true
        } finally {
            loading = false
        }
    }
    LaunchedEffect(meta.id, provider.providerId, sort, reloadToken) {
        comments.clear(); page = 0
        loadNext()
    }
    // Reviews only: a few more pages when the loaded ones hold fewer reviews than a row.
    LaunchedEffect(view, page, loading) {
        if (view != ReviewsView.ReviewsOnly || loading || failed || page >= pageCount || page >= ReviewsOnlyAutoPages) return@LaunchedEffect
        if (shown.size < ReviewColumns) loadNext()
    }
    val like: (TrackingComment) -> Unit = { comment ->
        val index = comments.indexOfFirst { it.id == comment.id }
        if (index >= 0) {
            val wanted = !comment.likedByMe
            comments[index] = comment.withLiked(wanted)
            scope.launch {
                if (!provider.setLiked(comment, wanted)) {
                    val at = comments.indexOfFirst { it.id == comment.id }
                    if (at >= 0) comments[at] = comments[at].withLiked(!wanted)
                    NuvioToastController.show(getString(Res.string.detail_reviews_failed))
                }
            }
        }
    }

    Column(modifier.fillMaxWidth().testTag("detail_board_reviews"), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // The tab names it: "<source> · Most liked", Newest and Reviews only on the left (frame "Reviews").
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            val mostLiked = stringResource(Res.string.detail_reviews_most_liked)
            RedesignPillGroup(
                options = ReviewsView.entries.toList(),
                selected = view,
                label = {
                    when (it) {
                        ReviewsView.MostLiked -> "${provider.providerId.displayName} · $mostLiked"
                        ReviewsView.Newest -> stringResource(Res.string.detail_reviews_newest)
                        ReviewsView.ReviewsOnly -> stringResource(Res.string.detail_reviews_only)
                    }
                },
                onSelect = { view = it },
                optionTag = { "detail_reviews_sort_${it.name}" },
            )
            Spacer(Modifier.weight(1f))
            RedesignSwitchPill(
                label = stringResource(Res.string.detail_reviews_hide_spoilers),
                checked = hideSpoilers,
                onCheckedChange = { hideSpoilers = it },
                modifier = Modifier.testTag("detail_reviews_hide_spoilers"),
            )
            if (provider.capabilities.canPost) {
                RedesignPillButton(
                    label = stringResource(Res.string.detail_reviews_write),
                    onClick = { composing = true },
                    leading = Icons.Rounded.Edit,
                    primary = true,
                    modifier = Modifier.testTag("detail_reviews_write"),
                )
            }
        }
        AnimatedContent(
            targetState = when {
                shown.isEmpty() && loading -> 0
                shown.isEmpty() && failed -> 1
                shown.isEmpty() -> 2
                else -> 3
            },
            transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(160)) },
            label = "reviews_state",
        ) { state ->
            when (state) {
                0 -> ReviewGrid(List(ReviewColumns) { null }, hideSpoilers, canLike = false, onOpen = {}, onLike = {})
                1 -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(Res.string.detail_reviews_failed), style = redesignText(14.sp, color = RedesignColors.textSecondary))
                    RedesignPillButton(label = stringResource(Res.string.action_retry), onClick = { reloadToken++ })
                }
                2 -> Text(stringResource(Res.string.detail_comments_empty), style = redesignText(14.sp, color = RedesignColors.textSecondary))
                else -> Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    ReviewGrid(shown, hideSpoilers, provider.capabilities.canLike, onOpen = { readerIndex = it }, onLike = like)
                    if (page < pageCount) {
                        RedesignPillButton(
                            label = stringResource(Res.string.detail_reviews_load_more),
                            onClick = { if (!loading) scope.launch { loadNext() } },
                            modifier = Modifier.align(Alignment.CenterHorizontally).testTag("detail_reviews_load_more"),
                        )
                    }
                }
            }
        }
    }

    readerIndex?.let { start ->
        // The reader walks the list as shown, so Reviews only opens the review that was clicked.
        ReviewReader(
            comments = shown,
            start = start,
            total = if (view == ReviewsView.ReviewsOnly) shown.size else total.coerceAtLeast(comments.size),
            providerName = provider.providerId.displayName,
            canLike = provider.capabilities.canLike,
            onLike = like,
            onNeedMore = { if (!loading && page < pageCount) scope.launch { loadNext() } },
            onClose = { readerIndex = null },
        )
    }
    if (composing) {
        ReviewComposer(
            meta = meta,
            provider = provider,
            episodes = episodes,
            currentEpisode = currentEpisode,
            onClose = { composing = false },
            onPosted = {
                composing = false
                if (view != ReviewsView.Newest) view = ReviewsView.Newest else reloadToken++
            },
        )
    }
}

/** The joined track's choices: two sorts, and Most liked limited to long reviews. */
private enum class ReviewsView { MostLiked, Newest, ReviewsOnly }

/** Pages Reviews only may load on its own to fill a row. */
private const val ReviewsOnlyAutoPages = 5

@Composable
private fun ReviewGrid(
    comments: List<TrackingComment?>,
    hideSpoilers: Boolean,
    canLike: Boolean,
    onOpen: (Int) -> Unit,
    onLike: (TrackingComment) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val gap = 14.dp
        val width = (maxWidth - gap * (ReviewColumns - 1)) / ReviewColumns
        Column(verticalArrangement = Arrangement.spacedBy(gap)) {
            comments.chunked(ReviewColumns).forEachIndexed { row, items ->
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    items.forEachIndexed { column, comment ->
                        val index = row * ReviewColumns + column
                        if (comment == null) {
                            Box(Modifier.width(width).height(ReviewCardHeight).clip(RedesignTileShape).background(RedesignColors.fill))
                        } else {
                            ReviewCard(comment, hideSpoilers, canLike, Modifier.width(width), { onOpen(index) }, { onLike(comment) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReviewCard(
    comment: TrackingComment,
    hideSpoilers: Boolean,
    canLike: Boolean,
    modifier: Modifier,
    onOpen: () -> Unit,
    onLike: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val border by animateColorAsState(if (hovered) RedesignColors.hairlineFocused else RedesignColors.hairline, tween(160), label = "review_card")
    Column(
        modifier.height(ReviewCardHeight).clip(RedesignTileShape).background(RedesignColors.fill).border(1.dp, border, RedesignTileShape)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onOpen)
            .padding(16.dp)
            .testTag("detail_review_card"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ReviewHeader(comment)
        val blurred = hideSpoilers && comment.hasSpoilerContent
        Text(
            comment.text,
            style = redesignText(13.5.sp, color = RedesignColors.textSecondary, lineHeight = 19.sp),
            maxLines = 5,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).then(if (blurred) Modifier.blur(7.dp) else Modifier),
        )
        if (canLike) LikeButton(comment, onLike)
    }
}

@Composable
private fun ReviewHeader(comment: TrackingComment) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        // Author, origin and the Review tag take the free width, so the score sits at the card's right edge.
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(comment.authorName, style = redesignText(14.sp, FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            // Imported comments credit where they were written (a tracker's attribution rules).
            comment.origin?.let { Text(it, style = redesignText(12.sp, color = RedesignColors.textMuted), maxLines = 1) }
            if (comment.review) {
                Text(
                    stringResource(Res.string.detail_comments_badge_review),
                    style = redesignText(11.sp, FontWeight.Bold, RedesignOnLight),
                    modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(RedesignColors.textPrimary).padding(horizontal = 6.dp, vertical = 1.dp),
                )
            }
        }
        comment.rating?.let { rating ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.testTag("detail_review_score")) {
                Icon(Icons.Rounded.Star, contentDescription = null, tint = Color(0xFFF5C518), modifier = Modifier.size(14.dp))
                Text(rating.toString(), style = redesignText(12.5.sp, FontWeight.SemiBold))
            }
        }
    }
}

/** The reader's header: an initials avatar, the author, and "Review · 9/10 · 52 likes · 12 Mar 2025". */
@Composable
private fun ReaderHeader(comment: TrackingComment) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(RedesignColors.fillStrong).border(1.dp, RedesignColors.hairline, CircleShape)
                .testTag("detail_review_reader_avatar"),
            contentAlignment = Alignment.Center,
        ) {
            Text(comment.authorName.reviewInitials(), style = redesignText(14.sp, FontWeight.Bold, RedesignColors.textSecondary))
        }
        Column {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(comment.authorName, style = redesignText(15.sp, FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                comment.origin?.let { Text(it, style = redesignText(12.sp, color = RedesignColors.textMuted), maxLines = 1) }
            }
            val parts = listOfNotNull(
                if (comment.review) stringResource(Res.string.detail_comments_badge_review) else null,
                comment.rating?.let { "$it/10" },
                pluralStringResource(Res.plurals.detail_reviews_likes, comment.likes, comment.likes),
                comment.createdAt?.take(10)?.takeIf { it.length == 10 }?.let(::formatReleaseDateForDisplay),
            )
            Text(parts.joinToString(" · "), style = redesignText(12.5.sp, color = RedesignColors.textMuted), maxLines = 1, modifier = Modifier.testTag("detail_review_reader_meta"))
        }
    }
}

/** "GT Connects" → "GC"; one word → its first letter. */
private fun String.reviewInitials(): String {
    val words = trim().split(Regex("\\s+")).filter { it.isNotBlank() }
    return when {
        words.isEmpty() -> "?"
        words.size == 1 -> words.first().take(1).uppercase()
        else -> (words.first().take(1) + words.last().take(1)).uppercase()
    }
}

@Composable
private fun LikeButton(comment: TrackingComment, onLike: () -> Unit) {
    val tint by animateColorAsState(if (comment.likedByMe) Color(0xFFFF6B6B) else RedesignColors.textMuted, tween(160), label = "like_tint")
    Row(
        Modifier.clip(CircleShape).clickable(role = Role.Button, onClick = onLike).padding(horizontal = 6.dp, vertical = 3.dp)
            .semantics { contentDescription = "like ${comment.likes}" }
            .testTag("detail_review_like"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Icon(if (comment.likedByMe) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
        Text(comment.likes.toString(), style = redesignText(12.5.sp, FontWeight.SemiBold, tint), modifier = Modifier.testTag("detail_review_like_count"))
    }
}

/** The frosted reader over the dimmed board: prev/next (← →), paging at the end, spoilers on reveal, Esc. */
@Composable
private fun ReviewReader(
    comments: List<TrackingComment>,
    start: Int,
    total: Int,
    providerName: String,
    canLike: Boolean,
    onLike: (TrackingComment) -> Unit,
    onNeedMore: () -> Unit,
    onClose: () -> Unit,
) {
    var index by remember { mutableIntStateOf(start) }
    var revealed by remember { mutableStateOf(setOf<String>()) }
    val state = remember { MutableTransitionState(false) }
    LaunchedEffect(Unit) { state.targetState = true }
    LaunchedEffect(state.currentState, state.targetState) { if (!state.currentState && !state.targetState && state.isIdle) onClose() }
    val close = { state.targetState = false }
    OverlayEscapeHandler { close() }
    LaunchedEffect(index, comments.size) { if (index >= comments.size - 2) onNeedMore() }
    val uriHandler = LocalUriHandler.current
    val focus = remember { FocusRequester() }
    LaunchedEffect(state.currentState) { if (state.currentState) runCatching { focus.requestFocus() } }
    Popup(onDismissRequest = close, properties = PopupProperties(focusable = true)) {
        AnimatedVisibility(visibleState = state, enter = fadeIn(tween(200)), exit = fadeOut(tween(160))) {
            Box(
                Modifier.fillMaxSize().background(Color(0x8C050607))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = close)
                    .onPreviewKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        when (event.key) {
                            Key.DirectionLeft -> { index = (index - 1).coerceAtLeast(0); true }
                            Key.DirectionRight -> { index = (index + 1).coerceAtMost(comments.lastIndex); true }
                            Key.Escape -> { close(); true }
                            else -> false
                        }
                    }
                    .focusRequester(focus)
                    .focusable(),
                contentAlignment = Alignment.Center,
            ) {
                val comment = comments.getOrNull(index) ?: return@Box
                val shape = RoundedCornerShape(22.dp)
                Column(
                    Modifier.width(720.dp).heightIn(max = 620.dp).clip(shape).background(LocalRedesignMenuSurface.current).border(1.dp, RedesignColors.hairline, shape)
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                        .padding(22.dp).testTag("detail_review_reader"),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) { ReaderHeader(comment) }
                        Text(
                            stringResource(Res.string.detail_reviews_count_of, index + 1, total),
                            style = redesignText(12.5.sp, color = RedesignColors.textMuted),
                            modifier = Modifier.padding(horizontal = 12.dp).testTag("detail_review_reader_position"),
                        )
                        RedesignIconButton("close", close, vector = Icons.Rounded.Close, size = 36.dp)
                    }
                    val hidden = comment.hasSpoilerContent && comment.id !in revealed
                    Box(Modifier.weight(1f, fill = false)) {
                        Text(
                            comment.text,
                            style = redesignText(15.sp, color = RedesignColors.textPrimary, lineHeight = 23.sp),
                            modifier = Modifier.verticalScroll(rememberScrollState()).then(if (hidden) Modifier.blur(8.dp) else Modifier),
                        )
                        if (hidden) {
                            RedesignPillButton(
                                label = stringResource(Res.string.detail_reviews_reveal),
                                onClick = { revealed = revealed + comment.id },
                                modifier = Modifier.align(Alignment.Center).testTag("detail_review_reveal"),
                            )
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        RedesignIconButton("previous", { index = (index - 1).coerceAtLeast(0) }, vector = Icons.AutoMirrored.Rounded.KeyboardArrowLeft, size = 36.dp)
                        RedesignIconButton("next", { index = (index + 1).coerceAtMost(comments.lastIndex) }, vector = Icons.AutoMirrored.Rounded.KeyboardArrowRight, size = 36.dp, modifier = Modifier.testTag("detail_review_reader_next"))
                        if (canLike) LikeButton(comment) { onLike(comment) }
                        Spacer(Modifier.weight(1f))
                        comment.webUrl?.let { url ->
                            RedesignPillButton(
                                label = stringResource(Res.string.detail_reviews_open_on, providerName),
                                onClick = { uriHandler.openUri(url) },
                                leading = Icons.AutoMirrored.Rounded.OpenInNew,
                            )
                        }
                    }
                    Text(
                        stringResource(Res.string.detail_reviews_reader_hint),
                        style = redesignText(12.sp, color = RedesignColors.textMuted),
                        modifier = Modifier.testTag("detail_review_reader_hint"),
                    )
                }
            }
        }
    }
}

/**
 * Write a review: the show, a season or an episode; a spoiler flag; the source's word rules (at
 * least [minWords]; from [reviewWordThreshold] it posts as a review). It posts as the signed-in
 * account, then the list shows Newest.
 */
@Composable
private fun ReviewComposer(
    meta: MetaDetails,
    provider: TrackingCommentsProvider,
    episodes: List<Pair<Int, Int>>,
    currentEpisode: Pair<Int, Int>?,
    onClose: () -> Unit,
    onPosted: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val caps = provider.capabilities
    val isSeries = meta.type == "series" && episodes.isNotEmpty()
    val seasons = remember(episodes) { episodes.map { it.first }.filter { it > 0 }.distinct().sorted() }
    var target by remember { mutableStateOf(TrackingCommentScope()) }
    var text by remember { mutableStateOf("") }
    var spoiler by remember { mutableStateOf(false) }
    var posting by remember { mutableStateOf(false) }
    val words = TrackingComments.wordCount(text)
    val state = remember { MutableTransitionState(false) }
    LaunchedEffect(Unit) { state.targetState = true }
    LaunchedEffect(state.currentState, state.targetState) { if (!state.currentState && !state.targetState && state.isIdle) onClose() }
    val close = { state.targetState = false }
    OverlayEscapeHandler { close() }
    val focus = remember { FocusRequester() }
    LaunchedEffect(state.currentState) { if (state.currentState) runCatching { focus.requestFocus() } }
    Popup(onDismissRequest = close, properties = PopupProperties(focusable = true)) {
        AnimatedVisibility(
            visibleState = state,
            enter = fadeIn(tween(200)) + scaleIn(tween(220), initialScale = 0.97f),
            exit = fadeOut(tween(140)) + scaleOut(tween(160), targetScale = 0.98f),
        ) {
            Box(Modifier.fillMaxSize().background(Color(0x8C050607)), contentAlignment = Alignment.Center) {
                val shape = RoundedCornerShape(22.dp)
                Column(
                    Modifier.width(660.dp).clip(shape).background(LocalRedesignMenuSurface.current).border(1.dp, RedesignColors.hairline, shape)
                        .padding(22.dp).testTag("detail_review_composer"),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(Res.string.detail_reviews_write_title), style = redesignText(18.sp, FontWeight.Bold))
                            Text(stringResource(Res.string.detail_reviews_from, provider.providerId.displayName), style = redesignText(12.sp, color = RedesignColors.textMuted))
                        }
                        RedesignIconButton("close", close, vector = Icons.Rounded.Close, size = 36.dp)
                    }
                    if (isSeries) {
                        // Season and episode are the ones the board is on (its play target), else the first.
                        val current = currentEpisode?.takeIf { it in episodes }
                        val selectedSeason = target.season ?: current?.first ?: seasons.firstOrNull() ?: 1
                        val firstEpisode = current?.second?.takeIf { current.first == selectedSeason }
                            ?: episodes.firstOrNull { it.first == selectedSeason }?.second ?: 1
                        RedesignPillGroup(
                            options = listOf(0, 1, 2),
                            selected = when { target.episode != null -> 2; target.season != null -> 1; else -> 0 },
                            label = { option ->
                                when (option) {
                                    0 -> stringResource(Res.string.detail_reviews_scope_title)
                                    1 -> stringResource(Res.string.detail_reviews_scope_season, selectedSeason)
                                    else -> stringResource(Res.string.detail_reviews_scope_episode, selectedSeason, target.episode ?: firstEpisode)
                                }
                            },
                            onSelect = { option ->
                                target = when (option) {
                                    0 -> TrackingCommentScope()
                                    1 -> TrackingCommentScope(season = selectedSeason)
                                    else -> TrackingCommentScope(season = selectedSeason, episode = target.episode ?: firstEpisode)
                                }
                            },
                            optionTag = { "detail_review_scope_$it" },
                        )
                    }
                    Box(
                        Modifier.fillMaxWidth().heightIn(min = 160.dp).clip(RoundedCornerShape(14.dp)).background(RedesignColors.fill)
                            .border(1.dp, RedesignColors.hairline, RoundedCornerShape(14.dp)).padding(14.dp),
                    ) {
                        if (text.isEmpty()) Text(stringResource(Res.string.detail_reviews_write_placeholder), style = redesignText(14.sp, color = RedesignColors.textMuted))
                        BasicTextField(
                            value = text,
                            onValueChange = { text = it },
                            textStyle = redesignText(14.sp, color = RedesignColors.textPrimary, lineHeight = 20.sp),
                            cursorBrush = SolidColor(RedesignColors.textPrimary),
                            modifier = Modifier.fillMaxWidth().heightIn(min = 132.dp).focusRequester(focus).testTag("detail_review_text"),
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        RedesignPillButton(
                            label = stringResource(Res.string.detail_reviews_spoiler),
                            onClick = { spoiler = !spoiler },
                            active = spoiler,
                            modifier = Modifier.testTag("detail_review_spoiler"),
                        )
                        Text(
                            when {
                                words < caps.minWords -> stringResource(Res.string.detail_reviews_words_needed, words, caps.minWords)
                                words >= caps.reviewWordThreshold -> stringResource(Res.string.detail_reviews_words_review, words)
                                else -> stringResource(Res.string.detail_reviews_words_comment, words, caps.reviewWordThreshold)
                            },
                            style = redesignText(12.5.sp, color = if (words < caps.minWords) RedesignColors.textMuted else RedesignColors.textSecondary),
                            modifier = Modifier.weight(1f).testTag("detail_review_word_count"),
                        )
                        // Your score comes from Rate, for whatever the review is about.
                        val ratingTarget = remember(meta, target) {
                            val season = target.season
                            val episode = target.episode
                            when {
                                season != null && episode != null ->
                                    meta.videos.firstOrNull { it.season == season && it.episode == episode }?.let { meta.ratingReference(it) }
                                season != null -> meta.seasonRatingReference(season)
                                else -> meta.ratingReference()
                            } ?: meta.ratingReference()
                        }
                        val yourRating by rememberRatingState(ratingTarget)
                        yourRating.rating?.let { score ->
                            Text(
                                stringResource(Res.string.detail_reviews_your_rating, score.roundToInt()),
                                style = redesignText(12.5.sp, FontWeight.SemiBold, RedesignColors.textSecondary),
                                modifier = Modifier.clip(CircleShape).background(RedesignColors.fill).border(1.dp, RedesignColors.hairline, CircleShape)
                                    .padding(horizontal = 12.dp, vertical = 7.dp).testTag("detail_review_your_rating"),
                            )
                        }
                        RedesignPillButton(label = stringResource(Res.string.action_cancel), onClick = close)
                        RedesignPillButton(
                            label = stringResource(Res.string.detail_reviews_post),
                            primary = true,
                            onClick = {
                                if (posting || words < caps.minWords) return@RedesignPillButton
                                posting = true
                                scope.launch {
                                    val result = provider.post(meta, target, text.trim(), spoiler)
                                    posting = false
                                    when (result) {
                                        TrackingCommentPostResult.Posted -> {
                                            NuvioToastController.show(getString(Res.string.detail_reviews_posted))
                                            onPosted()
                                        }
                                        TrackingCommentPostResult.TooShort -> NuvioToastController.show(getString(Res.string.detail_reviews_too_short, caps.minWords))
                                        is TrackingCommentPostResult.Failed -> NuvioToastController.show(getString(Res.string.detail_reviews_post_failed))
                                    }
                                }
                            },
                            modifier = Modifier.testTag("detail_review_post"),
                        )
                    }
                }
            }
        }
    }
}
