package com.sequmio.givlse.runner

/** Deterministic retry and external permission window policy. */
object RunRecoveryPolicy {
    const val MAX_RETRIES = 2
    const val WINDOW_CONFIRM_TIMEOUT_MS = 8_000L
    const val FOREGROUND_LOSS_TIMEOUT_MS = 5_000L
    const val PERMISSION_DIALOG_TIMEOUT_MS = 45_000L

    fun canRetry(retriesUsed: Int): Boolean = retriesUsed < MAX_RETRIES

    fun isPermissionDialog(packageName: String): Boolean {
        val name = packageName.lowercase()
        return name == "com.android.permissioncontroller" ||
            name == "com.google.android.permissioncontroller" ||
            name.endsWith(".permissioncontroller")
    }

    fun interruptionTimeout(packageName: String): Long =
        if (isPermissionDialog(packageName)) PERMISSION_DIALOG_TIMEOUT_MS
        else FOREGROUND_LOSS_TIMEOUT_MS
}
