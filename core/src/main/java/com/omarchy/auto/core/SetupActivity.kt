package com.omarchy.auto.core

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * One-screen setup for permissions. The AA service cannot show a runtime
 * permission dialog in the car, so grant access here before connecting USB.
 */
class SetupActivity : Activity() {
    private lateinit var status: TextView
    private var theme = Themes.all.first()
    private var isLauncher = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeStore.init(this)
        isLauncher = intent.getBooleanExtra(EXTRA_LAUNCHER, false)
        theme = ThemeStore.current()
        window.setBackgroundDrawable(ColorDrawable(theme.background))

        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(28), dp(24), dp(36))
            setBackgroundColor(theme.background)
        }

        column.addView(label("OMARCHY", 30f, theme.accent, bold = true))
        column.addView(label("// AUTO · SETUP", 14f, theme.dim, letterSpacing = 0.12f))
        column.addView(space(20))

        column.addView(label("PERMISSIONS", 12f, theme.dim, letterSpacing = 0.12f))
        status = label("", 14f, theme.foreground).apply {
            setLineSpacing(0f, 1.25f)
            setPadding(0, dp(8), 0, dp(16))
        }
        column.addView(status)

        column.addView(
            button("1 · NOTIFICATION ACCESS") {
                startActivity(NotificationAccess.settingsIntent())
            },
        )
        column.addView(space(10))
        column.addView(
            button("2 · LOCATION ACCESS") {
                requestPermissions(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                    ),
                    REQUEST_LOCATION,
                )
            },
        )
        if (!isLauncher) {
            column.addView(space(10))
            column.addView(
                button("3 · SERVICE NOTIFICATION") {
                    if (Build.VERSION.SDK_INT >= 33) {
                        requestPermissions(
                            arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                            REQUEST_NOTIFICATIONS,
                        )
                    }
                },
            )
            column.addView(space(24))

            column.addView(label("ANDROID AUTO OVER USB", 12f, theme.dim, letterSpacing = 0.12f))
            column.addView(
                label(
                    "1. Install this private build through Play Internal App Sharing.\n" +
                        "2. Open Omarchy Auto setup and grant the permissions above.\n" +
                        "3. Connect the phone to the car with USB.\n" +
                        "4. Launch Omarchy Auto from the Android Auto launcher.\n\n" +
                        "The car display is drawn by this app on the NavigationTemplate map surface. " +
                        "The host still owns the surrounding Android Auto action strip.\n\n" +
                        "Unknown sources is not required for a Play-installed build. If you use an " +
                        "ADB-sideloaded Car App Library build, Android Auto may hide it even with " +
                        "Unknown sources on. See the README.",
                    13f,
                    theme.foreground,
                ).apply {
                    setLineSpacing(0f, 1.35f)
                    setPadding(0, dp(8), 0, dp(18))
                },
            )
        }

        column.addView(space(16))
        column.addView(label("MAP & LOCATION PRIVACY", 12f, theme.dim, letterSpacing = 0.12f))
        column.addView(
            label(
                "When the Drive stage is open, the phone's location is sent to OpenFreeMap " +
                    "tile servers so the map can be rendered. Omarchy Auto does not store a " +
                    "location history. There is no map-off switch yet.",
                12f,
                theme.dim,
            ).apply { setPadding(0, dp(8), 0, dp(18)) },
        )

        column.addView(
            button("APP DETAILS") {
                startActivity(
                    Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:$packageName"),
                    ),
                )
            },
        )

        val scroll = ScrollView(this).apply {
            setBackgroundColor(theme.background)
            addView(
                column,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )
        }
        setContentView(scroll)
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        refreshStatus()
    }

    private fun refreshStatus() {
        val notification = NotificationAccess.isEnabled(this)
        val location = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        val post = Build.VERSION.SDK_INT < 33 ||
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        status.text = buildString {
            append(if (notification) "✓" else "×")
            append(" notification access\n")
            append(if (location) "✓" else "×")
            append(" fine location")
            if (!isLauncher) {
                append("\n")
                append(if (post) "✓" else "×")
                append(" service notification")
            }
        }
        LiveDataStore.setNotificationAccess(notification)
        LiveDataStore.setLocation(location)
    }

    private fun label(
        text: String,
        sizeSp: Float,
        color: Int,
        bold: Boolean = false,
        letterSpacing: Float = 0f,
    ) = TextView(this).apply {
        this.text = text
        textSize = sizeSp
        setTextColor(color)
        typeface = if (bold) {
            Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        } else {
            Typeface.MONOSPACE
        }
        this.letterSpacing = letterSpacing
        includeFontPadding = false
    }

    private fun button(text: String, action: () -> Unit) = Button(this).apply {
        this.text = text
        textSize = 13f
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        isAllCaps = false
        setTextColor(theme.background)
        backgroundTintList = android.content.res.ColorStateList.valueOf(theme.accent)
        setOnClickListener { action() }
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dp(52),
        )
    }

    private fun space(dp: Int) = TextView(this).apply {
        layoutParams = LinearLayout.LayoutParams(1, dp(dp))
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    companion object {
        const val EXTRA_LAUNCHER = "launcher_setup"
        private const val REQUEST_LOCATION = 41
        private const val REQUEST_NOTIFICATIONS = 42
    }
}
