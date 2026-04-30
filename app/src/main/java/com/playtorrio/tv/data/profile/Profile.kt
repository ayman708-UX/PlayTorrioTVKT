package com.playtorrio.tv.data.profile

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

data class Profile(
    val id: String,
    val name: String,
    val imageUrl: String? = null,
)

/**
 * Owns the multi-profile state. NOT profile-scoped (i.e. shared across all profiles).
 *
 * Persistence: SharedPreferences file `playtorrio_profiles`.
 *  - `profiles_v1` : JSON array of [Profile]
 *  - `active_profile_id` : currently selected profile id
 *
 * Per-profile state lives in `playtorrio_prefs_<id>` and `stremio_prefs_<id>` files
 * managed by [com.playtorrio.tv.data.AppPreferences] and
 * [com.playtorrio.tv.data.stremio.StremioAddonRepository].
 */
object ProfileManager {
    const val MAX_PROFILES = 5

    private const val PREFS_NAME = "playtorrio_profiles"
    private const val KEY_PROFILES = "profiles_v1"
    private const val KEY_ACTIVE = "active_profile_id"
    private const val DEFAULT_ID = "default"

    private lateinit var prefs: SharedPreferences
    private val gson = Gson()
    private lateinit var appContext: Context

    @Volatile
    private var activeIdCache: String = DEFAULT_ID

    fun init(context: Context) {
        appContext = context.applicationContext
        prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        // Ensure at least one profile exists.
        val list = loadProfiles().toMutableList()
        if (list.isEmpty()) {
            list.add(Profile(id = DEFAULT_ID, name = "Me", imageUrl = null))
            saveProfiles(list)
        }
        // Validate active id.
        val stored = prefs.getString(KEY_ACTIVE, null)
        activeIdCache = if (stored != null && list.any { it.id == stored }) stored else list.first().id
        prefs.edit().putString(KEY_ACTIVE, activeIdCache).apply()
    }

    fun activeId(): String = activeIdCache

    fun activeProfile(): Profile =
        loadProfiles().firstOrNull { it.id == activeIdCache }
            ?: Profile(id = DEFAULT_ID, name = "Me")

    fun loadProfiles(): List<Profile> {
        val json = prefs.getString(KEY_PROFILES, "[]") ?: "[]"
        return runCatching {
            val type = object : TypeToken<List<Profile>>() {}.type
            gson.fromJson<List<Profile>>(json, type) ?: emptyList()
        }.getOrDefault(emptyList())
    }

    private fun saveProfiles(list: List<Profile>) {
        prefs.edit().putString(KEY_PROFILES, gson.toJson(list)).apply()
    }

    /**
     * Sets the active profile and re-initializes per-profile storage so all subsequent
     * AppPreferences and StremioAddonRepository reads/writes target this profile.
     */
    fun setActive(id: String) {
        val list = loadProfiles()
        val target = list.firstOrNull { it.id == id } ?: return
        activeIdCache = target.id
        prefs.edit().putString(KEY_ACTIVE, target.id).apply()
        // Re-init profile-scoped stores. Order matters: AppPreferences re-inits Stremio too.
        com.playtorrio.tv.data.AppPreferences.init(appContext)
    }

    fun upsert(profile: Profile): Profile {
        val list = loadProfiles().toMutableList()
        val idx = list.indexOfFirst { it.id == profile.id }
        if (idx >= 0) list[idx] = profile else list.add(profile)
        saveProfiles(list)
        return profile
    }

    fun create(name: String, imageUrl: String?): Profile? {
        val current = loadProfiles()
        if (current.size >= MAX_PROFILES) return null
        val id = "p_" + System.currentTimeMillis().toString(36) +
            "_" + (0..3).joinToString("") { ('a'..'z').random().toString() }
        val p = Profile(id = id, name = name.ifBlank { "New profile" }, imageUrl = imageUrl)
        upsert(p)
        return p
    }

    fun canAddMore(): Boolean = loadProfiles().size < MAX_PROFILES

    fun delete(id: String) {
        val list = loadProfiles().toMutableList()
        if (list.size <= 1) return // never delete the last one
        val removed = list.removeAll { it.id == id }
        if (!removed) return
        saveProfiles(list)
        // Wipe per-profile storage files for the deleted profile.
        runCatching {
            appContext.deleteSharedPreferences("playtorrio_prefs_$id")
            appContext.deleteSharedPreferences("stremio_prefs_$id")
            appContext.deleteSharedPreferences("playtorrio_reading_progress_$id")
        }
        if (activeIdCache == id) {
            setActive(list.first().id)
        }
    }
}
