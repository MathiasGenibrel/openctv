package com.openctv.browser.core.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.IconButton
import androidx.tv.material3.IconButtonDefaults
import androidx.tv.material3.Text

// États désactivés M3 : conteneur 12 %, contenu 38 %.
private const val DisabledContainerAlpha = 0.12f
private const val DisabledContentAlpha = 0.38f

/**
 * `Filled` (action principale), `Tonal`, `Text` (discret, sans fond), `Outline` (contour, action secondaire),
 * `OutlineError` (contour et texte `error` : action risquée) et `TextError` (texte `error` sans contour : confirmation destructive).
 */
enum class OpenCtvButtonStyle { Filled, Tonal, Text, Outline, OutlineError, TextError }

private val OpenCtvButtonStyle.container: Color
    get() = when (this) {
        OpenCtvButtonStyle.Filled -> OpenCtvColors.Primary
        OpenCtvButtonStyle.Tonal -> OpenCtvColors.SecondaryContainer
        OpenCtvButtonStyle.Text, OpenCtvButtonStyle.Outline, OpenCtvButtonStyle.OutlineError, OpenCtvButtonStyle.TextError ->
            Color.Transparent
    }

private val OpenCtvButtonStyle.onContainer: Color
    get() = when (this) {
        OpenCtvButtonStyle.Filled -> OpenCtvColors.OnPrimary
        OpenCtvButtonStyle.Tonal -> OpenCtvColors.OnSecondaryContainer
        OpenCtvButtonStyle.Text, OpenCtvButtonStyle.Outline -> OpenCtvColors.Primary
        OpenCtvButtonStyle.OutlineError, OpenCtvButtonStyle.TextError -> OpenCtvColors.Error
    }

/** Sans fond au repos, un bouton `Text` / `Outline` prend un fond de surface au focus pour que l'état soit évident. */
private val OpenCtvButtonStyle.focusedContainer: Color
    get() = if (container == Color.Transparent) OpenCtvColors.SurfaceContainerHighest else container

private val OpenCtvButtonStyle.restingBorder: Border
    get() = when (this) {
        OpenCtvButtonStyle.Outline -> Border(BorderStroke(1.dp, OpenCtvColors.Outline), shape = CircleShape)
        OpenCtvButtonStyle.OutlineError -> Border(BorderStroke(1.dp, OpenCtvColors.Error), shape = CircleShape)
        else -> Border.None
    }

/** Bouton pilule d'OpenCTV : `Filled` (action principale, `primary`) ou `Tonal` (`secondaryContainer`), avec anneau de focus. [enabled] faux : grisé et non focalisable. */
@Composable
fun OpenCtvButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: OpenCtvButtonStyle = OpenCtvButtonStyle.Filled,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    Button(
        onClick = onClick,
        // Désactivé : grisé et non focalisable (jamais `canFocus = true`, cf. conventions). Le bouton qui perd le
        // focus en se désactivant est géré par l'écran.
        modifier = modifier.focusProperties { if (!enabled) canFocus = false },
        enabled = enabled,
        shape = ButtonDefaults.shape(CircleShape),
        border = ButtonDefaults.border(border = style.restingBorder, focusedBorder = focusRingBorder(CircleShape)),
        colors = ButtonDefaults.colors(
            containerColor = style.container,
            contentColor = style.onContainer,
            focusedContainerColor = style.focusedContainer,
            focusedContentColor = style.onContainer,
            disabledContainerColor = style.container.copy(alpha = DisabledContainerAlpha),
            disabledContentColor = style.onContainer.copy(alpha = DisabledContentAlpha),
        ),
        content = content,
    )
}

/**
 * Bouton icône rond d'OpenCTV (40 dp), mêmes styles et anneau de focus que [OpenCtvButton]. `Tonal` par
 * défaut : icônes d'en-tête (Réglages…). Désactivé ([enabled] faux) : grisé et non focalisable. [contentDescription]
 * est obligatoire : pas de libellé visible.
 *
 * [isRingFromFocus] : anneau dessiné d'après le focus réel ([focusRing]) plutôt que par la surface, et sans
 * agrandissement au focus. Réservé au bouton qu'un écran focalise dès sa composition (bouton retour de l'Historique) :
 * la surface tv-material n'en reçoit pas toujours l'événement de focus et ne se dessinerait pas focalisée.
 */
@Composable
fun OpenCtvIconButton(
    icon: Painter,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: OpenCtvButtonStyle = OpenCtvButtonStyle.Tonal,
    enabled: Boolean = true,
    isRingFromFocus: Boolean = false,
) {
    // Anneau lu sur le focus réel du bouton et non sur l'`interactionSource` de la Surface : un bouton qui a le focus dès
    // sa composition (écran ouvert depuis l'accueil) n'en reçoit pas toujours le `FocusInteraction.Focus`. Un seul
    // anneau : celui de la surface est alors retiré, et l'agrandissement au focus aussi ([focusRing] est dessiné hors du
    // calque de la surface : un bouton agrandi de 10 % recouvrirait l'écart de 2 dp).
    val ring = if (isRingFromFocus) Modifier.focusRing(CircleShape) else Modifier
    val scale = if (isRingFromFocus) IconButtonDefaults.scale(focusedScale = 1f) else IconButtonDefaults.scale()
    val border = if (isRingFromFocus) {
        IconButtonDefaults.border()
    } else {
        IconButtonDefaults.border(focusedBorder = focusRingBorder(CircleShape))
    }
    IconButton(
        onClick = onClick,
        // Les surfaces tv-material sont `focusable()` même désactivées : on retire explicitement le focus
        // (jamais `canFocus = true`, cf. conventions). Le bouton qui le perd en cours de focus est géré par l'écran.
        modifier = modifier.then(ring).focusProperties { if (!enabled) canFocus = false },
        enabled = enabled,
        shape = IconButtonDefaults.shape(CircleShape),
        scale = scale,
        border = border,
        colors = IconButtonDefaults.colors(
            containerColor = style.container,
            contentColor = style.onContainer,
            focusedContainerColor = style.container,
            focusedContentColor = style.onContainer,
            disabledContainerColor = style.container.copy(alpha = DisabledContainerAlpha),
            disabledContentColor = style.onContainer.copy(alpha = DisabledContentAlpha),
        ),
    ) {
        Icon(icon, contentDescription = contentDescription)
    }
}

@Preview(device = Devices.TV_1080p)
@Composable
private fun OpenCtvButtonPreview() {
    OpenCtvTheme {
        Row(modifier = Modifier.padding(32.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            OpenCtvButton(onClick = {}) { Text("Réessayer") }
            OpenCtvButton(onClick = {}, style = OpenCtvButtonStyle.Tonal) { Text("Accueil") }
            OpenCtvIconButton(OpenCtvIcons.Settings, contentDescription = "Réglages", onClick = {})
        }
    }
}
