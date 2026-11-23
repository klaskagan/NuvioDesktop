package com.nuvio.app.features.downloads

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** The system download location; not user-selectable on this platform. */
internal actual object DownloadsFolder {
    actual val isSupported: Boolean = false
    actual val customPath: StateFlow<String?> = MutableStateFlow(null)
    actual fun currentPath(): String = ""
    actual fun displayPath(): String = ""
    actual suspend fun choose(): DownloadsFolderChoice = DownloadsFolderChoice.Cancelled
    actual fun reset() = Unit
}
