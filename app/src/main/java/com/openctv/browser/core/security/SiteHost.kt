package com.openctv.browser.core.security

/**
 * Hôte d'une adresse web, normalisé pour désigner un site dans une [SiteException].
 *
 * Choix assumés :
 * - **hôte exact** : minuscules, `.` final retiré, **`www.` conservé** (contrairement à `BookmarkUrl.key`, qui
 *   sert à reconnaître un favori). Une exception de sécurité ne doit jamais s'étendre à un autre nom : faire
 *   confiance à `nas.maison` n'est pas faire confiance à `www.nas.maison` ;
 * - **port et schéma ignorés** : l'exception HTTP vaut pour l'hôte, le certificat est de toute façon lié à
 *   l'empreinte ;
 * - **strict** : seuls les caractères ASCII d'un nom d'hôte, ou d'une adresse IPv6 entre crochets, sont acceptés.
 *   Tout ce qu'on ne sait pas lire sans ambiguïté (`%`, espace, antislash, caractères non ASCII…) donne `null`
 *   et l'avertissement ne peut alors pas être contourné. L'antislash termine l'autorité comme `/` (Chromium
 *   le traite ainsi) : `http://evil.com\@good.com` désigne `evil.com`, jamais `good.com`.
 */
object SiteHost {
    private val AllowedHostChars = Regex("[a-z0-9._-]+")
    private val Ipv6Chars = Regex("\\[[0-9a-f:.]+]")
    private val AuthorityEnd = charArrayOf('/', '\\', '?', '#')

    fun of(url: String?): String? {
        val candidate = url?.trim() ?: return null
        val schemeEnd = candidate.indexOf("://")
        if (schemeEnd < 0) return null
        val authority = candidate.substring(schemeEnd + 3).let { rest ->
            val end = rest.indexOfAny(AuthorityEnd).takeIf { it >= 0 } ?: rest.length
            rest.substring(0, end)
        }
        val hostAndPort = authority.substringAfterLast('@')
        val host = if (hostAndPort.startsWith("[")) {
            hostAndPort.substringBefore(']', missingDelimiterValue = "") + "]"
        } else {
            hostAndPort.substringBefore(':')
        }
        val normalized = host.lowercase().removeSuffix(".")
        val isReadable = AllowedHostChars.matches(normalized) || Ipv6Chars.matches(normalized)
        return normalized.takeIf { isReadable }
    }
}
