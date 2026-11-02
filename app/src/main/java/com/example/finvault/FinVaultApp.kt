package com.example.finvault

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Главный Application-класс приложения.
 * Аннотация @HiltAndroidApp инициирует генерацию графа зависимостей Hilt
 * на уровне всего жизненного цикла приложения.
 */
@HiltAndroidApp
class FinVaultApp : Application()
