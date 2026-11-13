package com.nuvio.app.features.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.Font
import org.jetbrains.compose.resources.stringResource

// Native Legacy's Dynamic Home, in its 1920-wide coordinate space.
internal class NumberedRankingLayout(viewportWidth: Dp) {
    private val scale = viewportWidth.value / 1920f
    val inset = (104f * scale).dp
    val posterWidth = (212f * scale).dp
    val posterHeight = (322f * scale).dp
    val posterCorner = (24f * scale).dp
    val numeralLane = (138f * scale).dp
    val gap = (24f * scale).dp
    val numeralClearance = (14f * scale).dp
    val numeralBody = (260f * scale).dp
    val headerBody = (33f * scale).dp
    val focusRing = (4f * scale).dp
    val viewAllArrowBody = (56f * scale).dp
    val viewAllLabelGap = (14f * scale).dp
    val viewAllBorder = (2f * scale).dp
    val bottomGap = (32f * scale).dp
}

@Composable
internal fun legacyRankingFont(): FontFamily = FontFamily(Font(Res.font.inter_display_bold))

@Composable
internal fun NumberedRankingItem(
    rank: Int,
    layout: NumberedRankingLayout,
    font: FontFamily,
    poster: @Composable () -> Unit,
) {
    val measurer = rememberTextMeasurer()
    val body = with(LocalDensity.current) { layout.numeralBody.toSp() }
    val rankLabel = stringResource(Res.string.home_row_rank, rank)
    Box(
        Modifier.size(layout.numeralLane + layout.posterWidth, layout.posterHeight)
            .semantics { contentDescription = rankLabel }
            .drawWithCache {
                val numeral = measurer.measure(
                    rank.toString(),
                    style = TextStyle(fontFamily = font, fontSize = body),
                    softWrap = false,
                )
                val exposedFraction = 0.8f
                val lane = layout.numeralLane.toPx()
                val usableWidth = lane + layout.gap.toPx() - layout.numeralClearance.toPx()
                val fit = (usableWidth / (exposedFraction * numeral.size.width)).coerceAtMost(1f)
                val baseline = Offset(lane - numeral.size.width * fit * exposedFraction, size.height)
                onDrawBehind {
                    scale(fit, fit, pivot = baseline) {
                        drawText(
                            numeral,
                            color = Color(0xFFE8EAF0).copy(alpha = 0.86f),
                            topLeft = baseline - Offset(0f, numeral.firstBaseline),
                        )
                    }
                }
            },
    ) {
        Box(Modifier.padding(start = layout.numeralLane)) { poster() }
    }
}

@Composable
internal fun NumberedRankingViewAll(
    layout: NumberedRankingLayout,
    font: FontFamily,
    onClick: () -> Unit,
) {
    val density = LocalDensity.current
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(layout.posterCorner)
    Box(Modifier.padding(start = layout.numeralLane)) {
        Column(
            modifier = Modifier.size(layout.posterWidth, layout.posterHeight)
                .clip(shape)
                .background(Color.White.copy(alpha = if (focused) 0.16f else 0.06f))
                .border(layout.viewAllBorder, Color.White.copy(alpha = if (focused) 0.82f else 0.12f), shape)
                .onFocusChanged { focused = it.isFocused }
                .clickable(role = Role.Button, onClick = onClick),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(layout.viewAllLabelGap, Alignment.CenterVertically),
        ) {
            Text(
                text = "→",
                fontFamily = font,
                fontSize = with(density) { layout.viewAllArrowBody.toSp() },
                color = Color(0xFFECEEF2),
            )
            Text(
                text = stringResource(Res.string.home_view_all),
                fontFamily = font,
                fontSize = with(density) { layout.headerBody.toSp() },
                color = if (focused) Color.White else Color(0xFFBEC2CB),
            )
        }
    }
}
