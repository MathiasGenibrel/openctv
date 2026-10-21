package com.openctv.browser.features.adblock.domain

import com.openctv.browser.core.webview.CosmeticStyleScript

/**
 * Règles génériques (`##sélecteur`) retenues pour l'injection. Elles partent dans **chaque** page, sur le SoC modeste
 * de la TV : la liste « EasyList Cookie » en compte ~15 000 (~300 000 caractères, soit un script de ~320 Ko à analyser
 * et autant de règles CSS à compiler par page), dont l'essentiel est propre à un seul site (`#zpm-cookie`,
 * `#cookie-consent-typ9_wrapper`). On en garde au plus [MaxChars] caractères une fois encodés dans le script
 * ([CosmeticStyleScript.encodedLengthOf] ; ~1 800 sélecteurs, ~32 Ko), choisis **une fois**, à la compilation de la
 * liste, par ordre d'impact :
 * 1. `#id` / `.classe` d'une plateforme de consentement connue ([CmpVendors] : Didomi, OneTrust, Cookiebot…) ;
 * 2. `#id` / `.classe` formés du seul vocabulaire des bandeaux ([BannerWords] : `#cookie-banner`, `.gdprNotice`) : noms
 *    réutilisés d'un site à l'autre ;
 * 3. les autres `#id` / `.classe` ;
 * 4. le reste (attributs, combinateurs, pseudo-classes) : là où un `#id` / `.classe` n'est comparé qu'aux éléments qui
 *    le portent (index du moteur CSS), ces sélecteurs sont évalués sur bien plus d'éléments.
 *
 * À rang égal, le plus court d'abord (le nom le plus générique), puis l'ordre de la liste. Une liste qui tient dans le
 * budget est gardée en entier. Les règles par domaine (`site.com##…`) ne sont pas concernées : elles ne coûtent qu'à
 * leur site et passent en premier dans le script ([CosmeticStyleScript.MaxTotalChars] leur réserve [SiteRulesReserve]).
 */
internal object GenericCosmeticBudget {

    private const val SiteRulesReserve = 8_000
    const val MaxChars = CosmeticStyleScript.MaxTotalChars - SiteRulesReserve

    private const val RankVendor = 0
    private const val RankBannerWords = 1
    private const val RankSimple = 2
    private const val RankComplex = 3

    /** Fragments de noms (en minuscules) des plateformes de consentement les plus répandues. */
    private val CmpVendors = listOf(
        "didomi", "onetrust", "optanon", "ot-sdk", "qc-cmp", "quantcast", "sp_message", "sourcepoint", "cybot",
        "cookiebot", "truste", "trustarc", "usercentrics", "axeptio", "tarteaucitron", "iubenda", "cookieyes", "cky-",
        "osano", "termly", "cmplz", "complianz", "borlabs", "cmpbox", "consentmanager", "klaro", "evidon",
        "cookiefirst", "cookie-law-info", "moove_gdpr", "cc-window", "cc-banner", "cookie-script", "cookieconsent",
    )

    /** Mots qui désignent un bandeau de consentement : un nom de rang 2 contient au moins l'un d'eux. */
    private val ConsentWords = setOf(
        "cookie", "cookies", "consent", "gdpr", "rgpd", "cnil", "lgpd", "dsgvo", "rodo", "privacy",
    )

    /** Mots d'un nom de bandeau générique ; un nom de rang 2 n'est fait que d'eux. */
    private val BannerWords = ConsentWords + setOf(
        "banner", "bar", "notice", "popup", "popin", "modal", "overlay", "wrapper", "wrap", "container", "notification",
        "message", "msg", "alert", "policy", "law", "eu", "box", "info", "warning", "accept", "agree", "layer", "block",
        "footer", "bottom", "window", "dialog", "panel", "root", "content", "holder", "bandeau", "wall", "backdrop",
        "disclaimer", "prompt", "toast", "sticky", "widget", "notify", "main", "outer", "inner", "text", "use", "usage",
        "cookiebar", "cookiesbar", "cookiebanner", "cookiesbanner", "cookienotice", "cookiewall", "cookielaw",
        "cookiepolicy", "cookiealert", "cookiemessage", "cookieinfo", "cookiepopup", "cookiebox", "cookiewarning",
    )

    /** [selectors] tels quels s'ils tiennent dans [MaxChars] ; sinon les plus utiles, dans l'ordre d'impact. */
    fun select(selectors: Collection<String>): List<String> {
        val total = selectors.sumOf(CosmeticStyleScript::encodedLengthOf)
        if (total <= MaxChars) return selectors.toList()
        val ranked = selectors.sortedWith(compareBy<String>(::rankOf).thenBy { it.length })
        val kept = ArrayList<String>()
        var chars = 0
        for (selector in ranked) {
            val length = CosmeticStyleScript.encodedLengthOf(selector)
            if (chars + length > MaxChars) break
            kept += selector
            chars += length
        }
        return kept
    }

    private fun rankOf(selector: String): Int {
        val name = simpleNameOf(selector) ?: return RankComplex
        val lowered = name.lowercase()
        val isVendor = CmpVendors.any { it in lowered }
        if (isVendor) return RankVendor
        val words = wordsOf(name)
        val isOnlyBannerWords = words.all { it in BannerWords }
        val mentionsConsent = words.any { word -> ConsentWords.any { word.startsWith(it) } }
        return if (isOnlyBannerWords && mentionsConsent) RankBannerWords else RankSimple
    }

    /** Nom d'un sélecteur `#id` ou `.classe` seul ; `null` pour tout autre sélecteur. */
    private fun simpleNameOf(selector: String): String? {
        val hasSimplePrefix = selector.length > 1 && (selector[0] == '#' || selector[0] == '.')
        if (!hasSimplePrefix) return null
        val name = selector.substring(1)
        val isIdentifier = name.all { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' || it == '-' || it == '_' }
        return name.takeIf { isIdentifier }
    }

    /** Mots en minuscules d'un nom : séparés par `-`, `_` ou une majuscule (`cookieConsent_bar` → cookie, consent, bar). */
    private fun wordsOf(name: String): List<String> {
        val words = ArrayList<String>()
        val current = StringBuilder()
        for (i in name.indices) {
            val c = name[i]
            val isSeparator = c == '-' || c == '_'
            val startsCamelWord = c.isUpperCase() && i > 0 && !name[i - 1].isUpperCase()
            if ((isSeparator || startsCamelWord) && current.isNotEmpty()) {
                words += current.toString()
                current.clear()
            }
            if (!isSeparator) current.append(c.lowercaseChar())
        }
        if (current.isNotEmpty()) words += current.toString()
        return words
    }
}
