package com.nuvio.app.features.home.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.OverlayEscapeHandler
import com.nuvio.app.core.ui.RedesignColors
import com.nuvio.app.core.ui.LocalRedesignMenuSurface
import com.nuvio.app.core.ui.redesignText
import com.nuvio.app.features.home.HomeCatalogSettingsRepository
import com.nuvio.app.features.home.HomeRowStyle
import com.nuvio.app.features.home.HomeRowStyleChoice
import com.nuvio.app.features.home.HomeRowStyleNeed
import com.nuvio.app.features.home.homeRowStyleChoices
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.input.ImeAction

@Composable
internal fun homeRowStyleLabel(style: HomeRowStyle): String =
    if (style.isLandscapeSize) {
        stringResource(
            Res.string.home_row_style_landscape_size,
            stringResource(Res.string.home_row_style_landscape),
            landscapeSizeLabel(style),
        )
    } else {
        stringResource(
            when (style) {
                HomeRowStyle.Automatic -> Res.string.home_row_style_automatic
                HomeRowStyle.Posters -> Res.string.home_row_style_posters
                HomeRowStyle.BannerWithTitle -> Res.string.home_row_style_banner_with_title
                HomeRowStyle.NumberedRanking -> Res.string.home_row_style_numbered_ranking
                HomeRowStyle.Round -> Res.string.home_row_style_round
                HomeRowStyle.CompactList -> Res.string.home_row_style_compact_list
                HomeRowStyle.Mosaic -> Res.string.home_row_style_mosaic
                HomeRowStyle.Spotlight -> Res.string.home_row_style_spotlight
                HomeRowStyle.LogoWall -> Res.string.home_row_style_logo_wall
                HomeRowStyle.CalendarStrip -> Res.string.home_row_style_calendar_strip
                HomeRowStyle.InfoCards -> Res.string.home_row_style_info_cards
                HomeRowStyle.EditorialBand -> Res.string.home_row_style_editorial_band
                HomeRowStyle.CollageBanner -> Res.string.home_row_style_collage_banner
                else -> error("Landscape sizes have their own labels")
            },
        )
    }

@Composable
private fun landscapeSizeLabel(style: HomeRowStyle): String = stringResource(
    when (style) {
        HomeRowStyle.LandscapeSmall -> Res.string.home_row_style_landscape_small
        HomeRowStyle.LandscapeMedium -> Res.string.home_row_style_landscape_medium
        HomeRowStyle.LandscapeLarge -> Res.string.home_row_style_landscape_large
        else -> error("Expected a Landscape size")
    },
)

@Composable
private fun choiceLabel(choice: HomeRowStyleChoice): String =
    if (choice == HomeRowStyleChoice.Landscape) stringResource(Res.string.home_row_style_landscape)
    else homeRowStyleLabel(choice.applied)

private val LandscapeSizes = listOf(HomeRowStyle.LandscapeSmall, HomeRowStyle.LandscapeMedium, HomeRowStyle.LandscapeLarge)
private val PanelWidth = 540.dp
private val PanelShape = RoundedCornerShape(16.dp)
private val CellShape = RoundedCornerShape(12.dp)
private val ScreenMargin = 16.dp
private const val SCRIM_ALPHA = 0.38f

/**
 * The Row style panel ("Row style · picker"): a frosted grid of sketches opened beside [anchor]
 * (root bounds of the poster, tile or button it came from; centred near the top when unknown),
 * over a dimmed page. Picking a style saves it and closes; Landscape stays open with its inline
 * Small / Medium / Large switch ("Row style · Landscape size"). Esc and a click outside close it.
 * [title] defaults to the row's name. [rowSupplies] says what the row can fill, so styles it can't are left out.
 */
@Composable
internal fun HomeRowStylePicker(
    shelfKey: String,
    onDismiss: () -> Unit,
    anchor: Rect? = null,
    title: String? = null,
    rowSupplies: Set<HomeRowStyleNeed>? = null,
    dim: Boolean = true,
) {
    val settings by HomeCatalogSettingsRepository.uiState.collectAsStateWithLifecycle()
    // Rows publish what they can fill as they draw; a row not on screen falls back to its kind.
    val supplies = rowSupplies ?: HomeRowSupplies.of(shelfKey) ?: run {
        val isCollection = settings.items.firstOrNull { it.key == shelfKey }?.isCollection == true
        if (isCollection) emptySet() else setOf(HomeRowStyleNeed.Titles)
    }
    val choices = remember(shelfKey, supplies) { homeRowStyleChoices(shelfKey, supplies) }
    if (choices.isEmpty()) {
        LaunchedEffect(shelfKey) { onDismiss() }
        return
    }
    val state = settings
    val currentStyle = state.rowStyles[shelfKey] ?: HomeRowStyle.Automatic
    // "Row style · Editorial description": typed live into the row, saved on Enter or close.
    var description by remember(shelfKey) { mutableStateOf(state.rowDescriptions[shelfKey].orEmpty()) }
    val rowTitle = title ?: state.items.firstOrNull { it.key == shelfKey }?.let { it.customTitle.ifBlank { it.defaultTitle } }

    val visible = remember { MutableTransitionState(false) }
    LaunchedEffect(Unit) { visible.targetState = true }
    val dismiss = {
        if (currentStyle == HomeRowStyle.EditorialBand) HomeCatalogSettingsRepository.setRowDescription(shelfKey, description)
        RowDescriptionDrafts.clear(shelfKey)
        visible.targetState = false
    }
    LaunchedEffect(visible.currentState, visible.targetState) {
        if (!visible.currentState && !visible.targetState && visible.isIdle) onDismiss()
    }
    OverlayEscapeHandler { dismiss() }

    val scrim by animateFloatAsState(
        if (dim && visible.targetState) SCRIM_ALPHA else 0f,
        tween(if (visible.targetState) 160 else 120),
        label = "row_style_scrim",
    )
    Popup(popupPositionProvider = WindowOriginProvider, onDismissRequest = dismiss, properties = PopupProperties(focusable = true)) {
        AnchoredPanelLayout(
            anchor = anchor,
            modifier = Modifier.fillMaxSize()
                .background(Color.Black.copy(alpha = scrim))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = dismiss),
        ) {
            AnimatedVisibility(
                visibleState = visible,
                enter = fadeIn(tween(160)) + scaleIn(tween(200), initialScale = 0.96f, transformOrigin = TransformOrigin(0.5f, 0f)),
                exit = fadeOut(tween(120)) + scaleOut(tween(140), targetScale = 0.97f, transformOrigin = TransformOrigin(0.5f, 0f)),
            ) {
                RowStylePanel(
                    title = rowTitle,
                    choices = choices,
                    currentStyle = currentStyle,
                    onPick = { choice ->
                        if (choice == HomeRowStyleChoice.Landscape) {
                            // Stays open: the size switch slides in under the grid.
                            if (!currentStyle.isLandscapeSize) HomeCatalogSettingsRepository.setRowStyle(shelfKey, choice.applied)
                        } else if (choice == HomeRowStyleChoice.EditorialBand) {
                            // Stays open: the Row description field slides in under the grid.
                            HomeCatalogSettingsRepository.setRowStyle(shelfKey, choice.applied)
                        } else {
                            HomeCatalogSettingsRepository.setRowStyle(shelfKey, choice.applied)
                            dismiss()
                        }
                    },
                    onSize = { HomeCatalogSettingsRepository.setRowStyle(shelfKey, it) },
                    description = description,
                    onDescription = {
                        description = it
                        RowDescriptionDrafts.update(shelfKey, it)
                    },
                    onDescriptionDone = dismiss,
                )
            }
        }
    }
}

/** A popup that covers the window from its origin, so the scrim fills it and [anchor] is window space. */
private object WindowOriginProvider : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize) =
        IntOffset.Zero
}

/**
 * Places the panel under [anchor]'s left edge; end-aligned to it when that would cross the
 * window's right edge, and above it when there's no room below.
 */
@Composable
private fun AnchoredPanelLayout(anchor: Rect?, modifier: Modifier, content: @Composable () -> Unit) {
    Layout(content = content, modifier = modifier) { measurables, constraints ->
        val margin = ScreenMargin.roundToPx()
        val gap = 8.dp.roundToPx()
        val maxWidth = (constraints.maxWidth - 2 * margin).coerceAtLeast(0)
        val panelWidth = PanelWidth.roundToPx().coerceAtMost(maxWidth)
        val placeable = measurables.firstOrNull()?.measure(
            Constraints(minWidth = 0, maxWidth = panelWidth, minHeight = 0, maxHeight = (constraints.maxHeight - 2 * margin).coerceAtLeast(0)),
        )
        layout(constraints.maxWidth, constraints.maxHeight) {
            placeable ?: return@layout
            val w = placeable.width
            val h = placeable.height
            val maxX = (constraints.maxWidth - w - margin).coerceAtLeast(margin)
            val maxY = (constraints.maxHeight - h - margin).coerceAtLeast(margin)
            val (x, y) = if (anchor == null) {
                (constraints.maxWidth - w) / 2 to constraints.maxHeight / 4
            } else {
                val left = anchor.left.roundToInt()
                val x = if (left + w <= constraints.maxWidth - margin) left else anchor.right.roundToInt() - w
                val below = anchor.bottom.roundToInt() + gap
                val y = if (below + h <= constraints.maxHeight - margin) below else anchor.top.roundToInt() - gap - h
                x to y
            }
            placeable.place(x.coerceIn(margin, maxX), y.coerceIn(margin, maxY))
        }
    }
}

@Composable
private fun RowStylePanel(
    title: String?,
    choices: List<HomeRowStyleChoice>,
    currentStyle: HomeRowStyle,
    onPick: (HomeRowStyleChoice) -> Unit,
    onSize: (HomeRowStyle) -> Unit,
    description: String = "",
    onDescription: (String) -> Unit = {},
    onDescriptionDone: () -> Unit = {},
) {
    Column(
        Modifier.width(PanelWidth)
            .shadow(24.dp, PanelShape, ambientColor = Color.Black, spotColor = Color.Black)
            .clip(PanelShape).background(LocalRedesignMenuSurface.current)
            .border(1.dp, RedesignColors.hairline, PanelShape)
            // Clicks inside the panel stay in it rather than reaching the scrim.
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {})
            .padding(6.dp)
            .testTag("home_row_style_picker"),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(Res.string.home_row_style).uppercase(),
                style = redesignText(11.sp, FontWeight.Medium, RedesignColors.textMuted, letterSpacing = 0.1.em),
                maxLines = 1,
            )
            Spacer(Modifier.weight(1f))
            if (title != null) {
                Text(
                    title,
                    style = redesignText(11.sp, FontWeight.SemiBold, RedesignColors.textSecondary),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 16.dp),
                )
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(bottom = 2.dp)) {
            choices.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.forEach { choice ->
                        StyleCell(choice, selected = choice.matches(currentStyle), onClick = { onPick(choice) }, modifier = Modifier.weight(1f))
                    }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
        AnimatedVisibility(
            visible = currentStyle.isLandscapeSize && HomeRowStyleChoice.Landscape in choices,
            enter = expandVertically(tween(220, easing = FastOutSlowInEasing), expandFrom = Alignment.Top) + fadeIn(tween(180, delayMillis = 40)),
            exit = shrinkVertically(tween(180, easing = FastOutSlowInEasing), shrinkTowards = Alignment.Top) + fadeOut(tween(120)),
        ) {
            // Keeps the last size on screen while the switch collapses away.
            LandscapeSizeSwitch(
                selected = currentStyle.takeIf { it.isLandscapeSize } ?: HomeRowStyle.LandscapeMedium,
                onSize = onSize,
            )
        }
        AnimatedVisibility(
            visible = currentStyle == HomeRowStyle.EditorialBand && HomeRowStyleChoice.EditorialBand in choices,
            enter = expandVertically(tween(220, easing = FastOutSlowInEasing), expandFrom = Alignment.Top) + fadeIn(tween(180, delayMillis = 40)),
            exit = shrinkVertically(tween(180, easing = FastOutSlowInEasing), shrinkTowards = Alignment.Top) + fadeOut(tween(120)),
        ) {
            RowDescriptionField(description, onDescription, onDescriptionDone)
        }
    }
}

/** "Row style · Editorial description": the band's line, focused when it slides in. */
@Composable
private fun RowDescriptionField(value: String, onValue: (String) -> Unit, onDone: () -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val shape = RoundedCornerShape(12.dp)
    Column(Modifier.padding(start = 6.dp, end = 6.dp, top = 6.dp, bottom = 4.dp).fillMaxWidth()) {
        Text(
            stringResource(Res.string.home_row_description).uppercase(),
            style = redesignText(11.sp, FontWeight.Medium, RedesignColors.textMuted, letterSpacing = 0.1.em),
            modifier = Modifier.padding(start = 6.dp, bottom = 6.dp),
        )
        BasicTextField(
            value = value,
            onValueChange = { onValue(it.replace('\n', ' ')) },
            singleLine = true,
            textStyle = redesignText(13.sp, FontWeight.Normal, RedesignColors.textPrimary),
            cursorBrush = SolidColor(RedesignColors.textPrimary),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onDone() }),
            modifier = Modifier.fillMaxWidth().focusRequester(focus)
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown && (event.key == Key.Enter || event.key == Key.NumPadEnter)) {
                        onDone(); true
                    } else false
                }
                .testTag("home_row_description_field"),
            decorationBox = { inner ->
                Box(
                    Modifier.fillMaxWidth().height(38.dp).clip(shape).background(RedesignColors.fill)
                        .border(1.dp, RedesignColors.hairlineFocused, shape).padding(horizontal = 12.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (value.isEmpty()) {
                        Text(
                            stringResource(Res.string.home_row_description_placeholder),
                            style = redesignText(13.sp, FontWeight.Normal, RedesignColors.textMuted),
                            maxLines = 1,
                        )
                    }
                    inner()
                }
            },
        )
        Text(
            stringResource(Res.string.home_row_description_hint),
            style = redesignText(11.5.sp, FontWeight.Normal, RedesignColors.textMuted),
            modifier = Modifier.padding(start = 6.dp, top = 6.dp),
        )
    }
}

@Composable
private fun StyleCell(choice: HomeRowStyleChoice, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val focused by interaction.collectIsFocusedAsState()
    val fill by animateColorAsState(
        Color.White.copy(alpha = when {
            hovered || focused -> 0.08f
            selected -> 0.07f
            else -> 0.03f
        }),
        tween(140),
        label = "row_style_cell_fill",
    )
    val outline by animateColorAsState(if (selected) RedesignColors.hairlineFocused else Color.Transparent, tween(180), label = "row_style_cell_outline")
    val ink by animateColorAsState(if (selected || hovered) RedesignColors.textPrimary else RedesignColors.textSecondary, tween(140), label = "row_style_cell_ink")
    val tick by animateFloatAsState(if (selected) 1f else 0f, tween(160), label = "row_style_cell_tick")
    Column(
        modifier.clip(CellShape).background(fill).border(1.dp, outline, CellShape)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
            .semantics { this.selected = selected }
            .testTag("home_row_style_option_${choice.id}")
            .padding(start = 10.dp, end = 10.dp, top = 9.dp, bottom = 8.dp),
    ) {
        Box(Modifier.fillMaxWidth().height(44.dp), contentAlignment = Alignment.BottomStart) { StyleSketch(choice) }
        Row(Modifier.fillMaxWidth().padding(top = 7.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                choiceLabel(choice),
                style = redesignText(12.5.sp, FontWeight.SemiBold, ink),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Icon(
                Icons.Rounded.Check,
                contentDescription = null,
                tint = RedesignColors.textPrimary,
                modifier = Modifier.size(14.dp).graphicsLayer { alpha = tick },
            )
        }
    }
}

private val SketchFill = Color.White.copy(alpha = 0.22f)
private val SketchAccent = Color.White.copy(alpha = 0.34f)
private val SketchShape = RoundedCornerShape(4.dp)
private val EditorialSketchTint = Color(0xFFE0B48A).copy(alpha = 0.22f)

@Composable
private fun Bar(width: Dp, height: Dp, accent: Boolean = false, shape: Shape = SketchShape) {
    Box(Modifier.size(width, height).clip(shape).background(if (accent) SketchAccent else SketchFill))
}

/** The row's shape in miniature, from the frame's `sk()` sketches. */
@Composable
private fun StyleSketch(choice: HomeRowStyleChoice) {
    val gap = Arrangement.spacedBy(4.dp)
    when (choice) {
        HomeRowStyleChoice.Automatic -> Row(horizontalArrangement = gap, verticalAlignment = Alignment.Bottom) {
            Bar(52.dp, 30.dp, accent = true)
            repeat(3) { Bar(20.dp, 30.dp) }
        }
        HomeRowStyleChoice.Posters -> Row(horizontalArrangement = gap, verticalAlignment = Alignment.Bottom) {
            repeat(5) { Bar(20.dp, 30.dp) }
        }
        HomeRowStyleChoice.Landscape -> Row(horizontalArrangement = gap, verticalAlignment = Alignment.Bottom) {
            repeat(3) { Bar(40.dp, 23.dp) }
        }
        HomeRowStyleChoice.BannerWithTitle -> Row(horizontalArrangement = gap, verticalAlignment = Alignment.Bottom) {
            repeat(2) {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Bar(52.dp, 24.dp)
                    Bar(34.dp, 4.dp, accent = true)
                }
            }
        }
        HomeRowStyleChoice.NumberedRanking -> Row(verticalAlignment = Alignment.Bottom) {
            (1..3).forEach { rank ->
                Text(
                    rank.toString(),
                    style = redesignText(26.sp, FontWeight.ExtraBold, Color.White.copy(alpha = 0.45f), lineHeight = 0.8.em)
                        .copy(drawStyle = Stroke(width = 1.5f)),
                    // The frame's margin-right:-4px: the bar overlaps the numeral's last 4 dp.
                    modifier = Modifier.layout { measurable, constraints ->
                        val numeral = measurable.measure(constraints)
                        layout((numeral.width - 4.dp.roundToPx()).coerceAtLeast(0), numeral.height) { numeral.place(0, 0) }
                    },
                )
                Bar(18.dp, 28.dp)
            }
        }
        HomeRowStyleChoice.Round -> Row(Modifier.fillMaxHeight(), horizontalArrangement = gap, verticalAlignment = Alignment.CenterVertically) {
            repeat(4) { Bar(24.dp, 24.dp, shape = CircleShape) }
        }
        HomeRowStyleChoice.CompactList -> Column(Modifier.fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically)) {
            repeat(3) {
                Row(horizontalArrangement = gap, verticalAlignment = Alignment.CenterVertically) {
                    Bar(18.dp, 10.dp)
                    Bar(30.dp, 4.dp, accent = true)
                    Spacer(Modifier.width(2.dp))
                    Bar(18.dp, 10.dp)
                    Bar(30.dp, 4.dp, accent = true)
                }
            }
        }
        HomeRowStyleChoice.Mosaic -> Row(Modifier.fillMaxHeight(), horizontalArrangement = gap, verticalAlignment = Alignment.CenterVertically) {
            Bar(26.dp, 38.dp)
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                repeat(2) { Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) { repeat(2) { Bar(12.dp, 17.dp) } } }
            }
            Bar(26.dp, 38.dp)
        }
        HomeRowStyleChoice.Spotlight -> Row(Modifier.fillMaxHeight(), horizontalArrangement = gap, verticalAlignment = Alignment.CenterVertically) {
            Bar(62.dp, 35.dp, accent = true)
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                repeat(2) { Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) { repeat(2) { Bar(22.dp, 12.dp) } } }
            }
        }
        HomeRowStyleChoice.LogoWall -> Row(Modifier.fillMaxHeight(), horizontalArrangement = gap, verticalAlignment = Alignment.CenterVertically) {
            repeat(2) {
                Box(Modifier.size(44.dp, 25.dp).clip(SketchShape).background(Color.White.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(24.dp, 5.dp).clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = 0.6f)))
                }
            }
        }
        HomeRowStyleChoice.CalendarStrip -> Row(horizontalArrangement = gap, verticalAlignment = Alignment.Bottom) {
            repeat(2) {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Bar(18.dp, 4.dp, accent = true)
                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) { repeat(2) { Bar(28.dp, 16.dp) } }
                }
            }
        }
        HomeRowStyleChoice.InfoCards -> Row(Modifier.fillMaxHeight(), horizontalArrangement = gap, verticalAlignment = Alignment.CenterVertically) {
            repeat(2) {
                Row(
                    Modifier.clip(RoundedCornerShape(5.dp)).background(Color.White.copy(alpha = 0.08f)).padding(2.dp),
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Bar(16.dp, 24.dp)
                    Column(Modifier.padding(top = 2.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Bar(22.dp, 4.dp, accent = true)
                        Bar(22.dp, 3.dp)
                        Bar(16.dp, 3.dp)
                    }
                }
            }
        }
        HomeRowStyleChoice.EditorialBand -> Row(Modifier.fillMaxHeight(), verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier.clip(RoundedCornerShape(6.dp)).background(EditorialSketchTint).padding(horizontal = 5.dp, vertical = 4.dp),
                horizontalArrangement = gap,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Bar(24.dp, 5.dp, accent = true)
                    Bar(20.dp, 3.dp)
                }
                repeat(3) { Bar(14.dp, 22.dp) }
            }
        }
        HomeRowStyleChoice.CollageBanner -> Row(Modifier.fillMaxHeight(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(110.dp, 32.dp).clip(RoundedCornerShape(6.dp)).background(Color.White.copy(alpha = 0.1f))) {
                repeat(3) { k ->
                    Box(Modifier.offset(x = (40 + k * 22).dp, y = 2.dp).graphicsLayer { rotationZ = -12f }) { Bar(18.dp, 28.dp) }
                }
                Box(Modifier.offset(x = 6.dp, y = 13.dp)) { Bar(28.dp, 6.dp, accent = true) }
            }
        }
    }
}

/** "Row style · Landscape size": Small / Medium / Large under the grid; the white thumb slides. */
@Composable
private fun LandscapeSizeSwitch(selected: HomeRowStyle, onSize: (HomeRowStyle) -> Unit) {
    val pill = RoundedCornerShape(999.dp)
    BoxWithConstraints(
        Modifier.padding(start = 6.dp, end = 6.dp, top = 6.dp, bottom = 4.dp).fillMaxWidth()
            .clip(pill).background(RedesignColors.fill).border(1.dp, RedesignColors.hairline, pill)
            .padding(3.dp),
    ) {
        val segment = maxWidth / LandscapeSizes.size
        val index = LandscapeSizes.indexOf(selected).coerceAtLeast(0)
        val thumbX by animateDpAsState(segment * index, tween(220, easing = FastOutSlowInEasing), label = "row_style_size_thumb")
        Box(Modifier.offset(x = thumbX).width(segment).height(26.dp).clip(pill).background(Color(0xFFF3F4F6)))
        Row(Modifier.fillMaxWidth()) {
            LandscapeSizes.forEach { size ->
                val isSelected = size == selected
                val interaction = remember { MutableInteractionSource() }
                val hovered by interaction.collectIsHoveredAsState()
                val ink by animateColorAsState(
                    when {
                        isSelected -> Color(0xFF0A0B0D)
                        hovered -> RedesignColors.textPrimary
                        else -> RedesignColors.textSecondary
                    },
                    tween(160),
                    label = "row_style_size_ink",
                )
                Box(
                    Modifier.weight(1f).height(26.dp).clip(pill)
                        .hoverable(interaction)
                        .clickable(interactionSource = interaction, indication = null, role = Role.Tab) { onSize(size) }
                        .semantics { this.selected = isSelected }
                        .testTag("home_row_style_size_${size.id}"),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(landscapeSizeLabel(size), style = redesignText(12.5.sp, FontWeight.SemiBold, ink), maxLines = 1)
                }
            }
        }
    }
}
