package com.jianji.ime.keyboard

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import com.jianji.ime.ImTheme

/**
 * 候选区右侧的功能图标按钮（M4）：剪贴板 / 表情 / 设置。
 * 图标与 [HideKeyboardButton] 同一条词线（0.70h），常态灰、按下与展开态（active）用强调色。
 * 有组句内容（输入中）时由 Service 统一隐藏，候选栏占满宽度。
 */
class ToolButton(context: Context) : View(context) {

    enum class Glyph { CLIP, EMOJI, GEAR }

    var glyph: Glyph = Glyph.CLIP
        set(value) {
            field = value
            contentDescription = when (value) {
                Glyph.CLIP -> "剪贴板"
                Glyph.EMOJI -> "表情"
                Glyph.GEAR -> "设置"
            }
            invalidate()
        }

    /** 对应面板展开中：图标点亮（强调色） */
    var active: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    private val density = resources.displayMetrics.density
    private fun dp(v: Float) = v * density

    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(2.2f)
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    private val rect = RectF()
    private val arcRect = RectF()

    init {
        isClickable = true
        isFocusable = false
        contentDescription = "剪贴板"
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val color = when {
            isPressed -> ImTheme.accentPressed
            active -> ImTheme.accent
            else -> ImTheme.keyFnText
        }
        stroke.color = color
        fill.color = color
        val cx = width / 2f
        val cy = height * 0.70f
        when (glyph) {
            Glyph.CLIP -> drawClip(canvas, cx, cy)
            Glyph.EMOJI -> drawEmoji(canvas, cx, cy)
            Glyph.GEAR -> drawGear(canvas, cx, cy)
        }
    }

    /** 剪贴板：圆角板 + 顶夹 + 两条文字线 */
    private fun drawClip(canvas: Canvas, cx: Float, cy: Float) {
        val bw = dp(6.4f)
        val bh = dp(8.5f)
        rect.set(cx - bw, cy - bh, cx + bw, cy + bh)
        canvas.drawRoundRect(rect, dp(2.6f), dp(2.6f), stroke)
        rect.set(cx - dp(2.9f), cy - dp(10.4f), cx + dp(2.9f), cy - dp(6.4f))
        canvas.drawRoundRect(rect, dp(1.5f), dp(1.5f), fill)
        canvas.drawLine(cx - dp(3.6f), cy + dp(0.2f), cx + dp(3.6f), cy + dp(0.2f), stroke)
        canvas.drawLine(cx - dp(3.6f), cy + dp(4.2f), cx + dp(3.6f), cy + dp(4.2f), stroke)
    }

    /** 表情：圆脸 + 两点眼睛 + 微笑弧 */
    private fun drawEmoji(canvas: Canvas, cx: Float, cy: Float) {
        val r = dp(8.4f)
        canvas.drawCircle(cx, cy, r, stroke)
        canvas.drawCircle(cx - dp(3.0f), cy - dp(2.8f), dp(1.35f), fill)
        canvas.drawCircle(cx + dp(3.0f), cy - dp(2.8f), dp(1.35f), fill)
        arcRect.set(cx - dp(4.9f), cy - dp(5.0f), cx + dp(4.9f), cy + dp(5.2f))
        canvas.drawArc(arcRect, 28f, 124f, false, stroke)
    }

    /** 设置：齿轮（轮圈 + 8 颗齿 + 中孔） */
    private fun drawGear(canvas: Canvas, cx: Float, cy: Float) {
        val r = dp(6.4f)
        canvas.drawCircle(cx, cy, r, stroke)
        canvas.drawCircle(cx, cy, dp(2.4f), stroke)
        for (i in 0 until 8) {
            val a = Math.toRadians((i * 45).toDouble())
            val c = Math.cos(a).toFloat()
            val s = Math.sin(a).toFloat()
            canvas.drawLine(
                cx + c * (r - dp(0.4f)), cy + s * (r - dp(0.4f)),
                cx + c * (r + dp(2.6f)), cy + s * (r + dp(2.6f)),
                stroke,
            )
        }
    }
}
