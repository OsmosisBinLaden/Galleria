package com.galleria.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.itemContentType
import androidx.paging.compose.itemKey
import coil3.compose.AsyncImage
import com.galleria.app.data.model.MediaItem

/**
 * Reusable paged media grid component supporting stable keys, contentType,
 * customizable GalleryGridConfig, and click/long-click callbacks.
 */
@Composable
fun PagedMediaGrid(
    lazyPagingItems: LazyPagingItems<MediaItem>,
    modifier: Modifier = Modifier,
    gridConfig: GalleryGridConfig = GalleryGridConfig(),
    contentPadding: PaddingValues = PaddingValues(gridConfig.spacing),
    onPhotoClick: ((MediaItem) -> Unit)? = null,
    onPhotoLongClick: ((MediaItem) -> Unit)? = null
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(gridConfig.columns),
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(gridConfig.spacing),
        verticalArrangement = Arrangement.spacedBy(gridConfig.spacing)
    ) {
        items(
            count = lazyPagingItems.itemCount,
            key = lazyPagingItems.itemKey { it.id },
            contentType = lazyPagingItems.itemContentType { "photo" }
        ) { index ->
            val photo = lazyPagingItems[index]
            if (photo != null) {
                MediaTile(
                    photo = photo,
                    aspectRatio = gridConfig.aspectRatio,
                    onClick = onPhotoClick,
                    onLongClick = onPhotoLongClick
                )
            }
        }
    }
}

@Composable
private fun MediaTile(
    photo: MediaItem,
    aspectRatio: Float,
    onClick: ((MediaItem) -> Unit)?,
    onLongClick: ((MediaItem) -> Unit)?,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(aspectRatio)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(enabled = onClick != null) { onClick?.invoke(photo) }
    ) {
        AsyncImage(
            model = photo.contentUri,
            contentDescription = photo.displayName,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
    }
}
