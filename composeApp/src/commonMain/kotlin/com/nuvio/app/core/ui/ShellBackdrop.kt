package com.nuvio.app.core.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.compositionLocalOf
import com.nuvio.app.AppScreenTab
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.settings_shell_backdrop_ambient
import nuvio.composeapp.generated.resources.settings_shell_backdrop_mesh
import nuvio.composeapp.generated.resources.settings_shell_backdrop_solid
import nuvio.composeapp.generated.resources.settings_top_bar_background_always_solid
import nuvio.composeapp.generated.resources.settings_top_bar_background_frosted
import nuvio.composeapp.generated.resources.settings_top_bar_background_solid
import nuvio.composeapp.generated.resources.settings_top_bar_background_tinted
import nuvio.composeapp.generated.resources.settings_top_bar_background_translucent
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.graphics.luminance
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.nuvio.app.features.settings.ThemeSettingsRepository
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import org.jetbrains.compose.resources.StringResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Which artwork the ambient backdrop shows. Each screen sets [base] to one stable title
 * (Search: the top result). It deliberately does not follow poster hover; that felt dizzying.
 * Null falls back to the theme mesh.
 */
@Stable
class BackdropFocusController {
    var base by mutableStateOf<String?>(null)

    /** The colour behind the top bar on the shown artwork, for [TopBarBackground.Tinted]; null without artwork. */
    var tint by mutableStateOf<Color?>(null)

    val target: String? get() = base
}

/** What the redesigned desktop screens draw behind their content (Settings → Layout → Background). */
enum class ShellBackdropMode(val labelRes: StringResource) {
    /** Blurred artwork of the screen's lead title over the theme mesh. */
    Ambient(Res.string.settings_shell_backdrop_ambient),

    /** The theme mesh only; never artwork. */
    Mesh(Res.string.settings_shell_backdrop_mesh),

    /** No backdrop: screens keep the plain theme background. */
    Solid(Res.string.settings_shell_backdrop_solid),
    ;

    companion object {
        val Default = Ambient

        fun fromName(name: String?): ShellBackdropMode =
            entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: Default
    }
}

/** The user's choice, provided per nav entry in `MainAppContent.kt`. */
val LocalShellBackdropMode = compositionLocalOf { ShellBackdropMode.Default }

/**
 * Tabs whose screens are drawn for the shell backdrop. Other tabs keep the plain theme
 * background until they are redesigned; the shell fades the backdrop out for them.
 */
internal val ShellBackdropTabs = setOf(AppScreenTab.Home, AppScreenTab.Search, AppScreenTab.Calendar, AppScreenTab.Library)

/**
 * Start inset a backdrop screen applies to its content (not its backdrop) when the frame around
 * it lets the backdrop run under a frosted sidebar.
 */
val LocalBackdropContentStartInset = compositionLocalOf { 0.dp }

/** The shell's controller on the main tabs, or a screen's own; null means no backdrop is drawn. */
val LocalBackdropFocusController = staticCompositionLocalOf<BackdropFocusController?> { null }

private const val BackdropFocusDebounceMs = 250L

private val artworkScrim = Brush.verticalGradient(
    0f to Color.Black.copy(alpha = 0.40f),
    0.5f to Color.Black.copy(alpha = 0.55f),
    1f to Color.Black.copy(alpha = 0.74f),
)

/** Darker band behind the top bar and page header, whatever the artwork. */
private val chromeBand = Brush.verticalGradient(
    0f to Color.Black.copy(alpha = 0.35f),
    1f to Color.Transparent,
)
internal val BackdropChromeBandHeight = 220.dp

/**
 * Extra uniform dim for bright artwork, from its [artworkLuminance]: none at or below 0.25 (dark,
 * cinematic art looks exactly as before), rising linearly to [BackdropMaxBrightDim] at 0.7.
 */
internal fun backdropDimFor(luminance: Float): Float =
    ((luminance - 0.25f) / 0.45f).coerceIn(0f, 1f) * BackdropMaxBrightDim

internal const val BackdropMaxBrightDim = 0.35f

/**
 * Ambient screen background: a theme-tinted mesh, with a blurred copy of the focused title's
 * artwork crossfaded on top when there is one.
 */
@OptIn(FlowPreview::class)
@Composable
fun ShellBackdrop(
    controller: BackdropFocusController,
    modifier: Modifier = Modifier,
    showArtwork: Boolean = true,
) {
    val amoled = MaterialTheme.colorScheme.background == Color.Black
    var settledUrl by remember { mutableStateOf(controller.target) }
    LaunchedEffect(controller) {
        snapshotFlow { controller.target }
            .debounce(BackdropFocusDebounceMs)
            .distinctUntilChanged()
            .collect { settledUrl = it }
    }

    val loaded = rememberArtworkBackdrop(settledUrl.takeIf { showArtwork })
    // Hold the previous artwork while the next one loads, so focus changes crossfade art→art.
    var shown by remember { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(settledUrl, loaded, showArtwork) {
        if (settledUrl == null || !showArtwork) shown = null else if (loaded != null) shown = loaded
    }

    // The Tinted top bar takes the colour actually behind it: the top strip of the shown artwork.
    // (The image's dominant colour came out darker and of another hue than the backdrop.)
    val tintEnabled = ThemeSettingsRepository.topBarBackground.collectAsStateWithLifecycle().value == TopBarBackground.Tinted
    LaunchedEffect(shown, tintEnabled) {
        val bitmap = shown
        controller.tint = if (!tintEnabled || bitmap == null) null else withContext(Dispatchers.Default) {
            runCatching { backdropTopTint(bitmap) }.getOrNull()
        }
    }

    Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        ProfileMeshBackground(
            profileColor = MaterialTheme.colorScheme.primary,
            modifier = Modifier.graphicsLayer { alpha = if (amoled) 0.55f else 1f },
        )
        Crossfade(
            targetState = shown,
            animationSpec = tween(520, easing = FastOutSlowInEasing),
            label = "shell_backdrop_artwork",
        ) { bitmap ->
            if (bitmap != null) {
                Box(Modifier.fillMaxSize().graphicsLayer { alpha = if (amoled) 0.7f else 1f }) {
                    Image(
                        bitmap = bitmap,
                        contentDescription = null,
                        alignment = Alignment.TopCenter,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                    Box(Modifier.fillMaxSize().background(artworkScrim))
                    // Bright art gets more dim, so muted chrome labels stay readable over it. Each
                    // crossfading layer carries its own dim, so a title change never flashes.
                    val dim = remember(bitmap) { backdropDimFor(artworkLuminance(bitmap)) }
                    if (dim > 0f) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = dim)))
                    Box(Modifier.fillMaxWidth().height(BackdropChromeBandHeight).background(chromeBand))
                }
            }
        }
    }
}

/**
 * The Tinted top bar's colour for [bitmap]: the average of its top strip (where the bar sits),
 * under the backdrop's own top scrim (black at 40%), darkened further only if white bar text
 * would no longer stand out.
 */
internal fun backdropTopTint(bitmap: ImageBitmap): Color {
    val width = bitmap.width
    val height = (bitmap.height * 0.22f).toInt().coerceAtLeast(1)
    val pixels = IntArray(width * height)
    bitmap.readPixels(pixels, startX = 0, startY = 0, width = width, height = height)
    var r = 0L
    var g = 0L
    var b = 0L
    pixels.forEach { argb ->
        r += (argb shr 16) and 0xFF
        g += (argb shr 8) and 0xFF
        b += argb and 0xFF
    }
    val n = pixels.size.coerceAtLeast(1) * 255f
    return pinnedTintFromAverage(Color(r / n, g / n, b / n))
}

/** [average] under the backdrop's 40% top scrim, capped so white bar text keeps its contrast. */
internal fun pinnedTintFromAverage(average: Color): Color {
    val scrimmed = Color(average.red * 0.6f, average.green * 0.6f, average.blue * 0.6f)
    val luminance = scrimmed.luminance()
    val cap = 0.12f
    if (luminance <= cap) return scrimmed
    // Scale towards black until the luminance is at the cap, keeping the hue.
    var lo = 0f
    var hi = 1f
    repeat(16) {
        val mid = (lo + hi) / 2
        val l = Color(scrimmed.red * mid, scrimmed.green * mid, scrimmed.blue * mid).luminance()
        if (l > cap) hi = mid else lo = mid
    }
    return Color(scrimmed.red * lo, scrimmed.green * lo, scrimmed.blue * lo)
}

/** The near-black every pinned surface is built from. */
internal val PinnedBarInk = Color(0xFF0E0F12)

/**
 * How the desktop top bar, and every band pinned under it (Search's field, a service page's
 * header, Home's tabs), draw once content scrolls beneath them (Settings → Layout). One choice for
 * all of them, so the bar and the band under it are always the same surface.
 */
enum class TopBarBackground(val labelRes: StringResource) {
    /** Transparent at a page's top, solid near-black once scrolled. Nothing reads through. */
    Solid(Res.string.settings_top_bar_background_solid),

    /** Strong blur with a dark tint once scrolled. */
    Frosted(Res.string.settings_top_bar_background_frosted),

    /** Solid, in the colour of the backdrop artwork behind the bar; Solid where there is none. */
    Tinted(Res.string.settings_top_bar_background_tinted),

    /** Solid near-black even at a page's top. */
    AlwaysSolid(Res.string.settings_top_bar_background_always_solid),

    /** Light blur and a 72% dim (Search's original look); titles stay faintly visible. */
    Translucent(Res.string.settings_top_bar_background_translucent),
    ;

    companion object {
        val Default = Solid

        fun fromName(name: String?): TopBarBackground =
            entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: Default
    }
}

/**
 * The colour a pinned surface draws for [option]; [tint] is the backdrop's colour, if any.
 * [restsUnderBar] is false for a band that starts mid-page (Search's field): Always solid keeps
 * it clear until it pins, or it would be a solid stripe across the page.
 */
internal fun topBarSurfaceColor(
    option: TopBarBackground,
    scrolled: Boolean,
    tint: Color?,
    restsUnderBar: Boolean = true,
): Color = when {
    option == TopBarBackground.AlwaysSolid && (scrolled || restsUnderBar) -> PinnedBarInk
    !scrolled -> Color.Transparent
    option == TopBarBackground.Solid -> PinnedBarInk
    option == TopBarBackground.Frosted -> PinnedBarInk.copy(alpha = 0.82f)
    option == TopBarBackground.Tinted -> tint ?: PinnedBarInk
    else -> BackdropSurface.stickyHeader
}

/** Blur radius under a pinned surface; null when it is opaque or transparent. */
internal fun topBarSurfaceBlur(option: TopBarBackground, scrolled: Boolean): Dp? = when {
    !scrolled -> null
    option == TopBarBackground.Frosted -> 40.dp
    option == TopBarBackground.Translucent -> 24.dp
    else -> null
}

/**
 * The surface behind the top bar or a band pinned under it, filling its parent. Every pinned
 * surface draws through this, so they all follow [TopBarBackground] and change together.
 * [scrolled] means content has moved under the surface. [tint] is the page's backdrop colour.
 */
@Composable
internal fun BoxScope.PinnedBarBackground(
    scrolled: Boolean,
    hazeState: HazeState?,
    tint: Color? = null,
    /** Pages drawn without a backdrop pass their plain background: the surface is that, always. */
    plainBackground: Color? = null,
    /** False for a band that starts mid-page rather than right under the bar (see [topBarSurfaceColor]). */
    restsUnderBar: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val option by ThemeSettingsRepository.topBarBackground.collectAsStateWithLifecycle()
    val color by animateColorAsState(
        plainBackground ?: topBarSurfaceColor(option, scrolled, tint, restsUnderBar),
        tween(220),
        label = "pinned_bar_surface",
    )
    val blur = topBarSurfaceBlur(option, scrolled).takeIf { plainBackground == null }
    Box(
        modifier.matchParentSize()
            .then(if (blur != null && hazeState != null) Modifier.hazeEffect(state = hazeState) { blurRadius = blur } else Modifier)
            .background(color),
    )
    if (color.alpha > 0.5f) {
        Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(1.dp).background(RedesignColors.hairline))
    }
}

/** Translucent surface for sections that sit on the ambient backdrop. */
object BackdropSurface {
    val card = Color.White.copy(alpha = 0.045f)
    /** Dim over a Haze blur: scrolled content reads as soft colour, never as legible text, behind the bar. */
    val stickyHeader = Color(0xFF0E0F12).copy(alpha = 0.72f)
    /** For pinned bars with no blur behind them (Home's tabs): solid, so rows never read through the labels. */
    val stickyHeaderOpaque = Color(0xFF0E0F12)
}

fun Modifier.backdropCard(shape: Shape): Modifier = background(BackdropSurface.card, shape)

/**
 * One slice of a [BackdropSurface.card] spanning several LazyColumn items. Middle slices bleed
 * half of [itemGap] up and down so consecutive items read as one continuous card.
 */
fun Modifier.backdropCardSegment(
    isFirst: Boolean,
    isLast: Boolean,
    itemGap: androidx.compose.ui.unit.Dp,
    cornerRadius: androidx.compose.ui.unit.Dp = 16.dp,
): Modifier = drawBehind {
    val bleed = itemGap.toPx() / 2f
    val top = if (isFirst) 0f else -bleed
    val bottom = if (isLast) size.height else size.height + bleed
    val r = CornerRadius(cornerRadius.toPx())
    val path = Path().apply {
        addRoundRect(
            RoundRect(
                left = 0f, top = top, right = size.width, bottom = bottom,
                topLeftCornerRadius = if (isFirst) r else CornerRadius.Zero,
                topRightCornerRadius = if (isFirst) r else CornerRadius.Zero,
                bottomLeftCornerRadius = if (isLast) r else CornerRadius.Zero,
                bottomRightCornerRadius = if (isLast) r else CornerRadius.Zero,
            ),
        )
    }
    drawPath(path, BackdropSurface.card)
}
