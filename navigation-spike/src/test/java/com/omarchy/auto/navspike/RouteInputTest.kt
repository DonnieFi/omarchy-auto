package com.omarchy.auto.navspike
import org.junit.Assert.*
import org.junit.Test
class RouteInputTest {
    @Test fun acceptsHalifaxCoordinates() {
        assertEquals(RoutePoint(44.6488, -63.5752), RoutePoint.parse(" 44.6488, -63.5752 "))
    }
    @Test fun rejectsInvalidOrAmbiguousCoordinates() {
        listOf("", "Halifax", "1,2,3", "NaN,0", "0,Infinity", "91,0", "0,-181").forEach {
            try { RoutePoint.parse(it); fail("Accepted $it") } catch (_: IllegalArgumentException) { }
        }
    }
}
