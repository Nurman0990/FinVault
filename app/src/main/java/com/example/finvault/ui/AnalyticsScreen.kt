package com.example.finvault.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.finvault.RateEntity
import com.example.finvault.TransactionEntity
import com.example.finvault.util.AmountVisualTransformation
import com.example.finvault.util.AppPreferencesManager
import com.example.finvault.util.CategoryLocalizer
import com.example.finvault.util.CsvExporter
import com.example.finvault.util.CurrencyConverter
import com.example.finvault.util.currentAppStrings
import java.util.Locale

@Composable
fun AnalyticsScreen(
    transactions: List<TransactionEntity>,
    rates: List<RateEntity>,
    monthlyBudget: Int,
    onSaveBudget: (Int) -> Unit
) {
    val context = LocalContext.current
    val strings = currentAppStrings()
    val appCurrency by AppPreferencesManager.currency.collectAsState()
    val currSym = appCurrency.symbol

    var showBudgetDialog by remember { mutableStateOf(false) }
    var budgetInput by remember { mutableStateOf(if (monthlyBudget > 0) monthlyBudget.toString() else "") }

    val expenses = transactions.filter { !it.isIncome }
    val totalExpense = expenses.sumOf {
        CurrencyConverter.convert(it.amount, it.currencyCode, appCurrency.code, rates)
    }

    // Группировка расходов по категориям (в выбранной основной валюте и текущем языке)
    val categoryTotals = expenses
        .groupBy { CategoryLocalizer.localize(it.title, strings).ifEmpty { "Прочее" } }
        .mapValues { (_, txList) ->
            txList.sumOf { CurrencyConverter.convert(it.amount, it.currencyCode, appCurrency.code, rates) }
        }
        .toList()
        .sortedByDescending { it.second }

    // Гармоничная палитра цветов для диаграммы
    val chartColors = listOf(
        Color(0xFF38BDF8),
        Color(0xFF818CF8),
        Color(0xFF34D399),
        Color(0xFFFBBF24),
        Color(0xFFF472B6),
        Color(0xFF60A5FA),
        Color(0xFFA78BFA),
        Color(0xFFFB923C)
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(bottom = 80.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    strings.analyticsTitle,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f, fill = false)
                )

                Spacer(modifier = Modifier.width(8.dp))

                // Компактная фиксированная кнопка экспорта CSV (аккуратно вмещается на всех языках)
                FilledTonalButton(
                    onClick = {
                        val result = CsvExporter.exportTransactionsToCsv(context, transactions)
                        result.onSuccess { file ->
                            CsvExporter.shareCsvFile(context, file)
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.FileDownload,
                        contentDescription = "CSV",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("CSV", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }

        // КАРТОЧКА БЮДЖЕТА / ЛИМИТА
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            strings.monthlyLimitTitle,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        TextButton(onClick = {
                            budgetInput = if (monthlyBudget > 0) monthlyBudget.toString() else ""
                            showBudgetDialog = true
                        }) {
                            Text(if (monthlyBudget > 0) strings.changeLimitBtn else strings.setLimitBtn)
                        }
                    }

                    if (monthlyBudget > 0) {
                        val progress = (totalExpense.toFloat() / monthlyBudget.toFloat()).coerceIn(0f, 1f)
                        val isOverBudget = totalExpense > monthlyBudget
                        val progressColor = if (isOverBudget) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary

                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(10.dp)
                                .clip(RoundedCornerShape(5.dp)),
                            color = progressColor,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "${strings.spentText}: ${CurrencyConverter.formatAmount(totalExpense, appCurrency.code)}",
                                fontSize = 13.sp,
                                color = if (isOverBudget) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = if (isOverBudget) FontWeight.Bold else FontWeight.Normal
                            )
                            Text(
                                "${strings.limitText}: ${CurrencyConverter.formatAmount(monthlyBudget.toDouble(), appCurrency.code)}",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (isOverBudget) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Warning,
                                    contentDescription = "Alert",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    "${strings.limitExceededAlert} ${CurrencyConverter.formatAmount(totalExpense - monthlyBudget, appCurrency.code)}!",
                                    color = MaterialTheme.colorScheme.error,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    } else {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            strings.limitNotSetDesc,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // КРУГОВАЯ ДИАГРАММА РАСХОДОВ
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        strings.expenseStructureTitle,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.align(Alignment.Start)
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    if (totalExpense > 0) {
                        Box(
                            modifier = Modifier.size(200.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Canvas(modifier = Modifier.size(180.dp)) {
                                var startAngle = -90f
                                categoryTotals.forEachIndexed { index, (_, amount) ->
                                    val sweepAngle = (amount.toFloat() / totalExpense.toFloat()) * 360f
                                    val color = chartColors[index % chartColors.size]
                                    drawArc(
                                        color = color,
                                        startAngle = startAngle,
                                        sweepAngle = sweepAngle,
                                        useCenter = false,
                                        style = Stroke(width = 34.dp.toPx())
                                    )
                                    startAngle += sweepAngle
                                }
                            }

                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(strings.totalExpensesLabel, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    CurrencyConverter.formatAmount(totalExpense, appCurrency.code),
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(strings.noExpensesToDisplay, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }

        // СПИСОК КАТЕГОРИЙ С ПРОЦЕНТАМИ
        if (categoryTotals.isNotEmpty()) {
            item {
                Text(
                    strings.categoriesTitle,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            items(categoryTotals.indices.toList()) { index ->
                val (category, amount) = categoryTotals[index]
                val percent = if (totalExpense > 0) (amount * 100) / totalExpense else 0
                val color = chartColors[index % chartColors.size]

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .clip(CircleShape)
                                .background(color)
                        )
                        Text(category, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            CurrencyConverter.formatAmount(amount, appCurrency.code),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "($percent%)",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    if (showBudgetDialog) {
        AlertDialog(
            onDismissRequest = { showBudgetDialog = false },
            title = { Text(strings.saveBudgetDialogTitle) },
            text = {
                OutlinedTextField(
                    value = budgetInput,
                    onValueChange = { newValue ->
                        budgetInput = newValue.filter { ch -> ch.isDigit() }.trimStart('0')
                    },
                    label = { Text("${strings.limitInputLabel} ($currSym)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = AmountVisualTransformation()
                )
            },
            confirmButton = {
                Button(onClick = {
                    val newBudget = budgetInput.toIntOrNull() ?: 0
                    onSaveBudget(newBudget)
                    showBudgetDialog = false
                }) {
                    Text(strings.saveBtn)
                }
            },
            dismissButton = {
                TextButton(onClick = { showBudgetDialog = false }) {
                    Text(strings.cancelBtn)
                }
            }
        )
    }
}
