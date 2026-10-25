package com.nuvio.app.features.player

import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import androidx.compose.ui.unit.dp
import java.io.File

fun main() {
    MoviePlayerControlsFixture.seedMetadata()
    val file = File(checkNotNull(System.getProperty("nuvio.qa.media")))
    check(file.isFile)
    application {
        Window(onCloseRequest = ::exitApplication, title = "Nuvio Movie Controls QA", state = rememberWindowState(width = 1100.dp, height = 750.dp)) {
            var playing by remember { mutableStateOf(true) }
            if (playing) MoviePlayerControlsFixture.Content(file) { playing = false }
            else Text("Playback closed")
        }
    }
}
