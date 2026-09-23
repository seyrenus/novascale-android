package cc.galaxnet.novascale

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HostLaunchPolicyTest {
    @Test
    fun missingProfileRoutesThroughSshSettings() {
        assertTrue(requiresSshSettings(hasSavedProfile = false))
    }

    @Test
    fun savedProfileGoesDirectlyToHostTool() {
        assertFalse(requiresSshSettings(hasSavedProfile = true))
    }
}
