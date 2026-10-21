# Brief produit — OpenCTV

> À lire **avant toute tâche**. Ce document explique ce qu'on construit, pour qui, et ce qui fera
> réussir ou échouer le projet. Les docs `architecture.md` et `conventions.md` disent *comment* coder ;
> celui-ci dit *quoi* et *pourquoi*.

## Pourquoi ce projet existe

Les téléviseurs Android TV / Google TV (ici une TCL) n'ont pas de navigateur digne de ce nom :
Chrome n'existe pas en version TV, et les navigateurs du Play Store sont soit laids, soit truffés de
publicités, soit inutilisables à la télécommande. Le propriétaire de la TV a déjà dû désactiver les
applications publicitaires et de télémétrie du constructeur : **OpenCTV ne doit pas réintroduire ce
qu'on vient de retirer.**

OpenCTV est un **navigateur web pour Android TV**, au design soigné, pilotable entièrement à la
télécommande, **sans publicité, sans télémétrie, sans compte**.

## Pour qui

Une personne assise dans son canapé, à 3 mètres de l'écran, avec une télécommande à **croix
directionnelle (D-pad) + OK + Retour**. Pas de souris, pas de clavier physique, pas d'écran tactile.
Chaque interaction est évaluée à cette aune : *est-ce faisable en quelques appuis, sans se perdre ?*

## Moteur

On **n'embarque pas** Chromium et on ne le forke pas. On utilise **Android System WebView**
(`android.webkit.WebView`), le moteur Chromium déjà installé et mis à jour par Google sur la TV
(`com.google.android.webview`). Toute la valeur d'OpenCTV est dans l'UX autour du moteur.

Conséquences à garder en tête :
- La version du moteur dépend de la TV, pas de nous : ne pas supposer une API WebView récente sans
  vérifier `WebViewCompat` / `WebViewFeature.isFeatureSupported(...)`.
- Pas d'extensions (pas d'uBlock Origin) : le blocage de pubs est **notre** code.
- Certains sites refusent les WebViews ou leurs DRM : c'est une limite connue, pas un bug à corriger
  à tout prix.

## Périmètre de la version minimale (MVP)

| Fonctionnalité | Ce qui est attendu |
|---|---|
| **Accueil** | Écran soigné, lisible à 3 m : favoris en grille, champ de recherche, accès rapide. C'est la vitrine du projet. |
| **Barre d'adresse / recherche** | Saisie au clavier TV (IME système), détection URL vs recherche, moteur de recherche configurable parmi : **DuckDuckGo (défaut)**, Google, Bing, Qwant, Ecosia, Brave Search. **Suggestions locales uniquement** : favoris + recherches récentes stockées sur la TV ; aucune frappe n'est envoyée au réseau. |
| **Page de démarrage** | Configurable : **Écran d'accueil OpenCTV** (défaut) / Reprendre la dernière page / Un site précis. |
| **Écran Réglages** | Ouvert depuis l'engrenage de l'accueil : page de démarrage, moteur de recherche, masquage automatique de l'interface. Entièrement utilisable au D-pad. |
| **Masquage auto du HUD** | L'interface (barre d'adresse, menus) se masque après 3 / 5 / 10 s ou Jamais (**5 s par défaut**). Le premier appui sur la télécommande ne fait que réafficher le HUD, sans déclencher d'action. |
| **Favoris** | Ajouter / retirer / ouvrir, persistés localement. |
| **Curseur virtuel** | Pointeur à l'écran déplacé par le D-pad, OK = clic, défilement quand le pointeur touche un bord. **C'est le cœur du produit** (voir enjeux). |
| **Bloqueur de pubs** | Filtrage des requêtes à partir de listes type EasyList, via `WebViewClient.shouldInterceptRequest`. Activable/désactivable par site. |

**Hors MVP** (ne pas implémenter sans tâche dédiée) : onglets multiples, historique synchronisé,
comptes, saisie vocale, navigation « de lien en lien », téléchargements, mode privé, publication
Play Store.

## Les enjeux — ce qui fera réussir ou échouer OpenCTV

### 1. La navigation au D-pad (critique)
Le web est conçu pour la souris et le tactile. Si l'utilisateur ne peut pas atteindre un bouton,
fermer une popup ou faire défiler une page, **le produit a échoué**, quel que soit le reste.
- Le curseur doit être fluide (accélération sur appui long, pas de saccade), visible sur tout fond,
  et convertir OK en vrai événement de clic (`MotionEvent` down/up) au point visé.
- **Le focus ne doit jamais se perdre** : à tout moment, un élément est focalisé et visible. Un
  écran où l'appui sur une flèche ne fait rien est un bug bloquant.
- La touche **Retour** a une sémantique claire et prévisible : fermer le menu ouvert → reculer dans
  l'historique de la page → revenir à l'accueil → quitter. Jamais quitter l'app par surprise.
- Bascule claire entre « mode curseur » (dans la page) et « mode interface » (barre d'adresse, menus).

### 2. L'interface à 10 pieds (« 10-foot UI »)
- Marges de sécurité (overscan) d'environ 5 % sur les bords ; rien d'important collé au bord.
- Textes grands et contrastés, état focalisé **évident** (agrandissement, contour, couleur).
- Utiliser **Compose for TV** (`androidx.tv:tv-material`) pour les composants focalisables.
- Le design est un objectif à part entière : le navigateur actuel est « horrible », c'est la raison
  du projet.

### 3. Vie privée — non négociable
- **Aucun SDK d'analytics, de crash reporting tiers, de publicité.** Aucune donnée ne sort de la TV
  hormis les requêtes de navigation de l'utilisateur et le téléchargement des listes de filtres.
- Pas de compte, pas de cloud. Les favoris restent sur l'appareil.
- Toute nouvelle dépendance réseau ou tierce doit être justifiée sous cet angle.

### 4. Sécurité de la WebView
Une WebView mal configurée est une faille. Par défaut :
- `allowFileAccess = false`, `allowContentAccess = false`, pas d'accès fichier depuis les URL `file://`.
- **Aucun `addJavascriptInterface`** exposé aux pages web.
- Erreurs SSL (`onReceivedSslError`) : **`cancel()` par défaut**. `proceed()` est autorisé **uniquement** pour
  la page principale (jamais une sous-ressource), si l'utilisateur a explicitement accordé, sur un écran
  d'avertissement, une exception pour **cet hôte exact et l'empreinte SHA-256 de ce certificat** : un autre
  certificat pour le même hôte redéclenche l'avertissement. Exception de session (en mémoire) ou mémorisée sur
  la TV (jamais envoyée ailleurs), retirable dans Réglages › Exceptions de sécurité.
- Pages **HTTP** (non chiffrées) : jamais affichées sans avertissement. Une page principale en `http://` (saisie,
  lien, redirection, page de démarrage, favori) affiche un écran d'avertissement ; elle ne s'affiche qu'après
  « Afficher la page » (session) ou une exception HTTP mémorisée pour l'hôte exact. Le trafic en clair est
  autorisé au niveau de l'app (`usesCleartextTraffic`) **uniquement** parce que cet avertissement le garde ; le
  client HTTP de l'app (listes de filtres) reste en HTTPS seulement.
- Une page affichée par exception est signalée dans la barre d'outils (« Non sécurisé », « Certificat non vérifié »).
- Contenu mixte bloqué (`MIXED_CONTENT_NEVER_ALLOW`), Safe Browsing activé si disponible.
- Permissions demandées par les pages (caméra, micro, localisation) : refusées par défaut.
- Seuls les schémas `http`/`https` sont chargés ; les autres (`intent:`, `market:`…) sont filtrés.

### 5. Performance sur un matériel de TV
La TV cible a un SoC MediaTek modeste et peu de RAM.
- `shouldInterceptRequest` est appelé **pour chaque requête**, sur un thread de fond : la décision
  « bloquer / laisser passer » doit être quasi instantanée (structures précompilées en mémoire,
  recherche par domaine en O(1)), sans I/O ni allocation lourde par requête.
- Les listes de filtres sont téléchargées et compilées **hors** du chemin de navigation, puis mises
  en cache ; le premier lancement doit fonctionner même sans liste.
- Une seule WebView vivante pour le MVP ; libérer proprement ses ressources (`onPause`, `destroy`).

### 6. Vidéo plein écran
Les sites vidéo (YouTube web, replays) passent en plein écran via `WebChromeClient.onShowCustomView`.
Il faut le gérer, sinon la vidéo reste coincée dans la page. Retour quitte le plein écran.

### 7. Nom et marque
« Chrome » est une marque de Google : ne pas l'utiliser dans le nom affiché, les icônes ou les
textes de l'app. Le nom est **OpenCTV**, sans développer l'acronyme dans l'interface.

## Appareil de test

| | |
|---|---|
| Modèle | TCL Smart TV Pro (`G08_4K_GB`), Google TV |
| OS | Android 14 (API 34), SoC MediaTek |
| Accès | ADB réseau : `adb connect 10.0.0.51:5555` (autorisation déjà acceptée sur la TV) |
| Moteur | Android System WebView (`com.google.android.webview`) |

Simuler la télécommande :
```bash
adb -s 10.0.0.51:5555 shell input keyevent KEYCODE_DPAD_UP      # DOWN / LEFT / RIGHT
adb -s 10.0.0.51:5555 shell input keyevent KEYCODE_DPAD_CENTER  # OK
adb -s 10.0.0.51:5555 shell input keyevent KEYCODE_BACK
adb -s 10.0.0.51:5555 exec-out screencap -p > /tmp/openctv.png
```

Les builds et installs restent à la main de l'utilisateur (sauf l'agent `qa`, quand il est lancé).

## Critère de réussite global

> L'utilisateur ouvre OpenCTV, cherche un site, s'y rend, clique sur un bouton, fait défiler la page,
> regarde une vidéo en plein écran, ajoute le site en favori et revient à l'accueil — **uniquement à
> la télécommande, sans jamais être bloqué, et sans voir de publicité.**
