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
        val support = File(System.getProperty("user.home"), "Library/Application Support/Nuvio")
        val helper = Go3RuntimePaths.helper(
            System.getenv("NUVIO_GO3_HELPER"),
            System.getProperty("compose.application.resources.dir"),
            File(System.getProperty("user.dir")),
        )
        val executable = Go3RuntimePaths.executable(
            System.getenv("NUVIO_GO3_ELECTRON"), support, helper,
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
