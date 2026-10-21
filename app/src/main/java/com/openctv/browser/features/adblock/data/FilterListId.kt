package com.openctv.browser.features.adblock.data

/** Listes de filtres téléchargées. [cacheFileName] : `filters.txt` reste celui d'EasyList (caches existants conservés). */
enum class FilterListId(val cacheFileName: String, val url: String) {
    EasyList("filters.txt", "https://easylist.to/easylist/easylist.txt"),

    /** Liste officielle « EasyList Cookie » (easylist.to) : bandeaux de consentement, règles réseau et cosmétiques. */
    EasyListCookie("filters-cookie.txt", "https://secure.fanboy.co.nz/fanboy-cookiemonster.txt"),
}
