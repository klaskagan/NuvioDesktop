package com.nuvio.app.features.details

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.nuvio.app.features.handoff.TvHandoffRequest
import com.nuvio.app.features.handoff.openTvHandoff
import com.nuvio.app.features.handoff.resolveTvHandoffAccountId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Details' On TV action: opening the picker verifies the Nuvio account and prepares a handoff for
 * what the primary button would play; choosing a TV sends it. Closing the picker cancels whatever is
 * still pending. [request] is read when a TV is chosen, so it always describes the current pick.
 */
@Stable
internal class DetailTvHandoff(
    private val scope: CoroutineScope,
    val onManageDevices: () -> Unit,
    private val request: (targetDeviceId: String, senderDeviceId: String, accountId: String) -> TvHandoffRequest?,
) {
    /** The account lookup failed; the picker says so instead of listing TVs it can't send to. */
    var accountFailed by mutableStateOf(false)
        private set

    private var preparing: Job? = null
    private var closeHandoff: (() -> Unit)? = null

    fun open() {
        close()
        accountFailed = false
        preparing = scope.launch {
            val accountId = resolveTvHandoffAccountId()
            if (accountId == null) {
                accountFailed = true
                return@launch
            }
            closeHandoff = openTvHandoff(
                capture = { target, sender -> request(target, sender, accountId) },
                onTransferred = {},
                onResumeHere = {},
            )
        }
    }

    fun close() {
        preparing?.cancel()
        preparing = null
        closeHandoff?.invoke()
        closeHandoff = null
    }
}
