package com.openctv.browser.core.remote

import android.view.KeyEvent
import com.openctv.browser.core.webview.MediaCommand

/** Sens d'un défilement d'une page entière. */
enum class ScrollDirection { Up, Down }

/** Ce qu'une touche « confort » de la télécommande demande à l'application (hors croix, OK et Retour). */
sealed interface RemoteAction {
    /** Chaîne + / − : défile d'un écran. */
    data class ScrollPage(val direction: ScrollDirection) : RemoteAction

    /** Touche média : commande sur la vidéo de la page. */
    data class Media(val command: MediaCommand) : RemoteAction

    /** Recherche / assistant : ouvre la saisie d'adresse. */
    data object OpenSearch : RemoteAction

    /** Info : ouvre le menu du navigateur. */
    data object OpenMenu : RemoteAction

    /** Chiffre 1 à 9 : ouvre le favori de rang [position] (1 = premier de l'accueil). */
    data class OpenFavorite(val position: Int) : RemoteAction

    /** Un appui maintenu répète l'action (défilement, saut) ; sinon seul l'appui initial compte. */
    val repeatsWhenHeld: Boolean
        get() = when (this) {
            is ScrollPage -> true
            is Media -> command == MediaCommand.SeekBack || command == MediaCommand.SeekForward
            OpenSearch, OpenMenu, is OpenFavorite -> false
        }
}

/**
 * **Table unique** des touches de la télécommande autres que la croix, OK et Retour : pour ajuster le comportement
 * d'une touche (ou ajouter un code relevé sur la TV avec `getevent`), c'est ici et nulle part ailleurs.
 *
 * Tout `keyCode` absent de la table n'est pas touché : Accueil, Retour, Volume, Muet, Alimentation, Entrée/source,
 * Réglages, Guide, touches d'applications (Netflix, YouTube…) continuent vers le système ou la page. Ne jamais y
 * ajouter une touche système.
 */
object RemoteKeyMap {
    private const val MAX_FAVORITE_POSITION = 9

    private val scrollUp = RemoteAction.ScrollPage(ScrollDirection.Up)
    private val scrollDown = RemoteAction.ScrollPage(ScrollDirection.Down)

    private val digitKeys: Map<Int, RemoteAction> =
        (1..MAX_FAVORITE_POSITION).associate { position -> (KeyEvent.KEYCODE_0 + position) to RemoteAction.OpenFavorite(position) }

    private val actions: Map<Int, RemoteAction> = mapOf(
        KeyEvent.KEYCODE_CHANNEL_UP to scrollUp,
        KeyEvent.KEYCODE_PAGE_UP to scrollUp,
        KeyEvent.KEYCODE_CHANNEL_DOWN to scrollDown,
        KeyEvent.KEYCODE_PAGE_DOWN to scrollDown,
        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE to RemoteAction.Media(MediaCommand.TogglePlayback),
        KeyEvent.KEYCODE_MEDIA_PLAY to RemoteAction.Media(MediaCommand.Play),
        KeyEvent.KEYCODE_MEDIA_PAUSE to RemoteAction.Media(MediaCommand.Pause),
        KeyEvent.KEYCODE_MEDIA_FAST_FORWARD to RemoteAction.Media(MediaCommand.SeekForward),
        KeyEvent.KEYCODE_MEDIA_NEXT to RemoteAction.Media(MediaCommand.SeekForward),
        KeyEvent.KEYCODE_MEDIA_REWIND to RemoteAction.Media(MediaCommand.SeekBack),
        KeyEvent.KEYCODE_MEDIA_PREVIOUS to RemoteAction.Media(MediaCommand.SeekBack),
        KeyEvent.KEYCODE_SEARCH to RemoteAction.OpenSearch,
        KeyEvent.KEYCODE_ASSIST to RemoteAction.OpenSearch,
        KeyEvent.KEYCODE_INFO to RemoteAction.OpenMenu,
    ) + digitKeys

    /** Action de [keyCode] (code `android.view.KeyEvent`), `null` = touche laissée à qui la reçoit. */
    fun actionFor(keyCode: Int): RemoteAction? = actions[keyCode]
}

/** Part de la hauteur visible parcourue par un défilement d'un écran : un peu moins, pour garder un repère de lecture. */
private const val PAGE_SCROLL_FRACTION = 0.9f

/** Pixels de l'écran à parcourir pour défiler d'un écran dans [direction] (négatif = vers le haut). */
fun pageScrollPixels(visibleHeightOnScreen: Int, direction: ScrollDirection): Int {
    val distance = (visibleHeightOnScreen * PAGE_SCROLL_FRACTION).toInt()
    return if (direction == ScrollDirection.Up) -distance else distance
}
