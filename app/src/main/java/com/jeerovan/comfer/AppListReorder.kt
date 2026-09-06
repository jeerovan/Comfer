package com.jeerovan.comfer

internal fun <T> moveListItem(items: List<T>, fromIndex: Int, toIndex: Int): List<T> {
    if (fromIndex !in items.indices || toIndex !in items.indices || fromIndex == toIndex) {
        return items
    }
    return items.toMutableList().apply { add(toIndex, removeAt(fromIndex)) }
}

internal fun <T> moveListItemByKey(
    items: List<T>, fromKey: String, toKey: String, keyOf: (T) -> String,
): List<T> = moveListItem(
    items, items.indexOfFirst { keyOf(it) == fromKey }, items.indexOfFirst { keyOf(it) == toKey },
)
