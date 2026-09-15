package com.nexlink.social.ui.chrome

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable

/**
 * The app's icons, drawn.
 *
 * §14 asks for a surface that reads as a messaging app, and the first version
 * of it used emoji as controls — "☰", "📞", "+". That was wrong for three
 * separate reasons, and the third is the one that decided it:
 *
 *  1. An emoji is a *character*, so it is rendered by whichever font the
 *     handset ships. The same button is a flat glyph on one phone, a full
 *     colour cartoon on another, and a tofu box on a third.
 *  2. It cannot be tinted. A control that ignores the theme is the clearest
 *     possible tell that a screen was assembled rather than designed, and in
 *     the dark theme a black-line emoji simply disappears.
 *  3. A screen reader announces it as punctuation, or skips it. The existing
 *     code worked around that with `contentDescription` on every single one —
 *     the workaround was right, but having to apply it everywhere is the smell.
 *
 * So each icon is a handful of lines and arcs on a 24x24 grid, stroked in
 * whatever colour the caller asks for. No asset pipeline, no XML vectors to
 * keep in sync with the palette, and it scales to any size without a second
 * density bucket. `contentDescription` is still set by the caller — an icon is
 * never self-describing, drawn or not.
 */
class Icon(
    private val kind: Kind,
    private val tint: Int,
    /** Stroke weight on the 24dp grid. 2f reads as "regular" at any size. */
    private val weight: Float = 2f,
) : Drawable() {

    enum class Kind {
        BACK, SEARCH, MORE, VIDEO_CALL, ATTACH, SEND, NEW_CHAT, PEOPLE, CLOSE, CHECK
    }

    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = tint
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = tint
    }
    private val path = Path()

    override fun draw(canvas: Canvas) {
        val b = bounds
        if (b.width() <= 0 || b.height() <= 0) return
        val s = minOf(b.width(), b.height()) / 24f
        val save = canvas.save()
        canvas.translate(
            b.left + (b.width() - 24f * s) / 2f,
            b.top + (b.height() - 24f * s) / 2f,
        )
        canvas.scale(s, s)
        stroke.strokeWidth = weight
        path.reset()
        when (kind) {
            Kind.BACK -> {
                canvas.drawLine(20f, 12f, 5f, 12f, stroke)
                path.moveTo(11f, 6f); path.lineTo(5f, 12f); path.lineTo(11f, 18f)
                canvas.drawPath(path, stroke)
            }
            Kind.SEARCH -> {
                canvas.drawCircle(10.5f, 10.5f, 6.5f, stroke)
                canvas.drawLine(15.3f, 15.3f, 20.5f, 20.5f, stroke)
            }
            Kind.MORE -> {
                canvas.drawCircle(12f, 5.2f, 1.7f, fill)
                canvas.drawCircle(12f, 12f, 1.7f, fill)
                canvas.drawCircle(12f, 18.8f, 1.7f, fill)
            }
            Kind.VIDEO_CALL -> {
                // A camera body with the classic wedge, rather than a handset:
                // the call this starts is a video call by default (§17.2), and
                // a handset glyph would promise audio only.
                val r = android.graphics.RectF(2f, 6f, 15.5f, 18f)
                canvas.drawRoundRect(r, 3f, 3f, stroke)
                path.moveTo(15.5f, 10.5f); path.lineTo(21.5f, 7f)
                path.lineTo(21.5f, 17f); path.lineTo(15.5f, 13.5f); path.close()
                canvas.drawPath(path, stroke)
            }
            Kind.ATTACH -> {
                canvas.drawLine(12f, 5f, 12f, 19f, stroke)
                canvas.drawLine(5f, 12f, 19f, 12f, stroke)
            }
            Kind.SEND -> {
                // A paper plane, filled. The notch on the trailing edge is what
                // stops it reading as a plain triangle at 20dp.
                path.moveTo(3f, 20.5f)
                path.lineTo(21.5f, 12f)
                path.lineTo(3f, 3.5f)
                path.lineTo(6.2f, 12f)
                path.close()
                canvas.drawPath(path, fill)
            }
            Kind.NEW_CHAT -> {
                path.moveTo(4f, 20f); path.lineTo(4f, 15.8f); path.lineTo(15.6f, 4.2f)
                path.lineTo(19.8f, 8.4f); path.lineTo(8.2f, 20f); path.close()
                canvas.drawPath(path, stroke)
                canvas.drawLine(13.5f, 6.3f, 17.7f, 10.5f, stroke)
            }
            Kind.PEOPLE -> {
                canvas.drawCircle(9.5f, 8f, 3.6f, stroke)
                path.moveTo(3.2f, 19.5f)
                path.cubicTo(3.2f, 14.6f, 15.8f, 14.6f, 15.8f, 19.5f)
                canvas.drawPath(path, stroke)
                canvas.drawCircle(17.6f, 8.6f, 2.6f, stroke)
                path.reset()
                path.moveTo(17.2f, 14.2f)
                path.cubicTo(20.6f, 14.6f, 21.3f, 16.8f, 21.3f, 19.5f)
                canvas.drawPath(path, stroke)
            }
            Kind.CLOSE -> {
                canvas.drawLine(6f, 6f, 18f, 18f, stroke)
                canvas.drawLine(18f, 6f, 6f, 18f, stroke)
            }
            Kind.CHECK -> {
                path.moveTo(4.5f, 12.5f); path.lineTo(9.5f, 17.5f); path.lineTo(19.5f, 6.5f)
                canvas.drawPath(path, stroke)
            }
        }
        canvas.restoreToCount(save)
    }

    override fun setAlpha(alpha: Int) { stroke.alpha = alpha; fill.alpha = alpha }
    override fun setColorFilter(cf: ColorFilter?) { stroke.colorFilter = cf; fill.colorFilter = cf }
    @Deprecated("Drawable API", ReplaceWith("PixelFormat.TRANSLUCENT"))
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
