package com.omarchy.auto.core

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test

/**
 * Design-token contract between this Android repo and the iOS edition
 * (ADR-0017). `design/omarchy-themes.json` is generated from [Themes.all] and
 * must stay byte-for-byte identical to what Theme.kt produces.
 *
 * - `./gradlew :core:testDebugUnitTest` verifies the committed file.
 * - `./gradlew :core:testDebugUnitTest -Pomarchy.updateDesignTokens=true`
 *   (or the root alias `exportDesignTokens`) rewrites it after a theme edit.
 *   The rewrite happens in [beforeClass], so regeneration never depends on
 *   JUnit method ordering: every test sees the freshly written file.
 *
 * The export is deterministic: fixed schema, fixed field order, themes in
 * [Themes.all] order, LF line endings, no timestamps. Two exports of the same
 * Theme.kt are byte-identical, so the iOS side can vendor the file directly.
 */
class DesignTokensTest {

    @Test
    fun tokenExportIsDeterministicLfOnlyAndStable() {
        val first = canonicalTokensJson()
        val second = canonicalTokensJson()
        assertEquals("Serialization must be a pure function of Theme.kt", first, second)
        assertFalse("Export must use LF line endings only", first.contains("\r"))
        assertTrue("Export must end with exactly one trailing newline", first.endsWith("}\n"))
        assertFalse(first.endsWith("}\n\n"))
    }

    @Test
    fun committedTokensFileMatchesKotlinThemesByteForByte() {
        val expected = canonicalTokensJson().toByteArray(Charsets.UTF_8)
        assertTrue(
            "design/omarchy-themes.json is missing. Run " +
                "./gradlew exportDesignTokens -Pomarchy.updateDesignTokens=true",
            tokensFile.isFile,
        )
        if (!tokensFile.readBytes().contentEquals(expected)) {
            assertEquals(
                "design/omarchy-themes.json is stale: Theme.kt changed without regenerating. " +
                    "Run ./gradlew exportDesignTokens -Pomarchy.updateDesignTokens=true",
                canonicalTokensJson(),
                tokensFile.readText(Charsets.UTF_8),
            )
        }
    }

    // ------------------------------------------------------------------
    // Independent decode path: parse the committed JSON and compare every
    // field against the Kotlin themes, the way the iOS decoder will.
    // ------------------------------------------------------------------

    @Test
    fun committedTokensDecodeToTheExactKotlinThemeValues() {
        assertTrue(tokensFile.isFile)
        val text = tokensFile.readText(Charsets.UTF_8)
        val root = MiniJson(text).parseDocument() as Map<*, *>

        assertEquals(1L, root["schema"])
        assertEquals("core/src/main/java/com/omarchy/auto/core/Theme.kt", root["source"])
        assertEquals("#AARRGGBB", root["colorFormat"])
        assertEquals("0-255", root["alphaRange"])

        val themes = root["themes"] as List<*>
        assertEquals("JSON must carry every theme", Themes.all.size, themes.size)
        themes.forEachIndexed { index, raw ->
            val decoded = raw as Map<*, *>
            val kotlin = Themes.all[index]
            assertEquals("theme order must match Themes.all", kotlin.id, decoded["id"])
            assertEquals(kotlin.name, decoded["name"])
            assertEquals(kotlin.shortName, decoded["shortName"])
            for ((field, value) in listOf(
                "background" to kotlin.background,
                "surface" to kotlin.surface,
                "surfaceAlt" to kotlin.surfaceAlt,
                "foreground" to kotlin.foreground,
                "dim" to kotlin.dim,
                "border" to kotlin.border,
                "accent" to kotlin.accent,
                "green" to kotlin.green,
                "yellow" to kotlin.yellow,
                "red" to kotlin.red,
            )) {
                assertEquals("$field of ${kotlin.id}", argbHex(value), decoded[field])
            }
            assertEquals("$kotlin id=$index scrimAlpha", kotlin.scrimAlpha.toLong(), decoded["scrimAlpha"])
            assertEquals("$kotlin id=$index stageAlpha", kotlin.stageAlpha.toLong(), decoded["stageAlpha"])
        }
    }

    companion object {

        private val updateMode = System.getProperty("omarchy.updateDesignTokens") == "true"

        private val tokensFile: File = File(repoRoot(), "design/omarchy-themes.json")

        @JvmStatic
        @BeforeClass
        fun regenerateTokensFileWhenRequested() {
            // Rewrite before any test runs so update mode is order-independent.
            if (updateMode) {
                tokensFile.parentFile.mkdirs()
                tokensFile.writeBytes(canonicalTokensJson().toByteArray(Charsets.UTF_8))
            }
        }

        private fun repoRoot(): File {
            System.getProperty("omarchy.repo.root")?.let { return File(it) }
            var dir = File(System.getProperty("user.dir") ?: ".")
            while (dir != null &&
                !File(dir, "settings.gradle.kts").isFile &&
                !File(dir, "settings.gradle").isFile
            ) {
                dir = dir.parentFile
            }
            checkNotNull(dir) { "Cannot locate the repository root; run via Gradle." }
            return dir
        }

        // --------------------------------------------------------------
        // Canonical exporter
        // --------------------------------------------------------------

        private fun argbHex(value: Int) = "#%08X".format(value)

        private fun esc(value: String) = value.replace("\\", "\\\\").replace("\"", "\\\"")

        private fun canonicalTokensJson(): String {
            val sb = StringBuilder()
            sb.append("{\n")
            sb.append("  \"schema\": 1,\n")
            sb.append("  \"source\": \"core/src/main/java/com/omarchy/auto/core/Theme.kt\",\n")
            sb.append("  \"colorFormat\": \"#AARRGGBB\",\n")
            sb.append("  \"alphaRange\": \"0-255\",\n")
            sb.append("  \"themes\": [\n")
            val last = Themes.all.size - 1
            Themes.all.forEachIndexed { index, theme ->
                sb.append("    {\n")
                sb.append("      \"id\": \"${esc(theme.id)}\",\n")
                sb.append("      \"name\": \"${esc(theme.name)}\",\n")
                sb.append("      \"shortName\": \"${esc(theme.shortName)}\",\n")
                for ((field, value) in listOf(
                    "background" to theme.background,
                    "surface" to theme.surface,
                    "surfaceAlt" to theme.surfaceAlt,
                    "foreground" to theme.foreground,
                    "dim" to theme.dim,
                    "border" to theme.border,
                    "accent" to theme.accent,
                    "green" to theme.green,
                    "yellow" to theme.yellow,
                    "red" to theme.red,
                )) {
                    sb.append("      \"$field\": \"${argbHex(value)}\",\n")
                }
                sb.append("      \"scrimAlpha\": ${theme.scrimAlpha},\n")
                sb.append("      \"stageAlpha\": ${theme.stageAlpha}\n")
                sb.append("    }" + if (index == last) "\n" else ",\n")
            }
            sb.append("  ]\n")
            sb.append("}\n")
            return sb.toString()
        }

        /**
         * Strict JSON subset parser for the tokens file: objects, arrays,
         * strings, and integers. No third-party dependency; deliberately
         * independent of the exporter above.
         */
        private class MiniJson(private val text: String) {
            private var i = 0

            fun parseDocument(): Any? {
                val value = parseValue()
                skipWs()
                require(i >= text.length) { "Trailing content at offset $i" }
                return value
            }

            private fun parseValue(): Any? {
                skipWs()
                require(i < text.length) { "Unexpected end of input" }
                return when (val c = text[i]) {
                    '{' -> parseObject()
                    '[' -> parseArray()
                    '"' -> parseString()
                    else -> {
                        require(c == '-' || c.isDigit()) { "Unexpected character '$c' at $i" }
                        parseNumber()
                    }
                }
            }

            private fun skipWs() {
                while (i < text.length && text[i].let { it == ' ' || it == '\n' || it == '\t' || it == '\r' }) i++
            }

            private fun parseObject(): Map<String, Any?> {
                i++ // '{'
                val map = LinkedHashMap<String, Any?>()
                skipWs()
                if (i < text.length && text[i] == '}') {
                    i++
                    return map
                }
                while (true) {
                    skipWs()
                    val key = parseString()
                    skipWs()
                    require(i < text.length && text[i] == ':') { "Expected ':' at $i" }
                    i++
                    map[key] = parseValue()
                    skipWs()
                    require(i < text.length) { "Unterminated object" }
                    when (text[i]) {
                        ',' -> i++
                        '}' -> {
                            i++
                            return map
                        }
                        else -> throw IllegalArgumentException("Expected ',' or '}' at $i")
                    }
                }
            }

            private fun parseArray(): List<Any?> {
                i++ // '['
                val list = ArrayList<Any?>()
                skipWs()
                if (i < text.length && text[i] == ']') {
                    i++
                    return list
                }
                while (true) {
                    list.add(parseValue())
                    skipWs()
                    require(i < text.length) { "Unterminated array" }
                    when (text[i]) {
                        ',' -> i++
                        ']' -> {
                            i++
                            return list
                        }
                        else -> throw IllegalArgumentException("Expected ',' or ']' at $i")
                    }
                }
            }

            private fun parseString(): String {
                require(i < text.length && text[i] == '"') { "Expected string at $i" }
                i++
                val sb = StringBuilder()
                while (true) {
                    require(i < text.length) { "Unterminated string" }
                    when (val c = text[i]) {
                        '"' -> {
                            i++
                            return sb.toString()
                        }
                        '\\' -> {
                            i++
                            require(i < text.length) { "Unterminated escape" }
                            when (val escaped = text[i]) {
                                '"' -> sb.append('"')
                                '\\' -> sb.append('\\')
                                '/' -> sb.append('/')
                                'n' -> sb.append('\n')
                                't' -> sb.append('\t')
                                'r' -> sb.append('\r')
                                else -> throw IllegalArgumentException("Unsupported escape '\\$escaped' at $i")
                            }
                            i++
                        }
                        else -> {
                            sb.append(c)
                            i++
                        }
                    }
                }
            }

            private fun parseNumber(): Long {
                val start = i
                if (i < text.length && text[i] == '-') i++
                while (i < text.length && text[i].isDigit()) i++
                require(i > start) { "Expected number at $start" }
                return text.substring(start, i).toLong()
            }
        }
    }
}
