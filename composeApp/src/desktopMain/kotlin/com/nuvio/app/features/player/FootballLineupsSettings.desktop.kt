package com.nuvio.app.features.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import org.jetbrains.compose.resources.stringResource
import nuvio.composeapp.generated.resources.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.nuvio.app.features.player.FootballKeyStore
import com.nuvio.app.features.player.PitchApiProvider
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException

@Composable
internal actual fun FootballLineupsSettings(isTablet: Boolean) {
    val footballLineupsText = stringResource(Res.string.football_lineups_football_lineups)
    val pitchApiKeyDescriptionText = stringResource(Res.string.football_lineups_pitchapi_key_description)
    val apiKeyText = stringResource(Res.string.football_lineups_api_key)
    val keyValidatedAndSavedText = stringResource(Res.string.football_lineups_key_validated_and_saved)
    val unableToValidateCheckYourKeyText = stringResource(Res.string.football_lineups_unable_to_validate_check_your_key)
    val validateAndSaveText = stringResource(Res.string.football_lineups_validate_and_save)
    val keyRemovedText = stringResource(Res.string.football_lineups_key_removed)
    val removeText = stringResource(Res.string.football_lineups_remove)
    var key by remember { mutableStateOf(FootballKeyStore.read()) }
    var message by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    SettingsSection(title = footballLineupsText, isTablet = isTablet) {
        SettingsGroup(isTablet = isTablet) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(pitchApiKeyDescriptionText)
                OutlinedTextField(key, { key = it }, label = { Text(apiKeyText) },
                    visualTransformation = PasswordVisualTransformation(), singleLine = true, enabled = !busy)
                Row {
                    TextButton(enabled = !busy && key.isNotBlank(), onClick = {
                        scope.launch {
                            busy = true
                            try {
                                PitchApiProvider(key.trim()).use { it.validate() }
                                FootballKeyStore.save(key)
                                message = keyValidatedAndSavedText
                            } catch (e: CancellationException) { throw e }
                            catch (_: Exception) { message = unableToValidateCheckYourKeyText }
                            finally { busy = false }
                        }
                    }) { Text(validateAndSaveText) }
                    TextButton(enabled = !busy, onClick = { key = ""; FootballKeyStore.save(""); message = keyRemovedText }) { Text(removeText) }
                }
                if (message.isNotEmpty()) Text(message)
            }
        }
    }
}
