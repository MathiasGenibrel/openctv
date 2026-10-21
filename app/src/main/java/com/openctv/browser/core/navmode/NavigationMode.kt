package com.openctv.browser.core.navmode

import com.openctv.browser.core.security.SiteHost

/**
 * Comment le D-pad parcourt une page : avec le curseur virtuel ([Cursor], défaut) ou d'un élément à l'autre comme
 * avec la touche Tab d'un clavier ([Elements]).
 */
enum class NavigationMode {
    Cursor,
    Elements;

    /** L'autre mode : la bascule du menu. */
    val other: NavigationMode get() = if (this == Cursor) Elements else Cursor
}

/**
 * Mode à appliquer à la page [url] : [Elements] si son hôte exact en fait partie ([elementHosts]), sinon [Cursor]. Une
 * adresse sans hôte lisible (`about:blank`, hôte ambigu) reste au curseur.
 */
fun navigationModeOf(url: String?, elementHosts: Set<String>): NavigationMode {
    val host = SiteHost.of(url) ?: return NavigationMode.Cursor
    return if (host in elementHosts) NavigationMode.Elements else NavigationMode.Cursor
}
