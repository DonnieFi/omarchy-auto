package com.omarchy.auto.core.diag

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ScrubberTest {
    @Test
    fun removesEmails() {
        assertEquals("sent to <email>", Scrubber.scrub("sent to someone.name+tag@example.co.uk"))
    }

    @Test
    fun removesCoordinatePairsAndLabelledCoordinates() {
        assertEquals("fix <coords> ok", Scrubber.scrub("fix 44.64876, -63.57532 ok"))
        assertEquals("lat=<coord> lon=<coord>", Scrubber.scrub("lat=44.648 lon=-63.575"))
        assertEquals("latitude: <coord>", Scrubber.scrub("latitude: 44.6487612"))
    }

    @Test
    fun removesPhoneNumbers() {
        assertEquals("call <phone>", Scrubber.scrub("call +1 (902) 555-0142"))
        assertEquals("sms <phone>", Scrubber.scrub("sms 902-555-0142"))
    }

    @Test
    fun removesUsernamesFromPaths() {
        assertEquals("/home/<user>/x.txt", Scrubber.scrub("/home/red/x.txt"))
        assertEquals("/Users/<user>/a", Scrubber.scrub("/Users/donnie/a"))
        assertEquals("/storage/emulated/<n>/Download", Scrubber.scrub("/storage/emulated/0/Download"))
    }

    @Test
    fun removesAccountNames() {
        assertEquals("accountName=<account>", Scrubber.scrub("accountName=Donnie Fiander"))
    }

    @Test
    fun keepsStackTraceLineNumbersAndVersions() {
        val line = "at com.omarchy.auto.OmarchyScreen.onGetTemplate(OmarchyScreen.kt:101) v0.1.10"
        assertEquals(line, Scrubber.scrub(line))
        assertFalse(Scrubber.scrub("pid 12345 tid 67890").contains("<phone>"))
    }
}
