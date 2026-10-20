package com.openctv.browser.features.home.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.openctv.browser.R
import com.openctv.browser.core.common.Resource
import com.openctv.browser.core.designsystem.DPadIcon
import com.openctv.browser.core.designsystem.ExpressiveShape
import com.openctv.browser.core.designsystem.FocusableTile
import com.openctv.browser.core.designsystem.KeyCap
import com.openctv.browser.core.designsystem.KeyHint
import com.openctv.browser.core.designsystem.OpenCtvButton
import com.openctv.browser.core.designsystem.OpenCtvColors
import com.openctv.browser.core.designsystem.OpenCtvIconButton
import com.openctv.browser.core.designsystem.OpenCtvIcons
import com.openctv.browser.core.designsystem.OpenCtvShapes
import com.openctv.browser.core.designsystem.OpenCtvTheme
import com.openctv.browser.core.designsystem.OverscanSafeArea
import com.openctv.browser.core.designsystem.asShape
import com.openctv.browser.core.designsystem.focusRingBorder
import com.openctv.browser.core.designsystem.morphBackground
import com.openctv.browser.core.designsystem.morphToCircle
import com.openctv.browser.core.navigation.Destination
import com.openctv.browser.features.home.domain.HomeFavorite

private const val GridColumns = 4

/** Accueil. [onOpenSearch] et [onAddBookmark] sont raccordés par l'écran de recherche (#5). */
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onNavigate: (Destination) -> Unit,
    onExit: () -> Unit,
    onOpenSearch: () -> Unit,
    onAddBookmark: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()
    val searchEngineName by viewModel.searchEngineName.collectAsStateWithLifecycle()
    val isExitHintVisible by viewModel.isExitHintVisible.collectAsStateWithLifecycle()

    val currentOnNavigate by rememberUpdatedState(onNavigate)
    val currentOnExit by rememberUpdatedState(onExit)
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is HomeEvent.Navigate -> currentOnNavigate(event.destination)
                HomeEvent.Exit -> currentOnExit()
            }
        }
    }

    BackHandler { viewModel.onBack() }

    HomeContent(
        favorites = favorites,
        isExitHintVisible = isExitHintVisible,
        searchPlaceholder = stringResource(R.string.home_search_placeholder, searchEngineName),
        onOpenFavorite = viewModel::onOpenFavorite,
        onOpenSearch = onOpenSearch,
        onAddBookmark = onAddBookmark,
        onOpenSettings = onOpenSettings,
        modifier = modifier,
    )
}

@Composable
internal fun HomeContent(
    favorites: Resource<List<HomeFavorite>>,
    isExitHintVisible: Boolean,
    searchPlaceholder: String,
    onOpenFavorite: (HomeFavorite) -> Unit,
    onOpenSearch: () -> Unit,
    onAddBookmark: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val searchFocus = remember { FocusRequester() }
    val emptyAddFocus = remember { FocusRequester() }
    val isEmpty = favorites is Resource.Success && favorites.data.isEmpty()

    // Focus initial : la recherche ; l'état vide donne le focus à son bouton d'ajout.
    LaunchedEffect(isEmpty) {
        if (isEmpty) emptyAddFocus.requestFocus() else searchFocus.requestFocus()
    }

    OverscanSafeArea(modifier) {
        Box(Modifier.fillMaxSize().background(OpenCtvColors.Surface)) {
            Column(Modifier.fillMaxSize()) {
                HomeHeader(onOpenSettings = onOpenSettings)
                Spacer(Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.home_title),
                    style = MaterialTheme.typography.displaySmall,
                    color = OpenCtvColors.OnSurface,
                )
                Spacer(Modifier.height(16.dp))
                SearchBar(
                    placeholder = searchPlaceholder,
                    onClick = onOpenSearch,
                    modifier = Modifier.focusRequester(searchFocus),
                )
                Spacer(Modifier.height(16.dp))
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    when (favorites) {
                        Resource.Loading -> Unit
                        // Une erreur de stockage ne bloque pas l'accueil : la recherche reste utilisable.
                        is Resource.Error ->
                            EmptyFavorites(onAdd = onAddBookmark, addFocus = emptyAddFocus, isUnavailable = true)
                        is Resource.Success ->
                            if (favorites.data.isEmpty()) {
                                EmptyFavorites(onAdd = onAddBookmark, addFocus = emptyAddFocus)
                            } else {
                                FavoritesGrid(favorites.data, onOpenFavorite, onAddBookmark)
                            }
                    }
                }
                RemoteHints()
            }
            if (isExitHintVisible) {
                ExitHint(Modifier.align(Alignment.BottomCenter).padding(bottom = 32.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun HomeHeader(onOpenSettings: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(36.dp).background(OpenCtvColors.Primary, ExpressiveShape.Cookie9.asShape()))
        Spacer(Modifier.width(12.dp))
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.titleLarge,
            color = OpenCtvColors.OnSurface,
        )
        Spacer(Modifier.weight(1f))
        OpenCtvIconButton(
            icon = OpenCtvIcons.Settings,
            contentDescription = stringResource(R.string.home_settings),
            onClick = onOpenSettings,
        )
    }
}

@Composable
private fun SearchBar(placeholder: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = OpenCtvShapes.Full
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(56.dp),
        shape = ClickableSurfaceDefaults.shape(shape = shape, focusedShape = shape),
        border = ClickableSurfaceDefaults.border(focusedBorder = focusRingBorder(shape)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = OpenCtvColors.SurfaceContainerHigh,
            contentColor = OpenCtvColors.OnSurfaceVariant,
            focusedContainerColor = OpenCtvColors.SurfaceContainerHighest,
            focusedContentColor = OpenCtvColors.OnSurface,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
    ) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(OpenCtvIcons.Search, contentDescription = null)
            Text(
                text = placeholder,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            KeyCap("OK")
        }
    }
}

@Composable
private fun FavoritesGrid(
    favorites: List<HomeFavorite>,
    onOpenFavorite: (HomeFavorite) -> Unit,
    onAddBookmark: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = stringResource(R.string.home_favorites),
                style = MaterialTheme.typography.titleLarge,
                color = OpenCtvColors.OnSurface,
            )
            Text(
                text = favorites.size.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = OpenCtvColors.OnSecondaryContainer,
                modifier = Modifier
                    .background(OpenCtvColors.SecondaryContainer, CircleShape)
                    .padding(horizontal = 10.dp, vertical = 2.dp),
            )
        }
        // contentPadding : la tuile focalisée grandit et son anneau déborde ; la grille ne doit pas le rogner.
        LazyVerticalGrid(
            columns = GridCells.Fixed(GridColumns),
            contentPadding = PaddingValues(8.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(favorites, key = { it.url }) { favorite ->
                FavoriteTile(favorite, onClick = { onOpenFavorite(favorite) })
            }
            item(key = "add") { AddTile(onClick = onAddBookmark) }
        }
    }
}

private val TileModifier = Modifier.fillMaxWidth().height(96.dp)

@Composable
private fun FavoriteTile(favorite: HomeFavorite, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    FocusableTile(onClick = onClick, modifier = TileModifier, interactionSource = interactionSource) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Monogram(favorite, isFocused)
            Column(Modifier.weight(1f)) {
                Text(
                    text = favorite.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = favorite.domain,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isFocused) OpenCtvColors.OnPrimaryContainer else OpenCtvColors.OnSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private data class MonogramColors(val container: Color, val onContainer: Color)

private val MonogramPalette = listOf(
    MonogramColors(OpenCtvColors.PrimaryContainer, OpenCtvColors.OnPrimaryContainer),
    MonogramColors(OpenCtvColors.SecondaryContainer, OpenCtvColors.OnSecondaryContainer),
    MonogramColors(OpenCtvColors.TertiaryContainer, OpenCtvColors.OnTertiaryContainer),
)

/** Monogramme 56 dp : forme Expressive au repos, cercle `primary` au focus (morph spring default spatial). */
@Composable
private fun Monogram(favorite: HomeFavorite, isFocused: Boolean) {
    val morph = remember(favorite.index) { ExpressiveShape.forMonogram(favorite.index).morphToCircle() }
    // State lu au dessin (morphBackground) : l'animation ne recompose pas la tuile à chaque image.
    val progress = animateFloatAsState(
        targetValue = if (isFocused) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 380f),
        label = "monogramMorph",
    )
    val colors = MonogramPalette[Math.floorMod(favorite.index, MonogramPalette.size)]
    val container = if (isFocused) OpenCtvColors.Primary else colors.container
    val content = if (isFocused) OpenCtvColors.OnPrimary else colors.onContainer
    Box(
        modifier = Modifier.size(56.dp).morphBackground(morph, container) { progress.value },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = favorite.monogram,
            style = MaterialTheme.typography.headlineSmall,
            color = content,
        )
    }
}

@Composable
private fun AddTile(onClick: () -> Unit) {
    val dashColor = OpenCtvColors.OutlineVariant
    val density = LocalDensity.current
    FocusableTile(onClick = onClick, modifier = TileModifier) {
        Row(
            Modifier
                .fillMaxSize()
                .drawBehind {
                    val stroke = with(density) { 2.dp.toPx() }
                    drawRoundRect(
                        color = dashColor,
                        topLeft = Offset(stroke / 2, stroke / 2),
                        size = Size(size.width - stroke, size.height - stroke),
                        cornerRadius = CornerRadius(28.dp.toPx()),
                        style = Stroke(stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f))),
                    )
                }
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier.size(40.dp).background(OpenCtvColors.SecondaryContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(OpenCtvIcons.Add, contentDescription = null, tint = OpenCtvColors.OnSecondaryContainer)
            }
            Text(stringResource(R.string.home_add), style = MaterialTheme.typography.titleMedium)
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun EmptyFavorites(onAdd: () -> Unit, addFocus: FocusRequester, isUnavailable: Boolean = false) {
    Column(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(96.dp).background(OpenCtvColors.TertiaryContainer, ExpressiveShape.SoftBurst.asShape()),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                OpenCtvIcons.StarFilled,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = OpenCtvColors.OnTertiaryContainer,
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.home_empty_title),
            style = MaterialTheme.typography.headlineSmall,
            color = OpenCtvColors.OnSurface,
            textAlign = TextAlign.Center,
        )
        // Erreur de stockage : mention discrète, l'accueil et la recherche restent utilisables.
        if (isUnavailable) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.home_favorites_unavailable),
                style = MaterialTheme.typography.bodyMedium,
                color = OpenCtvColors.OnSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(24.dp))
        OpenCtvButton(onClick = onAdd, modifier = Modifier.focusRequester(addFocus)) {
            Text(stringResource(R.string.home_empty_add))
        }
    }
}

@Composable
private fun RemoteHints() {
    Row(
        Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(32.dp, Alignment.CenterHorizontally),
    ) {
        KeyHint(stringResource(R.string.home_hint_navigate)) { DPadIcon() }
        KeyHint(stringResource(R.string.home_hint_select)) { KeyCap("OK") }
        KeyHint(stringResource(R.string.home_hint_quit)) { KeyCap("⟵") }
    }
}

@Composable
private fun ExitHint(modifier: Modifier = Modifier) {
    Text(
        text = stringResource(R.string.home_exit_hint),
        style = MaterialTheme.typography.bodyLarge,
        color = OpenCtvColors.InverseOnSurface,
        modifier = modifier
            .background(OpenCtvColors.InverseSurface, OpenCtvShapes.ExtraSmall)
            .padding(horizontal = 24.dp, vertical = 14.dp),
    )
}

private val PreviewFavorites = listOf(
    HomeFavorite("https://www.arte.tv", "Arte", "arte.tv", "A", 0),
    HomeFavorite("https://www.wikipedia.org", "Wikipédia", "wikipedia.org", "W", 1),
    HomeFavorite("https://www.twitch.tv", "Twitch", "twitch.tv", "T", 2),
)

@Preview(device = Devices.TV_1080p)
@Composable
private fun HomePreview() {
    OpenCtvTheme {
        HomeContent(
            favorites = Resource.Success(PreviewFavorites),
            isExitHintVisible = false,
            searchPlaceholder = "Rechercher sur DuckDuckGo ou saisir une adresse",
            onOpenFavorite = {}, onOpenSearch = {}, onAddBookmark = {}, onOpenSettings = {},
        )
    }
}

@Preview(device = Devices.TV_1080p)
@Composable
private fun HomeEmptyPreview() {
    OpenCtvTheme {
        HomeContent(
            favorites = Resource.Success(emptyList()),
            isExitHintVisible = true,
            searchPlaceholder = "Rechercher sur DuckDuckGo ou saisir une adresse",
            onOpenFavorite = {}, onOpenSearch = {}, onAddBookmark = {}, onOpenSettings = {},
        )
    }
}
