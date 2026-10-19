import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 简记「生词等级」数据管线：青简 levels-<语言>.tsv → app 资产 levels-<语言>.bin
 *
 * 用法（JDK 21 单文件运行，工作目录 tools/dictgen）：
 *   java LevelGen.java <levels.tsv> <levels.bin>
 *   java LevelGen.java --lookup <levels.bin> <词>
 *
 * levels.tsv 每行： 词 \t 等级；（# 开头为注释，`# levels` 行给出等级从易到难的顺序）
 * 英文词统一小写后入库（查词也小写）；同一词多次出现取最易的等级。
 *
 * levels.bin 布局（大端）：
 *   char[4] "JJL1" | int levelCount | int count | int wordBlobLen
 *   levelCount × (byte len + UTF-8 名)   等级名（从易到难：A1..C2 / N5..N1）
 *   byte[wordBlobLen]                   词（UTF-8，按字节序升序）
 *   int[count+1]                        wordBlob 内偏移
 *   byte[count]                         等级下标
 */
public class LevelGen {

    public static void main(String[] args) throws Exception {
        if (args.length == 3 && args[0].equals("--lookup")) {
            lookup(args[1], args[2]);
            return;
        }
        if (args.length != 2) {
            System.err.println("用法: java LevelGen.java <levels.tsv> <levels.bin>");
            System.err.println("      java LevelGen.java --lookup <levels.bin> <词>");
            System.exit(2);
        }
        build(args[0], args[1]);
    }

    static void build(String tsvPath, String outPath) throws Exception {
        long t0 = System.currentTimeMillis();

        List<String> levels = new ArrayList<>();
        Map<String, Integer> map = new HashMap<>(32_768);
        int total = 0, comments = 0, invalid = 0, dup = 0, lowered = 0;

        for (String raw : Files.readAllLines(Path.of(tsvPath), StandardCharsets.UTF_8)) {
            if (raw.isEmpty()) continue;
            total++;
            if (raw.charAt(0) == '#') {
                comments++;
                String[] f = raw.substring(1).split("\t");
                if (f.length >= 1 && f[0].trim().equals("levels")) {
                    levels.clear();
                    for (int i = 1; i < f.length; i++) {
                        String v = f[i].trim();
                        if (!v.isEmpty()) levels.add(v);
                    }
                }
                continue;
            }
            String[] parts = raw.split("\t");
            if (parts.length < 2) { invalid++; continue; }
            String word = parts[0].trim().toLowerCase(Locale.ROOT);
            String lv = parts[1].trim();
            if (word.isEmpty() || lv.isEmpty()) { invalid++; continue; }
            int idx = levels.indexOf(lv);
            if (idx < 0) { invalid++; continue; }
            Integer old = map.get(word);
            if (old == null) {
                map.put(word, idx);
            } else {
                dup++;
                if (idx < old) { map.put(word, idx); lowered++; }
            }
        }
        if (levels.isEmpty()) throw new IllegalStateException("TSV 里没有 `# levels` 表头");

        List<String> words = new ArrayList<>(map.keySet());
        Map<String, byte[]> utf8 = new HashMap<>(words.size() * 2);
        for (String w : words) utf8.put(w, w.getBytes(StandardCharsets.UTF_8));
        words.sort(Comparator.comparing(utf8::get, LevelGen::compareBytes));

        int n = words.size();
        byte[] wordBlob;
        int[] wordOff = new int[n + 1];
        {
            int len = 0;
            for (String w : words) len += utf8.get(w).length;
            wordBlob = new byte[len];
            int p = 0;
            for (int i = 0; i < n; i++) {
                wordOff[i] = p;
                byte[] b = utf8.get(words.get(i));
                System.arraycopy(b, 0, wordBlob, p, b.length);
                p += b.length;
            }
            wordOff[n] = p;
        }

        Path out = Path.of(outPath);
        if (out.getParent() != null) Files.createDirectories(out.getParent());
        try (DataOutputStream os = new DataOutputStream(
                new BufferedOutputStream(Files.newOutputStream(out), 1 << 20))) {
            os.writeBytes("JJL1");
            os.writeInt(levels.size());
            os.writeInt(n);
            os.writeInt(wordBlob.length);
            for (String lv : levels) {
                byte[] b = lv.getBytes(StandardCharsets.UTF_8);
                os.writeByte(b.length);
                os.write(b);
            }
            os.write(wordBlob);
            for (int v : wordOff) os.writeInt(v);
            for (String w : words) os.writeByte(map.get(w));
        }

        long ms = System.currentTimeMillis() - t0;
        System.out.println("等级 " + levels + "，总行 " + total + "（注释 " + comments
                + "，无效 " + invalid + "，重复 " + dup + "，其中放宽 " + lowered
                + "）");
        System.out.println("词条 " + n);
        System.out.println("输出 " + out.toAbsolutePath() + " = " + Files.size(out) + " B（" + ms + " ms）");
        for (String w : new String[] {"hello", "develop", "car", "かかる", "開発", "食べる"}) {
            Integer i = map.get(w);
            System.out.println("抽查 " + w + " = " + (i == null ? "未收录" : levels.get(i)));
        }
    }

    static int compareBytes(byte[] a, byte[] b) {
        int m = Math.min(a.length, b.length);
        for (int i = 0; i < m; i++) {
            int c = (a[i] & 0xff) - (b[i] & 0xff);
            if (c != 0) return c;
        }
        return a.length - b.length;
    }

    // —————— 抽查（近似 Android 端逻辑） ——————

    static void lookup(String binPath, String word) throws Exception {
        byte[] buf = Files.readAllBytes(Path.of(binPath));
        if (!(buf[0] == 'J' && buf[1] == 'J' && buf[2] == 'L' && buf[3] == '1'))
            throw new IllegalStateException("bad magic");
        int p = 4;
        int levelCount = readInt(buf, p); p += 4;
        int count = readInt(buf, p); p += 4;
        int wordLen = readInt(buf, p); p += 4;
        String[] levels = new String[levelCount];
        for (int i = 0; i < levelCount; i++) {
            int l = buf[p] & 0xff; p += 1;
            levels[i] = new String(buf, p, l, StandardCharsets.UTF_8); p += l;
        }
        int wordPos = p; p += wordLen;
        int offPos = p; p += (count + 1) * 4;
        int lvPos = p;

        String q = word.toLowerCase(Locale.ROOT);
        byte[] qb = q.getBytes(StandardCharsets.UTF_8);
        int lo = 0, hi = count;
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            int a = readInt(buf, offPos + mid * 4);
            int b = readInt(buf, offPos + (mid + 1) * 4);
            if (compareSlice(buf, wordPos + a, b - a, qb) < 0) lo = mid + 1; else hi = mid;
        }
        if (lo < count) {
            int a = readInt(buf, offPos + lo * 4);
            int b = readInt(buf, offPos + (lo + 1) * 4);
            String w = new String(buf, wordPos + a, b - a, StandardCharsets.UTF_8);
            if (w.equals(q)) {
                System.out.println("词条 " + count + "，" + word + " = " + levels[buf[lvPos + lo] & 0xff]);
                return;
            }
        }
        System.out.println("词条 " + count + "，" + word + " = 未收录");
    }

    static int compareSlice(byte[] buf, int pos, int len, byte[] q) {
        int n = Math.min(len, q.length);
        for (int i = 0; i < n; i++) {
            int c = (buf[pos + i] & 0xff) - (q[i] & 0xff);
            if (c != 0) return c;
        }
        return len - q.length;
    }

    static int readInt(byte[] buf, int p) {
        return ((buf[p] & 0xff) << 24) | ((buf[p + 1] & 0xff) << 16) | ((buf[p + 2] & 0xff) << 8) | (buf[p + 3] & 0xff);
    }
}
