package com.nuvio.app.features.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nuvio.app.isDesktop
import com.nuvio.app.core.ui.SkeletonBlock
import com.nuvio.app.core.ui.SkeletonPosterRow
import com.nuvio.app.core.ui.SkeletonPoster
import com.nuvio.app.core.ui.LocalNuvioShelfHeaderStyle
import com.nuvio.app.core.ui.NuvioShelfHeaderStyle
import com.nuvio.app.core.ui.NuvioShelfSectionHeader
import com.nuvio.app.core.ui.NuvioTokens
import com.nuvio.app.core.ui.NuvioViewAllPillSize
import com.nuvio.app.core.ui.nuvio
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.nuvio.app.core.ui.desktopCatalogShelfPosterBaseWidthDp
import com.nuvio.app.core.ui.landscapePosterHeightForWidth
import com.nuvio.app.core.ui.landscapePosterWidth
import com.nuvio.app.core.ui.rememberPosterCardStyleUiState
import com.nuvio.app.core.ui.skeleton

@Composable
fun HomeSkeletonHero(
    modifier: Modifier = Modifier,
    viewportHeight: Dp? = null,
    mobileBelowSectionHeightHint: Dp? = null,
    sectionPadding: Dp? = null,
) {
    // Desktop board: the featured tile is a plain soft placeholder at the board's height (Home design
    // "For you · loading"), so the board keeps its shape and the carousel crossfades in over it.
    val boardHeight = LocalHomeHeroHeightCap.current
    if (isDesktop && boardHeight != null) {
        Box(modifier.fillMaxWidth().height(boardHeight).skeleton(RectangleShape).testTag("home_skeleton_featured"))
        return
    }
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp)),
    ) {
        val layout = homeHeroLayout(
            maxWidthDp = maxWidth.value,
            viewportHeightDp = viewportHeight?.value,
            mobileBelowSectionHeightHintDp = mobileBelowSectionHeightHint?.value,
            preferDesktopLayout = isDesktop,
            heroHeightCapDp = LocalHomeHeroHeightCap.current?.value,
        )
        val containerWidth = maxWidth
        val contentHorizontalPadding = sectionPadding ?: layout.contentHorizontalPadding

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(layout.heroHeight)
                .skeleton(RectangleShape),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.background.copy(alpha = 0.02f),
                                MaterialTheme.colorScheme.background.copy(alpha = 0.12f),
                                MaterialTheme.colorScheme.background.copy(alpha = 0.34f),
                                MaterialTheme.colorScheme.background.copy(alpha = 0.78f),
                            ),
                        ),
                    ),
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(layout.bottomFadeHeight)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.background.copy(alpha = 0f),
                                MaterialTheme.colorScheme.background,
                            ),
                        ),
                    ),
            )

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .then(
                        if (isDesktop) {
                            Modifier
                                .widthIn(max = layout.contentContainerMaxWidth)
                                .fillMaxWidth()
                        } else {
                            Modifier.fillMaxWidth()
                        },
                    )
                    .padding(
                        horizontal = contentHorizontalPadding,
                        vertical = layout.contentVerticalPadding,
                    ),
                horizontalAlignment = if (layout.isTablet) Alignment.Start else Alignment.CenterHorizontally,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth(layout.contentWidthFraction)
                        .widthIn(max = layout.contentMaxWidth),
                    horizontalAlignment = if (layout.isTablet) Alignment.Start else Alignment.CenterHorizontally,
                ) {
                    val logoWidth = containerWidth
                        .times(layout.contentWidthFraction * layout.logoWidthFraction)
                        .coerceAtMost(layout.contentMaxWidth * layout.logoWidthFraction)

                    Box(
                        modifier = Modifier.width(logoWidth).height(logoWidth / 2.6f),
                        contentAlignment = if (layout.isTablet) Alignment.BottomStart else Alignment.BottomCenter,
                    ) {
                        SkeletonBlock(width = logoWidth, height = 32.dp, cornerRadius = 8.dp)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    SkeletonBlock(width = 156.dp, height = 10.dp)
                }
                if (!layout.isTablet) {
                    Spacer(modifier = Modifier.height(14.dp))
                    SkeletonBlock(
                        width = 160.dp,
                        height = 48.dp,
                        cornerRadius = 40.dp,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                } else {
                    Spacer(modifier = Modifier.height(14.dp))
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
fun HomeSkeletonRow(
    modifier: Modifier = Modifier,
    horizontalPadding: Dp? = null,
) {
    val posterCardStyle = rememberPosterCardStyleUiState()
    val basePosterWidthDp = if (isDesktop) {
        desktopCatalogShelfPosterBaseWidthDp(posterCardStyle.widthDp)
    } else {
        posterCardStyle.widthDp
    }
    val skeletonWidth = if (posterCardStyle.catalogLandscapeModeEnabled) {
        landscapePosterWidth(basePosterWidthDp)
    } else {
        basePosterWidthDp.dp
    }
    val skeletonHeight = if (posterCardStyle.catalogLandscapeModeEnabled) {
        landscapePosterHeightForWidth(skeletonWidth)
    } else {
        if (isDesktop) {
            ((posterCardStyle.heightDp * basePosterWidthDp.toFloat()) / posterCardStyle.widthDp).toInt().dp
        } else {
            posterCardStyle.heightDp.dp
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val sectionPadding = horizontalPadding ?: homeSectionHorizontalPaddingForWidth(maxWidth.value)
        if (isDesktop && LocalNuvioShelfHeaderStyle.current == NuvioShelfHeaderStyle.Editorial) {
            HomeEditorialSkeletonRow(
                sectionPadding = sectionPadding,
                posterWidth = skeletonWidth,
                posterHeight = skeletonHeight,
                // Same rule and size as HomeCatalogRowSectionContent's lead card.
                leadHeight = if (posterCardStyle.catalogLandscapeModeEnabled) null else {
                    basePosterWidthDp.dp * (posterCardStyle.heightDp.toFloat() / posterCardStyle.widthDp.coerceAtLeast(1))
                },
                cornerRadius = posterCardStyle.cornerRadiusDp.dp,
                showLabels = !posterCardStyle.hideLabelsEnabled,
                showDetail = !posterCardStyle.catalogLandscapeModeEnabled,
            )
            return@BoxWithConstraints
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(modifier = Modifier.padding(horizontal = sectionPadding).height(32.dp)) {
                SkeletonBlock(
                    modifier = Modifier.align(Alignment.CenterStart),
                    width = 128.dp,
                    height = 16.dp,
                )
            }
            SkeletonPosterRow(
                width = skeletonWidth,
                height = skeletonHeight,
                cornerRadius = posterCardStyle.cornerRadiusDp.dp,
                horizontalPadding = sectionPadding,
                showLabels = !posterCardStyle.hideLabelsEnabled,
                showDetail = !posterCardStyle.catalogLandscapeModeEnabled,
            )
        }
    }
}

/**
 * Desktop Home row placeholder: the real Editorial shelf header (blank, invisible) with an eyebrow and
 * title bar over it, then the lead card's 16:9 block and posters at their loaded sizes and gaps, so the
 * row's content crossfades in without anything moving.
 */
@Composable
private fun HomeEditorialSkeletonRow(
    sectionPadding: Dp,
    posterWidth: Dp,
    posterHeight: Dp,
    leadHeight: Dp?,
    cornerRadius: Dp,
    showLabels: Boolean,
    showDetail: Boolean,
) {
    val tokens = MaterialTheme.nuvio
    Column(
        modifier = Modifier.fillMaxWidth().testTag("home_skeleton_row"),
        verticalArrangement = Arrangement.spacedBy(tokens.spacing.controlGap + NuvioTokens.Space.s2),
    ) {
        Box(Modifier.padding(horizontal = sectionPadding)) {
            Box(Modifier.alpha(0f).clearAndSetSemantics { }) {
                // Placeholder text, never shown: a blank eyebrow would be dropped (NBSP counts as blank).
                NuvioShelfSectionHeader(title = "Row", eyebrow = "Row", viewAllPillSize = NuvioViewAllPillSize.Compact)
            }
            Column(Modifier.matchParentSize()) {
                SkeletonBlock(modifier = Modifier.padding(top = 2.dp), width = 90.dp, height = 10.dp, cornerRadius = 5.dp)
                Spacer(Modifier.weight(1f))
                SkeletonBlock(modifier = Modifier.padding(bottom = 3.dp), width = 300.dp, height = 24.dp, cornerRadius = 8.dp)
            }
        }
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = sectionPadding),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            userScrollEnabled = false,
        ) {
            if (leadHeight != null) {
                item {
                    Box(
                        Modifier.width(leadHeight * (16f / 9f)).height(leadHeight)
                            .skeleton(RoundedCornerShape(14.dp)).testTag("home_skeleton_lead"),
                    )
                }
            }
            items(12) {
                SkeletonPoster(
                    modifier = Modifier.width(posterWidth).testTag("home_skeleton_poster"),
                    aspectRatio = posterWidth / posterHeight,
                    cornerRadius = cornerRadius,
                    showLabels = showLabels,
                    showDetail = showDetail,
                    labelSpacing = 6.dp,
                )
            }
        }
    }
}
