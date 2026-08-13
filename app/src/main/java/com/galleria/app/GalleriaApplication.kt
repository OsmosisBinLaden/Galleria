package com.galleria.app

import android.app.Application
import android.graphics.Bitmap
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.memory.MemoryCache
import coil3.request.bitmapConfig
import coil3.request.crossfade

/**
 * Application class configuring global Coil 3 ImageLoader settings
 * optimized for smooth 120 Hz gallery scrolling.
 */
class GalleriaApplication : Application(), SingletonImageLoader.Factory {

    override fun newImageLoader(context: PlatformContext): ImageLoader {
        return ImageLoader.Builder(context)
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context, 0.25)
                    .build()
            }
            .bitmapConfig(Bitmap.Config.HARDWARE)
            .crossfade(true)
            .build()
    }
}
