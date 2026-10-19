package com.jianji.ime

import android.content.ClipboardManager
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.RectF
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.jianji.ime.engine.Candidate
import com.jianji.ime.engine.EmojiDict
import com.jianji.ime.engine.EnglishDict
import com.jianji.ime.engine.FlatPinyinEngine
import com.jianji.ime.engine.FuzzyVariants
import com.jianji.ime.engine.GlossDict
import com.jianji.ime.engine.JianDict
import com.jianji.ime.engine.LevelDict
import com.jianji.ime.engine.PinyinEngine
import com.jianji.ime.keyboard.CandidateBar
import com.jianji.ime.keyboard.CandidateGridPanel
import com.jianji.ime.keyboard.ClipboardPanel
import com.jianji.ime.keyboard.EmojiPanel
import com.jianji.ime.keyboard.HideKeyboardButton
import com.jianji.ime.keyboard.KeyAction
import com.jianji.ime.keyboard.KeyDef
import com.jianji.ime.keyboard.KeyOptionsView
import com.jianji.ime.keyboard.KeyboardLayouts
import com.jianji.ime.keyboard.KeyboardView
import com.jianji.ime.keyboard.OptionsHost
import com.jianji.ime.keyboard.ShiftState
import com.jianji.ime.keyboard.ToolButton
import java.util.Locale

/**
 * 简记输入法主服务。
 *
 * M1（当前）：26 键键盘（搜狗习惯布局）+ 字母页/数字页/符号页 + 长按备选条 +
 * ⌫ 上划清空 + 大小写单击/双击锁定 + 声音振动开关。
 * M2 起：接入 PinyinEngine（拼音组词、候选、上屏）；M3 学习层双行候选。
 */
class JianjiImeService : InputMethodService() {

    private enum class Page { LETTERS, NUMBERS, SYMBOLS }

    /** 键盘区上方的覆盖面板（互斥）：更多候选 / 剪贴板 / 表情（M4） */
    private enum class Overlay { NONE, GRID, CLIP, EMOJI }

    private lateinit var keyboardView: KeyboardView

    private var shift = ShiftState.OFF
    private var page = Page.LETTERS

    // —————— M2：拼音组句与候选 ——————

    private lateinit var candidateBar: CandidateBar
    private lateinit var moreButton: HideKeyboardButton
    private lateinit var gridPanel: CandidateGridPanel
    private var overlay = Overlay.NONE
    private var candidateItems: List<CandidateBar.Item> = emptyList()

    /** 拼音引擎（词库异步载入，载好前只显示内联拼音） */
    @Volatile
    private var engine: PinyinEngine? = null

    /** 词库（自造短语的简拼切分用；与引擎一起异步载入） */
    @Volatile
    private var dict: JianDict? = null
    private val pinyin = StringBuilder()
    private var candidates: List<Candidate> = emptyList()
    private var englishMode = false

    // —————— v0.2：记忆（词频 + 新鲜度 + 搭配，见 Learning） ——————

    private val wordStats = HashMap<String, Learning.WordStat>()
    private val pairs = HashMap<String, Int>()

    /** 自造短语：整串重复输入学到的习惯（见 PhraseIndex / Learning） */
    private val phrases = HashMap<String, Learning.PhraseStat>()
    private val phraseIndex = PhraseIndex()

    /** 本次组句累计的「已上屏文本 / 已消费拼音」（学整串用：wutiaowu → 五 条 悟 → 五条悟） */
    private val sessionText = StringBuilder()
    private val sessionPinyin = StringBuilder()

    /** 刚上屏的词（搭配上下文；换输入框/会话时清空） */
    private var lastCommitted: String? = null

    /** 学习数据版本号：一有变化就失效加分缓存 */
    private var learnEpoch = 0
    private var boostCacheEpoch = -1
    private var boostCachePrev: String? = null
    private var boostDay = 0L
    private val boostCache = HashMap<String, Float>(256)

    // —————— M3：学习层（候选译文，一次一种语言） ——————

    private val mainHandler = Handler(Looper.getMainLooper())

    /** 释义表（按学习语言异步载入；载好前候选照常显示、不等待） */
    @Volatile
    private var gloss: GlossDict? = null
    private var glossLang: String? = null
    private var glossLoading: String? = null
    private var glossFailedLang: String? = null

    /** 生词等级表（v0.2）：英语 CEFR / 日语 JLPT，候选右上角标 B2 / N2 以上（「生词高亮」） */
    @Volatile
    private var levelsEn: LevelDict? = null

    @Volatile
    private var levelsJa: LevelDict? = null

    // —————— M3.5：英文模式（原样输入 + 联想词，词上补中文释义） ——————

    private val englishBuf = StringBuilder()
    private var englishSuggestions: List<String> = emptyList()

    /** 英文词表与英→中释义表（英文模式首次使用时异步载入） */
    @Volatile
    private var englishDict: EnglishDict? = null

    @Volatile
    private var zhGloss: GlossDict? = null
    private var englishLoading = false
    private var englishFailed = false

    // —————— M4：剪贴板 + 常用语 + 表情 ——————

    private lateinit var clipButton: ToolButton
    private lateinit var emojiButton: ToolButton
    private lateinit var gearButton: ToolButton
    private lateinit var clipPanel: ClipboardPanel
    private lateinit var emojiPanel: EmojiPanel

    /** 候选栏右缩尺寸：显示 / 隐藏（输入中）工具图标行两种 */
    private var barMarginTools = 0
    private var barMarginMode = 0

    /** 表情表（emoji.bin，22KB，启动即异步载好） */
    @Volatile
    private var emojiDict: EmojiDict? = null
    private var emojiLoading = false

    /** 系统剪贴板监听（只在系统允许时回调，抓到就进历史） */
    private var clipListenerAttached = false
    private val clipListener =
        ClipboardManager.OnPrimaryClipChangedListener { onPrimaryClipChanged() }

    /** 按键振动开关 = 用户偏好 && 系统触感设置（随偏好变化实时刷新） */
    private var vibrateOn = true

    /** 生词标记开关（设置页可关；关掉只影响角标，译文照常） */
    private var levelMarkOn = true

    /** 振动强度档位（弱 / 标准 / 强；默认弱，按时长分档，真机校准） */
    private var vibrateLevel = Prefs.VIBRATE_WEAK
    private var vibrator: Vibrator? = null
    private var vibeWeak: VibrationEffect? = null
    private var vibeStandard: VibrationEffect? = null
    private var vibeStrong: VibrationEffect? = null

    private val prefListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            refreshFeedbackFlags()
            when (key) {
                Prefs.KEY_LEARN_LANG -> onLearnLangChanged()
                Prefs.KEY_VIBRATE_LEVEL -> rebuildVibration()
                Prefs.KEY_USER_WORDS, Prefs.KEY_PAIRS, Prefs.KEY_PHRASES ->
                    if (!selfPrefWrite) reloadLearn()
            }
        }

    /** learn() 自己写盘时置位：避免监听器把自己的写入又读回来 */
    private var selfPrefWrite = false

    /** 键盘底板（根背景色；墨夜单主题） */
    private var baseLayout: LinearLayout? = null

    /** 候选区右侧图标行（剪贴板面板打开时整行隐藏，避免半透明面板透出图标残影） */
    private var rightRow: LinearLayout? = null

    /** 键盘上方预留区（56dp）：M1 承载长按备选条，M2 起承载候选栏 */
    private lateinit var optionsZone: FrameLayout
    private var rootView: FrameLayout? = null
    private var optionsView: KeyOptionsView? = null

    private val optionsHostAdapter = object : OptionsHost {

        override fun show(items: List<String>, highlight: Int, anchor: RectF) {
            val root = rootView ?: return
            if (!::optionsZone.isInitialized || root.width == 0) return
            val v = optionsView ?: KeyOptionsView(this@JianjiImeService).also { optionsView = it }
            v.show(items, highlight)
            v.measure(
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            )
            val d = resources.displayMetrics.density
            val w = v.measuredWidth
            val h = v.measuredHeight
            // 键盘在根布局中的顶部 = 预留区高度；anchor 是按键在 KeyboardView 内的矩形
            val keyboardTop = if (optionsZone.height > 0) optionsZone.height.toFloat() else 56f * d
            val margin = 8f * d
            var x = anchor.centerX() - w / 2f
            x = x.coerceIn(margin, (root.width - w - margin).coerceAtLeast(margin))
            var y = keyboardTop + anchor.top - h - 6f * d
            y = y.coerceAtLeast(margin / 2f)
            root.removeView(v)
            root.addView(
                v,
                FrameLayout.LayoutParams(w, h, Gravity.TOP or Gravity.LEFT).apply {
                    leftMargin = x.toInt()
                    topMargin = y.toInt()
                },
            )
        }

        override fun moveHighlightToScreenX(screenX: Float): Boolean {
            val v = optionsView ?: return false
            if (v.parent == null) return false
            val loc = IntArray(2)
            v.getLocationOnScreen(loc)
            val idx = v.indexForX(screenX - loc[0])
            if (idx == v.highlight) return false
            v.moveHighlight(idx)
            return true
        }

        override fun hide() {
            optionsView?.let { rootView?.removeView(it) }
        }

        override val currentItem: String?
            get() = optionsView?.let { it.itemAt(it.highlight) }
    }

    override fun onCreate() {
        super.onCreate()
        ImTheme.refresh(this)
        Prefs.get(this).registerOnSharedPreferenceChangeListener(prefListener)
        refreshFeedbackFlags()
        rebuildVibration()
        wordStats.putAll(Prefs.loadWordStats(this))
        pairs.putAll(Prefs.loadPairs(this))
        phrases.putAll(Prefs.loadPhrases(this))
        // 词库异步预载（~4.5MB），避免首次按键时卡顿
        Thread({
            engine = try {
                val d = assets.open("dict.bin").use { JianDict.load(it) }
                dict = d
                FlatPinyinEngine(
                    d,
                    userBoost = { w -> userBoost(w) },
                    fuzzyMask = { fuzzyMask() },
                    phraseHits = { q -> phraseIndex.hits(q, Learning.today()) },
                )
            } catch (t: Throwable) {
                null
            }
            // 短语简拼要用词库音节表切分，词库就绪后补建索引
            mainHandler.post { rebuildPhraseIndex() }
        }, "jianji-dict-load").start()
        // 释义表异步预载（~3.5–5MB，M3；语言变化时按需重载）
        ensureGloss()
        // 表情表异步预载（22KB）+ 系统剪贴板监听（M4）
        ensureEmoji()
        ensureClipListener()
    }

    override fun onDestroy() {
        Prefs.get(this).unregisterOnSharedPreferenceChangeListener(prefListener)
        super.onDestroy()
    }

    override fun onCreateInputView(): View {
        keyboardView = KeyboardView(this).apply {
            symbolTabs = KeyboardLayouts.symbolTabs
            onKeyFeedback = { feedback() }
            onKey = { handleKey(it) }
            onAlternate = { text -> onTextKey(text) }
            onClearInput = { clearInputField() }
            onSpaceMove = { step -> moveCursorBy(step) }
            onMoveFeedback = { feedback() }
            onSwitchIme = { showImePicker() }
            optionsHost = optionsHostAdapter
        }
        optionsZone = FrameLayout(this)
        rootView = FrameLayout(this)
        val d = resources.displayMetrics.density
        // 候选栏（右侧留给功能图标行：剪贴板 / 表情 / 设置 / 收起；输入中工具图标自动隐藏）
        candidateBar = CandidateBar(this).apply {
            onSelect = { onCandidateTap(it) }
            onSwipeUp = { onCandidateSwipeUp(it) }
        }
        val clipW = (40 * d).toInt()
        val emojiW = (38 * d).toInt()
        val gearW = (40 * d).toInt()
        val modeW = (44 * d).toInt()
        val gapW = (6 * d).toInt()
        barMarginTools = clipW + emojiW + gearW + modeW + gapW
        barMarginMode = modeW + gapW
        optionsZone.addView(
            candidateBar,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ).apply { rightMargin = barMarginTools },
        )
        // 候选区最右侧功能图标行（图标统一画在词线 0.70h）：
        // [剪贴板] [表情] [设置] [˅ 收起键盘 / ˅˅ 更多候选 / ˄˄ 收起面板]
        clipButton = ToolButton(this).apply {
            glyph = ToolButton.Glyph.CLIP
            setOnClickListener { toggleOverlay(Overlay.CLIP) }
        }
        emojiButton = ToolButton(this).apply {
            glyph = ToolButton.Glyph.EMOJI
            setOnClickListener { toggleOverlay(Overlay.EMOJI) }
        }
        gearButton = ToolButton(this).apply {
            glyph = ToolButton.Glyph.GEAR
            setOnClickListener { openSettings() }
        }
        moreButton = HideKeyboardButton(this).apply { setOnClickListener { onRightButtonTap() } }
        val rightBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(clipButton, LinearLayout.LayoutParams(clipW, ViewGroup.LayoutParams.MATCH_PARENT))
            addView(emojiButton, LinearLayout.LayoutParams(emojiW, ViewGroup.LayoutParams.MATCH_PARENT))
            addView(gearButton, LinearLayout.LayoutParams(gearW, ViewGroup.LayoutParams.MATCH_PARENT))
            addView(moreButton, LinearLayout.LayoutParams(modeW, ViewGroup.LayoutParams.MATCH_PARENT))
        }
        rightRow = rightBar
        optionsZone.addView(
            rightBar,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
                Gravity.END or Gravity.CENTER_VERTICAL,
            ).apply { rightMargin = (2 * d).toInt() },
        )
        val base = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(ImTheme.windowBg)
            addView(
                optionsZone,
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (56 * d).toInt()),
            )
            addView(
                keyboardView,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )
        }
        baseLayout = base
        rootView!!.addView(
            base,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )
        // 「更多候选」/「剪贴板」/「表情」面板：盖在键盘区上（高度跟键盘一致，不改输入法窗口高度）
        gridPanel = CandidateGridPanel(this).apply {
            onSelect = { onCandidateTap(it) }
            visibility = View.GONE
        }
        clipPanel = ClipboardPanel(this).apply {
            visibility = View.GONE
            onCommit = { text -> commitPanelText(text, close = true) }
            onSettings = { openSettings() }
            onClose = { setOverlay(Overlay.NONE) }
        }
        emojiPanel = EmojiPanel(this).apply {
            visibility = View.GONE
            onPick = { text -> commitPanelText(text, close = false) }
            onBack = { setOverlay(Overlay.NONE) }
            emojiDict?.let { setCategories(it.cats) }
        }
        for (panel in arrayOf<View>(gridPanel, clipPanel, emojiPanel)) {
            rootView!!.addView(
                panel,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    (226 * d).toInt(),
                    Gravity.BOTTOM,
                ),
            )
        }
        return rootView!!
    }

    /** 不用全屏抽取模式（横屏时也保持键盘内嵌） */
    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        // 键盘每次弹出都回到默认状态：字母页 + 无覆盖面板
        // （收起键盘再打开同一输入框时框架只回调 onStartInputView、不回调 onStartInput，
        // 不在这里归位的话会从数字/符号页或表情面板「续开」）
        resetInputState()
        refreshFeedbackFlags()
        // 键盘显示时顺手抓一下系统剪贴板（Android 10+ 只允许前台 IME 读）
        ClipboardStore.capture(this, force = false)
    }

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        resetInputState()
    }

    /** 新会话 / 键盘弹出统一归位：字母页、⇧ 关、清空组句与覆盖面板 */
    private fun resetInputState() {
        // 结束可能残留的组句态（内联拼音落为普通文字，避免旧输入框残留下划线）
        if (pinyin.isNotEmpty() || englishBuf.isNotEmpty()) {
            currentInputConnection?.finishComposingText()
        }
        shift = ShiftState.OFF
        page = Page.LETTERS
        englishMode = false // 每次唤起都回到中文输入（英文模式不跨会话残留）
        pinyin.setLength(0)
        englishBuf.setLength(0)
        candidates = emptyList()
        englishSuggestions = emptyList()
        lastCommitted = null
        if (::keyboardView.isInitialized) {
            keyboardView.shiftState = shift
            keyboardView.langEnglish = englishMode
            keyboardView.showLetterPage()
        }
        setOverlay(Overlay.NONE)
        clearComposing()
    }

    /** 输入焦点离开：结束组句态（内联拼音落为普通文字，避免旧输入框残留下划线） */
    override fun onFinishInput() {
        if (pinyin.isNotEmpty() || englishBuf.isNotEmpty()) {
            currentInputConnection?.finishComposingText()
        }
        setOverlay(Overlay.NONE)
        clearComposing()
        super.onFinishInput()
    }

    // —————— 按键处理 ——————

    private fun handleKey(key: KeyDef) {
        // 剪贴板 / 表情面板展开时，任何按键先收起面板回键盘
        if (overlay == Overlay.CLIP || overlay == Overlay.EMOJI) setOverlay(Overlay.NONE)
        when (key.action) {
            KeyAction.LETTER -> onLetter(key.label)
            KeyAction.TEXT -> onTextKey(key.text)
            KeyAction.SPACE -> onSpace()
            KeyAction.BACKSPACE -> onBackspace()
            KeyAction.ENTER -> onEnter()
            KeyAction.SHIFT -> onShiftTap()
            KeyAction.PAGE_NUMBERS -> {
                page = Page.NUMBERS
                keyboardView.showNumberPage()
            }

            KeyAction.PAGE_SYMBOLS -> {
                page = Page.SYMBOLS
                keyboardView.showSymbolPage()
            }

            KeyAction.PAGE_LETTERS -> {
                page = Page.LETTERS
                keyboardView.showLetterPage()
            }

            KeyAction.HIDE_KEYBOARD -> requestHideSelf(0)

            KeyAction.SYMBOL_PREV, KeyAction.SYMBOL_NEXT -> Unit // KeyboardView 内部处理

            KeyAction.LANG_TOGGLE -> onLangToggle()
        }
    }

    // —————— M2：拼音组句 ——————

    /**
     * 字母键：中文模式走拼音组句（⇧ 待大写时直接上屏字母）；
     * 英文模式攒进组句区出候选（第一个 = 原样输入的字母，对齐搜狗）。
     */
    private fun onLetter(letter: String) {
        if (englishMode) {
            if (englishBuf.length >= 24) return
            englishBuf.append(if (shift != ShiftState.OFF) letter.uppercase(Locale.ROOT) else letter)
            if (shift == ShiftState.SINGLE) {
                shift = ShiftState.OFF
                keyboardView.shiftState = shift
            }
            updateComposing()
            return
        }
        if (shift != ShiftState.OFF) {
            if (pinyin.isNotEmpty()) flushComposing()
            commitAscii(letter)
            return
        }
        if (pinyin.length >= 24) return
        pinyin.append(letter)
        updateComposing()
    }

    /** 直接上屏一个 ASCII 字母（按 ⇧ 状态定大小写） */
    private fun commitAscii(ch: String) {
        val text = if (shift != ShiftState.OFF) ch.uppercase(Locale.ROOT) else ch
        currentInputConnection?.commitText(text, 1)
        if (shift == ShiftState.SINGLE) {
            shift = ShiftState.OFF
            keyboardView.shiftState = shift
        }
    }

    /** 固定文本（标点、数字、符号）：有组句先收尾（中文 = 首选 + 剩余字母；英文 = 原样字母），与搜狗一致 */
    private fun onTextKey(text: String) {
        flushAnyComposing()
        currentInputConnection?.commitText(text, 1)
    }

    private fun onSpace() {
        if (englishMode && englishBuf.isNotEmpty()) {
            // 英文模式：先上屏首选（= 原样输入的字母），空格照常交给应用
            commitEnglish(0)
            currentInputConnection?.commitText(" ", 1)
        } else if (pinyin.isNotEmpty()) {
            commitPending()
        } else {
            currentInputConnection?.commitText(" ", 1)
        }
    }

    private fun onEnter() {
        if (englishMode && englishBuf.isNotEmpty()) {
            commitEnglish(0) // 英文组句中的回车 = 原样上屏字母（与搜狗一致）
        } else if (pinyin.isNotEmpty()) {
            commitRaw() // 组句中的回车 = 原样上屏拼音字母（与搜狗一致）
        } else {
            handleEnter()
        }
    }

    private fun onBackspace() {
        if (englishMode && englishBuf.isNotEmpty()) {
            englishBuf.deleteAt(englishBuf.length - 1)
            updateComposing()
        } else if (pinyin.isNotEmpty()) {
            pinyin.deleteAt(pinyin.length - 1)
            updateComposing()
        } else {
            backspace()
        }
    }

    private fun onLangToggle() {
        flushAnyComposing()
        englishMode = !englishMode
        keyboardView.langEnglish = englishMode
        if (englishMode) ensureEnglish()
    }

    /** 点选候选（中文支持部分上屏；英文第 0 个 = 原样输入） */
    private fun onCandidateTap(index: Int) {
        if (englishMode && englishBuf.isNotEmpty()) {
            commitEnglish(index)
            return
        }
        candidates.getOrNull(index)?.let { commitCandidate(it) }
    }

    /**
     * 候选上划：把候选对应的译文直接上屏（只取第一条义项，去词性 / 去振假名注音）。
     * 中文 = 学习语言译文（你好 → hello、开发 → 開発する）；英文 = 中文释义。
     * 无译文的候选 / 长句候选不动作（与译文注记同一门槛）。
     */
    private fun onCandidateSwipeUp(index: Int) {
        val ic = currentInputConnection ?: return
        if (englishMode && englishBuf.isNotEmpty()) {
            val word = if (index == 0) {
                englishBuf.toString()
            } else {
                englishSuggestions.getOrNull(index - 1) ?: return
            }
            val t = zhGloss?.inputFormFor(word)
                ?: zhGloss?.inputFormFor(word.lowercase(Locale.ROOT))
                ?: return
            if (t.isEmpty()) return
            ic.commitText(t, 1)
            clearComposing()
            return
        }
        val c = candidates.getOrNull(index) ?: return
        if (c.text.length > 4) return
        val t = gloss?.inputFormFor(c.text) ?: return
        if (t.isEmpty()) return
        commitCandidateText(c, t)
    }

    /** 上屏首选词（没有候选则原样上屏拼音） */
    private fun commitPending() {
        val top = candidates.firstOrNull()
        if (top != null) commitCandidate(top) else commitRaw()
    }

    /**
     * 上屏候选：consumed 小于拼音长度时只消费那一段，剩余拼音留在组句里继续出候选
     * （nihaom 点「你好」→ 输入框里剩 m 继续选；对齐搜狗 / 青简 commit 的语义）。
     * 否则整段上屏，清空组句。
     */
    private fun commitCandidate(c: Candidate) = commitCandidateText(c, c.text)

    /** 上屏候选文本（部分上屏语义同上；上划译文走这里，学习仍记原词） */
    private fun commitCandidateText(c: Candidate, text: String) {
        val ic = currentInputConnection ?: return
        learn(c.text)
        val consumed = if (c.consumed in 1..pinyin.length) c.consumed else pinyin.length
        val chunk = pinyin.substring(0, consumed)
        if (consumed < pinyin.length) {
            val rest = pinyin.substring(consumed)
            ic.commitText(text, 1)
            pinyin.setLength(0)
            pinyin.append(rest)
            sessionAppend(chunk, text)
            updateComposing()
        } else {
            ic.commitText(text, 1)
            sessionAppend(chunk, text)
            finishPhraseSession()
            clearComposing()
        }
    }

    /** 组句收尾：先上屏首选（可能只吃一段），剩余字母原样上屏（输标点 / 切语言前用） */
    private fun flushComposing() {
        if (pinyin.isEmpty()) return
        commitPending()
        if (pinyin.isNotEmpty()) commitRaw()
    }

    /** 英文组句收尾：原样上屏输入的字母 */
    private fun flushEnglish() {
        if (englishBuf.isEmpty()) return
        currentInputConnection?.commitText(englishBuf.toString(), 1)
        clearComposing()
    }

    /** 按当前模式收尾组句（输标点 / 切语言前用） */
    private fun flushAnyComposing() {
        if (englishMode) flushEnglish() else flushComposing()
    }

    /** 上屏英文候选：0 = 原样输入的字母，其余 = 联想词（下标对应候选栏） */
    private fun commitEnglish(index: Int) {
        val text = if (index == 0) englishBuf.toString() else englishSuggestions.getOrNull(index - 1)
        if (text.isNullOrEmpty()) return
        currentInputConnection?.commitText(text, 1)
        clearComposing()
    }

    /**
     * 记入学习数据：次数 +1、刷新最近使用日，并记一条「前词 → 本词」搭配。
     * 只有真上屏的词会走到这里（点选 / 上划译文 / 空格首选）。
     */
    private fun learn(word: String) {
        val st = wordStats[word]
        wordStats[word] = Learning.WordStat((st?.count ?: 0) + 1, Learning.today())
        val prev = lastCommitted
        if (prev != null && prev != word) {
            val k = Learning.pairKey(prev, word)
            pairs[k] = ((pairs[k] ?: 0) + 1).coerceAtMost(999)
        }
        lastCommitted = word
        learnEpoch++
        Learning.pruneWords(wordStats)
        Learning.prunePairs(pairs)
        selfPrefWrite = true
        Prefs.saveWordStats(this, wordStats)
        Prefs.savePairs(this, pairs)
        selfPrefWrite = false
    }

    /** 设置页改了学习数据（删词 / 清空）：立刻同步到内存，不用等输入法重启 */
    private fun reloadLearn() {
        wordStats.clear()
        wordStats.putAll(Prefs.loadWordStats(this))
        pairs.clear()
        pairs.putAll(Prefs.loadPairs(this))
        phrases.clear()
        phrases.putAll(Prefs.loadPhrases(this))
        learnEpoch++
        rebuildPhraseIndex()
    }

    // —————— 自造短语（v0.2）：整串重复输入 → 全拼 / 简拼都出整串候选（见 Learning / PhraseIndex） ——————

    /** 组句会话累计：文本必须是纯汉字（上划译文 / 英文串打断本次累计） */
    private fun sessionAppend(chunk: String, text: String) {
        if (!Learning.cjkOnly(text)) {
            sessionText.setLength(0)
            sessionPinyin.setLength(0)
            return
        }
        sessionText.append(text)
        sessionPinyin.append(chunk)
    }

    /**
     * 一次组句整串上屏：2~4 字的串记一次「习惯」（串 → 整段拼音 + 次数）；
     * 次数达到 Learning.PHRASE_MIN_COUNT 后整串出现在候选里 —— 只打一次不会记住。
     * 次数只增不减、拼音只升不降（见 Learning.mergePhrase）：用简拼上屏不会把记忆弄丢。
     */
    private fun finishPhraseSession() {
        val text = sessionText.toString()
        val pinyin = sessionPinyin.toString()
        sessionText.setLength(0)
        sessionPinyin.setLength(0)
        if (text.length < 2 || text.length > Learning.MAX_PHRASE_CHARS) return
        if (pinyin.length < text.length) return
        phrases[text] = Learning.mergePhrase(phrases[text], pinyin, Learning.today())
        Learning.prunePhrases(phrases)
        selfPrefWrite = true
        Prefs.savePhrases(this, phrases)
        selfPrefWrite = false
        rebuildPhraseIndex()
    }

    /** 短语候选索引（门槛内的短语；词库就绪后补音节切分，切不开的短语只按整段匹配） */
    private fun rebuildPhraseIndex() {
        if (phrases.isEmpty()) {
            phraseIndex.rebuild(emptyList())
            return
        }
        val d = dict
        val list = ArrayList<PhraseIndex.Entry>(phrases.size)
        for ((text, st) in phrases) {
            list.add(
                PhraseIndex.Entry(text, st.pinyin, d?.syllablesOf(st.pinyin, text.length), st.count, st.day),
            )
        }
        phraseIndex.rebuild(list)
    }

    /**
     * 候选学习加分（次数 + 新鲜度 + 刚上屏前词的搭配）。
     * 按「学习版本 + 前词」缓存：一次组句里每候选只算一次，不重复拼 key。
     */
    private fun userBoost(w: String): Float {
        val cur = lastCommitted
        if (boostCacheEpoch != learnEpoch || boostCachePrev != cur) {
            boostCache.clear()
            boostCacheEpoch = learnEpoch
            boostCachePrev = cur
            boostDay = Learning.today()
        }
        boostCache[w]?.let { return it }
        var b = Learning.boost(wordStats[w], boostDay)
        if (cur != null) b += Learning.pairBoost(pairs[Learning.pairKey(cur, w)])
        boostCache[w] = b
        return b
    }

    /** 模糊音开关（设置页写 Prefs）→ 引擎掩码；读取即生效 */
    private fun fuzzyMask(): Int {
        val g = Prefs.fuzzyGroups(this)
        if (g.isEmpty()) return 0
        var m = 0
        for (t in g.split(',')) {
            m = when (t) {
                "zh" -> m or FuzzyVariants.GROUP_ZH
                "nasal" -> m or FuzzyVariants.GROUP_NASAL
                "nl" -> m or FuzzyVariants.GROUP_NL
                else -> m
            }
        }
        return m
    }

    /** 原样上屏拼音字母 */
    private fun commitRaw() {
        val text = pinyin.toString()
        currentInputConnection?.commitText(text, 1)
        clearComposing()
    }

    /** 刷新内联组句（输入框里）+ 候选栏：中文 = 拼音候选 + 学习语言译文；英文 = 原样输入 + 联想词（中文释义） */
    private fun updateComposing() {
        val ic = currentInputConnection ?: return
        val text = if (englishMode) englishBuf.toString() else pinyin.toString()
        if (text.isEmpty()) {
            // 注意：finishComposingText 只会把内联拼音「落下」成普通文字，
            // 要真正删掉必须先用 setComposingText("") 清空组句区
            ic.setComposingText("", 0)
            ic.finishComposingText()
            clearComposing()
            return
        }
        ic.setComposingText(text, 1)
        if (englishMode) {
            ensureEnglish()
            englishSuggestions = englishDict?.suggest(text, ENGLISH_SUGGEST_LIMIT)
                ?.filterNot { it.equals(text, true) } ?: emptyList()
            val zg = zhGloss
            val list = ArrayList<CandidateBar.Item>(englishSuggestions.size + 1)
            list.add(
                CandidateBar.Item(
                    text,
                    zg?.annotationFor(text) ?: zg?.annotationFor(text.lowercase(Locale.ROOT)),
                    englishMark(text),
                ),
            )
            for (w in englishSuggestions) list.add(CandidateBar.Item(w, zg?.annotationFor(w), englishMark(w)))
            candidates = emptyList()
            candidateItems = list
        } else {
            candidates = engine?.candidates(text, 100) ?: emptyList()
            ensureGloss()
            val g = gloss
            // 译文只注短候选（≤4 字，词条体量）：整句/长短语没有现成译词，不查也不占位（青简同款）
            candidateItems = candidates.map {
                val short = it.text.length <= 4
                val ann = if (short) g?.annotationFor(it.text) else null
                val mark = if (short) chineseMark(it.text, g) else null
                CandidateBar.Item(it.text, ann, mark)
            }
        }
        candidateBar.setItems(candidateItems)
        if (overlay == Overlay.GRID) gridPanel.setItems(candidateItems)
        updateRightButton()
    }

    /** 英文候选的生词角标（CEFR；等级表按小写入库）；设置里关掉就不查 */
    private fun englishMark(word: String): String? {
        if (!levelMarkOn) return null
        return levelsEn?.markFor(word.lowercase(Locale.ROOT))
    }

    /** 中文候选的生词角标：看学习语言译文的「可输入形态」（英语查 CEFR、日语查 JLPT） */
    private fun chineseMark(word: String, g: GlossDict?): String? {
        if (!levelMarkOn) return null
        val form = g?.inputFormFor(word) ?: return null
        return when (glossLang) {
            "en" -> levelsEn?.markFor(form.lowercase(Locale.ROOT))
            "ja" -> levelsJa?.markFor(form) ?: levelsJa?.markFor(form.removeSuffix("する"))
            else -> null
        }
    }

    /**
     * 按当前学习语言异步载入释义表；切语言 / 切「不显示」时下次刷新生效。
     * 载好时若正在组句，重刷一遍候选把译文补上（青简「译文到达后再补画，不等待」）。
     */
    private fun ensureGloss() {
        val lang = Prefs.learnLang(this)
        if (lang != glossLang) {
            // 换语言 / 关译文：旧语言的译文先撤下，别串台；新表载好再补
            gloss = null
            glossLang = null
        }
        if (lang == "off" || glossLang == lang || glossFailedLang == lang || glossLoading == lang) return
        glossLoading = lang
        Thread({
            val g = try {
                assets.open("gloss-$lang.bin").use { GlossDict.load(it) }
            } catch (t: Throwable) {
                null
            }
            // 生词等级表（~100KB）顺手载：CEFR / JLPT，候选右上角角标用
            val lv = try {
                assets.open("levels-$lang.bin").use { LevelDict.load(it) }
            } catch (t: Throwable) {
                null
            }
            mainHandler.post {
                if (glossLoading == lang) glossLoading = null
                if (Prefs.learnLang(this) != lang) return@post
                if (g != null) {
                    gloss = g
                    glossLang = lang
                    if (lv != null) {
                        when (lang) {
                            "en" -> levelsEn = lv
                            "ja" -> levelsJa = lv
                        }
                    }
                } else {
                    glossFailedLang = lang
                }
                if (pinyin.isNotEmpty()) updateComposing()
            }
        }, "jianji-gloss-load").start()
    }

    /**
     * 英文模式数据（english.bin + gloss-zh.bin，约 3.3MB）首次用英文时异步载入；
     * 载好前候选只显示原样输入，载好后自动补联想词与中文释义。
     */
    private fun ensureEnglish() {
        if (englishDict != null || englishLoading || englishFailed) return
        englishLoading = true
        Thread({
            val d = try {
                assets.open("english.bin").use { EnglishDict.load(it) }
            } catch (t: Throwable) {
                null
            }
            val g = try {
                assets.open("gloss-zh.bin").use { GlossDict.load(it) }
            } catch (t: Throwable) {
                null
            }
            // 英文候选的生词角标（CEFR）也顺手载
            val lv = try {
                assets.open("levels-en.bin").use { LevelDict.load(it) }
            } catch (t: Throwable) {
                null
            }
            mainHandler.post {
                englishLoading = false
                if (d == null || g == null) {
                    englishFailed = true
                    return@post
                }
                englishDict = d
                zhGloss = g
                if (lv != null) levelsEn = lv
                if (englishMode && englishBuf.isNotEmpty()) updateComposing()
            }
        }, "jianji-english-load").start()
    }

    /** 学习语言变更（测试页 / 未来的设置页写入 Prefs 时回调）：立即按新语言重载并重画 */
    private fun onLearnLangChanged() {
        ensureGloss()
        if (pinyin.isNotEmpty()) updateComposing()
    }

    private fun clearComposing() {
        pinyin.setLength(0)
        englishBuf.setLength(0)
        // 组句被打断（删光 / 收尾）：本次不学整串短语
        sessionText.setLength(0)
        sessionPinyin.setLength(0)
        candidates = emptyList()
        englishSuggestions = emptyList()
        candidateItems = emptyList()
        if (::candidateBar.isInitialized) candidateBar.clear()
        if (overlay == Overlay.GRID) setOverlay(Overlay.NONE)
        // 删光输入后：右上按钮从「更多候选 ˅˅」回到「收起键盘 ˅」，工具图标恢复显示
        updateRightButton()
    }

    /** 候选区右侧按钮语义：无候选 = 收起键盘；有候选 = 展开更多候选；面板展开中 = 收起面板 */
    private fun onRightButtonTap() {
        when (moreButton.mode) {
            HideKeyboardButton.Mode.HIDE -> requestHideSelf(0)
            HideKeyboardButton.Mode.MORE -> setOverlay(Overlay.GRID)
            HideKeyboardButton.Mode.COLLAPSE -> setOverlay(Overlay.NONE)
        }
    }

    /** 工具图标（剪贴板 / 表情）：点开对应面板；再点一次收起 */
    private fun toggleOverlay(target: Overlay) {
        setOverlay(if (overlay == target) Overlay.NONE else target)
    }

    private fun updateRightButton() {
        if (!::moreButton.isInitialized) return
        moreButton.mode = when {
            // 表情面板：右上角按钮改为「收起键盘 ˅」；右下角（表情面板内）才是返回键盘
            overlay == Overlay.EMOJI -> HideKeyboardButton.Mode.HIDE
            overlay != Overlay.NONE -> HideKeyboardButton.Mode.COLLAPSE
            candidateItems.isNotEmpty() -> HideKeyboardButton.Mode.MORE
            else -> HideKeyboardButton.Mode.HIDE
        }
        if (::gearButton.isInitialized) {
            clipButton.active = overlay == Overlay.CLIP
            emojiButton.active = overlay == Overlay.EMOJI
            // 剪贴板面板打开时整行图标隐藏（面板顶边正好落在图标行上沿；表情/更多候选保持显示）
            rightRow?.visibility = if (overlay == Overlay.CLIP) View.INVISIBLE else View.VISIBLE
            // 输入中（有组句内容）自动隐藏 [剪贴板][表情][设置]，候选栏占满整个宽度
            val showTools = pinyin.isEmpty() && englishBuf.isEmpty()
            val vis = if (showTools) View.VISIBLE else View.GONE
            if (gearButton.visibility != vis) {
                clipButton.visibility = vis
                emojiButton.visibility = vis
                gearButton.visibility = vis
            }
            val lp = candidateBar.layoutParams as FrameLayout.LayoutParams
            val margin = if (showTools) barMarginTools else barMarginMode
            if (lp.rightMargin != margin) {
                lp.rightMargin = margin
                candidateBar.layoutParams = lp
            }
        }
    }

    /** 键盘上方的覆盖面板互斥切换（更多候选 / 剪贴板 / 表情） */
    private fun setOverlay(next: Overlay) {
        overlay = next
        // 面板盖在键盘区上：键盘停画（INVISIBLE 保留占位，窗口高度不变），面板底更干净
        if (::keyboardView.isInitialized) {
            keyboardView.visibility = if (next == Overlay.NONE) View.VISIBLE else View.INVISIBLE
        }
        if (::gridPanel.isInitialized) {
            if (next == Overlay.GRID) {
                syncPanelHeight(gridPanel)
                gridPanel.setItems(candidateItems)
            }
            gridPanel.visibility = if (next == Overlay.GRID) View.VISIBLE else View.GONE
        }
        if (::clipPanel.isInitialized) {
            if (next == Overlay.CLIP) {
                ClipboardStore.capture(this, force = false)
                clipPanel.refresh()
                syncPanelHeight(clipPanel)
            }
            clipPanel.visibility = if (next == Overlay.CLIP) View.VISIBLE else View.GONE
        }
        if (::emojiPanel.isInitialized) {
            if (next == Overlay.EMOJI) {
                ensureEmoji()
                emojiDict?.let { emojiPanel.setCategories(it.cats) }
                syncPanelHeight(emojiPanel)
            }
            emojiPanel.visibility = if (next == Overlay.EMOJI) View.VISIBLE else View.GONE
        }
        updateRightButton()
    }

    /**
     * 面板高度对齐当前键盘（字母页/数字页/符号页高度不同）。
     * 剪贴板面板再往上多盖住候选区（图标行 56dp）：打开时 [📋][🙂][⚙][˅] 整行正好被面板顶边盖住、不显示。
     */
    private fun syncPanelHeight(v: View) {
        if (!::keyboardView.isInitialized) return
        var h = keyboardView.height
        if (h <= 0) return
        if (v === clipPanel) h += (56 * resources.displayMetrics.density).toInt()
        val lp = v.layoutParams as FrameLayout.LayoutParams
        if (lp.height != h) {
            lp.height = h
            v.layoutParams = lp
        }
    }

    /** 面板里点一条：收尾当前组句后上屏；close = 剪贴板上屏后收起面板（表情可连点不关） */
    private fun commitPanelText(text: String, close: Boolean) {
        val ic = currentInputConnection ?: return
        flushAnyComposing()
        ic.commitText(text, 1)
        if (close) setOverlay(Overlay.NONE) else updateRightButton()
    }

    /** 面板里的「设置」：打开正式设置页 */
    private fun openSettings() {
        startActivity(Intent(this, SettingsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** 系统剪贴板变化（Android 10+ 只在允许时回调）：立刻记入历史 */
    private fun onPrimaryClipChanged() {
        if (ClipboardStore.capture(this, force = true) && overlay == Overlay.CLIP && ::clipPanel.isInitialized) {
            clipPanel.refresh()
        }
    }

    private fun ensureClipListener() {
        if (clipListenerAttached) return
        val cm = getSystemService(ClipboardManager::class.java) ?: return
        cm.addPrimaryClipChangedListener(clipListener)
        clipListenerAttached = true
    }

    /** 表情表异步载入（22KB，载好补画） */
    private fun ensureEmoji() {
        if (emojiDict != null || emojiLoading) return
        emojiLoading = true
        Thread({
            val d = try {
                assets.open("emoji.bin").use { EmojiDict.load(it) }
            } catch (t: Throwable) {
                null
            }
            mainHandler.post {
                emojiLoading = false
                if (d != null) {
                    emojiDict = d
                    if (::emojiPanel.isInitialized && overlay == Overlay.EMOJI) emojiPanel.setCategories(d.cats)
                }
            }
        }, "jianji-emoji-load").start()
    }

    private fun backspace() {
        val ic = currentInputConnection ?: return
        if (ic.getSelectedText(0).isNullOrEmpty()) {
            ic.deleteSurroundingTextInCodePoints(1, 0)
        } else {
            ic.commitText("", 1)
        }
    }

    /** ⌫ 上划：清空输入框（连同组句） */
    private fun clearInputField() {
        val ic = currentInputConnection ?: return
        pinyin.setLength(0)
        englishBuf.setLength(0)
        ic.finishComposingText()
        clearComposing()
        ic.performContextMenuAction(android.R.id.selectAll)
        ic.commitText("", 1)
    }

    private fun handleEnter() {
        if (!sendDefaultEditorAction(true)) {
            val ic = currentInputConnection ?: return
            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
        }
    }

    /**
     * ⇧（对齐搜狗官方行为）：单击 = 下一个字母大写、自动回落；
     * 未输入时再点一下 = 大写锁定；锁定状态下单击 = 解锁。
     * 不依赖双击时间窗，慢速双击也有效。
     */
    private fun onShiftTap() {
        shift = when (shift) {
            ShiftState.OFF -> ShiftState.SINGLE
            ShiftState.SINGLE -> ShiftState.LOCK
            ShiftState.LOCK -> ShiftState.OFF
        }
        keyboardView.shiftState = shift
    }

    // —————— 空格滑光标 / 输入法选择（地球） ——————

    /**
     * 空格左右滑：把光标挪 step 格（逐格触发）。
     * 组句中的不挪（免得内联拼音和光标拆开）；拿不到光标位置（个别编辑器）退化为方向键。
     */
    private fun moveCursorBy(step: Int) {
        val ic = currentInputConnection ?: return
        if (pinyin.isNotEmpty() || englishBuf.isNotEmpty()) return
        val sel = try {
            ic.getExtractedText(ExtractedTextRequest(), 0)?.selectionStart ?: -1
        } catch (_: Exception) {
            -1
        }
        if (sel < 0) {
            val key = if (step > 0) KeyEvent.KEYCODE_DPAD_RIGHT else KeyEvent.KEYCODE_DPAD_LEFT
            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, key))
            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, key))
            return
        }
        ic.setSelection(sel + step, sel + step)
    }

    /** 中/英键长按（地球）：弹输入法选择器 */
    private fun showImePicker() {
        try {
            getSystemService(InputMethodManager::class.java)?.showInputMethodPicker()
        } catch (_: Exception) {
            // 个别 ROM 不给输入法弹选择器；忽略
        }
    }

    // —————— 音效与振动 ——————

    private fun refreshFeedbackFlags() {
        @Suppress("DEPRECATION")
        val haptic = systemSwitchOn(Settings.System.HAPTIC_FEEDBACK_ENABLED)
        vibrateOn = Prefs.isVibrateOn(this) && haptic
        vibrateLevel = Prefs.vibrateLevel(this)
        levelMarkOn = Prefs.isLevelMarkOn(this)
    }

    private fun systemSwitchOn(key: String): Boolean = try {
        Settings.System.getInt(contentResolver, key, 1) == 1
    } catch (_: Exception) {
        true
    }

    /**
     * 振动强度（弱 / 标准 / 强）：系统触感常量（KEYBOARD_TAP）强度写死，只有振动器 API 能调。
     *
     * 注意本机（Lenovo L71091）振动器**不支持幅度控制**（dumpsys：mCapabilities=[ON_CALLBACK]，
     * mMaxAmplitudes count=0）——幅度参数会被系统忽略，三档只能靠**时长**拉开差距：
     * 5ms（微微一点）/ 15ms（常规）/ 30ms（明显更重）。幅度值留给支持幅度控制的设备。
     */
    private fun rebuildVibration() {
        val v = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Vibrator::class.java)
        }
        vibrator = v
        val amp = v?.hasAmplitudeControl() == true
        fun shot(ms: Long, level: Int): VibrationEffect? = try {
            VibrationEffect.createOneShot(ms, if (amp) level else VibrationEffect.DEFAULT_AMPLITUDE)
        } catch (_: Throwable) {
            null
        }
        vibeWeak = shot(5, 35)
        vibeStandard = shot(15, 110)
        vibeStrong = shot(30, 255)
    }

    /** 按键振动（跟随系统「触感反馈」开关） */
    private fun feedback() {
        if (!vibrateOn) return
        val eff = when (vibrateLevel) {
            Prefs.VIBRATE_STANDARD -> vibeStandard
            Prefs.VIBRATE_STRONG -> vibeStrong
            else -> vibeWeak
        }
        val v = vibrator
        if (eff != null && v != null && v.hasVibrator()) {
            try {
                v.vibrate(eff)
            } catch (_: Exception) {
                // 个别 ROM 会抛；忽略
            }
        } else if (::keyboardView.isInitialized) {
            keyboardView.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        }
    }

    private companion object {
        /** 英文模式最多联想多少个词（候选栏可横滚，「更多候选」面板可翻页） */
        const val ENGLISH_SUGGEST_LIMIT = 100
    }
}
