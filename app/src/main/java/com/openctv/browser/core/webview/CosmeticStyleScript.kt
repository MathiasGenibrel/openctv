package com.openctv.browser.core.webview

/**
 * Sélecteurs à masquer sur une page : [generic] valent pour tous les sites (règles `##sélecteur`), [specific] pour
 * l'hôte de la page (règles `domaine##sélecteur`). Fournis déjà compilés, jamais lus dans la page.
 */
class CosmeticSelection(val generic: List<String>, val specific: List<String>) {
    val isEmpty: Boolean get() = generic.isEmpty() && specific.isEmpty()

    companion object {
        val EMPTY = CosmeticSelection(emptyList(), emptyList())
    }
}

/** Source des règles cosmétiques de la page principale. Rien ici ne doit lire la page ni l'utilisateur. */
fun interface CosmeticSelectionSource {
    /** Sélection pour [pageUrl] (page principale http/https) ; [CosmeticSelection.EMPTY] si rien à masquer. Pas d'I/O. */
    fun selectionFor(pageUrl: String): CosmeticSelection

    companion object {
        val None = CosmeticSelectionSource { CosmeticSelection.EMPTY }
    }
}

/**
 * Construit le script qui masque les bandeaux de cookies : **seconde exception** à l'interdiction d'`evaluateJavascript`
 * (la première est [MediaCommand]), voir `conventions.md`, section WebView.
 *
 * Garanties :
 * - le script est un **modèle constant** ([Template]) ; seule varie la liste de sélecteurs, insérée comme **tableau
 *   JSON de chaînes** : jamais concaténée au code. Chaque sélecteur a passé [CosmeticSelector.isSafe] (revérifié ici,
 *   même si la source l'a déjà fait) et est échappé en littéral JS ;
 * - il n'ajoute qu'un élément `<style>` et n'y insère que des règles `sélecteur { display: none !important; }` via
 *   `CSSStyleSheet.insertRule` (une seule règle par appel : un sélecteur ne peut pas « sortir » de sa règle ; non
 *   bloqué par une CSP `style-src` stricte, contrairement à du texte injecté dans le `<style>`) ;
 * - aucune donnée de la page n'est lue, aucun résultat n'est exploité, toute erreur est ignorée en silence ;
 * - aucune interface n'est exposée à la page (pas d'`addJavascriptInterface`) ;
 * - un tableau de sélecteurs d'au plus [MaxTotalChars] caractères par page (les règles du site d'abord), donc un script
 *   d'environ 40 Ko au plus : il est analysé, et ses règles compilées, dans chaque page, sur le SoC modeste de la TV.
 *
 * Pourquoi `evaluateJavascript` et pas `addDocumentStartJavaScript` : les règles dépendent de l'hôte de la page.
 * Un script de début de document serait soit enregistré par hôte (asynchrone, sur le thread principal, donc en
 * course avec la navigation, avec une liste d'origines à tenir à jour), soit contiendrait toute la liste et la
 * choisirait lui-même dans la page. Ici le script est préparé hors du thread principal pour l'hôte exact, puis
 * évalué une fois par page, dès son premier rendu.
 *
 * Les règles génériques sont les mêmes pour toutes les pages : leur encodage JSON est mis en cache (liste identique par
 * référence), les sélecteurs refusés sont écartés une seule fois ; par page, seules les règles du site sont encodées.
 */
class CosmeticStyleScript {

    /** Sélecteurs [accepted] (validés) en éléments de tableau JSON ; [source] : liste d'origine, pour le cache par référence. */
    private class EncodedList(val source: List<String>, val accepted: List<String>) {
        val jsonItems: String = jsonItems(accepted)
        val chars: Int = accepted.sumOf(::encodedLengthOf)
    }

    @Volatile
    private var cachedGeneric: EncodedList? = null

    /** `null` s'il n'y a rien à masquer. Peut être appelé hors du thread principal. */
    fun build(selection: CosmeticSelection): String? {
        if (selection.isEmpty) return null
        val specific = EncodedList(selection.specific, safeWithin(selection.specific, MaxTotalChars))
        val generic = genericEncoded(selection.generic)
        val remaining = MaxTotalChars - specific.chars
        val genericItems = if (generic.chars <= remaining) generic.jsonItems else {
            jsonItems(safeWithin(generic.accepted, remaining))
        }
        val items = listOf(specific.jsonItems, genericItems).filter { it.isNotEmpty() }
        if (items.isEmpty()) return null
        return Template.replace(Placeholder, items.joinToString(separator = ",", prefix = "[", postfix = "]"))
    }

    private fun genericEncoded(selectors: List<String>): EncodedList {
        val cached = cachedGeneric
        if (cached != null && cached.source === selectors) return cached
        return EncodedList(selectors, safeWithin(selectors, Int.MAX_VALUE)).also { cachedGeneric = it }
    }

    /** Les sélecteurs sûrs, dans l'ordre, tant que leur encodage ([encodedLengthOf]) tient dans [budget] caractères. */
    private fun safeWithin(selectors: List<String>, budget: Int): List<String> {
        val kept = ArrayList<String>()
        var chars = 0
        for (selector in selectors) {
            if (!CosmeticSelector.isSafe(selector)) continue
            val length = encodedLengthOf(selector)
            if (chars + length > budget) break
            kept += selector
            chars += length
        }
        return kept
    }

    companion object {
        /**
         * Taille maximale du tableau JSON de sélecteurs d'une page, en caractères ([encodedLengthOf]) ; le script fait
         * ce plafond plus le modèle (~450 caractères). ~2 000 sélecteurs : assez pour les règles d'un site et les
         * règles génériques retenues par le bloqueur, peu au regard des ~320 Ko qu'aurait la liste générique entière.
         */
        const val MaxTotalChars = 40_000

        /** Place de [selector] dans le tableau JSON du script : guillemets, virgule et échappements compris. */
        fun encodedLengthOf(selector: String): Int = selector.length + 3 + selector.count { it == '"' || it == '\\' }

        private const val Placeholder = "__SELECTORS__"

        /** Le seul code exécuté : insère les règles par groupes de 50 (un groupe refusé est rejoué règle par règle). */
        private val Template: String =
            "(function(){try{var a=__SELECTORS__;" +
                "var e=document.createElement('style');" +
                "(document.head||document.documentElement).appendChild(e);" +
                "var s=e.sheet;var h='{display:none!important}';" +
                "for(var i=0;i<a.length;i+=50){var g=a.slice(i,i+50);" +
                "try{s.insertRule(g.join(',')+h,s.cssRules.length)}" +
                "catch(x){for(var j=0;j<g.length;j++){try{s.insertRule(g[j]+h,s.cssRules.length)}catch(y){}}}}" +
                "}catch(z){}})()"

        /** Éléments d'un tableau JSON de chaînes, sans les crochets : `"a","b"`. */
        private fun jsonItems(values: List<String>): String {
            val builder = StringBuilder()
            values.forEachIndexed { index, value ->
                if (index > 0) builder.append(',')
                appendJsString(builder, value)
            }
            return builder.toString()
        }

        /** Littéral JS entre guillemets doubles ; les sélecteurs validés n'ont que `"` à échapper, le reste est défensif. */
        private fun appendJsString(into: StringBuilder, value: String) {
            into.append('"')
            for (c in value) {
                when {
                    c == '"' -> into.append("\\\"")
                    c == '\\' -> into.append("\\\\")
                    c == '\u2028' || c == '\u2029' || c < ' ' -> into.append("\\u").append(c.code.toString(16).padStart(4, '0'))
                    else -> into.append(c)
                }
            }
            into.append('"')
        }
    }
}
