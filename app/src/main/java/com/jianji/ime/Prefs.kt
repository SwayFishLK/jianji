package com.jianji.ime

import android.content.Context
import android.content.SharedPreferences

/** 极简设置存储（正式设置页 M5 做；当前服务键盘开关与调试页） */
object Prefs {
    private const val NAME = "jianji_prefs"

    const val KEY_SOUND = "key_sound"
    const val KEY_VIBRATE = "key_vibrate"

    fun get(context: Context): SharedPreferences =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun isSoundOn(context: Context): Boolean =
        get(context).getBoolean(KEY_SOUND, true)

    fun setSoundOn(context: Context, on: Boolean) =
        get(context).edit().putBoolean(KEY_SOUND, on).apply()

    fun isVibrateOn(context: Context): Boolean =
        get(context).getBoolean(KEY_VIBRATE, true)

    fun setVibrateOn(context: Context, on: Boolean) =
        get(context).edit().putBoolean(KEY_VIBRATE, on).apply()

    // —————— M2：用户词频（学习） ——————

    private const val KEY_USER_WORDS = "user_words"

    /** 用户词频：每行 word\tcount */
    fun loadUserWords(context: Context): HashMap<String, Int> {
        val raw = get(context).getString(KEY_USER_WORDS, null) ?: return HashMap()
        val map = HashMap<String, Int>(raw.length / 8 + 8)
        for (line in raw.lineSequence()) {
            val tab = line.lastIndexOf('\t')
            if (tab <= 0) continue
            val count = line.substring(tab + 1).toIntOrNull() ?: continue
            map[line.substring(0, tab)] = count
        }
        return map
    }

    fun saveUserWords(context: Context, words: Map<String, Int>) {
        val sb = StringBuilder(words.size * 12)
        for ((w, c) in words) sb.append(w).append('\t').append(c).append('\n')
        get(context).edit().putString(KEY_USER_WORDS, sb.toString()).apply()
    }

    // —————— M3：学习语言（一次一种；'en' / 'ja' / 'off'） ——————

    const val KEY_LEARN_LANG = "key_learn_lang"

    fun learnLang(context: Context): String = get(context).getString(KEY_LEARN_LANG, "en") ?: "en"

    fun setLearnLang(context: Context, lang: String) =
        get(context).edit().putString(KEY_LEARN_LANG, lang).apply()
}
