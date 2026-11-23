package com.nuvio.app.features.library

import com.nuvio.app.core.ui.RedesignMessageTile
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nuvio.app.features.home.HomeCatalogSection
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.home.components.HomeCatalogRowSection
import com.nuvio.app.features.home.components.HomeEmptyStateCard
import com.nuvio.app.features.home.components.HomeSkeletonRow
import com.nuvio.app.features.servers.ServerCatalog
import com.nuvio.app.features.servers.ServerFailure
import com.nuvio.app.features.servers.ServerLibraryRef
import com.nuvio.app.features.servers.message
import com.nuvio.app.features.servers.serverFailure
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.action_retry
import nuvio.composeapp.generated.resources.library_server_load_failed
import nuvio.composeapp.generated.resources.library_servers_empty_message
import nuvio.composeapp.generated.resources.library_servers_empty_title
import org.jetbrains.compose.resources.stringResource

internal data class LibraryServerShelf(
    val ref: ServerLibraryRef,
    val section: HomeCatalogSection? = null,
    val failure: ServerFailure? = null,
)

internal suspend fun loadServerShelves(): List<LibraryServerShelf> = coroutineScope {
    (ServerCatalog.resumeLibraries() + ServerCatalog.libraries()).map { ref ->
        async {
            try {
                LibraryServerShelf(ref, section = ServerCatalog.librarySection(ref, SERVER_SHELF_LIMIT))
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                LibraryServerShelf(ref, failure = error.serverFailure())
            }
        }
    }.awaitAll()
}

internal fun LazyListScope.serverLibraryContent(
    shelves: List<LibraryServerShelf>?,
    watchedKeys: Set<String>,
    fullyWatchedSeriesKeys: Set<String>,
    onCatalogClick: ((HomeCatalogSection) -> Unit)?,
    onPosterClick: ((MetaPreview) -> Unit)?,
    onPosterLongClick: ((MetaPreview) -> Unit)?,
    onRetry: () -> Unit,
) {
    when {
        shelves == null -> items(3) { HomeSkeletonRow(horizontalPadding = 16.dp) }

        shelves.isEmpty() -> item {
            HomeEmptyStateCard(
                modifier = Modifier.padding(horizontal = 16.dp),
                title = stringResource(Res.string.library_servers_empty_title),
                message = stringResource(Res.string.library_servers_empty_message),
            )
        }

        else -> shelves.forEach { shelf ->
            val section = shelf.section
            if (section != null && section.items.isNotEmpty()) {
                item(key = "server-shelf:${section.key}") {
                    HomeCatalogRowSection(
                        section = section,
                        watchedKeys = watchedKeys,
                        fullyWatchedSeriesKeys = fullyWatchedSeriesKeys,
                        onViewAllClick = onCatalogClick?.takeIf { section.hasMore }?.let { open -> { open(section) } },
                        onPosterClick = onPosterClick,
                        onPosterLongClick = onPosterLongClick,
                    )
                }
            } else if (shelf.failure != null && !ServerCatalog.isResume(shelf.ref)) {
                item(key = "server-shelf-error:${shelf.ref.connection.id}:${shelf.ref.library.id}") {
                    HomeEmptyStateCard(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        title = stringResource(Res.string.library_server_load_failed, shelf.ref.title),
                        message = stringResource(shelf.failure.message()),
                        actionLabel = stringResource(Res.string.action_retry),
                        onActionClick = onRetry,
                    )
                }
            }
        }
    }
}

/**
 * Desktop server shelves: Home-style rows (lead card, editorial header) with the server's name as
 * the eyebrow over its library. A server that fails stays in place as a frosted tile with Retry.
 */
internal fun LazyListScope.libraryDesktopServerContent(
    shelves: List<LibraryServerShelf>?,
    gutter: Dp,
    watchedKeys: Set<String>,
    fullyWatchedSeriesKeys: Set<String>,
    onCatalogClick: ((HomeCatalogSection) -> Unit)?,
    onPosterClick: ((MetaPreview) -> Unit)?,
    onPosterLongClick: ((MetaPreview) -> Unit)?,
    onRetry: () -> Unit,
) {
    when {
        shelves == null -> items(3, key = { "server-placeholder:$it" }) {
            HomeSkeletonRow(libraryDesktopItemModifier().padding(top = 18.dp), horizontalPadding = gutter)
        }

        shelves.isEmpty() -> item(key = "server-empty") {
            RedesignMessageTile(tag = "library-message", 
                title = stringResource(Res.string.library_servers_empty_title),
                message = stringResource(Res.string.library_servers_empty_message),
                modifier = libraryDesktopItemModifier().padding(horizontal = gutter),
            )
        }

        else -> shelves.forEach { shelf ->
            val section = shelf.section
            if (section != null && section.items.isNotEmpty()) {
                item(key = "server-shelf:${section.key}") {
                    HomeCatalogRowSection(
                        section = section.copy(title = shelf.ref.library.name),
                        modifier = libraryDesktopItemModifier().padding(top = 18.dp),
                        watchedKeys = watchedKeys,
                        fullyWatchedSeriesKeys = fullyWatchedSeriesKeys,
                        sectionPadding = gutter,
                        onViewAllClick = onCatalogClick?.takeIf { section.hasMore }?.let { open -> { open(section) } },
                        onPosterClick = onPosterClick,
                        onPosterLongClick = onPosterLongClick,
                        eyebrow = shelf.ref.connection.name,
                        leadCard = true,
                    )
                }
            } else if (shelf.failure != null && !ServerCatalog.isResume(shelf.ref)) {
                item(key = "server-shelf-error:${shelf.ref.connection.id}:${shelf.ref.library.id}") {
                    RedesignMessageTile(tag = "library-message", 
                        title = stringResource(Res.string.library_server_load_failed, shelf.ref.title),
                        message = stringResource(shelf.failure.message()),
                        actionLabel = stringResource(Res.string.action_retry),
                        onAction = onRetry,
                        icon = Icons.Rounded.CloudOff,
                        compact = true,
                        modifier = libraryDesktopItemModifier().padding(top = 18.dp).padding(horizontal = gutter),
                    )
                }
            }
        }
    }
}

private const val SERVER_SHELF_LIMIT = 18
