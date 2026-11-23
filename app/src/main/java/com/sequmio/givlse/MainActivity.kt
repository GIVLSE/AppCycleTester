package com.sequmio.givlse

import android.content.Intent
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
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(13, 20, 36)
        window.navigationBarColor = android.graphics.Color.rgb(13, 20, 36)
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = 0
        setContent {
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
                            openAccessibility = {
                                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
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
