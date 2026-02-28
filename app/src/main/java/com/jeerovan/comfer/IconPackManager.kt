package com.jeerovan.comfer

import android.content.ComponentName
import android.content.Context
import android.content.res.Resources
import android.graphics.drawable.Drawable
import org.xmlpull.v1.XmlPullParser

object IconPackManager {
    private var iconPackPackage: String? = null
    private var iconPackRes: Resources? = null
    // Map ComponentName (String) -> Drawable Name (String)
    private val appFilterMap = mutableMapOf<String, String>()

    // Call this when the user selects an icon pack
    fun loadIconPack(context: Context, packageName: String) {
        if(iconPackPackage != null && iconPackPackage == packageName){
            return
        }
        iconPackPackage = packageName
        appFilterMap.clear()
        try {
            val pm = context.packageManager
            iconPackRes = pm.getResourcesForApplication(packageName)
            val appFilterId = iconPackRes?.getIdentifier("appfilter", "xml", packageName) ?: 0

            if (appFilterId != 0) {
                val xpp = iconPackRes?.getXml(appFilterId)
                if (xpp != null) {
                    var eventType = xpp.eventType
                    while (eventType != XmlPullParser.END_DOCUMENT) {
                        if (eventType == XmlPullParser.START_TAG && xpp.name == "item") {
                            val component = xpp.getAttributeValue(null, "component")
                            val drawableName = xpp.getAttributeValue(null, "drawable")
                            if (component != null && drawableName != null) {
                                appFilterMap[component] = drawableName
                            }
                        }
                        eventType = xpp.next()
                    }
                }
            }
            PreferenceManager.increaseAppListVersion(context)// will reload app list
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun unloadIconPack(context: Context){
        iconPackPackage = null
        appFilterMap.clear()
        PreferenceManager.increaseAppListVersion(context)// will reload app list
    }

    fun getCustomIcon(context: Context, componentName: ComponentName): Drawable? {
        // Format: ComponentInfo{package/class}
        val componentKey = "ComponentInfo{${componentName.packageName}/${componentName.className}}"
        val drawableName = appFilterMap[componentKey] ?: return null

        return try {
            val resId = iconPackRes?.getIdentifier(drawableName, "drawable", iconPackPackage) ?: 0
            if (resId != 0) iconPackRes?.getDrawable(resId, null) else null
        } catch (e: Exception) {
            null
        }
    }
}
