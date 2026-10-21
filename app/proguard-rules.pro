# R8 : le build release est minifié, obfusqué et réduit (proguard-android-optimize.txt) ; l'obfuscation reste active.
#
# Aucune règle `-keep` projet n'est nécessaire pour l'instant, vérifié sur la TV (accueil, favoris Room, saisie, navigation
# + curseur, plein écran, menu + bloqueur, réglages DataStore, mise à jour des listes OkHttp) :
#  - Room : les règles consommatrices de room-runtime gardent `*_Impl` (instancié par réflexion) ; le DAO et les entités
#    sont référencés par le code généré.
#  - Koin : les modules sont déclarés en Kotlin (lambdas), sans réflexion sur nos classes.
#  - DataStore (préférences) : clés `stringPreferencesKey("...")` et identifiants (`SearchEngine.id`...) sont des chaînes
#    persistées, jamais des noms d'enum ou de champ ; les règles de la lib gardent ses champs protobuf.
#    Idem pour les exceptions de sécurité (`core/security`) : DataStore + `SiteExceptionCodec` en chaînes, aucun nom d'enum persisté.
#  - OkHttp / Okio / coroutines : règles consommatrices fournies par les bibliothèques.
#  - WebView : `PointerAwareWebView`, `SecureWebViewClient`, `SecureWebChromeClient` sont instanciés en Kotlin par la
#    fabrique ; leurs surcharges de méthodes du framework sont conservées par R8. Aucune interface JavaScript n'est exposée.
#  - `MediaCommand` : scripts JavaScript constants (chaînes), aucune réflexion.
# Ajouter ici une règle seulement avec le crash qui la justifie.

# Traces de crash lisibles avec `mapping.txt` (sans désactiver l'obfuscation).
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
