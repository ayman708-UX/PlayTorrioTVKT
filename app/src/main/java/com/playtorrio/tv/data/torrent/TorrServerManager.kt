package com.playtorrio.tv.data.torrent

import android.content.Context
import android.util.Log
import java.io.File

/**
 * Manages TorrServer as a subprocess.
 * The binary is bundled in jniLibs as libtorrserver.so.
 */
object TorrServerManager {

    private const val TAG = "TorrServerManager"
    const val DEFAULT_PORT = 8090

    private var process: Process? = null
    var port: Int = DEFAULT_PORT; private set
    private var dataDir: File? = null
    private val lock = Object()

    val isRunning: Boolean get() {
        val p = process ?: return false
        return isProcessAlive(p)
    }

    /**
     * SDK-safe alive check. [Process.isAlive] is API 26+ and crashes with
     * NoSuchMethodError on older Fire Sticks (API 25 / Android 7.1). exitValue()
     * works on every API level: throws IllegalThreadStateException while running.
     */
    private fun isProcessAlive(p: Process): Boolean = try {
        p.exitValue()
        false
    } catch (_: IllegalThreadStateException) {
        true
    } catch (_: Throwable) {
        false
    }

    fun start(context: Context, listenPort: Int = DEFAULT_PORT): Boolean {
        synchronized(lock) {
            if (isRunning) {
                Log.d(TAG, "TorrServer already running on port $port (pid alive)")
                return true
            }

            // Kill any previous process
            process?.let { p ->
                Log.d(TAG, "Cleaning up stale process")
                try { p.destroy() } catch (_: Exception) {}
                process = null
            }

            val binaryPath = File(context.applicationInfo.nativeLibraryDir, "libtorrserver.so")
            Log.d(TAG, "Binary path: ${binaryPath.absolutePath}")
            Log.d(TAG, "Binary exists: ${binaryPath.exists()}, size: ${if (binaryPath.exists()) binaryPath.length() else 0}")

            if (!binaryPath.exists()) {
                Log.e(TAG, "TorrServer binary not found!")
                return false
            }

            // Make sure it's executable
            try {
                binaryPath.setExecutable(true, false)
            } catch (e: Exception) {
                Log.w(TAG, "Could not set executable: ${e.message}")
            }

            val dir = File(context.filesDir, "torr_data")
            dir.mkdirs()
            dataDir = dir
            port = listenPort

            return try {
                Log.d(TAG, "Starting TorrServer: ${binaryPath.absolutePath} -p $port -d ${dir.absolutePath} -k")
                val pb = ProcessBuilder(
                    binaryPath.absolutePath,
                    "-p", "$port",
                    "-d", dir.absolutePath,
                    "-k"
                )
                pb.redirectErrorStream(true)
                val proc = pb.start()
                process = proc
                Log.d(TAG, "Process started, alive=${isProcessAlive(proc)}")

                // Log output in background thread
                Thread {
                    try {
                        proc.inputStream?.bufferedReader()?.useLines { lines ->
                            for (line in lines) {
                                Log.d(TAG, line)
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Output reader error: ${e.message}")
                    }
                    Log.d(TAG, "Output reader thread ended, process alive=${isProcessAlive(proc)}")
                }.apply { isDaemon = true }.start()

                true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start TorrServer: ${e.message}", e)
                false
            }
        }
    }

    fun stop() {
        synchronized(lock) {
            process?.let { p ->
                Log.d(TAG, "Stopping TorrServer")
                p.destroy()
                try { p.waitFor() } catch (_: Exception) {}
                process = null
            }
        }
    }
}
