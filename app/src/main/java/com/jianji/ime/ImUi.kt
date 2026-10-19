package com.jianji.ime

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/**
 * 设置系界面（设置页 / 许可页）的共用样式（M5.1 卡片风格）：
 * 顶部「← 标题」栏、分区小标题、每行一张圆角卡片、左侧圆形图标泡、右侧值/危险文本与箭头。
 * 配色沿用「墨夜」；风格参考常见的现代安卓设置页（不照抄具体配色与图标）。
 */
object ImUi {

    val BG = 0xFF17191E.toInt()
    val CARD = 0xFF1E232C.toInt()
    val TEXT = 0xFFE8EBF1.toInt()
    val SUB = 0xFF8B93A3.toInt()
    val ACCENT = 0xFF5B8DEF.toInt()
    val DANGER = 0xFFE08A8A.toInt()
    private val CARD_TEXT2 = 0xFFC6CDD9.toInt()

    fun dp(a: Context, v: Float): Int = (v * a.resources.displayMetrics.density).toInt()

    fun rounded(a: Context, color: Int, radiusDp: Float): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(a, radiusDp).toFloat()
        }

    /** 「实现 MATCH_PARENT + 可选上/左/右间距」的布局参数 */
    fun lp(a: Context, topDp: Float, leftDp: Float = 0f, rightDp: Float = 0f): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply {
            topMargin = dp(a, topDp)
            leftMargin = dp(a, leftDp)
            rightMargin = dp(a, rightDp)
        }

    /** 顶部标题栏：←（返回）+ 标题 */
    fun header(a: Context, title: String, onBack: () -> Unit): LinearLayout {
        val row = LinearLayout(a).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        row.addView(
            ImageView(a).apply {
                setImageResource(R.drawable.ic_nav_back)
                val pad = dp(a, 11f)
                setPadding(pad, pad, pad, pad)
                isClickable = true
                setOnClickListener { onBack() }
            },
            LinearLayout.LayoutParams(dp(a, 46f), dp(a, 46f)),
        )
        row.addView(
            TextView(a).apply {
                text = title
                setTextColor(TEXT)
                textSize = 21f
            },
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { leftMargin = dp(a, 6f) },
        )
        return row
    }

    /** 分区小标题（accent 小字） */
    fun sectionLabel(a: Context, text: String): TextView = TextView(a).apply {
        this.text = text
        setTextColor(ACCENT)
        textSize = 13f
    }

    /** 组后的说明小字 */
    fun note(a: Context, text: String): TextView = TextView(a).apply {
        this.text = text
        setTextColor(SUB)
        textSize = 12f
        setLineSpacing(dp(a, 3f).toFloat(), 1f)
    }

    /** 左侧圆形图标泡（accent ~15% 底 + 浅蓝图标） */
    fun iconBubble(a: Context, iconRes: Int): View {
        val size = dp(a, 40f)
        val box = LinearLayout(a).apply {
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0x265B8DEF)
            }
            layoutParams = LinearLayout.LayoutParams(size, size)
        }
        val icon = dp(a, 21f)
        box.addView(
            ImageView(a).apply { setImageResource(iconRes) },
            LinearLayout.LayoutParams(icon, icon),
        )
        return box
    }

    /**
     * 圆角卡片行：左图标泡（可空）+ 标题/副标题（或自定义内容） + 右侧控件（值文本、开关、
     * 危险文本等）与可选箭头。onClick 非空时整行可点。
     */
    fun cardRow(
        a: Context,
        iconRes: Int?,
        title: String? = null,
        subtitle: String? = null,
        subtitleView: TextView? = null,
        content: View? = null,
        right: View? = null,
        chevron: Boolean = false,
        onClick: (() -> Unit)? = null,
    ): LinearLayout {
        val row = LinearLayout(a).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(a, 14f), dp(a, 13f), dp(a, 14f), dp(a, 13f))
            background = rounded(a, CARD, 22f)
            if (onClick != null) {
                isClickable = true
                setOnClickListener { onClick() }
            }
        }
        if (iconRes != null) row.addView(iconBubble(a, iconRes))
        val midLp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            .apply { if (iconRes != null) leftMargin = dp(a, 13f) }
        if (content != null) {
            row.addView(content, midLp)
        } else {
            val col = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL }
            col.addView(
                TextView(a).apply {
                    text = title.orEmpty()
                    setTextColor(TEXT)
                    textSize = 16f
                    maxLines = 2
                    ellipsize = android.text.TextUtils.TruncateAt.END
                },
            )
            val sub = subtitleView ?: subtitle?.let {
                TextView(a).apply {
                    text = it
                    setTextColor(SUB)
                    textSize = 12.5f
                }
            }
            if (sub != null) {
                col.addView(
                    sub,
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    ).apply { topMargin = dp(a, 2f) },
                )
            }
            row.addView(col, midLp)
        }
        if (right != null) row.addView(right)
        if (chevron) {
            row.addView(
                ImageView(a).apply { setImageResource(R.drawable.ic_chevron) },
                LinearLayout.LayoutParams(dp(a, 20f), dp(a, 20f)).apply { leftMargin = dp(a, 5f) },
            )
        }
        return row
    }

    /** accent 底白字小药丸按钮 */
    fun pillButton(a: Context, text: String, color: Int = ACCENT, onClick: () -> Unit): TextView =
        TextView(a).apply {
            this.text = text
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 14f
            gravity = Gravity.CENTER
            background = rounded(a, color, 16f)
            setPadding(dp(a, 16f), dp(a, 8f), dp(a, 16f), dp(a, 8f))
            isClickable = true
            setOnClickListener { onClick() }
        }

    /** 右侧灰色值文本（如「英语」） */
    fun valueText(a: Context): TextView = TextView(a).apply {
        setTextColor(SUB)
        textSize = 14f
    }

    /** 右侧副标题级文本（与 cardRow 的 subtitle 同字号，用于动态刷新） */
    fun subTextView(a: Context): TextView = TextView(a).apply {
        setTextColor(SUB)
        textSize = 12.5f
    }

    /** 右侧危险操作文本（如「清空」「删除」） */
    fun dangerText(a: Context, text: String, onClick: () -> Unit): TextView = TextView(a).apply {
        this.text = text
        setTextColor(DANGER)
        textSize = 14f
        val p = dp(a, 6f)
        setPadding(p, p, 0, p)
        isClickable = true
        setOnClickListener { onClick() }
    }

    /** 许可页卡片内正文（浅灰、可选中） */
    fun licenseBody(a: Context, text: String): TextView = TextView(a).apply {
        this.text = text
        setTextColor(CARD_TEXT2)
        textSize = 11f
        setLineSpacing(dp(a, 3f).toFloat(), 1f)
        setTextIsSelectable(true)
    }

    // —————— 二级选择面板（单选 / 多选） ——————

    /**
     * 与设置页同一套卡片风格的二级选择面板：圆角深色面板、圆角选项行、
     * 右侧圆形（单选）/ 方形（多选）勾选标记、底部「取消 / 确定」。
     *
     * @param checked 初始选中状态（会被就地修改）
     * @param multi true = 多选（点行切换，底部「确定」生效）；false = 单选（点行立即回调并关闭）
     */
    fun choiceDialog(
        a: Context,
        title: String,
        options: List<String>,
        checked: BooleanArray,
        multi: Boolean,
        note: String? = null,
        onPick: ((Int) -> Unit)? = null,
        onConfirm: ((BooleanArray) -> Unit)? = null,
    ): android.app.Dialog {
        val dialog = android.app.Dialog(a)
        val root = LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(a, 20f), dp(a, 20f), dp(a, 20f), dp(a, 16f))
            background = rounded(a, 0xFF232833.toInt(), 24f)
        }
        root.addView(
            TextView(a).apply {
                text = title
                setTextColor(TEXT)
                textSize = 17.5f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            },
        )
        if (!note.isNullOrEmpty()) {
            root.addView(
                TextView(a).apply {
                    text = note
                    setTextColor(SUB)
                    textSize = 12.5f
                    setLineSpacing(dp(a, 2f).toFloat(), 1f)
                },
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = dp(a, 6f) },
            )
        }

        val renders = ArrayList<() -> Unit>(options.size)
        for (idx in options.indices) {
            val row = LinearLayout(a).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(a, 16f), dp(a, 13f), dp(a, 14f), dp(a, 13f))
                isClickable = true
            }
            row.addView(
                TextView(a).apply {
                    text = options[idx]
                    textSize = 14.5f
                    maxLines = 3
                },
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
            )
            val badge = LinearLayout(a).apply { gravity = Gravity.CENTER }
            row.addView(
                badge,
                LinearLayout.LayoutParams(dp(a, 20f), dp(a, 20f)).apply { leftMargin = dp(a, 12f) },
            )
            root.addView(
                row,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = dp(a, 8f) },
            )

            val render = {
                val on = checked[idx]
                row.background = optionBg(a, on)
                (row.getChildAt(0) as TextView).setTextColor(if (on) TEXT else CARD_TEXT2)
                badge.background = badgeBg(a, on, multi)
                badge.removeAllViews()
                if (on) {
                    if (multi) {
                        badge.addView(
                            TextView(a).apply {
                                text = "✓"
                                setTextColor(Color.WHITE)
                                textSize = 12f
                                gravity = Gravity.CENTER
                                includeFontPadding = false
                            },
                        )
                    } else {
                        badge.addView(
                            View(a).apply {
                                background = GradientDrawable().apply {
                                    shape = GradientDrawable.OVAL
                                    setColor(Color.WHITE)
                                }
                            },
                            LinearLayout.LayoutParams(dp(a, 8f), dp(a, 8f)),
                        )
                    }
                }
            }
            renders.add(render)
            render()
            row.setOnClickListener {
                if (multi) {
                    checked[idx] = !checked[idx]
                } else {
                    for (k in checked.indices) checked[k] = k == idx
                }
                renders.forEach { it.invoke() }
                if (!multi) {
                    onPick?.invoke(idx)
                    dialog.dismiss()
                }
            }
        }

        val btnRow = LinearLayout(a).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
        }
        btnRow.addView(
            TextView(a).apply {
                text = "取消"
                setTextColor(SUB)
                textSize = 14f
                gravity = Gravity.CENTER
                setPadding(dp(a, 18f), dp(a, 10f), dp(a, 18f), dp(a, 10f))
                isClickable = true
                setOnClickListener { dialog.dismiss() }
            },
        )
        if (multi) {
            btnRow.addView(
                pillButton(a, "确定") {
                    onConfirm?.invoke(checked)
                    dialog.dismiss()
                },
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply { leftMargin = dp(a, 6f) },
            )
        }
        root.addView(
            btnRow,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(a, 14f) },
        )

        dialog.setContentView(root)
        dialog.show()
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setDimAmount(0.58f)
            val maxW = dp(a, 340f)
            val screenW = a.resources.displayMetrics.widthPixels - dp(a, 48f)
            setLayout(minOf(maxW, screenW), ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        return dialog
    }

    /** 选项行背景：选中 = accent 淡底 + 描边；未选中 = 卡片色；按下再提亮 */
    private fun optionBg(a: Context, selected: Boolean): Drawable {
        fun shape(color: Int): GradientDrawable = GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(a, 15f).toFloat()
            if (selected) setStroke(dp(a, 1f), withAlpha(ACCENT, 0x66))
        }
        val sl = StateListDrawable()
        sl.addState(
            intArrayOf(android.R.attr.state_pressed),
            shape(if (selected) withAlpha(ACCENT, 0x3D) else 0xFF2E3542.toInt()),
        )
        sl.addState(
            intArrayOf(),
            shape(if (selected) withAlpha(ACCENT, 0x26) else 0xFF2A303B.toInt()),
        )
        return sl
    }

    /** 勾选标记：单选圆 / 多选圆角方；选中 accent 实底 */
    private fun badgeBg(a: Context, checked: Boolean, multi: Boolean): GradientDrawable =
        GradientDrawable().apply {
            if (multi) cornerRadius = dp(a, 6f).toFloat() else shape = GradientDrawable.OVAL
            if (checked) {
                setColor(ACCENT)
            } else {
                setColor(Color.TRANSPARENT)
                setStroke(dp(a, 1.5f), 0xFF5A6373.toInt())
            }
        }

    private fun withAlpha(color: Int, alpha: Int): Int =
        (color and 0x00FFFFFF) or (alpha shl 24)
}
