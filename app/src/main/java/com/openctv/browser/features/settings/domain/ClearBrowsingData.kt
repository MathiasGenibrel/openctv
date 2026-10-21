package com.openctv.browser.features.settings.domain

import com.openctv.browser.core.common.Resource
import com.openctv.browser.core.webview.WebDataContract
import com.openctv.browser.features.history.domain.HistoryContract
import com.openctv.browser.features.search.domain.RecentSearchesContract

/** Une catégorie de données de navigation, effaçable séparément. */
enum class ClearDataStep { History, RecentSearches, SiteData, Cache }

/** Catégories cochées dans le panneau « Effacer les données » : toutes, tant que l'utilisateur n'en décoche pas. */
data class ClearDataSelection(val steps: Set<ClearDataStep> = ClearDataStep.entries.toSet()) {
    /** Rien de coché : il n'y a rien à effacer, le bouton est inactif. */
    val isEmpty: Boolean get() = steps.isEmpty()

    operator fun contains(step: ClearDataStep): Boolean = step in steps

    fun toggled(step: ClearDataStep): ClearDataSelection =
        copy(steps = if (step in steps) steps - step else steps + step)
}

/** Catégories dont l'effacement a échoué (celles qui ont réussi sont bel et bien effacées). */
data class ClearDataResult(val failed: Set<ClearDataStep>) {
    val isSuccess: Boolean get() = failed.isEmpty()
}

/**
 * Efface les données de navigation choisies. Chaque catégorie est indépendante : l'échec de l'une n'empêche pas les
 * autres, et le résultat dit lesquelles ont échoué.
 *
 * Ne touche jamais aux favoris, aux réglages, aux exceptions de sécurité ni aux zooms par site : aucun de ces contrats
 * n'est connu d'ici. Seule exception voulue : la dernière page mémorisée (« Reprendre la dernière page ») fait partie
 * de l'historique, elle est oubliée avec lui.
 */
class ClearBrowsingData(
    private val history: HistoryContract,
    private val recentSearches: RecentSearchesContract,
    private val settings: SettingsContract,
    private val webData: WebDataContract,
) {
    suspend operator fun invoke(selection: ClearDataSelection): ClearDataResult {
        val failed = ClearDataStep.entries
            .filter { it in selection }
            .filter { step -> run(step) is Resource.Error }
            .toSet()
        return ClearDataResult(failed)
    }

    private suspend fun run(step: ClearDataStep): Resource<Unit> = when (step) {
        ClearDataStep.History -> clearHistory()
        ClearDataStep.RecentSearches -> recentSearches.clear()
        ClearDataStep.SiteData -> webData.clearSiteData()
        ClearDataStep.Cache -> webData.clearCache()
    }

    /** Les deux effacements sont tentés, même si le premier échoue : une URL oubliée sans historique vaut mieux que l'inverse. */
    private suspend fun clearHistory(): Resource<Unit> {
        val entries = history.clear()
        val lastPage = settings.clearLastPage()
        return if (entries is Resource.Error) entries else lastPage
    }
}
