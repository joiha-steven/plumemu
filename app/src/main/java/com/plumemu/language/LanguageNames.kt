package com.plumemu.language

import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.plumemu.R

/**
 * Settings › Advanced › Language's value for [tag]: "System default" in the screen's language, or the
 * language's own name in itself (its folder's `language_name`), so a person who cannot read the
 * current language still finds theirs.
 */
@Composable
internal fun languageName(tag: String?): String {
    val language = AppLanguages.byTag(tag) ?: return stringResource(R.string.settings_language_system)
    val context = LocalContext.current
    return remember(language, context) { ownName(context, language) }
}

/** [language]'s `language_name`, read through a configuration in that language. */
internal fun ownName(
    context: Context,
    language: AppLanguage,
): String {
    val config = Configuration(context.resources.configuration)
    config.setLocale(language.locale)
    return context.createConfigurationContext(config).getString(R.string.language_name)
}
