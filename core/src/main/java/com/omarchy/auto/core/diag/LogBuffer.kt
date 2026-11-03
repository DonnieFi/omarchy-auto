package com.omarchy.auto.core.diag

import java.io.File

/**
 * Newest [capacity] scrubbed log lines, mirrored to [file] so they survive a
 * crash. The file is compacted back to [capacity] lines once it doubles.
 */
class LogBuffer(private val capacity: Int, private val file: File? = null) {
    private val lines = ArrayDeque<String>(capacity)
    private var fileLines = 0

    init {
        file?.takeIf { it.isFile }?.let {
            it.readLines().takeLast(capacity).forEach(lines::addLast)
            compact()
        }
    }

    @Synchronized
    fun add(line: String) {
        val clean = Scrubber.scrub(line).replace('\n', ' ')
        if (lines.size == capacity) lines.removeFirst()
        lines.addLast(clean)
        val target = file ?: return
        try {
            if (fileLines >= capacity * 2) {
                compact()
            } else {
                target.appendText(clean + "\n")
                fileLines++
            }
        } catch (_: Exception) {
            // Logging must never take the app down.
        }
    }

    @Synchronized
    fun lines(): List<String> = lines.toList()

    private fun compact() {
        val target = file ?: return
        target.parentFile?.mkdirs()
        target.writeText(lines.joinToString("") { it + "\n" })
        fileLines = lines.size
    }
}
