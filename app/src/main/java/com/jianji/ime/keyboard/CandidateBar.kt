package com.jianji.ime.keyboard

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
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
 * 候选栏（键盘上方 56dp 预留区）：双行候选格横向滚动。
 * M2 只画词面（translation 为 null）；M3 起在词面上方补译文小字。
 * 自绘 + OverScroller，与 KeyboardView 同一套手感（拖动 / 惯性 / 点选）。
 * v0.2：候选上划 = 上屏该候选的译文；右上角生词等级角标（mark）。
 */
class CandidateBar(context: Context) : View(context) {

    data class Item(val word: String, val translation: String? = null, val mark: String? = null)

    /** 点选回调：下标对应最近一次 setItems 的列表 */
    var onSelect: ((Int) -> Unit)? = null

    /** 上划回调：上划候选格 = 上屏对应译文（下标同 setItems） */
    var onSwipeUp: ((Int) -> Unit)? = null

    private val items = ArrayList<Item>()
    private val widths = ArrayList<Float>()

    /** 实际绘制的译文（超长时省略号截断，防止单格吃掉整屏） */
    private val shownTr = ArrayList<String?>()
    private var totalWidth = 0f
    private var scrollX = 0f

    private val scroller = OverScroller(context)
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val minFlingVelocity = ViewConfiguration.get(context).scaledMinimumFlingVelocity
    private var velocityTracker: VelocityTracker? = null

    private val density = resources.displayMetrics.density
    private fun dp(v: Float) = v * density

    private val leaderPad = dp(6f)
    private val gap = dp(5f)
    private val trailingPad = dp(10f)
    private val minCellW = dp(60f)
    private val padH = dp(13f)
    private val wordSize = dp(18f)
    private val trSize = dp(10.5f)

    private val wordPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = wordSize
    }
    private val trPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = trSize
    }

    /** 生词等级角标（右上角小字；比译文再小一号，抬高一点避免和译文贴住） */
    private val markPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.RIGHT
        textSize = dp(8.5f)
    }

    /** 首选候选的高亮药丸（对齐视觉稿 .cand.sel：accent 16% 底 + 50% 描边） */
    private val selFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val selStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1f)
    }
    private val pillRect = RectF()
    private val pillRadius = dp(12f)
    private val pillInsetH = dp(2f)
    private val pillInsetV = dp(5f)

    private var downX = 0f
    private var downY = 0f
    private var downScroll = 0f
    private var dragging = false

    /** 上划判定位移（点选 vs 上划译文） */
    private val swipeUpSlop = dp(26f)

    init {
        isClickable = true
        contentDescription = "候选栏"
    }

    fun setItems(list: List<Item>) {
        // 内容没变：保留滚动位置，也不重画（高频重建时不闪、不跳回开头）
        if (list == items) return
        items.clear()
        items.addAll(list)
        widths.clear()
        shownTr.clear()
        wordPaint.textSize = wordSize
        trPaint.textSize = trSize
        val maxTrW = resources.displayMetrics.widthPixels / 2f
        totalWidth = leaderPad + trailingPad
        for (it in items) {
            var w = maxOf(minCellW, wordPaint.measureText(it.word) + padH * 2)
            val tr = it.translation
            if (!tr.isNullOrEmpty()) {
                val shown = if (trPaint.measureText(tr) > maxTrW) {
                    TextUtils.ellipsize(tr, trPaint, maxTrW, TextUtils.TruncateAt.END).toString()
                } else {
                    tr
                }
                shownTr.add(shown)
                w = maxOf(w, trPaint.measureText(shown) + padH * 2)
            } else {
                shownTr.add(null)
            }
            widths.add(w)
            totalWidth += w + gap
        }
        scrollX = 0f
        scroller.forceFinished(true)
        invalidate()
    }

    fun clear() = setItems(emptyList())

    private fun maxScroll(): Float = maxOf(0f, totalWidth - width)

    override fun onDraw(canvas: Canvas) {
        if (items.isEmpty()) return
        val h = height.toFloat()
        wordPaint.color = ImTheme.candText
        trPaint.color = ImTheme.candTranslation
        selFill.color = ImTheme.accentAlpha(0x29)
        selStroke.color = ImTheme.accentAlpha(0x80)
        wordPaint.textSize = wordSize
        trPaint.textSize = trSize
        var x = leaderPad - scrollX
        for (k in items.indices) {
            val w = widths[k]
            val item = items[k]
            val cx = x + w / 2f
            if (cx + w / 2f >= 0f && cx - w / 2f <= width.toFloat()) {
                if (k == 0) {
                    pillRect.set(x + pillInsetH, pillInsetV, x + w - pillInsetH, h - pillInsetV)
                    canvas.drawRoundRect(pillRect, pillRadius, pillRadius, selFill)
                    canvas.drawRoundRect(pillRect, pillRadius, pillRadius, selStroke)
                }
                val tr = shownTr.getOrNull(k)
                if (tr.isNullOrEmpty()) {
                    // 无译文也画在词线（0.70h）上，与有译文的候选词保持同一水平线
                    val fm = wordPaint.fontMetrics
                    canvas.drawText(item.word, cx, h * 0.70f - (fm.ascent + fm.descent) / 2f, wordPaint)
                } else {
                    val fmW = wordPaint.fontMetrics
                    val fmT = trPaint.fontMetrics
                    canvas.drawText(tr, cx, h * 0.34f - (fmT.ascent + fmT.descent) / 2f, trPaint)
                    canvas.drawText(item.word, cx, h * 0.70f - (fmW.ascent + fmW.descent) / 2f, wordPaint)
                }
                // 生词等级角标（B2 / N1 之类，右上角；入门级不标）
                val mk = item.mark
                if (mk != null) {
                    markPaint.color = ImTheme.candMark
                    val fmM = markPaint.fontMetrics
                    canvas.drawText(mk, x + w - dp(6f), h * 0.17f - (fmM.ascent + fmM.descent) / 2f, markPaint)
                }
            }
            x += w + gap
        }
    }

    private fun indexAt(tx: Float): Int {
        var x = leaderPad - scrollX
        for (k in items.indices) {
            val w = widths[k]
            if (tx >= x && tx <= x + w) return k
            x += w + gap
        }
        return -1
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
                downScroll = scrollX
                dragging = false
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                velocityTracker?.addMovement(event)
                val dx = event.x - downX
                val dy = event.y - downY
                if (!dragging && abs(dx) > touchSlop && abs(dx) > abs(dy)) dragging = true
                if (dragging) {
                    scrollX = (downScroll - dx).coerceIn(0f, maxScroll())
                    invalidate()
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                velocityTracker?.addMovement(event)
                if (!dragging) {
                    val idx = indexAt(event.x)
                    if (idx >= 0) {
                        if (downY - event.y > swipeUpSlop) {
                            // 上划 = 上屏该候选对应的译文（没有译文的由 Service 忽略）
                            onSwipeUp?.invoke(idx)
                        } else {
                            onSelect?.invoke(idx)
                            performClick()
                        }
                    }
                } else {
                    velocityTracker?.computeCurrentVelocity(1000)
                    val vx = -(velocityTracker?.xVelocity ?: 0f)
                    if (abs(vx) > minFlingVelocity) {
                        scroller.fling(scrollX.toInt(), 0, vx.toInt(), 0, 0, maxScroll().toInt(), 0, 0)
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
            scrollX = scroller.currX.toFloat()
            invalidate()
        }
    }
}
