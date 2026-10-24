package com.nuvio.app.features.catalog

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nuvio.app.core.ui.NuvioBackButton
import com.nuvio.app.features.home.SeasonalTheme
import com.nuvio.app.isDesktop
import com.nuvio.app.navigation.LocalUseNativeNavigation
import org.jetbrains.compose.resources.painterResource

internal fun CatalogTarget.seasonalThemeOrNull(): SeasonalTheme? =
    (this as? CatalogTarget.Seasonal)?.themeId?.let { id -> SeasonalTheme.entries.firstOrNull { it.id == id } }

/** Full-bleed art shares the introductory grid item's height and scroll position. */
@Composable
internal fun SeasonalCatalogBackdrop(
    theme: SeasonalTheme,
    minHeight: Dp,
    measuredHeightPx: Int,
    gridState: LazyGridState,
) {
    val artwork = theme.backgroundArtwork ?: return
    CatalogHeroBackdrop(minHeight, measuredHeightPx, {
        if (gridState.firstVisibleItemIndex == 0) -gridState.firstVisibleItemScrollOffset.toFloat() else -it
    }, "seasonal_catalog_background_${theme.id}", theme.tint) {
        Image(painterResource(artwork), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    }
}

/** Shared by seasonal and configured collection art; the image follows its measured introduction. */
@Composable
internal fun CatalogHeroBackdrop(
    minHeight: Dp,
    measuredHeightPx: Int,
    scrollTranslation: (Float) -> Float,
    tag: String,
    tint: Color = MaterialTheme.colorScheme.primary,
    artwork: @Composable () -> Unit,
) {
    val height = with(LocalDensity.current) { measuredHeightPx.toDp() }.takeIf { measuredHeightPx > 0 } ?: minHeight
    Box(Modifier.fillMaxWidth().wrapContentHeight(Alignment.Top, unbounded = true).height(height)
        .graphicsLayer { translationY = scrollTranslation(size.height) }.testTag(tag)) {
        artwork()
        CatalogHeroReadability(Modifier.matchParentSize(), tint)
    }
}

@Composable
internal fun CatalogHeroReadability(modifier: Modifier, tint: Color = MaterialTheme.colorScheme.primary, fadeTop: Boolean = false) {
    val background = MaterialTheme.colorScheme.background
    Box(modifier.background(Brush.horizontalGradient(listOf(
        Color.Black.copy(alpha = 0.45f), tint.copy(alpha = 0.12f), Color.Black.copy(alpha = 0.24f),
    ))))
    Box(modifier.background(Brush.verticalGradient(
        0f to background.copy(alpha = if (fadeTop) 1f else 0.15f),
        0.55f to background.copy(alpha = 0.15f),
        1f to background,
    )))
}

@Composable
internal fun SeasonalCatalogIntroduction(title: String, subtitle: String, minHeight: Dp, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().heightIn(min = minHeight).testTag("seasonal_catalog_intro")
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(top = if (isDesktop) 100.dp else 92.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.Bottom,
    ) {
        Text(title, style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.onBackground)
        if (subtitle.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
    }
}

@Composable
internal fun SeasonalCatalogBackControl(pageHorizontalPadding: Dp, onBack: () -> Unit) {
    if (LocalUseNativeNavigation.current) return
    Box(
        Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(
            MaterialTheme.colorScheme.background.copy(alpha = 0.65f), Color.Transparent,
        ))).windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = pageHorizontalPadding)
            .padding(top = if (isDesktop) 32.dp else 52.dp, bottom = 12.dp),
    ) {
        NuvioBackButton(
            onClick = onBack,
            modifier = Modifier.size(if (isDesktop) 48.dp else 40.dp).testTag("catalog_back"),
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            iconSize = 24.dp,
        )
    }
}
