package dev.gopherglide.ggplugin.services

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BinaryStatusCacheTest {
    private var now = 1_000_000L
    private var lookups = 0
    private var inspected = mutableListOf<String>()
    private var release: GopherGlideVersion? = GopherGlideVersion(1, 3, 0)
    private var path: String? = "/bin/gg"

    private fun cache() = BinaryStatusCache(
        resolvePath = { path },
        inspect = { p ->
            inspected += p
            InstalledBinary(p, GopherGlideVersion(1, 2, 0), InstalledBinary.Source.SYSTEM)
        },
        fetchLatest = { lookups++; release },
        nowMillis = { now },
        foundTtlMillis = 1000,
        missTtlMillis = 100
    )

    @Test
    fun `release lookup is made once while it is fresh`() {
        val cache = cache()
        repeat(3) { cache.load() }
        assertEquals(1, lookups)
    }

    @Test
    fun `release lookup is repeated after the found lifetime`() {
        val cache = cache()
        cache.load()
        now += 1000
        cache.load()
        assertEquals(2, lookups)
    }

    @Test
    fun `failed lookup is retried sooner than a found one`() {
        release = null
        val cache = cache()
        cache.load()
        now += 99
        cache.load()
        assertEquals(1, lookups)
        now += 1
        cache.load()
        assertEquals(2, lookups)
    }

    @Test
    fun `binary is read on every load so a changed path is seen`() {
        val cache = cache()
        assertEquals("/bin/gg", cache.load().installed?.path)
        path = "/opt/gg"
        assertEquals("/opt/gg", cache.load().installed?.path)
        assertEquals(listOf("/bin/gg", "/opt/gg"), inspected)
    }

    @Test
    fun `no binary means no installed value and no inspection`() {
        path = null
        val snapshot = cache().load()
        assertNull(snapshot.installed)
        assertEquals(emptyList<String>(), inspected)
        assertEquals(GopherGlideVersion(1, 3, 0), snapshot.latest)
    }

    @Test
    fun `explicit path is inspected instead of the resolved one`() {
        cache().load("/run/gg")
        assertEquals(listOf("/run/gg"), inspected)
    }

    @Test
    fun `listeners are told about changes until removed`() {
        val cache = cache()
        var calls = 0
        val remove = cache.addListener { calls++ }
        cache.notifyChanged()
        remove()
        cache.notifyChanged()
        assertEquals(1, calls)
    }
}
