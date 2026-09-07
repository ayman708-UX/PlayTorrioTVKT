package com.playtorrio.tv.core.auth

internal fun isInvalidRemoteSessionResponse(
    statusCode: Int?,
    message: String
): Boolean {
    if (statusCode == 401) return true
    val normalizedMessage = message.lowercase()
    return INVALID_REMOTE_SESSION_MARKERS.any(normalizedMessage::contains)
}

private val INVALID_REMOTE_SESSION_MARKERS = listOf(
    "session not found",
    "session_not_found",
    "invalid session",
    "invalid token",
    "invalid jwt",
    "jwt expired",
    "user not found",
    "user does not exist"
)

internal fun Throwable.isJwtExpiredAuthError(): Boolean {
    var current: Throwable? = this
    while (current != null) {
        if (current.message?.contains("jwt expired", ignoreCase = true) == true) return true
        current = current.cause
    }
    return false
}
