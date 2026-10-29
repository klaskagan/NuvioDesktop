package com.nuvio.app.features.settings

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.*
import androidx.compose.material3.*
import com.nuvio.app.features.go3.Go3Catalog

internal fun LazyListScope.go3SettingsContent(isTablet: Boolean) {
    item {
        val status by Go3Catalog.status.collectAsState()
        var confirmReset by remember {mutableStateOf(false)}
        SettingsSection(title="Go3",isTablet=isTablet) {
            SettingsGroup(isTablet=isTablet) {
                SettingsNavigationRow(title="Sign in / Open Go3",description=status,isTablet=isTablet,onClick={Go3Catalog.action("signin")})
                SettingsGroupDivider(isTablet=isTablet)
                SettingsNavigationRow(title="Reset Go3 session",description="Sign out on this Mac. Your Nuvio account is unaffected.",isTablet=isTablet,onClick={confirmReset=true})
            }
        }
        if(confirmReset) AlertDialog(onDismissRequest={confirmReset=false},title={Text("Reset Go3 session?")},
            text={Text("This clears this player’s saved Go3 login. You will need to sign in again.")},
            confirmButton={TextButton(onClick={confirmReset=false;Go3Catalog.action("signout")}){Text("Reset")}},
            dismissButton={TextButton(onClick={confirmReset=false}){Text("Cancel")}})
    }
}
