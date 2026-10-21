package com.jianji.ime

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.Shader
import android.graphics.drawable.Drawable

/**
 * 键盘底片：主题色的深/浅渐变 + 两团主题光晕 + 一道很轻的斜向窗光。
 *
 * 不做真正的窗口透明，也不采样键盘下方的画面；只是在输入法窗口内画一层
 * 有颜色、有层次的「液体玻璃底」，键面再用半透明渐变叠上去。
 */
class SkinBackdropDrawable(private val theme: SkinTheme) : Drawable() {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isDither = true }

    private var base: Shader? = null
    private var glowA: Shader? = null
    private var glowB: Shader? = null
    private var sheen: Shader? = null
    private var builtFor: Rect? = null

    override fun onBoundsChange(bounds: Rect) {
        super.onBoundsChange(bounds)
        build(bounds)
    }

    private fun build(b: Rect) {
        val w = b.width().toFloat()
        val h = b.height().toFloat()
        if (w <= 1f || h <= 1f) {
            base = null
            builtFor = null
            return
        }
        base = LinearGradient(
            b.left.toFloat(), b.top.toFloat(), b.right.toFloat(), b.bottom.toFloat(),
            theme.windowTop, theme.windowBottom, Shader.TileMode.CLAMP,
        )
        // 右上主光晕 + 左下副光晕，模拟皮肤底片里透出的颜色
        glowA = RadialGradient(
            b.left + w * 0.86f, b.top + h * 0.10f, w * 0.72f,
            intArrayOf(theme.glow, Color.TRANSPARENT), floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP,
        )
        glowB = RadialGradient(
            b.left + w * 0.12f, b.top + h * 0.92f, w * 0.68f,
            intArrayOf(theme.glow2, Color.TRANSPARENT), floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP,
        )
        // 很轻的左上斜向窗光，给底片一点「玻璃片」方向感
        sheen = LinearGradient(
            b.left.toFloat(), b.top.toFloat(), b.right.toFloat(), b.top + h * 0.72f,
            intArrayOf(0x16FFFFFF, 0x00FFFFFF, 0x08FFFFFF),
            floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP,
        )
        builtFor = Rect(b)
    }

    override fun draw(canvas: Canvas) {
        val b = bounds
        if (b.width() <= 1 || b.height() <= 1) return
        if (builtFor != b) build(b)

        paint.shader = base
        canvas.drawRect(b, paint)
        paint.shader = glowA
        canvas.drawRect(b, paint)
        paint.shader = glowB
        canvas.drawRect(b, paint)
        paint.shader = sheen
        canvas.drawRect(b, paint)
        paint.shader = null
    }

    override fun setAlpha(alpha: Int) = Unit

    override fun setColorFilter(colorFilter: ColorFilter?) = Unit

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.OPAQUE
}