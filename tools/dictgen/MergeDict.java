import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 简记词库合并管线：青简 dict.tsv + 白霜拼音（rime-frost）筛选 + 热词表 → 合并 TSV
 *
 * 用法（JDK 21 单文件运行，工作目录 tools/dictgen）：
 *   java MergeDict.java <青简 dict.tsv> <rime-frost 扁平目录> <hotwords.tsv> <merged.tsv>
 *
 * rime-frost 扁平目录里放这 8 个文件（从仓库对应路径复制/改名）：
 *   base.dict.yaml                    cn_dicts/base.dict.yaml
 *   ext.dict.yaml                     cn_dicts/ext.dict.yaml
 *   exthot.dict.yaml                  cn_dicts_cell/exthot.dict.yaml
 *   industry_product.dict.yaml        cn_dicts_cell/industry_product.dict.yaml
 *   game.dict.yaml                    cn_dicts_cell/game.dict.yaml
 *   sport.dict.yaml                   cn_dicts_cell/sport.dict.yaml
 *   place.dict.yaml                   cn_dicts_cell/place.dict.yaml
 *   name2.dict.yaml                   cn_dicts_cell/name2.dict.yaml
 *
 * 规则：
 *   - 青简原有词条与词频原样保留（避免新词把老词排序顶掉）；仅青简词频 <1000 的现代词
 *     允许热词抬升（如「大模型」9 → 5000），高频老词不动；
 *   - 白霜 base/ext 取 2~6 字、权重 ≥20（6 字 ≥50）；网络热词/行业词全收；
 *     game 权重 ≥50；sport/place/name2 权重 ≥100；
 *   - 新词词频 = 白霜权重；权重为 0 的热词/行业词给保底（2000/1500），保证能出候选；
 *   - 热词表：词 \t 音节 \t 词频；补青简没有的词、抬白霜低权重的新词，或抬青简 <1000 的词；
 *   - 输出交给 DictGen 编译成 dict.bin。
 *
 * 输出规模（2026-10-20 白霜 c34bbd8）：青简 13.3 万 + 新增约 23 万 ≈ 36 万条。
 */
public class MergeDict {

    private static final Pattern SYLLABLE = Pattern.compile("[a-z]+");

    record Entry(String word, String syllables, String full, int freq, String source) {}

    record RimeSpec(String file, String label, int minLen, int maxLen,
                    int min2, int min3, int min4, int min5, int min6, int zeroFloor) {
        int minWeight(int len) {
            return switch (len) {
                case 2 -> min2;
                case 3 -> min3;
                case 4 -> min4;
                case 5 -> min5;
                default -> min6;
            };
        }
    }

    static final List<RimeSpec> RIME_FILES = List.of(
            // 基础词库：2 字及常用词；6 字只留权重高的长词
            new RimeSpec("base.dict.yaml", "base", 2, 6, 20, 20, 20, 20, 50, 0),
            // 扩展词库：3 字及以上，人工注音
            new RimeSpec("ext.dict.yaml", "ext", 2, 6, 20, 20, 20, 20, 50, 0),
            // 网络热词：权重普遍是 0，全收，给保底词频
            new RimeSpec("exthot.dict.yaml", "exthot", 2, 6, 0, 0, 0, 0, 0, 2000),
            // 行业/产品词：全收，给保底
            new RimeSpec("industry_product.dict.yaml", "industry", 2, 6, 0, 0, 0, 0, 0, 1500),
            // 游戏/动漫/影视名：只留权重高的，避免 15 万个名字灌进来
            new RimeSpec("game.dict.yaml", "game", 2, 6, 50, 50, 50, 50, 50, 0),
            new RimeSpec("sport.dict.yaml", "sport", 2, 6, 100, 100, 100, 100, 100, 0),
            new RimeSpec("place.dict.yaml", "place", 2, 6, 100, 100, 100, 100, 100, 0),
            new RimeSpec("name2.dict.yaml", "name", 2, 6, 100, 100, 100, 100, 100, 0));

    static final String[] PROBES = {
            "五条悟", "绝绝子", "摆烂", "内卷", "破防", "元宇宙", "多模态", "具身智能",
            "显眼包", "搭子", "情绪价值", "松弛感", "淄博烧烤", "村超", "特种兵旅游",
            "雪糕刺客", "电子榨菜", "鸡娃", "双减", "新质生产力", "低空经济", "银发经济",
            "数智化", "智能体", "人形机器人", "预制菜", "直播带货", "短视频", "碳中和",
            "双碳", "新能源", "充电桩", "网约车", "共享单车", "健康码", "方舱", "弹窗",
            "带货", "破圈", "塌房", "社死", "美拉德", "围炉煮茶", "陆冲", "桨板",
            "黑神话悟空", "鸿蒙", "原生鸿蒙", "大模型", "生成式人工智能"
    };

    public static void main(String[] args) throws Exception {
        if (args.length != 4) {
            System.err.println("用法: java MergeDict.java <青简 dict.tsv> <rime-frost 扁平目录> <hotwords.tsv> <merged.tsv>");
            System.exit(2);
        }
        Path curPath = Path.of(args[0]);
        Path rimeDir = Path.of(args[1]);
        Path hotPath = Path.of(args[2]);
        Path outPath = Path.of(args[3]);

        long t0 = System.currentTimeMillis();
        LinkedHashMap<String, Entry> current = new LinkedHashMap<>(200_000);
        HashSet<String> currentKeys = new HashSet<>(200_000);
        int currentN = loadCurrent(curPath, current, currentKeys);

        LinkedHashMap<String, Entry> rime = new LinkedHashMap<>(400_000);
        Map<String, Integer> rimeStats = new LinkedHashMap<>();
        for (RimeSpec spec : RIME_FILES) {
            Path f = rimeDir.resolve(spec.file());
            if (!Files.exists(f)) {
                System.out.println("跳过（不存在）: " + f);
                continue;
            }
            int before = rime.size();
            int scanned = loadRime(f, spec, rime);
            rimeStats.put(spec.label(), scanned);
            System.out.println("  " + spec.file() + "：" + scanned + " 条入表（本文件新增 " + (rime.size() - before) + "）");
        }
        // 已经有青简词条的 key 不重复加
        int dupCurrent = 0;
        for (String key : rime.keySet()) {
            if (currentKeys.contains(key)) dupCurrent++;
        }

        // 热词：补缺 + 把白霜低权重的新词抬起来；青简原有词条不动
        LinkedHashMap<String, Entry> hotOnly = new LinkedHashMap<>(1024);
        int hotN = 0, hotBoost = 0, hotSkip = 0;
        if (Files.exists(hotPath)) {
            for (String ln : Files.readAllLines(hotPath, StandardCharsets.UTF_8)) {
                if (ln.isBlank() || ln.startsWith("#")) continue;
                String[] p = ln.split("\t");
                if (p.length < 3) continue;
                String word = p[0].trim();
                int freq;
                try {
                    freq = Integer.parseInt(p[2].trim());
                } catch (NumberFormatException ex) {
                    continue;
                }
                String parsed = parseSyllables(p[1].trim(), word.codePointCount(0, word.length()));
                if (parsed == null) {
                    System.out.println("热词跳过（音节不合法）: " + word + " / " + p[1]);
                    continue;
                }
                String[] sp = parsed.split(" ", 2);
                String full = sp[0], syl = sp[1];
                String key = word + "\u0000" + full;
                hotN++;
                if (currentKeys.contains(key)) {
                    // 青简已有词条：只抬极低频的现代词（如「大模型」原来只有 9），
                    // 高频老词保持原词频不动，避免排序回归。
                    Entry ce = current.get(key);
                    if (ce != null && freq > ce.freq() && ce.freq() < 1000) {
                        current.put(key, new Entry(word, syl, full, freq, "hot"));
                        hotBoost++;
                    } else {
                        hotSkip++;
                    }
                    continue;
                }
                Entry old = rime.get(key);
                if (old != null) {
                    if (freq > old.freq()) {
                        rime.put(key, new Entry(word, syl, full, freq, "hot"));
                        hotBoost++;
                    } else {
                        hotSkip++;
                    }
                } else {
                    Entry oldHot = hotOnly.get(key);
                    if (oldHot == null || freq > oldHot.freq()) {
                        hotOnly.put(key, new Entry(word, syl, full, freq, "hot"));
                    }
                }
            }
        }

        // 合并：青简全部保留；白霜去掉与青简重复的；热词补缺
        LinkedHashMap<String, Entry> out = new LinkedHashMap<>(420_000);
        out.putAll(current);
        int addedRime = 0;
        for (Map.Entry<String, Entry> e : rime.entrySet()) {
            if (currentKeys.contains(e.getKey())) continue;
            out.put(e.getKey(), e.getValue());
            addedRime++;
        }
        int addedHot = 0;
        for (Map.Entry<String, Entry> e : hotOnly.entrySet()) {
            if (out.putIfAbsent(e.getKey(), e.getValue()) == null) addedHot++;
        }

        Files.createDirectories(outPath.toAbsolutePath().getParent());
        try (BufferedWriter w = Files.newBufferedWriter(outPath, StandardCharsets.UTF_8)) {
            for (Entry e : out.values()) {
                w.write(e.word());
                w.write('\t');
                w.write(e.syllables());
                w.write('\t');
                w.write(Integer.toString(e.freq()));
                w.write('\n');
            }
        }

        // 统计
        Map<String, Integer> bySource = new HashMap<>();
        Map<Integer, Integer> byLen = new HashMap<>();
        for (Entry e : out.values()) {
            bySource.merge(e.source(), 1, Integer::sum);
            byLen.merge(e.word().codePointCount(0, e.word().length()), 1, Integer::sum);
        }
        List<Integer> lens = new ArrayList<>(byLen.keySet());
        lens.sort(Integer::compareTo);
        Set<String> words = new HashSet<>(out.size() * 2);
        for (Entry e : out.values()) words.add(e.word());

        System.out.println();
        System.out.println("青简词条 " + currentN + "；白霜扫描入表 " + rime.size()
                + "（与青简同 key " + dupCurrent + "）；热词 " + hotN
                + "（抬频 " + hotBoost + "，跳过 " + hotSkip + "）");
        System.out.println("新增：白霜 " + addedRime + "，热词补缺 " + addedHot + "；合计 " + out.size() + " 条");
        System.out.print("来源：");
        bySource.forEach((k, v) -> System.out.print(k + "=" + v + "  "));
        System.out.println();
        System.out.print("字数：");
        for (int l : lens) System.out.print(l + "字=" + byLen.get(l) + "  ");
        System.out.println();
        System.out.println("输出 " + outPath.toAbsolutePath() + " = " + Files.size(outPath) + " B（"
                + (System.currentTimeMillis() - t0) + " ms）");

        System.out.println();
        System.out.println("新词抽查：");
        int miss = 0;
        for (String p : PROBES) {
            if (words.contains(p)) {
                Entry e = firstByWord(out, p);
                System.out.println("  ✓ " + p + "  " + e.syllables() + "  freq=" + e.freq() + "  [" + e.source() + "]");
            } else {
                miss++;
                System.out.println("  ✗ " + p);
            }
        }
        System.out.println("抽查命中 " + (PROBES.length - miss) + "/" + PROBES.length);
    }

    // —————— 读青简 ——————

    static int loadCurrent(Path path, LinkedHashMap<String, Entry> out, HashSet<String> keys) throws Exception {
        int n = 0, bad = 0;
        for (String ln : Files.readAllLines(path, StandardCharsets.UTF_8)) {
            if (ln.isEmpty() || ln.startsWith("#")) continue;
            String[] p = ln.split("\t");
            if (p.length < 3) { bad++; continue; }
            String word = p[0].trim();
            String syl = p[1].trim().replaceAll(" +", " ");
            int freq;
            try {
                freq = Math.max(0, Integer.parseInt(p[2].trim()));
            } catch (NumberFormatException ex) {
                bad++;
                continue;
            }
            String full = syl.replace(" ", "");
            if (!SYLLABLE.matcher(full).matches() || full.isEmpty()) { bad++; continue; }
            String key = word + "\u0000" + full;
            Entry old = out.get(key);
            if (old == null) {
                out.put(key, new Entry(word, syl, full, freq, "青简"));
                keys.add(key);
                n++;
            } else if (freq > old.freq()) {
                out.put(key, new Entry(word, syl, full, freq, "青简"));
            }
        }
        if (bad > 0) System.out.println("青简无效行 " + bad);
        return n;
    }

    // —————— 读白霜 ——————

    static int loadRime(Path file, RimeSpec spec, LinkedHashMap<String, Entry> rime) throws Exception {
        int scanned = 0, badSyl = 0, lowWeight = 0, nonHan = 0;
        boolean started = false;
        for (String ln : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            if (!started) {
                if (ln.equals("...")) started = true;
                continue;
            }
            if (ln.isEmpty() || ln.startsWith("#")) continue;
            String[] p = ln.split("\t");
            if (p.length < 2) continue;
            String word = p[0].trim();
            if (word.isEmpty() || !isHan(word)) { nonHan++; continue; }
            int len = word.codePointCount(0, word.length());
            if (len < spec.minLen() || len > spec.maxLen()) continue;
            int wt;
            try {
                wt = p.length >= 3 ? Integer.parseInt(p[2].trim()) : 0;
            } catch (NumberFormatException ex) {
                wt = 0;
            }
            if (wt < spec.minWeight(len)) { lowWeight++; continue; }
            String parsed = parseSyllables(p[1].trim(), len);
            if (parsed == null) { badSyl++; continue; }
            String[] sp = parsed.split(" ", 2);
            String full = sp[0], syl = sp[1];
            int freq = wt > 0 ? wt : spec.zeroFloor();
            String key = word + "\u0000" + full;
            Entry old = rime.get(key);
            if (old == null) {
                rime.put(key, new Entry(word, syl, full, freq, spec.label()));
            } else if (freq > old.freq()) {
                rime.put(key, new Entry(word, syl, full, freq, spec.label()));
            }
            scanned++;
        }
        if (nonHan > 0 || lowWeight > 0 || badSyl > 0) {
            // 白霜里少量注音是连写/不规范，静默跳过即可，这里只留一行提示
            System.out.println("    （跳过：非汉字 " + nonHan + "，权重低 " + lowWeight
                    + "，注音不合法 " + badSyl + "）");
        }
        return scanned;
    }

    /** 音节串 → "连写 空格分隔"；必须正好 n 个音节、每节 [a-z]+；lue/nue 归一成 lve/nve */
    static String parseSyllables(String raw, int n) {
        String[] parts = raw.toLowerCase(Locale.ROOT).trim().split(" +");
        if (parts.length != n) return null;
        StringBuilder full = new StringBuilder();
        StringBuilder syl = new StringBuilder();
        for (String s : parts) {
            String v = s.replace("lue", "lve").replace("nue", "nve");
            if (!SYLLABLE.matcher(v).matches()) return null;
            if (!full.isEmpty()) syl.append(' ');
            full.append(v);
            syl.append(v);
        }
        return full + " " + syl;
    }

    static boolean isHan(String s) {
        return s.codePoints().allMatch(cp -> Character.UnicodeScript.of(cp) == Character.UnicodeScript.HAN);
    }

    static Entry firstByWord(LinkedHashMap<String, Entry> out, String word) {
        for (Entry e : out.values()) {
            if (e.word().equals(word)) return e;
        }
        return null;
    }
}