package com.jeerovan.comfer

import android.graphics.drawable.Drawable

object AppIconCache {
    private val iconCache = mutableMapOf<String, Drawable>()
    fun getIcon(packageName: String): Drawable? {
        return iconCache[packageName]
    }

    fun cacheIcon(packageName: String, icon: Drawable) {
        iconCache[packageName] = icon
    }
}