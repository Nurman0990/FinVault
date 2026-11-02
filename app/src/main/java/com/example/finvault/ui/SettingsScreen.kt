package com.example.finvault.ui

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.finvault.RecurringPaymentEntity
import com.example.finvault.ui.theme.FinExpense
import com.example.finvault.ui.theme.FinIncome
import com.example.finvault.util.*
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun SettingsScreen(
    recurringPayments: List<RecurringPaymentEntity>,
    onAddRecurring: (title: String, amount: Int, isIncome: Boolean, intervalDays: Int, currencyCode: String) -> Unit,
    onDeleteRecurring: (RecurringPaymentEntity) -> Unit,
    onBackupRestored: () -> Unit,
    onClearAllData: () -> Unit,
    onExportCsv: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val strings = currentAppStrings()

    val currentTheme by AppPreferencesManager.themeMode.collectAsState()
    val currentLang by AppPreferencesManager.language.collectAsState()
    val currentCurrency by AppPreferencesManager.currency.collectAsState()
    val biometricTimeoutSec by AppPreferencesManager.biometricTimeoutSeconds.collectAsState()
    val biometricEnabled by AppPreferencesManager.biometricEnabled.collectAsState()
    val autoBackupEnabled by AppPreferencesManager.autoBackupEnabled.collectAsState()
    val lastBackupTimestamp by AppPreferencesManager.lastBackupTimestamp.collectAsState()
    val showBalanceOnLaunch by AppPreferencesManager.showBalanceOnLaunch.collectAsState()

    // Диалоги
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showCurrencyDialog by remember { mutableStateOf(false) }
    var showTimeoutDialog by remember { mutableStateOf(false) }
    var showAddRecurringDialog by remember { mutableStateOf(false) }
    var showClearDataConfirmDialog by remember { mutableStateOf(false) }

    // Поля нового регулярного платежа
    var recurringTitle by remember { mutableStateOf("") }
    var recurringAmount by remember { mutableStateOf("") }
    var recurringIsIncome by remember { mutableStateOf(false) }
    var recurringDaysInput by remember { mutableStateOf("30") }

    var backupStatusMessage by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(bottom = 90.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                strings.settingsTitle,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        // =====================================================================
        // 1. ВНЕШНИЙ ВИД И ВАЛЮТА
        // =====================================================================
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Palette, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(strings.appearanceSection, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Text(strings.themeTitle, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = currentTheme == ThemeMode.SYSTEM,
                            onClick = { AppPreferencesManager.setThemeMode(context, ThemeMode.SYSTEM) },
                            label = { Text(strings.themeSystem) },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = currentTheme == ThemeMode.LIGHT,
                            onClick = { AppPreferencesManager.setThemeMode(context, ThemeMode.LIGHT) },
                            label = { Text(strings.themeLight) },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = currentTheme == ThemeMode.DARK,
                            onClick = { AppPreferencesManager.setThemeMode(context, ThemeMode.DARK) },
                            label = { Text(strings.themeDark) },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 14.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                    // ВЫБОР ЯЗЫКА
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { showLanguageDialog = true }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(strings.languageTitle, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                            Text(
                                "${currentLang.flag} ${currentLang.displayName}",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 14.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                    // ВЫБОР ОСНОВНОЙ ВАЛЮТЫ (Тенге, Доллар, Рубль, Евро и т.д.)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { showCurrencyDialog = true }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(strings.currencySectionTitle, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                            Text(
                                "${currentCurrency.flag} ${currentCurrency.code} (${currentCurrency.symbol}) — ${currentCurrency.displayName}",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        // =====================================================================
        // 2. БЕЗОПАСНОСТЬ И ВХОД
        // =====================================================================
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Fingerprint, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(strings.securitySection, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Переключатель биометрии
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(strings.biometricsToggle, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                            Text(strings.biometricsDesc, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = biometricEnabled,
                            onCheckedChange = { AppPreferencesManager.setBiometricEnabled(context, it) }
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                    // Тайм-аут блокировки
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { showTimeoutDialog = true }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(strings.timeoutTitle, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                            val timeoutText = when (biometricTimeoutSec) {
                                0 -> strings.timeoutInstant
                                60 -> strings.timeout1Min
                                300 -> strings.timeout5Min
                                900 -> strings.timeout15Min
                                else -> "$biometricTimeoutSec сек."
                            }
                            Text(timeoutText, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                    // ОСТАВЛЯТЬ ГЛАЗИК ОТКРЫТЫМ ПРИ ВХОДЕ В ПРИЛОЖЕНИЕ
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(strings.showBalanceOnLaunchTitle, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                            Text(strings.showBalanceOnLaunchDesc, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = showBalanceOnLaunch,
                            onCheckedChange = { AppPreferencesManager.setShowBalanceOnLaunch(context, it) }
                        )
                    }
                }
            }
        }

        // =====================================================================
        // 3. РЕГУЛЯРНЫЕ ПЛАТЕЖИ / ПОДПИСКИ
        // =====================================================================
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Autorenew, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(strings.recurringSection, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }

                        IconButton(onClick = { showAddRecurringDialog = true }) {
                            Icon(Icons.Filled.AddCircle, contentDescription = "Добавить", tint = MaterialTheme.colorScheme.primary)
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(strings.recurringDesc, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(10.dp))

                    if (recurringPayments.isEmpty()) {
                        Text(
                            strings.noRecurringPrompt,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            recurringPayments.forEach { item ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(item.title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                                        val sdf = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
                                        Text(
                                            "${item.intervalDays} ${strings.daysUnit} • ${strings.nextDuePrefix} ${sdf.format(Date(item.nextDueTimestamp))}",
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            "${if (item.isIncome) "+" else "-"}${CurrencyConverter.formatAmount(item.amount.toDouble(), item.currencyCode)}",
                                            fontWeight = FontWeight.Bold,
                                            color = if (item.isIncome) FinIncome else FinExpense
                                        )
                                        IconButton(onClick = { onDeleteRecurring(item) }) {
                                            Icon(Icons.Filled.DeleteOutline, contentDescription = "Удалить", modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // =====================================================================
        // 4. ЗАШИФРОВАННЫЙ БЭКАП И ЭКСПОРТ
        // =====================================================================
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(strings.backupSection, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(strings.autoBackupToggle, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                            Text(strings.autoBackupDesc, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = autoBackupEnabled,
                            onCheckedChange = { AppPreferencesManager.setAutoBackupEnabled(context, it) }
                        )
                    }

                    if (lastBackupTimestamp > 0L) {
                        Spacer(modifier = Modifier.height(6.dp))
                        val sdf = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
                        Text(
                            "${strings.lastBackupPrefix} ${sdf.format(Date(lastBackupTimestamp))}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    val result = BackupRestoreManager.createEncryptedBackup(context)
                                    result.onSuccess { file ->
                                        AppPreferencesManager.setLastBackupTimestamp(context, System.currentTimeMillis())
                                        backupStatusMessage = "${strings.backupCreatedSuccess}: ${file.name}"
                                        CsvExporter.shareCsvFile(context, file)
                                    }.onFailure {
                                        backupStatusMessage = "Ошибка: ${it.localizedMessage}"
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(strings.createBackupBtn)
                        }

                        OutlinedButton(
                            onClick = {
                                coroutineScope.launch {
                                    val exportDir = File(context.cacheDir, "exports")
                                    val latestBackup = exportDir.listFiles()?.filter { it.extension == "finvault" }?.maxByOrNull { it.lastModified() }
                                    if (latestBackup != null) {
                                        val res = BackupRestoreManager.restoreEncryptedBackup(context, latestBackup)
                                        res.onSuccess { count ->
                                            backupStatusMessage = "${strings.backupRestoreSuccess} $count"
                                            onBackupRestored()
                                        }.onFailure {
                                            backupStatusMessage = "Ошибка: ${it.localizedMessage}"
                                        }
                                    } else {
                                        backupStatusMessage = strings.noBackupsFound
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(strings.restoreBackupBtn)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    FilledTonalButton(
                        onClick = onExportCsv,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Экспорт операций в CSV")
                    }

                    backupStatusMessage?.let { msg ->
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(msg, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }

        // =====================================================================
        // 5. УПРАВЛЕНИЕ ДАННЫМИ
        // =====================================================================
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.DeleteForever, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(strings.dataSection, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(strings.clearDataDesc, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedButton(
                        onClick = { showClearDataConfirmDialog = true },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(strings.clearDataTitle)
                    }
                }
            }
        }

        // =====================================================================
        // 6. О ПРИЛОЖЕНИИ
        // =====================================================================
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(strings.aboutSection, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(strings.appVersion, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(strings.aboutSecurityInfo, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }

    // =========================================================================
    // ДИАЛОГ ВЫБОРА ОСНОВНОЙ ВАЛЮТЫ
    // =========================================================================
    if (showCurrencyDialog) {
        AlertDialog(
            onDismissRequest = { showCurrencyDialog = false },
            title = { Text(strings.currencyDialogTitle, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    AppCurrency.values().forEach { curr ->
                        val isSelected = currentCurrency == curr
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else Color.Transparent)
                                .clickable {
                                    AppPreferencesManager.setCurrency(context, curr)
                                    showCurrencyDialog = false
                                }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(curr.flag, fontSize = 20.sp)
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        "${curr.code} (${curr.symbol})",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp
                                    )
                                    Text(
                                        curr.displayName,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            RadioButton(
                                selected = isSelected,
                                onClick = {
                                    AppPreferencesManager.setCurrency(context, curr)
                                    showCurrencyDialog = false
                                }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showCurrencyDialog = false }) { Text(strings.cancelBtn) }
            }
        )
    }

    // =========================================================================
    // ДИАЛОГ ВЫБОРА ЯЗЫКА
    // =========================================================================
    if (showLanguageDialog) {
        AlertDialog(
            onDismissRequest = { showLanguageDialog = false },
            title = { Text(strings.languageDialogTitle, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppLanguage.values().forEach { lang ->
                        val isSelected = currentLang == lang
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else Color.Transparent)
                                .clickable {
                                    AppPreferencesManager.setLanguage(context, lang)
                                    showLanguageDialog = false
                                }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(lang.flag, fontSize = 20.sp)
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    lang.displayName,
                                    fontSize = 16.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                            RadioButton(
                                selected = isSelected,
                                onClick = {
                                    AppPreferencesManager.setLanguage(context, lang)
                                    showLanguageDialog = false
                                }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLanguageDialog = false }) { Text(strings.cancelBtn) }
            }
        )
    }

    // =========================================================================
    // ДИАЛОГ ВЫБОРА ТАЙМ-АУТА АВТОБЛОКИРОВКИ
    // =========================================================================
    if (showTimeoutDialog) {
        val timeouts = listOf(
            0 to strings.timeoutInstant,
            60 to strings.timeout1Min,
            300 to strings.timeout5Min,
            900 to strings.timeout15Min
        )
        AlertDialog(
            onDismissRequest = { showTimeoutDialog = false },
            title = { Text(strings.timeoutDialogTitle, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    timeouts.forEach { (seconds, label) ->
                        val isSelected = biometricTimeoutSec == seconds
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else Color.Transparent)
                                .clickable {
                                    AppPreferencesManager.setBiometricTimeoutSeconds(context, seconds)
                                    showTimeoutDialog = false
                                }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(label, fontSize = 15.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                            RadioButton(
                                selected = isSelected,
                                onClick = {
                                    AppPreferencesManager.setBiometricTimeoutSeconds(context, seconds)
                                    showTimeoutDialog = false
                                }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showTimeoutDialog = false }) { Text(strings.cancelBtn) }
            }
        )
    }

    // =========================================================================
    // ДИАЛОГ ДОБАВЛЕНИЯ РЕГУЛЯРНОГО ПЛАТЕЖА (С ПЛАВНЫМ СКРОЛЛОМ ДНЕЙ БЕЗ СЪЕЗЖАНИЯ)
    // =========================================================================
    if (showAddRecurringDialog) {
        AlertDialog(
            onDismissRequest = { showAddRecurringDialog = false },
            title = { Text(strings.newRecurringTitle, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        FilterChip(
                            selected = !recurringIsIncome,
                            onClick = { recurringIsIncome = false },
                            label = { Text(strings.expense) }
                        )
                        FilterChip(
                            selected = recurringIsIncome,
                            onClick = { recurringIsIncome = true },
                            label = { Text(strings.income) }
                        )
                    }

                    OutlinedTextField(
                        value = recurringTitle,
                        onValueChange = { recurringTitle = it },
                        label = { Text("Название (Spotify, Аренда...)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = recurringAmount,
                        onValueChange = { recurringAmount = it.filter { ch -> ch.isDigit() } },
                        label = { Text("${strings.amountLabel} (${currentCurrency.symbol})") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        visualTransformation = AmountVisualTransformation()
                    )

                    Text(strings.intervalDaysPrompt, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

                    // Плавный скролл пресетов дней через LazyRow (больше никогда не съедет по горизонтали!)
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item {
                            FilterChip(
                                selected = recurringDaysInput == "30",
                                onClick = { recurringDaysInput = "30" },
                                label = { Text("30 дн.") }
                            )
                        }
                        item {
                            FilterChip(
                                selected = recurringDaysInput == "14",
                                onClick = { recurringDaysInput = "14" },
                                label = { Text("14 дн.") }
                            )
                        }
                        item {
                            FilterChip(
                                selected = recurringDaysInput == "7",
                                onClick = { recurringDaysInput = "7" },
                                label = { Text("7 дн.") }
                            )
                        }
                        item {
                            FilterChip(
                                selected = recurringDaysInput == "1",
                                onClick = { recurringDaysInput = "1" },
                                label = { Text("1 день") }
                            )
                        }
                    }

                    // ПРОИЗВОЛЬНЫЙ ВВОД ДНЕЙ
                    OutlinedTextField(
                        value = recurringDaysInput,
                        onValueChange = { recurringDaysInput = it.filter { ch -> ch.isDigit() } },
                        label = { Text(strings.customDaysLabel) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    val amount = recurringAmount.toIntOrNull() ?: 0
                    val days = recurringDaysInput.toIntOrNull() ?: 30
                    if (amount > 0 && recurringTitle.isNotBlank() && days >= 1) {
                        onAddRecurring(recurringTitle, amount, recurringIsIncome, days, currentCurrency.code)
                        showAddRecurringDialog = false
                        recurringTitle = ""
                        recurringAmount = ""
                        recurringDaysInput = "30"
                    }
                }) { Text(strings.addBtn) }
            },
            dismissButton = {
                TextButton(onClick = { showAddRecurringDialog = false }) { Text(strings.cancelBtn) }
            }
        )
    }

    // =========================================================================
    // ДИАЛОГ ПОДТВЕРЖДЕНИЯ ОЧИСТКИ ВСЕХ ДАННЫХ
    // =========================================================================
    if (showClearDataConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearDataConfirmDialog = false },
            title = { Text(strings.clearDataConfirmTitle, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error) },
            text = { Text(strings.clearDataConfirmMessage) },
            confirmButton = {
                Button(
                    onClick = {
                        onClearAllData()
                        showClearDataConfirmDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(strings.clearDataConfirmBtn)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDataConfirmDialog = false }) { Text(strings.cancelBtn) }
            }
        )
    }
}
