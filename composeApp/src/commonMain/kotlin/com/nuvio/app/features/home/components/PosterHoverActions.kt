package com.nuvio.app.features.home.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.nuvio.app.core.ui.NuvioDesktopAnimation
import com.nuvio.app.core.ui.NuvioAsyncImage
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.core.ui.NuvioTokens
import com.nuvio.app.core.ui.RedesignTooltip
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.home.stableKey
import com.nuvio.app.features.library.LibraryRepository
import com.nuvio.app.features.library.PendingTrackingMembershipRemoval
import com.nuvio.app.features.library.TrackingMembershipRemovalConfirmationHost
import com.nuvio.app.features.library.executeTrackingMembershipOperation
import com.nuvio.app.features.library.showTrackingMembershipRewriteFeedback
import com.nuvio.app.features.library.toLibraryItem
import com.nuvio.app.features.tracking.TrackingMembershipApplyResult
import com.nuvio.app.features.tracking.TrackingProviderId
import com.nuvio.app.features.watching.application.WatchingActions
import kotlinx.coroutines.launch
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.hero_add_to_library
import nuvio.composeapp.generated.resources.hero_mark_unwatched
import nuvio.composeapp.generated.resources.hero_mark_watched
import nuvio.composeapp.generated.resources.hero_remove_from_library
import nuvio.composeapp.generated.resources.home_poster_play_trailer
import nuvio.composeapp.generated.resources.home_quick_look
import nuvio.composeapp.generated.resources.tracking_lists_update_failed
import org.jetbrains.compose.resources.stringResource

/** Drawn inside a hovered poster's artwork: the white ring and the action strip ("Poster hover · actions"). */
internal typealias PosterHoverOverlay = @Composable BoxScope.(shape: Shape) -> Unit

private const val StripFadeMillis = 220
private const val RingFadeMillis = 160
private val StripScrimHeight = 64.dp

/**
 * Library membership for [item] with the tracker removal confirmation, shared by the strip and
 * Quick look. [content] gets whether it is saved and the toggle.
 */
@Composable
internal fun WithPosterLibraryToggle(
    item: MetaPreview,
    content: @Composable (isSaved: Boolean, toggle: () -> Unit) -> Unit,
) {
    val library by remember {
        LibraryRepository.ensureLoaded()
        LibraryRepository.uiState
    }.collectAsStateWithLifecycle()
    val isSaved = remember(library, item.id, item.type) { LibraryRepository.isSaved(item.id, item.type) }
    var pending by remember(item.type, item.id) { mutableStateOf<PendingTrackingMembershipRemoval?>(null) }
    val scope = rememberCoroutineScope()
    val failedMessage = stringResource(Res.string.tracking_lists_update_failed)
    val toggle: () -> Unit = remember(item, failedMessage) {
        {
            val libraryItem = item.toLibraryItem(savedAtEpochMs = 0L)
            scope.launch {
                val toggleMembership: suspend (Set<TrackingProviderId>) -> TrackingMembershipApplyResult = { confirmed ->
                    LibraryRepository.toggleSaved(item = libraryItem, confirmedRemovalProviders = confirmed)
                }
                val complete: suspend (TrackingMembershipApplyResult) -> Unit = { showTrackingMembershipRewriteFeedback(it) }
                val fail: suspend (Throwable) -> Unit = { error ->
                    com.nuvio.app.core.ui.NuvioToastController.show(error.message ?: failedMessage)
                }
                executeTrackingMembershipOperation(
                    operation = { toggleMembership(emptySet()) },
                    onSuccess = { result ->
                        if (result.requiresRemovalConfirmation) {
                            pending = PendingTrackingMembershipRemoval(
                                itemTitle = libraryItem.name,
                                confirmations = result.requiredRemovalConfirmations,
                                retry = toggleMembership,
                                onApplied = complete,
                                onFailure = fail,
                            )
                        } else {
                            complete(result)
                        }
                    },
                    onFailure = fail,
                )
            }
            Unit
        }
    }
    content(isSaved, toggle)
    pending?.let { TrackingMembershipRemovalConfirmationHost(pending = it, onPendingChange = { next -> pending = next }) }
}

@Composable
internal fun rememberPosterWatchedToggle(item: MetaPreview): () -> Unit {
    val scope = rememberCoroutineScope()
    return remember(item) { { scope.launch { WatchingActions.togglePosterWatched(item) }; Unit } }
}

/**
 * The hovered poster's ring and its action strip: Play trailer, Watched, Library and Quick look.
 * On a 2:3 poster the strip sits on its foot over a dark fade; on a wide card (landscape rows,
 * whose title is drawn at the bottom left) the buttons sit in the top corner instead, so the title
 * stays readable, as on the Seasonal spotlight cards.
 */
@Composable
internal fun BoxScope.PosterActionStrip(
    shape: Shape,
    ringVisible: Boolean,
    stripVisible: Boolean,
    isWatched: Boolean,
    isSaved: Boolean,
    onPlayTrailer: (() -> Unit)?,
    onWatched: () -> Unit,
    onSave: () -> Unit,
    onQuickLook: () -> Unit,
) {
    val ring by animateFloatAsState(if (ringVisible) 1f else 0f, tween(RingFadeMillis), label = "poster_ring")
    BoxWithConstraints(Modifier.matchParentSize()) {
        val wide = maxWidth > maxHeight
        val buttons: @Composable (Dp, Dp) -> Unit = { size, gap ->
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                if (onPlayTrailer != null) {
                    StripButton(Icons.Rounded.PlayArrow, stringResource(Res.string.home_poster_play_trailer), size, onPlayTrailer, "poster_strip_trailer")
                }
                StripButton(
                    if (isWatched) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                    stringResource(if (isWatched) Res.string.hero_mark_unwatched else Res.string.hero_mark_watched),
                    size, onWatched, "poster_strip_watched",
                )
                StripButton(
                    if (isSaved) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                    stringResource(if (isSaved) Res.string.hero_remove_from_library else Res.string.hero_add_to_library),
                    size, onSave, "poster_strip_library",
                )
                StripButton(Icons.Outlined.Info, stringResource(Res.string.home_quick_look), size, onQuickLook, "poster_strip_quick_look")
            }
        }
        if (wide) {
            AnimatedVisibility(
                visible = stripVisible,
                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).testTag("poster_action_strip"),
                enter = fadeIn(tween(StripFadeMillis, easing = NuvioTokens.Motion.decelerate)),
                exit = fadeOut(tween(StripFadeMillis - 60)),
            ) {
                buttons(28.dp, 5.dp)
            }
        } else {
            AnimatedVisibility(
                visible = stripVisible,
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
                enter = fadeIn(tween(StripFadeMillis, easing = NuvioTokens.Motion.decelerate)) +
                    slideInVertically(tween(StripFadeMillis, easing = NuvioTokens.Motion.decelerate)) { it / 5 },
                exit = fadeOut(tween(StripFadeMillis - 60)) + slideOutVertically(tween(StripFadeMillis - 60)) { it / 6 },
            ) {
                Box(
                    Modifier.fillMaxWidth().height(StripScrimHeight)
                        .background(Brush.verticalGradient(0f to Color.Transparent, 0.45f to StripScrim.copy(alpha = 0.92f), 1f to StripScrim.copy(alpha = 0.92f)))
                        .testTag("poster_action_strip"),
                ) {
                    // Four 30 dp buttons fit a 146 dp poster; narrower posters (a small poster size) get smaller ones.
                    val narrow = this@BoxWithConstraints.maxWidth < 140.dp
                    Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp)) {
                        buttons(if (narrow) 26.dp else 30.dp, if (narrow) 4.dp else 6.dp)
                    }
                }
            }
        }
    }
    if (ring > 0f) {
        Box(Modifier.matchParentSize().border(2.dp, Color.White.copy(alpha = 0.95f * ring), shape))
    }
}

private val StripScrim = Color(0xFF060607)

@Composable
internal fun StripButton(icon: ImageVector, label: String, size: Dp, onClick: () -> Unit, tag: String) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val fill by animateColorAsState(
        if (hovered) Color.White.copy(alpha = 0.22f) else Color(0xFF0A0A0B).copy(alpha = 0.66f),
        tween(150),
        label = "strip_button_fill",
    )
    Box(
        Modifier.size(size).clip(CircleShape).background(fill)
            .border(1.dp, Color.White.copy(alpha = 0.18f), CircleShape)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label }
            .testTag(tag),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
        RedesignTooltip(label, hovered)
    }
}

/**
 * The backdrop behind Quick look, the inline card and the trailer view. One image is chosen and
 * kept: the row's own backdrop when it has one, otherwise the details' backdrop once they arrive.
 * It fades in once over the panel colour and is never swapped afterwards (a blurred-poster
 * stand-in that was then replaced, and a later switch to the details' image, read as unstable).
 */
@Composable
internal fun HoverBackdrop(item: MetaPreview, details: com.nuvio.app.features.details.MetaDetails?, modifier: Modifier) {
    var chosen by remember(item.stableKey()) { mutableStateOf(item.banner ?: item.landscapePoster) }
    if (chosen.isNullOrBlank()) details?.background?.takeIf { it.isNotBlank() }?.let { chosen = it }
    val backdrop = chosen
    var loaded by remember(backdrop) { mutableStateOf(false) }
    val alpha by animateFloatAsState(if (loaded) 1f else 0f, tween(BackdropFadeMillis), label = "hover_backdrop")
    Box(modifier.background(Color(0xFF151618))) {
        if (!backdrop.isNullOrBlank()) {
            NuvioAsyncImage(
                model = backdrop,
                contentDescription = item.name,
                modifier = Modifier.fillMaxSize().graphicsLayer { this.alpha = alpha },
                contentScale = ContentScale.Crop,
                alignment = Alignment.TopCenter,
                desktopAnimation = NuvioDesktopAnimation.Always,
                onSuccess = { loaded = true },
            )
        }
    }
}

private const val BackdropFadeMillis = 320
