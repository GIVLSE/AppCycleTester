package io.github.givlse.appcycletester

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Consent-aware onboarding: permissions are only ever granted by the user in Android Settings.
 * A connected AccessibilityService is the readiness signal, not having visited a settings page.
 */
@Composable
internal fun AccessSetupDialog(
    ready: Boolean,
    onOpenAppDetails: () -> Unit,
    onOpenAccessibility: () -> Unit,
    onDismiss: () -> Unit,
    onStart: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (ready) "Доступ подключён" else "Подключение службы")
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "Для переключения между приложениями нужен доступ к специальным возможностям Android. " +
                        "Он даёт службе широкие возможности управления экраном. V1 использует его для запуска " +
                        "приложений и определения активного окна; запись действий не выполняется."
                )
                if (!ready) {
                    Text("Шаг 1. Разрешение ограниченных настроек",
                        style = MaterialTheme.typography.titleSmall)
                    Text(
                        if (Build.VERSION.SDK_INT >= 33)
                            "При установке APK вручную Android может заблокировать включение службы. " +
                                "Если это произошло, откройте «О приложении» и найдите " +
                                "«Разрешить ограниченные настройки» в меню ⋮. " +
                                "На Xiaomi/HyperOS расположение пункта может отличаться. " +
                                "Если ограничения нет — пропустите этот шаг."
                        else
                            "На этой версии Android обычно нет дополнительной блокировки " +
                                "ограниченных настроек. Переходите ко второму шагу."
                    )
                    OutlinedButton(
                        onClick = onOpenAppDetails,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Открыть сведения о приложении") }
                    Text("Шаг 2. Включение App Cycle Runner",
                        style = MaterialTheme.typography.titleSmall)
                    Text("Откройте «Специальные возможности» → «Скачанные приложения» → " +
                        "«App Cycle Runner» и включите службу.")
                    OutlinedButton(
                        onClick = onOpenAccessibility,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Открыть специальные возможности") }
                    Text("Статус: служба не подключена. Вернитесь сюда после включения.",
                        color = MaterialTheme.colorScheme.error)
                } else {
                    Text("Статус: служба подключена. Можно начать тест.",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.titleSmall)
                }
            }
        },
        confirmButton = {
            if (ready) Button(onClick = onStart) { Text("Начать тест") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(if (ready) "Позже" else "Закрыть")
            }
        }
    )
}
