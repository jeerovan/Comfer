package com.jeerovan.comfer

import android.content.res.Resources
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.LruCache

object AppIconCache {
    private const val CACHE_SIZE_KIB = 8 * 1024

    data class Key(
        val packageName: String,
        val componentName: String,
        val userHash: Int,
    )

    private data class Entry(
        val constantState: Drawable.ConstantState,
        val sizeKib: Int,
    )

    private val iconCache = object : LruCache<Key, Entry>(CACHE_SIZE_KIB) {
        override fun sizeOf(key: Key, value: Entry): Int {
            return value.sizeKib
        }
    }

    fun getIcon(key: Key, resources: Resources): Drawable? {
        return iconCache.get(key)?.constantState?.newDrawable(resources)
    }

    fun cacheIcon(key: Key, icon: Drawable) {
        val constantState = icon.constantState ?: return
        iconCache.put(key, Entry(constantState, estimateSizeKib(icon)))
    }

    fun clearCache() {
        iconCache.evictAll()
    }

    fun removePackage(packageName: String, userHash: Int) {
        iconCache.snapshot().keys
            .filter { it.packageName == packageName && it.userHash == userHash }
            .forEach(iconCache::remove)
    }

    private fun estimateSizeKib(drawable: Drawable): Int {
        val bytes = if (
            android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O &&
            drawable is AdaptiveIconDrawable
        ) {
                estimateSizeKib(drawable.background) * 1024 +
                    estimateSizeKib(drawable.foreground) * 1024
        } else if (drawable is BitmapDrawable) {
            drawable.bitmap.allocationByteCount
        } else {
            drawable.intrinsicWidth.coerceAtLeast(1) *
                drawable.intrinsicHeight.coerceAtLeast(1) * 4
        }
        return ((bytes + 1023) / 1024).coerceAtLeast(1)
    }
}
