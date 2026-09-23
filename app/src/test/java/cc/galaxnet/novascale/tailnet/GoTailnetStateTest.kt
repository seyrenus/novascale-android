package cc.galaxnet.novascale.tailnet

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GoTailnetStateTest {
    @Test
    fun persistedIdentityDoesNotBypassBrowserLogin() {
        assertFalse(backendCanBeReportedConnected("NeedsLogin", hasIdentity = true))
        assertFalse(backendCanBeReportedConnected("Starting", hasIdentity = true))
    }

    @Test
    fun runningBackendStillRequiresAnIdentity() {
        assertFalse(backendCanBeReportedConnected("Running", hasIdentity = false))
        assertTrue(backendCanBeReportedConnected("Running", hasIdentity = true))
    }
}
