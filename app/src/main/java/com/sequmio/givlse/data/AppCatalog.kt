package com.sequmio.givlse.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.sequmio.givlse.model.InstalledApp

object AppCatalog {
    fun list(context: Context): List<InstalledApp> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val results = if (Build.VERSION.SDK_INT >= 33)
            pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
        else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(intent, 0)
        }
        return results.asSequence()
            .filter { it.activityInfo != null && it.activityInfo.packageName != context.packageName }
            .map {
                InstalledApp(
                    packageName = it.activityInfo.packageName,
                    activityClass = it.activityInfo.name,
                    label = it.loadLabel(pm)?.toString().orEmpty()
                        .ifBlank { it.activityInfo.packageName }
                )
            }.distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }.toList()
    }
}
