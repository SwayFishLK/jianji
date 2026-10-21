package com.jianji.ime

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.Shader
import android.graphics.drawable.Drawable

/**
 * 键盘底片：先画一块接近「liquid」样品 theme-art 的彩色底，再压一层很薄的暗/亮玻璃，
 * 最后扫一道斜向高光、描一条顶部亮边。
 *
 * 不做真正的窗口透明，也不采样键盘下方的画面；但这次不再用深色渐变糊弄，
 * 而是用 3 团大色斑 + 4 段对角渐变把样品里「键盘下面那块彩色照片」模拟出来，
 * 让半透明键帽真的能透出颜色。
 */
class SkinBackdropDrawable(private val theme: SkinTheme) : Drawable() {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isDither = true }

    /**
     * 样品里的 backdrop-filter 带 saturate(2.05) contrast(1.08) brightness(1.04)；
     * 真机没有实时采样，就在画底片时对纯色层做一次类似的饱和度/对比度补偿，
     * 否则彩色照片会显得灰、键帽也立不起来。
     */
    private val artPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        isDither = true
        colorFilter = ColorMatrixColorFilter(buildArtColorMatrix())
    }
    private val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    /**
     * 冷色主题（深渊/极光/星云）额外抬一点 G/B，
     * 模拟样品在深蓝玻璃下更冷的色偏；暖色主题不套这一层，免得发绿。
     */
    private fun buildArtColorMatrix(): ColorMatrix {
        val cm = ColorMatrix().apply {
            setSaturation(2.0f)
            postConcat(
                ColorMatrix(
                    floatArrayOf(
                        1.10f, 0f, 0f, 0f, -8f,
                        0f, 1.10f, 0f, 0f, -8f,
                        0f, 0f, 1.10f, 0f, -8f,
                        0f, 0f, 0f, 1f, 0f,
                    ),
                ),
            )
        }
        val cool = when (theme.id) {
            "abyss", "aurora", "nebula" -> if (theme.light) 1.05f else 1.25f
            else -> 1.0f
        }
        cm.postConcat(
            ColorMatrix(
                floatArrayOf(
                    1f, 0f, 0f, 0f, 2f,
                    0f, cool, 0f, 0f, 2f,
                    0f, 0f, cool, 0f, 2f,
                    0f, 0f, 0f, 1f, 0f,
                ),
            ),
        )
        return cm
    }

    private var base: Shader? = null
    private var artA: Shader? = null
    private var artB: Shader? = null
    private var artC: Shader? = null
    private var tint: Shader? = null
    private var sheen: Shader? = null
    private var topShade: Shader? = null
    private var bottomShade: Shader? = null
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
        // 墨夜：旧版默认皮肤的平涂底，只保留很轻的上浅下深，不画彩色光斑。
        if (theme.classic) {
            base = LinearGradient(
                b.left.toFloat(), b.top.toFloat(),
                b.left.toFloat(), b.bottom.toFloat(),
                intArrayOf(theme.windowTop, theme.windowBottom),
                floatArrayOf(0f, 1f), Shader.TileMode.CLAMP,
            )
            artA = null
            artB = null
            artC = null
            tint = null
            sheen = null
            topShade = null
            bottomShade = null
            builtFor = Rect(b)
            return
        }
        // 样品 theme-art 的 4 段对角底色（0 / 38% / 70% / 100%）
        base = LinearGradient(
            b.left.toFloat(), b.top.toFloat(),
            b.left + w * 0.42f, b.bottom.toFloat(),
            theme.artBase, floatArrayOf(0f, 0.38f, 0.70f, 1f), Shader.TileMode.CLAMP,
        )

        // 右上亮斑 / 左侧冷色斑 / 右下深色斑：用大半径 + 偏低的透明截止点，
        // 做出类似 blur 的柔边，而不是三个清晰的圆。
        artA = radial(
            b.left + w * 0.78f, b.top + h * 0.18f, w * 0.96f,
            theme.artHi, 0.46f,
        )
        artB = radial(
            b.left + w * 0.20f, b.top + h * 0.24f, w * 0.84f,
            theme.artCool, 0.50f,
        )
        artC = radial(
            b.left + w * 0.72f, b.top + h * 0.92f, w * 1.02f,
            theme.artDeep, 0.68f,
        )

        // 样品里的 panel-bg：暗色主题是 29%→17% 的深蓝薄纱，浅色是 23%→8% 的白纱。
        // 没有真正的 backdrop blur，这层薄纱负责把彩色照片压到可读的亮度。
        tint = LinearGradient(
            b.left.toFloat(), b.top.toFloat(), b.left.toFloat(), b.bottom.toFloat(),
            if (theme.light) {
                intArrayOf(0x3BFFFFFF, 0x14FFFFFF)
            } else {
                intArrayOf(0x4A15273D, 0x2B07111F)
            },
            floatArrayOf(0f, 1f), Shader.TileMode.CLAMP,
        )

        // 样品 .ime::after 的宽幅斜向窗光
        sheen = LinearGradient(
            b.left.toFloat(), b.top.toFloat(),
            b.right.toFloat(), b.top + h * 0.68f,
            intArrayOf(0x00FFFFFF, 0x1FFFFFFF, 0x00FFFFFF),
            floatArrayOf(0f, 0.42f, 1f), Shader.TileMode.CLAMP,
        )

        // 顶部候选/工具条压暗，让白色图标和候选字不飘在亮色照片上；
        // 底部再压一点点，键盘不会像一块发光的塑料板。
        topShade = LinearGradient(
            b.left.toFloat(), b.top.toFloat(),
            b.left.toFloat(), b.top + h * 0.27f,
            if (theme.light) {
                intArrayOf(0x8AFFFFFF.toInt(), 0x00FFFFFF)
            } else {
                intArrayOf(0xD907111F.toInt(), 0x0007111F)
            },
            floatArrayOf(0f, 1f), Shader.TileMode.CLAMP,
        )
        bottomShade = LinearGradient(
            b.left.toFloat(), b.bottom - h * 0.22f,
            b.left.toFloat(), b.bottom.toFloat(),
            if (theme.light) {
                intArrayOf(0x00FFFFFF, 0x3DFFFFFF)
            } else {
                intArrayOf(0x0007111F, 0x6607111F)
            },
            floatArrayOf(0f, 1f), Shader.TileMode.CLAMP,
        )
        builtFor = Rect(b)
    }

    private fun radial(cx: Float, cy: Float, radius: Float, color: Int, fade: Float): Shader {
        // 用同色透明而不是 Color.TRANSPARENT，避免外圈发灰。
        val clear = color and 0x00FFFFFF
        return RadialGradient(
            cx, cy, radius,
            intArrayOf(color, clear, clear),
            floatArrayOf(0f, fade, 1f),
            Shader.TileMode.CLAMP,
        )
    }

    override fun draw(canvas: Canvas) {
        val b = bounds
        if (b.width() <= 1 || b.height() <= 1) return
        if (builtFor != b) build(b)

        if (theme.classic) {
            paint.shader = base
            canvas.drawRect(b, paint)
            paint.shader = null
            return
        }

        artPaint.shader = base
        canvas.drawRect(b, artPaint)
        // CSS 的层叠顺序：越靠前的 radial 越在顶层，所以倒着画。
        // 同时把圆形径向压扁成椭圆，匹配样品 120% 100% / 90% 90% 的比例，
        // 否则顶部色斑会一路糊到键盘中部，整体发灰。
        drawBlob(canvas, artC, b.left + b.width() * 0.72f, b.top + b.height() * 0.92f, 0.68f)
        drawBlob(canvas, artB, b.left + b.width() * 0.20f, b.top + b.height() * 0.24f, 0.68f)
        drawBlob(canvas, artA, b.left + b.width() * 0.78f, b.top + b.height() * 0.18f, 0.62f)
        artPaint.shader = null
        paint.shader = tint
        canvas.drawRect(b, paint)
        paint.shader = topShade
        canvas.drawRect(b, paint)
        paint.shader = sheen
        canvas.drawRect(b, paint)
        paint.shader = bottomShade
        canvas.drawRect(b, paint)

        // 样品 panel-rim：顶部一条 42%（浅色 95%）的白边
        paint.shader = null
        rimPaint.color = if (theme.light) 0xF2FFFFFF.toInt() else 0x6BFFFFFF
        canvas.drawRect(b.left.toFloat(), b.top.toFloat(), b.right.toFloat(), b.top + 2f, rimPaint)
    }

    /** 把圆形 shader 在 y 方向压扁后绘制，近似 CSS 的椭圆径向渐变。 */
    private fun drawBlob(canvas: Canvas, shader: Shader?, cx: Float, cy: Float, scaleY: Float) {
        if (shader == null) return
        artPaint.shader = shader
        canvas.save()
        canvas.scale(1f, scaleY, cx, cy)
        val b = bounds
        // 纵向多画 60%，防止压扁后矩形覆盖不到上下边缘。
        val overscan = b.height() * 0.6f
        canvas.drawRect(
            b.left.toFloat(), b.top - overscan,
            b.right.toFloat(), b.bottom + overscan,
            artPaint,
        )
        canvas.restore()
        artPaint.shader = null
    }

    override fun setAlpha(alpha: Int) = Unit

    override fun setColorFilter(colorFilter: ColorFilter?) = Unit

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.OPAQUE
}