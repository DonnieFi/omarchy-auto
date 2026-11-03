package com.omarchy.auto.core.diag

import android.content.Context
import android.util.Log
import java.io.File

/**
 * The app's one logger: forwards to logcat and keeps the last [CAPACITY]
 * scrubbed lines in memory and in app-private storage for "Send logs".
 */
object OaLog {
    const val CAPACITY = 500
    private var buffer = LogBuffer(CAPACITY)
    @Volatile private var logFile: File? = null

    @Synchronized
    fun init(context: Context) {
        if (logFile != null) return
        val file = File(context.applicationContext.filesDir, "logs/app.log")
        file.parentFile?.mkdirs()
        buffer = LogBuffer(CAPACITY, file)
        logFile = file
    }

    fun i(tag: String, message: String) = write(Log.INFO, tag, message, null)
    fun w(tag: String, message: String, error: Throwable? = null) = write(Log.WARN, tag, message, error)
    fun e(tag: String, message: String, error: Throwable? = null) = write(Log.ERROR, tag, message, error)

    fun lines(): List<String> = buffer.lines()

    private fun write(level: Int, tag: String, message: String, error: Throwable?) {
        if (error == null) Log.println(level, tag, message) else Log.println(level, tag, message + "\n" + Log.getStackTraceString(error))
        val levelName = when (level) { Log.ERROR -> "E"; Log.WARN -> "W"; else -> "I" }
        val suffix = error?.let { " | ${it.javaClass.name}: ${it.message}" }.orEmpty()
        buffer.add("${System.currentTimeMillis()} $levelName/$tag: $message$suffix")
    }
}
