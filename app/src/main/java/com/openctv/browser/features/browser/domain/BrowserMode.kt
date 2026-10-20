package com.openctv.browser.features.browser.domain

/**
 * Où va le D-pad : dans la page web ([Page]) ou dans la barre d'outils ([Interface]).
 * Le mode curseur (#8) affine le mode [Page] ; il n'a pas besoin d'un mode de plus ici.
 */
enum class BrowserMode { Page, Interface }
