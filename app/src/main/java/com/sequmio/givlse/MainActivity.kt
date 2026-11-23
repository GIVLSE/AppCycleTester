package com.sequmio.givlse

import android.content.Intent
import android.content.ComponentName
import android.content.ActivityNotFoundException
import com.sequmio.givlse.runner.CycleAccessibilityService
import kotlinx.coroutines.flow.MutableStateFlow
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier

class MainActivity : ComponentActivity() {
    private val settingsReturnVersion = MutableStateFlow(0)

    override fun onResume() {
        super.onResume()
        settingsReturnVersion.value += 1
    }

    private fun showAccessibilityServiceSettings() {
        // Direct service-details page where supported; otherwise generic Accessibility settings.
        val service = ComponentName(this, CycleAccessibilityService::class.java)
        val details = Intent("android.settings.ACCESSIBILITY_DETAILS_SETTINGS").apply {
            putExtra(Intent.EXTRA_COMPONENT_NAME, service.flattenToString())
        }
        try {
            startActivity(details)
        } catch (_: ActivityNotFoundException) {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        } catch (_: SecurityException) {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(13, 20, 36)
        window.navigationBarColor = android.graphics.Color.rgb(13, 20, 36)
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = 0
        setContent {
            val permissionRefresh by settingsReturnVersion.collectAsState()
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
                            openAccessibility = { showAccessibilityServiceSettings() },
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
