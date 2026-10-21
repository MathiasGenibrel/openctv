# Architecture

## Organisation du code — feature folders

```
app/src/main/java/com/openctv/browser/
  core/                      # transverse : network, database, designsystem, di, common
  features/
    <feature>/
      data/                  # dto, mapper, dao, <Feature>Contract + <Feature>Impl
      domain/                # modèles, use cases
      ui/                    # écrans Compose, composants, <Feature>ViewModel
      di/                    # module DI de la feature
```

### Règles
- Une **feature** = un dossier autonome (`data / domain / ui`, + son module DI).
- Le transverse (réseau, base, design system, utilitaires, DI racine) vit dans `core/`.
- **Aucune dépendance directe entre features** : passer par `core/` ou par le domaine.
- Une tâche touche idéalement **un seul** dossier de feature.

## Repositories
Pattern contract obligatoire pour tout nouveau repository : interface `*Contract` + `*Impl`,
câblés dans un module Koin.

## Injection
`MainActivity` est la **racine de composition** : elle seule résout les contrats Koin et construit les
ViewModels (`viewModel { … }` dans la portée de la destination). Les écrans reçoivent leur ViewModel en
paramètre ; aucune feature n'appelle Koin (`KoinPlatform`, `get()`) directement.

Exception : le `HomeViewModel` vit avec l'activité (l'accueil est la racine de la navigation) pour
mémoriser l'élément focalisé et le restaurer au retour (`HomeFocus`).

## Surcouches
Un écran qui doit rester vivant sous un autre (la page web sous la saisie d'adresse : WebView,
historique, défilement) ne change pas de destination : il affiche une **surcouche**. Son ViewModel en
expose l'état (`BrowserViewModel.layers` — pile `BrowserLayers` dont la couche la plus haute décide de
Retour via `backAction` —, `HomeViewModel.searchInput`) ; l'écran reçoit le
contenu de la surcouche en slot depuis `MainActivity` (aucune feature n'importe l'`ui/` d'une autre), la
place dans une portée `ScopedViewModels`, la rend modale (`Modifier.focusTrap()`) et rend le focus à
l'élément d'origine à sa fermeture.

## Découpage prévu pour OpenCTV

Cette carte est la cible, créée au fil des tâches (déjà en place : `core/common`, `core/di`, `core/designsystem`). Le brief produit
(`.claude/docs/product.md`) donne le pourquoi de chaque brique.

| Emplacement | Contenu |
|---|---|
| `core/designsystem/` | Thème TV (couleurs, typo lisible à 3 m, marges d'overscan ≈ 5 %), composants focalisables réutilisables (Compose for TV) |
| `core/webview/` | Fabrique de la `WebView` **verrouillée** (réglages de sécurité centralisés), `WebViewClient`/`WebChromeClient` de base, gestion du plein écran vidéo, masquage des bandeaux de cookies (`CosmeticSelector` : liste blanche, `CosmeticStyleScript` : script constant et budget, `CosmeticStyleGate` : quand l'appliquer, Kotlin pur). Un seul endroit configure la WebView. Décisions de sécurité de la page principale (HTTP, SSL, page affichée) dans `PageSecurityGuard`, Kotlin pur. |
| `core/security/` | Exceptions de sécurité accordées par l'utilisateur (`SecurityExceptionsContract` : HTTP par hôte, certificat par hôte + empreinte SHA-256), session en mémoire + DataStore local ; `SiteHost` (hôte exact, parseur strict) |
| `core/zoom/` | Zoom des pages : niveaux (`PageZoom` : 100 % = 960 px, 75 %, 67 %, 50 % = 1920 px), zoom propre à un site (`PageZoomContract` : DataStore local, hôte exact `SiteHost`) et niveau effectif d'une page (`effectiveZoom` : zoom du site, sinon niveau par défaut de `Settings.pageZoom`). La mise en page zoomée de la WebView (`pageZoomLayout`) et l'échelle de la vue (`PointerAwareWebView.applyPageZoom`) sont dans `core/webview/` ; `PointerAwareWebView` est le seul endroit qui convertit les coordonnées de l'écran en coordonnées de la vue (`toViewCoordinate`, `scrollByOnScreen`) : le curseur ne parle qu'en pixels de l'écran. |
| `core/history/` | `PageVisitsContract` : le navigateur y enregistre les pages affichées, sans connaître `features/history` qui l'implémente |
| `core/common/` | `Resource<T>`, dispatchers, utilitaires |
| `core/database/` | Base Room de l'app ; schémas versionnés dans `app/schemas/` (`exportSchema`), migrations dans `Migrations.kt` (`AllMigrations`, jamais destructives), vérifiées contre ces schémas par `MigrationSchemaTest` : toute nouvelle version de la base ajoute sa migration et commite son schéma |
| `core/network/` | Client OkHttp (listes de filtres uniquement), HTTPS seulement |
| `features/home/` | Écran d'accueil : grille de favoris, champ de recherche |
| `features/browser/` | Écran de navigation : WebView, barre d'adresse, résolution URL vs recherche, gestion de la touche Retour |
| `features/cursor/` | Curseur virtuel : état (position, vitesse, accélération), conversion OK → clic, défilement aux bords. Logique pure testable, indépendante de la WebView. |
| `features/search/` | Saisie d'adresse/recherche : résolution saisie → URL ou recherche (`InputResolver`), suggestions locales (favoris + recherches récentes, DataStore), écran de saisie |
| `features/bookmarks/` | Favoris : stockage Room, ajout/retrait |
| `features/history/` | Historique : Room (table `history_entries`, migration 1→2 dans `core/database/Migrations.kt`, jamais destructive), règles pures (`HistoryPolicy` : fusion des doublons consécutifs, 90 jours / 1 000 entrées ; `groupByDay`), écran Historique (destination depuis l'accueil, surcouche du navigateur depuis son menu). Une page rouverte depuis la destination Historique y ramène à Retour, sur son entrée (`Destination.Browser.returnTo`) ; « Accueil » mène toujours à l'accueil |
| `features/adblock/` | Téléchargement et compilation des listes (`FilterListId` : EasyList, EasyList Cookie), moteur de décision bloquer/laisser passer, activation par site. Règles cosmétiques de la liste cookies (`CosmeticRuleParser` → `CosmeticFilters`, index par domaine ; règles génériques réduites par `GenericCosmeticBudget`) exposées à `core/webview` par `CosmeticSelectionSource` (`CookieBannerSelectionSource`). |

Points d'attention :
- `features/browser` consomme le curseur et le bloqueur via leurs contrats exposés dans `core/` ou
  leur `domain/`, jamais en important leurs classes `ui/` ou `data/`.
- Le moteur de décision du bloqueur est appelé depuis `shouldInterceptRequest` (thread de fond, une
  fois par requête) : il doit être synchrone, sans I/O, et préchargé en mémoire.
- La logique du curseur (calcul de position, bords, accélération) est du Kotlin pur, testée en JUnit ;
  seul l'injection des `MotionEvent` dépend d'Android.
