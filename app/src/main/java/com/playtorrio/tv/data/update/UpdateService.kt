package com.playtorrio.tv.data.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.playtorrio.tv.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * In-app updater that pulls release APKs from a GitHub repository.
 *
 * Flow:
 *   1) GET https://api.github.com/repos/<repo>/releases/latest
 *   2) Compare tag_name (semver) to BuildConfig.VERSION_NAME
 *   3) Pick asset whose name contains the device's preferred ABI (Build.SUPPORTED_ABIS[0])
 *   4) Download to externalFilesDir/updates/, share via FileProvider, launch ACTION_VIEW
 *
 * Signing-key continuity: this only works as an "update" (vs install-as-new-app)
 * when both APKs are signed with the same keystore.
 */
object UpdateService {

    data class UpdateInfo(
        val tagName: String,           // e.g. "1.0.1"
        val versionName: String,       // tag without leading 'v'
        val releaseName: String,       // GitHub release title
        val releaseNotes: String,      // body markdown
        val downloadUrl: String,       // ABI-matched asset URL
        val assetName: String,
        val sizeBytes: Long
    )

    private val http: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    /** Returns non-null when a newer release exists with a usable APK for this device. */
    suspend fun check(): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val repo = BuildConfig.UPDATE_REPO
            if (repo.isBlank()) return@withContext null
            val req = Request.Builder()
                .url("https://api.github.com/repos/$repo/releases/latest")
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "PlayTorrioTV-Updater")
                .build()
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val body = resp.body?.string() ?: return@withContext null
                val json = JSONObject(body)
                val tag = json.optString("tag_name").trim()
                if (tag.isEmpty()) return@withContext null
                val remoteVer = tag.removePrefix("v").trim()
                if (!isNewer(remoteVer, BuildConfig.VERSION_NAME)) return@withContext null

                val assets = json.optJSONArray("assets") ?: return@withContext null
                val preferred = Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64-v8a"

                var match: JSONObject? = null
                for (i in 0 until assets.length()) {
                    val a = assets.getJSONObject(i)
                    val name = a.optString("name").lowercase()
                    if (!name.endsWith(".apk")) continue
                    if (name.contains(preferred)) { match = a; break }
                }
                if (match == null) {
                    // Fall back to a "universal" APK if present
                    for (i in 0 until assets.length()) {
                        val a = assets.getJSONObject(i)
                        val name = a.optString("name").lowercase()
                        if (name.endsWith(".apk") && name.contains("universal")) { match = a; break }
                    }
                }
                if (match == null) return@withContext null

                UpdateInfo(
                    tagName = tag,
                    versionName = remoteVer,
                    releaseName = json.optString("name").ifBlank { tag },
                    releaseNotes = json.optString("body"),
                    downloadUrl = match.optString("browser_download_url"),
                    assetName = match.optString("name"),
                    sizeBytes = match.optLong("size", 0L)
                )
            }
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Downloads the APK to externalFilesDir/updates/. Returns the file on success.
     * onProgress: (bytesRead, totalBytes) — totalBytes may be -1 if unknown.
     */
    suspend fun download(
        context: Context,
        info: UpdateInfo,
        onProgress: (Long, Long) -> Unit = { _, _ -> }
    ): File? = withContext(Dispatchers.IO) {
        try {
            val dir = File(context.getExternalFilesDir(null) ?: context.filesDir, "updates")
            if (!dir.exists()) dir.mkdirs()
            // Wipe stale APKs to keep storage tidy
            dir.listFiles()?.forEach { runCatching { it.delete() } }

            val outFile = File(dir, info.assetName)
            val req = Request.Builder()
                .url(info.downloadUrl)
                .header("User-Agent", "PlayTorrioTV-Updater")
                .build()
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val body = resp.body ?: return@withContext null
                val total = body.contentLength()
                body.byteStream().use { input ->
                    outFile.outputStream().use { output ->
                        val buf = ByteArray(64 * 1024)
                        var read: Int
                        var sum = 0L
                        while (input.read(buf).also { read = it } != -1) {
                            output.write(buf, 0, read)
                            sum += read
                            onProgress(sum, total)
                        }
                    }
                }
            }
            outFile
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Launches the system installer for the given APK file. Returns false if the
     * user has not granted "install unknown apps" permission yet (caller should
     * route to settings via [openInstallSourcesSettings]).
     */
    fun installApk(context: Context, apk: File): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!context.packageManager.canRequestPackageInstalls()) {
                openInstallSourcesSettings(context)
                return false
            }
        }
        val authority = "${context.packageName}.updates"
        val uri: Uri = FileProvider.getUriForFile(context, authority, apk)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        context.startActivity(intent)
        return true
    }

    fun openInstallSourcesSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                data = Uri.parse("package:${context.packageName}")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            runCatching { context.startActivity(intent) }
        }
    }

    /** Strict semver-ish compare: "1.0.10" > "1.0.2". Non-numeric chunks compared lexically. */
    internal fun isNewer(remote: String, local: String): Boolean {
        val r = remote.split('.', '-')
        val l = local.split('.', '-')
        val n = maxOf(r.size, l.size)
        for (i in 0 until n) {
            val rp = r.getOrNull(i) ?: "0"
            val lp = l.getOrNull(i) ?: "0"
            val rn = rp.toIntOrNull()
            val ln = lp.toIntOrNull()
            val cmp = if (rn != null && ln != null) rn.compareTo(ln) else rp.compareTo(lp)
            if (cmp != 0) return cmp > 0
        }
        return false
    }
}
