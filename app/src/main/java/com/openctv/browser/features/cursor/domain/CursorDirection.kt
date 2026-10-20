package com.openctv.browser.features.cursor.domain

/** Les quatre flèches du D-pad. [bit] sert à les tenir dans un masque (aucune allocation par frame). */
enum class CursorDirection(val dx: Int, val dy: Int, internal val bit: Int) {
    Left(-1, 0, 1),
    Right(1, 0, 2),
    Up(0, -1, 4),
    Down(0, 1, 8),
}
