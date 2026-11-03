package com.omarchy.auto.navspike

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.stadiamaps.ferrostar.maplibreui.views.DynamicallyOrientingNavigationView

class NavigationActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val model = SpikeRuntime.viewModel ?: run { finish(); return }
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                val status by SpikeRuntime.status.collectAsState()
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                    Text(status, Modifier.padding(12.dp))
                    Box(Modifier.weight(1f)) {
                        DynamicallyOrientingNavigationView(modifier = Modifier.fillMaxSize(), baseStyle = org.maplibre.compose.style.BaseStyle.Uri(SpikeRuntime.STYLE), viewModel = model, onTapExit = { SpikeRuntime.stop(); finish() })
                    }
                    Text("© OpenStreetMap contributors · OpenMapTiles · OpenFreeMap", Modifier.padding(8.dp))
                    Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (SpikeRuntime.simulated) {
                            Button(onClick = { SpikeRuntime.simulateWrongTurn() }) { Text("Simulate wrong turn") }
                        }
                        Button(onClick = { SpikeRuntime.stop(); finish() }) { Text("Stop navigation") }
                    }
                }
                }
            }
        }
    }
}
