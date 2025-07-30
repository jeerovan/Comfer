package com.jeerovan.comfer

import android.graphics.drawable.Drawable

object AppIconCache {
    private val iconCache = mutableMapOf<String, Drawable>()
    private val colorCache = mutableMapOf<String,Int>()
    fun getIcon(packageName: String): Drawable? {
        return iconCache[packageName]
    }

    fun cacheIcon(packageName: String, icon: Drawable) {
        iconCache[packageName] = icon
    }

    fun getColor(packageName: String): Int? {
        return colorCache[packageName]
    }

    fun cacheColor(packageName: String, color: Int) {
        colorCache[packageName] = color
    }

    fun invalidateCache(packageName: String) {
        iconCache.remove(packageName)
    }
}