package com.nuvio.app.features.go3

import com.nuvio.app.Go3Playback
import java.io.File
import java.nio.file.Files
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.*
import kotlinx.serialization.json.*

/** Single local browser service. Provider credentials never cross the process protocol. */
object Go3PlayerLauncher {
    private var process: Process? = null
    private val ids = AtomicLong()
    private val pending = ConcurrentHashMap<String, CompletableDeferred<JsonObject>>()
    private val scope = CoroutineScope(SupervisorJob()+Dispatchers.IO)
    fun install() {
        if (!System.getProperty("os.name").contains("Mac",true)) return
        Go3Playback.open = ::open
        Go3Catalog.loader = ::events
        Go3Catalog.accountAction = { action -> runCatching { send(buildJsonObject {put("action",action)}) }.isSuccess }
        Runtime.getRuntime().addShutdownHook(Thread { synchronized(this) { process?.outputStream?.close(); process?.destroy() } })
    }
    @Synchronized private fun ensureStarted(): Process {
        process?.takeIf {it.isAlive}?.let {return it}
        // Compose's :run uses composeApp as user.dir; tests commonly use the repository root.
        val helper = System.getenv("NUVIO_GO3_HELPER") ?: sequenceOf(
            File("scripts/go3-live-probe"), File("../scripts/go3-live-probe"),
        ).firstOrNull { File(it, "main.cjs").isFile }?.canonicalPath
            ?: error("Go3 helper unavailable; set NUVIO_GO3_HELPER")
        val localRoot = File(helper).parentFile.parentFile.parentFile
        val executable = System.getenv("NUVIO_GO3_ELECTRON") ?: File(localRoot,
            "go3-feasibility/runtime/Electron.app/Contents/MacOS/Electron").absolutePath
        require(File(executable).canExecute() && File(helper,"main.cjs").isFile) {"Go3 runtime unavailable"}
        val evidence=Files.createTempDirectory("nuvio-go3-player-").toFile()
        // Reuse the explicitly approved investigation profile when present; never copy Chrome data.
        val approved = File(File(helper).parentFile.parentFile.parentFile,"go3-feasibility/local-session")
        val profile=System.getenv("NUVIO_GO3_SESSION_DIR") ?: if(approved.isDirectory) approved.absolutePath else
            File(System.getProperty("user.home"),"Library/Application Support/Nuvio/Go3").absolutePath
        val child=ProcessBuilder(executable,helper).apply {
            environment()["NUVIO_GO3_PROBE_DIR"]=evidence.absolutePath
            environment()["NUVIO_GO3_SESSION_DIR"]=profile
            environment()["NUVIO_GO3_SERVICE"]="1"
            redirectError(ProcessBuilder.Redirect.DISCARD)
        }.start()
        process=child
        scope.launch {
            try {child.inputStream.bufferedReader().useLines {lines -> lines.forEach {line ->
                if(line.length<=500_000) runCatching {
                    val obj=Json.parseToJsonElement(line).jsonObject
                    obj["request"]?.jsonPrimitive?.content?.let {pending.remove(it)?.complete(obj)}
                }
            }}} finally { pending.values.forEach {it.completeExceptionally(IllegalStateException("Go3 runtime stopped"))};pending.clear() }
        }
        return child
    }
    @Synchronized private fun send(message: JsonObject) {
        ensureStarted().outputStream.let {it.write((message.toString()+"\n").toByteArray());it.flush()}
    }
    fun open(url: String): Boolean = Go3Playback.isWatchUrl(url) && runCatching {
        send(buildJsonObject {put("action","watch");put("url",url);put("fullscreen",Go3Playback.fullscreenRequested)})
    }.isSuccess
    private suspend fun events(kind: String): List<Go3Event> = withContext(Dispatchers.IO) {
        val request=ids.incrementAndGet().toString()
        val answer=CompletableDeferred<JsonObject>();pending[request]=answer
        try {
            send(buildJsonObject {put("action","events");put("request",request);put("catalog",kind)})
            val result=withTimeout(45_000){answer.await()}
            require(result["error"]==null) {result["error"]?.jsonPrimitive?.content ?: "Go3 catalogue unavailable"}
            result["entries"]!!.jsonArray.map {row -> val e=row.jsonObject
                Go3Event(e["id"]!!.jsonPrimitive.content,e["title"]!!.jsonPrimitive.content,e["url"]!!.jsonPrimitive.content,
                    e["artwork"]?.jsonPrimitive?.contentOrNull,e["state"]!!.jsonPrimitive.content,e["scheduleLabel"]!!.jsonPrimitive.content)
            }
        } finally {pending.remove(request)}
    }
}
