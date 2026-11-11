package com.jeerovan.comfer

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.util.Log
import android.widget.RemoteViews

/** Keep a provider's broken collection from taking down Home during measurement. */
internal class RecoverableWidgetHost(context: Context, hostId: Int) : AppWidgetHost(context, hostId) {
    override fun onCreateView(context: Context, appWidgetId: Int, appWidget: AppWidgetProviderInfo): AppWidgetHostView =
        RecoverableWidgetHostView(context)
}

private class RecoverableWidgetHostView(context: Context) : AppWidgetHostView(context) {
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        try {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        } catch (error: ArrayIndexOutOfBoundsException) {
            // Limit containment to the observed framework recycler failure. Do not
            // hide unrelated application bugs or change provider update ordering.
            if (error.stackTrace.none {
                    it.className == "android.widget.AbsListView\$RecycleBin" &&
                        it.methodName == "addScrapView"
                }) throw error
            Log.w("ComferWidgetHost", "Widget collection failed measurement; showing error view", error)
            // Like AOSP Launcher3's switchToErrorView, layout 0 asks the framework
            // to replace content AND reset its internal view mode. Removing children
            // ourselves would leave stale reapply state and break subsequent updates.
            // Keep the binding and placement; the next valid RemoteViews can recover.
            super.updateAppWidget(RemoteViews(context.packageName, 0))
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        }
    }
}
