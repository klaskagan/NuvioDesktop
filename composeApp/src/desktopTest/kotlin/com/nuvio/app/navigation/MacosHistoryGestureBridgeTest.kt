package com.nuvio.app.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import java.awt.Toolkit
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertNotNull

class MacosHistoryGestureBridgeTest {
    @get:Rule val compose = createComposeRule()

    @Test fun bundledNativeBridgeCanInstallAndRemoveItsAppKitMonitor() {
        assumeTrue(System.getProperty("os.name").contains("mac", ignoreCase = true))
        Toolkit.getDefaultToolkit()
        compose.setContent { Box(Modifier) }
        compose.runOnIdle {
            repeat(3) {
                val unsubscribe = MacosHistoryGestureBridge.subscribe {}
                assertNotNull(unsubscribe, "The bundled JNI library must load and install its monitor")
                unsubscribe()
            }
        }
    }
}
