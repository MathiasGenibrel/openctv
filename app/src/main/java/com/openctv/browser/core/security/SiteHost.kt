package com.openctv.browser.core.security

/**
 * Hôte d'une adresse web, normalisé pour désigner un site dans une [SiteException].
 *
 * Choix assumés :
 * - **hôte exact** : minuscules, `.` final retiré, **`www.` conservé** (contrairement à `BookmarkUrl.key`, qui
 *   sert à reconnaître un favori). Une exception de sécurité ne doit jamais s'étendre à un autre nom : faire
 *   confiance à `nas.maison` n'est pas faire confiance à `www.nas.maison` ;
 * - **port et schéma ignorés** : l'exception HTTP vaut pour l'hôte (un attaquant capable de lire un port en clair
 *   lit aussi les autres), le certificat est de toute façon lié à l'empreinte ;
 * - **strict** : seuls les caractères ASCII d'un nom d'hôte, ou d'une adresse IPv6 entre crochets, sont acceptés.
 *   Tout ce qu'on ne sait pas lire sans ambiguïté (`%`, espace, antislash, caractères non ASCII, port non numérique,
 *   texte après `]`…) donne `null` et l'avertissement ne peut alors pas être contourné. L'antislash termine
 *   l'autorité comme `/` (Chromium le traite ainsi) : `http://evil.com\@good.com` désigne `evil.com`, jamais `good.com` ;
 * - **IPv4 canonique seulement** : un hôte qui finit par un nombre est lu par Chromium comme une adresse IPv4, sous
 *   des formes que ce parseur ne recalcule pas (`3232235786`, `0xc0a8010a`, `192.168.010.010` en octal,
 *   `192.168.1`). Seule la forme `a.b.c.d` décimale est acceptée, les autres donnent `null`.
 */
object SiteHost {
    private val AllowedHostChars = Regex("[a-z0-9._-]+")
    private val Ipv6Chars = Regex("\\[[0-9a-f:.]+]")
    private val PortPart = Regex(":[0-9]*")
    private val NumericLabel = Regex("[0-9]+|0x[0-9a-f]*")
    private val CanonicalIpv4 = Regex("((25[0-5]|2[0-4][0-9]|1[0-9]{2}|[1-9]?[0-9])\\.){3}(25[0-5]|2[0-4][0-9]|1[0-9]{2}|[1-9]?[0-9])")
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
        val hostEnd = if (hostAndPort.startsWith("[")) {
            hostAndPort.indexOf(']').takeIf { it >= 0 }?.plus(1) ?: return null
        } else {
            hostAndPort.indexOf(':').takeIf { it >= 0 } ?: hostAndPort.length
        }
        val port = hostAndPort.substring(hostEnd)
        val isPortReadable = port.isEmpty() || PortPart.matches(port)
        val normalized = hostAndPort.substring(0, hostEnd).lowercase().removeSuffix(".")
        val isName = AllowedHostChars.matches(normalized) && !isAmbiguousIpv4(normalized)
        val isReadable = isPortReadable && (isName || Ipv6Chars.matches(normalized))
        return normalized.takeIf { isReadable }
    }

    /** Finit par un nombre (donc lu comme IPv4 par Chromium) sans être une adresse `a.b.c.d` décimale. */
    private fun isAmbiguousIpv4(host: String): Boolean {
        val endsInNumber = NumericLabel.matches(host.substringAfterLast('.'))
        return endsInNumber && !CanonicalIpv4.matches(host)
    }
}
