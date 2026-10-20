package com.openctv.browser.features.browser.domain

/** Phase d'une touche de la télécommande : appui initial, répétition d'une touche maintenue, relâchement. */
enum class KeyPhase { Down, Repeat, Up }

/**
 * Touche de la télécommande vue par le domaine, indépendamment de Compose ([code] = identité de la touche,
 * [isBack] = touche Retour, qui garde toujours son rôle).
 */
data class RemoteKey(val code: Long, val phase: KeyPhase, val isBack: Boolean = false)

/**
 * La touche qui réaffiche l'interface masquée ne fait que ça, **jusqu'à son relâchement** : son appui, ses
 * répétitions (flèche ou OK gardé enfoncé) et son relâchement sont avalés. Sans cela, une flèche maintenue
 * déplacerait le pointeur et un OK cliquerait sous lui (ou sur la barre focalisée) juste après le réaffichage.
 * Les autres touches ne sont pas concernées. Appelé sur le thread principal (aiguillage des touches).
 */
class RevealKeyGuard {
    private var revealKeyCode: Long? = null

    /** `true` = la touche est avalée : rien n'est transmis au curseur, à la page ni à la barre. */
    fun swallows(key: RemoteKey, isHudHidden: Boolean): Boolean {
        val isRevealKey = key.code == revealKeyCode
        val isNewPress = key.phase == KeyPhase.Down
        if (isRevealKey && !isNewPress) {
            if (key.phase == KeyPhase.Up) revealKeyCode = null
            return true
        }
        // Nouvel appui de la même touche : son relâchement s'est perdu (perte de focus…), on ne l'avale plus.
        if (isRevealKey) revealKeyCode = null
        // Seul un appui initial réaffiche en étant avalé : une répétition ou un relâchement sans appui connu
        // poursuit un geste déjà transmis (le curseur doit recevoir son relâchement pour ne pas rester « enfoncé »).
        val reveals = isHudHidden && isNewPress && !key.isBack
        if (reveals) revealKeyCode = key.code
        return reveals
    }
}
