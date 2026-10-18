import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/**
 * 简记英文词表数据管线：青简 english.tsv → app 资产 english.bin（英文模式候选联想词表）
 *
 * 用法（JDK 21 单文件运行）：
 *   java EnglishGen.java <english.tsv> <english.bin>
 *   java EnglishGen.java --lookup <english.bin> <前缀>
 *
 * english.tsv 每行：词 \t 编码 \t 词频（wordfreq 的 Zipf 频率 ×1000；# 开头为注释）。
 * 本词表两列内容相同（全小写、无撇号），查询按 UTF-8 字节序二分 + 前缀扫描。
 *
 * english.bin 布局（大端）：
 *   char[4] "JJE1" | int count | int wordBlobLen
 *   byte[wordBlobLen] 词（UTF-8，按字节序升序）
 *   int[count+1]      wordBlob 内偏移
 *   int[count]        词频（Zipf ×1000）
 */
public class EnglishGen {

    record Entry(String word, int freq) {}

    public static void main(String[] args) throws Exception {
        if (args.length == 3 && args[0].equals("--lookup")) {
            lookup(args[1], args[2]);
            return;
        }
        if (args.length != 2) {
            System.err.println("用法: java EnglishGen.java <english.tsv> <english.bin>");
            System.err.println("      java EnglishGen.java --lookup <english.bin> <前缀>");
            System.exit(2);
        }
        build(args[0], args[1]);
    }

    // —————— 构建 ——————

    static void build(String tsvPath, String outPath) throws Exception {
        long t0 = System.currentTimeMillis();

        int total = 0, comments = 0, invalid = 0, dup = 0;
        List<Entry> list = new ArrayList<>(100_000);
        HashSet<String> seen = new HashSet<>(200_000);

        for (String raw : Files.readAllLines(Path.of(tsvPath), StandardCharsets.UTF_8)) {
            if (raw.isEmpty()) continue;
            total++;
            if (raw.charAt(0) == '#') { comments++; continue; }
            String[] parts = raw.split("\t");
            if (parts.length < 3) { invalid++; continue; }
            String word = parts[0].trim();
            if (word.isEmpty()) { invalid++; continue; }
            int freq;
            try {
                freq = Integer.parseInt(parts[2].trim());
            } catch (Exception e) {
                invalid++;
                continue;
            }
            if (!seen.add(word)) { dup++; continue; }
            list.add(new Entry(word, freq));
        }

        Map<String, byte[]> utf8 = new HashMap<>(list.size() * 2);
        for (Entry e : list) utf8.put(e.word(), e.word().getBytes(StandardCharsets.UTF_8));
        list.sort(Comparator.comparing(e -> utf8.get(e.word()), EnglishGen::compareBytes));

        int n = list.size();
        byte[] blob = new byte[0];
        int[] off = new int[n + 1];
        int[] freq = new int[n];
        {
            int len = 0;
            for (Entry e : list) len += utf8.get(e.word()).length;
            blob = new byte[len];
            int p = 0;
            for (int i = 0; i < n; i++) {
                off[i] = p;
                byte[] b = utf8.get(list.get(i).word());
                System.arraycopy(b, 0, blob, p, b.length);
                p += b.length;
            }
            off[n] = p;
            for (int i = 0; i < n; i++) freq[i] = list.get(i).freq();
        }

        Path out = Path.of(outPath);
        if (out.getParent() != null) Files.createDirectories(out.getParent());
        try (DataOutputStream os = new DataOutputStream(
                new BufferedOutputStream(Files.newOutputStream(out), 1 << 20))) {
            os.writeBytes("JJE1");
            os.writeInt(n);
            os.writeInt(blob.length);
            os.write(blob);
            for (int v : off) os.writeInt(v);
            for (int v : freq) os.writeInt(v);
        }

        long ms = System.currentTimeMillis() - t0;
        System.out.println("总行 " + total + "（注释 " + comments + "，无效 " + invalid + "，重复 " + dup + "）");
        System.out.println("词条 " + n + "，最长词 " + longest(list));
        System.out.println("输出 " + out.toAbsolutePath() + " = " + Files.size(out) + " B（" + ms + " ms）");
        for (String p : new String[] {"a", "hel", "zh", "kubernetes"}) {
            System.out.println("抽查前缀 " + p + " = " + topOf(list, p, 5));
        }
    }

    static String longest(List<Entry> list) {
        String best = "";
        for (Entry e : list) if (e.word().length() > best.length()) best = e.word();
        return best;
    }

    static int compareBytes(byte[] a, byte[] b) {
        int m = Math.min(a.length, b.length);
        for (int i = 0; i < m; i++) {
            int c = (a[i] & 0xff) - (b[i] & 0xff);
            if (c != 0) return c;
        }
        return a.length - b.length;
    }

    /** 内存里抽查某前缀词频最高的几个（仅构建时打印用） */
    static String topOf(List<Entry> list, String prefix, int limit) {
        String q = prefix.toLowerCase();
        List<Entry> hits = new ArrayList<>();
        for (Entry e : list) if (e.word().startsWith(q)) hits.add(e);
        hits.sort((x, y) -> Integer.compare(y.freq(), x.freq()));
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(limit, hits.size()); i++) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(hits.get(i).word()).append('(').append(hits.get(i).freq()).append(')');
        }
        return sb.toString();
    }

    // —————— 抽查（近似 Android 端逻辑） ——————

    static void lookup(String binPath, String prefix) throws Exception {
        byte[] buf = Files.readAllBytes(Path.of(binPath));
        if (!(buf[0] == 'J' && buf[1] == 'J' && buf[2] == 'E' && buf[3] == '1'))
            throw new IllegalStateException("bad magic");
        int p = 4;
        int count = readInt(buf, p); p += 4;
        int blobLen = readInt(buf, p); p += 4;
        int blobPos = p; p += blobLen;
        int offPos = p; p += (count + 1) * 4;
        int freqPos = p;

        String q = prefix.toLowerCase();
        byte[] qb = q.getBytes(StandardCharsets.UTF_8);
        int lo = 0, hi = count;
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (compareWord(buf, blobPos, offPos, mid, qb) < 0) lo = mid + 1; else hi = mid;
        }
        System.out.println("词条 " + count + "，前缀 '" + prefix + "' 起点 " + lo);
        List<long[]> hits = new ArrayList<>(); // {freq, idx}
        for (int i = lo; i < count; i++) {
            if (!startsWith(buf, blobPos, offPos, i, qb)) break;
            hits.add(new long[] {readInt(buf, freqPos + i * 4), i});
        }
        hits.sort((x, y) -> Long.compare(y[0], x[0]));
        for (int k = 0; k < Math.min(15, hits.size()); k++) {
            int i = (int) hits.get(k)[1];
            System.out.println("  " + word(buf, blobPos, offPos, i) + "  " + hits.get(k)[0]);
        }
    }

    static int compareWord(byte[] buf, int blobPos, int offPos, int i, byte[] q) {
        int a = readInt(buf, offPos + i * 4);
        int b = readInt(buf, offPos + (i + 1) * 4);
        int len = b - a;
        int m = Math.min(len, q.length);
        for (int k = 0; k < m; k++) {
            int c = (buf[blobPos + a + k] & 0xff) - (q[k] & 0xff);
            if (c != 0) return c;
        }
        return len - q.length;
    }

    static boolean startsWith(byte[] buf, int blobPos, int offPos, int i, byte[] q) {
        int a = readInt(buf, offPos + i * 4);
        int b = readInt(buf, offPos + (i + 1) * 4);
        if (b - a < q.length) return false;
        for (int k = 0; k < q.length; k++) {
            if (buf[blobPos + a + k] != q[k]) return false;
        }
        return true;
    }

    static String word(byte[] buf, int blobPos, int offPos, int i) {
        int a = readInt(buf, offPos + i * 4);
        int b = readInt(buf, offPos + (i + 1) * 4);
        return new String(buf, blobPos + a, b - a, StandardCharsets.UTF_8);
    }

    static int readInt(byte[] buf, int p) {
        return ((buf[p] & 0xff) << 24) | ((buf[p + 1] & 0xff) << 16) | ((buf[p + 2] & 0xff) << 8) | (buf[p + 3] & 0xff);
    }
}
