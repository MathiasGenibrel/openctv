package com.openctv.browser.features.adblock.data

import java.io.IOException
import java.io.InputStream

/** Source des listes de filtres. Appel bloquant, hors thread principal ; fermer le flux libère la connexion. */
fun interface FilterListFetcher {
    /** @throws IOException si la liste est injoignable ou la réponse invalide. */
    fun fetch(list: FilterListId): InputStream
}
