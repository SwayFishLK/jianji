package com.jianji.ime.keyboard

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.view.View
import com.jianji.ime.ImTheme

/**
 * 候选区（键盘上方预留区）最右侧的按钮，三种形态：
 *  - HIDE：˅ 收起键盘（无候选时）
 *  - MORE：˅˅ 展开「更多候选」面板（拼音组句、有候选时）
 *  - COLLAPSE：˄˄ 收起候选面板（面板展开中）
 *
 * 图标统一画在「中间靠下一点」的词线（0.70h）上，三种形态都不跳动；
 * 后续的剪贴板 / 设置 / 表情图标同用这一水平线。
 */
class HideKeyboardButton(context: Context) : View(context) {

    enum class Mode { HIDE, MORE, COLLAPSE }

    var mode: Mode = Mode.HIDE
        set(value) {
            if (field != value) {
                field = value
                contentDescription = when (value) {
                    Mode.HIDE -> "收起键盘"
                    Mode.MORE -> "更多候选"
                    Mode.COLLAPSE -> "收起候选面板"
                }
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

    private val colorNormal get() = ImTheme.keyFnText
    private val colorAccent get() = ImTheme.accent
    private val colorPressed get() = ImTheme.accentPressed

    init {
        isClickable = true
        isFocusable = false
        contentDescription = "收起键盘"
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        stroke.color = when {
            isPressed -> colorPressed
            mode == Mode.HIDE -> colorNormal
            else -> colorAccent
        }
        val cx = width / 2f
        // 统一画在词线（0.70h，"中间靠下一点"）：有候选 / 无候选都保持同一水平位置
        val cy = height * 0.70f
        when (mode) {
            Mode.HIDE -> drawChevron(canvas, cx, cy, dp(7f), down = true)

            Mode.MORE -> {
                drawChevron(canvas, cx, cy - dp(4.5f), dp(6f), down = true)
                drawChevron(canvas, cx, cy + dp(4.5f), dp(6f), down = true)
            }

            Mode.COLLAPSE -> {
                drawChevron(canvas, cx, cy - dp(4.5f), dp(6f), down = false)
                drawChevron(canvas, cx, cy + dp(4.5f), dp(6f), down = false)
            }
        }
    }

    private fun drawChevron(canvas: Canvas, cx: Float, cy: Float, s: Float, down: Boolean) {
        val dy = if (down) 1f else -1f
        val path = Path().apply {
            moveTo(cx - s, cy - s * 0.5f * dy)
            lineTo(cx, cy + s * 0.6f * dy)
            lineTo(cx + s, cy - s * 0.5f * dy)
        }
        canvas.drawPath(path, stroke)
    }
}
