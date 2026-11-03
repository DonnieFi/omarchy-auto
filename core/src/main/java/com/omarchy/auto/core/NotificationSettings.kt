package com.omarchy.auto.core

import android.content.Context
import android.content.SharedPreferences

/**
 * Privacy policy for the Comms tile. Sensitive categories are blocked by
 * default. The optional allow-list lets the owner choose which packages may
 * surface at all; notification bodies are never logged.
 */
object NotificationSettings {
    private const val PREFS = "omarchy_notifications"
    private const val KEY_ALLOW_ALL = "allow_all"
    private const val KEY_SENSITIVE = "sensitive_filter"
    private const val KEY_ALLOWED = "allowed_packages"
    private const val KEY_KNOWN = "known_packages"

    private val sensitiveText = Regex(
        "(?i)(\\b(otp\\d*|one[- ]?time (?:password|code)|verification code|security code|" +
            "two[- ]?factor|2fa|authentication code|passcode|login code|sign[- ]?in code)\\b|" +
            "sensitive notification content hidden)",
    )
    private val sensitivePackage = Regex(
        "(?i)(bank|wallet|paypal|venmo|revolut|cash\\.app)",
    )

    private var prefs: SharedPreferences? = null

    @Volatile
    var allowAll: Boolean = true
        private set

    @Volatile
    var blockSensitive: Boolean = true
        private set

    @Volatile
    var allowedPackages: Set<String> = emptySet()
        private set

    @Volatile
    var knownPackages: Set<String> = emptySet()
        private set

    fun init(context: Context) {
        if (prefs != null) return
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        allowAll = prefs?.getBoolean(KEY_ALLOW_ALL, true) ?: true
        blockSensitive = prefs?.getBoolean(KEY_SENSITIVE, true) ?: true
        allowedPackages = prefs?.getStringSet(KEY_ALLOWED, emptySet())
            ?.toSet() ?: emptySet()
        knownPackages = prefs?.getStringSet(KEY_KNOWN, emptySet())
            ?.toSet() ?: emptySet()
    }

    fun isPackageAllowed(packageName: String): Boolean =
        packageAllowed(packageName, allowAll, allowedPackages)

    internal fun packageAllowed(
        packageName: String,
        allowAll: Boolean,
        allowedPackages: Set<String>,
    ): Boolean = allowAll || packageName in allowedPackages

    fun isSensitivePackage(packageName: String): Boolean =
        blockSensitive && sensitivePackage.containsMatchIn(packageName)

    fun isSensitiveText(title: String, body: String): Boolean =
        blockSensitive && sensitiveText.containsMatchIn("$title $body")

    fun recordPackage(context: Context, packageName: String) {
        init(context)
        if (packageName in knownPackages) return
        knownPackages = knownPackages + packageName
        prefs?.edit()?.putStringSet(KEY_KNOWN, knownPackages)?.apply()
    }

    fun setAllowAll(context: Context, value: Boolean) {
        init(context)
        allowAll = value
        prefs?.edit()?.putBoolean(KEY_ALLOW_ALL, value)?.apply()
    }

    fun setBlockSensitive(context: Context, value: Boolean) {
        init(context)
        blockSensitive = value
        prefs?.edit()?.putBoolean(KEY_SENSITIVE, value)?.apply()
    }

    fun setPackageAllowed(context: Context, packageName: String, allowed: Boolean) {
        init(context)
        allowedPackages = if (allowed) {
            allowedPackages + packageName
        } else {
            allowedPackages - packageName
        }
        prefs?.edit()?.putStringSet(KEY_ALLOWED, allowedPackages)?.apply()
    }

    fun label(context: Context, packageName: String): String = try {
        val pm = context.packageManager
        val info = pm.getApplicationInfo(packageName, 0)
        pm.getApplicationLabel(info).toString()
    } catch (_: Exception) {
        packageName.substringAfterLast('.')
    }
}
