package com.omarchy.auto.core.diag

/** Strips personal data from log lines and crash text before it is stored. */
object Scrubber {
    private val rules: List<Pair<Regex, String>> = listOf(
        Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}") to "<email>",
        Regex("(?i)\\b(account(?:Name)?\\s*[=:]\\s*)[^,;\\n]+") to "$1<account>",
        Regex("(?i)\\b(lat|lon|lng|latitude|longitude)(\\s*[=:]\\s*)-?\\d{1,3}\\.\\d+") to "$1$2<coord>",
        Regex("-?\\d{1,3}\\.\\d{3,}\\s*,\\s*-?\\d{1,3}\\.\\d{3,}") to "<coords>",
        Regex("(?<![\\w.])(?:\\+\\d{1,2}[\\s-]?)?\\(?\\d{3}\\)?[\\s.-]\\d{3}[\\s.-]\\d{4}(?!\\d)") to "<phone>",
        Regex("/home/[^/\\s]+") to "/home/<user>",
        Regex("/Users/[^/\\s]+") to "/Users/<user>",
        Regex("/storage/emulated/\\d+") to "/storage/emulated/<n>",
        Regex("/data/user/\\d+") to "/data/user/<n>",
    )

    fun scrub(text: String): String =
        rules.fold(text) { acc, (pattern, replacement) -> pattern.replace(acc, replacement) }
}
