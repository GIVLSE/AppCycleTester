package com.sequmio.givlse

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Explicit disclosure + consent before Android's independent Accessibility grant. */
@Composable
internal fun AccessSetupDialog(
    ready: Boolean,
    consentAccepted: Boolean,
    onConsent: () -> Unit,
    onOpenAppDetails: () -> Unit,
    onOpenAccessibility: () -> Unit,
    onDismiss: () -> Unit,
    onStart: () -> Unit
) {
    var checked by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (!consentAccepted) "Согласие на доступ"
            else if (ready) "Доступ подключён" else "Подключение службы") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (!consentAccepted) {
                    Text("Sequmio запрашивает доступ к специальным возможностям Android " +
                        "для последовательного запуска выбранных вами приложений и проверки активного окна.")
                    Text("Служба получает название пакета активного приложения и время появления окна. " +
                        "Эти сведения, ваши настройки и результаты тестов сохраняются локально на устройстве.")
                    Text("Данные не отправляются разработчику и не передаются третьим лицам. " +
                        "Текущая версия не читает текст окон, не записывает действия и не делает скриншоты.")
                    Text("Этот доступ доброволен. Без него автоматический запуск недоступен. " +
                        "Вы можете отключить службу в настройках Android в любой момент.")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = checked, onCheckedChange = { checked = it })
                        Text("Я понимаю назначение доступа и согласен")
                    }
                } else {
                    Text("Для переключения между приложениями включите Sequmio Runner в " +
                        "специальных возможностях Android. Система потребует вашего подтверждения.")
                    if (!ready) {
                        Text("Шаг 1. Проверка ограниченных настроек",
                            style = MaterialTheme.typography.titleSmall)
                        Text(if (Build.VERSION.SDK_INT >= 33)
                            "Если Android ограничивает доступ для установленного вручную APK, " +
                                "в сведениях о приложении найдите «Разрешить ограниченные настройки». " +
                                "При установке через Google Play этот шаг обычно не требуется."
                            else "Перейдите сразу ко второму шагу.")
                        OutlinedButton(onClick = onOpenAppDetails, modifier = Modifier.fillMaxWidth()) {
                            Text("Сведения о приложении")
                        }
                        Text("Шаг 2. Включить Sequmio Runner",
                            style = MaterialTheme.typography.titleSmall)
                        OutlinedButton(onClick = onOpenAccessibility, modifier = Modifier.fillMaxWidth()) {
                            Text("Открыть специальные возможности")
                        }
                        Text("Статус: служба ещё не подключена.",
                            color = MaterialTheme.colorScheme.error)
                    } else {
                        Text("Статус: служба подключена.",
                            color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        },
        confirmButton = {
            if (!consentAccepted) {
                Button(enabled = checked, onClick = onConsent) {
                    Text("Согласен, продолжить")
                }
            } else if (ready) {
                Button(onClick = onStart) { Text("Начать тест") }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Закрыть") }
        }
    )
}
