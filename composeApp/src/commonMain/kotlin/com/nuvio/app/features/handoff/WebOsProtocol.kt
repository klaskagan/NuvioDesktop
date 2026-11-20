package com.nuvio.app.features.handoff

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/** Paired SSAP messages for LG webOS (wss://tv:3001). Transports own sockets and certificate pinning. */
internal object WebOsProtocol {
    const val PORT = 3001
    const val MAX_MESSAGE = 65_536

    fun register(clientKey: String?): String = buildJsonObject {
        put("id", "register")
        put("type", "register")
        putJsonObject("payload") {
            if (clientKey != null) put("client-key", clientKey)
            put("pairingType", "PROMPT")
            put("forcePairing", false)
            putJsonObject("manifest") {
                put("manifestVersion", 1)
                put("appVersion", "1.0")
                putJsonArray("permissions") {
                    listOf("LAUNCH", "READ_APP_STATUS", "READ_INSTALLED_APPS", "READ_POWER_STATE")
                        .forEach { add(JsonPrimitive(it)) }
                }
            }
        }
    }.toString()

    fun launch(appId: String, parameters: JsonObject): String = buildJsonObject {
        put("id", "launch")
        put("type", "request")
        put("uri", "ssap://system.launcher/launch")
        putJsonObject("payload") { put("id", appId); put("params", parameters) }
    }.toString()

    fun parse(message: String): JsonObject = Json.parseToJsonElement(message).jsonObject

    /** Returns the issued client key once registered, null while waiting for the TV prompt. */
    fun registrationKey(message: JsonObject): String? {
        if (message["type"]?.jsonPrimitive?.content == "registered") {
            val issued = message["payload"]?.jsonObject?.get("client-key")?.jsonPrimitive?.content
            check(!issued.isNullOrBlank()) { "LG did not complete pairing." }
            return issued
        }
        check(message["type"]?.jsonPrimitive?.content != "error") { "LG pairing was declined." }
        return null
    }

    /** True once the launch request is acknowledged. */
    fun launchAcknowledged(message: JsonObject): Boolean {
        if (message["id"]?.jsonPrimitive?.content != "launch") return false
        check(message["type"]?.jsonPrimitive?.content != "error") { "LG could not launch Nuvio. Check its installed app ID." }
        return true
    }
}
