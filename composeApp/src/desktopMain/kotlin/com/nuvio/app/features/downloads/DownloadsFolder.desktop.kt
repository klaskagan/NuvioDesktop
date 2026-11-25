package com.nuvio.app.features.downloads

import com.nuvio.app.core.storage.DesktopStorage
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import javax.swing.JFileChooser
import javax.swing.SwingUtilities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

internal actual object DownloadsFolder {
    // Device-wide, not per profile: it names a folder on this machine.
    private const val Key = "download_folder"
    private val store = DesktopStorage.store("nuvio_downloads")
    private val state = MutableStateFlow(store.getString(Key)?.takeIf { it.isNotBlank() })

    /** Opens the system folder picker; replaced in tests. Returns the picked folder or null. */
    internal var picker: suspend () -> File? = ::pickFolder

    actual val isSupported: Boolean = true

    actual val customPath: StateFlow<String?> = state.asStateFlow()

    actual fun currentPath(): String =
        state.value ?: DownloadsPlatformDownloader.defaultDownloadsDir.absolutePath

    actual fun displayPath(): String = shortFolderPath(currentPath(), System.getProperty("user.home"))

    actual suspend fun choose(): DownloadsFolderChoice {
        val folder = picker() ?: return DownloadsFolderChoice.Cancelled
        val usable = withContext(Dispatchers.IO) { runCatching { folder.mkdirs(); folder.isDirectory && folder.canWrite() }.getOrDefault(false) }
        if (!usable) return DownloadsFolderChoice.NotWritable
        set(folder.absolutePath)
        return DownloadsFolderChoice.Chosen
    }

    actual fun reset() = set(null)

    actual fun freeBytes(): Long? = runCatching {
        // The folder may not exist until the first download; ask the nearest existing parent.
        generateSequence(File(currentPath())) { it.parentFile }.firstOrNull { it.exists() }?.usableSpace
    }.getOrNull()?.takeIf { it > 0L }

    private fun set(path: String?) {
        if (path == null) store.remove(Key) else store.putString(Key, path)
        state.value = path
    }
}

private suspend fun pickFolder(): File? = withContext(Dispatchers.IO) {
    var picked: File? = null
    val start = DownloadsFolder.currentPath()
    SwingUtilities.invokeAndWait {
        if (System.getProperty("os.name").orEmpty().lowercase().contains("mac")) {
            // macOS: the native panel, switched to choosing folders.
            val previous = System.getProperty("apple.awt.fileDialogForDirectories")
            System.setProperty("apple.awt.fileDialogForDirectories", "true")
            try {
                val dialog = FileDialog(null as Frame?, "Choose download folder", FileDialog.LOAD).apply {
                    directory = start
                    isVisible = true
                }
                picked = dialog.file?.let { File(dialog.directory, it) }
            } finally {
                if (previous == null) System.clearProperty("apple.awt.fileDialogForDirectories")
                else System.setProperty("apple.awt.fileDialogForDirectories", previous)
            }
        } else {
            val chooser = JFileChooser(start).apply {
                fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
                dialogTitle = "Choose download folder"
            }
            if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) picked = chooser.selectedFile
        }
    }
    picked
}
