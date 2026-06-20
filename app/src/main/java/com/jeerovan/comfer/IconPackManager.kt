package com.jeerovan.comfer

import android.content.ComponentName
import android.content.Context
import android.content.res.Resources
import android.graphics.drawable.Drawable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser

object IconPackManager {

    // Encapsulate state immutably to prevent race conditions during reads
    private data class IconPackState(
        val packageName: String,
        val resources: Resources,
        val appFilterMap: Map<String, String>
    )

    @Volatile
    private var currentState: IconPackState? = null

    // Now a suspend function enforcing Dispatchers.IO
    suspend fun loadIconPack(context: Context, packageName: String) = withContext(Dispatchers.IO) {
        val current = currentState
        if (current != null && current.packageName == packageName) {
            return@withContext
        }

        try {
            val pm = context.packageManager
            // BINDER CALL: Moved off Main thread
            val res = pm.getResourcesForApplication(packageName)
            val appFilterId = res.getIdentifier("appfilter", "xml", packageName)

            // Parse into a local map first, so we don't mutate state while others are reading
            val newMap = mutableMapOf<String, String>()

            if (appFilterId != 0) {
                val xpp = res.getXml(appFilterId) // HEAVY DISK/XML I/O
                var eventType = xpp.eventType
                while (eventType != XmlPullParser.END_DOCUMENT) {
                    if (eventType == XmlPullParser.START_TAG && xpp.name == "item") {
                        val component = xpp.getAttributeValue(null, "component")
                        val drawableName = xpp.getAttributeValue(null, "drawable")
                        if (component != null && drawableName != null) {
                            newMap[component] = drawableName
                        }
                    }
                    eventType = xpp.next()
                }
            }

            // Atomically swap the state. Background threads reading icons instantly see the new map.
            currentState = IconPackState(packageName, res, newMap)

            withContext(Dispatchers.Main) {
                PreferenceManager.increaseAppListVersion(context)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun unloadIconPack(context: Context) {
        currentState = null
        PreferenceManager.increaseAppListVersion(context)
    }

    suspend fun getCustomIcon(context: Context, componentName: ComponentName): Drawable? = withContext(Dispatchers.IO) {
        val state = currentState ?: return@withContext null

        val componentKey = "ComponentInfo{${componentName.packageName}/${componentName.className}}"
        val drawableName = state.appFilterMap[componentKey] ?: return@withContext null

        try {
            val resId = state.resources.getIdentifier(drawableName, "drawable", state.packageName)
            if (resId != 0) state.resources.getDrawable(resId, null) else null
        } catch (e: Exception) {
            null
        }
    }
}