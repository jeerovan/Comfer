package com.jeerovan.comfer

import kotlinx.serialization.SerializationException
import org.junit.Assert.assertEquals
import org.junit.Test

class PrefMigratorTest {
    @Test
    fun validFolderFixturePreservesIdsTitlesAndPackages() {
        val folders = parseLegacyFolders(
            """{"work":{"title":"Work","packages":["com.example.one","com.example.\"quoted"]}}""",
        )

        assertEquals(1, folders.size)
        assertEquals("work", folders.single().id)
        assertEquals("Work", folders.single().title)
        assertEquals(
            """["com.example.one","com.example.\"quoted"]""",
            folders.single().packagesJson,
        )
    }

    @Test(expected = SerializationException::class)
    fun malformedFolderFixtureAbortsInsteadOfSilentlyDroppingData() {
        parseLegacyFolders("""{"work":{"title":"Work","packages":not-json}}""")
    }
}
