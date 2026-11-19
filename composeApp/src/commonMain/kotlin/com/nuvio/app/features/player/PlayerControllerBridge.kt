package com.nuvio.app.features.player

import kotlin.concurrent.Volatile

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Commands the player understands from non-pointer input (keyboard arrows, gamepad).
 *
 * The desktop key dispatcher and the spatial focus engine both live outside the player composable,
 * so the active player registers itself here while it is on screen. Keyboard and controller input
 * then drive playback instead of moving focus around the invisible app shell behind the player.
 */
internal enum class PlayerCommand {
    SeekBack,
    SeekForward,
    VolumeUp,
    VolumeDown,
    TogglePlayback,
    ToggleChrome,
    Back,
    Subtitles,
    Audio,
    Episodes,
    Sources,
}

internal object PlayerControllerBridge {
    @Volatile
    private var scope: CoroutineScope? = null

    @Volatile
    private var handler: ((PlayerCommand) -> Boolean)? = null

    @Volatile
    private var keyboardShortcutsHandled: (() -> Boolean)? = null

    fun register(
        scope: CoroutineScope?,
        handler: ((PlayerCommand) -> Boolean)?,
        keyboardShortcutsHandled: (() -> Boolean)? = null,
    ) {
        this.scope = scope
        this.handler = handler
        this.keyboardShortcutsHandled = keyboardShortcutsHandled
    }

    /**
     * True when the player's own controls (the desktop webview) already receive and handle keyboard
     * shortcuts. The desktop key dispatcher must then leave them alone: handling the same press in
     * both places doubled every arrow-key skip. Gamepad input still goes through [dispatch].
     */
    fun keyboardShortcutsHandledByPlayer(): Boolean =
        runCatching { keyboardShortcutsHandled?.invoke() == true }.getOrDefault(false)

    /**
     * Dispatches a command to the player. Gamepad input arrives on the polling thread, so the
     * command is always executed on the composition's dispatcher; the caller only needs to know
     * that an active player took it.
     */
    fun dispatch(command: PlayerCommand): Boolean {
        val current = handler ?: return false
        val currentScope = scope
        if (currentScope == null) {
            return runCatching { current(command) }.getOrDefault(false)
        }
        currentScope.launch { current(command) }
        return true
    }
}
