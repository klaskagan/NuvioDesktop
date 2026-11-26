package com.nuvio.app.features.details.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.nuvio.app.OverlayEscapeHandler
import com.nuvio.app.core.ui.NuvioAsyncImage
import com.nuvio.app.core.ui.NuvioToastController
import com.nuvio.app.core.ui.RedesignColors
import com.nuvio.app.core.ui.RedesignIconButton
import com.nuvio.app.core.ui.RedesignPillButton
import com.nuvio.app.core.ui.RedesignPillGroup
import com.nuvio.app.core.ui.RedesignSwitchPill
import com.nuvio.app.core.ui.redesignText
import com.nuvio.app.features.gallery.GalleryFilter
import com.nuvio.app.features.gallery.GalleryImage
import com.nuvio.app.features.gallery.GalleryKind
import com.nuvio.app.features.gallery.galleryKinds
import com.nuvio.app.features.gallery.galleryVisible
import com.nuvio.app.features.gallery.saveGalleryImage
import kotlinx.coroutines.launch
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.detail_gallery_all
import nuvio.composeapp.generated.resources.detail_gallery_backdrops
import nuvio.composeapp.generated.resources.detail_gallery_behind_the_scenes
import nuvio.composeapp.generated.resources.detail_gallery_close
import nuvio.composeapp.generated.resources.detail_gallery_episode_stills
import nuvio.composeapp.generated.resources.detail_gallery_posters
import nuvio.composeapp.generated.resources.detail_gallery_save_failed
import nuvio.composeapp.generated.resources.detail_gallery_save_image
import nuvio.composeapp.generated.resources.detail_gallery_saved
import nuvio.composeapp.generated.resources.detail_gallery_set_background
import nuvio.composeapp.generated.resources.detail_gallery_stills
import nuvio.composeapp.generated.resources.detail_gallery_text_free
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

private val GalleryShape = RoundedCornerShape(12.dp)
private const val GalleryColumns = 4

@Composable
private fun galleryKindLabel(kind: GalleryKind?): String = stringResource(
    when (kind) {
        null -> Res.string.detail_gallery_all
        GalleryKind.Backdrop -> Res.string.detail_gallery_backdrops
        GalleryKind.EpisodeStill -> Res.string.detail_gallery_episode_stills
        GalleryKind.Poster -> Res.string.detail_gallery_posters
        GalleryKind.Still -> Res.string.detail_gallery_stills
        GalleryKind.BehindTheScenes -> Res.string.detail_gallery_behind_the_scenes
    },
)

/**
 * The board's Gallery tab: All / Backdrops / Episode stills / Posters (and Stills / Behind the
 * scenes with a Kinopoisk key), "Text-free only" on by default, and a grid. A tile opens the
 * full-window viewer; Esc returns to the same spot.
 */
@Composable
internal fun BoardGallery(
    images: List<GalleryImage>,
    title: String,
    onSetBackground: ((String) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    var filter by remember { mutableStateOf(GalleryFilter()) }
    val kinds = remember(images) { galleryKinds(images) }
    val visible = remember(images, filter) { galleryVisible(images, filter) }
    var viewerIndex by rememberSaveable { mutableStateOf<Int?>(null) }
    Column(modifier.fillMaxWidth().testTag("detail_board_gallery"), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // The tab already names it: filters on the left, Text-free only on the right (design frame "Gallery").
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (kinds.size > 1) {
                RedesignPillGroup(
                    options = listOf<GalleryKind?>(null) + kinds,
                    selected = filter.kind,
                    label = { galleryKindLabel(it) },
                    onSelect = { filter = filter.copy(kind = it) },
                    optionTag = { "detail_gallery_filter_${it?.name ?: "All"}" },
                    // Counts follow Text-free only, so a pill never promises images it hides.
                    trailing = { option -> galleryVisible(images, filter.copy(kind = option)).size.toString() },
                )
            }
            Spacer(Modifier.weight(1f))
            RedesignSwitchPill(
                label = stringResource(Res.string.detail_gallery_text_free),
                checked = filter.textFreeOnly,
                onCheckedChange = { filter = filter.copy(textFreeOnly = it) },
                modifier = Modifier.testTag("detail_gallery_text_free"),
            )
        }
        AnimatedContent(targetState = visible, transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(160)) }, label = "gallery_grid") { shown ->
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val gap = 12.dp
                val tile = (maxWidth - gap * (GalleryColumns - 1)) / GalleryColumns
                Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                    shown.chunked(GalleryColumns).forEachIndexed { rowIndex, row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                            row.forEachIndexed { column, image ->
                                GalleryTile(image, Modifier.width(tile)) { viewerIndex = rowIndex * GalleryColumns + column }
                            }
                        }
                    }
                }
            }
        }
    }
    viewerIndex?.let { start ->
        GalleryViewer(images = visible, start = start.coerceIn(0, (visible.size - 1).coerceAtLeast(0)), title = title, onSetBackground = onSetBackground, onClose = { viewerIndex = null })
    }
}

@Composable
private fun GalleryTile(image: GalleryImage, modifier: Modifier, onOpen: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val border by animateColorAsState(if (hovered) RedesignColors.hairlineFocused else RedesignColors.hairline, tween(160), label = "gallery_tile")
    Box(
        modifier.aspectRatio(if (image.kind == GalleryKind.Poster) 2f / 3f else 16f / 9f).clip(GalleryShape).background(RedesignColors.fill)
            .border(1.dp, border, GalleryShape)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onOpen)
            .testTag("detail_gallery_tile"),
    ) {
        NuvioAsyncImage(model = image.thumbnail, contentDescription = image.caption, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    }
}

/** Full window: the image, ← → (keys too), a filmstrip, the caption with its source, and actions. */
@Composable
internal fun GalleryViewer(
    images: List<GalleryImage>,
    start: Int,
    title: String,
    onSetBackground: ((String) -> Unit)?,
    onClose: () -> Unit,
) {
    if (images.isEmpty()) return
    var index by remember { mutableIntStateOf(start) }
    val state = remember { MutableTransitionState(false) }
    LaunchedEffect(Unit) { state.targetState = true }
    LaunchedEffect(state.currentState, state.targetState) { if (!state.currentState && !state.targetState && state.isIdle) onClose() }
    val close = { state.targetState = false }
    OverlayEscapeHandler { close() }
    val scope = rememberCoroutineScope()
    val focus = remember { FocusRequester() }
    // Focus once the viewer is on screen, so ← → and Esc reach it.
    LaunchedEffect(state.currentState) { if (state.currentState) runCatching { focus.requestFocus() } }
    val strip = rememberLazyListState()
    LaunchedEffect(index) { strip.animateScrollToItem((index - 3).coerceAtLeast(0)) }
    Popup(onDismissRequest = close, properties = PopupProperties(focusable = true)) {
        AnimatedVisibility(visibleState = state, enter = fadeIn(tween(200)), exit = fadeOut(tween(160))) {
            Box(
                Modifier.fillMaxSize().background(Color(0xF2050607))
                    .onPreviewKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        when (event.key) {
                            Key.DirectionLeft -> { index = (index - 1).coerceAtLeast(0); true }
                            Key.DirectionRight -> { index = (index + 1).coerceAtMost(images.lastIndex); true }
                            Key.Escape -> { close(); true }
                            else -> false
                        }
                    }
                    .focusRequester(focus)
                    .focusable()
                    .testTag("detail_gallery_viewer"),
            ) {
                val image = images[index]
                AnimatedContent(targetState = image, transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(160)) }, label = "gallery_image",
                    modifier = Modifier.fillMaxSize().padding(start = 72.dp, end = 72.dp, top = 64.dp, bottom = 150.dp)) { shown ->
                    NuvioAsyncImage(model = shown.url, contentDescription = shown.caption, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                }
                Row(Modifier.align(Alignment.TopEnd).padding(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (onSetBackground != null) {
                        RedesignPillButton(
                            label = stringResource(Res.string.detail_gallery_set_background),
                            onClick = { onSetBackground(image.url) },
                            leading = Icons.Rounded.Wallpaper,
                            modifier = Modifier.testTag("detail_gallery_set_background"),
                        )
                    }
                    RedesignPillButton(
                        label = stringResource(Res.string.detail_gallery_save_image),
                        onClick = {
                            scope.launch {
                                val saved = saveGalleryImage(image.url, "$title ${index + 1}")
                                NuvioToastController.show(
                                    if (saved != null) getString(Res.string.detail_gallery_saved, saved) else getString(Res.string.detail_gallery_save_failed),
                                )
                            }
                        },
                        leading = Icons.Rounded.Download,
                        modifier = Modifier.testTag("detail_gallery_save"),
                    )
                    RedesignIconButton(stringResource(Res.string.detail_gallery_close), close, vector = Icons.Rounded.Close, size = 40.dp, modifier = Modifier.testTag("detail_gallery_close"))
                }
                if (index > 0) {
                    RedesignIconButton("‹", { index-- }, vector = Icons.AutoMirrored.Rounded.KeyboardArrowLeft, size = 44.dp, modifier = Modifier.align(Alignment.CenterStart).padding(16.dp))
                }
                if (index < images.lastIndex) {
                    RedesignIconButton("›", { index++ }, vector = Icons.AutoMirrored.Rounded.KeyboardArrowRight, size = 44.dp, modifier = Modifier.align(Alignment.CenterEnd).padding(16.dp).testTag("detail_gallery_next"))
                }
                Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(bottom = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        listOfNotNull(image.caption, galleryKindLabel(image.kind), image.source.label, "${index + 1} / ${images.size}").joinToString("  ·  "),
                        style = redesignText(13.sp, FontWeight.Medium, RedesignColors.textSecondary),
                        modifier = Modifier.testTag("detail_gallery_caption"),
                    )
                    Spacer(Modifier.height(10.dp))
                    LazyRow(state = strip, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(horizontal = 24.dp)) {
                        itemsIndexed(images, key = { i, it -> "$i:${it.url}" }) { i, item ->
                            val selected = i == index
                            val ring by animateColorAsState(if (selected) RedesignColors.textPrimary else Color.Transparent, tween(160), label = "strip_ring")
                            Box(
                                Modifier.size(width = if (item.kind == GalleryKind.Poster) 44.dp else 104.dp, height = 62.dp).clip(RoundedCornerShape(8.dp))
                                    .border(2.dp, ring, RoundedCornerShape(8.dp))
                                    .clickable(role = Role.Button) { index = i },
                            ) {
                                NuvioAsyncImage(model = item.thumbnail, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                            }
                        }
                    }
                }
            }
        }
    }
}
