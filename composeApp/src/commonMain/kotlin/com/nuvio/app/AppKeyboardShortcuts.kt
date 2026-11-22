package com.nuvio.app

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * App-wide shortcuts caught at the window level (a screen's own key handler only sees keys while
 * something inside it has focus). MainAppContent turns each request into navigation.
 */
object AppKeyboardShortcuts {
    internal var searchEscape: (() -> Boolean)? = null
    private val focusSearch = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val focusSearchRequests: SharedFlow<Unit> = focusSearch.asSharedFlow()

    /** ⌘F / Ctrl+F: open Search and focus its field. */
    fun requestSearchFocus() {
        focusSearch.tryEmit(Unit)
    }
}
