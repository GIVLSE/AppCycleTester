package com.sequmio.givlse

import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun SequmioAccessSetup(
    ready: Boolean,
    consent: Boolean,
    onConsent: () -> Unit,
    onAppInfo: () -> Unit,
    onAccessibility: () -> Unit,
    onBack: () -> Unit,
    onStart: () -> Unit
) {
    var approved by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        SeqTopBar("Accessibility Setup", onBack)
        SeqCard(Modifier.fillMaxWidth()) {
            Text(if (ready) "✓ Runner connected" else "○ Runner not connected",
                Modifier.padding(17.dp),
                color = if (ready) SeqColors.Success else SeqColors.Error,
                fontWeight = FontWeight.SemiBold)
        }
        Text("Why Sequmio needs this access", fontWeight = FontWeight.Bold)
        Text(
            "Sequmio uses Android Accessibility Service to launch the apps you select " +
                "and detect which app is in the foreground. It reads the active app's " +
                "package identifier and timestamps of window changes. App selections, " +
                "settings and test logs are stored locally on your phone.",
            color = SeqColors.Muted, fontSize = 14.sp
        )
        Text("V1 does not read screen text, record taps or screenshots, or send " +
            "test information to the developer or third parties.",
            color = SeqColors.Muted, fontSize = 14.sp)
        AccessStep(1, "Review access", "Read the disclosure and choose whether to continue.",
            onClick = null)
        if (consent) {
        if (Build.VERSION.SDK_INT >= 33 && !ready) {
            AccessStep(2, "Open App Info",
                "If Android blocks a sideloaded APK, allow restricted settings manually. " +
                    "Google Play installs normally do not require this step.", onAppInfo)
        }
        AccessStep(3, "Enable Sequmio Runner",
            "Open Android Accessibility settings and explicitly enable Sequmio Runner.",
            onAccessibility)
        }
        if (!consent) {
            SeqCard(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().clickable { approved = !approved }
                    .padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = approved, onCheckedChange = { approved = it })
                    Spacer(Modifier.width(8.dp))
                    Text("I understand this access and consent to its use for app testing.",
                        fontWeight = FontWeight.Medium)
                }
            }
            SeqPrimaryButton("Agree & Continue",
                onClick = onConsent,
                enabled = approved,
                modifier = Modifier.fillMaxWidth())
        } else if (ready) {
            SeqPrimaryButton("Continue to Test", onStart,
                Modifier.fillMaxWidth())
        } else {
            Text("Waiting for system permission. Return here after enabling the service.",
                color = SeqColors.Muted)
        }
        TextButton(onClick = onBack) { Text("Not now") }
    }
}

@Composable
private fun AccessStep(
    number: Int, title: String, description: String, onClick: (() -> Unit)?
) {
    SeqCard(if (onClick != null) Modifier.fillMaxWidth().clickable { onClick() }
        else Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text(number.toString(), color = SeqColors.Teal, fontSize = 22.sp,
                fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                Text(description, color = SeqColors.Muted, fontSize = 12.sp)
            }
            Text("›", fontSize = 25.sp, color = SeqColors.Muted)
        }
    }
}
