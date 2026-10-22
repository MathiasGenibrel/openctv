package com.openctv.browser.core.designsystem

import android.content.res.Configuration
import androidx.annotation.PluralsRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import java.util.Locale

/**
 * Langue des textes de l'app : seules les ressources par défaut (`values/`, en français) sont fournies. À revoir si une
 * traduction est ajoutée (la langue des pluriels devra alors suivre celle des ressources résolues).
 */
private val AppTextLocale: Locale = Locale.FRENCH

/**
 * Texte au pluriel (`plurals`) accordé selon les règles de la **langue des textes**, pas de celle de la TV.
 * `pluralStringResource` applique les règles de la langue de l'appareil à des textes français : TV en anglais,
 * « 0 bloquées » ; en japonais (catégorie unique), « 1 bloquées » ; en russe, « 21 bloquée ». Ici, Android applique les
 * règles du français (0 et 1 au singulier) quelle que soit la langue de la TV. À utiliser pour tout `plurals`.
 */
@Composable
fun appPluralStringResource(@PluralsRes id: Int, count: Int, vararg formatArgs: Any): String {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val textResources = remember(context, configuration) {
        val textConfiguration = Configuration(configuration).apply { setLocale(AppTextLocale) }
        context.createConfigurationContext(textConfiguration).resources
    }
    return textResources.getQuantityString(id, count, *formatArgs)
}
