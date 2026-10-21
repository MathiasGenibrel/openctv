package com.openctv.browser.core.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter

/**
 * Marque visuelle d'une page affichée malgré un risque, partagée par la barre d'adresse, l'avertissement et
 * Réglages › Exceptions de sécurité : un seul endroit décide de l'icône et de la couleur d'accent.
 * - [Http] : connexion non chiffrée, `no_encryption` en `tertiary` ;
 * - [Certificate] : certificat non vérifié (plus grave), `gpp_bad` en `error`.
 *
 * [icon] crée un `Painter` à chaque lecture : ne pas le stocker pour plusieurs [androidx.tv.material3.Icon]
 * (un `Painter` vectoriel partagé entre deux tailles dessine un glyphe tronqué).
 */
enum class InsecurityMark(val color: Color) {
    Http(OpenCtvColors.Tertiary),
    Certificate(OpenCtvColors.Error),
    ;

    val icon: Painter
        @Composable get() = when (this) {
            Http -> OpenCtvIcons.NoEncryption
            Certificate -> OpenCtvIcons.GppBad
        }
}
