package com.sequmio.givlse

import android.content.Intent
import android.content.ComponentName
import android.widget.Toast
import android.content.ActivityNotFoundException
import com.sequmio.givlse.runner.CycleAccessibilityService
import kotlinx.coroutines.flow.MutableStateFlow
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import android.net.Uri
import android.os.Bundle
import android.os.Build
import android.Manifest
import android.view.WindowManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.SideEffect
import com.sequmio.givlse.runner.RunnerRuntime
import com.sequmio.givlse.model.RunPhase
import com.sequmio.givlse.data.RunReportStore
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier

class MainActivity : ComponentActivity() {
    companion object {
        const val ACTION_SHOW_RUN_RESULT = "com.sequmio.givlse.SHOW_RUN_RESULT"
    }

    private val runResultOpenVersion = MutableStateFlow(0)
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.action == ACTION_SHOW_RUN_RESULT) runResultOpenVersion.value += 1
    }

    private val settingsReturnVersion = MutableStateFlow(0)
    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            settingsReturnVersion.value += 1
        }

    override fun onResume() {
        super.onResume()
        settingsReturnVersion.value += 1
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && RunnerRuntime.state.value.phase == RunPhase.FINISHED)
            CycleAccessibilityService.instance?.acknowledgeResultVisible()
    }

    private fun tryOpenSettings(intent: Intent): Boolean {
        return try {
            startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        } catch (_: SecurityException) {
            false
        }
    }

    private fun showAccessibilitySettings() {
        if (!tryOpenSettings(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) &&
            !tryOpenSettings(Intent(Settings.ACTION_SETTINGS))) {
            Toast.makeText(this, "Open Android Settings > Accessibility manually.",
                Toast.LENGTH_LONG).show()
        }
    }

    private fun showAccessibilityServiceSettings() {
        // AOSP has a service-specific system action, but it is not a public SDK
        // contract and vendors may not expose it. The ordinary settings route
        // remains independently available, even if a vendor opens the wrong screen.
        val service = ComponentName(this, CycleAccessibilityService::class.java)
        val details = Intent("android.settings.ACCESSIBILITY_DETAILS_SETTINGS")
            .putExtra(Intent.EXTRA_COMPONENT_NAME, service.flattenToString())
        if (!tryOpenSettings(details)) showAccessibilitySettings()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent?.action == ACTION_SHOW_RUN_RESULT) runResultOpenVersion.value += 1
        if (CycleAccessibilityService.instance == null) {
            RunReportStore(this).recoverInterrupted()
        }
        window.statusBarColor = android.graphics.Color.rgb(13, 20, 36)
        window.navigationBarColor = android.graphics.Color.rgb(13, 20, 36)
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = 0
        setContent {
            val permissionRefresh by settingsReturnVersion.collectAsState()
            val resultOpenVersion by runResultOpenVersion.collectAsState()
            val run by RunnerRuntime.state.collectAsState()
            SideEffect {
                if (run.phase == RunPhase.RUNNING || run.phase == RunPhase.PAUSED)
                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                else
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
            SequmioTheme {
                // Unlike Box.background(), Surface propagates the foreground text color
                // to every Compose Text, including content outside custom cards.
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = SeqColors.Background,
                    contentColor = SeqColors.Text
                ) {
                    Box(Modifier.fillMaxSize().background(SeqBackground)) {
                        SequmioApp(
                            permissionRefreshToken = permissionRefresh,
                            resultOpenVersion = resultOpenVersion,
                            requestNotificationPermission = {
                                if (Build.VERSION.SDK_INT >= 33)
                                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            },
                            openAccessibility = { showAccessibilityServiceSettings() },
                            openAccessibilityList = { showAccessibilitySettings() },
                            openDisplaySettings = {
                                startActivity(Intent(Settings.ACTION_DISPLAY_SETTINGS))
                            },
                            openAppDetails = {
                                startActivity(Intent(
                                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    Uri.parse("package:$packageName")
                                ))
                            }
                        )
                    }
                }
            }
        }
    }
}
