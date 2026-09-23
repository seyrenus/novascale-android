// Copyright (C) 2026 NovaScale contributors
// SPDX-License-Identifier: GPL-3.0-only
package cc.galaxnet.novascale.browser
import org.junit.Assert.*
import org.junit.Test
class BrowserPolicyTest {
    @Test fun hostSheetPortsChooseSchemeAndPreserveEncodedComponents() {
        assertEquals("https://grafana/dashboard?q=a%26b#x", hostWebAddressWithPort("http://grafana:3000/dashboard?q=a%26b#x", 443))
        assertEquals("http://[fd7a:115c:a1e0::1]:8096/web", hostWebAddressWithPort("http://[fd7a:115c:a1e0::1]/web", 8096))
        assertEquals("http://grafana/", hostWebAddressWithPort("https://grafana:3000/", 80))
    }

    @Test fun hostLinksUseShortNamesAndBracketIpv6Fallbacks() {
        assertEquals("http://grafana", browserHostAddress("grafana.tail123.ts.net.", listOf("100.64.0.1")))
        assertEquals("http://jellyfin", browserHostAddress("jellyfin.internal.example", emptyList()))
        assertEquals("http://[fd7a:115c:a1e0::1]", browserHostAddress(null, listOf("fd7a:115c:a1e0::1")))
    }
    @Test fun tabsKeepIndependentAddressAndNavigationState() {
        val model = BrowserViewModel()
        assertTrue(model.tabs.isEmpty())
        assertNull(model.open())
        assertTrue(model.tabs.isEmpty())
        val first = model.active
        model.address("grafana:3000")
        first.requestedUrl = model.open()
        model.newTab("http://jellyfin:8096")
        val second = model.active
        assertNull(second.requestedUrl)
        model.select(first)
        assertEquals("http://grafana:3000", model.state.value.url)
        assertEquals("grafana:3000", model.state.value.address)
        model.close(first)
        assertSame(second, model.active)
        model.close(second)
        assertEquals(0, model.tabs.size)
        assertEquals("http://", model.state.value.address)
        assertNull(model.active.requestedUrl)
    }
    @Test fun portPresetsPreserveHostAndPath() {
        assertEquals("http://grafana:3000/", browserPortUrl("http://grafana:8080", 3000))
        assertEquals("https://[fd7a:115c:a1e0::1]:8096/web/", browserPortUrl("https://[fd7a:115c:a1e0::1]/web/", 8096))
        assertEquals("https://example.com:9090/a%20b", browserPortUrl("https://example.com/a%20b", 9090))
        assertNull(browserPortUrl("https://", 8080))
    }

    @Test fun acceptsTailnetAndInternetAddresses() {
        assertEquals("http://grafana:3000", browserUrl("grafana:3000"))
        assertEquals("http://100.64.0.42:8096/web/", browserUrl("http://100.64.0.42:8096/web/"))
        assertEquals("https://example.com/", browserUrl(" https://example.com/ "))
    }
    @Test fun rejectsCredentialsLocalhostAndNonWebSchemes() {
        for (value in listOf("https://user:password@host", "http://127.0.0.2:8080", "http://[::1]", "http://localhost", "file:///data/x", "https://host:65536", "https://host:0")) {
            assertThrows(value, IllegalArgumentException::class.java) { browserUrl(value) }
        }
    }
}
