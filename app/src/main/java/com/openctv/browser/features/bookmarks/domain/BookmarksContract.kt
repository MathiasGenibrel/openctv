package com.openctv.browser.features.bookmarks.domain

import com.openctv.browser.core.common.Resource
import kotlinx.coroutines.flow.Flow

/**
 * Favoris de l'utilisateur, stockés uniquement sur l'appareil.
 *
 * Exposé dans `domain/` pour que les autres features (accueil, navigation, réglages) le consomment
 * sans importer `data/`. Les URLs passées en paramètre sont normalisées ici ([BookmarkUrl]) :
 * l'appelant n'a pas à le faire.
 *
 * Les flux sont réactifs : `Loading` une seule fois à l'abonnement, puis un `Success` à chaque
 * modification (ajout, retrait), ou un `Error` terminal si le stockage échoue.
 */
interface BookmarksContract {
    /** Favoris par ordre d'ajout. */
    fun getBookmarks(): Flow<Resource<List<Bookmark>>>

    fun isBookmarked(url: String): Flow<Resource<Boolean>>

    /** Sans effet si l'URL (normalisée) est déjà favorite. */
    suspend fun add(url: String, title: String): Resource<Unit>

    suspend fun remove(url: String): Resource<Unit>
}
