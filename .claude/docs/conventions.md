# Code Conventions

## Contract-Based Repository Pattern

```kotlin
// Contract
interface BookmarksContract {
    fun getBookmarks(): Flow<Resource<List<Bookmark>>>
}

// Implementation
class BookmarksImpl(private val dao: BookmarkDao) : BookmarksContract {
    override fun getBookmarks() = flow {
        emit(Resource.Loading())
        try {
            emit(Resource.Success(dao.getAll().map { it.toDomain() }))
        } catch (e: Exception) {
            emit(Resource.Error(e.message))
        }
    }
}

// Module Koin
single<BookmarksContract> { BookmarksImpl(get()) }
```

## Error Handling
- Erreurs réseau/stockage capturées dans le repository → `Resource.Error(message)`
- Les ViewModels réagissent aux états `Resource`, ne catchent jamais d'exceptions directement
- L'UI affiche les erreurs via Snackbar/Dialog selon `Resource.Error` — **focalisable** et fermable avec Retour

## State Management
- Repositories & ViewModels exposent `StateFlow<Resource<T>>`
- Les composables collectent via `collectAsStateWithLifecycle()`
- Aucune logique métier dans les composables — déléguer au ViewModel

## Spécifique TV
- Composants focalisables : **Compose for TV** (`androidx.tv:tv-material`), état focalisé toujours visible.
- Chaque écran déclare son focus initial (`FocusRequester`) : à l'ouverture, un élément est focalisé.
- La touche Retour est gérée explicitement (`BackHandler`) selon la sémantique du brief produit.
- **Jamais `focusProperties { canFocus = true }`** (ni `canFocus = <booléen>` qui peut valoir `true`) sur un
  conteneur : ces propriétés s'appliquent au premier `focusTarget` en dessous, y compris ceux, non focalisables
  par défaut, de `verticalScroll`, `Lazy*` et `focusGroup()`, qui deviennent alors focalisables. Or Compose
  traduit Retour en `FocusDirection.Exit` *avant* l'`OnBackPressedDispatcher` : le focus monte vers ce
  conteneur invisible, la touche est consommée, le `BackHandler` n'est pas appelé et plus rien n'est focalisé
  à l'écran. Pour retirer le focus : `if (désactivé) canFocus = false`. Pour une surcouche modale (panneau,
  menu, saisie) : `Modifier.focusTrap()` sur la surcouche (`SidePanelOverlay` pour un panneau latéral),
  plutôt que de désactiver l'écran dessous.
- Pas de dépendance au tactile (`android.hardware.touchscreen` non requis dans le manifest).

## WebView
- La `WebView` n'est créée **que** par la fabrique de `core/webview/`, qui applique les réglages de
  sécurité du brief produit. Aucune feature ne modifie `WebSettings` directement.
- Toute API WebView récente passe par `WebViewFeature.isFeatureSupported(...)` (androidx.webkit).

## Testing — JUnit 4 + MockK + Turbine + Truth
Style Gherkin obligatoire, dans le **nom de la méthode** (pas de fichier `.feature`) :

```kotlin
@Test
fun `Given a bookmark already saved When the user adds the same url Then the list keeps a single entry`() =
    runTest {
        turbineScope {
            // Given
            val existing = BookmarkBuilder()
                .withUrl("https://example.org")
                .build()

            coEvery { dao.getAll() } returns listOf(existing)

            // When
            repository.add(existing.url, title = "Example")
            val turbine = repository.getBookmarks().testIn(backgroundScope)

            // Then
            assertThat(turbine.awaitItem()).isInstanceOf(Resource.Loading::class.java)
            val result = (turbine.awaitItem() as Resource.Success).data
            assertThat(result).hasSize(1)

            turbine.cancelAndIgnoreRemainingEvents()
        }
    }
```

On teste des **comportements utilisateur** et **règles métier** : résolution saisie → URL ou recherche,
décision du bloqueur (domaine bloqué, sous-domaine, exception par site), parsing des listes de filtres,
déplacement du curseur (bords, accélération, défilement), sémantique de la touche Retour, mapping
entités → domaine, états `Resource`. Pas chaque fonction.

## Dates & temps
- `java.time` : `Instant` pour les horodatages (pas `System.currentTimeMillis()`),
  `LocalDate` pour la logique calendaire.

## Lisibilité
- SRP : nommer chaque booléen intermédiaire, pas de condition composée inline.

## UI Components
- Simples et réutilisables, pas de sur-spécialisation
- Prévisualisation avec `@Preview` (device TV : `@Preview(device = Devices.TV_1080p)`)

## Dépendances
- Toute nouvelle dépendance doit être justifiée, alignée sur le catalogue de versions, et publiée
  depuis **au moins 10 jours** (protection supply-chain).
- **Interdit** : SDK d'analytics, de publicité, de crash reporting tiers (Firebase Analytics/Crashlytics,
  etc.) — voir le brief produit, section vie privée.
