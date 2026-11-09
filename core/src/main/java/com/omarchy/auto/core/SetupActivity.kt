package com.omarchy.auto.core

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.omarchy.auto.core.diag.LogShare
import com.omarchy.auto.core.diag.OaLog

/**
 * One-screen setup for permissions. The AA service cannot show a runtime
 * permission dialog in the car, so grant access here before connecting USB.
 */
class SetupActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var notificationButton: Button
    private lateinit var sensitiveFilterButton: Button
    private lateinit var allowAllNotificationsButton: Button
    private lateinit var notificationSources: LinearLayout
    private lateinit var customBackgroundStatus: TextView
    private var theme = Themes.all.first()
    private var isLauncher = false
    private var pendingNavigate: NavigateIntent.Request? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeStore.init(this)
        DisplaySettings.init(this)
        BackgroundStore.init(this)
        LaunchTargetStore.init(this)
        RailConfigStore.init(this)
        NotificationSettings.init(this)
        OaLog.init(this)
        isLauncher = intent.getBooleanExtra(EXTRA_LAUNCHER, false)
        theme = ThemeStore.current()
        pendingNavigate = NavigateIntent.parse(intent?.data)
        window.setBackgroundDrawable(ColorDrawable(theme.background))

        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(28), dp(24), dp(36))
            setBackgroundColor(theme.background)
        }

        column.addView(label("OMARCHY", 30f, theme.accent, bold = true))
        column.addView(label("// AUTO · SETUP", 14f, theme.dim, letterSpacing = 0.12f))
        column.addView(space(20))
        pendingNavigate?.let { request ->
            column.addView(navigatePrompt(request))
            column.addView(space(16))
        }

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
                        "On Android Auto the car shows the map. Turn instructions and arrival " +
                        "time use the host navigation card. Search, zoom, and settings are on " +
                        "the host action strips. YouTube, browsers, and message lists stay off " +
                        "that map.\n\n" +
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

        column.addView(space(12))
        column.addView(button("SEND LOGS") { LogShare.share(this) })
        column.addView(space(10))
        notificationButton = button("NOTIFICATION TEXT · ${DisplaySettings.label()}") {
            DisplaySettings.cycle()
            refreshDisplayControls()
        }
        column.addView(notificationButton)

        column.addView(space(16))
        column.addView(label("NOTIFICATION PRIVACY", 12f, theme.dim, letterSpacing = 0.12f))
        column.addView(
            label(
                "OTP/2FA, banking, ongoing and system alerts are hidden. Bodies are " +
                    "never logged. When the allow-list is on, only checked apps appear.",
                12f,
                theme.dim,
            ).apply { setPadding(0, dp(8), 0, dp(4)) },
        )
        sensitiveFilterButton = button("SENSITIVE FILTER · ON") {
            NotificationSettings.setBlockSensitive(
                this,
                !NotificationSettings.blockSensitive,
            )
            OmarchyNotificationListener.refresh(this)
            refreshNotificationControls()
        }
        column.addView(sensitiveFilterButton)
        allowAllNotificationsButton = button("ALLOW ALL APPS · ON") {
            NotificationSettings.setAllowAll(this, !NotificationSettings.allowAll)
            OmarchyNotificationListener.refresh(this)
            refreshNotificationControls()
        }
        column.addView(allowAllNotificationsButton)
        notificationSources = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(4), 0, dp(4))
        }
        column.addView(notificationSources)

        column.addView(space(16))
        column.addView(label("RIGHT RAIL TILES & APPS", 12f, theme.dim, letterSpacing = 0.12f))
        column.addView(
            label(
                "Choose which tiles and apps appear in the right-side rail. When more than " +
                    "4 items are selected, the rail scrolls with ▲ / ▼ controls.",
                12f,
                theme.dim,
            ).apply { setPadding(0, dp(8), 0, dp(4)) },
        )
        RailConfigStore.allSelectable().forEach { railItem ->
            column.addView(
                CheckBox(this).apply {
                    text = "${railItem.title} · ${railItem.subtitle}"
                    textSize = 13f
                    typeface = Typeface.MONOSPACE
                    setTextColor(theme.foreground)
                    buttonTintList = ColorStateList.valueOf(theme.accent)
                    isChecked = RailConfigStore.isEnabled(railItem)
                    isEnabled = railItem != RailItemId.HOME
                    gravity = Gravity.CENTER_VERTICAL
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        dp(46),
                    )
                    setOnCheckedChangeListener { _, checked ->
                        RailConfigStore.setEnabled(this@SetupActivity, railItem, checked)
                    }
                },
            )
        }

        column.addView(space(16))
        column.addView(label("MEDIA TILE APP", 12f, theme.dim, letterSpacing = 0.12f))
        column.addView(
            label(
                "Choose the active app for the Media stage and OPEN PLAYER action. " +
                    "YouTube, ChatGPT, and Grok can also open directly inside the car stage.",
                12f,
                theme.dim,
            ).apply { setPadding(0, dp(8), 0, dp(4)) },
        )
        val targets = LaunchTargetStore.stageTargets(this)
        if (targets.isEmpty()) {
            column.addView(
                label(
                    "No supported media or assistant apps are installed.",
                    12f,
                    theme.yellow,
                ).apply { setPadding(0, dp(4), 0, dp(8)) },
            )
        } else {
            val selected = LaunchTargetStore.selected(this).key
            val group = RadioGroup(this).apply {
                orientation = RadioGroup.VERTICAL
                setPadding(0, dp(4), 0, dp(4))
            }
            targets.forEach { target ->
                val installed = LaunchTargetStore.isInstalled(this, target)
                val suffix = when {
                    target.key == "auto" -> ""
                    target.stageApp != null && installed -> " · STAGE + APP"
                    target.stageApp != null -> " · WEB STAGE"
                    else -> ""
                }
                group.addView(
                    RadioButton(this).apply {
                        id = View.generateViewId()
                        text = "${target.label}$suffix"
                        textSize = 13f
                        typeface = Typeface.MONOSPACE
                        setTextColor(theme.foreground)
                        buttonTintList = ColorStateList.valueOf(theme.accent)
                        isChecked = target.key == selected
                        gravity = Gravity.CENTER_VERTICAL
                        layoutParams = RadioGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            dp(48),
                        )
                        setPadding(0, dp(4), 0, dp(4))
                        setOnClickListener {
                            LaunchTargetStore.select(this@SetupActivity, target.key)
                        }
                    },
                )
            }
            column.addView(group)
        }
        column.addView(
            label(
                "ChatGPT and Grok open their app; voice mode is a tap inside the app.",
                11f,
                theme.dim,
            ).apply { setPadding(0, 0, 0, dp(8)) },
        )

        column.addView(space(12))
        column.addView(label("BACKGROUND", 12f, theme.dim, letterSpacing = 0.12f))
        column.addView(
            button("CHOOSE BACKGROUND") {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "image/*"
                }
                startActivityForResult(intent, REQUEST_BACKGROUND)
            },
        )
        if (BackgroundStore.hasBundledTom(this)) {
            column.addView(
                button("USE TOM BACKGROUND") {
                    val appContext = applicationContext
                    Thread {
                        val applied = BackgroundStore.useBundledTom(appContext)
                        runOnUiThread {
                            if (isDestroyed) return@runOnUiThread
                            if (applied) {
                                refreshDisplayControls()
                            } else {
                                Toast.makeText(
                                    this,
                                    "Tom background is not bundled",
                                    Toast.LENGTH_SHORT,
                                ).show()
                            }
                        }
                    }.start()
                },
            )
        }
        column.addView(
            button("USE THEME BACKGROUNDS") {
                BackgroundStore.clear(this)
                refreshDisplayControls()
            },
        )
        customBackgroundStatus = label(
            "CUSTOM BACKGROUND · ${if (BackgroundStore.enabled) "ON" else "OFF"}",
            12f,
            if (BackgroundStore.enabled) theme.green else theme.dim,
        ).apply { setPadding(0, dp(8), 0, dp(12)) }
        column.addView(customBackgroundStatus)

        column.addView(space(16))
        column.addView(label("MAP & LOCATION PRIVACY", 12f, theme.dim, letterSpacing = 0.12f))
        column.addView(
            label(
                "When the Drive stage is open, the phone's location is sent to OpenFreeMap " +
                    "tile servers so the map can be rendered. Address searches are sent to " +
                    "Photon (Komoot) and Nominatim (OpenStreetMap), and active route guidance " +
                    "requests road geometry from OSRM (project-osrm.org) using your current GPS " +
                    "origin and selected destination. Omarchy Auto does not store a location " +
                    "history.",
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
        refreshDisplayControls()
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
        if (::notificationButton.isInitialized) refreshDisplayControls()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        refreshStatus()
    }

    private fun refreshDisplayControls() {
        notificationButton.text = "NOTIFICATION TEXT · ${DisplaySettings.label()}"
        customBackgroundStatus.text =
            "CUSTOM BACKGROUND · ${if (BackgroundStore.enabled) "ON" else "OFF"}"
        customBackgroundStatus.setTextColor(
            if (BackgroundStore.enabled) theme.green else theme.dim,
        )
        refreshNotificationControls()
    }

    private fun refreshNotificationControls() {
        sensitiveFilterButton.text =
            "SENSITIVE FILTER · ${if (NotificationSettings.blockSensitive) "ON" else "OFF"}"
        allowAllNotificationsButton.text =
            "ALLOW ALL APPS · ${if (NotificationSettings.allowAll) "ON" else "OFF"}"
        notificationSources.removeAllViews()
        if (NotificationSettings.allowAll) {
            notificationSources.addView(
                label(
                    "Allow-list off; every non-sensitive app is shown.",
                    12f,
                    theme.dim,
                ).apply { setPadding(0, dp(4), 0, dp(4)) },
            )
            return
        }
        val packages = NotificationSettings.knownPackages.sorted()
        if (packages.isEmpty()) {
            notificationSources.addView(
                label(
                    "No notification sources seen yet. Checked apps will appear here " +
                        "after they post.",
                    12f,
                    theme.yellow,
                ).apply { setPadding(0, dp(4), 0, dp(4)) },
            )
            return
        }
        packages.forEach { pkg ->
            notificationSources.addView(
                CheckBox(this).apply {
                    text = NotificationSettings.label(this@SetupActivity, pkg)
                    textSize = 13f
                    typeface = Typeface.MONOSPACE
                    setTextColor(theme.foreground)
                    buttonTintList = ColorStateList.valueOf(theme.accent)
                    isChecked = pkg in NotificationSettings.allowedPackages
                    gravity = Gravity.CENTER_VERTICAL
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        dp(48),
                    )
                    setOnCheckedChangeListener { _, checked ->
                        NotificationSettings.setPackageAllowed(
                            this@SetupActivity,
                            pkg,
                            checked,
                        )
                        OmarchyNotificationListener.refresh(this@SetupActivity)
                    }
                },
            )
        }
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

    private fun navigatePrompt(request: NavigateIntent.Request): LinearLayout {
        val place = when {
            !request.query.isNullOrBlank() -> request.query
            request.hasCoordinates -> "Pinned place"
            else -> "This place"
        }
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, dp(8))
            addView(label("NAVIGATION REQUEST", 12f, theme.dim, letterSpacing = 0.12f))
            addView(label(place, 16f, theme.foreground, bold = true).apply {
                setPadding(0, dp(8), 0, dp(12))
            })
            addView(button("START ROUTE") {
                val current = pendingNavigate ?: return@button
                pendingNavigate = null
                visibility = View.GONE
                confirmNavigate(current)
            })
            addView(space(8))
            addView(button("NOT NOW") {
                pendingNavigate = null
                visibility = View.GONE
            })
        }
    }

    private fun confirmNavigate(request: NavigateIntent.Request) {
        if (request.hasCoordinates) {
            NavigationController.selectDestination(
                SearchCandidate(
                    title = request.query?.takeIf { it.isNotBlank() } ?: "Pinned place",
                    subtitle = "",
                    latitude = request.latitude ?: return,
                    longitude = request.longitude ?: return,
                ),
                autoStartNav = true,
            )
            Toast.makeText(this, "Route queued for the car map", Toast.LENGTH_LONG).show()
            return
        }
        val query = request.query?.trim().orEmpty()
        if (query.isEmpty()) return
        NavigationController.setQuery(query)
        NavigationController.searchAddress(query)
        Toast.makeText(this, "Searching for $query", Toast.LENGTH_LONG).show()
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_BACKGROUND || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        val appContext = applicationContext
        Thread {
            val applied = BackgroundStore.setCustom(appContext, uri)
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                if (applied) {
                    refreshDisplayControls()
                } else {
                    Toast.makeText(
                        this,
                        "Could not load that image",
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            }
        }.start()
    }

    companion object {
        const val EXTRA_LAUNCHER = "launcher_setup"
        private const val REQUEST_LOCATION = 41
        private const val REQUEST_NOTIFICATIONS = 42
        private const val REQUEST_BACKGROUND = 43
    }
}
