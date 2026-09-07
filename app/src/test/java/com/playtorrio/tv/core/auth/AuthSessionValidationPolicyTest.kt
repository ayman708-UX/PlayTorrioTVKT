package com.playtorrio.tv.core.auth

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthSessionValidationPolicyTest {

    @Test
    fun `invalidates unauthorized auth responses`() {
        assertTrue(isInvalidRemoteSessionResponse(401, "Unauthorized"))
    }

    @Test
    fun `invalidates explicit revoked session responses`() {
        assertTrue(isInvalidRemoteSessionResponse(400, "session_not_found"))
        assertTrue(isInvalidRemoteSessionResponse(403, "Invalid session"))
        assertTrue(isInvalidRemoteSessionResponse(null, "Invalid JWT"))
        assertTrue(isInvalidRemoteSessionResponse(404, "User does not exist"))
    }

    @Test
    fun `keeps cached session for transient failures`() {
        assertFalse(isInvalidRemoteSessionResponse(403, "Forbidden"))
        assertFalse(isInvalidRemoteSessionResponse(403, "Request blocked by proxy"))
        assertFalse(isInvalidRemoteSessionResponse(429, "Too many requests"))
        assertFalse(isInvalidRemoteSessionResponse(503, "Service unavailable"))
        assertFalse(isInvalidRemoteSessionResponse(null, "Unable to resolve host"))
    }
}
