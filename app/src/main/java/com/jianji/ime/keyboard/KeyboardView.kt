package com.jianji.ime.keyboard

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import com.jianji.ime.ImTheme
import java.util.Locale

/**
 * 长按备选条宿主：由外部（Service）提供显示，弹层显示在按键正上方。
 */
interface OptionsHost {
    /** 显示备选条；anchor 为按键矩形（KeyboardView 坐标系） */
    fun show(items: List<String>, highlight: Int, anchor: RectF)

    /** 手指屏幕 x 坐标 → 更新高亮；返回 true 表示高亮发生了变化 */
    fun moveHighlightToScreenX(screenX: Float): Boolean

    /** 隐藏备选条 */
    fun hide()

    /** 当前高亮项；null 表示未显示 */
    val currentItem: String?
}

/**
 * 自绘 26 键键盘视图（方案 A「墨夜」，布局参照搜狗习惯）。
 *
 * - 多指独立跟踪 + 键缝命中容差；快速连打不丢键
 * - 字母键上方小字显示长按备选；长按弹备选条（大写/备选/小写），松手上屏
 * - ⌫ 上划清空输入框、长按连删；⇧ 单击一次性大写 / 双击锁定
 * - 数字页、符号页（分类标签栏）由本视图内部处理页切换
 */
class KeyboardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    // —————— 对外状态与回调 ——————

    var layout: KeyboardLayout = KeyboardLayouts.qwerty
        private set(value) {
            field = value
            dismissOptions()
            rebuild()
            requestLayout()
            invalidate()
        }

    var shiftState: ShiftState = ShiftState.OFF
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    /** 英文模式（中/英键状态；仅影响绘制高亮） */
    var langEnglish: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    /** 符号页分类（Service 初始化时注入） */
    var symbolTabs: List<SymbolTab> = emptyList()

    /** 按下反馈（音效/振动） */
    var onKeyFeedback: (() -> Unit)? = null

    /** 抬手触发（松手时手指下的键） */
    var onKey: ((KeyDef) -> Unit)? = null

    /** 长按备选条选中了一个文本 */
    var onAlternate: ((String) -> Unit)? = null

    /** ⌫ 上划 → 请求清空输入框 */
    var onClearInput: (() -> Unit)? = null

    /** 备选条宿主（由 Service 提供） */
    var optionsHost: OptionsHost? = null

    // —————— 页切换 ——————

    fun showLetterPage() {
        symbolPageIndex = 0
        layout = KeyboardLayouts.qwerty
    }

    fun showNumberPage() {
        layout = KeyboardLayouts.numbers
    }

    fun showSymbolPage() {
        symbolTabIndex = 0
        symbolPageIndex = 0
        layout = KeyboardLayouts.symbolsFirstPage
    }

    private var symbolTabIndex = 0
    private var symbolPageIndex = 0

    private fun switchSymbolTab(i: Int) {
        if (i == symbolTabIndex) return
        val tab = symbolTabs.getOrNull(i) ?: return
        symbolTabIndex = i
        symbolPageIndex = 0
        layout = tab.pages.first()
    }

    private fun moveSymbolPage(delta: Int) {
        val tab = symbolTabs.getOrNull(symbolTabIndex) ?: return
        val next = (symbolPageIndex + delta).coerceIn(0, tab.pages.lastIndex)
        if (next != symbolPageIndex) {
            symbolPageIndex = next
            layout = tab.pages[next]
            onKeyFeedback?.invoke()
        }
    }

    // —————— 尺寸 ——————

    private val density = resources.displayMetrics.density
    private fun dp(v: Float) = v * density

    private val keyGap = dp(6f)
    private val keyRadius = dp(12f)
    private val padH = dp(8f)
    private val padV = dp(8f)
    private val hitSlop = dp(8f)
    private val clearSwipeUp = dp(40f)
    private val optionsCancelBelow = dp(52f)

    /** 标签栏顶部 y（无标签时为负） */
    private var tabTop = -1f

    // —————— 画笔与颜色 ——————

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val iconStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(2.3f)
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    /** 实心图标（⇧ 锁定、回车小鱼）用；用完记得把 style 还原成 FILL */
    private val iconFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    private val colorKeyBg get() = ImTheme.keyBg
    private val colorKeyPressed get() = ImTheme.keyPressed
    private val colorFnBg get() = ImTheme.keyFnBg
    private val colorAccent get() = ImTheme.accent
    private val colorAccentPressed get() = ImTheme.accentPressed
    private val colorKeyText get() = ImTheme.keyText
    private val colorFnText get() = ImTheme.keyFnText
    private val colorHintText get() = ImTheme.keyHintText

    // —————— 布局缓存 ——————

    private class PlacedKey(val def: KeyDef, val rect: RectF)

    private var placedRows: List<List<PlacedKey>> = emptyList()

    // —————— 触控状态 ——————

    private class PointerState(
        val down: PlacedKey,
        var current: PlacedKey?,
        val downY: Float,
        var consumed: Boolean = false,
    )

    private val pointers = HashMap<Int, PointerState>()
    private val pressedKeys = HashSet<PlacedKey>()

    private val handler = Handler(Looper.getMainLooper())

    private var repeatPointerId = -1
    private val repeatStart = Runnable {
        val st = pointers[repeatPointerId] ?: return@Runnable
        val cur = st.current ?: return@Runnable
        if (cur.def.action != KeyAction.BACKSPACE) return@Runnable
        onKey?.invoke(cur.def)
        handler.postDelayed(repeatRunnable, 45L)
    }
    private val repeatRunnable = object : Runnable {
        override fun run() {
            val st = pointers[repeatPointerId] ?: return
            val cur = st.current
            if (cur?.def?.action != KeyAction.BACKSPACE) return
            onKey?.invoke(cur.def)
            handler.postDelayed(this, 45L)
        }
    }

    private var optionsPointerId = -1
    private var optionsActive = false
    private val optionsRunnable = Runnable { showOptions() }

    // —————— 测量与布局 ——————

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val rows = layout.rows.size
        val kh = dp(layout.keyHeight)
        var h = padV * 2 + kh * rows + keyGap * (rows - 1)
        if (layout.tabs.isNotEmpty()) h += dp(layout.tabBarHeight)
        setMeasuredDimension(w, h.toInt())
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        rebuild()
    }

    /** 依据当前宽度重算所有键的矩形与标签栏位置 */
    private fun rebuild() {
        val w = width.toFloat()
        if (w <= 0f) return
        val avail = w - padH * 2
        val kh = dp(layout.keyHeight)
        val rows = ArrayList<List<PlacedKey>>(layout.rows.size)
        var y = padV
        layout.rows.forEach { row ->
            val n = row.keys.size
            val gaps = keyGap * (n - 1)
            var sum = row.pad * 2f
            row.keys.forEach { sum += it.weight }
            val unit = (avail - gaps) / sum
            var x = padH + row.pad * unit
            val placed = ArrayList<PlacedKey>(n)
            row.keys.forEach { key ->
                val kw = unit * key.weight
                placed.add(PlacedKey(key, RectF(x, y, x + kw, y + kh)))
                x += kw + keyGap
            }
            rows.add(placed)
            y += kh + keyGap
        }
        placedRows = rows
        tabTop = if (layout.tabs.isNotEmpty()) {
            height.toFloat() - dp(layout.tabBarHeight)
        } else {
            -1f
        }
    }

    /** 命中判定：最近键，距离不超过容差（键缝也能按到） */
    private fun keyAt(x: Float, y: Float): PlacedKey? {
        var best: PlacedKey? = null
        var bestD2 = Float.MAX_VALUE
        placedRows.forEach { row ->
            row.forEach { pk ->
                val dx = when {
                    x < pk.rect.left -> pk.rect.left - x
                    x > pk.rect.right -> x - pk.rect.right
                    else -> 0f
                }
                val dy = when {
                    y < pk.rect.top -> pk.rect.top - y
                    y > pk.rect.bottom -> y - pk.rect.bottom
                    else -> 0f
                }
                val d2 = dx * dx + dy * dy
                if (d2 < bestD2) {
                    bestD2 = d2
                    best = pk
                }
            }
        }
        return if (bestD2 <= hitSlop * hitSlop) best else null
    }

    private fun tabIndexAt(x: Float, y: Float): Int {
        if (layout.tabs.isEmpty() || tabTop < 0f || y < tabTop) return -1
        val n = layout.tabs.size
        val cellW = width.toFloat() / n
        return (x / cellW).toInt().coerceIn(0, n - 1)
    }

    // —————— 绘制 ——————

    override fun onDraw(canvas: Canvas) {
        placedRows.forEach { row ->
            row.forEach { pk ->
                val def = pk.def
                val isPressed = pk in pressedKeys
                val isShiftActive = def.action == KeyAction.SHIFT && shiftState != ShiftState.OFF

                fillPaint.color = when {
                    def.style == KeyStyle.ACCENT ->
                        if (isPressed) colorAccentPressed else colorAccent

                    isPressed -> colorKeyPressed
                    def.action == KeyAction.SHIFT && shiftState == ShiftState.LOCK -> colorKeyPressed
                    def.style == KeyStyle.FN -> colorFnBg
                    else -> colorKeyBg
                }
                canvas.drawRoundRect(pk.rect, keyRadius, keyRadius, fillPaint)

                if (def.style != KeyStyle.SPACE) {
                    drawKeyContent(canvas, pk, isPressed, isShiftActive)
                }
            }
        }
        drawSymbolTabs(canvas)
    }

    /** 键面内容（字母/功能字/图标/中英/⇧）：在键帽已绘制之后调用 */
    private fun drawKeyContent(canvas: Canvas, pk: PlacedKey, isPressed: Boolean, isShiftActive: Boolean) {        val def = pk.def
        val sizeLetter = dp(18f)
        val sizeGlyph = dp(17f)
        val sizeFn = dp(14f)

        val labelColor = when {
            def.style == KeyStyle.ACCENT -> 0xFFFFFFFF.toInt()
            isShiftActive -> colorAccent
            def.style == KeyStyle.FN -> colorFnText
            else -> colorKeyText
        }

        if (def.icon != KeyIcon.NONE) {
            drawIcon(canvas, def.icon, pk.rect, labelColor, isPressed)
            return
        }

        if (def.action == KeyAction.LANG_TOGGLE) {
            drawLangLabel(canvas, pk.rect)
            return
        }

        if (def.action == KeyAction.SHIFT) {
            drawShiftIcon(canvas, pk.rect)
            return
        }

        val hint = if (def.action == KeyAction.LETTER) {
            KeyboardLayouts.alternatesFor(def).firstOrNull()
        } else {
            null
        }

        textPaint.color = labelColor
        textPaint.textSize = when {
            def.action == KeyAction.SHIFT || def.action == KeyAction.BACKSPACE -> sizeGlyph
            def.style == KeyStyle.LETTER -> sizeLetter
            else -> sizeFn
        }
        val label = if (def.action == KeyAction.LETTER && shiftState != ShiftState.OFF) {
            def.label.uppercase(Locale.ROOT)
        } else {
            def.label
        }
        val cx = pk.rect.centerX()
        if (hint != null) {
            textPaint.color = colorHintText
            textPaint.textSize = dp(10f)
            canvas.drawText(hint, cx, pk.rect.top + dp(14.5f), textPaint)
            textPaint.textSize = sizeLetter
            textPaint.color = labelColor
        }
        val cy = pk.rect.centerY() + (if (hint != null) dp(3f) else 0f) -
            (textPaint.descent() + textPaint.ascent()) / 2f
        canvas.drawText(label, cx, cy, textPaint)
    }

    private fun drawSymbolTabs(canvas: Canvas) {
        val names = layout.tabs
        if (names.isEmpty() || tabTop < 0f) return
        textPaint.color = colorFnText
        textPaint.textSize = dp(13f)
        val cellW = width.toFloat() / names.size
        val cy = tabTop + (height - tabTop) / 2f -
            (textPaint.descent() + textPaint.ascent()) / 2f
        names.forEachIndexed { i, name ->
            val selected = i == symbolTabIndex
            if (selected) {
                textPaint.color = colorAccent
            } else {
                textPaint.color = colorFnText
            }
            canvas.drawText(name, cellW * i + cellW / 2f, cy, textPaint)
            if (selected) {
                fillPaint.color = colorAccent
                val bw = dp(18f)
                val bx = cellW * i + cellW / 2f
                canvas.drawRoundRect(
                    RectF(bx - bw / 2f, height - dp(4f), bx + bw / 2f, height - dp(2f)),
                    1f, 1f, fillPaint,
                )
            }
        }
    }

    /** 中/英键：当前语言的字符放大高亮，另一个缩小压暗 */
    private fun drawLangLabel(canvas: Canvas, rect: RectF) {
        val bigSize = dp(16.5f)
        val smallSize = dp(10.5f)
        val first = if (!langEnglish) "中" else "英"
        val second = if (!langEnglish) "英" else "中"

        textPaint.textSize = bigSize
        val w1 = textPaint.measureText(first)
        textPaint.textSize = smallSize
        val wSlash = textPaint.measureText("/")
        val w2 = textPaint.measureText(second)

        val total = w1 + wSlash + w2
        var x = rect.centerX() - total / 2f
        textPaint.textSize = bigSize
        val fm = textPaint.fontMetrics
        val baseline = rect.centerY() - (fm.ascent + fm.descent) / 2f

        textPaint.textAlign = Paint.Align.LEFT
        textPaint.color = colorAccent
        canvas.drawText(first, x, baseline, textPaint)
        x += w1
        textPaint.textSize = smallSize
        textPaint.color = colorHintText
        canvas.drawText("/", x, baseline, textPaint)
        x += wSlash
        canvas.drawText(second, x, baseline, textPaint)
        textPaint.textAlign = Paint.Align.CENTER
    }

    /** 自绘矢量图标（比字体符号更大更统一） */
    private fun drawIcon(canvas: Canvas, icon: KeyIcon, rect: RectF, color: Int, pressed: Boolean) {
        iconStroke.color = color
        val cx = rect.centerX()
        val cy = rect.centerY()

        when (icon) {
            KeyIcon.ENTER -> {
                // 白色小鱼（回车，鱼头朝左）：整体涂白，眼睛挖空成键底色
                val s = dp(1f)
                iconFill.style = Paint.Style.FILL_AND_STROKE
                iconFill.strokeWidth = dp(1.2f)
                iconFill.strokeJoin = Paint.Join.ROUND
                iconFill.color = color
                val fish = Path().apply {
                    moveTo(cx - 7.7f * s, cy) // 鱼头（左）
                    cubicTo(
                        cx - 6.8f * s, cy - 4.4f * s,
                        cx - 3.0f * s, cy - 5.6f * s,
                        cx + 1.4f * s, cy - 5.0f * s,
                    ) // 上背
                    quadTo(cx + 2.9f * s, cy - 4.7f * s, cx + 3.7f * s, cy - 4.4f * s) // 背部尾根
                    quadTo(cx + 6.5f * s, cy - 7.0f * s, cx + 7.9f * s, cy - 5.7f * s) // 上尾瓣（圆尖）
                    quadTo(cx + 6.7f * s, cy - 2.7f * s, cx + 5.5f * s, cy) // 上尾瓣内缘 → 宽浅凹口
                    quadTo(cx + 6.7f * s, cy + 2.7f * s, cx + 7.9f * s, cy + 5.7f * s) // 下尾瓣内缘 ← 凹口
                    quadTo(cx + 6.5f * s, cy + 7.0f * s, cx + 3.7f * s, cy + 4.4f * s) // 下尾瓣（圆尖）
                    quadTo(cx + 2.9f * s, cy + 4.7f * s, cx + 1.4f * s, cy + 5.0f * s)
                    cubicTo(
                        cx - 3.0f * s, cy + 5.6f * s,
                        cx - 6.8f * s, cy + 4.4f * s,
                        cx - 7.7f * s, cy,
                    ) // 下腹
                    close()
                }
                canvas.drawPath(fish, iconFill)
                iconFill.style = Paint.Style.FILL
                iconFill.color = if (pressed) colorAccentPressed else colorAccent
                canvas.drawCircle(cx - 4.4f * s, cy - 1.5f * s, dp(1.2f), iconFill)
                iconFill.color = color
            }

            KeyIcon.PREV -> {
                val s = dp(6.5f)
                val path = Path().apply {
                    moveTo(cx - s, cy + s * 0.5f)
                    lineTo(cx, cy - s * 0.5f)
                    lineTo(cx + s, cy + s * 0.5f)
                }
                canvas.drawPath(path, iconStroke)
            }

            KeyIcon.NEXT -> {
                val s = dp(6.5f)
                val path = Path().apply {
                    moveTo(cx - s, cy - s * 0.5f)
                    lineTo(cx, cy + s * 0.5f)
                    lineTo(cx + s, cy - s * 0.5f)
                }
                canvas.drawPath(path, iconStroke)
            }

            KeyIcon.NONE -> Unit
        }
    }

    /** ⇧：未激活 = 灰描边；单击大写待用 = 蓝色描边；锁定 = 整块涂蓝 */
    private fun drawShiftIcon(canvas: Canvas, rect: RectF) {
        val s = dp(1f)
        val cx = rect.centerX()
        val cy = rect.centerY()
        val path = Path().apply {
            moveTo(cx, cy - 7.3f * s)
            lineTo(cx + 7.1f * s, cy - 0.5f * s)
            lineTo(cx + 3.2f * s, cy - 0.5f * s)
            lineTo(cx + 3.2f * s, cy + 7.0f * s)
            lineTo(cx - 3.2f * s, cy + 7.0f * s)
            lineTo(cx - 3.2f * s, cy - 0.5f * s)
            lineTo(cx - 7.1f * s, cy - 0.5f * s)
            close()
        }
        when (shiftState) {
            ShiftState.LOCK -> {
                iconFill.style = Paint.Style.FILL_AND_STROKE
                iconFill.strokeWidth = dp(1.6f)
                iconFill.strokeJoin = Paint.Join.ROUND
                iconFill.color = colorAccent
                canvas.drawPath(path, iconFill)
                iconFill.style = Paint.Style.FILL
            }

            ShiftState.SINGLE -> {
                iconFill.style = Paint.Style.STROKE
                iconFill.strokeWidth = dp(2.1f)
                iconFill.strokeJoin = Paint.Join.ROUND
                iconFill.color = colorAccent
                canvas.drawPath(path, iconFill)
                iconFill.style = Paint.Style.FILL
            }

            ShiftState.OFF -> {
                iconFill.style = Paint.Style.STROKE
                iconFill.strokeWidth = dp(1.9f)
                iconFill.strokeJoin = Paint.Join.ROUND
                iconFill.color = colorFnText
                canvas.drawPath(path, iconFill)
                iconFill.style = Paint.Style.FILL
            }
        }
    }

    // —————— 触控 ——————

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val idx = event.actionIndex
                val id = event.getPointerId(idx)
                val x = event.getX(idx)
                val y = event.getY(idx)
                if (optionsActive) dismissOptions()

                // 符号页标签栏
                val tab = tabIndexAt(x, y)
                if (tab >= 0) {
                    switchSymbolTab(tab)
                    onKeyFeedback?.invoke()
                    return true
                }

                val pk = keyAt(x, y)
                if (pk != null) {
                    pointers[id] = PointerState(pk, pk, y)
                    onKeyFeedback?.invoke()
                    when {
                        pk.def.action == KeyAction.BACKSPACE -> {
                            repeatPointerId = id
                            handler.removeCallbacks(repeatStart)
                            handler.removeCallbacks(repeatRunnable)
                            handler.postDelayed(
                                repeatStart,
                                ViewConfiguration.getLongPressTimeout().toLong(),
                            )
                        }

                        KeyboardLayouts.alternatesFor(pk.def).isNotEmpty() -> {
                            optionsPointerId = id
                            handler.removeCallbacks(optionsRunnable)
                            handler.postDelayed(
                                optionsRunnable,
                                ViewConfiguration.getLongPressTimeout().toLong(),
                            )
                        }
                    }
                    refreshPressed()
                }
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until event.pointerCount) {
                    val id = event.getPointerId(i)
                    val st = pointers[id] ?: continue
                    val x = event.getX(i)
                    val y = event.getY(i)

                    // 备选条激活：只更新高亮（手指往下拉可取消）
                    if (optionsActive && id == optionsPointerId) {
                        if (y > st.down.rect.bottom + optionsCancelBelow) {
                            dismissOptions()
                        } else if (optionsHost?.moveHighlightToScreenX(event.getRawX(i)) == true) {
                            onKeyFeedback?.invoke()
                        }
                        continue
                    }

                    // ⌫ 上划 → 清空输入框
                    if (!st.consumed && st.down.def.action == KeyAction.BACKSPACE &&
                        st.downY - y > clearSwipeUp
                    ) {
                        st.consumed = true
                        cancelRepeat(id)
                        onClearInput?.invoke()
                        refreshPressed()
                        continue
                    }
                    if (st.consumed) continue

                    val pk = keyAt(x, y)
                    if (pk !== st.current) {
                        st.current = pk
                        if (pk != null) onKeyFeedback?.invoke()
                        if (pk !== st.down && !optionsActive && id == optionsPointerId) {
                            handler.removeCallbacks(optionsRunnable)
                        }
                        refreshPressed()
                    }
                }
            }

            MotionEvent.ACTION_POINTER_UP -> {
                val idx = event.actionIndex
                val id = event.getPointerId(idx)
                val st = pointers.remove(id) ?: return true
                handlePointerUp(id, st)
                refreshPressed()
            }

            MotionEvent.ACTION_UP -> {
                val idx = event.actionIndex
                val id = event.getPointerId(idx)
                val st = pointers.remove(id)
                if (st != null) handlePointerUp(id, st)
                refreshPressed()
            }

            MotionEvent.ACTION_CANCEL -> {
                pointers.clear()
                pressedKeys.clear()
                dismissOptions()
                cancelRepeat(-1)
                handler.removeCallbacksAndMessages(null)
                invalidate()
            }
        }
        return true
    }

    private fun handlePointerUp(id: Int, st: PointerState) {
        if (id == optionsPointerId && !optionsActive) handler.removeCallbacks(optionsRunnable)

        // 备选条激活中松手 → 上屏选中的备选
        if (optionsActive && id == optionsPointerId) {
            val text = optionsHost?.currentItem
            dismissOptions()
            if (text != null) onAlternate?.invoke(text)
            return
        }

        cancelRepeat(id)
        if (st.consumed) return

        val cur = st.current ?: return
        when (cur.def.action) {
            KeyAction.SYMBOL_PREV -> moveSymbolPage(-1)
            KeyAction.SYMBOL_NEXT -> moveSymbolPage(1)
            else -> onKey?.invoke(cur.def)
        }
    }

    private fun refreshPressed() {
        val next = HashSet<PlacedKey>()
        pointers.values.forEach { st ->
            if (!st.consumed) st.current?.let { next.add(it) }
        }
        if (next != pressedKeys) {
            pressedKeys.clear()
            pressedKeys.addAll(next)
            invalidate()
        }
    }

    private fun cancelRepeat(id: Int) {
        if (id == -1 || repeatPointerId == id) {
            repeatPointerId = -1
            handler.removeCallbacks(repeatStart)
            handler.removeCallbacks(repeatRunnable)
        }
    }

    // —————— 长按备选条 ——————

    private fun showOptions() {
        val st = pointers[optionsPointerId] ?: return
        val pk = st.current ?: return
        if (pk !== st.down) return
        val alts = KeyboardLayouts.alternatesFor(pk.def)
        if (alts.isEmpty()) return
        val host = optionsHost ?: return
        val items = if (pk.def.action == KeyAction.LETTER) {
            listOf(pk.def.label.uppercase(Locale.ROOT)) + alts + pk.def.label
        } else {
            listOf(pk.def.label) + alts
        }
        host.show(items, 1, RectF(pk.rect))
        optionsActive = true
    }

    /** 关闭备选条；若松手前被取消，则该指不再触发普通按键 */
    private fun dismissOptions() {
        handler.removeCallbacks(optionsRunnable)
        if (optionsActive) {
            pointers[optionsPointerId]?.consumed = true
            optionsHost?.hide()
        }
        optionsActive = false
        optionsPointerId = -1
    }

    override fun onDetachedFromWindow() {
        handler.removeCallbacksAndMessages(null)
        dismissOptions()
        super.onDetachedFromWindow()
    }
}

/** 长按备选字符条：一行圆角格，左右滑动切换高亮 */
class KeyOptionsView(context: Context) : View(context) {

    private val density = resources.displayMetrics.density
    private fun dp(v: Float) = v * density

    private var items: List<String> = emptyList()
    var highlight = 0
        private set
    private var cellWidths: List<Float> = emptyList()

    private val padH = dp(4f)
    private val cellH = dp(44f)
    private val cellTextPad = dp(11f)
    private val minCellW = dp(42f)
    private val cornerR = dp(11f)

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val selPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1f)
    }
    private val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1.5f)
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = dp(18f)
        typeface = Typeface.DEFAULT
    }

    fun show(list: List<String>, hi: Int) {
        items = list
        highlight = hi
        cellWidths = list.map { s ->
            val tw = textPaint.measureText(s) + cellTextPad * 2
            if (tw > minCellW) tw else minCellW
        }
        requestLayout()
        invalidate()
    }

    fun itemAt(i: Int): String? = items.getOrNull(i)

    /** x 相对本视图；超出范围时贴边（夹取到首/末格） */
    fun indexForX(x: Float): Int {
        if (items.isEmpty()) return 0
        var acc = padH
        for (i in items.indices) {
            acc += cellWidths[i]
            if (x <= acc) return i
        }
        return items.lastIndex
    }

    fun moveHighlight(i: Int) {
        if (i == highlight) return
        highlight = i
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = (padH * 2 + cellWidths.sum() + dp(3f) * items.size).toInt()
        val h = (cellH + dp(8f)).toInt()
        setMeasuredDimension(w, h)
    }

    override fun onDraw(canvas: Canvas) {
        bgPaint.color = ImTheme.popupBg
        selPaint.color = ImTheme.popupSel
        borderPaint.color = ImTheme.popupBorder
        accentPaint.color = ImTheme.accent
        textPaint.color = ImTheme.popupText
        val bounds = RectF(0f, 0f, width.toFloat(), height.toFloat())
        canvas.drawRoundRect(bounds, cornerR, cornerR, bgPaint)
        canvas.drawRoundRect(bounds, cornerR, cornerR, borderPaint)
        var x = padH
        items.forEachIndexed { i, s ->
            val w = cellWidths[i]
            val cell = RectF(x + dp(2f), dp(4f), x + w + dp(2f), dp(4f) + cellH)
            if (i == highlight) {
                canvas.drawRoundRect(cell, cornerR, cornerR, selPaint)
                canvas.drawRoundRect(cell, cornerR, cornerR, accentPaint)
            }
            val cy = cell.centerY() - (textPaint.descent() + textPaint.ascent()) / 2f
            canvas.drawText(s, cell.centerX(), cy, textPaint)
            x += w + dp(3f)
        }
    }
}
