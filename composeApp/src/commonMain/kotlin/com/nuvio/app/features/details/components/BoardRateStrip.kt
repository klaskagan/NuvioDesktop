package com.nuvio.app.features.details.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nuvio.app.OverlayEscapeHandler
import com.nuvio.app.core.ui.NuvioToastController
import com.nuvio.app.core.ui.RedesignColors
import com.nuvio.app.core.ui.RedesignOnLight
import com.nuvio.app.core.ui.redesignText
import com.nuvio.app.features.tracking.TrackingFavoriteState
import com.nuvio.app.features.tracking.TrackingFavorites
import com.nuvio.app.features.tracking.TrackingMediaReference
import com.nuvio.app.features.tracking.TrackingRatingState
import com.nuvio.app.features.tracking.TrackingRatings
import kotlinx.coroutines.launch
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.action_cancel
import nuvio.composeapp.generated.resources.action_remove
import nuvio.composeapp.generated.resources.action_save
import nuvio.composeapp.generated.resources.rating_failed
import nuvio.composeapp.generated.resources.rating_favorite
import nuvio.composeapp.generated.resources.rating_rate
import nuvio.composeapp.generated.resources.rating_word_1
import nuvio.composeapp.generated.resources.rating_word_10
import nuvio.composeapp.generated.resources.rating_word_2
import nuvio.composeapp.generated.resources.rating_word_3
import nuvio.composeapp.generated.resources.rating_word_4
import nuvio.composeapp.generated.resources.rating_word_5
import nuvio.composeapp.generated.resources.rating_word_6
import nuvio.composeapp.generated.resources.rating_word_7
import nuvio.composeapp.generated.resources.rating_word_8
import nuvio.composeapp.generated.resources.rating_word_9
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

private val ratingWords: List<StringResource> = listOf(
    Res.string.rating_word_1, Res.string.rating_word_2, Res.string.rating_word_3, Res.string.rating_word_4,
    Res.string.rating_word_5, Res.string.rating_word_6, Res.string.rating_word_7, Res.string.rating_word_8,
    Res.string.rating_word_9, Res.string.rating_word_10,
)

private val RatingGold = Color(0xFFF5C518)
private val FavoriteRed = Color(0xFFFF6B6B)

/**
 * The value a key press picks in the strip: ← → step, 1–9 pick that score and 0 picks 10. Null for
 * keys the strip ignores.
 */
internal fun rateStripKeyValue(key: Key, current: Int): Int? = when (key) {
    Key.DirectionLeft -> (current - 1).coerceAtLeast(1)
    Key.DirectionRight -> (current + 1).coerceAtMost(TrackingRatings.MAX)
    Key.Zero, Key.NumPad0 -> 10
    Key.One, Key.NumPad1 -> 1
    Key.Two, Key.NumPad2 -> 2
    Key.Three, Key.NumPad3 -> 3
    Key.Four, Key.NumPad4 -> 4
    Key.Five, Key.NumPad5 -> 5
    Key.Six, Key.NumPad6 -> 6
    Key.Seven, Key.NumPad7 -> 7
    Key.Eight, Key.NumPad8 -> 8
    Key.Nine, Key.NumPad9 -> 9
    else -> null
}

/**
 * Rate, option B: inline in the action row. Idle it looks like the other buttons; hovering or
 * clicking it opens a 10-star strip with a live label, the tracker's favorite ♥ and ✓ / ✕. A click
 * on a star sets it; Enter or ✓ saves, Esc or ✕ cancels, ← → and 1–9/0 pick. Once saved the strip
 * folds into a "★ 8 ♥" chip that reopens it. [onOpenChange] lets the row collapse its other
 * buttons to icons so nothing reflows.
 */
@Composable
internal fun BoardRateControl(
    media: TrackingMediaReference,
    rating: TrackingRatingState,
    favorite: TrackingFavoriteState,
    onOpenChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 44.dp,
    /** Always the strip (an episode's context menu), never the idle button. */
    alwaysOpen: Boolean = false,
) {
    val scope = rememberCoroutineScope()
    var pinned by remember { mutableStateOf(false) }
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val saved = rating.rating?.roundToInt()?.takeIf { it > 0 }
    var picked by remember(saved) { mutableIntStateOf(saved ?: 0) }
    var preview by remember { mutableIntStateOf(0) }
    var saving by remember { mutableStateOf(false) }
    // Hover opens the strip on the way in; Cancel or Esc closes it even while the pointer is still over it.
    var hoverOpen by remember { mutableStateOf(false) }
    LaunchedEffect(hovered) { hoverOpen = hovered }
    val open = alwaysOpen || pinned || hoverOpen || saving
    LaunchedEffect(open) { onOpenChange(open) }
    LaunchedEffect(media, favorite.provider) { if (favorite.isAvailable) TrackingFavorites.refresh(media) }
    val settle = remember { Animatable(1f) }
    val pulse = remember { Animatable(1f) }
    val providerName = rating.provider?.displayName.orEmpty()

    fun close() {
        pinned = false
        hoverOpen = false
        preview = 0
        picked = saved ?: 0
    }

    fun save(value: Int?) {
        if (!rating.isAvailable || saving) return
        saving = true
        scope.launch {
            val ok = try {
                TrackingRatings.rate(media, value)
            } finally {
                saving = false
            }
            if (!ok) NuvioToastController.show(getString(Res.string.rating_failed, providerName))
            pinned = false
            hoverOpen = false
            preview = 0
        }
    }

    fun pick(value: Int) {
        // A pick keeps the strip open after the pointer leaves it.
        pinned = true
        picked = value
        // A pick wins over the star under the pointer until the pointer moves to another star.
        preview = 0
        scope.launch {
            settle.snapTo(1.25f)
            settle.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMediumLow))
        }
        scope.launch {
            pulse.snapTo(1.2f)
            pulse.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessLow))
        }
    }

    if (pinned) OverlayEscapeHandler { close() }
    val focus = remember { FocusRequester() }
    LaunchedEffect(pinned) { if (pinned) runCatching { focus.requestFocus() } }

    Box(
        modifier.hoverable(interaction)
            // Above the focus target, so keys reach it while the strip or one of its stars has focus.
            .onPreviewKeyEvent { event ->
                if (!open || event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.Enter, Key.NumPadEnter -> { if (picked > 0) save(picked); true }
                    Key.Escape -> { close(); true }
                    else -> rateStripKeyValue(event.key, picked.coerceAtLeast(1))?.let { pick(it); true } ?: false
                }
            }
            .focusRequester(focus)
            .focusable()
            .testTag("detail_board_rate"),
    ) {
        AnimatedContent(
            targetState = when {
                open -> 2
                saved != null || favorite.isFavorite -> 1
                else -> 0
            },
            transitionSpec = {
                (fadeIn(tween(180)) + scaleIn(tween(220), initialScale = 0.96f)) togetherWith
                    (fadeOut(tween(120)) + scaleOut(tween(160), targetScale = 0.98f)) using
                    SizeTransform(clip = false) { _, _ -> tween(260, easing = FastOutSlowInEasing) }
            },
            label = "rate_control",
        ) { state ->
            when (state) {
                0 -> RatePill(height, onClick = { pinned = true }) {
                    Icon(Icons.Rounded.StarBorder, contentDescription = null, tint = RedesignColors.textPrimary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(Res.string.rating_rate), style = redesignText(14.sp, FontWeight.SemiBold), maxLines = 1)
                }
                1 -> RatePill(height, onClick = { pinned = true }, tag = "detail_board_rate_chip") {
                    if (saved != null) {
                        Icon(Icons.Rounded.Star, contentDescription = null, tint = RatingGold, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(saved.toString(), style = redesignText(15.sp, FontWeight.Bold), maxLines = 1)
                    }
                    if (favorite.isFavorite) {
                        if (saved != null) Spacer(Modifier.width(8.dp))
                        Icon(Icons.Rounded.Favorite, contentDescription = null, tint = FavoriteRed, modifier = Modifier.size(16.dp))
                    }
                }
                else -> {
                    val shown = preview.takeIf { it > 0 } ?: picked
                    Row(
                        Modifier.height(height).clip(CircleShape).background(Color(0xCC101114)).border(1.dp, RedesignColors.hairlineFocused, CircleShape)
                            .padding(start = 12.dp, end = 6.dp)
                            .testTag("detail_board_rate_strip"),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (rating.isAvailable) {
                            (1..TrackingRatings.MAX).forEach { value ->
                                val lit = value <= shown
                                val starInteraction = remember { MutableInteractionSource() }
                                val starHovered by starInteraction.collectIsHoveredAsState()
                                LaunchedEffect(starHovered) { if (starHovered) preview = value else if (preview == value) preview = 0 }
                                val tint by animateColorAsState(if (lit) RatingGold else RedesignColors.textMuted, tween(120), label = "rate_star")
                                Icon(
                                    if (lit) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                                    contentDescription = null,
                                    tint = tint,
                                    modifier = Modifier.size(22.dp)
                                        .graphicsLayer { val s = if (value == picked) settle.value else 1f; scaleX = s; scaleY = s }
                                        .hoverable(starInteraction)
                                        .clickable(interactionSource = starInteraction, indication = null, role = Role.Button) { pick(value) }
                                        .semantics { contentDescription = "$value" }
                                        .testTag("detail_board_rate_star_$value"),
                                )
                            }
                            Text(
                                if (shown > 0) "$shown · ${stringResource(ratingWords[shown - 1])}" else "–",
                                style = redesignText(13.sp, FontWeight.SemiBold, if (shown > 0) RedesignColors.textPrimary else RedesignColors.textMuted),
                                maxLines = 1,
                                modifier = Modifier.padding(start = 10.dp, end = 6.dp).widthIn(min = 96.dp).testTag("detail_board_rate_label"),
                            )
                        }
                        if (favorite.isAvailable) {
                            StripButton(
                                icon = if (favorite.isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                description = stringResource(Res.string.rating_favorite, favorite.provider?.displayName.orEmpty()),
                                tint = if (favorite.isFavorite) FavoriteRed else RedesignColors.textSecondary,
                                tag = "detail_board_rate_favorite",
                            ) {
                                scope.launch {
                                    if (!TrackingFavorites.setFavorite(media, !favorite.isFavorite)) {
                                        NuvioToastController.show(getString(Res.string.rating_failed, favorite.provider?.displayName.orEmpty()))
                                    }
                                }
                            }
                        }
                        if (rating.isAvailable && saved != null) {
                            StripButton(Icons.Rounded.Close, stringResource(Res.string.action_remove), RedesignColors.textSecondary, "detail_board_rate_remove") { save(null) }
                        } else {
                            StripButton(Icons.Rounded.Close, stringResource(Res.string.action_cancel), RedesignColors.textSecondary, "detail_board_rate_cancel") { close() }
                        }
                        if (rating.isAvailable) {
                            Box(Modifier.graphicsLayer { scaleX = pulse.value; scaleY = pulse.value }) {
                                StripButton(
                                    Icons.Rounded.Check,
                                    stringResource(Res.string.action_save),
                                    if (picked > 0 && picked != saved) RedesignOnLight else RedesignColors.textMuted,
                                    "detail_board_rate_save",
                                    filled = picked > 0 && picked != saved,
                                ) { if (picked > 0 && picked != saved) save(picked) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RatePill(height: Dp, onClick: () -> Unit, tag: String = "detail_board_rate_idle", content: @Composable () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val fill by animateColorAsState(if (hovered) RedesignColors.fillStrong else Color(0x33101114), tween(180), label = "rate_pill")
    Row(
        Modifier.height(height).clip(CircleShape).background(fill).border(1.dp, RedesignColors.hairline, CircleShape)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
            .testTag(tag)
            .padding(start = 14.dp, end = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) { content() }
}

@Composable
private fun StripButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    tint: Color,
    tag: String,
    filled: Boolean = false,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val fill by animateColorAsState(
        when {
            filled -> RedesignColors.textPrimary
            hovered -> RedesignColors.fillStrong
            else -> Color.Transparent
        },
        tween(160),
        label = "strip_button",
    )
    Box(
        Modifier.padding(start = 4.dp).size(34.dp).clip(CircleShape).background(fill)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description }
            .testTag(tag),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
    }
}

/** The rating and favorite of [media], kept current. */
@Composable
internal fun rememberFavoriteState(media: TrackingMediaReference): TrackingFavoriteState {
    val flow = remember(media) { TrackingFavorites.observe(media) }
    return flow.collectAsState(remember(media) { TrackingFavorites.current(media) }).value
}
