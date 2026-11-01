package io.github.grassified.wristprompt.ui

/** Ephemeral viewport math only. No file, conversation, session or inference authority. */
internal data class ImageViewport(val scale: Float = 1f, val x: Float = 0f, val y: Float = 0f) {
    fun transform(zoom: Float, panX: Float, panY: Float, centerX: Float, centerY: Float,
        width: Float, height: Float, imageWidth: Int, imageHeight: Int): ImageViewport {
        if (listOf(zoom, panX, panY, centerX, centerY, width, height).any { !it.isFinite() } ||
            zoom <= 0 || width <= 0 || height <= 0 || imageWidth <= 0 || imageHeight <= 0) return this
        val next = (scale * zoom).coerceIn(1f, 5f)
        val factor = next / scale
        val fit = minOf(width / imageWidth, height / imageHeight)
        val maxX = ((imageWidth * fit * next - width) / 2).coerceAtLeast(0f)
        val maxY = ((imageHeight * fit * next - height) / 2).coerceAtLeast(0f)
        return ImageViewport(next, if (maxX == 0f) 0f else ((x - centerX) * factor + centerX + panX).coerceIn(-maxX, maxX),
            if (maxY == 0f) 0f else ((y - centerY) * factor + centerY + panY).coerceIn(-maxY, maxY))
    }
}
