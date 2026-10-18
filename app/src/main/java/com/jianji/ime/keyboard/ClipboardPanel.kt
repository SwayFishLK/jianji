package com.jianji.ime.keyboard

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.widget.OverScroller
import com.jianji.ime.ClipboardStore
import com.jianji.ime.ImTheme
import kotlin.math.abs

/**
 * 剪贴板面板（M4.2 搜狗风格改版）：盖在键盘区上。
 *
 * - 顶栏：‹ 关闭 + 分段标签 [剪贴板|常用语] + ⚙ 设置（回调 [onSettings] / [onClose]）；
 * - 状态行：共 N/50 条内容 + [清空]（点一次变「确认清空」，再点生效）；
 * - 卡片列表：点一条上屏（回调 [onCommit]）；超两行的卡片有 ˅/˄ 可展开收起；
 * - 左划卡片露出操作按钮：剪贴板 = [＋存常用语][🗑删除]，常用语 = [🗑删除]（替代长按）；
 * - 数据读写走 [ClipboardStore]，改完 [refresh] / 内部 reload 重画。
 */
class ClipboardPanel(context: Context) : View(context) {

    var onCommit: ((String) -> Unit)? = null
    var onSettings: (() -> Unit)? = null
    var onClose: (() -> Unit)? = null

    /** 0 = 剪贴板，1 = 常用语 */
    private var tab = 0

    private class Item(val text: String) {
        var top = 0f
        var h = 0f
        var expanded = false
        var hasMore = false
        var layout: StaticLayout? = null
        var fullLayout: StaticLayout? = null
    }

    private val items = ArrayList<Item>()
    private val history = ArrayList<String>()
    private val phrases = ArrayList<String>()

    /** 展开过的条目（默认收起；跨刷新保留） */
    private val expandedTexts = HashSet<String>()

    private var contentH = 0f

    // —————— 尺寸 ——————

    private val density = resources.displayMetrics.density
    private fun dp(v: Float) = v * density

    private val headerH = dp(46f)
    private val statusH = dp(28f)
    private val cardPadH = dp(12f)
    private val cardPadV = dp(11f)
    private val cardGap = dp(8f)
    private val cardRadius = dp(12f)
    private val cardTextPad = dp(14f)
    private val chevronZone = dp(30f)
    private val minCardH = dp(46f)
    private val segW = dp(184f)
    private val segH = dp(32f)

    /** 左划操作按钮：半径与两种露出宽度 */
    private val btnR = dp(20f)
    private val revealClip = dp(116f)
    private val revealPhrase = dp(58f)

    // —————— 画笔 ——————

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val accentFill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dividerPaint = Paint()
    private val iconStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(2f)
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val iconFill = Paint(Paint.ANTI_ALIAS_FLAG)

    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = dp(15f) }
    private val uiPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val statusPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)

    private val cardRect = RectF()
    private val segRect = RectF()
    private val pillRect = RectF()
    private val bodyRect = RectF()
    private val bgPath = Path()
    private val chevron = Path()

    // —————— 滚动 / 左划 / 按压状态 ——————

    private var scrollY = 0f
    private val scroller = OverScroller(context)
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val minFlingVelocity = ViewConfiguration.get(context).scaledMinimumFlingVelocity
    private var velocityTracker: VelocityTracker? = null

    private var downX = 0f
    private var downY = 0f
    private var downScroll = 0f
    private var downRow = -1
    private var downBtn = -1
    private var downChevron = false
    private var dragging = false
    private var swiping = false
    private var swipeBase = 0f
    private var pressedRow = -1

    /** 当前被左划的行（-1 = 无）；[slide] 为其左移距离 0..maxReveal */
    private var slideRow = -1
    private var slide = 0f
    private var animator: ValueAnimator? = null

    /** 状态行临时消息（已存为常用语 / 已删除…） */
    private var flashMsg: String? = null
    private val flashClear = Runnable {
        flashMsg = null
        invalidate()
    }

    /** 清空二次确认 */
    private var clearArmed = false
    private val armClear = Runnable {
        clearArmed = false
        invalidate()
    }

    init {
        isClickable = true
        contentDescription = "剪贴板面板"
    }

    // —————— 数据 ——————

    /** 从存储重新装载常用语与剪贴板历史（面板每次打开、增删后调用） */
    fun refresh() {
        history.clear()
        history.addAll(ClipboardStore.history(context))
        phrases.clear()
        phrases.addAll(ClipboardStore.phrases(context))
        slideRow = -1
        slide = 0f
        animator?.cancel()
        clearArmed = false
        removeCallbacks(armClear)
        flashMsg = null
        removeCallbacks(flashClear)
        rebuild(preserveScroll = false)
    }

    private fun reload() {
        history.clear()
        history.addAll(ClipboardStore.history(context))
        phrases.clear()
        phrases.addAll(ClipboardStore.phrases(context))
    }

    private fun curList(): List<String> = if (tab == 0) history else phrases

    private fun maxReveal(): Float = if (tab == 0) revealClip else revealPhrase

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        rebuild(preserveScroll = true)
    }

    private fun rebuild(preserveScroll: Boolean) {
        val w = width
        items.clear()
        if (w > 0) {
            textPaint.textSize = dp(15f)
            val cardW = w - cardPadH * 2
            val textW = (cardW - cardTextPad * 2 - chevronZone).toInt().coerceAtLeast(dp(60f).toInt())
            for (t in curList()) {
                val it = Item(t)
                it.expanded = expandedTexts.contains(t)
                it.layout = buildLayout(t, textW, 2)
                it.hasMore = ellipsized(it.layout)
                if (it.expanded) it.fullLayout = buildLayout(t, textW, 14)
                items.add(it)
            }
        }
        var y = dp(6f)
        for (it in items) {
            it.top = y
            val textH = if (it.expanded) (it.fullLayout?.height ?: 0) else (it.layout?.height ?: 0)
            it.h = maxOf(minCardH, textH + cardPadV * 2)
            y += it.h + cardGap
        }
        contentH = y + dp(8f)
        if (!preserveScroll) scrollY = 0f
        scrollY = scrollY.coerceIn(0f, maxScroll())
        scroller.forceFinished(true)
        slide = 0f
        slideRow = -1
        invalidate()
    }

    private fun buildLayout(text: String, textW: Int, maxLines: Int): StaticLayout =
        StaticLayout.Builder.obtain(text, 0, text.length, textPaint, textW)
            .setMaxLines(maxLines)
            .setEllipsize(TextUtils.TruncateAt.END)
            .setLineSpacing(0f, 1.12f)
            .setIncludePad(false)
            .build()

    private fun ellipsized(l: StaticLayout?): Boolean {
        l ?: return false
        for (i in 0 until l.lineCount) if (l.getEllipsisCount(i) > 0) return true
        return false
    }

    private fun maxScroll(): Float = maxOf(0f, contentH - (height - headerH - statusH))

    private val clearZoneW = dp(84f)

    // —————— 绘制 ——————

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        cardPaint.color = ImTheme.cardBg
        pillPaint.color = ImTheme.keyPressed
        accentFill.color = ImTheme.accentAlpha(0x29)
        dividerPaint.color = ImTheme.divider
        textPaint.color = ImTheme.candText

        // 底：顶部两角微圆（Sogou 卡片面板感）
        bgPath.reset()
        bgPath.addRoundRect(RectF(0f, 0f, w, height + dp(24f)), dp(14f), dp(14f), Path.Direction.CW)
        canvas.drawPath(bgPath, bgPaint)

        drawHeader(canvas)
        drawStatus(canvas)
        canvas.save()
        val listTop = headerH + statusH
        canvas.clipRect(0f, listTop, w, height.toFloat())
        drawList(canvas, listTop)
        canvas.restore()
    }

    private fun drawHeader(canvas: Canvas) {
        val cy = headerH / 2f
        // ‹ 返回
        iconStroke.color = ImTheme.keyFnText
        chevronPath(canvas, dp(16f), cy, dp(6f), down = false, forceLeft = true)

        // 分段标签
        val cx = width / 2f
        segRect.set(cx - segW / 2f, (headerH - segH) / 2f, cx + segW / 2f, (headerH + segH) / 2f)
        canvas.drawRoundRect(segRect, segH / 2f, segH / 2f, cardPaint)
        val half = segW / 2f
        pillRect.set(segRect)
        if (tab == 0) pillRect.right = segRect.left + half else pillRect.left = segRect.left + half
        pillRect.inset(dp(3f), dp(3f))
        val pr = (segH - dp(6f)) / 2f
        canvas.drawRoundRect(pillRect, pr, pr, pillPaint)
        uiPaint.textSize = dp(13f)
        val ty = cy - (uiPaint.ascent() + uiPaint.descent()) / 2f
        uiPaint.color = if (tab == 0) ImTheme.accent else ImTheme.keyFnText
        canvas.drawText("剪贴板", cx - half / 2f, ty, uiPaint)
        uiPaint.color = if (tab == 1) ImTheme.accent else ImTheme.keyFnText
        canvas.drawText("常用语", cx + half / 2f, ty, uiPaint)

        // ⚙ 设置
        drawGear(canvas, width - dp(24f), cy)

        canvas.drawRect(0f, headerH - dp(0.5f), width.toFloat(), headerH, dividerPaint)
    }

    private fun drawStatus(canvas: Canvas) {
        val cy = headerH + statusH / 2f
        statusPaint.textSize = dp(11f)
        val ty = cy - (statusPaint.ascent() + statusPaint.descent()) / 2f
        val msg = flashMsg
        statusPaint.color = if (msg != null) ImTheme.accent else ImTheme.candTranslation
        val label = msg ?: if (tab == 0) {
            "共 ${history.size}/${ClipboardStore.HISTORY_MAX} 条内容"
        } else {
            "共 ${phrases.size} 条常用语"
        }
        canvas.drawText(label, cardPadH, ty, statusPaint)

        if (tab == 0 && history.isNotEmpty() && msg == null) {
            uiPaint.textSize = dp(12.5f)
            uiPaint.color = if (clearArmed) ImTheme.warn else ImTheme.keyFnText
            val t = if (clearArmed) "确认清空" else "清空"
            canvas.drawText(t, width - cardPadH - uiPaint.measureText(t) / 2f, ty, uiPaint)
        }
    }

    private fun drawList(canvas: Canvas, listTop: Float) {
        if (items.isEmpty()) {
            drawEmpty(canvas, listTop)
            return
        }
        val w = width.toFloat()
        for (i in items.indices) {
            val it = items[i]
            val y0 = listTop + it.top - scrollY
            if (y0 > height || y0 + it.h < listTop) continue
            val rowSlide = if (i == slideRow) slide else 0f
            val left = cardPadH - rowSlide
            val right = w - cardPadH - rowSlide

            // 左划露出的操作按钮（先画，卡片滑过去盖住未露出的部分）
            if (rowSlide > 0.5f) drawRowActions(canvas, i, y0)

            cardRect.set(left, y0, right, y0 + it.h)
            canvas.drawRoundRect(cardRect, cardRadius, cardRadius, cardPaint)
            if (i == pressedRow) canvas.drawRoundRect(cardRect, cardRadius, cardRadius, accentFill)

            val layout = if (it.expanded) it.fullLayout else it.layout
            if (layout != null) {
                canvas.save()
                canvas.translate(left + cardTextPad, y0 + cardPadV)
                layout.draw(canvas)
                canvas.restore()
            }

            if (it.hasMore) {
                iconStroke.color = ImTheme.keyFnText
                chevronPath(canvas, right - dp(16f), y0 + it.h / 2f, dp(5f), down = !it.expanded, forceLeft = false)
            }
        }
    }

    /** 左划露出：＋（存常用语，仅剪贴板页）与 🗑（删除） */
    private fun drawRowActions(canvas: Canvas, i: Int, y0: Float) {
        val cy = y0 + items[i].h / 2f
        val w = width.toFloat()
        val delCx = w - cardPadH - btnR
        val addCx = delCx - btnR * 2 - dp(12f)
        if (tab == 0) drawRoundBtn(canvas, addCx, cy, plus = true)
        drawRoundBtn(canvas, delCx, cy, plus = false)
    }

    private fun drawRoundBtn(canvas: Canvas, cx: Float, cy: Float, plus: Boolean) {
        iconFill.color = ImTheme.keyFnBg
        canvas.drawCircle(cx, cy, btnR, iconFill)
        if (plus) {
            iconStroke.color = ImTheme.accent
            canvas.drawLine(cx - dp(6f), cy, cx + dp(6f), cy, iconStroke)
            canvas.drawLine(cx, cy - dp(6f), cx, cy + dp(6f), iconStroke)
        } else {
            iconStroke.color = ImTheme.warn
            // 盖 + 提手
            canvas.drawLine(cx - dp(7.5f), cy - dp(4.6f), cx + dp(7.5f), cy - dp(4.6f), iconStroke)
            canvas.drawLine(cx - dp(2.2f), cy - dp(4.6f), cx - dp(2.2f), cy - dp(6.4f), iconStroke)
            canvas.drawLine(cx + dp(2.2f), cy - dp(4.6f), cx + dp(2.2f), cy - dp(6.4f), iconStroke)
            canvas.drawLine(cx - dp(2.2f), cy - dp(6.4f), cx + dp(2.2f), cy - dp(6.4f), iconStroke)
            // 桶身
            bodyRect.set(cx - dp(5.6f), cy - dp(3.0f), cx + dp(5.6f), cy + dp(7.0f))
            canvas.drawRoundRect(bodyRect, dp(1.8f), dp(1.8f), iconStroke)
        }
    }

    private fun drawEmpty(canvas: Canvas, listTop: Float) {
        val cx = width / 2f
        val cy = listTop + (height - listTop) / 2f - dp(16f)
        uiPaint.textSize = dp(13.5f)
        uiPaint.color = ImTheme.candText
        canvas.drawText(if (tab == 0) "剪贴板是空的" else "还没有常用语", cx, cy, uiPaint)
        uiPaint.textSize = dp(11.5f)
        uiPaint.color = ImTheme.candTranslation
        canvas.drawText(
            if (tab == 0) "复制的文字会出现在这里，左划可存为常用语或删除" else "在剪贴板里左划一条，点 ＋ 存为常用语",
            cx, cy + dp(22f), uiPaint,
        )
    }

    private fun drawGear(canvas: Canvas, cx: Float, cy: Float) {
        val r = dp(6f)
        val sw = iconStroke.strokeWidth
        iconStroke.strokeWidth = dp(1.8f)
        iconStroke.color = ImTheme.keyFnText
        canvas.drawCircle(cx, cy, r, iconStroke)
        canvas.drawCircle(cx, cy, dp(2.2f), iconStroke)
        for (i in 0 until 8) {
            val a = Math.toRadians((i * 45).toDouble())
            val c = Math.cos(a).toFloat()
            val s = Math.sin(a).toFloat()
            canvas.drawLine(
                cx + c * (r - dp(0.3f)), cy + s * (r - dp(0.3f)),
                cx + c * (r + dp(2.4f)), cy + s * (r + dp(2.4f)),
                iconStroke,
            )
        }
        iconStroke.strokeWidth = sw
    }

    private fun chevronPath(canvas: Canvas, cx: Float, cy: Float, s: Float, down: Boolean, forceLeft: Boolean) {
        val dy = if (down) 1f else -1f
        chevron.reset()
        if (forceLeft) {
            chevron.moveTo(cx + s * 0.7f, cy - s)
            chevron.lineTo(cx - s * 0.5f, cy)
            chevron.lineTo(cx + s * 0.7f, cy + s)
        } else {
            chevron.moveTo(cx - s, cy - s * 0.5f * dy)
            chevron.lineTo(cx, cy + s * 0.6f * dy)
            chevron.lineTo(cx + s, cy - s * 0.5f * dy)
        }
        canvas.drawPath(chevron, iconStroke)
    }

    // —————— 命中 ——————

    private fun listTop(): Float = headerH + statusH

    private fun itemAt(y: Float): Int {
        if (y < listTop()) return -1
        val cy = y - listTop() + scrollY
        for (i in items.indices) {
            val it = items[i]
            if (cy >= it.top && cy < it.top + it.h) return i
        }
        return -1
    }

    private fun buttonAt(x: Float, y: Float): Int {
        if (slideRow < 0 || slide < maxReveal() - dp(2f)) return -1
        if (itemAt(y) != slideRow) return -1
        val w = width.toFloat()
        val delCx = w - cardPadH - btnR
        val addCx = delCx - btnR * 2 - dp(12f)
        if (abs(x - delCx) <= btnR + dp(4f)) return 1
        if (tab == 0 && abs(x - addCx) <= btnR + dp(4f)) return 0
        return -1
    }

    private fun chevronAt(x: Float, y: Float): Int {
        val i = itemAt(y) 
        if (i < 0) return -1
        val it = items[i]
        if (!it.hasMore) return -1
        val rowSlide = if (i == slideRow) slide else 0f
        val right = width.toFloat() - cardPadH - rowSlide
        return if (x >= right - chevronZone - dp(6f)) i else -1
    }

    // —————— 触控 ——————

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                animator?.cancel()
                velocityTracker?.recycle()
                velocityTracker = VelocityTracker.obtain().also { it.addMovement(event) }
                downX = event.x
                downY = event.y
                downScroll = scrollY
                dragging = false
                swiping = false
                downBtn = -1
                downChevron = false
                downRow = itemAt(event.y)
                if (downRow >= 0) {
                    if (slideRow == downRow) downBtn = buttonAt(event.x, event.y)
                    if (downBtn < 0) downChevron = chevronAt(event.x, event.y) == downRow
                }
                pressedRow = if (downBtn < 0 && downRow >= 0) downRow else -1
                invalidate()
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                velocityTracker?.addMovement(event)
                val dx = event.x - downX
                val dy = event.y - downY
                if (!swiping && !dragging) {
                    if (abs(dy) > touchSlop && abs(dy) >= abs(dx)) {
                        dragging = true
                        pressedRow = -1
                        downBtn = -1
                        downChevron = false
                        animator?.cancel()
                        slide = 0f
                        slideRow = -1
                    } else if (abs(dx) > touchSlop && abs(dx) > abs(dy) && downRow >= 0) {
                        swiping = true
                        pressedRow = -1
                        if (slideRow != downRow) {
                            slide = 0f
                            slideRow = downRow
                        }
                        swipeBase = slide
                    }
                }
                if (dragging) {
                    scrollY = (downScroll - dy).coerceIn(0f, maxScroll())
                    invalidate()
                } else if (swiping) {
                    slide = (swipeBase - dx).coerceIn(0f, maxReveal())
                    invalidate()
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                velocityTracker?.addMovement(event)
                when {
                    swiping -> {
                        velocityTracker?.computeCurrentVelocity(1000)
                        val vx = velocityTracker?.xVelocity ?: 0f
                        val maxR = maxReveal()
                        if (slide > maxR * 0.45f || vx < -minFlingVelocity) {
                            animateSlide(maxR, clearRow = false)
                        } else {
                            animateSlide(0f, clearRow = true)
                        }
                    }

                    dragging -> {
                        velocityTracker?.computeCurrentVelocity(1000)
                        val vy = -(velocityTracker?.yVelocity ?: 0f)
                        if (abs(vy) > minFlingVelocity) {
                            scroller.fling(0, scrollY.toInt(), 0, vy.toInt(), 0, 0, 0, maxScroll().toInt())
                            postInvalidateOnAnimation()
                        }
                    }

                    else -> tapUp(event.x, event.y)
                }
                pressedRow = -1
                velocityTracker?.recycle()
                velocityTracker = null
                invalidate()
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                pressedRow = -1
                velocityTracker?.recycle()
                velocityTracker = null
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun tapUp(x: Float, y: Float) {
        // 左划按钮
        if (downBtn >= 0) {
            if (buttonAt(x, y) == downBtn) {
                when (downBtn) {
                    0 -> addToPhrases()
                    1 -> deleteCurrent()
                }
                animateSlide(0f, clearRow = true)
            }
            return
        }
        // 展开箭头
        if (downChevron) {
            val i = chevronAt(x, y)
            if (i >= 0) toggleExpand(i)
            return
        }
        // 有行打开时，点任何地方先收起
        if (slideRow >= 0 && slide > 0.5f) {
            animateSlide(0f, clearRow = true)
            return
        }
        // 顶栏
        if (y <= headerH) {
            when {
                x <= dp(52f) -> onClose?.invoke()
                x >= width - dp(52f) -> onSettings?.invoke()
                else -> {
                    val cx = width / 2f
                    if (x >= cx - segW / 2f && x <= cx + segW / 2f) {
                        val t = if (x < cx) 0 else 1
                        if (t != tab) {
                            tab = t
                            rebuild(preserveScroll = false)
                            performClick()
                        }
                    }
                }
            }
            return
        }
        // 状态行：清空（二次确认）
        if (y <= listTop()) {
            if (tab == 0 && flashMsg == null && x > width - cardPadH - clearZoneW) {
                if (clearArmed) {
                    ClipboardStore.clearHistory(context)
                    clearArmed = false
                    removeCallbacks(armClear)
                    history.clear()
                    flash("已清空剪贴板")
                    rebuild(preserveScroll = true)
                    performClick()
                } else {
                    clearArmed = true
                    postDelayed(armClear, 2500L)
                    invalidate()
                }
            } else if (clearArmed) {
                clearArmed = false
                removeCallbacks(armClear)
                invalidate()
            }
            return
        }
        // 列表卡片：上屏
        val i = itemAt(y)
        if (i >= 0) {
            onCommit?.invoke(items[i].text)
            performClick()
        }
    }

    // —————— 动作 ——————

    private fun addToPhrases() {
        val text = items.getOrNull(slideRow)?.text ?: return
        val ok = ClipboardStore.addPhrase(context, text)
        phrases.clear()
        phrases.addAll(ClipboardStore.phrases(context))
        flash(if (ok) "已存为常用语" else "已在常用语中或已满")
    }

    private fun deleteCurrent() {
        val it = items.getOrNull(slideRow) ?: return
        if (tab == 0) {
            ClipboardStore.removeHistory(context, it.text)
        } else {
            ClipboardStore.removePhrase(context, it.text)
        }
        reload()
        flash("已删除")
        rebuild(preserveScroll = true)
    }

    private fun toggleExpand(i: Int) {
        val it = items.getOrNull(i) ?: return
        if (!it.hasMore) return
        it.expanded = !it.expanded
        if (it.expanded) expandedTexts.add(it.text) else expandedTexts.remove(it.text)
        rebuild(preserveScroll = true)
    }

    private fun flash(msg: String) {
        flashMsg = msg
        removeCallbacks(flashClear)
        postDelayed(flashClear, 1800L)
        invalidate()
    }

    private fun animateSlide(target: Float, clearRow: Boolean) {
        animator?.cancel()
        val from = slide
        if (abs(from - target) < dp(1f)) {
            slide = target
            if (clearRow) slideRow = -1
            invalidate()
            return
        }
        animator = ValueAnimator.ofFloat(from, target).apply {
            duration = 150L
            addUpdateListener {
                slide = it.animatedValue as Float
                invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (clearRow) {
                        slide = 0f
                        slideRow = -1
                    }
                    invalidate()
                }
            })
            start()
        }
    }

    override fun computeScroll() {
        if (scroller.computeScrollOffset()) {
            scrollY = scroller.currY.toFloat()
            invalidate()
        }
    }
}
