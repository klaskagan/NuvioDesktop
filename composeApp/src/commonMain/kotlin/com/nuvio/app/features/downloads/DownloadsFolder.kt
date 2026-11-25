package com.nuvio.app.features.downloads

import kotlinx.coroutines.flow.StateFlow

/** What happened when the user was asked for a download folder. */
internal enum class DownloadsFolderChoice { Chosen, Cancelled, NotWritable }

/**
 * Where new downloads are saved. Desktop lets the user pick a folder (Downloads page and Settings →
 * Playback); other platforms keep their system location and report [isSupported] = false.
 * Changing it affects new downloads only: each [DownloadItem] keeps the folder it started in.
 */
internal expect object DownloadsFolder {
    val isSupported: Boolean

    /** The chosen folder's absolute path, or null for the app's default folder. */
    val customPath: StateFlow<String?>

    /** The folder new downloads go to, for display. */
    fun currentPath(): String

    /** [currentPath] shortened for display ("~/Movies/Nuvio"). */
    fun displayPath(): String

    suspend fun choose(): DownloadsFolderChoice

    fun reset()

    /** Free space where new downloads go, in bytes; null where the platform can't tell. */
    fun freeBytes(): Long?
}

/** "~/Movies/Nuvio" for display; the full path when it isn't under [home]. */
internal fun shortFolderPath(path: String, home: String?): String =
    if (!home.isNullOrBlank() && (path == home || path.startsWith("$home/"))) "~" + path.removePrefix(home) else path
