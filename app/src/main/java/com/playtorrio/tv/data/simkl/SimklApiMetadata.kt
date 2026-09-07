package com.playtorrio.tv.data.simkl

import com.playtorrio.tv.BuildConfig
import okhttp3.HttpUrl.Companion.toHttpUrl

data class SimklApiConfiguration(
    val clientId: String,
    val appName: String,
    val appVersion: String,
    val clientSecret: String = "",
    val baseUrl: String = "https://api.simkl.com"
)

fun buildSimklApiUrl(
    configuration: SimklApiConfiguration,
    path: String,
    query: Map<String, String> = emptyMap()
): String {
    val normalizedPath = path.trim().let { value -> if (value.startsWith('/')) value else "/$value" }
    val builder = configuration.baseUrl.toHttpUrl().newBuilder().encodedPath(normalizedPath)
    query.filterKeys { it !in SIMKL_REQUIRED_QUERY_KEYS }.forEach { (key, value) ->
        builder.addQueryParameter(key, value)
    }
    builder.addQueryParameter("client_id", configuration.clientId)
    if (configuration.clientSecret.isNotBlank() && normalizedPath.startsWith("/oauth")) {
        builder.addQueryParameter("client_secret", configuration.clientSecret)
    }
    builder.addQueryParameter("app-name", configuration.appName)
    builder.addQueryParameter("app-version", configuration.appVersion)
    return builder.build().toString()
}

fun simklRequestHeaders(
    configuration: SimklApiConfiguration,
    accessToken: String? = null,
    contentTypeJson: Boolean = false
): Map<String, String> = buildMap {
    put("User-Agent", "$SIMKL_USER_AGENT_APP_NAME/${configuration.appVersion}")
    put("Accept", "application/json")
    accessToken?.trim()?.takeIf(String::isNotBlank)?.let { token ->
        put("Authorization", "Bearer $token")
    }
    if (contentTypeJson) put("Content-Type", "application/json")
}

fun defaultSimklApiConfiguration(): SimklApiConfiguration = SimklApiConfiguration(
    clientId = BuildConfig.SIMKL_CLIENT_ID,
    appName = BuildConfig.SIMKL_APP_NAME.ifBlank { "playtorrio" },
    appVersion = BuildConfig.VERSION_NAME.ifBlank { "dev" },
    clientSecret = BuildConfig.SIMKL_CLIENT_SECRET
)

private val SIMKL_REQUIRED_QUERY_KEYS = setOf("client_id", "client_secret", "app-name", "app-version")
private const val SIMKL_USER_AGENT_APP_NAME = "PlayTorrioTV"
