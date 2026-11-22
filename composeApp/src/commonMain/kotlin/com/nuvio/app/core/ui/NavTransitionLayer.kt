package com.nuvio.app.core.ui

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.nuvio.app.isDesktop

/**
 * Screen-to-screen motion for the desktop redesign. Navigation entries switch instantly; each
 * screen animates its own layers, so the app sidebar both entries draw never moves.
 */
enum class NavTransitionStyle {
    /** Home hero carousel: layers fade as they drift (background 5.5%, content 18%), both screens at once. */
    HeroParallax,

    /** The current screen slides fully out to the left while the new one slides in from the right. */
    FullSlide,
}

/**
 * TEMPORARY: lets the user compare both styles in the running app (toggle on the person page).
 * Remove the toggle and keep the chosen style once decided.
 */
object NavTransitionPreview {
    var style by mutableStateOf(NavTransitionStyle.HeroParallax)
}

/** How far a layer drifts in [NavTransitionStyle.HeroParallax]; the same values as Home's hero. */
enum class NavLayer(val heroParallax: Float) {
    Background(0.055f),
    Content(0.18f),
}

/** Where a screen sits: underneath (Search) leaves to the left; above (Person) arrives from the right. */
enum class NavSide { Underneath, Above }

/** The nav entry's AnimatedVisibilityScope; null outside a NavDisplay (tests, previews) means no motion. */
val LocalNavLayerScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

/** Home hero's pager spring: no bounce, medium-low stiffness. */
private fun <T> navSpring() = spring<T>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)

/**
 * Animates this layer when its screen enters or leaves. Underneath screens leave to (and return
 * from) the left; above screens arrive from (and leave to) the right, so push and pop mirror.
 */
@Composable
fun Modifier.navTransitionLayer(layer: NavLayer, side: NavSide): Modifier {
    if (!isDesktop) return this
    val scope = LocalNavLayerScope.current ?: return this
    val direction = if (side == NavSide.Above) 1 else -1
    val (enter, exit) = when (NavTransitionPreview.style) {
        NavTransitionStyle.HeroParallax -> {
            val drift = layer.heroParallax
            Pair<EnterTransition, ExitTransition>(
                slideInHorizontally(navSpring()) { (it * drift * direction).toInt() } + fadeIn(navSpring()),
                slideOutHorizontally(navSpring()) { (it * drift * direction).toInt() } + fadeOut(navSpring()),
            )
        }
        NavTransitionStyle.FullSlide -> Pair<EnterTransition, ExitTransition>(
            slideInHorizontally(navSpring()) { it * direction },
            slideOutHorizontally(navSpring()) { it * direction },
        )
    }
    return then(with(scope) { Modifier.animateEnterExit(enter = enter, exit = exit) })
}
