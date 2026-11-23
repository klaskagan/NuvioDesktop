package com.nuvio.app.features.downloads

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.core.ui.NuvioToastController
import com.nuvio.app.core.ui.RedesignAction
import kotlinx.coroutines.launch
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource

/** Shared by the Downloads page's folder menu and Settings → Playback → Downloads. */
internal class DownloadsFolderControls(
    val displayPath: String,
    val isCustom: Boolean,
    val change: () -> Unit,
    val reset: () -> Unit,
)

@Composable
internal fun rememberDownloadsFolderControls(): DownloadsFolderControls {
    val custom by DownloadsFolder.customPath.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val notWritable = stringResource(Res.string.downloads_folder_not_writable)
    val changed = stringResource(Res.string.downloads_folder_changed)
    return DownloadsFolderControls(
        displayPath = remember(custom) { DownloadsFolder.displayPath() },
        isCustom = custom != null,
        change = {
            scope.launch {
                when (DownloadsFolder.choose()) {
                    DownloadsFolderChoice.Chosen -> NuvioToastController.show(changed)
                    DownloadsFolderChoice.NotWritable -> NuvioToastController.show(notWritable)
                    DownloadsFolderChoice.Cancelled -> Unit
                }
            }
        },
        reset = { DownloadsFolder.reset() },
    )
}

/** Open folder / Change folder… / Use default folder, for a ⋯-style folder menu. */
@Composable
internal fun downloadsFolderActions(controls: DownloadsFolderControls, onOpen: () -> Unit): List<RedesignAction> {
    val open = stringResource(Res.string.downloads_open_directory)
    val change = stringResource(Res.string.downloads_folder_change)
    val reset = stringResource(Res.string.downloads_folder_use_default)
    return buildList {
        add(RedesignAction(open, Icons.Outlined.FolderOpen, tag = "downloads-folder-open", onClick = onOpen))
        add(RedesignAction(change, Icons.Outlined.Folder, tag = "downloads-folder-change", onClick = controls.change))
        if (controls.isCustom) add(RedesignAction(reset, Icons.Rounded.Restore, tag = "downloads-folder-reset", onClick = controls.reset))
    }
}
