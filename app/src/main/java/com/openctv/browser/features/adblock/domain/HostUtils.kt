package com.openctv.browser.features.adblock.domain

import java.util.Locale

/** Utilitaires d'hôte en Kotlin pur (pas d'`android.net.Uri` : testable en JVM, sans I/O). */
internal object HostUtils {

    private val twoPartSecondLevels = setOf("co", "com", "org", "net", "gov", "edu", "ac")

    /** Hôte d'une URL http/https, en minuscules ; `null` pour tout autre schéma ou URL invalide. */
    fun hostOf(url: String): String? {
        val schemeEnd = url.indexOf("://")
        if (schemeEnd <= 0) return null
        val scheme = url.substring(0, schemeEnd)
        val isWebScheme = scheme.equals("http", true) || scheme.equals("https", true)
        if (!isWebScheme) return null

        var authorityEnd = url.length
        for (i in schemeEnd + 3 until url.length) {
            val c = url[i]
            if (c == '/' || c == '?' || c == '#') {
                authorityEnd = i
                break
            }
        }
        var authority = url.substring(schemeEnd + 3, authorityEnd)
        authority = authority.substringAfterLast('@')
        if (authority.startsWith("[")) return null // IPv6 littérale : jamais dans une liste de domaines
        authority = authority.substringBefore(':').trimEnd('.')
        return authority.takeIf { it.isNotEmpty() }?.lowercase(Locale.ROOT)
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

    fun isSameOrSubdomainOf(host: String, domain: String): Boolean =
        host == domain || (host.length > domain.length && host.endsWith(domain) &&
            host[host.length - domain.length - 1] == '.')

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
