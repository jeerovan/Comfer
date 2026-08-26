package com.jeerovan.comfer

internal fun clampToAvailableRange(value: Float, minimum: Float, maximum: Float): Float {
    return value.coerceIn(minimum, maximum.coerceAtLeast(minimum))
}

internal fun maximumWidgetGridStart(gridColumns: Int, spanColumns: Int): Int {
    return (gridColumns - spanColumns).coerceAtLeast(0)
}
