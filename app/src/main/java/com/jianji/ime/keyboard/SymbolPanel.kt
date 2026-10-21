package com.jianji.ime.keyboard

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.text.TextPaint
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.widget.OverScroller
import com.jianji.ime.ImTheme
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * 符号面板（v0.4.1）：盖在键盘区上，分类标签在底部，符号网格可上下滑动。
 * 视觉与交互对齐表情面板：拖动/惯性滚动、点选上屏、点分类切换；右侧固定
 * [返回][⌫] 两键（中间留空），分类数据来自 [KeyboardLayouts.symbolTabs]。
 */
class SymbolPanel(context: Context) : View(context) {

    var onPick: ((String) -> Unit)? = null

    /** 点右侧「返回」：收起面板回键盘 */
    var onBack: (() -> Unit)? = null

    /** 点右侧「⌫」：删除一个字符 */
    var onBackspace: (() -> Unit)? = null

    private data class Cat(val name: String, val symbols: List<String>)

    private var cats: List<Cat> = emptyList()
    private var cur = 0

    private val density = resources.displayMetrics.density
    private fun dp(v: Float) = v * density

    private val cols = 4
    private val padH = dp(8f)
    private val gap = dp(6f)
    private val cellH = dp(42f)
    private val tabH = dp(36f)
    private val gridTop = dp(2f)
    private val sideW = dp(52f)

    private val bgPaint = Paint()
    private val dividerPaint = Paint()
    private val keyBgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val keyEdgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1f)
    }
    private val fnBgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val scrollPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val symbolPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
    }
    private val uiPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = dp(13f)
    }
    private val tabPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = dp(11.5f)
    }

    private var scrollY = 0f
    private val scroller = OverScroller(context)
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val minFlingVelocity = ViewConfiguration.get(context).scaledMinimumFlingVelocity
    private var velocityTracker: VelocityTracker? = null

    private enum class Side { NONE, BACK, BACKSPACE }

    private var downX = 0f
    private var downY = 0f
    private var downScroll = 0f
    private var downTab = -1
    private var downSide = Side.NONE
    private var downItem = -1
    private var downInGrid = false
    private var dragging = false

    private var pressedTab = -1
    private var pressedSide = Side.NONE
    private var pressedItem = -1

    init {
        isClickable = true
        contentDescription = "符号面板"
    }

    /** 注入分类数据；每次打开面板回到第一个分类。 */
    fun setCategories(tabs: List<SymbolTab>) {
        cats = tabs.map { tab ->
            Cat(
                tab.name,
                tab.pages.flatMap { page ->
                    page.rows.flatMap { row ->
                        row.keys.filter { it.action == KeyAction.TEXT }.map { it.text }
                    }
                },
            )
        }
        cur = 0
        scrollY = 0f
        scroller.forceFinished(true)
        invalidate()
    }

    private fun curCat(): Cat? = cats.getOrNull(cur)
    private fun items(): List<String> = curCat()?.symbols ?: emptyList()

    private fun gridBottom(): Float = max(0f, height - tabH)
    private fun viewportH(): Float = max(0f, gridBottom() - gridTop)
    private fun rowStep(): Float = cellH + gap
    private fun contentH(): Float {
        val n = items().size
        if (n == 0) return 0f
        val rows = (n + cols - 1) / cols
        return rows * cellH + (rows - 1).coerceAtLeast(0) * gap
    }

    private fun maxScroll(): Float = max(0f, contentH() - viewportH())
    private fun sideLeft(): Float = max(padH, width - padH - sideW)
    private fun symbolsRight(): Float = max(padH, sideLeft() - gap)
    private fun cellW(): Float {
        val avail = symbolsRight() - padH - gap * (cols - 1)
        return max(dp(1f), avail / cols)
    }

    private fun visibleRows(): Int {
        if (rowStep() <= 0f) return 1
        return max(1, ((viewportH() + gap) / rowStep()).toInt())
    }

    /** 删除键固定在「返回」下方一行（不再放到底部，避免中间空一截显得断开）。 */
    private fun backspaceRow(): Int = if (visibleRows() >= 2) 1 else 0

    private fun rowTop(row: Int): Float = gridTop + row * rowStep() - scrollY
    private fun cellRect(row: Int, col: Int): RectF {
        val w = cellW()
        val left = padH + col * (w + gap)
        val top = rowTop(row)
        return RectF(left, top, left + w, top + cellH)
    }

    private fun sideRect(row: Int): RectF {
        val top = gridTop + row * rowStep()
        val left = sideLeft()
        return RectF(left, top, left + sideW, top + cellH)
    }

    // —————— 绘制 ——————

    override fun onDraw(canvas: Canvas) {
        bgPaint.color = ImTheme.panelBg
        dividerPaint.color = ImTheme.divider
        keyBgPaint.color = ImTheme.keyBg
        keyEdgePaint.color = if (ImTheme.isLight) {
            ImTheme.withA(0xFF000000.toInt(), 0x28)
        } else {
            ImTheme.keyBorder
        }
        fnBgPaint.color = ImTheme.keyFnBg
        accentPaint.color = ImTheme.accent
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)
        drawSymbols(canvas)
        drawSide(canvas)
        drawTabs(canvas)
    }

    private fun drawSymbols(canvas: Canvas) {
        val list = items()
        if (list.isEmpty()) return
        val bottom = gridBottom()
        val viewport = viewportH()
        if (viewport <= 0f) return
        val first = max(0, (scrollY / rowStep()).toInt())
        val last = min((list.size + cols - 1) / cols - 1, ((scrollY + viewport) / rowStep()).toInt())
        canvas.save()
        canvas.clipRect(0f, gridTop, symbolsRight(), bottom)
        for (r in first..last) {
            for (c in 0 until cols) {
                val idx = r * cols + c
                if (idx >= list.size) break
                val rect = cellRect(r, c)
                if (rect.bottom < gridTop || rect.top > bottom) continue
                keyBgPaint.color = if (idx == pressedItem) ImTheme.keyPressed else ImTheme.keyBg
                canvas.drawRoundRect(rect, dp(12f), dp(12f), keyBgPaint)
                canvas.drawRoundRect(rect, dp(12f), dp(12f), keyEdgePaint)
                symbolPaint.color = ImTheme.keyText
                drawFittedText(canvas, list[idx], rect, symbolPaint)
            }
        }
        canvas.restore()
        drawScrollbar(canvas)
    }

    private fun drawFittedText(canvas: Canvas, text: String, rect: RectF, paint: TextPaint) {
        val maxW = max(dp(1f), rect.width() - dp(7f))
        paint.textSize = when {
            text.length <= 1 -> dp(19f)
            text.length <= 3 -> dp(15f)
            else -> dp(12.5f)
        }
        val measured = paint.measureText(text)
        if (measured > maxW) paint.textSize *= maxW / measured
        val fm = paint.fontMetrics
        canvas.drawText(text, rect.centerX(), rect.centerY() - (fm.ascent + fm.descent) / 2f, paint)
    }

    private fun drawScrollbar(canvas: Canvas) {
        val maxS = maxScroll()
        if (maxS <= 0f) return
        val trackTop = gridTop + dp(5f)
        val trackBottom = gridBottom() - dp(5f)
        val trackH = trackBottom - trackTop
        if (trackH <= 0f) return
        val thumbH = max(dp(22f), trackH * viewportH() / contentH())
        val y = trackTop + (trackH - thumbH) * (scrollY / maxS)
        scrollPaint.color = ImTheme.accentAlpha(0x55)
        val x = symbolsRight() + gap / 2f - dp(1f)
        canvas.drawRoundRect(RectF(x, y, x + dp(2f), y + thumbH), dp(1f), dp(1f), scrollPaint)
    }

    private fun drawSide(canvas: Canvas) {
        drawSideKey(canvas, sideRect(0), "返回", Side.BACK)
        drawSideKey(canvas, sideRect(backspaceRow()), "⌫", Side.BACKSPACE)
    }

    private fun drawSideKey(canvas: Canvas, rect: RectF, label: String, side: Side) {
        val pressed = side == pressedSide
        fnBgPaint.color = if (pressed) ImTheme.keyPressed else ImTheme.keyFnBg
        canvas.drawRoundRect(rect, dp(12f), dp(12f), fnBgPaint)
        canvas.drawRoundRect(rect, dp(12f), dp(12f), keyEdgePaint)
        uiPaint.color = if (pressed) ImTheme.accent else ImTheme.keyFnText
        if (side == Side.BACK) {
            uiPaint.textSize = dp(13f)
        } else {
            uiPaint.textSize = dp(20f)
        }
        val fm = uiPaint.fontMetrics
        canvas.drawText(label, rect.centerX(), rect.centerY() - (fm.ascent + fm.descent) / 2f, uiPaint)
    }

    private fun drawTabs(canvas: Canvas) {
        if (cats.isEmpty()) return
        val top = height - tabH
        canvas.drawRect(0f, top, width.toFloat(), top + dp(0.5f), dividerPaint)
        val cw = width.toFloat() / cats.size
        for (i in cats.indices) {
            val rect = RectF(i * cw, top, (i + 1) * cw, height.toFloat())
            if (i == cur) {
                accentPaint.color = ImTheme.accentAlpha(0x24)
                canvas.drawRect(rect, accentPaint)
                tabPaint.color = ImTheme.accent
            } else {
                tabPaint.color = ImTheme.keyFnText
            }
            val name = cats[i].name
            tabPaint.textSize = dp(11.5f)
            val maxW = cw - dp(6f)
            val measured = tabPaint.measureText(name)
            if (measured > maxW) tabPaint.textSize *= maxW / measured
            val fm = tabPaint.fontMetrics
            canvas.drawText(
                name,
                rect.centerX(),
                top + tabH / 2f - (fm.ascent + fm.descent) / 2f,
                tabPaint,
            )
        }
        val x = cur * cw
        accentPaint.color = ImTheme.accent
        canvas.drawRoundRect(
            RectF(x + dp(10f), height - dp(4f), x + cw - dp(10f), height - dp(2f)),
            dp(1f),
            dp(1f),
            accentPaint,
        )
    }

    // —————— 命中 ——————

    private fun tabAt(x: Float, y: Float): Int {
        if (cats.isEmpty() || y < height - tabH) return -1
        val cw = width.toFloat() / cats.size
        if (cw <= 0f) return -1
        return (x / cw).toInt().coerceIn(0, cats.lastIndex)
    }

    private fun sideAt(x: Float, y: Float): Side {
        if (x < sideLeft() || y < gridTop || y > gridBottom()) return Side.NONE
        if (y >= sideRect(0).top && y <= sideRect(0).bottom) return Side.BACK
        val bs = sideRect(backspaceRow())
        if (y >= bs.top && y <= bs.bottom) return Side.BACKSPACE
        return Side.NONE
    }

    private fun itemAt(x: Float, y: Float): Int {
        val list = items()
        if (list.isEmpty() || x < padH || x > symbolsRight() || y < gridTop || y > gridBottom()) return -1
        val step = rowStep()
        if (step <= 0f) return -1
        val row = ((y - gridTop + scrollY) / step).toInt()
        if (row < 0) return -1
        val rect = cellRect(row, 0)
        if (y < rect.top || y > rect.bottom) return -1
        val w = cellW()
        if (w <= 0f) return -1
        val col = ((x - padH) / (w + gap)).toInt().coerceIn(0, cols - 1)
        val cell = cellRect(row, col)
        if (x < cell.left || x > cell.right) return -1
        val idx = row * cols + col
        return if (idx in list.indices) idx else -1
    }

    // —————— 触控 ——————

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (cats.isEmpty()) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                scroller.forceFinished(true)
                velocityTracker?.recycle()
                velocityTracker = VelocityTracker.obtain().also { it.addMovement(event) }
                downX = event.x
                downY = event.y
                downScroll = scrollY
                dragging = false
                downTab = tabAt(event.x, event.y)
                downSide = if (downTab < 0) sideAt(event.x, event.y) else Side.NONE
                downItem = if (downTab < 0 && downSide == Side.NONE) itemAt(event.x, event.y) else -1
                downInGrid = downTab < 0 && downSide == Side.NONE && event.y < gridBottom()
                pressedTab = downTab
                pressedSide = downSide
                pressedItem = downItem
                invalidate()
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                velocityTracker?.addMovement(event)
                val dx = event.x - downX
                val dy = event.y - downY
                if (!dragging && downInGrid && abs(dy) > touchSlop && abs(dy) > abs(dx)) {
                    dragging = true
                    pressedItem = -1
                    invalidate()
                }
                if (dragging) {
                    scrollY = (downScroll - dy).coerceIn(0f, maxScroll())
                    invalidate()
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                velocityTracker?.addMovement(event)
                if (dragging) {
                    velocityTracker?.computeCurrentVelocity(1000)
                    val vy = -(velocityTracker?.yVelocity ?: 0f)
                    if (abs(vy) > minFlingVelocity) {
                        scroller.fling(0, scrollY.toInt(), 0, vy.toInt(), 0, 0, 0, maxScroll().toInt())
                        postInvalidateOnAnimation()
                    }
                } else if (downTab >= 0) {
                    if (tabAt(event.x, event.y) == downTab) {
                        selectTab(downTab)
                        performClick()
                    }
                } else if (downSide != Side.NONE) {
                    if (sideAt(event.x, event.y) == downSide) {
                        when (downSide) {
                            Side.BACK -> onBack?.invoke()
                            Side.BACKSPACE -> onBackspace?.invoke()
                            Side.NONE -> Unit
                        }
                        performClick()
                    }
                } else if (downItem >= 0) {
                    if (itemAt(event.x, event.y) == downItem) {
                        items().getOrNull(downItem)?.let { onPick?.invoke(it) }
                        performClick()
                    }
                }
                pressedTab = -1
                pressedSide = Side.NONE
                pressedItem = -1
                invalidate()
                velocityTracker?.recycle()
                velocityTracker = null
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                dragging = false
                pressedTab = -1
                pressedSide = Side.NONE
                pressedItem = -1
                velocityTracker?.recycle()
                velocityTracker = null
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun selectTab(i: Int) {
        if (i !in cats.indices || i == cur) return
        cur = i
        scrollY = 0f
        scroller.forceFinished(true)
        invalidate()
    }

    override fun computeScroll() {
        if (scroller.computeScrollOffset()) {
            scrollY = scroller.currY.toFloat()
            invalidate()
        }
    }

    override fun onDetachedFromWindow() {
        velocityTracker?.recycle()
        velocityTracker = null
        scroller.forceFinished(true)
        super.onDetachedFromWindow()
    }
}
