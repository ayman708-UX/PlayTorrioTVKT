package com.playtorrio.tv.data.remote

import com.playtorrio.tv.domain.model.isPublicServerHost
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerDiscoveryPolicyTest {
    @Test
    fun discoveryUrlAddsSchemeAndKnownPath() {
        assertEquals(
            "https://backend.example.com/.well-known/playtorrio",
            ServerDiscoveryPolicy.discoveryUrl("backend.example.com")
        )
    }

    @Test
    fun discoveryUrlPreservesBackendPathAndIsIdempotent() {
        assertEquals(
            "https://example.com/backend/.well-known/playtorrio",
            ServerDiscoveryPolicy.discoveryUrl("https://example.com/backend/.well-known/playtorrio")
        )
    }

    @Test
    fun parseAcceptsVersionOneDocument() {
        val configuration = ServerDiscoveryPolicy.parse(
            discoveryUrl = "https://example.com/.well-known/playtorrio",
            document = """
                {
                  "version": 1,
                  "service": "playtorrio",
                  "self_hosted": true,
                  "backend_url": "https://backend.example.com/",
                  "publishable_key": "public-key",
                  "capabilities": {
                    "email_password_auth": true,
                    "tv_login": true
                  }
                }
            """.trimIndent()
        )

        assertEquals("https://backend.example.com", configuration.backendUrl)
        assertEquals("public-key", configuration.publishableKey)
        assertTrue(configuration.isCustom)
        assertTrue(configuration.capabilities.emailPasswordAuth)
        assertTrue(configuration.capabilities.tvLogin)
    }

    @Test
    fun parseRejectsUnsupportedVersion() {
        val error = assertThrows(ServerDiscoveryException::class.java) {
            ServerDiscoveryPolicy.parse(
                discoveryUrl = "https://example.com/.well-known/playtorrio",
                document = """
                    {
                      "version": 2,
                      "service": "playtorrio",
                      "self_hosted": true,
                      "backend_url": "https://backend.example.com",
                      "publishable_key": "public-key",
                      "capabilities": { "email_password_auth": true }
                    }
                """.trimIndent()
            )
        }

        assertEquals(ServerDiscoveryFailure.UNSUPPORTED_VERSION, error.failure)
    }

    @Test
    fun parseRejectsDocumentWithoutSupportedAuthentication() {
        val error = assertThrows(ServerDiscoveryException::class.java) {
            ServerDiscoveryPolicy.parse(
                discoveryUrl = "https://example.com/.well-known/playtorrio",
                document = """
                    {
                      "version": 1,
                      "service": "playtorrio",
                      "self_hosted": true,
                      "backend_url": "https://backend.example.com",
                      "publishable_key": "public-key",
                      "capabilities": {}
                    }
                """.trimIndent()
            )
        }

        assertEquals(ServerDiscoveryFailure.NO_SUPPORTED_AUTH, error.failure)
    }

    @Test
    fun publicHostClassificationDistinguishesPrivateNetworks() {
        assertFalse(isPublicServerHost("http://192.168.1.10:8000"))
        assertFalse(isPublicServerHost("http://172.20.0.5"))
        assertFalse(isPublicServerHost("http://server.local"))
        assertTrue(isPublicServerHost("https://backend.example.com"))
    }

    @Test
    fun matchesOfficialBackendByHostAndPort() {
        assertTrue(
            ServerDiscoveryPolicy.matchesBackend(
                "https://api.playtorrio.tv/.well-known/playtorrio",
                "https://api.playtorrio.tv"
            )
        )
        assertFalse(
            ServerDiscoveryPolicy.matchesBackend(
                "https://api.playtorrio.tv.example.com/.well-known/playtorrio",
                "https://api.playtorrio.tv"
            )
        )
    }
}
