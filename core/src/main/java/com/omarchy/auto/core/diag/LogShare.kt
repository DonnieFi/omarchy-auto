package com.omarchy.auto.core.diag

import android.app.Activity
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

/** Shares the scrubbed log buffer as a text/plain attachment via the share sheet. */
object LogShare {
    const val RECIPIENT = "donniefi@mail.grokbot.com"

    fun share(activity: Activity) {
        val pm = activity.packageManager
        val info = pm.getPackageInfo(activity.packageName, 0)
        @Suppress("DEPRECATION")
        val version = "${info.versionName} (${info.versionCode})"
        val dir = File(activity.cacheDir, "shared-logs").apply { mkdirs() }
        val file = File(dir, "omarchy-auto-logs.txt")
        file.writeText(
            "Omarchy Auto $version\n${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}, " +
                "Android ${android.os.Build.VERSION.RELEASE}\n\n" + OaLog.lines().joinToString("\n"),
        )
        val uri = FileProvider.getUriForFile(activity, activity.packageName + ".logs", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_EMAIL, arrayOf(RECIPIENT))
            putExtra(Intent.EXTRA_SUBJECT, "Omarchy Auto logs $version")
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        activity.startActivity(Intent.createChooser(send, "Send logs"))
    }
}
