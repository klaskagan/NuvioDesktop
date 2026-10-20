package com.nuvio.app.features.trakt

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class TraktAuthRepositoryTest {
    private val json = Json { encodeDefaults = true }

    @Test
    fun `desktop public clients only need a client ID`() {
        assertTrue(traktCredentialsConfigured("client-id", "", desktop = true))
        assertFalse(traktCredentialsConfigured(" ", "", desktop = true))
        assertFalse(traktCredentialsConfigured("client-id", "", desktop = false))
        assertTrue(traktCredentialsConfigured("client-id", "legacy-secret", desktop = false))
    }

    @Test
    fun `device token requests contain only the device code and client ID`() {
        val body = json.parseToJsonElement(
            json.encodeToString(TraktDeviceTokenRequest("device-code", "client-id")),
        ).jsonObject
        assertEquals(setOf("code", "client_id"), body.keys)
        assertEquals("device-code", body.getValue("code").jsonPrimitive.content)
    }

    @Test
    fun `desktop refresh and revoke omit client secret even when defaults are encoded`() {
        val secret = traktOAuthClientSecret("legacy-secret", desktop = true)
        val refresh = json.parseToJsonElement(json.encodeToString(
            TraktRefreshTokenRequest("refresh-token", "client-id", secret, "http://localhost"),
        )).jsonObject
        val revoke = json.parseToJsonElement(json.encodeToString(
            TraktRevokeRequest("access-token", "client-id", secret),
        )).jsonObject
        assertFalse("client_secret" in refresh)
        assertFalse("client_secret" in revoke)
        assertEquals("refresh_token", refresh.getValue("grant_type").jsonPrimitive.content)
    }

    @Test
    fun `legacy mobile refresh retains configured secret`() {
        val secret = traktOAuthClientSecret("legacy-secret", desktop = false)
        val body = json.parseToJsonElement(json.encodeToString(
            TraktRefreshTokenRequest("refresh-token", "client-id", secret, "nuvio://auth/trakt"),
        )).jsonObject
        assertEquals("legacy-secret", body.getValue("client_secret").jsonPrimitive.content)
    }

    @Test
    fun `HTTP 400 permanently invalidates a rejected refresh token`() {
        assertEquals(
            TraktTokenRefreshResponseAction.INVALIDATE,
            traktTokenRefreshResponseAction(400),
        )
    }

    @Test
    fun `successful token responses are accepted`() {
        assertEquals(
            TraktTokenRefreshResponseAction.ACCEPT,
            traktTokenRefreshResponseAction(200),
        )
        assertEquals(
            TraktTokenRefreshResponseAction.ACCEPT,
            traktTokenRefreshResponseAction(201),
        )
    }

    @Test
    fun `non-400 failures preserve credentials for a later attempt`() {
        listOf(401, 429, 500, 503).forEach { status ->
            assertEquals(
                TraktTokenRefreshResponseAction.TRANSIENT_FAILURE,
                traktTokenRefreshResponseAction(status),
            )
        }
    }
}
