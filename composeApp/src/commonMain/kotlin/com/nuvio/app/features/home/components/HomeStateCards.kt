package com.nuvio.app.features.home.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nuvio.app.core.network.NetworkCondition
import com.nuvio.app.core.network.NetworkStatusRepository
import com.nuvio.app.core.ui.NuvioNetworkOfflineCard
import com.nuvio.app.core.ui.NuvioPrimaryButton
import com.nuvio.app.core.ui.NuvioSurfaceCard
import com.nuvio.app.core.ui.RedesignMessageTile
import com.nuvio.app.isDesktop
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.action_retry
import nuvio.composeapp.generated.resources.home_empty_no_rows_message
import nuvio.composeapp.generated.resources.home_empty_no_rows_title
import nuvio.composeapp.generated.resources.home_empty_no_sources_message
import nuvio.composeapp.generated.resources.home_empty_no_sources_title
import nuvio.composeapp.generated.resources.home_load_failed_title
import nuvio.composeapp.generated.resources.home_state_load_failed_message
import nuvio.composeapp.generated.resources.home_state_no_sources_message
import nuvio.composeapp.generated.resources.home_state_no_sources_title
import nuvio.composeapp.generated.resources.home_state_offline_message
import nuvio.composeapp.generated.resources.home_state_offline_title
import nuvio.composeapp.generated.resources.home_state_open_addons
import nuvio.composeapp.generated.resources.home_tab_empty_message
import nuvio.composeapp.generated.resources.home_tab_empty_title
import org.jetbrains.compose.resources.stringResource

@Composable
fun HomeEmptyStateCard(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onActionClick: (() -> Unit)? = null,
) {
    NuvioSurfaceCard(modifier = modifier) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (actionLabel != null && onActionClick != null) {
            Spacer(modifier = Modifier.height(16.dp))
            NuvioPrimaryButton(
                text = actionLabel,
                onClick = onActionClick,
            )
        }
    }
}

/** Why Home has no rows to show ("For you · empty, error and offline" in the Home design). */
internal enum class HomeStateKind { NoSources, LoadFailed, Offline, NoRows, TabEmpty }

/** Home design `.state`: the card is 440 dp wide, centred where the rows would be. */
internal val HomeStateCardMaxWidth = 440.dp

/**
 * Home's empty, error and offline states. Desktop shows the shared [RedesignMessageTile] centred in
 * [modifier]'s area with the design's copy; mobile keeps its existing cards.
 *
 * Offline has no button on desktop: when the connection returns the card leaves and [onRetry] runs,
 * so the rows load by themselves.
 */
@Composable
internal fun HomeStateCard(
    kind: HomeStateKind,
    horizontalPadding: Dp,
    modifier: Modifier = Modifier,
    errorMessage: String? = null,
    offlineCondition: NetworkCondition = NetworkCondition.NoInternet,
    onRetry: (() -> Unit)? = null,
    onOpenAddons: (() -> Unit)? = null,
) {
    if (!isDesktop) {
        MobileHomeStateCard(kind, Modifier.padding(horizontal = horizontalPadding), errorMessage, offlineCondition, onRetry)
        return
    }
    if (kind == HomeStateKind.Offline && onRetry != null) {
        val retry by rememberUpdatedState(onRetry)
        DisposableEffect(Unit) {
            onDispose {
                if (!NetworkStatusRepository.uiState.value.isOfflineLike) retry()
            }
        }
    }
    val retryLabel = stringResource(Res.string.action_retry)
    val addonsLabel = stringResource(Res.string.home_state_open_addons)
    Box(
        modifier.fillMaxWidth().padding(horizontal = horizontalPadding),
        contentAlignment = Alignment.Center,
    ) {
        val tileModifier = Modifier.widthIn(max = HomeStateCardMaxWidth)
        val tag = "home-state-${kind.name.lowercase()}"
        when (kind) {
            HomeStateKind.NoSources -> RedesignMessageTile(
                title = stringResource(Res.string.home_state_no_sources_title),
                message = stringResource(Res.string.home_state_no_sources_message),
                icon = Icons.Rounded.Extension,
                actionLabel = addonsLabel.takeIf { onOpenAddons != null },
                onAction = onOpenAddons,
                modifier = tileModifier,
                tag = tag,
            )
            HomeStateKind.LoadFailed -> RedesignMessageTile(
                title = stringResource(Res.string.home_load_failed_title),
                message = stringResource(Res.string.home_state_load_failed_message),
                icon = Icons.Rounded.WarningAmber,
                actionLabel = retryLabel.takeIf { onRetry != null },
                onAction = onRetry,
                modifier = tileModifier,
                tag = tag,
            )
            HomeStateKind.Offline -> RedesignMessageTile(
                title = stringResource(Res.string.home_state_offline_title),
                message = stringResource(Res.string.home_state_offline_message),
                icon = Icons.Rounded.WifiOff,
                modifier = tileModifier,
                tag = tag,
            )
            HomeStateKind.NoRows -> RedesignMessageTile(
                title = stringResource(Res.string.home_empty_no_rows_title),
                message = stringResource(Res.string.home_empty_no_rows_message),
                icon = Icons.Rounded.Extension,
                actionLabel = addonsLabel.takeIf { onOpenAddons != null },
                onAction = onOpenAddons,
                modifier = tileModifier,
                tag = tag,
            )
            HomeStateKind.TabEmpty -> RedesignMessageTile(
                title = stringResource(Res.string.home_tab_empty_title),
                message = stringResource(Res.string.home_tab_empty_message),
                icon = Icons.Rounded.Inbox,
                modifier = tileModifier,
                tag = tag,
            )
        }
    }
}

@Composable
private fun MobileHomeStateCard(
    kind: HomeStateKind,
    modifier: Modifier,
    errorMessage: String?,
    offlineCondition: NetworkCondition,
    onRetry: (() -> Unit)?,
) {
    when (kind) {
        HomeStateKind.Offline -> NuvioNetworkOfflineCard(condition = offlineCondition, modifier = modifier, onRetry = onRetry)
        HomeStateKind.LoadFailed -> HomeEmptyStateCard(
            modifier = modifier,
            title = stringResource(Res.string.home_load_failed_title),
            message = errorMessage ?: stringResource(Res.string.home_state_load_failed_message),
            actionLabel = stringResource(Res.string.action_retry).takeIf { onRetry != null },
            onActionClick = onRetry,
        )
        HomeStateKind.NoSources -> HomeEmptyStateCard(
            modifier = modifier,
            title = stringResource(Res.string.home_empty_no_sources_title),
            message = stringResource(Res.string.home_empty_no_sources_message),
        )
        HomeStateKind.NoRows -> HomeEmptyStateCard(
            modifier = modifier,
            title = stringResource(Res.string.home_empty_no_rows_title),
            message = stringResource(Res.string.home_empty_no_rows_message),
        )
        HomeStateKind.TabEmpty -> HomeEmptyStateCard(
            modifier = modifier,
            title = stringResource(Res.string.home_tab_empty_title),
            message = stringResource(Res.string.home_tab_empty_message),
        )
    }
}
