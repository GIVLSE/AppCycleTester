package com.sequmio.givlse

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import com.sequmio.givlse.runner.CycleAccessibilityService

/** System enablement is not the same as a currently bound service. Read only. */
internal fun isSequmioAccessibilityEnabled(context: Context): Boolean {
    val expected = ComponentName(context, CycleAccessibilityService::class.java)
    val enabled = Settings.Secure.getString(context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
    return enabled.split(':').any { ComponentName.unflattenFromString(it) == expected }
}
