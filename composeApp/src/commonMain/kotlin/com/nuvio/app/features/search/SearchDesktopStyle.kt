package com.nuvio.app.features.search

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.nuvio.app.core.ui.LocalNuvioTypeScale
import com.nuvio.app.core.ui.NuvioTypeScale
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.inter_variable
import org.jetbrains.compose.resources.Font

/** Colors for the desktop Search redesign, tuned for text on the ambient backdrop. */
internal object SearchColors {
    val textPrimary = Color(0xFFF3F4F6)
    val textSecondary = Color(0xFFB8BEC5)
    val textMuted = Color(0xFF8F959C)
    val fill = Color.White.copy(alpha = 0.07f)
    val fillStrong = Color.White.copy(alpha = 0.12f)
    val hairline = Color.White.copy(alpha = 0.10f)
    val hairlineFocused = Color.White.copy(alpha = 0.55f)
    val imdb = Color(0xFFF5C518)
}

@Composable
private fun interFontFamily(): FontFamily {
    val normal = Font(Res.font.inter_variable, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400)))
    val medium = Font(Res.font.inter_variable, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500)))
    val semiBold = Font(Res.font.inter_variable, FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600)))
    val bold = Font(Res.font.inter_variable, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700)))
    val extraBold = Font(Res.font.inter_variable, FontWeight.ExtraBold, variationSettings = FontVariation.Settings(FontVariation.weight(800)))
    return remember(normal, medium, semiBold, bold, extraBold) {
        FontFamily(normal, medium, semiBold, bold, extraBold)
    }
}

private fun TextStyle.inter(family: FontFamily) = copy(fontFamily = family)

private fun Typography.withFontFamily(family: FontFamily) = Typography(
    displayLarge = displayLarge.inter(family), displayMedium = displayMedium.inter(family), displaySmall = displaySmall.inter(family),
    headlineLarge = headlineLarge.inter(family), headlineMedium = headlineMedium.inter(family), headlineSmall = headlineSmall.inter(family),
    titleLarge = titleLarge.inter(family), titleMedium = titleMedium.inter(family), titleSmall = titleSmall.inter(family),
    bodyLarge = bodyLarge.inter(family), bodyMedium = bodyMedium.inter(family), bodySmall = bodySmall.inter(family),
    labelLarge = labelLarge.inter(family), labelMedium = labelMedium.inter(family), labelSmall = labelSmall.inter(family),
)

private fun NuvioTypeScale.withFontFamily(family: FontFamily) = copy(
    labelXs = labelXs.inter(family), labelSm = labelSm.inter(family), bodySm = bodySm.inter(family),
    bodyMd = bodyMd.inter(family), bodyLg = bodyLg.inter(family), titleSm = titleSm.inter(family),
    titleMd = titleMd.inter(family), titleLg = titleLg.inter(family), displaySm = displaySm.inter(family),
    displayMd = displayMd.inter(family),
)

/** Renders [content] with Inter in place of the app font, so reused components (grids, chips) match. */
@Composable
internal fun SearchDesktopTypography(enabled: Boolean = true, content: @Composable () -> Unit) {
    if (!enabled) {
        content()
        return
    }
    val family = interFontFamily()
    val typography = MaterialTheme.typography
    val scale = LocalNuvioTypeScale.current
    val searchTypography = remember(typography, family) { typography.withFontFamily(family) }
    val searchScale = remember(scale, family) { scale.withFontFamily(family) }
    MaterialTheme(colorScheme = MaterialTheme.colorScheme, shapes = MaterialTheme.shapes, typography = searchTypography) {
        CompositionLocalProvider(LocalNuvioTypeScale provides searchScale, content = content)
    }
}

/** Search type ramp: hero 34, section 20, card title 14, meta 12/13, body 15. */
@Composable
internal fun searchText(
    size: TextUnit,
    weight: FontWeight = FontWeight.Normal,
    color: Color = SearchColors.textPrimary,
    lineHeight: TextUnit = TextUnit.Unspecified,
    letterSpacing: TextUnit = TextUnit.Unspecified,
): TextStyle = MaterialTheme.typography.bodyLarge.copy(
    fontSize = size,
    fontWeight = weight,
    color = color,
    lineHeight = lineHeight,
    letterSpacing = letterSpacing,
)

internal val SearchHeroTitleSize = 34.sp
internal val SearchTightTracking = (-0.02).em
