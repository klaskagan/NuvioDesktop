package com.nuvio.app.features.handoff

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nuvio.app.core.ui.DialogButton
import com.nuvio.app.core.ui.DialogButtonStyle
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.features.player.PlayerControlTvDevice
import com.nuvio.app.features.settings.SettingsGroup
import com.nuvio.app.features.settings.SettingsGroupDivider
import com.nuvio.app.features.settings.SettingsNavigationRow
import com.nuvio.app.features.settings.SettingsSection
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject

/** Whether this platform can pair TVs and send playback to them. */
internal val tvHandoffSupported: Boolean get() = platformTvHandoffTransport() != null

private object TvPickerWindow {
    var picker: TvHandoffPicker? = null
}

/** Settings manages pairings; playback captures only when a saved TV is selected. */
internal fun openTvHandoff(
    manageDevices: Boolean = false,
    capture: (targetDeviceId: String, senderDeviceId: String) -> TvHandoffRequest?,
    onTransferred: () -> Unit,
    onResumeHere: () -> Unit,
): () -> Unit {
    val transport = platformTvHandoffTransport() ?: return {}
    val owner = Any()
    val existing = TvPickerWindow.picker
    if (existing != null && existing.manageDevices != manageDevices) existing.closePicker()
    if (existing != null && existing.manageDevices == manageDevices) {
        existing.rebind(owner, capture, onTransferred, onResumeHere)
    } else {
        TvHandoffPicker(transport, owner, manageDevices, capture, onTransferred, onResumeHere).show()
    }
    return { TvPickerWindow.picker?.closeIfOwned(owner) }
}

/** Embedded in the existing Settings navigation surface. */
@Composable
internal fun PairedTvSettings(onBack: () -> Unit) {
    val transport = remember { platformTvHandoffTransport() } ?: return
    val picker = remember { TvHandoffPicker(transport, Any(), true, { _, _ -> null }, {}, {}) }
    DisposableEffect(picker) {
        picker.startSettings()
        onDispose { picker.closePicker() }
    }
    picker.Content()
}

private class TvHandoffPicker(
    private val transport: TvHandoffTransport,
    private var owner: Any,
    val manageDevices: Boolean,
    private var capture: (String, String) -> TvHandoffRequest?,
    private var onTransferred: () -> Unit,
    private var onResumeHere: () -> Unit,
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private fun rememberAddresses(additions: List<DiscoveredTv>): List<DiscoveredTv> {
        val old = runCatching {
            json.decodeFromString<List<DiscoveredTv>>(transport.readFile(HISTORY_FILE) ?: "[]")
        }.getOrDefault(emptyList())
        val all = (additions + old).distinctBy { it.platform to it.host }.take(64)
        transport.writeFile(HISTORY_FILE, json.encodeToString(all))
        return all
    }
    private val devices = loadPairedTvs(transport).toMutableList()
    private var isOpen = false
    private var activeAttempt: TvHandoffAttempt? = null
    private var pairingAttempt: TvHandoffAttempt? = null
    private var transferred = false
    private var sending = false
    private var selectedTv by mutableStateOf(devices.firstOrNull())
    private var platformKind by mutableStateOf("sony")
    private var tvName by mutableStateOf("My TV")
    private var tvHost by mutableStateOf("")
    private var tvMac by mutableStateOf("")
    private var tvAppId by mutableStateOf("com.nuvio.tv")
    private var statusValue by mutableStateOf("")
    var status: String
        get() = statusValue
        private set(value) { statusValue = value; notifyPlaybackHandoffChanged() }
    private var workingValue by mutableStateOf(false)
    private var working: Boolean
        get() = workingValue
        set(value) { workingValue = value; notifyPlaybackHandoffChanged() }
    private var canResumeValue by mutableStateOf(false)
    private var canResume: Boolean
        get() = canResumeValue
        set(value) { canResumeValue = value; notifyPlaybackHandoffChanged() }
    private enum class SetupPanel { None, Discovery, Editor }
    private var setupPanel by mutableStateOf(SetupPanel.None)
    private var discovering by mutableStateOf(false)
    private var discoveryRevision = 0L
    private var nearby by mutableStateOf(emptyList<DiscoveredTv>())
    private var pairingCode by mutableStateOf("")
    private var codePrompt by mutableStateOf<CompletableDeferred<String?>?>(null)
    private var deviceRevision by mutableStateOf(0)
    private var expiryTimer: Job? = null

    fun show() {
        isOpen = true
        TvPickerWindow.picker = this
        notifyPlaybackHandoffChanged()
    }

    fun startSettings() {
        isOpen = true
        selectedTv?.let(::populate)
        runCatching { rememberAddresses(devices.map { DiscoveredTv(it.name, it.host, it.platform) }) }
    }

    private fun editTv(tv: PairedTv?) {
        discoveryRevision++
        discovering = false
        busy(false)
        nearby = emptyList()
        status = ""
        selectedTv = tv
        if (tv != null) populate(tv) else {
            platformKind = "sony"; tvName = "My TV"; tvHost = ""; tvMac = ""; tvAppId = "com.nuvio.tv"
        }
        setupPanel = SetupPanel.Editor
    }

    private fun cancelSetup() {
        discoveryRevision++
        pairingAttempt?.close(); pairingAttempt = null
        codePrompt?.complete(null); codePrompt = null
        discovering = false; busy(false)
        nearby = emptyList(); setupPanel = SetupPanel.None; status = ""
    }

    @Composable
    fun Content() {
        val tokens = MaterialTheme.nuvio
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Set up a TV once. Both devices must be on the same home network.",
                style = MaterialTheme.typography.bodyMedium, color = tokens.colors.textSecondary)
            SettingsSection(title = "TVs", isTablet = true) {
                SettingsGroup(isTablet = true) {
                    key(deviceRevision) {
                        if (devices.isEmpty()) Text("No paired TVs yet.", modifier = Modifier.padding(20.dp),
                            color = tokens.colors.textMuted)
                        devices.forEachIndexed { index, tv ->
                            if (index > 0) SettingsGroupDivider(isTablet = true)
                            SettingsNavigationRow(title = tv.name,
                                description = if (tv.platform == "sony") "Android TV" else "LG webOS",
                                isTablet = true, enabled = !working || discovering,
                                trailingContent = {
                                    IconButton(onClick = { editTv(tv) }, enabled = !working || discovering,
                                        modifier = Modifier.size(36.dp)) {
                                        Icon(Icons.Rounded.Edit, contentDescription = "Edit ${tv.name}",
                                            modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                                    }
                                }, onClick = { editTv(tv) })
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                DialogButton("Find TVs", { discoverTvs() }, enabled = !working)
                DialogButton("Add TV", { editTv(null) }, enabled = !working || discovering)
            }
            val prompt = codePrompt
            if (prompt != null) {
                SettingsGroup(isTablet = true) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Enter the code shown on Sony")
                        OutlinedTextField(pairingCode, { pairingCode = it }, singleLine = true, label = { Text("Pairing code") })
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            DialogButton("Pair", { prompt.complete(pairingCode); codePrompt = null }, style = DialogButtonStyle.Primary)
                            DialogButton("Cancel", { cancelSetup() })
                        }
                    }
                }
            } else when (setupPanel) {
                SetupPanel.Discovery -> SettingsSection(title = "Available TVs", isTablet = true) {
                    SettingsGroup(isTablet = true) {
                        nearby.forEachIndexed { index, tv ->
                            if (index > 0) SettingsGroupDivider(isTablet = true)
                            SettingsNavigationRow(title = tv.name,
                                description = tv.host + if (tv.previouslySeen) " · previously saved address" else "",
                                isTablet = true, enabled = !working,
                                onClick = {
                                    val paired = devices.firstOrNull { it.host == tv.host && it.platform == tv.platform }
                                    editTv(paired)
                                    if (paired == null) {
                                        platformKind = tv.platform; tvName = tv.name; tvHost = tv.host
                                        tvAppId = if (tv.platform == "sony") "com.nuvio.tv" else "space.nuvio.webos"
                                    }
                                })
                        }
                        if (nearby.isEmpty()) Text(if (discovering) "Looking for TVs…" else "No TVs found.",
                            modifier = Modifier.padding(20.dp), color = tokens.colors.textMuted)
                    }
                }
                SetupPanel.Editor -> SettingsSection(title = if (selectedTv == null) "Add TV" else "TV details", isTablet = true) {
                    SettingsGroup(isTablet = true) {
                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf("sony" to "Android TV", "lg" to "LG webOS").forEach { (kind, label) ->
                                    FilterChip(selected = platformKind == kind, onClick = {
                                        platformKind = kind; tvAppId = if (kind == "sony") "com.nuvio.tv" else "space.nuvio.webos"
                                    }, label = { Text(label) }, enabled = !working)
                                }
                            }
                            OutlinedTextField(tvName, { tvName = it }, label = { Text("TV name") }, enabled = !working, singleLine = true, modifier = Modifier.fillMaxWidth())
                            OutlinedTextField(tvHost, { tvHost = it }, label = { Text("Local IP address") }, enabled = !working, singleLine = true, modifier = Modifier.fillMaxWidth())
                            OutlinedTextField(tvMac, { tvMac = it }, label = { Text("MAC address for wake · optional") }, enabled = !working, singleLine = true, modifier = Modifier.fillMaxWidth())
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                val choices = if (platformKind == "lg") listOf("space.nuvio.webos" to "Nuvio", "space.nuvio.native.legacy" to "Native Legacy")
                                    else listOf("com.nuvio.tv" to "Nuvio", "space.nuvio.nativelegacy.strictdebug" to "Native Legacy")
                                choices.forEach { (id, label) ->
                                    FilterChip(selected = tvAppId == id, onClick = { tvAppId = id }, label = { Text(label) }, enabled = !working)
                                }
                            }
                            OutlinedTextField(tvAppId, { tvAppId = it }, label = { Text("Nuvio app ID") }, enabled = !working, singleLine = true, modifier = Modifier.fillMaxWidth())
                            Text("Requires a receiver build with Continue on TV support.", style = MaterialTheme.typography.bodySmall, color = tokens.colors.textSecondary)
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                DialogButton(if (selectedTv != null) "Save TV" else "Pair TV", { pairTv() }, enabled = !working, style = DialogButtonStyle.Primary)
                                if (selectedTv != null) DialogButton("Forget", {
                                    devices.removeAll { it.id == selectedTv?.id }; persistDevices(); deviceRevision++
                                    selectedTv = null; setupPanel = SetupPanel.None
                                    status = "Pairing forgotten. Its address remains available for rediscovery."
                                }, enabled = !working, style = DialogButtonStyle.Destructive)
                                DialogButton("Cancel", { cancelSetup() })
                            }
                        }
                    }
                }
                SetupPanel.None -> Unit
            }
            if (working) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (status.isNotBlank()) Text(status, style = MaterialTheme.typography.bodyMedium, color = tokens.colors.textSecondary)
        }
    }

    private fun discoverTvs() {
        val revision = ++discoveryRevision
        setupPanel = SetupPanel.Discovery; nearby = emptyList()
        discovering = true; busy(true); status = ""
        val addresses = devices.map { DiscoveredTv(it.name, it.host, it.platform) }
        scope.launch {
            val known = runCatching { rememberAddresses(addresses) }.getOrDefault(emptyList())
            val found = try { transport.discover(known) } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) { emptyList() }
            runCatching { rememberAddresses(found) }
            if (!isOpen || discoveryRevision != revision || setupPanel != SetupPanel.Discovery) return@launch
            discovering = false; busy(false); nearby = found
            status = if (found.isEmpty()) {
                "Turn the TV on. If discovery is blocked by your network, add its IP address manually." +
                    transport.lastDiscoveryNote?.let { " ($it)" }.orEmpty()
            } else "Choose a TV to set up."
        }
    }

    private fun populate(tv: PairedTv) {
        platformKind = tv.platform; tvName = tv.name; tvHost = tv.host; tvMac = tv.mac; tvAppId = tv.appId
    }

    private fun busy(value: Boolean) { working = value }

    private fun persistDevices() = transport.writeFile(DEVICES_FILE, json.encodeToString(devices.toList()))

    private fun pairTv() {
        val address = tvHost.trim()
        val deviceName = tvName.trim()
        val addressMac = tvMac.trim()
        val packageId = tvAppId.trim()
        val kind = platformKind
        try {
            requirePrivateTvAddress(address)
            require(deviceName.isNotBlank() && packageId.matches(Regex("[A-Za-z0-9_.]+"))) { "Enter a TV name and valid app ID." }
            if (addressMac.isNotBlank()) tvWakePacket(addressMac)
        } catch (error: Exception) { status = error.message ?: "Check the TV details."; return }
        val existing = devices.firstOrNull { it.host == address && it.platform == kind }
        if (existing != null) {
            val changed = existing.copy(name = deviceName, mac = addressMac, appId = packageId)
            devices.indices.forEach { if (devices[it].id == existing.id) devices[it] = changed }
            persistDevices(); runCatching { rememberAddresses(listOf(DiscoveredTv(deviceName, address, kind))) }
            deviceRevision++; selectedTv = changed; setupPanel = SetupPanel.None
            status = "Saved. Existing pairing preserved."
            return
        }
        val attempt = TvHandoffAttempt()
        pairingAttempt = attempt
        busy(true); status = "Approve pairing on the TV."
        scope.launch {
            try {
                val pairing = if (kind == "sony") {
                    val pin = transport.pairAndroidTv(address, attempt) {
                        // Transports may ask from a network thread; prompt state belongs to the UI thread.
                        val pending = withContext(Dispatchers.Main) {
                            if (!isOpen || !attempt.isActive) null else CompletableDeferred<String?>().also {
                                pairingCode = ""; codePrompt = it
                            }
                        }
                        pending?.await()
                    }
                    WebOsPairing("", pin)
                } else transport.pairWebOs(address, attempt)
                val device = PairedTv(newTvHandoffId(), deviceName, kind, address,
                    addressMac, packageId, pairing.certificatePin, pairing.clientKey)
                if (!isOpen || pairingAttempt !== attempt) return@launch
                devices.removeAll { it.id == device.id }; devices += device; persistDevices()
                deviceRevision++; selectedTv = device; setupPanel = SetupPanel.None
                busy(false); status = "Paired. A compatible TV build is required to receive playback."
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (isOpen && pairingAttempt === attempt) {
                    busy(false); status = if (transport.classify(error) == TvHandoffFailure.LocalNetworkDenied)
                        transport.localNetworkSettingsHint
                    else "Pairing failed. Check the TV is on and try again."
                }
            } finally { attempt.close() }
        }
    }

    private fun sendToTv() {
        val tv = selectedTv ?: return
        // Edited addresses/credentials must be saved through pairing first.
        if (tv.host != tvHost.trim() || tv.appId != tvAppId.trim() || tv.mac != tvMac.trim()) {
            status = "Save your changes with Pair / Save TV first."; return
        }
        val senderId = transport.readFile(SENDER_FILE)?.takeIf { it.isNotBlank() }
            ?: newTvHandoffId().also { transport.writeFile(SENDER_FILE, it) }
        val request = capture(tv.id, senderId) ?: run {
            status = "Sign in to Nuvio and start a movie or episode before sending."; return
        }
        val attempt = TvHandoffAttempt()
        activeAttempt = attempt
        busy(true); sending = true; canResume = true; status = "Connecting to ${tv.name}…"
        expiryTimer = scope.launch {
            delay(TV_HANDOFF_TTL_MS)
            if (isCurrent(attempt) && !transferred) { cancelTransfer(); status = "Transfer timed out. Resume here or try again."; busy(false) }
        }
        scope.launch {
            try {
                if (!isCurrent(attempt)) return@launch
                val identity = transport.identity()
                val approvalCode = tvHandoffApprovalCode(tv.appId, identity.certificateDer,
                    identity.subjectPublicKeyInfo, transport::sha256)
                val localServer = transport.startServer(tv.host, request) { update ->
                    scope.launch { onReceiverUpdate(tv, attempt, update, approvalCode) }
                }
                if (!attempt.attach(localServer, localServer::cancelAndCheckPlayback)) return@launch
                if (!isCurrent(attempt)) return@launch
                val params = localServer.launchParameters
                awaitTvReceiver(
                    isActive = { isCurrent(attempt) }, receiverReached = { attempt.receiverReached },
                    retryable = { error -> transport.classify(error).let {
                        it != TvHandoffFailure.LocalNetworkDenied && it != TvHandoffFailure.IdentityFailure
                    } },
                    launch = {
                        if (tv.platform == "sony") {
                            val link = androidTvHandoffLink(tv.appId, params.toString())
                            try {
                                transport.launchAndroidTv(tv, link, attempt)
                            } catch (error: Exception) {
                                // Sony can wake over its authenticated remote. Avoid broadcasting
                                // unless that connection actually failed and the attempt is still active.
                                val failure = transport.classify(error)
                                if (error !is CancellationException && isCurrent(attempt) && tv.mac.isNotBlank() &&
                                    failure != TvHandoffFailure.IdentityFailure && failure != TvHandoffFailure.LocalNetworkDenied)
                                    runCatching { transport.wake(tv) }
                                throw error
                            }
                        } else {
                            if (tv.mac.isNotBlank()) runCatching { transport.wake(tv) }
                            transport.launchWebOs(tv, buildJsonObject { put("handoff", params) }, attempt)
                        }
                    },
                )
                if (isCurrent(attempt) && !transferred && !attempt.receiverReached)
                    status = "Approve on ${tv.name}. Desktop code: $approvalCode"
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) { fail(error, attempt) }
        }
    }

    private fun onReceiverUpdate(tv: PairedTv, attempt: TvHandoffAttempt, update: TvHandoffUpdate, approvalCode: String) {
        if (!isCurrent(attempt) || !isOpen) return
        attempt.receiverReached = true
        status = when (update.phase) {
            TvHandoffPhase.PLAYING -> "Playing on ${tv.name}"
            TvHandoffPhase.FAILED -> when (update.failureCode) {
                "USER_DECLINED" -> "Transfer declined on TV."
                "PROFILE_REQUIRED" -> "Select the same profile on TV, then retry."
                "ACCOUNT_PROFILE_OR_REQUEST_INVALID" -> "Check the TV account and profile, then retry."
                else -> "TV could not start playback. Try another stream or resume here."
            }
            TvHandoffPhase.AWAITING_USER -> "Approve on TV or choose a stream. Desktop code: $approvalCode"
            else -> "${tv.name}: ${update.phase.name.lowercase().replace('_', ' ')}…"
        }
        if (update.phase == TvHandoffPhase.PLAYING) {
            if (transferred) return
            transferred = true; expiryTimer?.cancel(); canResume = false
            onTransferred()
            scope.launch { delay(2_000); closePicker() }
        } else if (update.phase == TvHandoffPhase.FAILED) {
            cancelTransfer(); busy(false)
        }
    }

    private fun isCurrent(attempt: TvHandoffAttempt): Boolean = activeAttempt === attempt && attempt.isActive && sending

    private fun fail(error: Exception, attempt: TvHandoffAttempt? = null) {
        if (attempt != null && !isCurrent(attempt)) return
        if (isOpen && !transferred) {
            cancelTransfer(); busy(false)
            status = when (transport.classify(error)) {
                TvHandoffFailure.LocalNetworkDenied -> transport.localNetworkSettingsHint
                TvHandoffFailure.Unreachable ->
                    "TV is unreachable. Check its power/IP address and Nuvio’s Local Network permission, then retry."
                TvHandoffFailure.IdentityFailure -> "TV identity could not be verified. Pair this TV again."
                TvHandoffFailure.Timeout -> "TV did not respond. Turn it on, check its IP address and retry."
                TvHandoffFailure.Other -> "TV connection failed. Check the TV is on and retry, or resume here."
            }
        }
    }

    private fun cancelTransfer() {
        val attempt = activeAttempt
        activeAttempt = null
        sending = false
        expiryTimer?.cancel()
        if (!transferred && attempt?.cancelAndCheckPlayback() == true) {
            transferred = true
            onTransferred()
        } else attempt?.close()
    }

    fun rebind(owner: Any, capture: (String, String) -> TvHandoffRequest?, onTransferred: () -> Unit, onResumeHere: () -> Unit) {
        if (!sending) {
            this.owner = owner
            this.capture = capture; this.onTransferred = onTransferred; this.onResumeHere = onResumeHere
        }
    }

    fun playbackView() = TvHandoffPlaybackView(
        devices.map { PlayerControlTvDevice(it.name, it.platform) },
        ready = isOpen && !manageDevices, busy = working, canResume = canResume && !transferred, status = status,
    )

    fun selectDevice(index: Int) {
        if (!isOpen || manageDevices || working || transferred) return
        val tv = devices.getOrNull(index) ?: return
        selectedTv = tv; populate(tv); sendToTv()
    }

    fun resumePlayback() {
        if (!canResume || transferred || manageDevices) return
        cancelTransfer()
        if (!transferred) { onResumeHere(); closePicker() }
    }

    private fun cleanup() {
        if (!isOpen) return
        isOpen = false
        cancelTransfer(); pairingAttempt?.close(); pairingAttempt = null
        codePrompt?.complete(null)
        if (TvPickerWindow.picker === this) {
            TvPickerWindow.picker = null
        }
        notifyPlaybackHandoffChanged()
        scope.cancel()
    }

    fun closePicker() {
        cleanup()
    }

    fun closeIfOwned(owner: Any) {
        if (this.owner === owner) closePicker()
    }
}

private const val DEVICES_FILE = "devices.json"
private const val HISTORY_FILE = "seen-tvs.json"
private const val SENDER_FILE = "sender-id"

private fun loadPairedTvs(transport: TvHandoffTransport): List<PairedTv> = runCatching {
    Json { ignoreUnknownKeys = true }.decodeFromString<List<PairedTv>>(transport.readFile(DEVICES_FILE) ?: "[]")
}.getOrDefault(emptyList())

internal data class TvHandoffPlaybackView(
    val devices: List<PlayerControlTvDevice>,
    val ready: Boolean = false, val busy: Boolean = false, val canResume: Boolean = false, val status: String = "",
)

internal fun tvHandoffPlaybackView(): TvHandoffPlaybackView {
    TvPickerWindow.picker?.takeUnless { it.manageDevices }?.let { return it.playbackView() }
    // A picker is prepared asynchronously after account verification. Display names never include pairing credentials.
    val transport = platformTvHandoffTransport() ?: return TvHandoffPlaybackView(emptyList())
    return TvHandoffPlaybackView(loadPairedTvs(transport).map { PlayerControlTvDevice(it.name, it.platform) })
}

internal fun selectTvHandoffDevice(index: Int) { TvPickerWindow.picker?.selectDevice(index) }
internal fun resumeTvHandoffPlayback() { TvPickerWindow.picker?.resumePlayback() }

private var playbackObserverOwner: Any? = null
private var playbackObserver: (() -> Unit)? = null
internal fun observeTvHandoffPlayback(owner: Any, changed: () -> Unit) {
    playbackObserverOwner = owner; playbackObserver = changed
}
internal fun stopObservingTvHandoffPlayback(owner: Any) {
    if (playbackObserverOwner === owner) { playbackObserverOwner = null; playbackObserver = null }
}
private fun notifyPlaybackHandoffChanged() { playbackObserver?.invoke() }
