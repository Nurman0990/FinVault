package com.example.finvault.util

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class AppLanguage(val code: String, val displayName: String, val flag: String) {
    SYSTEM("", "Системный", "🌐"),
    RUSSIAN("ru", "Русский", "🇷🇺"),
    KAZAKH("kk", "Қазақша", "🇰🇿"),
    ENGLISH("en", "English", "🇬🇧")
}

enum class AppCurrency(val code: String, val symbol: String, val displayName: String, val flag: String) {
    KZT("KZT", "₸", "Казахстанский тенге", "🇰🇿"),
    USD("USD", "$", "Доллар США", "🇺🇸"),
    EUR("EUR", "€", "Евро", "🇪🇺"),
    RUB("RUB", "₽", "Российский рубль", "🇷🇺"),
    TRY("TRY", "₺", "Турецкая лира", "🇹🇷"),
    AED("AED", "AED", "Дирхам ОАЭ", "🇦🇪"),
    GBP("GBP", "£", "Британский фунт", "🇬🇧"),
    CNY("CNY", "¥", "Китайский юань", "🇨🇳")
}

object AppPreferencesManager {

    private const val PREFS_NAME = "finvault_settings_prefs"
    private const val KEY_THEME = "key_theme_mode"
    private const val KEY_LANG = "key_app_language"
    private const val KEY_CURRENCY = "key_app_currency"
    private const val KEY_BIOMETRIC_TIMEOUT = "key_biometric_timeout_sec"
    private const val KEY_BIOMETRIC_ENABLED = "key_biometric_enabled"
    private const val KEY_AUTO_BACKUP_ENABLED = "key_auto_backup_enabled"
    private const val KEY_LAST_BACKUP_TIME = "key_last_backup_time"
    private const val KEY_SHOW_BALANCE_ON_LAUNCH = "key_show_balance_on_launch"

    private val _themeMode = MutableStateFlow(ThemeMode.SYSTEM)
    val themeMode = _themeMode.asStateFlow()

    private val _language = MutableStateFlow(AppLanguage.SYSTEM)
    val language = _language.asStateFlow()

    private val _currency = MutableStateFlow(AppCurrency.KZT)
    val currency = _currency.asStateFlow()

    private val _biometricTimeoutSeconds = MutableStateFlow(60) // 1 минута по умолчанию
    val biometricTimeoutSeconds = _biometricTimeoutSeconds.asStateFlow()

    private val _biometricEnabled = MutableStateFlow(true)
    val biometricEnabled = _biometricEnabled.asStateFlow()

    private val _autoBackupEnabled = MutableStateFlow(true)
    val autoBackupEnabled = _autoBackupEnabled.asStateFlow()

    private val _lastBackupTimestamp = MutableStateFlow(0L)
    val lastBackupTimestamp = _lastBackupTimestamp.asStateFlow()

    private val _showBalanceOnLaunch = MutableStateFlow(false) // глазик закрыт по умолчанию для безопасности, можно включить
    val showBalanceOnLaunch = _showBalanceOnLaunch.asStateFlow()

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        val themeOrdinal = prefs.getInt(KEY_THEME, ThemeMode.SYSTEM.ordinal)
        _themeMode.value = ThemeMode.values().getOrElse(themeOrdinal) { ThemeMode.SYSTEM }

        val langCode = prefs.getString(KEY_LANG, "") ?: ""
        _language.value = AppLanguage.values().firstOrNull { it.code == langCode } ?: AppLanguage.SYSTEM

        val currCode = prefs.getString(KEY_CURRENCY, "KZT") ?: "KZT"
        _currency.value = AppCurrency.values().firstOrNull { it.code == currCode } ?: AppCurrency.KZT

        _biometricTimeoutSeconds.value = prefs.getInt(KEY_BIOMETRIC_TIMEOUT, 60)
        _biometricEnabled.value = prefs.getBoolean(KEY_BIOMETRIC_ENABLED, true)
        _autoBackupEnabled.value = prefs.getBoolean(KEY_AUTO_BACKUP_ENABLED, true)
        _lastBackupTimestamp.value = prefs.getLong(KEY_LAST_BACKUP_TIME, 0L)
        _showBalanceOnLaunch.value = prefs.getBoolean(KEY_SHOW_BALANCE_ON_LAUNCH, false)
    }

    fun setThemeMode(context: Context, mode: ThemeMode) {
        _themeMode.value = mode
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_THEME, mode.ordinal)
            .apply()
    }

    fun setLanguage(context: Context, appLang: AppLanguage) {
        _language.value = appLang
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LANG, appLang.code)
            .apply()

        val localeList = if (appLang.code.isEmpty()) {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(appLang.code)
        }
        AppCompatDelegate.setApplicationLocales(localeList)
    }

    fun setCurrency(context: Context, appCurrency: AppCurrency) {
        _currency.value = appCurrency
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_CURRENCY, appCurrency.code)
            .apply()
    }

    fun setBiometricTimeoutSeconds(context: Context, seconds: Int) {
        _biometricTimeoutSeconds.value = seconds
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_BIOMETRIC_TIMEOUT, seconds)
            .apply()
    }

    fun setBiometricEnabled(context: Context, enabled: Boolean) {
        _biometricEnabled.value = enabled
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_BIOMETRIC_ENABLED, enabled)
            .apply()
    }

    fun setAutoBackupEnabled(context: Context, enabled: Boolean) {
        _autoBackupEnabled.value = enabled
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_AUTO_BACKUP_ENABLED, enabled)
            .apply()
    }

    fun setLastBackupTimestamp(context: Context, timestamp: Long) {
        _lastBackupTimestamp.value = timestamp
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_LAST_BACKUP_TIME, timestamp)
            .apply()
    }

    fun setShowBalanceOnLaunch(context: Context, show: Boolean) {
        _showBalanceOnLaunch.value = show
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_SHOW_BALANCE_ON_LAUNCH, show)
            .apply()
    }
}
