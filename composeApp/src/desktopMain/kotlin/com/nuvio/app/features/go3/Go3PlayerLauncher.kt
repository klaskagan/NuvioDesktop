package com.nuvio.app.features.go3

import com.nuvio.app.Go3Playback
import java.io.File
import java.nio.file.Files
import kotlinx.serialization.json.*

/** Single local browser service. Provider credentials never cross the process protocol. */
object Go3PlayerLauncher {
    private var process: Process? = null
    fun install() {
        if (!System.getProperty("os.name").contains("Mac",true)) return
        Go3Playback.open = ::open
        val catalog = Go3PublicCatalog()
        Go3Catalog.loader = catalog::load
        Go3Catalog.programmeLoader = catalog::programme
        Go3Catalog.accountAction = { action -> runCatching { send(buildJsonObject {put("action",action)}) }.isSuccess }
        Runtime.getRuntime().addShutdownHook(Thread { synchronized(this) { process?.outputStream?.close(); process?.destroy() } })
    }
    @Synchronized private fun ensureStarted(): Process {
        process?.takeIf {it.isAlive}?.let {return it}
        val support = File(System.getProperty("user.home"), "Library/Application Support/Nuvio")
        val workingDirectory = File(System.getProperty("user.dir"))
        val helper = Go3RuntimePaths.helper(
            System.getenv("NUVIO_GO3_HELPER"),
            System.getProperty("compose.application.resources.dir"),
            workingDirectory,
        )
        val executable = Go3RuntimePaths.executable(
            System.getenv("NUVIO_GO3_ELECTRON"), support, helper, workingDirectory,
        )
        val evidence=Files.createTempDirectory("nuvio-go3-player-").toFile()
        // Keep an existing approved development session; packaged apps use stable app data.
        val approved = File(helper.parentFile.parentFile.parentFile,"go3-feasibility/local-session")
        val profile=System.getenv("NUVIO_GO3_SESSION_DIR") ?: if(approved.isDirectory) approved.absolutePath else
            File(support,"Go3").absolutePath
        val child=ProcessBuilder(executable.absolutePath,helper.absolutePath).apply {
            environment()["NUVIO_GO3_PROBE_DIR"]=evidence.absolutePath
            environment()["NUVIO_GO3_SESSION_DIR"]=profile
            environment()["NUVIO_GO3_SERVICE"]="1"
            redirectOutput(ProcessBuilder.Redirect.DISCARD)
            redirectError(ProcessBuilder.Redirect.DISCARD)
        }.start()
        process=child
        return child
    }
    @Synchronized private fun send(message: JsonObject) {
        ensureStarted().outputStream.let {it.write((message.toString()+"\n").toByteArray());it.flush()}
    }
    fun open(request: Go3Playback.Request): Boolean = Go3Playback.isWatchUrl(request.url) && runCatching {
        send(buildJsonObject {
            put("action", "watch")
            put("footballKey", com.nuvio.app.features.player.FootballKeyStore.read())
            put("url", request.url)
            put("fullscreen", request.fullscreen)
            request.programmeTitle?.let { put("programmeTitle", it) }
        })
    }.isSuccess
}
