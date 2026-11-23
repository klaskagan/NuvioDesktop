package com.nuvio.app.core.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset

internal fun Modifier.secondaryClick(onClick: (() -> Unit)?): Modifier =
    secondaryClickAt(onClick?.let { action -> { action() } })

internal expect fun Modifier.secondaryClickAt(onClick: ((Offset) -> Unit)?): Modifier

/**
 * Desktop mouse press-and-hold at a local position. Compose's `combinedClickable` ignores long
 * presses from a mouse, so desktop callers that promise "press and hold" need this; place it after
 * the clickable so a fired hold swallows the release and no click follows. No-op on touch platforms,
 * where `combinedClickable(onLongClick)` already works.
 */
internal expect fun Modifier.mousePressAndHoldAt(onHold: ((Offset) -> Unit)?): Modifier
