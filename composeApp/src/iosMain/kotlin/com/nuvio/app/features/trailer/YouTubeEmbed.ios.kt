package com.nuvio.app.features.trailer
import androidx.compose.runtime.Composable
internal actual object YouTubeEmbed {
    actual val supported = false
    @Composable actual fun Popup(videoUrl: String?, title: String, type: String, contentTitle: String, loading: Boolean, error: String?, onDismiss: () -> Unit, onRetry: (() -> Unit)?) = Unit
}
