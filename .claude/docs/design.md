# Design — OpenCTV (M3 Expressive, Compose for TV)

> Source : maquette Figma « OpenCTV », page « Écrans », section **Proposition M3 Expressive**
> (https://www.figma.com/design/fw3KJASBVjD9esgrunMqeK/OpenCTV?node-id=6-405). Construite avec le
> *Material 3 Design Kit*. Les agents n'ont pas accès à Figma : ce fichier fait foi pour les valeurs.

## Unités
- Les écrans sont conçus en **dp Compose for TV** : un écran 1080p = **960 × 540 dp**. Les composants M3
  gardent leur taille native (bouton 40–56 dp, icône 24 dp…).
- Marges d'overscan : **48 dp** horizontal, **27 dp** vertical (≈ 5 %).

## Couleurs — schéma M3 **Dark** (baseline)
| Rôle | Hex | Rôle | Hex |
|---|---|---|---|
| primary | `#D0BCFF` | onPrimary | `#381E72` |
| primaryContainer | `#4F378B` | onPrimaryContainer | `#EADDFF` |
| secondary | `#CCC2DC` | onSecondary | `#332D41` |
| secondaryContainer | `#4A4458` | onSecondaryContainer | `#E8DEF8` |
| tertiary | `#EFB8C8` | onTertiary | `#492532` |
| tertiaryContainer | `#633B48` | onTertiaryContainer | `#FFD8E4` |
| error | `#F2B8B5` | onError | `#601410` |
| errorContainer | `#8C1D18` (*valeur M3 standard, absente du Figma*) | onErrorContainer | `#F9DEDC` (*idem*) |
| surface | `#141218` | onSurface | `#E6E0E9` |
| onSurfaceVariant | `#CAC4D0` | outline | `#938F99` |
| outlineVariant | `#49454F` | inverseSurface | `#E6E0E9` |
| inverseOnSurface | `#322F35` | inversePrimary | `#6750A4` |
| surfaceContainerLowest | `#0F0D13` (*M3 standard*) | surfaceContainerLow | `#1D1B20` |
| surfaceContainer | `#211F26` | surfaceContainerHigh | `#2B2930` |
| surfaceContainerHighest | `#36343B` | scrim | `#000000` (utilisé à 45–60 %) |

## Formes (corner)
Extra-small 4 · Small 8 · Medium 12 · Large 16 · Large-increased 20 · Extra-large 28 · Full (pilule).
- Tuiles, panneaux, cartes : **Extra-large 28** ; groupes de listes / menu : **Large 16** ; barre de
  recherche, toolbar, champ d'adresse, boutons : **Full**.
- Formes Expressive (monogrammes, illustrations, curseur) : cookie 9 et 12 pointes, clover 4, sunny 8,
  burst 12, soft burst 8–10, pentagone arrondi, arche (haut 28 / bas 8), pilule. Utiliser `MaterialShapes`
  (material3 1.5) / `androidx.graphics.shapes.RoundedPolygon`.

## Typographie (Roboto, échelle M3, en sp)
| Style | Taille / interligne | Graisse (emphasized) |
|---|---|---|
| displayLarge | 57 / 64 (tracking −0.25) | Regular (Medium) |
| displayMedium | 45 / 52 | Regular (Medium) |
| displaySmall | 36 / 44 | Regular (**Medium** — titre « Où va-t-on ce soir ? », « Réglages ») |
| headlineLarge | 32 / 40 | Regular (Medium) |
| headlineMedium | 28 / 36 | Regular (Medium — titres d'erreur) |
| headlineSmall | 24 / 32 | Regular (Medium — titres de panneau, monogrammes) |
| titleLarge | 22 / 28 | Regular (Medium — « OpenCTV », « Favoris ») |
| titleMedium | 16 / 24 (0.15) | Medium |
| labelLarge | 14 / 20 (0.1) | Medium (SemiBold) |
| labelMedium | 12 / 16 (0.5) | Medium |
| labelSmall | 11 / 16 (0.5) | Medium |
| bodyLarge | 16 / 24 (0.5) | Regular |
| bodyMedium | 14 / 20 (0.25) | Regular |
| bodySmall | 12 / 16 (0.4) | Regular |

## Focus
- Indicateur M3 : contour **3 dp `secondary`**, à l'extérieur de l'élément (offset 2 dp), forme qui suit
  celle de l'élément. Ne jamais le rogner (pas de clip sur les parents).
  Code : surface `tv-material` (Surface, Button, Card…) → `border = …Defaults.border(focusedBorder =
  focusRingBorder(shape))`, qui suit l'agrandissement au focus ; autre élément → `Modifier.focusRing(shape)`.
- Tuile de favori focalisée : fond `primaryContainer`, textes `onPrimaryContainer`, monogramme qui se
  transforme en cercle `primary` (morph, spring default spatial), élévation légère.
- Bouton / FAB focalisé : variante Focused M3 + indicateur.

## Motion (springs M3 Expressive, masse 1)
- fast spatial : amortissement 0.6, raideur 800 · default spatial : 0.8 / 380 · slow spatial : 0.8 / 200
- fast effects : 1.0 / 3800 · default effects : 1.0 / 1600
- HUD : sortie 200 ms (emphasized accelerate), entrée 250 ms (emphasized decelerate).

## Écrans (node Figma → contenu)
- **Accueil** `6-406` : en-tête (logo cookie 36 dp `primary` + « OpenCTV » titleLarge-emph ; icônes tonales
  Historique et Réglages), titre displaySmall-emph « Où va-t-on ce soir ? », barre de recherche pleine
  largeur 56 dp (icône recherche, placeholder « Rechercher sur DuckDuckGo ou saisir une adresse », icône
  clavier) **focalisée à l'ouverture**, section « Favoris » + badge du nombre, grille 4 colonnes de tuiles
  204 × 96 dp (fond `surfaceContainer`, monogramme 56 dp coloré en container primary/secondary/tertiary,
  nom titleMedium, domaine bodySmall `onSurfaceVariant`), tuile « Ajouter » en pointillés `outlineVariant`
  avec FAB secondaryContainer, aide télécommande (D-pad / OK / Retour) en bas.
  États : **Focus favori** `6-3846`, **Empty** `6-3962` (illustration soft burst `tertiaryContainer`,
  « Aucun favori pour l'instant », Extended FAB `primary` « Ajouter un favori » focalisé).
- **Réglages** `6-5651` : colonne gauche (bouton retour tonal, displaySmall « Réglages », texte « Tout reste
  sur cette TV : aucun compte, aucune donnée envoyée. »), liste groupée à droite (groupes
  `surfaceContainer` Large 16) : Page de démarrage · Moteur de recherche | Masquer l'interface · Vitesse du
  curseur | Bloqueur de pubs (switch) · Listes de filtres. Chaque item : icône, libellé + valeur, chevron.
- **Réglages / Page de démarrage** `6-5821` : panneau latéral droit 380 dp (`surfaceContainerHigh`,
  Extra-large), radios : Écran d'accueil OpenCTV (Par défaut) · Reprendre la dernière page · Un site
  précis (valeur) ; puces « Depuis vos favoris ».
- **Navigation / Mode interface** `6-2872` : toolbar flottante **Vibrant** (`primaryContainer`, Full) en
  haut : Précédent, Suivant, Actualiser, champ d'adresse (`surfaceContainer`, Full, cadenas + domaine,
  « OK pour modifier ») focalisé, Favori, Menu ; snackbar d'aide en bas.
- **Navigation / Mode curseur** `6-2899` : page seule + curseur + snackbar « Mode curseur : OK pour
  cliquer · Retour pour la page précédente » + puce « Maintenir Haut : barre d'adresse ».
- **Navigation / Loading** `6-4421` : toolbar (Actualiser → Arrêter), barre de progression **ondulée**
  sous la toolbar, LoadingIndicator M3 avec conteneur au centre + « Chargement de <site>… ».
- **Navigation / Error** `6-4952` : illustration cookie 12, headlineMedium « Impossible d'atteindre
  <site> », texte, boutons « Réessayer » (filled, focalisé) et « Accueil » (tonal).
- **Sécurité / HTTP — Avertissement** `89-900` : même structure que l'erreur (toolbar, illustration soft burst /
  cookie 12 de 80 dp `tertiaryContainer` + icône **`no_encryption`** 40 dp `onTertiaryContainer`), **pastille de gravité** `tertiaryContainer` /
  `onTertiaryContainer` (icône `warning` 18 dp, « Connexion non chiffrée (HTTP) »), headlineMedium « Cette page n'est
  pas chiffrée », bodyLarge, puis « Retour en sécurité » (filled, **focalisé**) et « Options avancées » (text +
  chevron `expand_more`). `89-1266` **options avancées** : l'illustration disparaît (tient dans 540 dp) ; panneau
  `surfaceContainer` Large 720 dp : bodyMedium, ligne case à cocher (`check_box` / `check_box_outline_blank`,
  « Toujours autoriser <hôte> en HTTP sur cette TV »), bouton outline « Afficher la page (non sécurisé) » avec icône `no_encryption` en tête (18 dp, 8 dp d'écart). Le
  focus va sur la case à l'ouverture, revient sur « Options avancées » à la fermeture.
- **Sécurité / Certificat invalide — Avertissement** `89-1684` : pastille `error` / `onError` (icône `gpp_bad`,
  « Danger · certificat de sécurité invalide »), titre « Ce site n'est peut-être pas celui qu'il prétend être »,
  illustration `gpp_bad` 40 dp `onError` sur `error` (cookie 12) ; carte `surfaceContainer` Medium « Cause » + « Émis pour · valide jusqu'au » : libellé `onSurface` (labelLarge), valeur bodyMedium — **Cause en `error`**, autres valeurs en `onSurfaceVariant`. `89-1771` options : explication
  (réseau local vs site public), case « Faire confiance à ce certificat pour <hôte> » (« S'il change,
  l'avertissement reviendra »), bouton outline en `error` (texte, contour, icône) « Continuer malgré tout (dangereux) » avec icône `warning` en tête.
- **Sécurité / Barre** `89-1887` (HTTP autorisé : « Non sécurisé », `tertiary`, icône `no_encryption`) et
  `89-1950` (certificat de confiance : « Certificat non vérifié », `error`, icône `gpp_bad`) **remplace** le
  cadenas (icône seule, à gauche du domaine) et le libellé coloré remplace « OK pour modifier » (à droite), + snackbar d'information à l'ouverture.
  Code : la paire icône + couleur d'accent (HTTP : `no_encryption` `tertiary` ; certificat : `gpp_bad` `error`) vient
  d'une source unique, `core/designsystem/InsecurityMark`, partagée par la barre, l'illustration d'avertissement et
  Réglages › Exceptions de sécurité.
- **Réglages / Exceptions de sécurité** `89-2013` : ligne « Exceptions de sécurité » (nombre) → panneau latéral
  380 dp listant hôte, type (HTTP / certificat + empreinte abrégée) ; OK retire l'exception (le focus passe à la
  ligne suivante, au bouton Fermer s'il n'en reste plus) ; icône de tête `no_encryption` `tertiary` (HTTP) ou
  `gpp_bad` `error` (certificat), icône de fin `delete` ; Retour : panneau → Réglages.
- **Navigation / Plein écran** `6-6283` : page seule (HUD masqué) — note `6-6294`.
- **Menu navigateur** `94:2416` (remplace `6-3518`) : panneau latéral droit, scrim, groupes **Vibrant**
  (`tertiaryContainer`, Large 16) : [Bloqueur de pubs (switch, « 12 requêtes bloquées sur ce site »), Ajouter aux
  favoris, Zoom de la page → « 100 % » (icône `zoom_in`), Navigation → « Curseur » / « Éléments » (icône
  `keyboard_tab`)] [Accueil, Historique (icône `history`)] ; « Retour pour fermer le menu ». « Actualiser la page »
  (dans la barre), « Moteur de recherche » et « Vitesse du curseur » (dans Réglages) n'y sont plus : le menu tient en
  540 dp **sans défiler** (colonne défilable gardée en filet de sécurité). Dimensions réelles (`ListGroupDensity.Compact`) :
  zone sûre 486 dp − marge verticale du panneau 2 × 16 = 454 dp ; en-tête 56 (titre + site sur une ligne) ; groupe 1 =
  4 + 64 (Bloqueur, deux lignes) + 2 + 64 (Favoris, deux lignes) + 2 + 48 (Zoom, valeur en fin de ligne) + 2 + 48
  (Navigation) + 4 = 238 ; 8 d'écart ; groupe 2 = 4 + 48 + 2 + 48 + 4 = 106 ; marge de colonne 8 (anneau de focus) ;
  aide 20 ⇒ 436 dp. Compteur du bloqueur raccourci pour tenir sur une ligne à côté de l'interrupteur : « 12 bloquées sur
  ce site » (0 et 1 au singulier). Navigation : OK bascule le mode pour le site ouvert (mémorisé), le menu se ferme, snackbar
  « Navigation par éléments pour <hôte> » / « Navigation au curseur pour <hôte> ».
- **Menu / Moteur de recherche** `6-5331` : radios DuckDuckGo (Par défaut), Google, Bing, Qwant (Sans
  pistage), Ecosia, Brave Search (Sans pistage).
- **Zoom des pages** `93:1436` (menu `93:1437`, sous-menu `93:1650`, Réglages `93:1769`, rendus `93:2058` /
  `93:2162`, confirmation, note `93:2372`). Niveaux : **100 % · 960 px (défaut)**, 75 % · 1280 px, 67 % ·
  1440 px, 50 % · 1920 px « comme un écran de PC » : largeur vue par la page (`window.innerWidth`) = 960 / zoom
  (la TV affiche en 1080p avec un pixel ratio de 2). Choisi **site par site** (hôte exact), mémorisé sur la TV.
  - **Menu / Zoom de la page** : sous-menu (même panneau que « Moteur de recherche »), titre « Zoom de la page »,
    sous-titre « Mémorisé pour <hôte> », radios (libellé + largeur, « Par défaut » en texte de fin sur le niveau
    des Réglages), note « Le niveau « Par défaut » suit Réglages › Zoom des pages. Un autre niveau est mémorisé
    pour ce site uniquement. », aide « OK pour choisir · Retour pour revenir au menu ». Choisir un niveau
    l'applique tout de suite (sans recharger), ferme le menu et affiche le snackbar « Zoom 50 % mémorisé pour
    <hôte> » ; choisir le niveau par défaut retire le zoom du site (« Zoom 100 % (par défaut) pour <hôte> »).
  - **Réglages / Zoom des pages** : ligne dans le 2e groupe après « Vitesse du curseur » (icône `zoom_in`,
    valeur « 100 % · 960 px »), panneau latéral 380 dp (comme « Page de démarrage ») : « Taille des sites web par
    défaut », les 4 radios, puis « Sites avec leur propre zoom · OK pour le retirer » et une puce
    (`hôte · niveau`) par site ; OK sur une puce retire le zoom du site (le focus passe à la puce suivante, au
    niveau choisi s'il n'en reste plus).
  - Seule la page change de taille : barre, menus, curseur et clavier gardent la leur ; le curseur garde sa
    position à l'écran.
- **Navigation, historique & données** `94:2415` (note `95:3779`) :
  - **Mode éléments** `95:2598` : page seule, pas de curseur ; l'élément focalisé porte le contour de focus **dessiné par
    la page** (`:focus-visible` du site, aucun style injecté) ; barre basse : snackbar « Mode éléments : Haut/Bas pour
    changer d'élément · OK pour activer » + puce « Maintenir Haut : barre d'adresse ». Haut/Gauche = élément
    précédent (Maj+Tab), Bas/Droite = suivant (Tab), OK = Entrée.
  - **Historique** `95:2623` : même structure que Réglages — colonne gauche (bouton retour tonal, displaySmall
    « Historique », « Pages visitées sur cette TV. Rien n'est envoyé ailleurs. », bouton outline `error` « Tout
    effacer » (icône `delete_sweep`), aide en bas « OK : ouvrir · Droite : retirer de l'historique ») ; liste à droite :
    libellé de jour (« Aujourd'hui », « Hier », puis date) en `onSurfaceVariant`, groupe `surfaceContainer` Large 16 par
    jour ; ligne : icône `language`, titre (label), « domaine · HH:mm » (supporting), icône de fin `delete`
    focalisable (Droite depuis la ligne). Focus initial : première entrée.
  - **Historique / Empty** `95:2779` : illustration soft burst `tertiaryContainer` 120 dp + icône `history`,
    « Aucune page visitée », « Les sites que vous ouvrez sur cette TV apparaîtront ici. » ; pas de « Tout effacer » ;
    focus sur le bouton retour ; aide « Retour pour revenir à l'accueil ».
  - **Historique / Confirmation** `95:2843` : scrim + dialogue `surfaceContainerHigh` Extra-large 28, 420 dp, titre
    headlineSmall « Effacer tout l'historique ? », bodyMedium « Les N pages visitées sur cette TV seront retirées.
    Favoris, réglages et cookies sont conservés. », boutons texte « Annuler » (**focalisé**) et « Effacer » (`error`).
  - **Réglages** `95:2988` (liste défilée) : 2e groupe + « Zoom des pages » ; 3e groupe : Bloqueur de pubs (switch),
    **Bandeaux de cookies** (switch, icône `cookie`, « Masqués · vous refusez par défaut », activé par défaut), Listes
    de filtres (« EasyList, EasyList Cookie · il y a N jours ») ; 4e groupe : Exceptions de sécurité (icône `lock`),
    **Effacer les données de navigation** (icône `delete_sweep`, « Historique, recherches, cookies, cache »).
  - **Réglages / Effacer les données** `95:3271` : panneau latéral 380 dp, titre « Effacer les données », sous-titre
    « Sur cette TV uniquement », cases (`check_box` / `check_box_outline_blank`, `primary`) cochées par défaut :
    Historique (« N pages »), Recherches récentes (« N recherches »), Cookies et données de sites (« Vous serez
    déconnecté des sites »), Cache (« Images et fichiers temporaires ») ; note « Favoris, réglages, exceptions de
    sécurité et zooms sont conservés. » ; bouton outline `error` « Effacer les données » (icône `delete_sweep`) ;
    aide « OK pour cocher · Retour pour revenir aux réglages ». Focus initial : première case.
  - **Confirmation** `95:3593` : snackbar « Données de navigation effacées », focus rendu à la ligne des Réglages.
- **Recherche / Saisie** `6-3725` : barre (retour, texte saisi, effacer) bord `primary`, liste de
  suggestions `surfaceContainer` (favori = étoile « Favori · ouvrir », recherche récente = horloge), clavier
  système en bas.
- **Curseur** `6-6368` (états), `6-6503` (prototype), `6-6521` (motion) : soft burst 24 dp `primary`,
  contour 3 dp `onPrimary`, halo `primary` 28 % ; survol cliquable → cercle 30 dp `tertiary` ; appui →
  contraction 0.8 + onde ; survol texte → pilule 8 × 28 ; bord → flèche + lueur ; inactif → fondu.
