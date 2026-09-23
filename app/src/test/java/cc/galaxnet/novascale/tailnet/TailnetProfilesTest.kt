package cc.galaxnet.novascale.tailnet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TailnetProfilesTest {
    @Test
    fun officialControlAliasesUseTheDefaultProfile() {
        assertNull(normalizeControlUrl("https://login.tailscale.com/"))
        assertNull(normalizeControlUrl("https://controlplane.tailscale.com"))
    }

    @Test
    fun customControlServerIsPreserved() {
        assertEquals("https://headscale.example/base", normalizeControlUrl(" https://headscale.example/base/ "))
    }
}
