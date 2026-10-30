package io.github.yasarmahomedabbas.immichtv.ui.theme

import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Test

class CanvasDensityTest {
    @Test
    fun `on the Chromecast a canvas pixel is a screen pixel`() {
        val density = canvasDensity(Density(density = 2f, fontScale = 1f), screenWidthDp = 960)

        with(density) {
            assertEquals(1920f, 1920.dp.toPx(), 0.01f)
            assertEquals(18f, 18.sp.toPx(), 0.01f)
        }
    }

    @Test
    fun `the canvas fills the width of a 4K panel`() {
        val density = canvasDensity(Density(density = 2f, fontScale = 1f), screenWidthDp = 1920)

        with(density) { assertEquals(3840f, 1920.dp.toPx(), 0.01f) }
    }

    @Test
    fun `the canvas fills the width of a 720p TV`() {
        val density = canvasDensity(Density(density = 1f, fontScale = 1f), screenWidthDp = 1280)

        with(density) { assertEquals(1280f, 1920.dp.toPx(), 0.01f) }
    }

    @Test
    fun `the system font scale is kept`() {
        val system = Density(density = 2f, fontScale = 1.3f)
        val density = canvasDensity(system, screenWidthDp = 960)

        assertEquals(1.3f, density.fontScale, 0f)
        // Text follows the system's own scale curve, at half the system's density.
        val systemPx = with(system) { 20.sp.toPx() }
        with(density) { assertEquals(systemPx / 2, 20.sp.toPx(), 0.01f) }
    }

    @Test
    fun `an unknown width keeps the system density`() {
        val system = Density(density = 2f, fontScale = 1f)

        assertEquals(system, canvasDensity(system, screenWidthDp = 0))
    }
}
