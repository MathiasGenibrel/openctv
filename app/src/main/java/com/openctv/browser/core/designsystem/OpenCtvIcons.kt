package com.openctv.browser.core.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import com.openctv.browser.R

/**
 * Icônes d'OpenCTV : **Material Symbols Rounded** (poids 400, grade 0, taille optique 24 px, contour ;
 * [StarFilled] en variante remplie). Ce sont les vector drawables officiels `res/drawable/ic_*.xml` du
 * dépôt `google/material-design-icons` (commit `49d4db35df873165d6bd6ba09b063c7dafbac2f4`,
 * `symbols/android/<nom>/materialsymbolsrounded/<nom>_24px.xml`), sous licence Apache 2.0 — voir `NOTICE`.
 *
 * Les tracés sont blancs : la couleur vient du `tint` de [Icon] (par défaut la couleur de contenu
 * courante, donc l'état focalisé d'une surface `tv-material` s'applique tout seul).
 *
 * ```
 * Icon(OpenCtvIcons.Search, contentDescription = null)
 * ```
 */
object OpenCtvIcons {
    val Add: Painter @Composable get() = painterResource(R.drawable.ic_add)
    val ArrowBack: Painter @Composable get() = painterResource(R.drawable.ic_arrow_back)
    val ArrowForward: Painter @Composable get() = painterResource(R.drawable.ic_arrow_forward)
    val Check: Painter @Composable get() = painterResource(R.drawable.ic_check)
    val ChevronRight: Painter @Composable get() = painterResource(R.drawable.ic_chevron_right)
    val Close: Painter @Composable get() = painterResource(R.drawable.ic_close)
    val FilterList: Painter @Composable get() = painterResource(R.drawable.ic_filter_list)
    val Gamepad: Painter @Composable get() = painterResource(R.drawable.ic_gamepad)
    val History: Painter @Composable get() = painterResource(R.drawable.ic_history)
    val Home: Painter @Composable get() = painterResource(R.drawable.ic_home)
    val Keyboard: Painter @Composable get() = painterResource(R.drawable.ic_keyboard)
    val Lock: Painter @Composable get() = painterResource(R.drawable.ic_lock)
    val MoreVert: Painter @Composable get() = painterResource(R.drawable.ic_more_vert)
    val RadioButtonChecked: Painter @Composable get() = painterResource(R.drawable.ic_radio_button_checked)
    val RadioButtonUnchecked: Painter @Composable get() = painterResource(R.drawable.ic_radio_button_unchecked)
    val Refresh: Painter @Composable get() = painterResource(R.drawable.ic_refresh)
    val Search: Painter @Composable get() = painterResource(R.drawable.ic_search)
    val Settings: Painter @Composable get() = painterResource(R.drawable.ic_settings)
    val Shield: Painter @Composable get() = painterResource(R.drawable.ic_shield)
    val Speed: Painter @Composable get() = painterResource(R.drawable.ic_speed)
    val Star: Painter @Composable get() = painterResource(R.drawable.ic_star)
    val StarFilled: Painter @Composable get() = painterResource(R.drawable.ic_star_filled)
    val Timer: Painter @Composable get() = painterResource(R.drawable.ic_timer)
    val TravelExplore: Painter @Composable get() = painterResource(R.drawable.ic_travel_explore)
    val WifiOff: Painter @Composable get() = painterResource(R.drawable.ic_wifi_off)
}

@Preview(device = Devices.TV_1080p)
@Composable
private fun OpenCtvIconsPreview() {
    OpenCtvTheme {
        Column(modifier = Modifier.padding(32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            listOf(
                OpenCtvIcons.Search, OpenCtvIcons.Settings, OpenCtvIcons.Keyboard, OpenCtvIcons.Star,
                OpenCtvIcons.StarFilled, OpenCtvIcons.Add, OpenCtvIcons.ArrowBack, OpenCtvIcons.ArrowForward,
                OpenCtvIcons.Refresh, OpenCtvIcons.Close, OpenCtvIcons.MoreVert, OpenCtvIcons.Lock,
                OpenCtvIcons.Shield, OpenCtvIcons.Home, OpenCtvIcons.TravelExplore, OpenCtvIcons.Speed,
                OpenCtvIcons.Timer, OpenCtvIcons.FilterList, OpenCtvIcons.WifiOff, OpenCtvIcons.History,
                OpenCtvIcons.Check, OpenCtvIcons.ChevronRight, OpenCtvIcons.RadioButtonChecked,
                OpenCtvIcons.RadioButtonUnchecked, OpenCtvIcons.Gamepad,
            ).chunked(13).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    row.forEach { Icon(it, contentDescription = null, tint = OpenCtvColors.OnSurface) }
                }
            }
        }
    }
}
