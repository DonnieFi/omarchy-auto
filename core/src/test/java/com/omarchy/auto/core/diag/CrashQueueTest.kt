package com.omarchy.auto.core.diag

import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CrashQueueTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test
    fun deletesOnlyReportsThatGet2xx() {
        val queue = CrashQueue(tmp.root, cap = 10)
        queue.save("{\"n\":1}", nowMs = 1)
        queue.save("{\"n\":2}", nowMs = 2)
        val sent = queue.uploadPending { body -> if (body.contains("1")) 201 else 500 }
        assertEquals(1, sent)
        assertEquals(listOf("{\"n\":2}"), queue.pending().map { it.readText() })
    }

    @Test
    fun keepsReportWhenSenderThrows() {
        val queue = CrashQueue(tmp.root, cap = 10)
        queue.save("{}", nowMs = 1)
        queue.uploadPending { throw java.io.IOException("offline") }
        assertEquals(1, queue.pending().size)
    }

    @Test
    fun capsStoredReportsDroppingOldest() {
        val queue = CrashQueue(tmp.root, cap = 3)
        (1..5).forEach { queue.save("{\"n\":$it}", nowMs = it.toLong()) }
        assertEquals(listOf("{\"n\":3}", "{\"n\":4}", "{\"n\":5}"), queue.pending().map { it.readText() })
    }
}
