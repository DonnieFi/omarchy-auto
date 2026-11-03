package com.omarchy.auto.core.diag

/** One uncaught-exception report. Free text is scrubbed in [toJson]. */
data class CrashReport(
    val timestampMs: Long,
    val versionCode: Long,
    val versionName: String,
    val manufacturer: String,
    val model: String,
    val sdkInt: Int,
    val release: String,
    val hostPackage: String?,
    val hostVersion: String?,
    val exception: String,
    val message: String?,
    val stackTrace: String,
) {
    fun toJson(): String = buildString {
        append('{')
        append("\"ts\":").append(timestampMs)
        append(",\"versionCode\":").append(versionCode)
        append(",\"versionName\":").append(str(versionName))
        append(",\"manufacturer\":").append(str(manufacturer))
        append(",\"model\":").append(str(model))
        append(",\"sdkInt\":").append(sdkInt)
        append(",\"release\":").append(str(release))
        append(",\"hostPackage\":").append(str(hostPackage))
        append(",\"hostVersion\":").append(str(hostVersion))
        append(",\"exception\":").append(str(exception))
        append(",\"message\":").append(str(message?.let(Scrubber::scrub)?.take(MAX_MESSAGE)))
        append(",\"stackTrace\":").append(str(Scrubber.scrub(stackTrace).take(MAX_TRACE)))
        append('}')
    }

    companion object {
        const val MAX_MESSAGE = 1_000
        const val MAX_TRACE = 12_000

        private fun str(value: String?): String {
            if (value == null) return "null"
            val out = StringBuilder(value.length + 2).append('"')
            for (c in value) {
                when {
                    c == '"' -> out.append("\\\"")
                    c == '\\' -> out.append("\\\\")
                    c == '\n' -> out.append("\\n")
                    c == '\r' -> out.append("\\r")
                    c == '\t' -> out.append("\\t")
                    c < ' ' -> out.append(String.format("\\u%04x", c.code))
                    else -> out.append(c)
                }
            }
            return out.append('"').toString()
        }
    }
}
