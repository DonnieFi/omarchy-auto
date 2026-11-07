package com.omarchy.auto.core

/**
 * Staggered QWERTY geometry for the phone launcher keyboard.
 * Android Auto does not draw this keyboard. Destination search there uses the
 * host SearchTemplate keyboard.
 */
object QwertyLayout {
    data class KeyDef(val label: String, val weight: Float = 1f)

    data class Row(val indentKeys: Float, val keys: List<KeyDef>)

    fun rows(): List<Row> = listOf(
        Row(
            0f,
            listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0").map { KeyDef(it) },
        ),
        Row(
            0.5f,
            listOf("Q", "W", "E", "R", "T", "Y", "U", "I", "O", "P").map { KeyDef(it) },
        ),
        Row(
            0.85f,
            listOf("A", "S", "D", "F", "G", "H", "J", "K", "L").map { KeyDef(it) },
        ),
        Row(
            1.4f,
            listOf("Z", "X", "C", "V", "B", "N", "M", ".", "/").map { KeyDef(it) },
        ),
        Row(0f, listOf(KeyDef("SPACE", weight = 6f))),
    )

    fun valueOf(label: String): String = if (label == "SPACE") " " else label
}
