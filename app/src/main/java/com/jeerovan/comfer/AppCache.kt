package com.jeerovan.comfer

import java.util.concurrent.ConcurrentHashMap

/**
 * A thread-safe singleton cache for storing String and Int values in memory.
 * This cache is volatile and will be cleared when the application process is terminated.
 */
object AppCache {

    private val cache: MutableMap<String, Any> = ConcurrentHashMap()

    /**
     * Caches a value associated with a specific key.
     * The value can be of any type.
     *
     * @param key The key to store the value against.
     * @param value The value to be cached.
     */
    fun set(key: String, value: Any) {
        cache[key] = value
    }

    /**
     * Retrieves a cached value for a given key.
     *
     * @param key The key of the value to retrieve.
     * @return The cached value, or null if the key is not found.
     */
    fun get(key: String): Any? {
        return cache[key]
    }

    /**
     * Removes a specific entry from the cache.
     *
     * @param key The key of the entry to remove.
     */
    fun remove(key: String) {
        cache.remove(key)
    }

    /**
     * Removes all entries from the cache, freeing up memory.
     */
    fun clear() {
        cache.clear()
    }
}