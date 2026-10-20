package com.openctv.browser.core.designsystem

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/** Échelle de formes M3 (voir .claude/docs/design.md). */
object OpenCtvShapes {
    val ExtraSmall = RoundedCornerShape(4.dp)
    val Small = RoundedCornerShape(8.dp)
    val Medium = RoundedCornerShape(12.dp)
    val Large = RoundedCornerShape(16.dp)
    val LargeIncreased = RoundedCornerShape(20.dp)
    val ExtraLarge = RoundedCornerShape(28.dp)
    val Full = CircleShape
}
