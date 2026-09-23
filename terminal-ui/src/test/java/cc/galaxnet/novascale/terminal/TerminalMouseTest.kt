package cc.galaxnet.novascale.terminal

import org.junit.Assert.*
import org.junit.Test

class TerminalMouseTest {
    @Test fun sgrClicksDragWheelAndModifiers() {
        val flags = 4 or 16
        assertEquals("\u001b[<0;300;10M", TerminalMouse.encode(flags, 0, 300, 10)!!.decodeToString())
        assertEquals("\u001b[<0;300;10m", TerminalMouse.encode(flags, 0, 300, 10, release = true)!!.decodeToString())
        assertEquals("\u001b[<60;4;5M", TerminalMouse.encode(flags, 0, 4, 5, motion = true, shift = true, alt = true, control = true)!!.decodeToString())
        assertEquals("\u001b[<64;4;5M", TerminalMouse.encode(flags, 64, 4, 5)!!.decodeToString())
        assertEquals("\u001b[<65;4;5M", TerminalMouse.encode(flags, 65, 4, 5)!!.decodeToString())
        assertEquals("\u001b[<2;4;5m", TerminalMouse.encode(flags, 2, 4, 5, release = true)!!.decodeToString())
    }
    @Test fun trackingModesAreHonored() {
        assertNull(TerminalMouse.encode(16, 0, 1, 1)) // Encoding alone does not enable tracking.
        assertNull(TerminalMouse.encode(2 or 16, 0, 1, 1, motion = true))
        assertNull(TerminalMouse.encode(4 or 16, 3, 1, 1, motion = true))
        assertEquals("\u001b[<35;1;1M", TerminalMouse.encode(8 or 16, 3, 1, 1, motion = true)!!.decodeToString())
        assertNull(TerminalMouse.encode(1, 0, 1, 1, release = true))
        assertNull(TerminalMouse.encode(1, 64, 1, 1))
    }
    @Test fun legacyEncodingUsesRawBytesAndDoesNotOverflow() {
        assertArrayEquals(byteArrayOf(27,91,77,32,232.toByte(),33), TerminalMouse.encode(2,0,200,1))
        assertNull(TerminalMouse.encode(2,0,224,1))
        assertEquals("\u001b[M#!!", TerminalMouse.encode(2,0,1,1,release=true)!!.decodeToString())
    }
    @Test fun pixelAndExtendedCoordinates() {
        assertEquals("\u001b[<0;400;200M", TerminalMouse.encode(2 or 128,0,4,5,pixelX=400,pixelY=200)!!.decodeToString())
        assertEquals("\u001b[32;300;10M", TerminalMouse.encode(2 or 64,0,300,10)!!.decodeToString())
        assertEquals("\u001b[M " + 332.toChar() + 42.toChar(), TerminalMouse.encode(2 or 32,0,300,10)!!.decodeToString())
    }
}
