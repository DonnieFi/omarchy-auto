package com.omarchy.auto.core.diag

import java.io.File

/** Pending crash reports in app-private storage, oldest dropped past [cap]. */
class CrashQueue(private val dir: File, private val cap: Int = 10) {

    @Synchronized
    fun save(json: String, nowMs: Long = System.currentTimeMillis()) {
        dir.mkdirs()
        var file = File(dir, name(nowMs, 0))
        var n = 0
        while (file.exists()) file = File(dir, name(nowMs, ++n))
        file.writeText(json)
        pending().dropLast(cap).forEach { it.delete() }
    }

    @Synchronized
    fun pending(): List<File> =
        dir.listFiles { f -> f.name.startsWith(PREFIX) && f.name.endsWith(".json") }
            ?.sortedBy { it.name }.orEmpty()

    /** Sends each report; deletes it only on a 2xx. Returns how many were sent. */
    fun uploadPending(send: (String) -> Int): Int {
        var sent = 0
        for (file in pending()) {
            val code = try {
                send(file.readText())
            } catch (_: Exception) {
                continue
            }
            if (code in 200..299) {
                file.delete()
                sent++
            }
        }
        return sent
    }

    private fun name(nowMs: Long, n: Int) = "$PREFIX%015d-%03d.json".format(nowMs, n)

    private companion object {
        const val PREFIX = "crash-"
    }
}
