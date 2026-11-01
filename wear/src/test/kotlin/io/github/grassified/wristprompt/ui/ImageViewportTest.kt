package io.github.grassified.wristprompt.ui

import org.junit.Assert.*
import org.junit.Test

class ImageViewportTest {
    private fun ImageViewport.move(z: Float, x: Float = 0f, y: Float = 0f, w: Int = 100, h: Int = 100) =
        transform(z, x, y, 0f, 0f, 100f, 100f, w, h)
    @Test fun startsAspectFitAndReopenIsIndependent() {
        assertEquals(ImageViewport(), ImageViewport().move(1f, 100f, -100f))
        assertEquals(ImageViewport(), ImageViewport().move(0.2f))
        val zoomed = ImageViewport().move(3f, 20f, -20f)
        assertEquals(3f, zoomed.scale); assertEquals(20f, zoomed.x); assertEquals(-20f, zoomed.y)
        assertEquals(ImageViewport(), ImageViewport())
    }
    @Test fun scaleAndPanRespectActualAspectFitBounds() {
        val limited = ImageViewport().move(100f, 999f, -999f, w = 200, h = 100)
        assertEquals(5f, limited.scale); assertEquals(200f, limited.x); assertEquals(-75f, limited.y)
        val reset = limited.move(0.01f)
        assertEquals(ImageViewport(), reset)
    }
    @Test fun centroidAnchorAndInvalidGesturesAreSafe() {
        val anchored = ImageViewport().transform(2f, 0f, 0f, 20f, 10f, 100f, 100f, 100, 100)
        assertEquals(-20f, anchored.x); assertEquals(-10f, anchored.y)
        assertEquals(anchored, anchored.move(Float.NaN))
        assertEquals(anchored, anchored.move(-1f))
    }
    @Test fun anEdgeClampsOutwardPanButAlwaysAllowsMovementBackIntoTheImage() {
        val edge = ImageViewport().move(3f, 1000f, -1000f)
        assertEquals(100f, edge.x); assertEquals(-100f, edge.y)
        assertEquals(edge, edge.move(1f, 40f, -40f))
        val towardCenter = edge.move(1f, -40f, 40f)
        assertEquals(60f, towardCenter.x); assertEquals(-60f, towardCenter.y)
        assertEquals(ImageViewport(), towardCenter.move(0.01f))
    }
}
