package com.openctv.browser.features.cursor.domain

enum class PointerAction { Down, Up }

/**
 * Appui ou relâchement de clic à injecter dans la page, déjà en **coordonnées de la WebView** (origine = coin
 * haut gauche de la vue). La vue y ajoute l'horloge et fabrique le `MotionEvent`. Le survol, lui, passe par
 * `CursorController.pageX/pageY` (une fois par frame, sans allocation).
 */
data class PointerEvent(val action: PointerAction, val x: Float, val y: Float)

/** Ce que la page dit du point survolé, d'après l'icône de pointeur qu'elle demande (cf. `PointerKind`). */
enum class HoverTarget { None, Clickable, Text }
