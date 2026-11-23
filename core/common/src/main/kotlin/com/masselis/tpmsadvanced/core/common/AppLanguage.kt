package com.masselis.tpmsadvanced.core.common

import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.content.res.Configuration
import android.content.res.Resources
import android.view.ContextThemeWrapper
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.Locale

/**
 * The language the app's texts are shown in, chosen on the debug page. [SYSTEM], the default,
 * follows the phone, and is what turning the debug options off goes back to.
 *
 * It's the app's own setting rather than `AppCompatDelegate.setApplicationLocales`: before Android
 * 13 the latter only reaches the activities, while the service's notifications, the alerts and
 * Android Auto also run with no activity at all, after a restart for example.
 */
public enum class AppLanguage(public val tag: String?) {
    SYSTEM(null),
    ENGLISH("en"),
    RUSSIAN("ru");

    public companion object {
        private val preferences by lazy { appContext.getSharedPreferences("LANGUAGE", MODE_PRIVATE) }

        public val current: MutableStateFlow<AppLanguage> by lazy {
            observableStateFlow(
                preferences
                    .getString("LANGUAGE", null)
                    ?.let { name -> entries.firstOrNull { it.name == name } }
                    ?: SYSTEM
            ) { _, newValue ->
                preferences.edit { putString("LANGUAGE", newValue.name) }
                newValue.setAsDefaultLocale()
            }
        }

        /** So that dates and numbers follow the language from the start of the process */
        internal fun applyAtStartup() = current.value.setAsDefaultLocale()
    }

    public val locale: Locale
        get() = tag
            ?.let(Locale::forLanguageTag)
            ?: Resources.getSystem().configuration.locales[0]

    private fun setAsDefaultLocale() = Locale.setDefault(locale)

    internal fun applyTo(context: Context): Context = tag
        ?.let {
            Configuration(context.resources.configuration)
                // Not just `locale`: that would be the configuration's own field
                .apply { setLocale(this@AppLanguage.locale) }
                .let(context::createConfigurationContext)
        }
        ?: context
}

/**
 * To call from an activity's `attachBaseContext`, after super. Only the locale is overridden, the
 * theme's night mode stays AppCompat's.
 */
public fun ContextThemeWrapper.applyAppLanguage(): Unit = AppLanguage
    .current
    .value
    .tag
    ?.let(Locale::forLanguageTag)
    ?.let { locale -> Configuration().apply { setLocale(locale) } }
    ?.let(::applyOverrideConfiguration)
    ?: Unit

/**
 * A text in the chosen [AppLanguage], for what isn't in a composition: notifications, toasts, labels
 * read from a lambda. Don't keep the result in a `val` which outlives a language change.
 */
public fun localized(@StringRes id: Int, vararg args: Any): String = AppLanguage
    .current
    .value
    .applyTo(appContext)
    .getString(id, *args)

/** A quantity's text in the chosen [AppLanguage], see [localized] */
public fun localizedPlural(@PluralsRes id: Int, count: Int, vararg args: Any): String = AppLanguage
    .current
    .value
    .applyTo(appContext)
    .resources
    .getQuantityString(id, count, *args)
