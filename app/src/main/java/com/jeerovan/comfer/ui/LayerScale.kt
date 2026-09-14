package com.jeerovan.comfer.ui

internal fun layerScale(renderedSize: Float, measuredSize: Float): Float {
    if (!renderedSize.isFinite() || !measuredSize.isFinite() || renderedSize <= 0f || measuredSize <= 0f) return 1f
    return (renderedSize / measuredSize).takeIf { it.isFinite() } ?: 1f
}
