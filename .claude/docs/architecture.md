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

## Découpage prévu pour OpenCTV

Cette carte est la cible, créée au fil des tâches (déjà en place : `core/common`, `core/di`, `core/designsystem`). Le brief produit
(`.claude/docs/product.md`) donne le pourquoi de chaque brique.

| Emplacement | Contenu |
|---|---|
| `core/designsystem/` | Thème TV (couleurs, typo lisible à 3 m, marges d'overscan ≈ 5 %), composants focalisables réutilisables (Compose for TV) |
| `core/webview/` | Fabrique de la `WebView` **verrouillée** (réglages de sécurité centralisés), `WebViewClient`/`WebChromeClient` de base, gestion du plein écran vidéo. Un seul endroit configure la WebView. |
| `core/common/` | `Resource<T>`, dispatchers, utilitaires |
| `core/database/` | Base Room de l'app |
| `core/network/` | Client OkHttp (listes de filtres uniquement) |
| `features/home/` | Écran d'accueil : grille de favoris, champ de recherche |
| `features/browser/` | Écran de navigation : WebView, barre d'adresse, résolution URL vs recherche, gestion de la touche Retour |
| `features/cursor/` | Curseur virtuel : état (position, vitesse, accélération), conversion OK → clic, défilement aux bords. Logique pure testable, indépendante de la WebView. |
| `features/bookmarks/` | Favoris : stockage Room, ajout/retrait |
| `features/adblock/` | Téléchargement et compilation des listes (EasyList), moteur de décision bloquer/laisser passer, activation par site |

Points d'attention :
- `features/browser` consomme le curseur et le bloqueur via leurs contrats exposés dans `core/` ou
  leur `domain/`, jamais en important leurs classes `ui/` ou `data/`.
- Le moteur de décision du bloqueur est appelé depuis `shouldInterceptRequest` (thread de fond, une
  fois par requête) : il doit être synchrone, sans I/O, et préchargé en mémoire.
- La logique du curseur (calcul de position, bords, accélération) est du Kotlin pur, testée en JUnit ;
  seul l'injection des `MotionEvent` dépend d'Android.
