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
  sécurité du brief produit. Aucune feature ne modifie `WebSettings` directement. Seule exception, dans
  `core/webview/` : l'instance temporaire de `WebViewDataCleaner.clearCache` (le cache HTTP n'est atteignable que par
  une instance), créée sur le thread principal, jamais chargée ni affichée, détruite aussitôt (`finally`).
- **Effacement des données** (`WebDataContract`) : `WebViewDataCleaner` reste un adaptateur mince (appels du moteur
  seulement) ; l'orchestration (catégories indépendantes, échecs listés, effacement non annulable une fois lancé)
  vit dans `ClearBrowsingData`, testé en JUnit.
- **SSL** : `onReceivedSslError` annule (`cancel()`) par défaut. `proceed()` seulement si `SslErrorPolicy.shouldProceed`
  le décide : erreur de la **page principale**, et exception (`SiteException.Certificate`) accordée par l'utilisateur
  pour **cet hôte exact + cette empreinte SHA-256**. Jamais pour une sous-ressource, jamais sans empreinte lisible.
- **HTTP** : la page principale en `http://` est remplacée par un avertissement (`WebNavigationPolicy.navigationOf`,
  appliqué au `load` du ViewModel et dans `shouldOverrideUrlLoading`), sauf exception `SiteException.Http`. Chaque page
  principale affichée est **revérifiée** à `onPageStarted` / `onPageFinished` (`PageSecurityGuard.checkShownPage`) :
  historique, rechargement et POST ne passent pas par `shouldOverrideUrlLoading`.
- **Décisions SSL retenues par le moteur** : après un `proceed()`, Chromium ne rappelle plus `onReceivedSslError` pour
  ce couple hôte + certificat, même dans une autre WebView. La fabrique appelle donc `clearSslPreferences()` à chaque
  création, et `PageSecurityGuard` retient les certificats acceptés (badge maintenu, rechargement si l'exception a été
  retirée). Toute logique de décision vit dans `PageSecurityGuard` (Kotlin pur, testé) ; `SecureWebViewClient` n'est
  que l'adaptateur Android.
- **Client HTTP de l'app** (`core/network`) : HTTPS seulement (`ConnectionSpec.MODERN_TLS`, pas de `CLEARTEXT`), car
  `usesCleartextTraffic="true"` (nécessaire à la WebView) l'autoriserait aussi.
- **Exceptions de sécurité** : `core/security` (`SecurityExceptionsContract`), hôte normalisé par `SiteHost` (hôte
  exact, `www.` conservé, strict : l'ambiguïté donne `null`, donc aucune exception possible). Session en mémoire,
  mémorisées dans DataStore sur la TV. Ne jamais accorder une exception sans geste explicite de l'utilisateur.
- **Navigation par éléments** (mode par site, `core/navmode`, défaut curseur) : **aucun JavaScript**. Le D-pad est traduit
  par `ElementKeys` (Kotlin pur, testé) en `KeyEvent` Tab / Maj+Tab / Entrée envoyés à la WebView
  (`core/webview/ElementKeyInjector`) ; la page dessine son propre contour (`:focus-visible`) et défile jusqu'à
  l'élément, **aucun style n'est injecté**. Seule cette traduction envoie des touches à la page, et seulement quand
  `BrowserViewModel.onElementKey` l'autorise : mode éléments, page affichée, ni surcouche ni mode interface ni erreur.
  Champ de saisie focalisé (`isTextFieldFocused`) : OK ouvre le clavier, Gauche/Droite restent à la page.
- Toute API WebView récente passe par `WebViewFeature.isFeatureSupported(...)` (androidx.webkit).
- `evaluateJavascript` autorisé pour **deux usages seulement**, tous deux dans `core/webview/`, à résultat ignoré et
  échec silencieux, jamais de JavaScript persistant ni d'`addJavascriptInterface` :
  1. commandes média constantes sur la vidéo de la page, en plein écran **comme hors plein écran** (`MediaCommand` :
     lecture/pause, lecture, pause, saut ±10 s ; déclenchées par les touches média de la télécommande,
     `core/remote/RemoteKeyMap`) : ensemble fermé de scripts constants, aucune donnée de la page ou de l'utilisateur
     concaténée ;
  2. **masquage des bandeaux de cookies** (`CosmeticStyleScript`, liste « EasyList Cookie », réglage « Bandeaux de
     cookies ») : un **modèle de script constant** qui n'ajoute qu'un élément `<style>` et y insère, par
     `CSSStyleSheet.insertRule` (une règle par appel, donc rien ne « sort » de sa règle ; non bloqué par une CSP
     `style-src`), des règles `sélecteur { display: none !important }`. Les sélecteurs viennent **uniquement** de la liste
     compilée (jamais de la page ni de l'utilisateur), passent la liste blanche de `CosmeticSelector` (revérifiée à la
     construction du script), et sont insérés comme tableau JSON de chaînes, jamais concaténés au code. Aucune donnée
     lue dans la page (hors `document.head` pour y rattacher le `<style>`), aucun résultat exploité. Page principale
     http/https seulement, page que le garde a laissé s'afficher, une fois par page (`onPageCommitVisible`, sinon
     `onPageFinished`) ; jamais pour une page en échec ou sous avertissement (HTTP, certificat), même si le moteur y
     affiche sa page d'erreur. Ces décisions vivent dans `CosmeticStyleGate` (Kotlin pur, testé) ; `SecureWebViewClient`
     n'en est que l'adaptateur. Rien si le bloqueur est coupé (globalement ou pour le site) ou si « Bandeaux de cookies » l'est.
     `addDocumentStartJavaScript` n'est **pas** utilisé : les règles dépendent de l'hôte, il faudrait enregistrer un
     script par hôte (asynchrone, sur le thread principal, en course avec la navigation) ou embarquer toute la liste
     dans un script qui la trie dans la page. Le script est donc préparé hors du thread principal (à la requête de la
     page principale) pour l'hôte exact, puis évalué à son premier rendu.
- **Liste blanche des sélecteurs cosmétiques** (`CosmeticSelector.isSafe`, appliquée au parsing **et** à la
  construction du script) : caractères `A-Za-z0-9 _-.#>+~*:[]()="'^$|,` seulement ; 256 caractères au plus ; refus de
  `{ } ; @ \ < / !`, des non ASCII, de `url(`, `expression`, `javascript`, `import`, des pseudo-éléments, des
  pseudo-classes hors `not/is/where/has/nth-*/first-child/last-child/…` et de toute parenthèse hors de celles-ci, des
  crochets/parenthèses/guillemets mal appariés, et des sélecteurs qui masquent toute la page (`*`, `html`, `body`,
  `head`, `:root`). Une règle dont un sélecteur est refusé est ignorée en entier.
- **Budget du script cosmétique** (performance TV : il est analysé et ses règles compilées dans chaque page) : tableau de
  sélecteurs de 40 000 caractères au plus par page (`CosmeticStyleScript.MaxTotalChars`, script de ~40 Ko au plus contre
  ~320 Ko pour la liste générique entière), règles du site d'abord. Les règles génériques (~15 000 dans la liste, presque
  toutes propres à un site) sont réduites **une fois, à la compilation de la liste**, à 32 000 caractères
  (`GenericCosmeticBudget`, ~1 800 sélecteurs, ~32 Ko) par ordre d'impact : `#id`/`.classe` des plateformes de
  consentement connues, puis noms de bandeau génériques (`#cookie-banner`), puis autres `#id`/`.classe`, puis le reste
  (attributs, combinateurs, pseudo-classes, plus coûteux pour le moteur CSS). Par page, seules les règles du site sont
  encodées : l'encodage des génériques est mis en cache, la préparation dans `shouldInterceptRequest` reste de l'ordre
  de la dizaine de microsecondes.

## Touches de la télécommande
- Table unique : `core/remote/RemoteKeyMap` (Kotlin pur, testée). Ajuster une touche = changer cette table. Toute touche
  absente (Accueil, Retour, Volume, Muet, Alimentation, source, Réglages, Guide, touches d'applis, croix) n'est jamais
  interceptée : **ne jamais y ajouter une touche système**.
- Traduction unique d'une touche Compose : `KeyEvent.dispatchRemoteAction` (`core/remote`), appelée par l'accueil et par
  `BrowserKeys` ; `onPreviewKeyEvent` ne fait que déléguer. Le plein écran (vue Android hors Compose) lit la même table
  via `FullscreenKeys`.
- Aiguillage : `BrowserViewModel.onRemoteAction` / `HomeViewModel.onRemoteAction`. Sous une surcouche (saisie, menu,
  historique, plein écran) rien n'est consommé ; le premier appui d'un HUD masqué ne fait que le réafficher
  (`onRemoteKey`, avant l'aiguillage).
- **Champ de saisie de la page** : `onPreviewKeyEvent` passe avant la WebView. Quand un champ éditable de la page a le
  focus (`WebView.isTextFieldFocused`, `core/webview` : état d'IME du moteur, sans JavaScript), chiffres, Recherche et
  Chaîne/Page ± lui reviennent (`RemoteAction.yieldsToPageTextField`) ; média et Info gardent leur rôle.

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
