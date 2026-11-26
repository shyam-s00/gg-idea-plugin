package dev.gopherglide.ggplugin.mcp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HttpHostsTest {
    @Test
    fun everyRequestsHostIsCollectedWithItsPort() {
        val scan = HttpHosts.scan(
            """
            GET http://127.0.0.1:18099/ok

            ###

            POST https://api.example.com/orders HTTP/1.1
            Content-Type: application/json

            {"x": 1}
            """.trimIndent()
        )
        assertEquals(setOf("127.0.0.1:18099", "api.example.com"), scan.hosts)
        assertFalse(scan.unresolved)
    }

    @Test
    fun aBareUrlWithoutAMethodCounts() {
        assertEquals(setOf("svc.test:9"), HttpHosts.scan("http://svc.test:9/health").hosts)
    }

    @Test
    fun headersAndBodiesAreNotMistakenForRequests() {
        val scan = HttpHosts.scan("GET http://a.test/x\nHost: http://not-a-request.test\nAccept: */*\n\nhttps://body.test/ignored")
        assertEquals(setOf("a.test"), scan.hosts)
    }

    @Test
    fun commentsAndSeparatorLabelsAreSkipped() {
        val scan = HttpHosts.scan("# GET http://commented.test\n// another\n### second request\n\nGET http://real.test/y")
        assertEquals(setOf("real.test"), scan.hosts)
        assertFalse(scan.unresolved)
    }

    @Test
    fun aHostThatIsNotLiteralMakesTheScanUnresolved() {
        assertTrue(HttpHosts.scan("GET {{baseUrl}}/users").unresolved)
        assertTrue(HttpHosts.scan("GET /relative/path").unresolved)
    }

    @Test
    fun userInfoIsNotPartOfTheHost() {
        assertEquals(setOf("h.test"), HttpHosts.scan("GET http://user:pw@h.test/x").hosts)
    }

    @Test
    fun anUnresolvedFileMeansNoRestrictionAtAll() {
        val scan = HttpHosts.scan("GET http://a.test/x\n###\nGET {{host}}/y")
        assertEquals(setOf("a.test"), scan.hosts)
        assertEquals(emptyList<String>(), scan.allowList())
    }

    @Test
    fun resolvedScansCombineAndSortTheirHosts() {
        val all = HttpHosts.scan("GET http://b.test/").let { it + HttpHosts.scan("GET http://a.test/") }
        assertEquals(listOf("a.test", "b.test"), all.allowList())
        assertEquals(emptyList<String>(), HostScan.NONE.allowList())
    }
}
