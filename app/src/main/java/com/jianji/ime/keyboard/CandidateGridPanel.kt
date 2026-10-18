package com.jianji.ime.keyboard

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.text.TextPaint
import android.text.TextUtils
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.widget.OverScroller
import com.jianji.ime.ImTheme
import kotlin.math.abs

/**
 * 「更多候选」面板：有候选时点候选区右侧按钮展开，盖在键盘区上（不改输入法窗口高度）。
 * 5 列网格 + 纵向滚动 + 点选上屏。M2 只画词面；M3 起词面上方补译文小字。
 */
class CandidateGridPanel(context: Context) : View(context) {

    /** 点选回调：下标对应最近一次 setItems 的列表 */
    var onSelect: ((Int) -> Unit)? = null

    private val items = ArrayList<CandidateBar.Item>()

    /** 实际绘制的译文（超长时按一格宽省略号截断） */
    private val shownTr = ArrayList<String?>()
    private var scrollY = 0f
    private val scroller = OverScroller(context)
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val minFlingVelocity = ViewConfiguration.get(context).scaledMinimumFlingVelocity
    private var velocityTracker: VelocityTracker? = null

    private val density = resources.displayMetrics.density
    private fun dp(v: Float) = v * density

    private val cols = 5
    private val cellH = dp(56f)
    private val wordSize = dp(16f)
    private val trSize = dp(9.5f)

    private val bgPaint = Paint()
    private val wordPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = wordSize
    }
    private val trPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = trSize
    }

    private var downX = 0f
    private var downY = 0f
    private var downScroll = 0f
    private var dragging = false

    init {
        isClickable = true
        contentDescription = "更多候选"
    }

    /** 数量不多（≤100）时避免处于滚动中途的旧偏移 */
    fun setItems(list: List<CandidateBar.Item>) {
        items.clear()
        items.addAll(list)
        shownTr.clear()
        val maxTrW = resources.displayMetrics.widthPixels / cols - dp(6f)
        for (it in items) {
            val tr = it.translation
            shownTr.add(
                when {
                    tr.isNullOrEmpty() -> null
                    trPaint.measureText(tr) > maxTrW ->
                        TextUtils.ellipsize(tr, trPaint, maxTrW, TextUtils.TruncateAt.END).toString()
                    else -> tr
                },
            )
        }
        scrollY = 0f
        scroller.forceFinished(true)
        invalidate()
    }

    private fun cellW(): Float = width.toFloat() / cols
    private fun rowCount(): Int = (items.size + cols - 1) / cols
    private fun maxScroll(): Float = maxOf(0f, rowCount() * cellH - height)

    override fun onDraw(canvas: Canvas) {
        bgPaint.color = ImTheme.panelBg
        wordPaint.color = ImTheme.candText
        trPaint.color = ImTheme.candTranslation
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)
        if (items.isEmpty()) return
        val cw = cellW()
        val first = maxOf(0, (scrollY / cellH).toInt())
        val last = minOf(rowCount() - 1, ((scrollY + height) / cellH).toInt())
        for (r in first..last) {
            val top = r * cellH - scrollY
            for (c in 0 until cols) {
                val idx = r * cols + c
                if (idx >= items.size) break
                val item = items[idx]
                val cx = c * cw + cw / 2f
                canvas.save()
                canvas.clipRect(c * cw, top, (c + 1) * cw, top + cellH)
                val tr = shownTr.getOrNull(idx)
                if (tr.isNullOrEmpty()) {
                    // 无译文也画在词线（0.70 格高）上，与有译文的候选词保持同一水平线
                    val fm = wordPaint.fontMetrics
                    canvas.drawText(item.word, cx, top + cellH * 0.70f - (fm.ascent + fm.descent) / 2f, wordPaint)
                } else {
                    val fmW = wordPaint.fontMetrics
                    val fmT = trPaint.fontMetrics
                    canvas.drawText(tr, cx, top + cellH * 0.32f - (fmT.ascent + fmT.descent) / 2f, trPaint)
                    canvas.drawText(item.word, cx, top + cellH * 0.70f - (fmW.ascent + fmW.descent) / 2f, wordPaint)
                }
                canvas.restore()
            }
        }
    }

    private fun indexAt(x: Float, y: Float): Int {
        val cw = cellW()
        if (cw <= 0f) return -1
        val yy = y + scrollY
        val r = (yy / cellH).toInt()
        val c = (x / cw).toInt()
        if (r < 0 || c < 0 || c >= cols) return -1
        val idx = r * cols + c
        return if (idx in items.indices) idx else -1
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (items.isEmpty()) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                scroller.forceFinished(true)
                velocityTracker?.recycle()
                velocityTracker = VelocityTracker.obtain().also { it.addMovement(event) }
                downX = event.x
                downY = event.y
                downScroll = scrollY
                dragging = false
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                velocityTracker?.addMovement(event)
                val dy = event.y - downY
                val dx = event.x - downX
                if (!dragging && abs(dy) > touchSlop && abs(dy) > abs(dx)) dragging = true
                if (dragging) {
                    scrollY = (downScroll - dy).coerceIn(0f, maxScroll())
                    invalidate()
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                velocityTracker?.addMovement(event)
                if (!dragging) {
                    val idx = indexAt(event.x, event.y)
                    if (idx >= 0) {
                        onSelect?.invoke(idx)
                        performClick()
                    }
                } else {
                    velocityTracker?.computeCurrentVelocity(1000)
                    val vy = -(velocityTracker?.yVelocity ?: 0f)
                    if (abs(vy) > minFlingVelocity) {
                        scroller.fling(0, scrollY.toInt(), 0, vy.toInt(), 0, 0, 0, maxScroll().toInt())
                        postInvalidateOnAnimation()
                    }
                }
                velocityTracker?.recycle()
                velocityTracker = null
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                velocityTracker?.recycle()
                velocityTracker = null
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun computeScroll() {
        if (scroller.computeScrollOffset()) {
            scrollY = scroller.currY.toFloat()
            invalidate()
        }
    }
}
