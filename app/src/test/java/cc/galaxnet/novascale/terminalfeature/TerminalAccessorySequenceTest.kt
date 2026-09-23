package cc.galaxnet.novascale.terminalfeature

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TerminalAccessorySequenceTest {
    @Test
    fun plainArrowUsesNormalCsiSequence() {
        assertArrayEquals(
            "\u001b[A".toByteArray(),
            terminalAccessorySequence("A", control = false, alt = false, navigation = true),
        )
    }

    @Test
    fun controlAltArrowUsesCombinedCsiModifier() {
        assertArrayEquals(
            "\u001b[1;7D".toByteArray(),
            terminalAccessorySequence("D", control = true, alt = true, navigation = true),
        )
    }

    @Test
    fun controlSymbolAndAltPrefixAreEncoded() {
        assertArrayEquals(
            byteArrayOf(0x1b, 0x1f),
            terminalAccessorySequence("_", control = true, alt = true, navigation = false),
        )
    }

    @Test
    fun tabBarOnlyConsumesTerminalSpaceForMultipleTabs() {
        assertFalse(shouldShowTerminalTabBar(tabCount = 1))
        assertTrue(shouldShowTerminalTabBar(tabCount = 2))
        assertTrue(shouldShowTerminalTabBar(tabCount = 6))
    }
}
