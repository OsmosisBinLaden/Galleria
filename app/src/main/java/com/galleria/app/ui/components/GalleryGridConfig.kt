package com.galleria.app.ui.components

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Reusable layout configuration for gallery grids.
 */
@Immutable
data class GalleryGridConfig(
    val columns: Int = 3,
    val spacing: Dp = 2.dp,
    val aspectRatio: Float = 1.0f
)
