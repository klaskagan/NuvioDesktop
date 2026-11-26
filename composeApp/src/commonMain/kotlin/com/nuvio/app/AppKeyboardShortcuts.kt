package com.nuvio.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * App-wide shortcuts caught at the window level (a screen's own key handler only sees keys while
 * something inside it has focus). MainAppContent turns each request into navigation.
 */
object AppKeyboardShortcuts {
    internal var searchEscape: (() -> Boolean)? = null

    // Open layers stack: a card menu opened over Library's open list must close before the list,
    // and closing it must hand Esc back to the list instead of dropping it.
    private val overlayEscapes = ArrayList<() -> Boolean>()

    /**
     * The innermost open in-page layer (a menu, panel, card overlay, sheet) takes Esc before Search
     * or Back do. Assigning a handler claims it; assigning null releases the innermost claim (the
     * older `if (overlayEscape === handler) overlayEscape = null` pattern). Prefer
     * [OverlayEscapeHandler].
     */
    internal var overlayEscape: (() -> Boolean)?
        get() = overlayEscapes.lastOrNull()
        set(value) {
            if (value == null) overlayEscapes.removeLastOrNull() else claimOverlayEscape(value)
        }

    /** Drops every claim; for tests that tear down a screen mid-layer. */
    internal fun clearOverlayEscapes() {
        overlayEscapes.clear()
    }

    internal fun claimOverlayEscape(handler: () -> Boolean) {
        overlayEscapes.remove(handler)
        overlayEscapes.add(handler)
    }

    internal fun releaseOverlayEscape(handler: () -> Boolean) {
        overlayEscapes.remove(handler)
    }

    // Like Esc: the innermost claim gets playback keys (Space, arrows, M) before the focused control.
    private val mediaKeys = ArrayList<(Key) -> Boolean>()

    internal fun claimMediaKeys(handler: (Key) -> Boolean) {
        mediaKeys.remove(handler)
        mediaKeys.add(handler)
    }

    internal fun releaseMediaKeys(handler: (Key) -> Boolean) {
        mediaKeys.remove(handler)
    }

    /**
     * An unmodified key press from the desktop key dispatcher, which calls this before arrows move
     * focus; true when an open player layer took it.
     */
    fun handleMediaKey(key: Key): Boolean =
        typingFields == 0 && mediaKeys.lastOrNull()?.invoke(key) == true

    // Text fields that have focus. While one does, Space and the letters are typing, so no layer
    // may claim them (Space over a hovered poster opened Quick look from Library's search field).
    private var typingFields = 0

    internal fun typingFieldFocused(focused: Boolean) {
        typingFields = (typingFields + if (focused) 1 else -1).coerceAtLeast(0)
    }

    private val focusSearch = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val focusSearchRequests: SharedFlow<Unit> = focusSearch.asSharedFlow()

    /** ⌘F / Ctrl+F: open Search and focus its field. */
    fun requestSearchFocus() {
        focusSearch.tryEmit(Unit)
    }
}

/**
 * Claims the desktop Esc key for an open layer while [enabled]. The window-level Esc dispatcher
 * runs before Compose back handlers, so a layer that only registers a back handler never sees Esc
 * and the page underneath navigates Back instead (or, for a sheet, crashes while disposing).
 */
@Composable
internal fun OverlayEscapeHandler(enabled: Boolean = true, onEscape: () -> Unit) {
    if (!enabled) return
    val current by rememberUpdatedState(onEscape)
    DisposableEffect(Unit) {
        val handler: () -> Boolean = { current(); true }
        AppKeyboardShortcuts.claimOverlayEscape(handler)
        onDispose { AppKeyboardShortcuts.releaseOverlayEscape(handler) }
    }
}

/**
 * Claims the window's playback keys while [enabled] (an expanded trailer, say). [onKey] returns
 * whether it used the key; unused keys go on to the focused control as usual.
 */
@Composable
internal fun MediaKeysHandler(enabled: Boolean = true, onKey: (Key) -> Boolean) {
    if (!enabled) return
    val current by rememberUpdatedState(onKey)
    DisposableEffect(Unit) {
        val handler: (Key) -> Boolean = { current(it) }
        AppKeyboardShortcuts.claimMediaKeys(handler)
        onDispose { AppKeyboardShortcuts.releaseMediaKeys(handler) }
    }
}

/**
 * Marks a text field so window-level key claims ([MediaKeysHandler]) leave its keys alone while it
 * has focus. Use it on any field shown on a screen with hoverable posters or an in-page player.
 */
@Composable
internal fun Modifier.typingField(): Modifier {
    val focused = remember { booleanArrayOf(false) }
    // A field removed while focused gets no focus change; release its claim when it leaves.
    DisposableEffect(Unit) {
        onDispose {
            if (focused[0]) {
                focused[0] = false
                AppKeyboardShortcuts.typingFieldFocused(false)
            }
        }
    }
    return onFocusChanged { state ->
        if (state.isFocused != focused[0]) {
            focused[0] = state.isFocused
            AppKeyboardShortcuts.typingFieldFocused(state.isFocused)
        }
    }
}
