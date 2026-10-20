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
- **Navigation / Plein écran** `6-6283` : page seule (HUD masqué) — note `6-6294`.
- **Menu navigateur** `6-3518` : panneau latéral droit, scrim, groupes **Vibrant** (`tertiaryContainer`,
  Large 16) : [Bloqueur de pubs (switch, « 12 requêtes bloquées sur ce site »), Ajouter aux favoris]
  [Actualiser la page, Accueil] [Moteur de recherche → valeur, Vitesse du curseur → valeur] ;
  « Retour pour fermer le menu ».
- **Menu / Moteur de recherche** `6-5331` : radios DuckDuckGo (Par défaut), Google, Bing, Qwant (Sans
  pistage), Ecosia, Brave Search (Sans pistage).
- **Recherche / Saisie** `6-3725` : barre (retour, texte saisi, effacer) bord `primary`, liste de
  suggestions `surfaceContainer` (favori = étoile « Favori · ouvrir », recherche récente = horloge), clavier
  système en bas.
- **Curseur** `6-6368` (états), `6-6503` (prototype), `6-6521` (motion) : soft burst 24 dp `primary`,
  contour 3 dp `onPrimary`, halo `primary` 28 % ; survol cliquable → cercle 30 dp `tertiary` ; appui →
  contraction 0.8 + onde ; survol texte → pilule 8 × 28 ; bord → flèche + lueur ; inactif → fondu.
