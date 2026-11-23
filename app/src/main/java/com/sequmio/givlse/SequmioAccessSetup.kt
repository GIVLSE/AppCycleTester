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
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Unified settings checklist.
 *
 * The first checkmark records explicit consent. The other two are READ-ONLY
 * reflections of the real Android accessibility state and live service.
 * Tapping an unchecked system permission opens the appropriate Android screen.
 */
@Composable
internal fun SequmioAccessSetup(
    ready: Boolean,
    systemEnabled: Boolean,
    consent: Boolean,
    onConsent: () -> Unit,
    onAppInfo: () -> Unit,
    onAccessibility: () -> Unit,
    onPrivacy: () -> Unit,
    onBack: () -> Unit,
    onStart: () -> Unit
) {
    var consentChecked by remember { mutableStateOf(false) }
    val checkColors = CheckboxDefaults.colors(
        checkedColor = SeqColors.PrimaryAction,
        uncheckedColor = SeqColors.Muted,
        checkmarkColor = Color.White,
        disabledCheckedColor = SeqColors.Teal,
        disabledUncheckedColor = SeqColors.Border
    )
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        SeqTopBar("Permissions", onBack)
        Text(
            "One place to review access. Sequmio reads permission status from Android " +
                "when you return from Settings; it cannot turn on system accessibility itself.",
            color = SeqColors.Muted, fontSize = 14.sp
        )
        SeqCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text("1. Your consent", fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                Text(
                    "Sequmio uses Android Accessibility Service to open apps you choose and " +
                        "observe which app is in the foreground. The service accesses the " +
                        "active app package name and window-change timestamps. Plans, " +
                        "consent and run history stay on your device.",
                    color = SeqColors.Muted, fontSize = 14.sp
                )
                Text(
                    "Sequmio does not read screen text, record taps or screenshots, " +
                        "or send test data to the developer or third parties.",
                    color = SeqColors.Muted, fontSize = 14.sp
                )
                Row(
                    Modifier.fillMaxWidth().clickable(enabled = !consent) {
                        consentChecked = !consentChecked
                    },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = consent || consentChecked,
                        onCheckedChange = if (consent) null else ({ checked: Boolean -> consentChecked = checked }),
                        enabled = !consent,
                        colors = checkColors
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (consent) "Consent provided" else
                            "I understand this access and agree to its use for testing.",
                        color = SeqColors.Text, fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f)
                    )
                }
                if (!consent) {
                    SeqPrimaryButton(
                        "Agree & continue",
                        onClick = onConsent,
                        enabled = consentChecked,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
        SeqCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("2. Android Accessibility", fontWeight = FontWeight.SemiBold,
                    fontSize = 17.sp)
                Row(
                    Modifier.fillMaxWidth().clickable(enabled = consent) { onAccessibility() },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = systemEnabled,
                        onCheckedChange = if (consent) ({ _: Boolean -> onAccessibility() }) else null,
                        enabled = consent,
                        colors = checkColors
                    )
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (systemEnabled) "Sequmio Runner enabled"
                            else "Enable Sequmio Runner",
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            if (systemEnabled) "Confirmed by Android. Tap to review the setting."
                            else "Tap to open the Android permission screen and confirm there.",
                            color = SeqColors.Muted, fontSize = 12.sp
                        )
                    }
                    Text("›", color = SeqColors.Primary, fontSize = 28.sp)
                }
                if (!systemEnabled && consent && Build.VERSION.SDK_INT >= 33) {
                    Text(
                        "If Android blocks a manually installed APK, you may need " +
                            "Allow restricted settings in App Info. " +
                            "This security setting cannot be changed automatically.",
                        color = SeqColors.Muted, fontSize = 12.sp
                    )
                    SeqTextButton(onClick = onAppInfo) { Text("Open App Info") }
                }
            }
        }
        SeqCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text("3. Service connection", fontWeight = FontWeight.SemiBold,
                    fontSize = 17.sp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = ready,
                        onCheckedChange = null,
                        enabled = false,
                        colors = checkColors
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (ready) "Runner connected — ready to start"
                        else if (systemEnabled) "Enabled; waiting for service connection"
                        else "Waiting for Android permission",
                        color = if (ready) SeqColors.Success else SeqColors.Muted,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
        if (consent && ready) {
            SeqPrimaryButton("Start test", onStart, Modifier.fillMaxWidth())
        } else if (consent) {
            Text(
                "Return from Android Settings to refresh the checkmarks.",
                color = SeqColors.Muted, fontSize = 13.sp
            )
        }
        SeqTextButton(onClick = onPrivacy) { Text("Privacy Policy") }
        SeqTextButton(onClick = onBack) { Text("Back to Sequmio") }
    }
}
