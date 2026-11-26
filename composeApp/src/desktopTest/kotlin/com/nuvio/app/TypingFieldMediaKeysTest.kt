package com.nuvio.app

import androidx.compose.ui.input.key.Key
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Space over a hovered poster opened Quick look while typing in Library's search field. */
class TypingFieldMediaKeysTest {
    @Test fun focusedTextFieldKeepsSpaceFromWindowKeyClaims() {
        var claimed = 0
        val handler: (Key) -> Boolean = { claimed++; true }
        AppKeyboardShortcuts.claimMediaKeys(handler)
        try {
            assertTrue(AppKeyboardShortcuts.handleMediaKey(Key.Spacebar))
            AppKeyboardShortcuts.typingFieldFocused(true)
            assertFalse(AppKeyboardShortcuts.handleMediaKey(Key.Spacebar))
            AppKeyboardShortcuts.typingFieldFocused(false)
            assertTrue(AppKeyboardShortcuts.handleMediaKey(Key.Spacebar))
            assertEquals(2, claimed)
        } finally {
            AppKeyboardShortcuts.releaseMediaKeys(handler)
        }
    }
}
