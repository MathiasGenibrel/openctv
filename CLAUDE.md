# CLAUDE.md

OpenCTV — navigateur web pour Android TV / Google TV, basé sur Android System WebView (Chromium),
pilotable entièrement à la télécommande, sans pub ni télémétrie. Jetpack Compose app (Kotlin) avec
Compose for TV, architecture feature folders, DI Koin, OkHttp (téléchargement des listes de filtres),
Room (favoris).

> Le code fait foi sur ce fichier. Catalogue de versions : `gradle/libs.versions.toml`
> (toute version ≥ 10 jours).

**Avant toute tâche, lire le brief produit : @.claude/docs/product.md** — ce qu'on construit, pour
qui, et les enjeux (navigation au D-pad, UI à 10 pieds, vie privée, sécurité WebView, performance TV).

## Quick Reference
- **Min SDK 28 / Target SDK 35** — Kotlin 2.x, Java 17, Gradle (wrapper `./gradlew`)
- **Package / applicationId** : `com.openctv.browser` — activité de lancement `com.openctv.browser/.MainActivity`
  (déclarée `LEANBACK_LAUNCHER`, `android.software.leanback` requis, `android.hardware.touchscreen` non requis)
- **Test command**: `./gradlew :app:testDebugUnitTest`
- **Install command** (utilisateur uniquement): `adb connect 10.0.0.51:5555 && ANDROID_SERIAL=10.0.0.51:5555 ./gradlew :app:installDebug`
- **Appareil cible** : TCL Smart TV Pro (G08), Android 14 / API 34, ADB réseau `10.0.0.51:5555`
- **Claude ne déclenche jamais de build applicatif.** Les builds/installs sont la responsabilité
  de l'utilisateur ; les agents exécutent en revanche la suite de tests unitaires.

## Core Rules
- Follow existing patterns. Nouveaux repositories → pattern contract (`*Contract` + `*Impl`)
- `Resource<T>` pour l'état, `StateFlow` pour l'exposition, `collectAsStateWithLifecycle()` en UI
- Google Android/Compose guidelines + **Compose for TV** pour tout composant focalisable. Aucune logique métier dans les composables
- **Le focus ne se perd jamais** : chaque écran a un élément focalisé visible, la touche Retour a un comportement prévisible
- **Aucun SDK d'analytics, de crash reporting tiers ou de publicité.** Aucune donnée ne quitte la TV hors navigation
- **WebView verrouillée par défaut** : pas d'accès fichier, pas de `addJavascriptInterface`, erreurs SSL annulées par défaut (`proceed()` uniquement pour une exception explicite hôte + empreinte de certificat choisie par l'utilisateur, page principale seulement), pages HTTP jamais affichées sans avertissement
- Tests JUnit, nommage Gherkin (Given/When/Then), orientés comportement utilisateur
- Simplicité d'abord. Impact minimal. Pas de correctif temporaire. Pas de phrases de remplissage.

## Boucle agentique

**Une tâche = un worktree = une branche.** `/dev` ouvre `.claude/worktrees/<réf>` depuis `origin/main`
via `.claude/scripts/worktree.sh setup <réf> <branche>`, et y fait travailler tous les agents : plusieurs tâches
indépendantes avancent ainsi **en parallèle** sans conflit de fichiers. Le script relie les fichiers non
versionnés du build (`local.properties`) — Gradle fonctionne depuis le worktree. Aucun agent ne supprime
de worktree : le nettoyage passe par `/worktree-clean`, une fois la PR fusionnée. Détails :
@.claude/docs/workflows.md

| Commande | Rôle |
|---|---|
| `/issue <besoin>` | Découpe un besoin en tâches (GitHub Issues) (validation utilisateur obligatoire avant création) |
| `/dev <tâche>` | Orchestration : `dev-integration` → `dev-architecture` → PR → QA (sur demande) |
| `/dev-with-plan <tâche>` | Idem, précédée d'un plan écrit dans `tasks/` et validé par l'utilisateur |
| `/worktree-clean` | Supprime les worktrees dont la PR est fusionnée/fermée et l'arbre propre |

| Agent | Modèle | Rôle |
|---|---|---|
| `dev-integration` | Sonnet 5 | Implémente, écrit et exécute les tests, ouvre la PR |
| `dev-architecture` | Opus 5 | Lance le skill `improve-codebase-architecture` et applique les refactors retenus |
| `qa` | Opus 5 | Valide la PR sur la TV TCL au D-pad (sur demande explicite) — coûteux en tokens |

Le skill `.claude/skills/improve-codebase-architecture/` est une **dépendance importée** de
`dev-architecture` : il reste identique à sa version de référence, ne le modifie pas.

## Detailed Documentation
- **Product brief & enjeux** (à lire en premier) : @.claude/docs/product.md
- Architecture & packages: @.claude/docs/architecture.md
- Design (M3 Expressive : couleurs, formes, typo, focus, écrans) : @.claude/docs/design.md
- Code conventions & examples: @.claude/docs/conventions.md
- Workflow & task management: @.claude/docs/workflows.md
