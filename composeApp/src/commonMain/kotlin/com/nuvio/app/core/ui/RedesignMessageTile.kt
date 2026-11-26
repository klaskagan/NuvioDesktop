package com.nuvio.app.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Frosted empty / error state: icon, title, one line, optional pill action. Sized to the Home design's
 * state card (`.state`): a 48 dp round badge, 18 dp title, 14 dp text; Home, Library and Downloads share it.
 */
@Composable
internal fun RedesignMessageTile(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    icon: ImageVector? = null,
    bookmark: Boolean = false,
    compact: Boolean = false,
    tag: String = "message",
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null,
) {
    if (compact) {
        // One row in a shelf's place (a server that failed): icon, text, action on the right.
        Row(
            modifier.fillMaxWidth().clip(RedesignTileShape).background(RedesignColors.fill)
                .border(1.dp, RedesignColors.hairline, RedesignTileShape)
                .padding(horizontal = 18.dp, vertical = 16.dp)
                .testTag(tag),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (icon != null) {
                Box(Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(RedesignColors.fill), contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = RedesignColors.textSecondary, modifier = Modifier.size(20.dp))
                }
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = redesignText(14.sp, FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (message.isNotBlank()) {
                    Text(message, style = redesignText(12.sp, color = RedesignColors.textMuted), maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp))
                }
            }
            if (actionLabel != null && onAction != null) {
                RedesignPillButton(actionLabel, onAction, modifier = Modifier.testTag("$tag-action"))
            }
        }
        return
    }
    Column(
        modifier.fillMaxWidth().clip(RedesignTileShape).background(RedesignColors.fill)
            .border(1.dp, RedesignColors.hairline, RedesignTileShape)
            .padding(horizontal = 28.dp, vertical = 26.dp)
            .testTag(tag),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val glyph = icon ?: if (bookmark) Icons.Rounded.BookmarkAdd else null
        if (glyph != null) {
            Box(
                Modifier.size(48.dp).clip(CircleShape).background(RedesignColors.fillStrong),
                contentAlignment = Alignment.Center,
            ) {
                Icon(glyph, contentDescription = null, tint = RedesignColors.textSecondary, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.height(14.dp))
        }
        Text(title, style = redesignText(18.sp, FontWeight.Bold, letterSpacing = RedesignTightTracking), textAlign = TextAlign.Center)
        if (message.isNotBlank()) {
            Text(
                message,
                style = redesignText(14.sp, color = RedesignColors.textSecondary, lineHeight = 21.sp),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        val hasPrimary = actionLabel != null && onAction != null
        val hasSecondary = secondaryLabel != null && onSecondary != null
        if (hasPrimary || hasSecondary) {
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (hasPrimary) RedesignPillButton(actionLabel!!, onAction!!, primary = true, modifier = Modifier.testTag("$tag-action"))
                if (hasSecondary) RedesignPillButton(secondaryLabel!!, onSecondary!!, modifier = Modifier.testTag("$tag-secondary"))
            }
        }
    }
}
