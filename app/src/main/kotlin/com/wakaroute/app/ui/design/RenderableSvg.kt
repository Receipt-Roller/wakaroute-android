package com.wakaroute.app.ui.design

import android.graphics.Canvas
import android.graphics.RectF
import com.caverock.androidsvg.SVG

/**
 * An inline SVG from a lesson body, prepared for drawing.
 *
 * Parsed once and reused, because parsing is the expensive part and a lesson
 * scrolls past the same figure many times.
 *
 * [parse] returns null rather than throwing. A diagram that cannot be drawn
 * must degrade to the author's description, never take the lesson down with it
 * — §6's rule about undecodable responses, applied to content.
 */
class RenderableSvg private constructor(
    private val svg: SVG,
    /** width ÷ height, from the viewBox. Used to reserve the right space. */
    val aspectRatio: Float,
) {
    fun drawInto(canvas: Canvas, width: Float, height: Float) {
        // Told the exact box rather than left to the document's own width/height,
        // which the authored SVGs express as `width:100%` — meaningless without
        // a containing page.
        svg.documentWidth = width
        svg.documentHeight = height
        svg.renderToCanvas(canvas)
    }

    companion object {
        /** Fallback shape for a diagram whose viewBox is missing or degenerate. */
        private const val DEFAULT_ASPECT_RATIO = 16f / 9f

        fun parse(source: String): RenderableSvg? = try {
            val svg = SVG.getFromString(source)
            RenderableSvg(svg, svg.documentViewBox.aspectRatio())
        } catch (e: Exception) {
            // Malformed markup, or a feature this renderer does not support.
            // Either way the description takes over.
            null
        }

        private fun RectF?.aspectRatio(): Float {
            if (this == null || width() <= 0f || height() <= 0f) return DEFAULT_ASPECT_RATIO
            return width() / height()
        }
    }
}
