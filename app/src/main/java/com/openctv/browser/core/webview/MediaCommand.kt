package com.openctv.browser.core.webview

/**
 * Commandes de la vidéo de la page (plein écran ou non), envoyées par [PointerAwareWebView.runMediaCommand]. Les sites vidéo mobiles
 * (YouTube) n'ont aucun raccourci clavier : un script ponctuel est le seul moyen qu'a la télécommande de les piloter.
 *
 * **Premier des deux usages autorisés d'`evaluateJavascript`** (le second : [CosmeticStyleScript] ; voir `conventions.md`,
 * section WebView) : un ensemble fermé de scripts **constants**, sans aucune donnée de la page ni de l'utilisateur, dont
 * le résultat est ignoré, déclenchés par les touches média de la télécommande, en plein écran comme hors plein écran.
 * Rien n'est exposé à la page (pas d'`addJavascriptInterface`), rien ne reste injecté.
 *
 * Cible : la vidéo de l'élément plein écran du document ; sans élément plein écran, la première vidéo de la page.
 * Un élément plein écran sans vidéo accessible (lecteur dans une iframe d'un autre site) ne fait rien, plutôt que
 * d'agir sur une autre vidéo de la page. Toute erreur (vidéo absente, lecture refusée) est ignorée en silence.
 */
enum class MediaCommand(internal val script: String) {
    TogglePlayback(
        mediaScript("if(v.paused){var p=v.play();if(p&&p.catch)p.catch(function(){});}else{v.pause();}"),
    ),
    Play(mediaScript("var p=v.play();if(p&&p.catch)p.catch(function(){});")),
    Pause(mediaScript("v.pause();")),
    SeekBack(seekScript(-SEEK_SECONDS)),
    SeekForward(seekScript(SEEK_SECONDS)),
}

/** Pas d'un saut Gauche / Droite. */
private const val SEEK_SECONDS = 10

private fun seekScript(seconds: Int): String =
    mediaScript("v.currentTime=Math.max(0,v.currentTime+($seconds));")

private fun mediaScript(action: String): String =
    "(function(){try{var f=document.fullscreenElement;" +
        "var v=f?(f.tagName==='VIDEO'?f:f.querySelector('video')):document.querySelector('video');" +
        "if(!v)return;$action}catch(e){}})()"
