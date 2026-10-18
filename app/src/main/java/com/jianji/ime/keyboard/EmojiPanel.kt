package com.jianji.ime.keyboard

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.text.TextPaint
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.widget.OverScroller
import com.jianji.ime.ImTheme
import com.jianji.ime.engine.EmojiDict
import kotlin.math.abs
import kotlin.math.min

/**
 * 表情面板（M4）：盖在键盘区上，底部分类标签（两行）+ 网格滚动。
 * 点一个表情 = 上屏（回调 [onPick]，面板不关，可连点）；分类不足两行时，
 * 右下角空位画「‹ 返回键盘」按钮（[onBack]，M4.2b）。
 * 数据来自 emoji.bin（[EmojiDict]），分类「颜文字」用 3 列小字排版。
 */
class EmojiPanel(context: Context) : View(context) {

    var onPick: ((String) -> Unit)? = null

    /** 右下角返回键盘（收起表情面板） */
    var onBack: (() -> Unit)? = null

    private var cats: List<EmojiDict.Cat> = emptyList()
    private var cur = 0

    private val density = resources.displayMetrics.density
    private fun dp(v: Float) = v * density

    private val tabRows = 2
    private val tabCols = 6
    private val tabH = dp(30f)
    private val tabBarH = tabH * tabRows + dp(1f)
    private val cols = 8
    private val colsKaomoji = 3
    private val cellH = dp(42f)
    private val cellHKaomoji = dp(40f)

    private val bgPaint = Paint()
    private val dividerPaint = Paint()
    private val selPaint = Paint()
    private val accentPaint = Paint()

    /** 右下角返回按钮 */
    private val backRect = RectF()
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(2f)
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val iconPath = Path()
    private var backDown = false

    private val emojiPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = dp(26f)
    }
    private val kaoPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = dp(15f)
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
    private var downY = 0f
    private var downScroll = 0f
    private var dragging = false

    init {
        isClickable = true
        contentDescription = "表情面板"
    }

    fun setCategories(list: List<EmojiDict.Cat>) {
        cats = list
        cur = 0
        scrollY = 0f
        scroller.forceFinished(true)
        invalidate()
    }

    private fun curCat(): EmojiDict.Cat? = cats.getOrNull(cur)
    private fun isKaomoji(): Boolean = curCat()?.name?.contains("颜文字") == true
    private fun curCols(): Int = if (isKaomoji()) colsKaomoji else cols
    private fun curCellH(): Float = if (isKaomoji()) cellHKaomoji else cellH

    private fun gridTop(): Float = 0f

    private fun gridBottom(): Float = maxOf(0f, height - tabBarH)

    private fun rowCount(): Int {
        val c = curCat() ?: return 0
        return (c.items.size + curCols() - 1) / curCols()
    }

    private fun maxScroll(): Float = maxOf(0f, rowCount() * curCellH() - (gridBottom() - gridTop()))

    // —————— 绘制 ——————

    override fun onDraw(canvas: Canvas) {
        bgPaint.color = ImTheme.panelBg
        dividerPaint.color = ImTheme.divider
        selPaint.color = ImTheme.accentAlpha(0x29)
        accentPaint.color = ImTheme.accent
        kaoPaint.color = ImTheme.candText
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)
        drawGrid(canvas)
        drawTabs(canvas)
    }

    private fun drawGrid(canvas: Canvas) {
        val cat = curCat() ?: return
        val cols = curCols()
        val cellH = curCellH()
        val cw = width.toFloat() / cols
        val bottom = gridBottom()
        canvas.save()
        canvas.clipRect(0f, gridTop(), width.toFloat(), bottom)
        val first = maxOf(0, (scrollY / cellH).toInt())
        val last = minOf(rowCount() - 1, ((scrollY + bottom) / cellH).toInt())
        val kao = isKaomoji()
        for (r in first..last) {
            val top = r * cellH - scrollY
            for (c in 0 until cols) {
                val idx = r * cols + c
                if (idx >= cat.items.size) break
                val cx = c * cw + cw / 2f
                val cy = top + cellH / 2f
                val item = cat.items[idx]
                if (kao) {
                    val maxW = cw - dp(8f)
                    kaoPaint.textSize = dp(15f)
                    val w = kaoPaint.measureText(item)
                    if (w > maxW) kaoPaint.textSize = dp(15f) * maxW / w
                    canvas.drawText(item, cx, cy - (kaoPaint.ascent() + kaoPaint.descent()) / 2f, kaoPaint)
                } else {
                    canvas.drawText(item, cx, cy - (emojiPaint.ascent() + emojiPaint.descent()) / 2f, emojiPaint)
                }
            }
        }
        canvas.restore()
    }

    private fun drawTabs(canvas: Canvas) {
        if (cats.isEmpty()) return
        val top = height - tabBarH
        canvas.drawRect(0f, top, width.toFloat(), top + dp(0.5f), dividerPaint)
        val cw = width.toFloat() / tabCols
        val ty = tabH / 2f - (tabPaint.ascent() + tabPaint.descent()) / 2f
        for ((i, cat) in cats.withIndex()) {
            val row = i / tabCols
            val col = i % tabCols
            val x0 = col * cw
            val y0 = top + row * tabH
            val rect = RectF(x0, y0, x0 + cw, y0 + tabH)
            if (i == cur) {
                canvas.drawRect(rect, selPaint)
                tabPaint.color = ImTheme.accent
            } else {
                tabPaint.color = ImTheme.keyFnText
            }
            canvas.drawText(cat.name, rect.centerX(), y0 + ty, tabPaint)
        }
        // 选中下划线
        val row = cur / tabCols
        val col = cur % tabCols
        canvas.drawRect(col * cw + dp(8f), top + (row + 1) * tabH - dp(2f), (col + 1) * cw - dp(8f), top + (row + 1) * tabH, accentPaint)
        // 右下角空位：返回键盘按钮（分类占满两行时自动省去）
        val backIdx = tabRows * tabCols - 1
        if (cats.size <= backIdx) {
            val br = backIdx / tabCols
            val bc = backIdx % tabCols
            backRect.set(bc * cw, top + br * tabH, (bc + 1) * cw, top + (br + 1) * tabH)
            if (backDown) canvas.drawRect(backRect, selPaint)
            val cx = backRect.centerX()
            val cy = backRect.centerY()
            val s = dp(5f)
            iconPaint.color = if (backDown) ImTheme.accent else ImTheme.keyFnText
            iconPath.reset()
            iconPath.moveTo(cx + s * 0.55f, cy - s)
            iconPath.lineTo(cx - s * 0.6f, cy)
            iconPath.lineTo(cx + s * 0.55f, cy + s)
            canvas.drawPath(iconPath, iconPaint)
        }
    }

    /** 点到了右下角返回按钮（分类未占满两行时该位置才有按钮） */
    private fun backAt(x: Float, y: Float): Boolean {
        val backIdx = tabRows * tabCols - 1
        if (cats.size > backIdx) return false
        val top = height - tabBarH
        if (y < top) return false
        val row = ((y - top) / tabH).toInt()
        val col = (x / (width.toFloat() / tabCols)).toInt()
        if (row < 0 || row >= tabRows || col < 0 || col >= tabCols) return false
        return row * tabCols + col == backIdx
    }

    // —————— 触控 ——————

    private fun tabAt(x: Float, y: Float): Int {
        val top = height - tabBarH
        if (y < top) return -1
        val row = ((y - top) / tabH).toInt()
        val col = (x / (width.toFloat() / tabCols)).toInt()
        if (row < 0 || row >= tabRows || col < 0 || col >= tabCols) return -1
        val idx = row * tabCols + col
        return if (idx in cats.indices) idx else -1
    }

    private fun cellAt(x: Float, y: Float): String? {
        val cat = curCat() ?: return null
        val bottom = gridBottom()
        if (y < gridTop() || y > bottom) return null
        val cols = curCols()
        val cellH = curCellH()
        val cw = width.toFloat() / cols
        val c = (x / cw).toInt()
        val r = ((y + scrollY - gridTop()) / cellH).toInt()
        if (c < 0 || c >= cols || r < 0) return null
        val idx = r * cols + c
        return cat.items.getOrNull(idx)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (cats.isEmpty()) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                scroller.forceFinished(true)
                velocityTracker?.recycle()
                velocityTracker = VelocityTracker.obtain().also { it.addMovement(event) }
                downY = event.y
                downScroll = scrollY
                dragging = false
                backDown = backAt(event.x, event.y)
                if (backDown) invalidate()
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                velocityTracker?.addMovement(event)
                val dy = event.y - downY
                if (!dragging && abs(dy) > touchSlop) {
                    dragging = true
                    if (backDown) {
                        backDown = false
                        invalidate()
                    }
                }
                if (dragging && event.y < height - tabBarH) {
                    scrollY = (downScroll - dy).coerceIn(0f, maxScroll())
                    invalidate()
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                velocityTracker?.addMovement(event)
                if (!dragging) {
                    if (backDown && backAt(event.x, event.y)) {
                        onBack?.invoke()
                        performClick()
                    } else {
                        val tab = tabAt(event.x, event.y)
                        if (tab >= 0) {
                            if (tab != cur) {
                                cur = tab
                                scrollY = 0f
                                scroller.forceFinished(true)
                            }
                            invalidate()
                            performClick()
                        } else {
                            cellAt(event.x, event.y)?.let {
                                onPick?.invoke(it)
                                performClick()
                            }
                        }
                    }
                } else {
                    velocityTracker?.computeCurrentVelocity(1000)
                    val vy = -(velocityTracker?.yVelocity ?: 0f)
                    if (abs(vy) > minFlingVelocity) {
                        scroller.fling(0, scrollY.toInt(), 0, vy.toInt(), 0, 0, 0, maxScroll().toInt())
                        postInvalidateOnAnimation()
                    }
                }
                if (backDown) {
                    backDown = false
                    invalidate()
                }
                velocityTracker?.recycle()
                velocityTracker = null
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                if (backDown) {
                    backDown = false
                    invalidate()
                }
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
