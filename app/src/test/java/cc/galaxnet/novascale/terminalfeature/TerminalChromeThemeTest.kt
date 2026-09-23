package cc.galaxnet.novascale.terminalfeature

import cc.galaxnet.novascale.core.CursorShape
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class TerminalChromeThemeTest {
    @Test
    fun darkBackgroundIsLiftedLikeIosAccessoryChrome() {
        assertEquals(0xff141414.toInt(), adjustTerminalChromeBackground(0xff000000.toInt()))
        assertNotEquals(
            TerminalThemeCatalog.default.background,
            adjustTerminalChromeBackground(TerminalThemeCatalog.default.background),
        )
    }

    @Test
    fun lightBackgroundIsLoweredLikeIosAccessoryChrome() {
        assertEquals(0xfff0f0f0.toInt(), adjustTerminalChromeBackground(0xffffffff.toInt()))
        assertNotEquals(
            TerminalThemeCatalog.builtIns[1].background,
            adjustTerminalChromeBackground(TerminalThemeCatalog.builtIns[1].background),
        )
    }

    @Test
    fun chromeAdjustmentPreservesAlpha() {
        assertEquals(0x7f, adjustTerminalChromeBackground(0x7f282a36).ushr(24))
    }

    @Test
    fun cursorShapeSettingsNormalizeAndMapToRendererOverrides() {
        assertEquals(
            TerminalCursorShapeOption.BEAM,
            TerminalSettings(cursorShapeId = "unknown").normalized().cursorShape,
        )
        assertEquals(TerminalCursorShapeOption.BEAM, TerminalSettings().cursorShape)
        assertEquals(null, TerminalCursorShapeOption.APPLICATION.shapeOverride)
        assertEquals(CursorShape.BLOCK, TerminalCursorShapeOption.BLOCK.shapeOverride)
        assertEquals(CursorShape.BEAM, TerminalCursorShapeOption.BEAM.shapeOverride)
        assertEquals(CursorShape.UNDERLINE, TerminalCursorShapeOption.UNDERLINE.shapeOverride)
    }
}
