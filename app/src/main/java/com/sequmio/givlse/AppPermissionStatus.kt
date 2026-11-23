package com.sequmio.givlse

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.view.accessibility.AccessibilityManager
import com.sequmio.givlse.runner.CycleAccessibilityService

/**
 * Read the real Android permission state. The app never writes accessibility
 * settings and cannot toggle this service on behalf of the user.
 */
internal fun isSequmioAccessibilityEnabled(context: Context): Boolean {
    val manager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
        ?: return false
    return manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        .any { info ->
            info.resolveInfo?.serviceInfo?.let { service ->
                service.packageName == context.packageName &&
                    service.name == CycleAccessibilityService::class.java.name
            } == true
        }
}
