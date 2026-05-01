package com.playtorrio.tv.i18n

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import java.util.Locale

object AppLocaleManager {
    const val SYSTEM = "system"
    const val ENGLISH = "en"
    const val ITALIAN = "it"

    private const val PREFS_NAME = "playtorrio_i18n"
    private const val KEY_APP_LANGUAGE = "app_language"

    fun getAppLanguage(context: Context): String {
        val stored = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_APP_LANGUAGE, SYSTEM)
            ?: SYSTEM
        return normalize(stored)
    }

    fun setAppLanguage(activity: Activity, language: String) {
        val normalized = normalize(language)
        val prefs = activity.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if ((prefs.getString(KEY_APP_LANGUAGE, SYSTEM) ?: SYSTEM) == normalized) return
        prefs.edit().putString(KEY_APP_LANGUAGE, normalized).apply()
        activity.recreate()
    }

    fun wrap(context: Context): Context {
        val appLanguage = getAppLanguage(context)
        if (appLanguage == SYSTEM) return context

        val locale = Locale.forLanguageTag(appLanguage)
        Locale.setDefault(locale)

        val configuration = Configuration(context.resources.configuration)
        configuration.setLocale(locale)
        configuration.setLayoutDirection(locale)
        return context.createConfigurationContext(configuration)
    }

    private fun normalize(language: String): String = when (language) {
        ENGLISH -> ENGLISH
        ITALIAN -> ITALIAN
        else -> SYSTEM
    }
}
