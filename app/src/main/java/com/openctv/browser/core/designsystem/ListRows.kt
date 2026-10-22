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
import androidx.compose.ui.text.style.TextOverflow
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

/**
 * Densité d'un groupe : `Comfortable` (Réglages, sous-panneaux), ou `Compact` (menu du navigateur, qui doit tenir en
 * 540 dp sans défiler) : lignes d'une ligne 48 dp, de deux lignes 64 dp, textes sur une seule ligne (ellipse), tailles
 * de texte inchangées. Toutes les dimensions qui en dépendent sont ci-dessous.
 */
enum class ListGroupDensity { Comfortable, Compact }

private val LocalListGroupDensity = staticCompositionLocalOf { ListGroupDensity.Comfortable }

private val ListGroupDensity.isCompact: Boolean
    get() = this == ListGroupDensity.Compact

private val ListGroupDensity.groupPadding: Dp
    get() = if (isCompact) CompactGroupPadding else ListGroupPadding

private val ListGroupDensity.rowSpacing: Dp
    get() = if (isCompact) 2.dp else 4.dp

/** Écart entre l'icône (ou la radio), les textes et l'élément de fin. */
private val ListGroupDensity.rowContentSpacing: Dp
    get() = if (isCompact) 12.dp else 16.dp

/** Textes d'une [ListRow] sur une seule ligne (ellipse) : la hauteur fixée ne doit pas être dépassée. */
private val ListGroupDensity.isRowTextSingleLine: Boolean
    get() = isCompact

private fun ListGroupDensity.rowMinHeight(style: ListGroupStyle, hasSupportingLine: Boolean): Dp = when {
    !isCompact -> style.rowMinHeight
    hasSupportingLine -> CompactTwoLinesRowHeight
    else -> CompactOneLineRowHeight
}

private fun ListGroupDensity.rowHorizontalPadding(style: ListGroupStyle): Dp =
    if (isCompact) CompactRowHorizontalPadding else style.rowHorizontalPadding

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

/**
 * Marge `Compact` : l'anneau d'une ligne focalisée déborde un peu de la carte (≈ 2 dp en haut et en bas, ≈ 4 dp sur
 * les côtés avec l'agrandissement). Il n'est pas rogné tant que le conteneur laisse au moins 2 dp de plus autour du
 * groupe avant tout clip (colonne défilable du menu : 4 dp).
 */
private val CompactGroupPadding = 4.dp
private val CompactOneLineRowHeight = 48.dp
private val CompactTwoLinesRowHeight = 64.dp
private val CompactRowHorizontalPadding = 12.dp
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
    density: ListGroupDensity = ListGroupDensity.Comfortable,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalListGroupStyle provides style, LocalListGroupDensity provides density) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .background(style.container, OpenCtvShapes.Large)
                .padding(density.groupPadding),
            verticalArrangement = Arrangement.spacedBy(density.rowSpacing),
        ) { content() }
    }
}

/**
 * Ligne focalisable : [icon], [label], [supporting] optionnel (deuxième ligne) et [trailing]
 * (valeur, interrupteur, chevron). [iconTint] colore l'icône de tête (couleur du contenu par défaut).
 * La ligne grandit avec son contenu : rien n'est découpé, sauf en densité `Compact` (textes sur une ligne, ellipse).
 */
@Composable
fun ListRow(
    icon: Painter,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    iconTint: Color = Color.Unspecified,
    /** Libellé sur une seule ligne, terminé par une ellipse (nom d'hôte long) ; sinon il passe à la ligne. */
    isLabelSingleLine: Boolean = false,
    trailing: @Composable () -> Unit = {},
) {
    val style = LocalListGroupStyle.current
    val density = LocalListGroupDensity.current
    val hasSupportingLine = supporting != null
    ListRowLayout(
        onClick = onClick,
        modifier = modifier,
        minHeight = density.rowMinHeight(style, hasSupportingLine),
        horizontalPadding = density.rowHorizontalPadding(style),
        leading = { Icon(icon, contentDescription = null, tint = iconTint.takeOrElse { LocalContentColor.current }) },
        label = label,
        isLabelSingleLine = isLabelSingleLine || density.isRowTextSingleLine,
        isSupportingSingleLine = density.isRowTextSingleLine,
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

/** Ligne qui bascule une valeur sur place (OK change la valeur) : [value] courante en texte de fin, sans chevron. */
@Composable
fun ListValueRow(
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
        trailing = { Text(value, style = MaterialTheme.typography.labelLarge, color = tint) },
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
    val isCompact = LocalListGroupDensity.current.isCompact
    val chevron = @Composable {
        Icon(OpenCtvIcons.ChevronRight, contentDescription = null, tint = tint.takeOrElse { LocalContentColor.current })
    }
    ListRow(
        icon = icon,
        label = label,
        onClick = onClick,
        modifier = modifier,
        // Compact : la valeur tient sur la ligne du libellé, devant le chevron.
        supporting = value.takeUnless { isCompact },
        trailing = {
            if (isCompact) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(value, style = MaterialTheme.typography.labelLarge, color = tint)
                    chevron()
                }
            } else {
                chevron()
            }
        },
    )
}

/**
 * Option exclusive d'un sous-panneau (radio) ; [supporting] précise « Par défaut », « Sans pistage »…
 * [trailingText] est un texte de fin de ligne (« Par défaut » d'un niveau de zoom).
 */
@Composable
fun ListRadioRow(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    trailingText: String? = null,
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
        trailing = {
            if (trailingText != null) Text(trailingText, style = MaterialTheme.typography.labelLarge, color = style.secondaryContent)
        },
    )
}

/** Case à cocher d'un sous-panneau : [onClick] bascule [isChecked] ; [supporting] précise la catégorie (compteur, effet). */
@Composable
fun ListCheckRow(
    label: String,
    isChecked: Boolean,
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
        leading = {
            Icon(
                painter = if (isChecked) OpenCtvIcons.CheckBox else OpenCtvIcons.CheckBoxOutlineBlank,
                contentDescription = null,
                tint = if (isChecked) OpenCtvColors.Primary else OpenCtvColors.Outline,
            )
        },
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
    trailingText: @Composable (T) -> String? = { null },
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
            trailingText = trailingText(option),
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
    isLabelSingleLine: Boolean = false,
    isSupportingSingleLine: Boolean = false,
) {
    val style = LocalListGroupStyle.current
    val density = LocalListGroupDensity.current
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
            horizontalArrangement = Arrangement.spacedBy(density.rowContentSpacing),
        ) {
            leading()
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = if (isLabelSingleLine) 1 else Int.MAX_VALUE,
                    overflow = if (isLabelSingleLine) TextOverflow.Ellipsis else TextOverflow.Clip,
                )
                if (supporting != null) {
                    Text(
                        supporting,
                        style = supportingStyle,
                        color = style.secondaryContent,
                        maxLines = if (isSupportingSingleLine) 1 else Int.MAX_VALUE,
                        overflow = if (isSupportingSingleLine) TextOverflow.Ellipsis else TextOverflow.Clip,
                    )
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
