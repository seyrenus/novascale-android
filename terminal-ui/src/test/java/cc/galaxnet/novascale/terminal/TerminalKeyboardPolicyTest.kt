package cc.galaxnet.novascale.terminal

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TerminalKeyboardPolicyTest {
    @Test
    fun touchRequestsTheSoftwareKeyboardWithoutHardwareKeys() {
        assertTrue(shouldRequestSoftwareKeyboard(hasExternalHardwareKeyboard = false))
    }

    @Test
    fun hardwareKeyboardLeavesOnlyTheAccessorySurface() {
        assertFalse(shouldRequestSoftwareKeyboard(hasExternalHardwareKeyboard = true))
    }

    @Test
    fun dragAccumulatesWholeRowsInBothDirections() {
        val accumulator = TerminalScrollAccumulator()
        assertEquals(0, accumulator.consume(9f, 20f))
        assertEquals(1, accumulator.consume(12f, 20f))
        assertEquals(-1, accumulator.consume(-22f, 20f))
        accumulator.reset()
        assertEquals(0, accumulator.consume(19f, 20f))
    }

    @Test
    fun wheelScrollsThreeRowsTowardGhosttyHistoryOrBottom() {
        assertEquals(-3, terminalWheelRows(0.2f))
        assertEquals(3, terminalWheelRows(-1f))
        assertEquals(0, terminalWheelRows(0f))
    }
}
