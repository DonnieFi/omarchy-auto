package com.omarchy.auto.core.diag

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LogBufferTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test
    fun keepsOnlyTheNewestLinesUpToCap() {
        val buffer = LogBuffer(capacity = 3)
        (1..5).forEach { buffer.add("line $it") }
        assertEquals(listOf("line 3", "line 4", "line 5"), buffer.lines())
    }

    @Test
    fun scrubsLinesAsTheyAreAdded() {
        val buffer = LogBuffer(capacity = 10)
        buffer.add("user a@b.com at 44.64876, -63.57532")
        assertEquals(listOf("user <email> at <coords>"), buffer.lines())
    }

    @Test
    fun persistsToFileAndReloadsCappedOnRestart() {
        val file = tmp.root.resolve("app.log")
        val first = LogBuffer(capacity = 3, file = file)
        (1..7).forEach { first.add("line $it") }
        val second = LogBuffer(capacity = 3, file = file)
        assertEquals(listOf("line 5", "line 6", "line 7"), second.lines())
        assertTrue(file.readLines().size <= 6)
    }
}
