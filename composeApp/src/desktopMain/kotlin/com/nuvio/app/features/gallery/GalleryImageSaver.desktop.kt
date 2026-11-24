package com.nuvio.app.features.gallery

import java.net.HttpURLConnection
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val MaxImageBytes = 40 * 1024 * 1024

/** Raw bytes (the shared HTTP helper decodes text), written to ~/Downloads under a free name. */
internal actual suspend fun saveGalleryImage(url: String, fileName: String): String? = withContext(Dispatchers.IO) {
    runCatching {
        val connection = URI(url).toURL().openConnection() as HttpURLConnection
        connection.connectTimeout = 10_000
        connection.readTimeout = 20_000
        val bytes = try {
            if (connection.responseCode !in 200..299) return@runCatching null
            connection.inputStream.use { input -> input.readNBytes(MaxImageBytes) }
        } finally {
            connection.disconnect()
        }
        if (bytes.isEmpty()) return@runCatching null
        val folder = Path.of(System.getProperty("user.home"), "Downloads").also { Files.createDirectories(it) }
        val safe = fileName.replace(Regex("""[\\/:*?"<>|]"""), " ").trim().ifBlank { "image" }
        val extension = url.substringBefore('?').substringAfterLast('.', "jpg").take(4).ifBlank { "jpg" }
        var target = folder.resolve("$safe.$extension")
        var index = 2
        while (Files.exists(target)) target = folder.resolve("$safe ($index).$extension").also { index++ }
        Files.write(target, bytes)
        target.fileName.toString()
    }.getOrNull()
}
