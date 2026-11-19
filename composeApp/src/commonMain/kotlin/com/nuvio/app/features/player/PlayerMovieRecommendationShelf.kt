package com.nuvio.app.features.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nuvio.app.core.ui.NuvioPosterCard
import com.nuvio.app.core.ui.NuvioPosterShape
import com.nuvio.app.core.ui.PlatformBackHandler
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.isDesktop
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.action_close
import nuvio.composeapp.generated.resources.details_more_like_this
import org.jetbrains.compose.resources.stringResource

/** Header row (46dp) plus the vertical padding around the posters, matching the desktop shelf. */
private val ShelfChromeHeight = 66.dp

/** Poster height of the desktop shelf: min(clamp(110dp, 28% height, 290dp), 39% width). */
internal fun movieRecommendationPosterHeight(maxWidth: Dp, maxHeight: Dp): Dp =
    minOf((maxHeight * 0.28f).coerceIn(110.dp, 290.dp), maxWidth * 0.39f)

internal fun movieRecommendationShelfHeight(maxWidth: Dp, maxHeight: Dp): Dp =
    movieRecommendationPosterHeight(maxWidth, maxHeight) + ShelfChromeHeight

/**
 * Whether the Compose "More Like This" shelf is on screen. Desktop draws its own shelf in the
 * HTML player overlay from the same runtime state (see controls.js renderMovieRecommendations).
 */
internal fun PlayerScreenRuntime.composeMovieRecommendationsVisible(isInPip: Boolean): Boolean =
    !isDesktop && PlayerPlatformFeatures.movieRecommendations && !isInPip && !playerControlsLocked && !isPanelOpen &&
        initialLoadCompleted && errorMessage == null &&
        movieRecommendationStage > 0 && movieRecommendationCandidates.isNotEmpty() &&
        !(movieRecommendationDismissedSession == movieRecommendationSessionKey && movieRecommendationsDismissed)

internal fun PlayerScreenRuntime.openMovieRecommendation(preview: MetaPreview) {
    if (movieRecommendationStage <= 0) return
    val open = args.onOpenMetaDetails ?: return
    val session = movieRecommendationSessionKey
    requestBack { if (session == movieRecommendationSessionKey) open(preview) }
}

internal fun PlayerScreenRuntime.dismissMovieRecommendations() {
    movieRecommendationDismissedSession = movieRecommendationSessionKey
    movieRecommendationsDismissed = movieRecommendationStage > 0
}

@Composable
internal fun PlayerScreenRuntime.RenderMovieRecommendationShelf(
    visible: Boolean,
    modifier: Modifier = Modifier,
) {
    val items = movieRecommendationCandidates
    val sessionKey = movieRecommendationSessionKey
    PlatformBackHandler(enabled = visible, onBack = ::dismissMovieRecommendations)
    BoxWithConstraints(modifier = modifier) {
        val posterHeight = movieRecommendationPosterHeight(maxWidth, maxHeight)
        AnimatedVisibility(
            visible = visible,
            enter = slideInVertically(tween(300)) { it } + fadeIn(tween(220)),
            exit = slideOutVertically(tween(300)) { it } + fadeOut(tween(220)),
            modifier = Modifier.align(androidx.compose.ui.Alignment.BottomCenter),
        ) {
            MovieRecommendationShelf(
                items = items,
                sessionKey = sessionKey,
                posterHeight = posterHeight,
                horizontalPadding = metrics.horizontalPadding + horizontalSafePadding,
                onOpen = ::openMovieRecommendation,
                onDismiss = ::dismissMovieRecommendations,
            )
        }
    }
}

@Composable
private fun MovieRecommendationShelf(
    items: List<MetaPreview>,
    sessionKey: String,
    posterHeight: Dp,
    horizontalPadding: Dp,
    onOpen: (MetaPreview) -> Unit,
    onDismiss: () -> Unit,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(sessionKey) { listState.scrollToItem(0) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.linearGradient(
                    0f to Color(0x4F5F6974),
                    0.62f to Color(0xE00B0E13),
                ),
            )
            // Swallow taps between posters so they do not toggle the player chrome underneath.
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
            .navigationBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp)
                .padding(horizontal = horizontalPadding),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(Res.string.details_more_like_this),
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(Color(0xBD0A0F14)),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = stringResource(Res.string.action_close),
                    tint = Color.White,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        LazyRow(
            state = listState,
            contentPadding = PaddingValues(start = horizontalPadding, end = horizontalPadding, top = 4.dp, bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(items, key = { "${it.type}:${it.id}" }) { item ->
                NuvioPosterCard(
                    title = item.name,
                    imageUrl = item.poster ?: item.banner,
                    shape = NuvioPosterShape.Poster,
                    basePosterWidthDp = (posterHeight.value * 2f / 3f).toInt(),
                    showTitleBelow = false,
                    artworkCornerRadius = 8.dp,
                    mediaIdentity = "player-recommendation:${item.type}:${item.id}",
                    onClick = { onOpen(item) },
                )
            }
        }
    }
}
