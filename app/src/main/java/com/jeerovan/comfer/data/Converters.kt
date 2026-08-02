package com.jeerovan.comfer.data

import androidx.room.TypeConverter
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Type converters used by Room entities. Lists of strings are stored as JSON
 * so they can live in a normal SQLite TEXT column.
 */
class Converters {
    private val json = Json { ignoreUnknownKeys = true }

    @TypeConverter
    fun fromStringList(value: List<String>): String = json.encodeToString(value)

    @TypeConverter
    fun toStringList(value: String): List<String> =
        try {
            json.decodeFromString(value)
        } catch (e: Exception) {
            emptyList()
        }
}
