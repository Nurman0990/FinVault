package com.example.finvault

import android.content.Context
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
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
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.finvault.RateEntity
import com.example.finvault.repository.RatesRepository
import com.example.finvault.ui.AnalyticsScreen
import com.example.finvault.ui.CurrencySelectionDialog
import com.example.finvault.ui.RatesScreen
import com.example.finvault.ui.SettingsScreen
import com.example.finvault.ui.theme.FinExpense
import com.example.finvault.ui.theme.FinIncome
import com.example.finvault.ui.theme.FinVaultTheme
import com.example.finvault.util.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

// =============================================================================
// ВЬЮМОДЕЛЬ (FinVaultViewModel)
// =============================================================================

@dagger.hilt.android.lifecycle.HiltViewModel
class FinVaultViewModel @javax.inject.Inject constructor(
    private val transactionDao: TransactionDao,
    private val ratesRepository: RatesRepository,
    private val recurringPaymentDao: RecurringPaymentDao,
    @dagger.hilt.android.qualifiers.ApplicationContext context: Context
) : ViewModel() {

    private val prefs = context.getSharedPreferences("finvault_prefs", Context.MODE_PRIVATE)

    val transactions = transactionDao.getAllTransactions()
    val rates = ratesRepository.allRates
    val recurringPayments = recurringPaymentDao.getAllRecurringPayments()

    private val _isRefreshingRates = MutableStateFlow(false)
    val isRefreshingRates = _isRefreshingRates.asStateFlow()

    private val _monthlyBudget = MutableStateFlow(prefs.getInt("monthly_budget", 0))
    val monthlyBudget = _monthlyBudget.asStateFlow()

    init {
        refreshRates()
    }

    fun addTransaction(amount: Double, isIncome: Boolean, title: String, currencyCode: String = "KZT") {
        viewModelScope.launch {
            transactionDao.insertTransaction(
                TransactionEntity(
                    amount = amount,
                    isIncome = isIncome,
                    title = title.ifBlank { if (isIncome) "Пополнение" else "Трата" },
                    timestamp = System.currentTimeMillis(),
                    currencyCode = currencyCode
                )
            )
        }
    }

    fun refreshRates() {
        viewModelScope.launch {
            _isRefreshingRates.value = true
            ratesRepository.refreshRates()
            _isRefreshingRates.value = false
        }
    }

    fun setMonthlyBudget(budget: Int) {
        _monthlyBudget.value = budget
        prefs.edit().putInt("monthly_budget", budget).apply()
    }

    fun addRecurringPayment(title: String, amount: Int, isIncome: Boolean, intervalDays: Int, currencyCode: String = "KZT") {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val nextDue = now + TimeUnit.DAYS.toMillis(intervalDays.toLong())
            recurringPaymentDao.insertRecurringPayment(
                RecurringPaymentEntity(
                    title = title,
                    amount = amount,
                    isIncome = isIncome,
                    intervalDays = intervalDays,
                    isActive = true,
                    nextDueTimestamp = nextDue,
                    lastProcessedTimestamp = now,
                    currencyCode = currencyCode
                )
            )
            // Создаем сразу первую транзакцию
            transactionDao.insertTransaction(
                TransactionEntity(
                    amount = amount.toDouble(),
                    isIncome = isIncome,
                    title = "$title (Автоплатеж)",
                    timestamp = now,
                    currencyCode = currencyCode
                )
            )
        }
    }

    fun deleteRecurringPayment(payment: RecurringPaymentEntity) {
        viewModelScope.launch {
            recurringPaymentDao.deleteRecurringPayment(payment)
        }
    }

    fun clearAllTransactions() {
        viewModelScope.launch {
            transactionDao.clearAllTransactions()
        }
    }
}

// =============================================================================
// ГЛАВНАЯ АКТИВНОСТЬ (MainActivity)
// =============================================================================

@dagger.hilt.android.AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private lateinit var lifecycleObserver: DefaultLifecycleObserver

    /**
     * isAuthenticated хранится в companion object — переживает пересоздание Activity
     * (которое происходит при смене языка через AppCompatDelegate.setApplicationLocales).
     * Это предотвращает повторный запрос биометрии после смены языка.
     */
    companion object {
        val isAuthenticated = mutableStateOf(false)
        var backgroundTimestamp: Long = 0L
    }

    private var biometricPrompt: BiometricPrompt? = null
    private var promptInfo: BiometricPrompt.PromptInfo? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Разрешаем делать скриншоты экрана
        window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)

        AppPreferencesManager.init(applicationContext)

        // Если биометрия выключена — сразу открываем доступ
        if (!AppPreferencesManager.biometricEnabled.value) {
            isAuthenticated.value = true
        }

        setupBiometrics()

        // Планировщики фоновых задач WorkManager
        WorkManagerScheduler.scheduleDailyRatesSync(applicationContext)
        WorkManagerScheduler.scheduleRecurringPaymentsCheck(applicationContext)
        WorkManagerScheduler.scheduleDailyAutoBackup(applicationContext)

        // Автоблокировка с настраиваемым тайм-аутом
        lifecycleObserver = object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) {
                backgroundTimestamp = System.currentTimeMillis()
            }

            override fun onStart(owner: LifecycleOwner) {
                if (!AppPreferencesManager.biometricEnabled.value) {
                    isAuthenticated.value = true
                    return
                }

                if (backgroundTimestamp > 0L) {
                    val elapsed = System.currentTimeMillis() - backgroundTimestamp
                    val timeoutMs = AppPreferencesManager.biometricTimeoutSeconds.value * 1000L
                    if (elapsed >= timeoutMs) {
                        isAuthenticated.value = false
                    }
                }
            }
        }
        ProcessLifecycleOwner.get().lifecycle.addObserver(lifecycleObserver)

        setContent {
            val themeMode by AppPreferencesManager.themeMode.collectAsState()
            val isDark = when (themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }

            FinVaultTheme(darkTheme = isDark, dynamicColor = false) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val viewModel: FinVaultViewModel = androidx.hilt.navigation.compose.hiltViewModel()
                    val authenticated by isAuthenticated

                    if (authenticated) {
                        FinVaultMainContainer(viewModel)
                    } else {
                        AuthenticationScreen(
                            onRequestAuth = { showBiometricPrompt() }
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Запрашиваем биометрию только если пользователь ещё НЕ аутентифицирован.
        // Благодаря companion object, isAuthenticated НЕ сбрасывается при пересоздании
        // Activity (смена языка), поэтому повторного запроса не будет.
        if (AppPreferencesManager.biometricEnabled.value && !isAuthenticated.value) {
            showBiometricPrompt()
        }
    }

    private fun setupBiometrics() {
        val strings = LocalizedStrings.get(AppPreferencesManager.language.value)
        val executor = ContextCompat.getMainExecutor(this)
        biometricPrompt = BiometricPrompt(
            this,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    isAuthenticated.value = true
                    backgroundTimestamp = 0L
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                }
            }
        )

        promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(strings.biometricPromptTitle)
            .setSubtitle(strings.biometricPromptSubtitle)
            .setAllowedAuthenticators(BIOMETRIC_STRONG or DEVICE_CREDENTIAL)
            .build()
    }

    private fun showBiometricPrompt() {
        val prompt = biometricPrompt
        val info = promptInfo
        if (prompt != null && info != null && !isAuthenticated.value) {
            try {
                prompt.authenticate(info)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        ProcessLifecycleOwner.get().lifecycle.removeObserver(lifecycleObserver)
    }
}

// =============================================================================
// НАВИГАЦИЯ И ВЕРХНИЙ КОНТЕЙНЕР
// =============================================================================

enum class AppTab(val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    TRANSACTIONS(Icons.Filled.AccountBalanceWallet),
    RATES(Icons.AutoMirrored.Filled.TrendingUp),
    ANALYTICS(Icons.Filled.PieChart),
    SETTINGS(Icons.Filled.Settings)
}

@Composable
fun FinVaultMainContainer(viewModel: FinVaultViewModel) {
    val context = LocalContext.current
    val strings = currentAppStrings()
    var currentTab by remember { mutableStateOf(AppTab.TRANSACTIONS) }

    val transactions by viewModel.transactions.collectAsState(initial = emptyList())
    val rates by viewModel.rates.collectAsState(initial = emptyList())
    val isRefreshingRates by viewModel.isRefreshingRates.collectAsState()
    val monthlyBudget by viewModel.monthlyBudget.collectAsState()
    val recurringPayments by viewModel.recurringPayments.collectAsState(initial = emptyList())

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp
            ) {
                AppTab.values().forEach { tab ->
                    val title = when (tab) {
                        AppTab.TRANSACTIONS -> strings.tabTransactions
                        AppTab.RATES -> strings.tabRates
                        AppTab.ANALYTICS -> strings.tabAnalytics
                        AppTab.SETTINGS -> strings.tabSettings
                    }
                    NavigationBarItem(
                        selected = currentTab == tab,
                        onClick = { currentTab = tab },
                        icon = { Icon(tab.icon, contentDescription = title) },
                        label = { Text(title, fontSize = 12.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    )
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (currentTab) {
                AppTab.TRANSACTIONS -> TransactionsScreen(viewModel, transactions, rates)
                AppTab.RATES -> RatesScreen(
                    rates = rates,
                    isRefreshing = isRefreshingRates,
                    onRefresh = { viewModel.refreshRates() }
                )
                AppTab.ANALYTICS -> AnalyticsScreen(
                    transactions = transactions,
                    rates = rates,
                    monthlyBudget = monthlyBudget,
                    onSaveBudget = { viewModel.setMonthlyBudget(it) }
                )
                AppTab.SETTINGS -> SettingsScreen(
                    recurringPayments = recurringPayments,
                    onAddRecurring = { title, amt, isInc, days, currCode ->
                        viewModel.addRecurringPayment(title, amt, isInc, days, currCode)
                    },
                    onDeleteRecurring = { viewModel.deleteRecurringPayment(it) },
                    onBackupRestored = { viewModel.refreshRates() },
                    onClearAllData = { viewModel.clearAllTransactions() },
                    onExportCsv = {
                        val res = CsvExporter.exportTransactionsToCsv(context, transactions)
                        res.onSuccess { file -> CsvExporter.shareCsvFile(context, file) }
                    }
                )
            }
        }
    }
}

// =============================================================================
// ЭКРАН ТРАНЗАКЦИЙ С КРАСИВЫМИ ПЛАШКАМИ И ВЫБОРОМ ВАЛЮТЫ
// =============================================================================

enum class DateFilterType { ALL, TODAY, WEEK, MONTH }

@Composable
fun TransactionsScreen(
    viewModel: FinVaultViewModel,
    transactions: List<TransactionEntity>,
    rates: List<RateEntity>
) {
    val strings = currentAppStrings()
    val appCurrency by AppPreferencesManager.currency.collectAsState()
    val showBalanceOnLaunch by AppPreferencesManager.showBalanceOnLaunch.collectAsState()
    val currSym = appCurrency.symbol

    var showDialog by remember { mutableStateOf(false) }
    var amountText by remember { mutableStateOf("") }
    var categoryText by remember { mutableStateOf("") }
    var isIncomeSelected by remember { mutableStateOf(false) }
    var selectedCurrencyCode by remember { mutableStateOf(appCurrency.code) }
    var showCurrencyPickerDialog by remember { mutableStateOf(false) }

    // Глазик открыт или закрыт в соответствии с настройкой
    var isBalanceHidden by remember { mutableStateOf(!showBalanceOnLaunch) }

    // Поиск и фильтрация
    var searchQuery by remember { mutableStateOf("") }
    var selectedDateFilter by remember { mutableStateOf(DateFilterType.ALL) }
    var selectedTypeFilter by remember { mutableStateOf<Boolean?>(null) } // null = все, true = доход, false = расход

    val now = System.currentTimeMillis()
    val filteredTransactions = transactions.filter { tx ->
        val matchesSearch = tx.title.contains(searchQuery, ignoreCase = true) ||
                tx.amount.toString().contains(searchQuery)

        val matchesType = when (selectedTypeFilter) {
            null -> true
            true -> tx.isIncome
            false -> !tx.isIncome
        }

        val matchesDate = when (selectedDateFilter) {
            DateFilterType.ALL -> true
            DateFilterType.TODAY -> (now - tx.timestamp) <= TimeUnit.DAYS.toMillis(1)
            DateFilterType.WEEK -> (now - tx.timestamp) <= TimeUnit.DAYS.toMillis(7)
            DateFilterType.MONTH -> (now - tx.timestamp) <= TimeUnit.DAYS.toMillis(30)
        }

        matchesSearch && matchesType && matchesDate
    }

    // Мультивалютный общий баланс: переводим каждую операцию в выбранную основную валюту
    val displayBalance: Double = transactions.sumOf { tx ->
        val converted = CurrencyConverter.convert(tx.amount, tx.currencyCode, appCurrency.code, rates)
        if (tx.isIncome) converted else -converted
    }

    val defaultExpenseCategories = strings.defaultExpenseCategories
    val defaultIncomeCategories = strings.defaultIncomeCategories

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                strings.appTitle,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(12.dp))

            // КАРТОЧКА ОБЩЕГО БАЛАНСА
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
            ) {
                Column(
                    modifier = Modifier
                        .padding(20.dp)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            strings.totalBalance,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                            fontWeight = FontWeight.Medium
                        )
                        IconButton(onClick = { isBalanceHidden = !isBalanceHidden }) {
                            Icon(
                                imageVector = if (isBalanceHidden)
                                    Icons.Filled.VisibilityOff
                                else
                                    Icons.Filled.Visibility,
                                contentDescription = "Скрыть/Показать баланс",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                            )
                        }
                    }

                    val balanceText = if (isBalanceHidden) {
                        "*** $currSym"
                    } else {
                        CurrencyConverter.formatAmount(displayBalance, appCurrency.code)
                    }

                    Text(
                        text = balanceText,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ПОИСКОВАЯ СТРОКА
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text(strings.searchTransactionsPlaceholder) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = "Поиск") },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(10.dp))

            // КРАСИВАЯ СТРОКА ФИЛЬТРОВ (БЕЗ НЕУМЕСТНЫХ БЕЛЫХ ПРЯМОУГОЛЬНИКОВ)
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    CustomFilterPill(
                        text = strings.filterAll,
                        selected = selectedDateFilter == DateFilterType.ALL,
                        onClick = { selectedDateFilter = DateFilterType.ALL }
                    )
                }
                item {
                    CustomFilterPill(
                        text = strings.filterToday,
                        selected = selectedDateFilter == DateFilterType.TODAY,
                        onClick = { selectedDateFilter = DateFilterType.TODAY }
                    )
                }
                item {
                    CustomFilterPill(
                        text = strings.filterWeek,
                        selected = selectedDateFilter == DateFilterType.WEEK,
                        onClick = { selectedDateFilter = DateFilterType.WEEK }
                    )
                }
                item {
                    CustomFilterPill(
                        text = strings.filterMonth,
                        selected = selectedDateFilter == DateFilterType.MONTH,
                        onClick = { selectedDateFilter = DateFilterType.MONTH }
                    )
                }
                item {
                    CustomFilterPill(
                        text = strings.filterOnlyExpenses,
                        selected = selectedTypeFilter == false,
                        onClick = { selectedTypeFilter = if (selectedTypeFilter == false) null else false },
                        accentColor = FinExpense
                    )
                }
                item {
                    CustomFilterPill(
                        text = strings.filterOnlyIncomes,
                        selected = selectedTypeFilter == true,
                        onClick = { selectedTypeFilter = if (selectedTypeFilter == true) null else true },
                        accentColor = FinIncome
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (filteredTransactions.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(bottom = 60.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (transactions.isEmpty()) strings.emptyTransactionsPrompt else strings.emptyFilteredPrompt,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 95.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredTransactions, key = { it.id }) { transaction ->
                        TransactionItem(transaction, isBalanceHidden, appCurrency.code, rates)
                    }
                }
            }
        }

        // КНОПКА ДОБАВЛЕНИЯ ОПЕРАЦИИ (КРИСТАЛЬНО ЧЁТКАЯ)
        FloatingActionButton(
            onClick = {
                categoryText = if (isIncomeSelected) defaultIncomeCategories.first() else defaultExpenseCategories.first()
                selectedCurrencyCode = appCurrency.code
                showDialog = true
            },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 8.dp, pressedElevation = 12.dp),
            shape = CircleShape,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 20.dp)
        ) {
            Icon(
                Icons.Filled.Add,
                contentDescription = strings.addBtn,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(28.dp)
            )
        }

        // ДИАЛОГ ДОБАВЛЕНИЯ ОПЕРАЦИИ С ВЫБОРОМ ВАЛЮТЫ
        if (showDialog) {
            val popularCurrencies = remember(selectedCurrencyCode) {
                val list = mutableListOf("KZT", "USD", "EUR", "RUB")
                if (!list.contains(selectedCurrencyCode)) {
                    list.add(selectedCurrencyCode)
                }
                list
            }

            AlertDialog(
                onDismissRequest = {
                    showDialog = false
                    amountText = ""
                    categoryText = ""
                },
                title = { Text(strings.newTransactionTitle, fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        // Элегантный переключатель Расход / Доход
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                                .padding(4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (!isIncomeSelected) FinExpense.copy(alpha = 0.2f) else Color.Transparent)
                                    .clickable {
                                        isIncomeSelected = false
                                        categoryText = defaultExpenseCategories.first()
                                    }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    strings.expense,
                                    fontWeight = if (!isIncomeSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (!isIncomeSelected) FinExpense else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isIncomeSelected) FinIncome.copy(alpha = 0.2f) else Color.Transparent)
                                    .clickable {
                                        isIncomeSelected = true
                                        categoryText = defaultIncomeCategories.first()
                                    }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    strings.income,
                                    fontWeight = if (isIncomeSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isIncomeSelected) FinIncome else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // ВЫБОР ВАЛЮТЫ ТРАНЗАКЦИИ
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                strings.currencyLabel,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Medium
                            )
                            TextButton(
                                onClick = { showCurrencyPickerDialog = true },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(strings.otherCurrency, fontSize = 12.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        // Быстрые плашки выбора валюты
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(popularCurrencies) { code ->
                                val isSelected = selectedCurrencyCode == code
                                val sym = CurrencyConverter.getSymbol(code)
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                                    border = BorderStroke(1.dp, if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                                    modifier = Modifier.clickable { selectedCurrencyCode = code }
                                ) {
                                    Text(
                                        text = "$code ($sym)",
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Поле ввода суммы с выбранным символом валюты
                        val curSymbol = CurrencyConverter.getSymbol(selectedCurrencyCode)
                        OutlinedTextField(
                            value = amountText,
                            onValueChange = { newValue ->
                                val filtered = newValue.replace(',', '.')
                                if (filtered.count { it == '.' } <= 1 && filtered.all { it.isDigit() || it == '.' }) {
                                    amountText = filtered
                                }
                            },
                            label = { Text("${strings.amountLabel} ($curSymbol)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth(),
                            visualTransformation = AmountVisualTransformation()
                        )

                        Spacer(modifier = Modifier.height(12.dp))
                        Text(strings.categoryLabel, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(6.dp))

                        val currentCategories = if (isIncomeSelected) defaultIncomeCategories else defaultExpenseCategories
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(currentCategories) { cat ->
                                val isCatSelected = categoryText == cat
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (isCatSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                                    border = BorderStroke(1.dp, if (isCatSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                                    modifier = Modifier.clickable { categoryText = cat }
                                ) {
                                    Text(
                                        text = cat,
                                        fontSize = 12.sp,
                                        fontWeight = if (isCatSelected) FontWeight.Bold else FontWeight.Normal,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = categoryText,
                            onValueChange = { categoryText = it },
                            label = { Text(strings.customCategoryPlaceholder) },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val amount = amountText.toDoubleOrNull() ?: 0.0
                            if (amount > 0.0) {
                                viewModel.addTransaction(amount, isIncomeSelected, categoryText, selectedCurrencyCode)
                            }
                            showDialog = false
                            amountText = ""
                            categoryText = ""
                            isIncomeSelected = false
                        }
                    ) { Text(strings.addBtn) }
                },
                dismissButton = {
                    TextButton(onClick = {
                        showDialog = false
                        amountText = ""
                        categoryText = ""
                    }) { Text(strings.cancelBtn) }
                }
            )
        }

        // Диалог полного списка валют (если нажали "Ещё...")
        if (showCurrencyPickerDialog) {
            CurrencySelectionDialog(
                rates = rates,
                selectedCode = selectedCurrencyCode,
                onSelect = {
                    selectedCurrencyCode = it
                    showCurrencyPickerDialog = false
                },
                onDismiss = { showCurrencyPickerDialog = false }
            )
        }
    }
}

/**
 * Стильная скругленная плашка фильтра (без белых квадратов на сером)
 */
@Composable
fun CustomFilterPill(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    accentColor: Color? = null
) {
    val activeColor = accentColor ?: MaterialTheme.colorScheme.primary
    val backgroundColor = if (selected) {
        activeColor.copy(alpha = 0.16f)
    } else {
        MaterialTheme.colorScheme.surface
    }
    val borderColor = if (selected) {
        activeColor
    } else {
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
    }
    val textColor = if (selected) {
        activeColor
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = backgroundColor,
        border = BorderStroke(1.dp, borderColor),
        modifier = Modifier.clickable { onClick() }
    ) {
        Text(
            text = text,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = textColor,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
        )
    }
}

// =============================================================================
// ЭКРАН АУТЕНТИФИКАЦИИ
// =============================================================================

@Composable
fun AuthenticationScreen(onRequestAuth: () -> Unit) {
    val strings = currentAppStrings()

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Filled.Lock,
            contentDescription = strings.appLockedTitle,
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(strings.appLockedTitle, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            strings.appLockedSubtitle,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = { onRequestAuth() },
            shape = RoundedCornerShape(16.dp)
        ) {
            Icon(Icons.Filled.Fingerprint, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(strings.unlockBtn)
        }
    }
}

// =============================================================================
// ИКОНКИ КАТЕГОРИЙ ТРАНЗАКЦИЙ
// =============================================================================

/**
 * Возвращает подходящую иконку Material Icons на основе названия категории/транзакции.
 * Поддерживает русский, казахский и английский тексты.
 */
fun categoryIcon(title: String, isIncome: Boolean): androidx.compose.ui.graphics.vector.ImageVector {
    val t = title.lowercase()
    return when {
        // Расходы
        t.contains("продукт") || t.contains("азық") || t.contains("groceri") || t.contains("еда") || t.contains("супермаркет") ->
            Icons.Filled.ShoppingCart
        t.contains("транспорт") || t.contains("көлік") || t.contains("transport") || t.contains("метро") || t.contains("такси") || t.contains("автобус") ->
            Icons.Filled.DirectionsBus
        t.contains("кафе") || t.contains("дәмхана") || t.contains("cafe") || t.contains("ресторан") || t.contains("restaurant") || t.contains("еда") ->
            Icons.Filled.Restaurant
        t.contains("покупк") || t.contains("сатып") || t.contains("shopping") || t.contains("магазин") || t.contains("shop") ->
            Icons.Filled.ShoppingBag
        t.contains("жильё") || t.contains("аренда") || t.contains("тұрғын") || t.contains("housing") || t.contains("rent") || t.contains("квартир") ->
            Icons.Filled.Home
        t.contains("здоровь") || t.contains("медицин") || t.contains("денсаулық") || t.contains("health") || t.contains("аптека") || t.contains("врач") ->
            Icons.Filled.LocalHospital
        t.contains("развлечен") || t.contains("ойын") || t.contains("entertainment") || t.contains("кино") || t.contains("игр") || t.contains("спорт") ->
            Icons.Filled.SportsEsports
        t.contains("связь") || t.contains("байланыс") || t.contains("services") || t.contains("телефон") || t.contains("интернет") ->
            Icons.Filled.PhoneAndroid
        t.contains("подписк") || t.contains("жазылым") || t.contains("subscription") || t.contains("spotify") || t.contains("netflix") ->
            Icons.Filled.Subscriptions
        t.contains("кофе") || t.contains("coffee") ->
            Icons.Filled.LocalCafe
        t.contains("одежд") || t.contains("clothes") ->
            Icons.Filled.Checkroom
        // Доходы
        t.contains("зарплат") || t.contains("жалақы") || t.contains("salary") ->
            Icons.Filled.AccountBalance
        t.contains("фриланс") || t.contains("freelance") || t.contains("жұмыс") || t.contains("work") ->
            Icons.Filled.Work
        t.contains("перевод") || t.contains("аударым") || t.contains("transfer") ->
            Icons.Filled.SwapHoriz
        t.contains("инвестиц") || t.contains("investm") || t.contains("дивиденд") ->
            Icons.AutoMirrored.Filled.TrendingUp
        t.contains("подарок") || t.contains("сыйлық") || t.contains("gift") ->
            Icons.Filled.CardGiftcard
        t.contains("кэшбэк") || t.contains("cashback") ->
            Icons.Filled.Percent
        t.contains("автоплатеж") || t.contains("автотөлем") || t.contains("auto") ->
            Icons.Filled.Autorenew
        // Fallback: зелёная стрелка вниз для дохода, красная стрелка вверх для расхода
        else -> if (isIncome) Icons.Filled.ArrowDownward else Icons.Filled.ArrowUpward
    }
}

// =============================================================================
// ЭЛЕМЕНТ СПИСКА ТРАНЗАКЦИЙ (КРАСИВАЯ ГАРМОНИЧНАЯ КАРТОЧКА С ИКОНКОЙ КАТЕГОРИИ)
// =============================================================================

@Composable
fun TransactionItem(
    transaction: TransactionEntity,
    isHidden: Boolean,
    mainCurrencyCode: String,
    rates: List<RateEntity>
) {
    val color = if (transaction.isIncome) FinIncome else FinExpense
    val sign = if (transaction.isIncome) "+" else "-"

    val formattedOriginal = if (isHidden) {
        "***"
    } else {
        CurrencyConverter.formatAmount(transaction.amount, transaction.currencyCode)
    }

    // Если валюта транзакции отличается от выбранной основной — показываем приблизительный эквивалент
    val showConverted = !isHidden && !transaction.currencyCode.equals(mainCurrencyCode, ignoreCase = true)
    val convertedAmount = if (showConverted) {
        CurrencyConverter.convert(transaction.amount, transaction.currencyCode, mainCurrencyCode, rates)
    } else 0.0

    val sdf = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
    val dateStr = sdf.format(Date(transaction.timestamp))

    // Иконка, соответствующая категории транзакции
    val icon = categoryIcon(transaction.title, transaction.isIncome)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.weight(1f)
            ) {
                // Круглый значок с иконкой категории
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(color.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = color,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    val strings = currentAppStrings()
                    val localizedTitle = CategoryLocalizer.localize(transaction.title, strings)
                    Text(
                        text = localizedTitle,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = dateStr,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "$sign$formattedOriginal",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = color
                )
                if (showConverted) {
                    Text(
                        text = "≈ $sign${CurrencyConverter.formatAmount(convertedAmount, mainCurrencyCode)}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}