package com.openctv.browser.features.adblock.domain

import java.util.Locale

/** Utilitaires d'hôte en Kotlin pur (pas d'`android.net.Uri` : testable en JVM, sans I/O). */
internal object HostUtils {

    private val twoPartSecondLevels = setOf("co", "com", "org", "net", "gov", "edu", "ac")

    /**
     * Hôte d'une URL http/https, en minuscules ; `null` pour tout autre schéma ou URL invalide.
     * Appelé pour chaque requête : parcours par index, une seule chaîne allouée.
     */
    fun hostOf(url: String): String? {
        val schemeEnd = url.indexOf("://")
        val isHttp = schemeEnd == 4 && url.regionMatches(0, "http", 0, 4, ignoreCase = true)
        val isHttps = schemeEnd == 5 && url.regionMatches(0, "https", 0, 5, ignoreCase = true)
        if (!isHttp && !isHttps) return null

        val authorityStart = schemeEnd + 3
        var authorityEnd = url.length
        for (i in authorityStart until url.length) {
            val c = url[i]
            if (c == '/' || c == '?' || c == '#') {
                authorityEnd = i
                break
            }
        }

        val userInfoEnd = url.lastIndexOf('@', authorityEnd - 1)
        val hostStart = if (userInfoEnd >= authorityStart) userInfoEnd + 1 else authorityStart
        val isIpv6Literal = hostStart < authorityEnd && url[hostStart] == '['
        if (isIpv6Literal) return null // jamais dans une liste de domaines

        var hostEnd = hostStart
        while (hostEnd < authorityEnd && url[hostEnd] != ':') hostEnd++
        while (hostEnd > hostStart && url[hostEnd - 1] == '.') hostEnd--
        if (hostEnd == hostStart) return null
        return url.substring(hostStart, hostEnd).lowercase(Locale.ROOT)
    }

    /** Hôte lui-même puis chaque suffixe de domaine : a.b.c -> a.b.c, b.c, c. */
    inline fun forEachSuffix(host: String, action: (String) -> Boolean) {
        var start = 0
        while (start < host.length) {
            val suffix = if (start == 0) host else host.substring(start)
            if (!action(suffix)) return
            val dot = host.indexOf('.', start)
            if (dot < 0) return
            start = dot + 1
        }
    }

    /** `true` si [host] est l'un des [domains] ou l'un de leurs sous-domaines. */
    fun isCoveredBy(host: String, domains: Set<String>): Boolean {
        if (domains.isEmpty()) return false
        var found = false
        forEachSuffix(host) { suffix ->
            found = suffix in domains
            !found
        }
        return found
    }

    /** Approximation sans Public Suffix List : 2 derniers labels, 3 pour `co.uk`, `com.au`… */
    fun baseDomain(host: String): String {
        val labels = host.split('.')
        if (labels.size <= 2) return host
        val last = labels[labels.size - 1]
        val second = labels[labels.size - 2]
        val keep = if (last.length == 2 && second in twoPartSecondLevels) 3 else 2
        return labels.takeLast(keep).joinToString(".")
    }

    fun isThirdParty(requestHost: String, pageHost: String): Boolean =
        baseDomain(requestHost) != baseDomain(pageHost)
}
