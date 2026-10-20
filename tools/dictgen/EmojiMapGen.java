import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.BufferedInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 简记「中文词 → 一个 emoji」数据管线：
 *   青简 emoji-zh.tsv（Unicode CLDR zh annotations）+ 表情面板 emoji.bin（常用顺序）
 *   → app 资产 emoji-map.bin（每个词只留一个 emoji）。
 *
 * 用法（JDK 单文件运行）：
 *   java EmojiMapGen.java <emoji-zh.tsv> <emoji.bin> <emoji-map.bin>
 *
 * 选法：同一个词对应多个 emoji 时，取「常用」面板里更靠前的那个（和面板观感一致）；
 * 都不在常用里就取 CLDR 的第一个。少量高频词用 OVERRIDES 人工指定，避免「生气 → 🥹」这类跑偏。
 *
 * emoji-map.bin 布局（大端，全部 int 长度）：
 *   char[4] "JJX1" | int 词条数
 *   每条：int 词字节长 + 词（UTF-8）| int emoji 字节长 + emoji（UTF-8）
 */
public class EmojiMapGen {

    /** 只收 2~4 字的中文词（与候选栏 emoji 门槛一致；单字太常用，配 emoji 会变噪声） */
    private static final int MIN_WORD = 2;
    private static final int MAX_WORD = 4;

    /** 高频词人工校准（覆盖 CLDR 的自动选择） */
    private static final Map<String, String> OVERRIDES = Map.ofEntries(
        Map.entry("开心", "😄"),
        Map.entry("高兴", "😀"),
        Map.entry("难过", "😞"),
        Map.entry("生气", "😠"),
        Map.entry("喜欢", "😍"),
        Map.entry("可爱", "🥰"),
        Map.entry("加油", "💪"),
        Map.entry("猫", "🐱"),
        Map.entry("狗", "🐶"),
        Map.entry("吃饭", "🍚"),
        Map.entry("饿了", "🍜"),
        Map.entry("好吃", "😋"),
        Map.entry("咖啡", "☕"),
        Map.entry("生日", "🎂"),
        Map.entry("晚安", "😴"),
        Map.entry("早安", "🌞"),
        Map.entry("睡觉", "😴"),
        Map.entry("爱你", "❤️"),
        Map.entry("对不起", "🙇"),
        Map.entry("谢谢", "🙏"),
        Map.entry("厉害", "👍"),
        Map.entry("棒", "👍"),
        Map.entry("赞", "👍"),
        Map.entry("无语", "😑"),
        Map.entry("尴尬", "😬"),
        Map.entry("惊讶", "😲"),
        Map.entry("害怕", "😨"),
        Map.entry("紧张", "😰"),
        Map.entry("下雨", "🌧️"),
        Map.entry("太阳", "☀️"),
        Map.entry("工作", "💼"),
        Map.entry("上班", "💼"),
        Map.entry("下班", "🏠"),
        Map.entry("周末", "🎉"),
        Map.entry("旅行", "✈️"),
        Map.entry("电影", "🎬"),
        Map.entry("音乐", "🎵"),
        Map.entry("游戏", "🎮"),
        Map.entry("手机", "📱"),
        Map.entry("电脑", "💻"),
        Map.entry("学习", "📚")
    );

    public static void main(String[] args) throws Exception {
        if (args.length != 3) {
            System.err.println("用法: java EmojiMapGen.java <emoji-zh.tsv> <emoji.bin> <emoji-map.bin>");
            System.exit(2);
        }
        build(args[0], args[1], args[2]);
    }

    static void build(String tsvPath, String emojiBinPath, String outPath) throws Exception {
        long t0 = System.currentTimeMillis();

        // 常用面板顺序：rank 越小越靠前
        Map<String, Integer> rank = commonRank(emojiBinPath);

        int lines = 0, skipped = 0;
        Map<String, String> map = new LinkedHashMap<>();
        for (String raw : Files.readAllLines(Path.of(tsvPath), StandardCharsets.UTF_8)) {
            if (raw.isEmpty() || raw.startsWith("#")) continue;
            lines++;
            int tab = raw.indexOf('\t');
            if (tab <= 0) { skipped++; continue; }
            String word = raw.substring(0, tab).trim();
            String[] emojis = raw.substring(tab + 1).trim().split("\\s+");
            if (word.length() < MIN_WORD || word.length() > MAX_WORD || !isCjk(word) || emojis.length == 0) {
                skipped++;
                continue;
            }
            String best = emojis[0];
            int bestRank = rank.getOrDefault(best, Integer.MAX_VALUE);
            for (String e : emojis) {
                int r = rank.getOrDefault(e, Integer.MAX_VALUE);
                if (r < bestRank) {
                    bestRank = r;
                    best = e;
                }
            }
            map.put(word, best);
        }

        // 人工覆盖允许 1 个字（只挑「猫 / 狗」这类具体名词；单字常规词不收，免得「我 / 好」天天挂 emoji）
        for (Map.Entry<String, String> e : OVERRIDES.entrySet()) {
            String w = e.getKey();
            if (w.length() >= 1 && w.length() <= MAX_WORD) map.put(w, e.getValue());
        }

        // 排序输出（便于复查 / 稳定构建）
        Map<String, String> sorted = new TreeMap<>(map);
        Path out = Path.of(outPath);
        if (out.getParent() != null) Files.createDirectories(out.getParent());
        try (DataOutputStream os = new DataOutputStream(
                new BufferedOutputStream(Files.newOutputStream(out), 1 << 16))) {
            os.writeBytes("JJX1");
            os.writeInt(sorted.size());
            for (Map.Entry<String, String> e : sorted.entrySet()) {
                byte[] w = e.getKey().getBytes(StandardCharsets.UTF_8);
                byte[] m = e.getValue().getBytes(StandardCharsets.UTF_8);
                os.writeInt(w.length);
                os.write(w);
                os.writeInt(m.length);
                os.write(m);
            }
        }

        long ms = System.currentTimeMillis() - t0;
        System.out.println("从 " + tsvPath + " 读 " + lines + " 行，跳过 " + skipped);
        System.out.println("词条 " + sorted.size() + "，人工覆盖 " + OVERRIDES.size());
        System.out.println("输出 " + out.toAbsolutePath() + " = " + Files.size(out) + " B（" + ms + " ms）");
        for (String w : new String[] {"开心", "生气", "喜欢", "猫", "谢谢", "吃饭", "周末", "旅行", "咖啡"}) {
            System.out.println("  " + w + " → " + sorted.getOrDefault(w, "(无)"));
        }
    }

    /** 表情面板 emoji.bin（JJM1）：取「常用」分类的条目顺序 */
    static Map<String, Integer> commonRank(String path) throws Exception {
        Map<String, Integer> rank = new HashMap<>();
        try (DataInputStream in = new DataInputStream(
                new BufferedInputStream(Files.newInputStream(Path.of(path)), 1 << 16))) {
            byte[] magic = new byte[4];
            in.readFully(magic);
            if (!(magic[0] == 'J' && magic[1] == 'J' && magic[2] == 'M' && magic[3] == '1'))
                throw new IllegalStateException("bad emoji.bin magic");
            int cats = in.readInt();
            for (int c = 0; c < cats; c++) {
                int nl = in.readInt();
                String name = new String(in.readNBytes(nl), StandardCharsets.UTF_8);
                int cnt = in.readInt();
                List<String> items = new ArrayList<>(cnt);
                for (int i = 0; i < cnt; i++) {
                    int l = in.readInt();
                    items.add(new String(in.readNBytes(l), StandardCharsets.UTF_8));
                }
                if (name.equals("常用")) {
                    for (int i = 0; i < items.size(); i++) rank.putIfAbsent(items.get(i), i);
                }
            }
        }
        if (rank.isEmpty()) throw new IllegalStateException("emoji.bin 里没有「常用」分类");
        return rank;
    }

    static boolean isCjk(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c < '\u4e00' || c > '\u9fff') return false;
        }
        return true;
    }
}