package com.openctv.browser.core.zoom

import com.openctv.browser.core.security.SiteHost

/**
 * Niveau de zoom d'une page. La TV affiche son interface en 1080p avec un pixel ratio de 2 : à [Full], une page
 * voit [pageWidth] = 960 px CSS (`window.innerWidth`). Les niveaux plus petits agrandissent la zone que la page
 * croit avoir : à 50 %, elle voit 1920 px, comme sur un écran de PC.
 *
 * [id] est la clé de persistance : ne jamais la renommer.
 */
enum class PageZoom(val id: String, val percent: Int, val pageWidth: Int) {
    Full("100", 100, 960),
    ThreeQuarters("75", 75, 1280),
    TwoThirds("67", 67, 1440),
    Half("50", 50, 1920);

    /** Échelle d'affichage de la page : `1` à 100 %, `0,5` à 50 %. */
    val factor: Float get() = BASE_PAGE_WIDTH.toFloat() / pageWidth

    companion object {
        /** 960 px CSS : largeur vue par une page à 100 % sur la TV (1920 px / pixel ratio 2). */
        const val BASE_PAGE_WIDTH = 960

        val Default = Full

        /** Niveau d'identifiant [id], ou `null` si inconnu (valeur stockée obsolète). */
        fun fromId(id: String?): PageZoom? = entries.firstOrNull { it.id == id }
    }
}

/** Zoom propre à un site : [host] est l'hôte exact normalisé par [SiteHost]. */
data class SiteZoom(val host: String, val zoom: PageZoom)

/**
 * Niveau à appliquer à la page [url] : celui mémorisé pour son hôte exact, sinon [default]. Une adresse sans hôte
 * lisible (`about:blank`, hôte ambigu) n'a jamais de zoom propre.
 */
fun effectiveZoom(url: String?, sites: List<SiteZoom>, default: PageZoom): PageZoom {
    val host = SiteHost.of(url) ?: return default
    return sites.firstOrNull { it.host == host }?.zoom ?: default
}

/** Forme stockée d'un [SiteZoom] (`hôte|id`) ; une entrée illisible est ignorée. */
object SiteZoomCodec {
    private const val SEPARATOR = '|'

    fun encode(site: SiteZoom): String = "${site.host}$SEPARATOR${site.zoom.id}"

    fun decode(stored: String): SiteZoom? {
        val parts = stored.split(SEPARATOR)
        val host = parts.getOrNull(0)?.takeIf { it.isNotEmpty() }
        val zoom = PageZoom.fromId(parts.getOrNull(1))
        return if (parts.size == 2 && host != null && zoom != null) SiteZoom(host, zoom) else null
    }
}
