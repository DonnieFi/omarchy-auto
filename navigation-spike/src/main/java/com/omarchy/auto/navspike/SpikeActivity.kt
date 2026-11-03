package com.omarchy.auto.navspike

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class SpikeActivity : ComponentActivity() {
    private val permissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("routing-spike", MODE_PRIVATE)
        val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24,24,24,24) }
        setContentView(ScrollView(this).apply { addView(column) })
        fun label(text: String) = TextView(this).apply { this.text = text; column.addView(this) }
        fun field(hint: String, value: String) = EditText(this).apply {
            this.hint = hint; setText(value); isSingleLine = true; column.addView(this)
        }
        label("Omarchy · Routing alpha")
        label("Ferrostar + Valhalla. Set up while parked. Standard routing needs internet; no live traffic. Maps © OpenStreetMap contributors / OpenMapTiles / OpenFreeMap.")
        label("Stadia Maps API key")
        val key = field("Stadia Maps API key", prefs.getString("key", "") ?: "").apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        label("Destination · latitude, longitude")
        val destination = field("Destination: latitude, longitude", prefs.getString("destination", "44.6488, -63.5752")!!)
        label("Simulation start · latitude, longitude")
        val origin = field("Simulation start: latitude, longitude", "44.6425, -63.5800")
        val simulate = CheckBox(this).apply { text = "Simulate route at 2× (no GPS needed)"; isChecked = true; column.addView(this) }
        val message = label(SpikeRuntime.status.value)
        fun button(text: String, action: () -> Unit) {
            column.addView(Button(this).apply { this.text = text; setOnClickListener { action() } })
        }
        button("Start route") {
            try {
                val target = RoutePoint.parse(destination.text.toString())
                val start = if (simulate.isChecked) RoutePoint.parse(origin.text.toString()) else RoutePoint(0.0, 0.0)
                require(key.text.isNotBlank()) { "Enter your Stadia Maps API key." }
                if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    permissions.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                    message.text = "Grant precise location for the navigation service, then tap Start route again."
                } else {
                    // Private app storage; backups disabled. Never bake a shared key into a distributed APK.
                    prefs.edit().putString("key", key.text.toString()).putString("destination", destination.text.toString()).apply()
                    SpikeRuntime.start(this, key.text.toString(), target, simulate.isChecked, start) {
                        startActivity(Intent(this, NavigationActivity::class.java))
                    }
                }
            } catch (error: IllegalArgumentException) { message.text = error.message }
        }
        button("Show active map") {
            if (SpikeRuntime.viewModel != null) startActivity(Intent(this, NavigationActivity::class.java))
        }
        button("Simulate wrong turn") { SpikeRuntime.simulateWrongTurn() }
        button("Stop navigation") { SpikeRuntime.stop() }
        button("Forget API key") { prefs.edit().remove("key").apply(); key.text.clear() }
        lifecycleScope.launch { SpikeRuntime.status.collect { message.text = it } }
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            permissions.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
        }
    }
}
