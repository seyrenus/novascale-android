package cc.galaxnet.novascale

import cc.galaxnet.novascale.core.FailureCategory
import cc.galaxnet.novascale.core.TailnetIdentity
import cc.galaxnet.novascale.core.TailnetState
import cc.galaxnet.novascale.tailnet.shouldRestoreTailnetAtLaunch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppSectionTest {
    @Test
    fun publicNavigationIncludesBrowser() {
        assertEquals(
            listOf(R.string.nav_home, R.string.nav_browser, R.string.nav_settings),
            AppSection.entries.map { it.labelRes },
        )
    }

    @Test
    fun onlyAStoppedTailnetIsRestoredAtAppLaunch() {
        assertTrue(shouldRestoreTailnetAtLaunch(TailnetState.Stopped))
        assertFalse(shouldRestoreTailnetAtLaunch(TailnetState.Starting))
        assertFalse(shouldRestoreTailnetAtLaunch(TailnetState.LoginRequired(null)))
        assertFalse(
            shouldRestoreTailnetAtLaunch(
                TailnetState.Running(TailnetIdentity("node", "device.example", emptyList()), 0),
            ),
        )
        assertFalse(
            shouldRestoreTailnetAtLaunch(
                TailnetState.Failed(FailureCategory.NETWORK, "Unavailable"),
            ),
        )
    }
}
