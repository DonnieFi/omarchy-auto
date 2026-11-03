package com.omarchy.auto.navspike

import android.app.Activity
import android.os.Bundle
import android.content.Intent
import com.stadiamaps.ferrostar.core.http.HttpClientProvider
import com.stadiamaps.ferrostar.core.http.IResponse
import org.json.JSONObject
import uniffi.ferrostar.RouteRequest

/** Debug-only fixture replay. Route requests use a recording; basemap tiles still use the network. */
class ReplayActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val bytes = assets.open("valhalla-driving-route.json").use { it.readBytes() }
        val points = JSONObject(String(bytes)).getJSONArray("waypoints")
        val start = points.getJSONObject(0).getJSONArray("location")
        val end = points.getJSONObject(points.length() - 1).getJSONArray("location")
        val client = object : HttpClientProvider {
            override suspend fun call(request: RouteRequest): IResponse = object : IResponse {
                override val isSuccessful = true
                override val code = 200
                override fun bodyBytes() = bytes
            }
        }
        SpikeRuntime.start(this, "", RoutePoint(end.getDouble(1), end.getDouble(0)), true,
            RoutePoint(start.getDouble(1), start.getDouble(0)), replayClient = client) {
                startActivity(Intent(this, NavigationActivity::class.java))
                finish()
            }
    }
}
