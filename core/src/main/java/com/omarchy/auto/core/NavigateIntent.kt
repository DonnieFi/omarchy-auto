package com.omarchy.auto.core

import android.net.Uri

/**
 * Parses Assistant and geo navigation intents (quality NF-6) into a
 * destination the router can start, or into a search query when the intent
 * has no coordinates.
 */
object NavigateIntent {
    data class Request(
        val query: String? = null,
        val latitude: Double? = null,
        val longitude: Double? = null,
    ) {
        val hasCoordinates: Boolean
            get() = latitude != null && longitude != null
    }

    fun parse(uri: Uri?): Request? {
        if (uri == null) return null
        val scheme = uri.scheme?.lowercase() ?: return null
        if (scheme != "geo" && scheme != "google.navigation") return null
        // geo: and google.navigation: are opaque. getQueryParameter throws on them.
        val ssp = uri.schemeSpecificPart.orEmpty()
        val query = opaqueParameter(ssp, "q")
        val coords = coordinates(ssp, query)
        if (coords != null) {
            return Request(query = query, latitude = coords.first, longitude = coords.second)
        }
        val text = query?.trim().orEmpty()
        if (text.isEmpty() || text == "0,0") return null
        return Request(query = text)
    }

    private fun opaqueParameter(ssp: String, name: String): String? {
        val query = if ('?' in ssp) ssp.substringAfter('?') else ssp
        if ('?' !in ssp && '=' !in ssp) return null
        for (part in query.split('&')) {
            val eq = part.indexOf('=')
            if (eq <= 0) continue
            val key = Uri.decode(part.substring(0, eq))
            if (key != name) continue
            val value = Uri.decode(part.substring(eq + 1).replace('+', ' ')).trim()
            return value.ifEmpty { null }
        }
        return null
    }

    private fun coordinates(ssp: String, query: String?): Pair<Double, Double>? {
        parsePair(ssp.substringBefore('?'))?.let { return it }
        if (query != null) parsePair(query)?.let { return it }
        return null
    }

    private fun parsePair(raw: String): Pair<Double, Double>? {
        val parts = raw.split(',')
        if (parts.size < 2) return null
        val lat = parts[0].trim().toDoubleOrNull() ?: return null
        val lon = parts[1].trim().substringBefore(' ').toDoubleOrNull() ?: return null
        if (lat == 0.0 && lon == 0.0) return null
        if (lat !in -90.0..90.0 || lon !in -180.0..180.0) return null
        return lat to lon
    }
}
