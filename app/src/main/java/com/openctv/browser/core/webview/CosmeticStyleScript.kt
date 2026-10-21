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
 * - au plus [MaxTotalChars] caractères de sélecteurs par page (les règles du site d'abord).
 *
 * Pourquoi `evaluateJavascript` et pas `addDocumentStartJavaScript` : les règles dépendent de l'hôte de la page.
 * Un script de début de document serait soit enregistré par hôte (asynchrone, sur le thread principal, donc en
 * course avec la navigation, avec une liste d'origines à tenir à jour), soit contiendrait toute la liste et la
 * choisirait lui-même dans la page. Ici le script est préparé hors du thread principal pour l'hôte exact, puis
 * évalué une fois par page, dès son premier rendu.
 *
 * Les règles génériques sont les mêmes pour toutes les pages : leur encodage est mis en cache (liste identique par
 * référence), les sélecteurs refusés sont écartés une seule fois.
 */
class CosmeticStyleScript {

    /** Sélecteurs [accepted] (validés) en tableau JSON ; [source] est la liste d'origine, pour le cache par référence. */
    private class EncodedList(val source: List<String>, val accepted: List<String>) {
        val json: String = jsonArray(accepted)
        val chars: Int = accepted.sumOf { it.length }
    }

    @Volatile
    private var cachedGeneric: EncodedList? = null

    /** `null` s'il n'y a rien à masquer. Peut être appelé hors du thread principal. */
    fun build(selection: CosmeticSelection): String? {
        if (selection.isEmpty) return null
        val specific = EncodedList(selection.specific, safeWithin(selection.specific, MaxTotalChars))
        val generic = genericEncoded(selection.generic)
        val remaining = MaxTotalChars - specific.chars
        val fittingGeneric = if (generic.chars <= remaining) generic else {
            EncodedList(generic.source, safeWithin(generic.accepted, remaining))
        }
        val accepted = specific.accepted + fittingGeneric.accepted
        if (accepted.isEmpty()) return null
        return Template.replace(Placeholder, jsonArray(accepted))
    }

    private fun genericEncoded(selectors: List<String>): EncodedList {
        val cached = cachedGeneric
        if (cached != null && cached.source === selectors) return cached
        return EncodedList(selectors, safeWithin(selectors, Int.MAX_VALUE)).also { cachedGeneric = it }
    }

    /** Les sélecteurs sûrs, dans l'ordre, tant que leur total tient dans [budget] caractères. */
    private fun safeWithin(selectors: List<String>, budget: Int): List<String> {
        val kept = ArrayList<String>()
        var chars = 0
        for (selector in selectors) {
            if (!CosmeticSelector.isSafe(selector)) continue
            if (chars + selector.length > budget) break
            kept += selector
            chars += selector.length
        }
        return kept
    }

    companion object {
        const val MaxTotalChars = 400_000
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

        private fun jsonArray(values: List<String>): String {
            val builder = StringBuilder("[")
            values.forEachIndexed { index, value ->
                if (index > 0) builder.append(',')
                appendJsString(builder, value)
            }
            return builder.append(']').toString()
        }

        /** Littéral JS entre guillemets doubles ; les sélecteurs validés n'ont que `"` à échapper, le reste est défensif. */
        private fun appendJsString(into: StringBuilder, value: String) {
            into.append('"')
            for (c in value) {
                when {
                    c == '"' -> into.append("\\\"")
                    c == '\\' -> into.append("\\\\")
                    c == ' ' || c == ' ' || c < ' ' -> into.append("\\u").append(c.code.toString(16).padStart(4, '0'))
                    else -> into.append(c)
                }
            }
            into.append('"')
        }
    }
}
