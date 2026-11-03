package com.omarchy.auto.core.diag

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CrashReportTest {
    private val report = CrashReport(
        timestampMs = 1_793_678_400_000,
        versionCode = 11,
        versionName = "0.1.10",
        manufacturer = "Google",
        model = "Pixel 11",
        sdkInt = 37,
        release = "17",
        hostPackage = "com.google.android.projection.gearhead",
        hostVersion = "17.9.664044-release",
        exception = "java.lang.IllegalStateException",
        message = "bad \"quote\" from a@b.com",
        stackTrace = "line1\n\tat x(Y.kt:1)",
    )

    @Test
    fun serializesAllFieldsAsEscapedJson() {
        val json = report.toJson()
        assertTrue(json.startsWith("{") && json.endsWith("}"))
        assertTrue(json.contains("\"versionCode\":11"))
        assertTrue(json.contains("\"versionName\":\"0.1.10\""))
        assertTrue(json.contains("\"model\":\"Pixel 11\""))
        assertTrue(json.contains("\"sdkInt\":37"))
        assertTrue(json.contains("\"hostVersion\":\"17.9.664044-release\""))
        assertTrue(json.contains("\"ts\":1793678400000"))
        assertTrue(json.contains("\"stackTrace\":\"line1\\n\\tat x(Y.kt:1)\""))
        assertTrue(json.contains("bad \\\"quote\\\""))
    }

    @Test
    fun scrubsMessageAndTrace() {
        val json = report.copy(stackTrace = "at /home/red/x").toJson()
        assertFalse(json.contains("a@b.com"))
        assertFalse(json.contains("/home/red"))
    }

    @Test
    fun nullHostIsJsonNull() {
        assertTrue(report.copy(hostPackage = null, hostVersion = null).toJson().contains("\"hostPackage\":null"))
    }
}
