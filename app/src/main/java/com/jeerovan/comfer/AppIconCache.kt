package com.jeerovan.comfer

import android.graphics.drawable.Drawable
import android.util.LruCache

object AppIconCache {
    private const val CACHE_SIZE = 100
    
    private val iconCache = object : LruCache<String, Drawable>(CACHE_SIZE) {
        override fun sizeOf(key: String, value: Drawable): Int {
            return 1
        }
    }

    fun getIcon(packageName: String): Drawable? {
        return iconCache.get(packageName)
    }

    fun cacheIcon(packageName: String, icon: Drawable) {
        iconCache.put(packageName, icon)
    }
    
    fun clearCache() {
        iconCache.evictAll()
    }
}
