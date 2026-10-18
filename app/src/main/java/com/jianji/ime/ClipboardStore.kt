package com.jianji.ime

import android.content.ClipboardManager
import android.content.Context
import java.nio.charset.StandardCharsets
import java.util.Base64

/**
 * 剪贴板历史与常用语的存取（M4）。
 *
 * - 历史：最近 [HISTORY_MAX] 条剪贴板文字，最新在前，重复内容上移（不重复存）；
 * - 常用语：用户手动保存的短语，剪贴板面板里置顶显示，设置页可增删；
 * - 编码：[encode] / [decode] 是纯逻辑（JVM 可测），每条 Base64 一行，兼容换行的内容；
 * - 本类不做系统级监听，只在「开输入视图 / 打开剪贴板面板」时抓取系统剪贴板（Android 10+ 限制）。
 */
object ClipboardStore {

    /** 历史条数上限 */
    const val HISTORY_MAX = 50

    /** 常用语条数上限 */
    const val PHRASE_MAX = 100

    /** 单条文字长度上限（超长不记录） */
    const val TEXT_MAX = 1000

    private const val KEY_HISTORY = "clip_history"
    private const val KEY_PHRASES = "phrases"

    /** 删除/清空后跳过的那条系统剪贴板内容（持久化，重启依然有效） */
    private const val KEY_SKIP = "clip_skip"

    // —————— 纯逻辑（JVM 测试台用） ——————

    /** 每项 Base64（UTF-8），一行一条 */
    fun encode(items: List<String>): String =
        items.joinToString("\n") {
            Base64.getEncoder().encodeToString(it.toByteArray(StandardCharsets.UTF_8))
        }

    fun decode(raw: String?): MutableList<String> {
        if (raw.isNullOrEmpty()) return mutableListOf()
        val out = ArrayList<String>()
        for (line in raw.lineSequence()) {
            if (line.isEmpty()) continue
            try {
                out.add(String(Base64.getDecoder().decode(line), StandardCharsets.UTF_8))
            } catch (_: IllegalArgumentException) {
                // 坏行跳过，不拖垮其余内容
            }
        }
        return out
    }

    /** 规范化一条可存的文字：空/纯空白/超长 → null */
    fun normalize(text: String): String? {
        val t = text.trim()
        if (t.isEmpty() || t.length > TEXT_MAX) return null
        return t
    }

    /**
     * 打开面板顺手一抓（force=false）时是否跳过这条：
     * 与「删除/清空时记下的跳过内容」相同才跳过；剪贴板真变化（force=true）从不跳过。
     */
    fun shouldSkip(text: String, force: Boolean, skip: String?): Boolean =
        !force && text == skip

    /**
     * 新内容入历史：已在开头则不动；在别处则上移；否则插到最前。超上限截掉最旧的。
     * @return 是否有变化（决定要不要回写）
     */
    fun pushHistory(history: MutableList<String>, text: String): Boolean {
        val t = normalize(text) ?: return false
        val old = history.indexOf(t)
        if (old == 0) return false
        if (old > 0) history.removeAt(old)
        history.add(0, t)
        while (history.size > HISTORY_MAX) history.removeAt(history.size - 1)
        return true
    }

    // —————— 历史（Android） ——————

    fun history(context: Context): MutableList<String> =
        decode(Prefs.get(context).getString(KEY_HISTORY, null))

    fun saveHistory(context: Context, list: List<String>) {
        Prefs.get(context).edit().putString(KEY_HISTORY, encode(list)).apply()
    }

    private fun skipOf(context: Context): String? =
        Prefs.get(context).getString(KEY_SKIP, null)

    private fun setSkip(context: Context, text: String?) {
        val e = Prefs.get(context).edit()
        if (text == null) e.remove(KEY_SKIP) else e.putString(KEY_SKIP, text)
        e.apply()
    }

    /** 当前系统剪贴板文字（规范化后）；没有 / 读不到 → null */
    fun currentClipboardText(context: Context): String? = try {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val clip = cm?.primaryClip
        if (clip != null && clip.itemCount > 0) {
            clip.getItemAt(0).coerceToText(context)?.toString()?.let(::normalize)
        } else {
            null
        }
    } catch (_: Throwable) {
        null
    }

    /** 从历史删一条；若这条还留在系统剪贴板上，记下跳过，避免再被自动抓回来 */
    fun removeHistory(context: Context, text: String): Boolean {
        val t = normalize(text) ?: return false
        val h = history(context)
        if (!h.remove(t)) return false
        saveHistory(context, h)
        if (currentClipboardText(context) == t) setSkip(context, t)
        return true
    }

    /**
     * 抓取系统剪贴板入历史。
     * @param force true = 剪贴板刚变化（监听回调），总是处理；false = 打开面板时顺手一抓，跳过刚被清空的那条
     * @return 是否有变化
     */
    fun capture(context: Context, force: Boolean): Boolean = try {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val clip = cm?.primaryClip
        val text = if (clip != null && clip.itemCount > 0) {
            clip.getItemAt(0).coerceToText(context)?.toString()
        } else {
            null
        }
        if (text == null) {
            false
        } else {
            val t = normalize(text)
            if (t == null || shouldSkip(t, force, skipOf(context))) {
                false
            } else {
                val h = history(context)
                if (pushHistory(h, t)) {
                    saveHistory(context, h)
                    true
                } else {
                    false
                }
            }
        }
    } catch (_: Throwable) {
        false
    }

    fun clearHistory(context: Context) {
        // 记住当前系统剪贴板内容：清空后面板再打开时不要把它立刻抓回来
        setSkip(context, currentClipboardText(context))
        Prefs.get(context).edit().remove(KEY_HISTORY).apply()
    }

    // —————— 常用语（Android） ——————

    fun phrases(context: Context): MutableList<String> =
        decode(Prefs.get(context).getString(KEY_PHRASES, null))

    fun savePhrases(context: Context, list: List<String>) {
        Prefs.get(context).edit().putString(KEY_PHRASES, encode(list)).apply()
    }

    /** 添加一条常用语（去重、限量）；返回是否成功 */
    fun addPhrase(context: Context, text: String): Boolean {
        val t = normalize(text) ?: return false
        val list = phrases(context)
        if (list.contains(t) || list.size >= PHRASE_MAX) return false
        list.add(t)
        savePhrases(context, list)
        return true
    }

    /** 移除一条常用语；返回是否移除到 */
    fun removePhrase(context: Context, text: String): Boolean {
        val list = phrases(context)
        if (!list.remove(text)) return false
        savePhrases(context, list)
        return true
    }
}
