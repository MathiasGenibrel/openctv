package com.openctv.browser.core.designsystem

/** Espace sans chasse : autorise un retour à la ligne sans rien afficher. */
private const val ZERO_WIDTH_SPACE = '​'

/**
 * Hôte long qui se coupe **aux points** (« gitlab-dashboard.tools. / internal ») plutôt qu'au milieu d'un libellé : un
 * point de coupure est inséré après chaque point. Pour l'affichage seulement, jamais pour comparer ou mémoriser l'hôte.
 */
fun breakableHost(host: String): String = host.replace(".", ".$ZERO_WIDTH_SPACE")
