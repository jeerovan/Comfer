package com.jeerovan.comfer.spatial

import androidx.compose.runtime.*

internal fun spatialTestContext(context: android.content.Context, tag: String): android.content.Context {
    val configuration = android.content.res.Configuration(context.resources.configuration)
    configuration.setLocale(java.util.Locale.forLanguageTag(tag))
    return context.createConfigurationContext(configuration)
}

@Composable
internal fun SpatialTestLocale(tag: String, content: @Composable () -> Unit) {
    val base = androidx.compose.ui.platform.LocalContext.current
    val context = remember(base, tag) { spatialTestContext(base, tag) }
    CompositionLocalProvider(
        androidx.compose.ui.platform.LocalContext provides context,
        androidx.compose.ui.platform.LocalResources provides context.resources,
        androidx.compose.ui.platform.LocalConfiguration provides context.resources.configuration,
        androidx.compose.ui.platform.LocalLayoutDirection provides
            if (context.resources.configuration.layoutDirection == 1)
                androidx.compose.ui.unit.LayoutDirection.Rtl else androidx.compose.ui.unit.LayoutDirection.Ltr,
        content = content,
    )
}
