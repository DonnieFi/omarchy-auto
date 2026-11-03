package com.omarchy.auto.navspike

/** Explicit coordinates keep this spike focused on routing, without a geocoding dependency. */
data class RoutePoint(val latitude: Double, val longitude: Double) {
    companion object {
        fun parse(text: String): RoutePoint {
            val parts = text.split(',')
            require(parts.size == 2) { "Enter latitude, longitude." }
            val lat = parts[0].trim().toDoubleOrNull()
            val lon = parts[1].trim().toDoubleOrNull()
            require(lat != null && lat.isFinite() && lat in -90.0..90.0 &&
                lon != null && lon.isFinite() && lon in -180.0..180.0) {
                "Use latitude −90…90 and longitude −180…180."
            }
            return RoutePoint(lat, lon)
        }
    }
}
