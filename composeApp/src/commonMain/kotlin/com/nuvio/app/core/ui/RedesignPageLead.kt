package com.nuvio.app.core.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/** The redesign's page kicker: small, uppercase, muted, tracked out (Calendar, Library). */
@Composable
internal fun RedesignKicker(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = redesignText(11.sp, FontWeight.Medium, RedesignColors.textMuted, letterSpacing = 0.1.em),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

/**
 * A redesigned page's lead: kicker, 34sp title and subtitle. The kicker and subtitle crossfade when
 * they change, and the subtitle keeps its line while [subtitle] is null, so content never moves.
 */
@Composable
internal fun RedesignPageLead(
    title: String,
    modifier: Modifier = Modifier,
    kicker: String? = null,
    subtitle: String? = null,
) {
    Column(modifier) {
        AnimatedContent(
            targetState = kicker.orEmpty(),
            transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(160)) },
            label = "page_kicker",
        ) { shown -> RedesignKicker(shown) }
        Spacer(Modifier.height(4.dp))
        Text(
            title,
            style = redesignText(SearchHeroTitleSize, FontWeight.Bold, letterSpacing = RedesignTightTracking),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        AnimatedContent(
            targetState = subtitle.orEmpty(),
            transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(160)) },
            label = "page_subtitle",
        ) { shown ->
            Text(
                shown.ifEmpty { " " },
                style = redesignText(15.sp, color = if (shown.isEmpty()) Color.Transparent else RedesignColors.textSecondary),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}
