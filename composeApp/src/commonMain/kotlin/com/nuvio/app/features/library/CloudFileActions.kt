package com.nuvio.app.features.library

import com.nuvio.app.features.cloud.CloudLibraryContentType
import com.nuvio.app.features.cloud.CloudLibraryFile
import com.nuvio.app.features.cloud.CloudLibraryItem
import com.nuvio.app.features.cloud.CloudLibraryPlaybackResult
import com.nuvio.app.features.cloud.CloudLibraryRepository
import com.nuvio.app.features.cloud.playbackVideoId
import com.nuvio.app.features.cloud.providerPosterUrl
import com.nuvio.app.features.downloads.DownloadEnqueueResult
import com.nuvio.app.features.downloads.DownloadsRepository
import com.nuvio.app.features.streams.StreamBehaviorHints
import com.nuvio.app.features.streams.StreamItem

/**
 * Cloud file actions behind a row's right-click / press-and-hold menu. Both resolve the file's link
 * through the provider first, exactly as Play does; null means the link couldn't be resolved.
 */
internal suspend fun resolveCloudFileUrl(item: CloudLibraryItem, file: CloudLibraryFile): String? =
    (CloudLibraryRepository.resolvePlayback(item, file) as? CloudLibraryPlaybackResult.Success)?.url?.takeIf { it.isNotBlank() }

/** Queues the file in Downloads; a season pack's episodes group under one show there. */
internal suspend fun downloadCloudFile(item: CloudLibraryItem, file: CloudLibraryFile): DownloadEnqueueResult? {
    val url = resolveCloudFileUrl(item, file) ?: return null
    val episode = cloudEpisodeOf(file)
    val show = item.mediaKind() == CloudMediaKind.Show && episode != null
    val label = cloudFileLabel(file)
    return DownloadsRepository.enqueueFromStream(
        contentType = CloudLibraryContentType,
        videoId = item.playbackVideoId(file),
        // One parent per file unless it's an episode, so two films from one pack don't replace each other.
        parentMetaId = if (show) item.stableKey else item.playbackVideoId(file),
        parentMetaType = CloudLibraryContentType,
        title = if (show) cloudShowTitle(item.name) else cloudDisplayTitle(file.name.ifBlank { item.name }),
        logo = null,
        poster = item.providerPosterUrl(),
        background = null,
        seasonNumber = episode?.first?.takeIf { show },
        episodeNumber = episode?.second?.takeIf { show },
        episodeTitle = label.substringAfter(" · ", "").takeIf { show && it.isNotBlank() },
        episodeThumbnail = null,
        stream = StreamItem(
            name = item.providerName,
            title = file.name,
            url = url,
            addonName = item.providerName,
            addonId = "cloud:${item.providerId}",
            behaviorHints = StreamBehaviorHints(filename = file.name, videoSize = file.sizeBytes),
        ),
    )
}
