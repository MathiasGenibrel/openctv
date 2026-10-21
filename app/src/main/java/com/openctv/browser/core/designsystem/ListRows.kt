package com.openctv.browser.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Switch
import androidx.tv.material3.Text

/**
 * Couleurs d'une liste groupée : `Standard` (Réglages : `surfaceContainer`, focus `primaryContainer`) ou
 * `Vibrant` (menu du navigateur : `tertiaryContainer`, focus `tertiary`).
 */
enum class ListGroupStyle { Standard, Vibrant }

/** Style des lignes : celui du [ListGroup] englobant ; `Standard` hors groupe (radios d'un sous-panneau). */
private val LocalListGroupStyle = staticCompositionLocalOf { ListGroupStyle.Standard }

private val ListGroupStyle.container: Color
    get() = if (this == ListGroupStyle.Standard) OpenCtvColors.SurfaceContainer else OpenCtvColors.TertiaryContainer

/** Texte secondaire et chevron ; `Unspecified` = couleur du contenu de la ligne (suit le focus). */
private val ListGroupStyle.secondaryContent: Color
    get() = if (this == ListGroupStyle.Standard) OpenCtvColors.OnSurfaceVariant else Color.Unspecified

private val ListGroupStyle.rowMinHeight: Dp
    get() = if (this == ListGroupStyle.Standard) 64.dp else RadioRowMinHeight

private val ListGroupStyle.rowHorizontalPadding: Dp
    get() = if (this == ListGroupStyle.Standard) 20.dp else RadioRowHorizontalPadding

/**
 * Marge interne d'un groupe : elle absorbe l'agrandissement d'une ligne focalisée ([RowFocusedScale]) et son
 * anneau (5 dp), qui ne doivent pas déborder de la carte.
 */
private val ListGroupPadding = 12.dp
private const val RowFocusedScale = 1.02f
private val RadioRowMinHeight = 56.dp
private val RadioRowHorizontalPadding = 16.dp

@Composable
private fun ListGroupStyle.radioSupportingStyle(): TextStyle =
    if (this == ListGroupStyle.Standard) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium

@Composable
private fun ListGroupStyle.rowColors() = when (this) {
    ListGroupStyle.Standard -> ClickableSurfaceDefaults.colors(
        containerColor = Color.Transparent,
        contentColor = OpenCtvColors.OnSurface,
        focusedContainerColor = OpenCtvColors.PrimaryContainer,
        focusedContentColor = OpenCtvColors.OnPrimaryContainer,
        pressedContainerColor = OpenCtvColors.PrimaryContainer,
        pressedContentColor = OpenCtvColors.OnPrimaryContainer,
    )
    ListGroupStyle.Vibrant -> ClickableSurfaceDefaults.colors(
        containerColor = Color.Transparent,
        contentColor = OpenCtvColors.OnTertiaryContainer,
        focusedContainerColor = OpenCtvColors.Tertiary,
        focusedContentColor = OpenCtvColors.OnTertiary,
        pressedContainerColor = OpenCtvColors.Tertiary,
        pressedContentColor = OpenCtvColors.OnTertiary,
    )
}

/** Groupe de lignes, coins Large ; les lignes qu'il contient prennent son [style]. */
@Composable
fun ListGroup(
    modifier: Modifier = Modifier,
    style: ListGroupStyle = ListGroupStyle.Standard,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalListGroupStyle provides style) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .background(style.container, OpenCtvShapes.Large)
                .padding(ListGroupPadding),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) { content() }
    }
}

/**
 * Ligne focalisable : [icon], [label], [supporting] optionnel (deuxième ligne) et [trailing]
 * (valeur, interrupteur, chevron). [iconTint] colore l'icône de tête (couleur du contenu par défaut). La ligne grandit avec son contenu : rien n'est découpé.
 */
@Composable
fun ListRow(
    icon: Painter,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    iconTint: Color = Color.Unspecified,
    trailing: @Composable () -> Unit = {},
) {
    val style = LocalListGroupStyle.current
    ListRowLayout(
        onClick = onClick,
        modifier = modifier,
        minHeight = style.rowMinHeight,
        horizontalPadding = style.rowHorizontalPadding,
        leading = { Icon(icon, contentDescription = null, tint = iconTint.takeOrElse { LocalContentColor.current }) },
        label = label,
        supporting = supporting,
        supportingStyle = MaterialTheme.typography.bodyMedium,
        trailing = trailing,
    )
}

/** Ligne à interrupteur : [onClick] bascule la valeur, l'interrupteur affiche [isChecked]. */
@Composable
fun ListSwitchRow(
    icon: Painter,
    label: String,
    isChecked: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    supporting: String? = null,
) {
    ListRow(
        icon = icon,
        label = label,
        onClick = onClick,
        modifier = modifier,
        supporting = supporting,
        trailing = { Switch(checked = isChecked, onCheckedChange = null) },
    )
}

/** Ligne qui ouvre un sous-panneau : [value] courante et chevron. */
@Composable
fun ListNavigationRow(
    icon: Painter,
    label: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tint = LocalListGroupStyle.current.secondaryContent
    ListRow(
        icon = icon,
        label = label,
        onClick = onClick,
        modifier = modifier,
        supporting = value,
        trailing = { Icon(OpenCtvIcons.ChevronRight, contentDescription = null, tint = tint.takeOrElse { LocalContentColor.current }) },
    )
}

/** Option exclusive d'un sous-panneau (radio) ; [supporting] précise « Par défaut », « Sans pistage »… */
@Composable
fun ListRadioRow(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    supporting: String? = null,
) {
    val style = LocalListGroupStyle.current
    ListRowLayout(
        onClick = onClick,
        modifier = modifier,
        minHeight = RadioRowMinHeight,
        horizontalPadding = RadioRowHorizontalPadding,
        leading = { RadioMark(isSelected, style) },
        label = label,
        supporting = supporting,
        supportingStyle = style.radioSupportingStyle(),
    )
}

/** Radios d'un sous-panneau, une par option : le focus initial est sur l'option [selected]. */
@Composable
fun <T> ListRadioOptions(
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    supporting: @Composable (T) -> String?,
    onSelect: (T) -> Unit,
) {
    val selectedFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { selectedFocus.requestFocus() }

    options.forEach { option ->
        val isSelected = option == selected
        ListRadioRow(
            label = label(option),
            isSelected = isSelected,
            onClick = { onSelect(option) },
            supporting = supporting(option),
            modifier = if (isSelected) Modifier.focusRequester(selectedFocus) else Modifier,
        )
    }
}

@Composable
private fun ListRowLayout(
    onClick: () -> Unit,
    modifier: Modifier,
    minHeight: Dp,
    horizontalPadding: Dp,
    leading: @Composable () -> Unit,
    label: String,
    supporting: String?,
    supportingStyle: TextStyle,
    trailing: @Composable () -> Unit = {},
) {
    val style = LocalListGroupStyle.current
    val shape = OpenCtvShapes.Medium
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = ClickableSurfaceDefaults.shape(shape = shape, focusedShape = shape),
        border = ClickableSurfaceDefaults.border(focusedBorder = focusRingBorder(shape)),
        colors = style.rowColors(),
        scale = ClickableSurfaceDefaults.scale(focusedScale = RowFocusedScale),
    ) {
        Row(
            modifier = Modifier.heightIn(min = minHeight).padding(horizontal = horizontalPadding, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            leading()
            Column(modifier = Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.titleMedium)
                if (supporting != null) {
                    Text(supporting, style = supportingStyle, color = style.secondaryContent)
                }
            }
            trailing()
        }
    }
}

@Composable
private fun RadioMark(isSelected: Boolean, style: ListGroupStyle) {
    val icon = if (isSelected) OpenCtvIcons.RadioButtonChecked else OpenCtvIcons.RadioButtonUnchecked
    val tint = when {
        style == ListGroupStyle.Vibrant -> LocalContentColor.current
        isSelected -> OpenCtvColors.Primary
        else -> OpenCtvColors.Outline
    }
    Icon(icon, contentDescription = null, tint = tint)
}

@Preview(device = Devices.TV_1080p)
@Composable
private fun ListRowsPreview() {
    OpenCtvTheme {
        Row(modifier = Modifier.padding(32.dp), horizontalArrangement = Arrangement.spacedBy(32.dp)) {
            ListGroupStyle.entries.forEach { style ->
                Column(modifier = Modifier.weight(1f)) {
                    ListGroup(style = style) {
                        ListSwitchRow(OpenCtvIcons.Shield, "Bloqueur de pubs", isChecked = true, onClick = {}, supporting = "Activé")
                        ListRow(OpenCtvIcons.Refresh, "Actualiser la page", onClick = {})
                        ListNavigationRow(OpenCtvIcons.Speed, "Vitesse du curseur", "Normale", onClick = {})
                        ListRadioRow("DuckDuckGo", isSelected = true, onClick = {}, supporting = "Par défaut · Sans pistage")
                    }
                }
            }
        }
    }
}
