package com.playtorrio.tv.data.stremio

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Manages the list of installed Stremio addons.
 * Persists to SharedPreferences as a JSON array of InstalledAddon.
 * Thread-safe via coroutine dispatchers.
 */
object StremioAddonRepository {

    private const val PREFS_BASE = "stremio_prefs"
    private const val KEY_ADDONS = "installed_addons"
    private const val TAG = "StremioAddonRepo"

    private lateinit var prefs: SharedPreferences

    private val gson = GsonBuilder()
        .registerTypeAdapter(ResourceDescriptor::class.java, ResourceDescriptorDeserializer())
        .create()

    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    fun init(context: Context) {
        val activeId = com.playtorrio.tv.data.profile.ProfileManager.activeId()
        val fileName = if (activeId == "default") PREFS_BASE else "${PREFS_BASE}_$activeId"
        prefs = context.getSharedPreferences(fileName, Context.MODE_PRIVATE)
    }

    // ── Read ─────────────────────────────────────────────────────────────────

    fun getAddons(): List<InstalledAddon> {
        val json = prefs.getString(KEY_ADDONS, "[]") ?: "[]"
        return try {
            val type = object : TypeToken<List<InstalledAddon>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to deserialize addons", e)
            emptyList()
        }
    }

    // ── Install ───────────────────────────────────────────────────────────────

    /**
     * Fetches and installs an addon by manifest URL.
     * @return Result.success(InstalledAddon) or Result.failure with an error message.
     */
    suspend fun installAddon(manifestUrl: String): Result<InstalledAddon> =
        withContext(Dispatchers.IO) {
            try {
                val cleanUrl = manifestUrl.trim()

                // Build transport URL by stripping /manifest.json
                val transportUrl = when {
                    cleanUrl.endsWith("/manifest.json") ->
                        cleanUrl.removeSuffix("/manifest.json")
                    else -> {
                        // Accept transport URL entered without /manifest.json
                        cleanUrl.trimEnd('/')
                    }
                }
                val fetchUrl = "$transportUrl/manifest.json"

                val req = Request.Builder().url(fetchUrl).build()
                val body = http.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        return@withContext Result.failure(
                            Exception("HTTP ${resp.code}: ${resp.message}")
                        )
                    }
                    resp.body?.string()
                        ?: return@withContext Result.failure(Exception("Empty response"))
                }

                val manifest = gson.fromJson(body, AddonManifest::class.java)
                    ?: return@withContext Result.failure(Exception("Failed to parse manifest"))

                if (manifest.id.isBlank()) {
                    return@withContext Result.failure(Exception("Invalid manifest: missing id"))
                }

                val addon = InstalledAddon(transportUrl = transportUrl, manifest = manifest)

                // Deduplicate by addon id, replacing existing if same id
                val current = getAddons().toMutableList()
                current.removeAll { it.manifest.id == manifest.id }
                current.add(addon)
                saveAddons(current)

                Result.success(addon)
            } catch (e: Exception) {
                Log.e(TAG, "Install failed for $manifestUrl", e)
                Result.failure(e)
            }
        }

    // ── Remove ────────────────────────────────────────────────────────────────

    suspend fun removeAddon(addonId: String) = withContext(Dispatchers.IO) {
        val current = getAddons().toMutableList()
        current.removeAll { it.manifest.id == addonId }
        saveAddons(current)
    }

    // ── Persist ───────────────────────────────────────────────────────────────

    private fun saveAddons(addons: List<InstalledAddon>) {
        prefs.edit().putString(KEY_ADDONS, gson.toJson(addons)).apply()
    }
}
