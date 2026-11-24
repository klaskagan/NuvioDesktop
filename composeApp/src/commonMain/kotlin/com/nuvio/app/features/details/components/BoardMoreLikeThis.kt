package com.nuvio.app.features.details.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nuvio.app.core.ui.RedesignColors
import com.nuvio.app.core.ui.RedesignPillButton
import com.nuvio.app.core.ui.redesignText
import com.nuvio.app.features.home.MetaPreview
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.detail_board_seen_everything
import nuvio.composeapp.generated.resources.detail_board_show_watched
import nuvio.composeapp.generated.resources.detail_board_unwatched_only
import nuvio.composeapp.generated.resources.details_more_like_this
import org.jetbrains.compose.resources.stringResource

/**
 * The board's More like this: an "Unwatched only" switch (on by default and remembered) over the
 * rail. With it on, the rail is filled from the shared unwatched recommendations; when everything
 * there has been watched it says so, with Show watched.
 */
@Composable
internal fun BoardMoreLikeThis(
    items: List<MetaPreview>,
    unwatchedOnly: Boolean,
    onUnwatchedOnlyChange: (Boolean) -> Unit,
    watchedKeys: Set<String>,
    fullyWatchedSeriesKeys: Set<String>,
    sourceLabel: String?,
    horizontalScrollPadding: Dp,
    onPosterClick: ((MetaPreview) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth().testTag("detail_board_more_like_this"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(Res.string.details_more_like_this), style = redesignText(18.sp, FontWeight.Bold))
                if (sourceLabel != null) Text(sourceLabel, style = redesignText(12.sp, color = RedesignColors.textMuted))
            }
            RedesignPillButton(
                label = stringResource(Res.string.detail_board_unwatched_only),
                onClick = { onUnwatchedOnlyChange(!unwatchedOnly) },
                active = unwatchedOnly,
                leading = if (unwatchedOnly) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                modifier = Modifier.testTag("detail_board_unwatched_only"),
            )
        }
        AnimatedContent(
            targetState = items.isEmpty(),
            transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(160)) },
            label = "more_like_this_empty",
        ) { empty ->
            if (empty) {
                Column(Modifier.fillMaxWidth().padding(vertical = 24.dp).testTag("detail_board_seen_everything"), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(Res.string.detail_board_seen_everything), style = redesignText(15.sp, FontWeight.SemiBold, RedesignColors.textSecondary))
                    Spacer(Modifier.height(12.dp))
                    RedesignPillButton(label = stringResource(Res.string.detail_board_show_watched), onClick = { onUnwatchedOnlyChange(false) })
                }
            } else {
                DetailPosterRailSection(
                    title = stringResource(Res.string.details_more_like_this),
                    items = items,
                    watchedKeys = watchedKeys,
                    fullyWatchedSeriesKeys = fullyWatchedSeriesKeys,
                    showHeader = false,
                    horizontalScrollPadding = horizontalScrollPadding,
                    onPosterClick = onPosterClick,
                )
            }
        }
    }
}
