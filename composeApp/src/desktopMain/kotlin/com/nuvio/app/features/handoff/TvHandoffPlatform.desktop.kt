package com.nuvio.app.features.handoff

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Edit
import com.nuvio.app.features.settings.SettingsGroup
import com.nuvio.app.features.settings.SettingsGroupDivider
import com.nuvio.app.features.settings.SettingsNavigationRow
import com.nuvio.app.features.settings.SettingsSection
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nuvio.app.core.ui.DialogButton
import com.nuvio.app.core.ui.DialogButtonStyle
import com.nuvio.app.core.ui.nuvio
import java.net.InetAddress
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import javax.swing.SwingUtilities
import javax.swing.Timer

@Serializable
internal data class PairedTv(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val platform: String,
    val host: String,
    val mac: String = "",
    val appId: String,
    val certificatePin: String,
    val clientKey: String = "",
) {
    override fun toString() = name
}

internal fun tvWakePacket(mac: String): ByteArray {
    require(mac.matches(Regex("([0-9a-fA-F]{2}[:-]){5}[0-9a-fA-F]{2}"))) {
        "Enter the TV network adapter's MAC address, such as AA:BB:CC:DD:EE:FF."
    }
    val bytes = mac.split(':', '-').map { it.toInt(16).toByte() }.toByteArray()
    require(bytes.any { it != 0.toByte() } && bytes.first().toInt() and 1 == 0)
    return ByteArray(6) { 0xff.toByte() } + ByteArray(96) { bytes[it % 6] }
}

private object TvPickerWindow {
    var picker: TvHandoffPicker? = null
}

internal actual fun openTvHandoff(
    manageDevices: Boolean,
    capture: (String, String) -> TvHandoffRequest?,
    onTransferred: () -> Unit,
    onResumeHere: () -> Unit,
): () -> Unit {
    val owner = Any()
    SwingUtilities.invokeLater {
        TvPickerWindow.picker?.let { existing ->
            if (existing.manageDevices != manageDevices) existing.closePicker()
            else {
                existing.rebind(owner, capture, onTransferred, onResumeHere)
                return@invokeLater
            }
        }
        TvHandoffPicker(owner, manageDevices, capture, onTransferred, onResumeHere).show()
    }
    return {
        val close = { TvPickerWindow.picker?.closeIfOwned(owner); Unit }
        if (SwingUtilities.isEventDispatchThread()) close() else SwingUtilities.invokeLater(close)
    }
}

@Composable
internal actual fun PairedTvSettings(onBack: () -> Unit) {
    val picker = remember { TvHandoffPicker(Any(), true, { _, _ -> null }, {}, {}) }
    DisposableEffect(picker) {
        picker.startSettings()
        onDispose { picker.closePicker() }
    }
    picker.Content()
}

private class TvHandoffPicker(
    private var owner: Any,
    val manageDevices: Boolean,
    private var capture: (String, String) -> TvHandoffRequest?,
    private var onTransferred: () -> Unit,
    private var onResumeHere: () -> Unit,
) {
    private val directory = Path.of(System.getProperty("user.home"), ".nuvio", "tv-handoff")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val devicesFile = directory.resolve("devices.json")
    private val historyFile = directory.resolve("seen-tvs.json")
    private fun rememberAddresses(additions: List<DiscoveredTv>): List<DiscoveredTv> {
        val old = runCatching { json.decodeFromString<List<DiscoveredTv>>(Files.readString(historyFile)) }.getOrDefault(emptyList())
        val all = (additions + old).distinctBy { it.platform to it.host }.take(64)
        privateWrite(historyFile, json.encodeToString(all).toByteArray())
        return all
    }
    private val devices = runCatching {
        json.decodeFromString<List<PairedTv>>(Files.readString(devicesFile)).toMutableList()
    }.getOrDefault(mutableListOf())
    private val worker = Executors.newSingleThreadExecutor { Thread(it, "nuvio-tv-handoff").apply { isDaemon = true } }
    @Volatile private var isOpen = false
    @Volatile private var activeAttempt: TvHandoffAttempt? = null
    @Volatile private var pairingAttempt: TvHandoffAttempt? = null
    private var transferred = false
    @Volatile private var sending = false
    private var selectedTv by mutableStateOf(devices.firstOrNull())
    private var platformKind by mutableStateOf("sony")
    private var tvName by mutableStateOf("My TV")
    private var tvHost by mutableStateOf("")
    private var tvMac by mutableStateOf("")
    private var tvAppId by mutableStateOf("com.nuvio.tv")
    private var statusValue by mutableStateOf("")
    private var status: String
        get() = statusValue
        set(value) { statusValue = value; notifyPlaybackHandoffChanged() }
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
    private var codePrompt by mutableStateOf<CompletableFuture<String?>?>(null)
    private var deviceRevision by mutableStateOf(0)
    private var expiryTimer: Timer? = null

    fun show() {
        isOpen = true
        TvPickerWindow.picker = this
        notifyPlaybackHandoffChanged()
    }

    fun startSettings() {
        isOpen = true
        selectedTv?.let(::populate)
        rememberAddresses(devices.map { DiscoveredTv(it.name, it.host, it.platform) })
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
        worker.submit {
            val known = runCatching { rememberAddresses(addresses) }.getOrDefault(emptyList())
            val found = TvDiscovery.discover(known)
            runCatching { rememberAddresses(found) }
            SwingUtilities.invokeLater {
                if (!isOpen || discoveryRevision != revision || setupPanel != SetupPanel.Discovery) return@invokeLater
                discovering = false; busy(false); nearby = found
                status = if (found.isEmpty()) "Turn the TV on. If discovery is blocked by your network, add its IP address manually." else "Choose a TV to set up."
            }
        }
    }

    private fun populate(tv: PairedTv) {
        platformKind = tv.platform; tvName = tv.name; tvHost = tv.host; tvMac = tv.mac; tvAppId = tv.appId
    }

    private fun busy(value: Boolean) { working = value }

    private fun persistDevices() = privateWrite(devicesFile, json.encodeToString(devices).toByteArray())

    private fun pairTv() {
        val address = tvHost.trim()
        val deviceName = tvName.trim()
        val addressMac = tvMac.trim()
        val packageId = tvAppId.trim()
        val kind = platformKind
        try {
            privateTvAddress(address)
            require(deviceName.isNotBlank() && packageId.matches(Regex("[A-Za-z0-9_.]+"))) { "Enter a TV name and valid app ID." }
            if (addressMac.isNotBlank()) tvWakePacket(addressMac)
        } catch (error: Exception) { status = error.message ?: "Check the TV details."; return }
        val existing = devices.firstOrNull { it.host == address && it.platform == kind }
        if (existing != null) {
            val changed = existing.copy(name = deviceName, mac = addressMac, appId = packageId)
            devices.replaceAll { if (it.id == existing.id) changed else it }
            persistDevices(); rememberAddresses(listOf(DiscoveredTv(deviceName, address, kind)))
            deviceRevision++; selectedTv = changed; setupPanel = SetupPanel.None
            status = "Saved. Existing pairing preserved."
            return
        }
        val attempt = TvHandoffAttempt()
        pairingAttempt = attempt
        busy(true); status = "Approve pairing on the TV."
        worker.submit {
            try {
                val identity = TvIdentity(directory)
                val pairing = if (kind == "sony") {
                    val pin = AndroidTvRemote(identity).pair(address) {
                        val pending = CompletableFuture<String?>()
                        SwingUtilities.invokeLater {
                            if (!isOpen || !attempt.isActive) pending.complete(null)
                            else { pairingCode = ""; codePrompt = pending }
                        }
                        pending.get() ?: error("Pairing cancelled.")
                    }
                    WebOsPairing("", pin)
                } else {
                    WebOsRemote(address, existing?.certificatePin).use { remote ->
                        check(attempt.attach(remote)) { "Pairing cancelled." }
                        remote.register(existing?.clientKey)
                    }
                }
                val device = PairedTv(existing?.id ?: UUID.randomUUID().toString(), deviceName, kind, address,
                    addressMac, packageId, pairing.certificatePin, pairing.clientKey)
                SwingUtilities.invokeLater {
                    if (!isOpen || pairingAttempt !== attempt) return@invokeLater
                    devices.removeAll { it.id == device.id }; devices += device; persistDevices()
                    deviceRevision++; selectedTv = device; setupPanel = SetupPanel.None
                    busy(false); status = "Paired. A compatible TV build is required to receive playback."
                }
            } catch (error: Exception) {
                SwingUtilities.invokeLater {
                    if (isOpen && pairingAttempt === attempt) {
                        busy(false); status = if (error.hasLocalNetworkDenial())
                            "Allow Nuvio in macOS Settings → Privacy & Security → Local Network, then retry."
                        else "Pairing failed. Check the TV is on and try again."
                    }
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
        val senderFile = directory.resolve("sender-id")
        val senderId = if (Files.exists(senderFile)) Files.readString(senderFile) else UUID.randomUUID().toString().also {
            privateWrite(senderFile, it.toByteArray())
        }
        val request = capture(tv.id, senderId) ?: run {
            status = "Sign in to Nuvio and start a movie or episode before sending."; return
        }
        val attempt = TvHandoffAttempt()
        activeAttempt = attempt
        busy(true); sending = true; canResume = true; status = "Connecting to ${tv.name}…"
        expiryTimer = Timer(TV_HANDOFF_TTL_MS.toInt()) {
            if (isCurrent(attempt) && !transferred) { cancelTransfer(); status = "Transfer timed out. Resume here or try again."; busy(false) }
        }.apply { isRepeats = false; start() }
        worker.submit {
            try {
                if (!isCurrent(attempt)) return@submit
                val identity = TvIdentity(directory)
                val approvalCode = if (tv.appId == "space.nuvio.native.legacy" || tv.appId.startsWith("space.nuvio.nativelegacy"))
                    java.util.Base64.getEncoder().encodeToString(java.security.MessageDigest.getInstance("SHA-256")
                        .digest(identity.certificate.publicKey.encoded)).take(6)
                else identity.certificate.fingerprint().take(6).uppercase()
                val localServer = LocalHandoffServer(identity, privateTvAddress(tv.host), request) { update ->
                    SwingUtilities.invokeLater {
                        if (!isCurrent(attempt) || !isOpen) return@invokeLater
                        attempt.receiverReached = true
                        status = when (update.phase) {
                            TvHandoffPhase.PLAYING -> "Playing on ${tv.name}"
                            TvHandoffPhase.FAILED -> when (update.failureCode) {
                                "USER_DECLINED" -> "Transfer declined on TV."
                                "PROFILE_REQUIRED" -> "Select the same profile on TV, then retry."
                                "ACCOUNT_PROFILE_OR_REQUEST_INVALID" -> "Check the TV account and profile, then retry."
                                else -> "TV could not start playback. Try another stream or resume here."
                            }
                            TvHandoffPhase.AWAITING_USER -> "Approve on TV or choose a stream. Desktop code: ${approvalCode}"
                            else -> "${tv.name}: ${update.phase.name.lowercase().replace('_', ' ')}…"
                        }
                        if (update.phase == TvHandoffPhase.PLAYING) {
                            if (transferred) return@invokeLater
                            transferred = true; expiryTimer?.stop(); canResume = false
                            onTransferred()
                            Timer(2_000) { closePicker() }.apply { isRepeats = false; start() }
                        } else if (update.phase == TvHandoffPhase.FAILED) {
                            cancelTransfer(); busy(false)
                        }
                    }
                }
                if (!attempt.attach(localServer, localServer::cancelAndCheckPlayback)) return@submit
                if (!isCurrent(attempt)) return@submit
                val params = localServer.launchParameters
                awaitTvReceiver(
                    isActive = { isCurrent(attempt) }, receiverReached = { attempt.receiverReached },
                    retryable = { error -> !error.hasLocalNetworkDenial() &&
                        !error.hasTvIdentityFailure() },
                    launch = {
                        if (tv.platform == "sony") {
                            val link = androidTvHandoffLink(tv.appId, params.toString())
                            try {
                                attempt.attach(AndroidTvRemote(identity).launch(tv.host, tv.certificatePin, link))
                            } catch (error: Exception) {
                                // Sony can wake over its authenticated remote. Avoid broadcasting
                                // unless that connection actually failed and the attempt is still active.
                                if (isCurrent(attempt) && tv.mac.isNotBlank() &&
                                    !error.hasTvIdentityFailure() && !error.hasLocalNetworkDenial())
                                    runCatching { sendTvWakePackets(privateTvAddress(tv.host), tv.mac) }
                                throw error
                            }
                        } else {
                            if (tv.mac.isNotBlank()) runCatching { sendTvWakePackets(privateTvAddress(tv.host), tv.mac) }
                            val remote = WebOsRemote(tv.host, tv.certificatePin)
                            if (attempt.attach(remote)) {
                                try {
                                    remote.register(tv.clientKey)
                                    if (isCurrent(attempt)) remote.launch(tv.appId,
                                        kotlinx.serialization.json.buildJsonObject { put("handoff", params) })
                                } catch (error: Exception) { remote.close(); throw error }
                            }
                        }
                    },
                )
                SwingUtilities.invokeLater {
                    if (isCurrent(attempt) && !transferred && !attempt.receiverReached)
                        status = "Approve on ${tv.name}. Desktop code: ${approvalCode}"
                }
            } catch (error: Exception) { fail(error, attempt) }
        }
    }

    private fun isCurrent(attempt: TvHandoffAttempt): Boolean = activeAttempt === attempt && attempt.isActive && sending

    private fun fail(error: Exception, attempt: TvHandoffAttempt? = null) = SwingUtilities.invokeLater {
        if (attempt != null && !isCurrent(attempt)) return@invokeLater
        if (isOpen && !transferred) {
            cancelTransfer(); busy(false)
            status = if (error.hasLocalNetworkDenial())
                "Allow Nuvio in macOS Settings → Privacy & Security → Local Network, then retry."
            else if (generateSequence<Throwable>(error) { it.cause }.take(8).any { it is java.net.NoRouteToHostException })
                "TV is unreachable. Check its power/IP address and Nuvio’s Local Network permission, then retry."
            else if (error.hasTvIdentityFailure()) "TV identity could not be verified. Pair this TV again."
            else when (error) {
                is java.net.SocketTimeoutException, is java.net.ConnectException ->
                    "TV did not respond. Turn it on, check its IP address and retry."
                is javax.net.ssl.SSLException, is SecurityException ->
                    "TV identity could not be verified. Pair this TV again."
                else -> "TV connection failed. Check the TV is on and retry, or resume here."
            }
        }
    }

    private fun cancelTransfer() {
        val attempt = activeAttempt
        activeAttempt = null
        sending = false
        expiryTimer?.stop()
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
        devices.map { com.nuvio.app.features.player.PlayerControlTvDevice(it.name, it.platform) },
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
        codePrompt?.complete(null); worker.shutdownNow()
        if (TvPickerWindow.picker === this) {
            TvPickerWindow.picker = null
        }
        notifyPlaybackHandoffChanged()
    }

    fun closePicker() {
        cleanup()
    }

    fun closeIfOwned(owner: Any) {
        if (this.owner === owner) closePicker()
    }
}

internal data class TvHandoffPlaybackView(
    val devices: List<com.nuvio.app.features.player.PlayerControlTvDevice>,
    val ready: Boolean = false, val busy: Boolean = false, val canResume: Boolean = false, val status: String = "",
)

internal fun tvHandoffPlaybackView(): TvHandoffPlaybackView {
    TvPickerWindow.picker?.takeUnless { it.manageDevices }?.let { return it.playbackView() }
    // A picker is prepared asynchronously after account verification. Display names never include pairing credentials.
    val devices = runCatching {
        val path = Path.of(System.getProperty("user.home"), ".nuvio", "tv-handoff", "devices.json")
        Json { ignoreUnknownKeys = true }.decodeFromString<List<PairedTv>>(Files.readString(path))
            .map { com.nuvio.app.features.player.PlayerControlTvDevice(it.name, it.platform) }
    }.getOrDefault(emptyList())
    return TvHandoffPlaybackView(devices)
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

internal fun Throwable.hasLocalNetworkDenial(): Boolean =
    generateSequence(this) { it.cause }.take(8).any {
        it is java.net.SocketException &&
            (it.message.orEmpty().contains("permission denied", ignoreCase = true) ||
             it.message.orEmpty().contains("operation not permitted", ignoreCase = true))
    }
