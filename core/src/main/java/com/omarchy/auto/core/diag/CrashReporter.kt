package com.omarchy.auto.core.diag

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * Self-hosted crash reporting. Writes a scrubbed JSON report synchronously,
 * tries one short upload, then hands the crash to the previous handler so
 * the process still dies normally. Pending reports upload on next start.
 */
object CrashReporter {
    const val ENDPOINT = "https://omarchyauto.com/api/crash"
    private const val GEARHEAD = "com.google.android.projection.gearhead"
    private const val TAG = "CrashReporter"
    private const val CRASH_UPLOAD_WAIT_MS = 1_500L

    /** Set by the car Session from CarContext.getHostInfo(). */
    @Volatile var hostPackage: String? = null

    private lateinit var queue: CrashQueue
    private var token: String = ""
    private var installed = false

    @Synchronized
    fun install(context: Context, token: String) {
        if (installed) return
        installed = true
        val app = context.applicationContext
        this.token = token
        queue = CrashQueue(File(app.filesDir, "crashes"))
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                queue.save(buildReport(app, error).toJson())
                val upload = Thread { uploadPending() }.apply { isDaemon = true; start() }
                upload.join(CRASH_UPLOAD_WAIT_MS)
            } catch (_: Throwable) {
                // Never mask the original crash.
            }
            previous?.uncaughtException(thread, error)
        }
        Executors.newSingleThreadExecutor().execute { uploadPending() }
    }

    fun buildReport(context: Context, error: Throwable): CrashReport {
        val pm = context.packageManager
        val self = pm.getPackageInfo(context.packageName, 0)
        val host = hostPackage ?: GEARHEAD
        val hostVersion = try {
            pm.getPackageInfo(host, 0).versionName
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }
        @Suppress("DEPRECATION")
        val code = if (Build.VERSION.SDK_INT >= 28) self.longVersionCode else self.versionCode.toLong()
        return CrashReport(
            timestampMs = System.currentTimeMillis(),
            versionCode = code,
            versionName = self.versionName.orEmpty(),
            manufacturer = Build.MANUFACTURER.orEmpty(),
            model = Build.MODEL.orEmpty(),
            sdkInt = Build.VERSION.SDK_INT,
            release = Build.VERSION.RELEASE.orEmpty(),
            hostPackage = if (hostVersion != null || hostPackage != null) host else null,
            hostVersion = hostVersion,
            exception = error.javaClass.name,
            message = error.message,
            stackTrace = android.util.Log.getStackTraceString(error),
        )
    }

    private fun uploadPending() {
        if (token.isEmpty()) return
        try {
            queue.uploadPending(::post)
        } catch (error: Exception) {
            OaLog.w(TAG, "Crash upload failed", error)
        }
    }

    private fun post(body: String): Int {
        val conn = URL(ENDPOINT).openConnection() as HttpURLConnection
        return try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 5_000
            conn.readTimeout = 5_000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("X-OA-Crash-Token", token)
            conn.outputStream.use { it.write(body.toByteArray()) }
            conn.responseCode
        } finally {
            conn.disconnect()
        }
    }
}
