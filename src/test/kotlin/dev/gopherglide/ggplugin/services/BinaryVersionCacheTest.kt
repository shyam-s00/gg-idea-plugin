package dev.gopherglide.ggplugin.services

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BinaryVersionCacheTest {
    private var spawns = 0
    private val stamps = mutableMapOf<String, BinaryVersionCache.Stamp?>("/bin/gg" to BinaryVersionCache.Stamp(100, 10))
    private val outputs = mutableMapOf<String, String?>("/bin/gg" to "gg (Gopher Glide)\n  Version:    1.3.0")

    private val cache = BinaryVersionCache(
        readOutput = { spawns++; outputs[it] },
        stampOf = { stamps[it] }
    )

    @Test
    fun repeatedLookupsOfAnUnchangedBinarySpawnOnce() {
        repeat(5) { assertEquals(GopherGlideVersion(1, 3, 0), cache.versionOf("/bin/gg")) }
        assertEquals(1, spawns)
    }

    @Test
    fun aNewModifiedTimeReadsTheVersionAgain() {
        cache.versionOf("/bin/gg")
        stamps["/bin/gg"] = BinaryVersionCache.Stamp(200, 10)
        outputs["/bin/gg"] = "Version: 1.4.0"
        assertEquals(GopherGlideVersion(1, 4, 0), cache.versionOf("/bin/gg"))
        assertEquals(2, spawns)
    }

    @Test
    fun aNewSizeAloneReadsTheVersionAgain() {
        cache.versionOf("/bin/gg")
        stamps["/bin/gg"] = BinaryVersionCache.Stamp(100, 11)
        cache.versionOf("/bin/gg")
        assertEquals(2, spawns)
    }

    @Test
    fun aMissingFileGivesNullWithoutSpawning() {
        assertNull(cache.versionOf("/bin/missing"))
        assertEquals(0, spawns)
    }

    @Test
    fun eachPathIsCachedSeparately() {
        stamps["/other/gg"] = BinaryVersionCache.Stamp(1, 1)
        outputs["/other/gg"] = "Version: 1.2.0"
        assertEquals(GopherGlideVersion(1, 3, 0), cache.versionOf("/bin/gg"))
        assertEquals(GopherGlideVersion(1, 2, 0), cache.versionOf("/other/gg"))
        assertEquals(2, spawns)
    }

    @Test
    fun anUnreadableVersionIsRememberedUntilTheFileChanges() {
        outputs["/bin/gg"] = null
        assertNull(cache.versionOf("/bin/gg"))
        assertNull(cache.versionOf("/bin/gg"))
        assertEquals(1, spawns)
    }
}
