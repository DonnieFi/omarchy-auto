package com.omarchy.auto.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationSettingsTest {
    @Test
    fun blocksOneTimeCodes() {
        assertTrue(
            NotificationSettings.isSensitiveText(
                "Your bank",
                "Verification code 481920. Do not share it.",
            ),
        )
        assertTrue(NotificationSettings.isSensitiveText("Sign-in", "Your 2FA code is 884211"))
        assertTrue(NotificationSettings.isSensitiveText("Passcode", "One-time password: 9912"))
        assertTrue(
            NotificationSettings.isSensitiveText(
                "Shell",
                "Sensitive notification content hidden",
            ),
        )
        assertTrue(NotificationSettings.isSensitiveText("Verification", "otp4"))
    }

    @Test
    fun allowsOrdinaryMessages() {
        assertFalse(NotificationSettings.isSensitiveText("Alex", "Running five minutes late"))
        assertFalse(NotificationSettings.isSensitiveText("Calendar", "Standup at 10:30"))
    }

    @Test
    fun allowListOnlyShowsCheckedAppsWhenEnabled() {
        val allowed = setOf("com.example.chat")
        assertTrue(NotificationSettings.packageAllowed("com.example.other", true, allowed))
        assertTrue(NotificationSettings.packageAllowed("com.example.chat", false, allowed))
        assertFalse(NotificationSettings.packageAllowed("com.example.other", false, allowed))
    }

    @Test
    fun blocksBankingPackages() {
        assertTrue(NotificationSettings.isSensitivePackage("com.example.bank.mobile"))
        assertTrue(NotificationSettings.isSensitivePackage("com.paypal.android.p2pmobile"))
        assertFalse(NotificationSettings.isSensitivePackage("com.google.android.apps.messaging"))
    }
}
