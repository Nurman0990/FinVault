package com.example.finvault.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.CurrencyBitcoin
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.finvault.RateEntity
import com.example.finvault.ui.theme.FinCrypto
import com.example.finvault.ui.theme.FinIncome
import com.example.finvault.util.AmountVisualTransformation
import com.example.finvault.util.currentAppStrings
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class RateFilter { ALL, FIAT, CRYPTO }
enum class RatesTab { LIST, CALCULATOR }

private val rateSymbols = java.text.DecimalFormatSymbols(Locale("ru", "RU")).apply {
    groupingSeparator = ' '
    decimalSeparator = ','
}
private val rateFormat2 = java.text.DecimalFormat("#,##0.00", rateSymbols)
private val rateFormat4 = java.text.DecimalFormat("#,##0.0000", rateSymbols)
private val rateFormat6 = java.text.DecimalFormat("#,##0.000000", rateSymbols)

@Composable
fun RatesScreen(
    rates: List<RateEntity>,
    isRefreshing: Boolean,
    onRefresh: () -> Unit
) {
    var currentSubTab by remember { mutableStateOf(RatesTab.LIST) }
    val strings = currentAppStrings()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // Заголовок и кнопка обновить
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    strings.ratesTitle,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                if (rates.isNotEmpty()) {
                    val lastUpdate = rates.maxOfOrNull { it.lastUpdated } ?: 0L
                    if (lastUpdate > 0) {
                        val sdf = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
                        Text(
                            "${strings.ratesUpdated}: ${sdf.format(Date(lastUpdate))}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            IconButton(
                onClick = onRefresh,
                enabled = !isRefreshing
            ) {
                if (isRefreshing) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                } else {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = strings.refreshTooltip,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Переключатель: Список курсов / Калькулятор-конвертер
        PrimaryTabRow(
            selectedTabIndex = currentSubTab.ordinal,
            containerColor = Color.Transparent
        ) {
            Tab(
                selected = currentSubTab == RatesTab.LIST,
                onClick = { currentSubTab = RatesTab.LIST },
                text = { Text(strings.quotesTab, fontWeight = if (currentSubTab == RatesTab.LIST) FontWeight.Bold else FontWeight.Normal) },
                icon = { Icon(Icons.AutoMirrored.Filled.TrendingUp, contentDescription = null, modifier = Modifier.size(18.dp)) }
            )
            Tab(
                selected = currentSubTab == RatesTab.CALCULATOR,
                onClick = { currentSubTab = RatesTab.CALCULATOR },
                text = { Text(strings.calculatorTab, fontWeight = if (currentSubTab == RatesTab.CALCULATOR) FontWeight.Bold else FontWeight.Normal) },
                icon = { Icon(Icons.Filled.Calculate, contentDescription = null, modifier = Modifier.size(18.dp)) }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        when (currentSubTab) {
            RatesTab.LIST -> RatesListSection(rates = rates)
            RatesTab.CALCULATOR -> CurrencyCalculatorSection(rates = rates)
        }
    }
}

@Composable
fun RatesListSection(rates: List<RateEntity>) {
    val strings = currentAppStrings()
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf(RateFilter.ALL) }

    val filteredRates = rates.filter { rate ->
        val matchesFilter = when (selectedFilter) {
            RateFilter.ALL -> true
            RateFilter.FIAT -> !rate.isCrypto
            RateFilter.CRYPTO -> rate.isCrypto
        }
        val matchesSearch = rate.code.contains(searchQuery, ignoreCase = true) ||
                rate.name.contains(searchQuery, ignoreCase = true)
        matchesFilter && matchesSearch
    }

    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text(strings.searchCurrencyPlaceholder) },
            leadingIcon = {
                Icon(Icons.Filled.Search, contentDescription = "Поиск")
            },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = selectedFilter == RateFilter.ALL,
                onClick = { selectedFilter = RateFilter.ALL },
                label = { Text("${strings.selectAllCurrencies} (${rates.size})") }
            )
            FilterChip(
                selected = selectedFilter == RateFilter.FIAT,
                onClick = { selectedFilter = RateFilter.FIAT },
                label = { Text(strings.filterFiat) }
            )
            FilterChip(
                selected = selectedFilter == RateFilter.CRYPTO,
                onClick = { selectedFilter = RateFilter.CRYPTO },
                label = { Text(strings.filterCrypto) }
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (filteredRates.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 64.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (rates.isEmpty()) strings.loadRatesFirstPrompt else strings.emptyFilteredPrompt,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                items(filteredRates, key = { it.code }) { rate ->
                    RateItemCard(rate)
                }
            }
        }
    }
}

/**
 * Удобный и наглядный калькулятор конвертации валют с большим диалогом выбора
 */
@Composable
fun CurrencyCalculatorSection(rates: List<RateEntity>) {
    val strings = currentAppStrings()

    if (rates.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize().padding(bottom = 80.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(strings.loadRatesFirstPrompt, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    var fromCurrencyCode by remember { mutableStateOf(rates.firstOrNull { it.code == "USD" }?.code ?: rates.first().code) }
    var toCurrencyCode by remember { mutableStateOf("KZT") }
    var inputAmountText by remember { mutableStateOf("100") }

    // Состояния для удобного диалога выбора валюты
    var pickingForFrom by remember { mutableStateOf(false) }
    var pickingForTo by remember { mutableStateOf(false) }

    val fromRate = rates.firstOrNull { it.code == fromCurrencyCode }
    val toRate = rates.firstOrNull { it.code == toCurrencyCode }

    val inputAmount = inputAmountText.toDoubleOrNull() ?: 0.0
    val fromRateInKzt = fromRate?.rateInKzt ?: 1.0
    val toRateInKzt = toRate?.rateInKzt ?: 1.0

    val calculatedResult = if (toRateInKzt > 0) {
        (inputAmount * fromRateInKzt) / toRateInKzt
    } else 0.0

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 80.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        strings.converterTitle,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    // БЛОК «ОТКУДА» (FROM)
                    Text(strings.converterIHave, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Medium)
                    Spacer(modifier = Modifier.height(6.dp))

                    // КРУПНАЯ УДОБНАЯ КАРТОЧКА ВЫБОРА ВАЛЮТЫ (FROM)
                    CurrencySelectorCard(
                        currencyCode = fromCurrencyCode,
                        currencyName = fromRate?.name ?: "",
                        onClick = { pickingForFrom = true }
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = inputAmountText,
                        onValueChange = { newValue ->
                            inputAmountText = newValue.filter { it.isDigit() }.trimStart('0')
                        },
                        label = { Text("Сумма $fromCurrencyCode") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth(),
                        visualTransformation = AmountVisualTransformation()
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // КНОПКА СВАПА (SWAP)
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        FilledTonalIconButton(
                            onClick = {
                                val temp = fromCurrencyCode
                                fromCurrencyCode = toCurrencyCode
                                toCurrencyCode = temp
                            },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                Icons.Filled.SwapVert,
                                contentDescription = "Swap",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // БЛОК «КУДА» (TO)
                    Text(strings.converterIGet, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Medium)
                    Spacer(modifier = Modifier.height(6.dp))

                    // КРУПНАЯ УДОБНАЯ КАРТОЧКА ВЫБОРА ВАЛЮТЫ (TO)
                    CurrencySelectorCard(
                        currencyCode = toCurrencyCode,
                        currencyName = toRate?.name ?: "",
                        onClick = { pickingForTo = true }
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Результат
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            val formattedResult = when {
                                calculatedResult >= 1 -> rateFormat2.format(calculatedResult)
                                calculatedResult > 0 -> rateFormat6.format(calculatedResult)
                                else -> "0"
                            }

                            Text(
                                text = "$formattedResult $toCurrencyCode",
                                fontSize = 30.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            val oneUnitConversion = if (toRateInKzt > 0) fromRateInKzt / toRateInKzt else 0.0
                            val formattedOne = when {
                                oneUnitConversion >= 1 -> rateFormat2.format(oneUnitConversion)
                                else -> rateFormat6.format(oneUnitConversion)
                            }
                            Text(
                                text = "1 $fromCurrencyCode = $formattedOne $toCurrencyCode",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }

    // ДИАЛОГИ ВЫБОРА ВАЛЮТЫ (БОЛЬШОЙ И УДОБНЫЙ СПИСОК С ПОИСКОМ)
    if (pickingForFrom) {
        CurrencySelectionDialog(
            rates = rates,
            selectedCode = fromCurrencyCode,
            onSelect = {
                fromCurrencyCode = it
                pickingForFrom = false
            },
            onDismiss = { pickingForFrom = false }
        )
    }

    if (pickingForTo) {
        CurrencySelectionDialog(
            rates = rates,
            selectedCode = toCurrencyCode,
            onSelect = {
                toCurrencyCode = it
                pickingForTo = false
            },
            onDismiss = { pickingForTo = false }
        )
    }
}

/**
 * Крупная кликабельная карточка выбора валюты с иконкой и стрелочкой
 */
@Composable
fun CurrencySelectorCard(
    currencyCode: String,
    currencyName: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = currencyCode.take(3),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }

                Column {
                    Text(currencyCode, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    if (currencyName.isNotBlank()) {
                        Text(currencyName, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            Icon(
                Icons.Filled.ArrowDropDown,
                contentDescription = "Выбрать",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

/**
 * Большой диалог с поиском и комфортным списком валют
 */
@Composable
fun CurrencySelectionDialog(
    rates: List<RateEntity>,
    selectedCode: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var search by remember { mutableStateOf("") }
    val filtered = rates.filter {
        it.code.contains(search, ignoreCase = true) || it.name.contains(search, ignoreCase = true)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("Выберите валюту", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    placeholder = { Text("Поиск (USD, EUR, BTC...)") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 380.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(filtered, key = { it.code }) { rate ->
                    val isSelected = rate.code == selectedCode
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                else Color.Transparent
                            )
                            .clickable { onSelect(rate.code) }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (rate.isCrypto) FinCrypto.copy(alpha = 0.2f)
                                        else FinIncome.copy(alpha = 0.15f)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (rate.isCrypto) Icons.Filled.CurrencyBitcoin else Icons.Filled.Payments,
                                    contentDescription = null,
                                    tint = if (rate.isCrypto) FinCrypto else FinIncome,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            Column {
                                Text(rate.code, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                Text(rate.name, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }

                        RadioButton(
                            selected = isSelected,
                            onClick = { onSelect(rate.code) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Закрыть") }
        }
    )
}

@Composable
fun RateItemCard(rate: RateEntity) {
    val strings = currentAppStrings()

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(
                            if (rate.isCrypto) FinCrypto.copy(alpha = 0.15f)
                            else FinIncome.copy(alpha = 0.15f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (rate.isCrypto) Icons.Filled.CurrencyBitcoin else Icons.Filled.Payments,
                        contentDescription = null,
                        tint = if (rate.isCrypto) FinCrypto else FinIncome,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Column {
                    Text(
                        text = rate.code,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = rate.name,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                val formattedRate = when {
                    rate.rateInKzt >= 1 -> rateFormat2.format(rate.rateInKzt)
                    else -> rateFormat4.format(rate.rateInKzt)
                }
                Text(
                    text = "$formattedRate ₸",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "${strings.forOneUnit} ${rate.code}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
