package com.jianji.ime

import android.content.Context
import android.content.SharedPreferences

/** 极简设置存储（正式设置页 M5 做；当前服务键盘开关与调试页） */
object Prefs {
    private const val NAME = "jianji_prefs"

    const val KEY_VIBRATE = "key_vibrate"

    fun get(context: Context): SharedPreferences =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun isVibrateOn(context: Context): Boolean =
        get(context).getBoolean(KEY_VIBRATE, true)

    fun setVibrateOn(context: Context, on: Boolean) =
        get(context).edit().putBoolean(KEY_VIBRATE, on).apply()

    // —————— v0.2：振动强度（弱 / 标准 / 强，默认弱） ——————

    const val KEY_VIBRATE_LEVEL = "key_vibrate_level"
    const val VIBRATE_WEAK = "weak"
    const val VIBRATE_STANDARD = "standard"
    const val VIBRATE_STRONG = "strong"

    fun vibrateLevel(context: Context): String =
        get(context).getString(KEY_VIBRATE_LEVEL, VIBRATE_WEAK) ?: VIBRATE_WEAK

    fun setVibrateLevel(context: Context, level: String) =
        get(context).edit().putString(KEY_VIBRATE_LEVEL, level).apply()

    // —————— v0.2：模糊音（逗号分隔的组名：zh 平翘舌 / nasal 前后鼻音 / nl n-l） ——————

    const val KEY_FUZZY = "key_fuzzy"

    fun fuzzyGroups(context: Context): String =
        get(context).getString(KEY_FUZZY, "") ?: ""

    fun setFuzzyGroups(context: Context, groups: String) =
        get(context).edit().putString(KEY_FUZZY, groups).apply()

    // —————— M2 / v0.2：学习数据（用户词表 + 搭配表，格式见 Learning） ——————

    const val KEY_USER_WORDS = "user_words"
    const val KEY_PAIRS = "user_pairs"
    fun loadWordStats(context: Context): HashMap<String, Learning.WordStat> =
        Learning.parseWords(get(context).getString(KEY_USER_WORDS, null))

    fun saveWordStats(context: Context, words: Map<String, Learning.WordStat>) =
        get(context).edit().putString(KEY_USER_WORDS, Learning.serializeWords(words)).apply()

    fun loadPairs(context: Context): HashMap<String, Int> =
        Learning.parsePairs(get(context).getString(KEY_PAIRS, null))

    fun savePairs(context: Context, pairs: Map<String, Int>) =
        get(context).edit().putString(KEY_PAIRS, Learning.serializePairs(pairs)).apply()

    // —————— v0.2：自造短语（同一串重复输入 → 整串出候选，格式见 Learning） ——————

    const val KEY_PHRASES = "user_phrases"

    fun loadPhrases(context: Context): HashMap<String, Learning.PhraseStat> =
        Learning.parsePhrases(get(context).getString(KEY_PHRASES, null))

    fun savePhrases(context: Context, phrases: Map<String, Learning.PhraseStat>) =
        get(context).edit().putString(KEY_PHRASES, Learning.serializePhrases(phrases)).apply()

    // —————— M3：学习语言（一次一种；'en' / 'ja' / 'off'） ——————

    const val KEY_LEARN_LANG = "key_learn_lang"

    fun learnLang(context: Context): String = get(context).getString(KEY_LEARN_LANG, "en") ?: "en"

    fun setLearnLang(context: Context, lang: String) =
        get(context).edit().putString(KEY_LEARN_LANG, lang).apply()

    // —————— v0.2：生词标记（候选右上角 B2 / N2 及以上等级角标，默认开） ——————

    const val KEY_LEVEL_MARK = "level_mark"

    fun isLevelMarkOn(context: Context): Boolean =
        get(context).getBoolean(KEY_LEVEL_MARK, true)

    fun setLevelMarkOn(context: Context, on: Boolean) =
        get(context).edit().putBoolean(KEY_LEVEL_MARK, on).apply()
}
