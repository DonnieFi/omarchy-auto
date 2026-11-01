package com.omarchy.auto.launcher

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.Toast
import com.omarchy.auto.core.LiveDataController
import com.omarchy.auto.core.SetupActivity
import com.omarchy.auto.core.ThemeStore
import com.omarchy.auto.core.TileId

class MainActivity : Activity() {

    private lateinit var controller: LiveDataController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeStore.init(this)
        controller = LiveDataController(this)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val view = TileView(this).apply {
            onSetup = {
                startActivity(
                    Intent(this@MainActivity, SetupActivity::class.java)
                        .putExtra(SetupActivity.EXTRA_LAUNCHER, true),
                )
            }
            onTileTap = { tile -> launch(tile) }
        }
        setContentView(view)

        if (Build.VERSION.SDK_INT >= 30) {
            window.decorView.windowInsetsController?.apply {
                hide(WindowInsets.Type.systemBars())
                systemBarsBehavior =
                    WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility =
                android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                android.view.View.SYSTEM_UI_FLAG_FULLSCREEN or
                android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        }

        maybeAskForLocation()
    }

    override fun onResume() {
        super.onResume()
        // Rebuild after a permission grant, a Settings round-trip, or a return
        // from Maps/Spotify.
        controller.stop()
        controller.start()
    }

    override fun onPause() {
        controller.stop()
        super.onPause()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        controller.stop()
        controller.start()
    }

    private fun launch(tile: TileId) {
        when (tile) {
            TileId.DRIVE -> open(
                listOf("com.google.android.apps.maps"),
                Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=")),
            )

            TileId.MEDIA -> open(
                listOf("com.spotify.music", "com.google.android.apps.youtube.music"),
                null,
            )

            TileId.COMMS -> open(
                emptyList(),
                Intent(Intent.ACTION_VIEW, Uri.parse("sms:")),
            )

            // Vehicle remains a visible mock for now; Torque is intentionally
            // not wired up.
            TileId.VEHICLE -> Unit

            TileId.HOME -> open(
                emptyList(),
                Intent(Settings.ACTION_SETTINGS),
            )
        }
    }

    private fun open(packages: List<String>, fallback: Intent?) {
        for (pkg in packages) {
            val intent = packageManager.getLaunchIntentForPackage(pkg)
            if (intent != null && startSafely(intent)) return
        }
        if (fallback != null && fallback.resolveActivity(packageManager) != null &&
            startSafely(fallback)
        ) {
            return
        }
        val label = when {
            packages.contains("com.spotify.music") -> "Spotify or YouTube Music"
            packages.contains("com.google.android.apps.maps") -> "Maps"
            else -> "an app for this tile"
        }
        Toast.makeText(this, "Install $label to open this tile", Toast.LENGTH_SHORT).show()
    }

    private fun startSafely(intent: Intent): Boolean = try {
        startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }

    private fun maybeAskForLocation() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val prefs = getSharedPreferences("omarchy_auto", MODE_PRIVATE)
        if (prefs.getBoolean("asked_location", false)) return
        prefs.edit().putBoolean("asked_location", true).apply()
        requestPermissions(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            ),
            REQUEST_LOCATION,
        )
    }

    companion object {
        private const val REQUEST_LOCATION = 51
    }
}
