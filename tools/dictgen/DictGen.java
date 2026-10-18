import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * 简记词库数据管线：青简 dict.tsv → app 资产 dict.bin
 *
 * 用法（JDK 21 单文件运行）：
 *   java DictGen.java <dict.tsv> <dict.bin>
 *   java DictGen.java --lookup <dict.bin> <拼音>
 *
 * dict.tsv 每行： 词 \t 音节（空格分隔，小写字母，ü 写作 v） \t 词频；# 开头为注释。
 *
 * dict.bin 布局（大端）：
 *   char[4] "JJD1" | int count | int syllableCount |
 *   int pinyinBlobLen | int wordBlobLen | int initialsBlobLen | int syllablesBlobLen
 *   byte[pinyinBlobLen]    全拼连写（按 full 升序，同 full 词频降序）
 *   int[count+1]           pinyinBlob 内偏移
 *   byte[wordBlobLen]      词（UTF-8）
 *   int[count+1]           wordBlob 内偏移
 *   int[count]             词频
 *   byte[initialsBlobLen]  简拼（按简拼升序，同简拼词频降序）
 *   int[count+1]           initialsBlob 内偏移
 *   int[count]             简拼序 → 主表词条下标
 *   byte[syllablesBlobLen] 全部音节（升序，查询切分用）
 *   int[syllableCount+1]   syllablesBlob 内偏移
 *
 * --lookup 用与 Android 端近似的「全拼前缀 + 简拼前缀」查一下，仅作管线正确性抽查。
 */
public class DictGen {

    record Entry(String word, String full, String initials, int freq) {}

    public static void main(String[] args) throws Exception {
        if (args.length == 3 && args[0].equals("--lookup")) {
            lookup(args[1], args[2]);
            return;
        }
        if (args.length != 2) {
            System.err.println("用法: java DictGen.java <dict.tsv> <dict.bin>");
            System.err.println("      java DictGen.java --lookup <dict.bin> <拼音>");
            System.exit(2);
        }
        build(args[0], args[1]);
    }

    // —————— 构建 ——————

    static void build(String tsvPath, String outPath) throws Exception {
        long t0 = System.currentTimeMillis();

        int total = 0, comments = 0, invalid = 0, dedup = 0, freqZero = 0;
        Map<String, Entry> map = new HashMap<>(400_000);
        TreeSet<String> syllables = new TreeSet<>();

        for (String ln : Files.readAllLines(Path.of(tsvPath), StandardCharsets.UTF_8)) {
            if (ln.isEmpty()) continue;
            total++;
            if (ln.charAt(0) == '#') { comments++; continue; }
            String[] parts = ln.split("\t");
            if (parts.length < 3) { invalid++; continue; }
            String word = parts[0].trim();
            String sylRaw = parts[1].trim();
            int freq;
            try {
                freq = Integer.parseInt(parts[2].trim());
            } catch (NumberFormatException ex) {
                invalid++;
                continue;
            }
            if (word.isEmpty() || sylRaw.isEmpty()) { invalid++; continue; }

            StringBuilder full = new StringBuilder();
            StringBuilder ini = new StringBuilder();
            boolean bad = false;
            for (String s : sylRaw.split(" +")) {
                if (s.isEmpty()) continue;
                for (int i = 0; i < s.length(); i++) {
                    char c = s.charAt(i);
                    if (c < 'a' || c > 'z') { bad = true; break; }
                }
                if (bad) break;
                syllables.add(s);
                full.append(s);
                ini.append(s.charAt(0));
            }
            if (bad || full.length() == 0) { invalid++; continue; }
            if (freq <= 0) freqZero++;

            String key = word + "\u0000" + full;
            Entry e = new Entry(word, full.toString(), ini.toString(), Math.max(freq, 0));
            Entry old = map.get(key);
            if (old == null) {
                map.put(key, e);
            } else {
                dedup++;
                if (e.freq() > old.freq()) map.put(key, e);
            }
        }

        // 主表：按 full 升序、词频降序
        List<Entry> byPin = new ArrayList<>(map.values());
        byPin.sort(Comparator.comparing((Entry e) -> e.full())
                .thenComparing(Comparator.comparingInt(Entry::freq).reversed())
                .thenComparing(Entry::word));

        // 简拼索引：按 initials 升序、词频降序（存主表下标）
        Integer[] byIni = new Integer[byPin.size()];
        for (int i = 0; i < byPin.size(); i++) byIni[i] = i;
        java.util.Arrays.sort(byIni, Comparator.comparing((Integer i) -> byPin.get(i).initials())
                .thenComparing(i -> -byPin.get(i).freq())
                .thenComparing(i -> byPin.get(i).word()));

        // 组装字节块
        int n = byPin.size();
        byte[] pinBlob = new byte[0];
        int[] pinOff = new int[n + 1];
        {
            int len = 0;
            for (Entry e : byPin) len += e.full().length();
            pinBlob = new byte[len];
            int p = 0;
            for (int i = 0; i < n; i++) {
                pinOff[i] = p;
                byte[] b = byPin.get(i).full().getBytes(StandardCharsets.US_ASCII);
                System.arraycopy(b, 0, pinBlob, p, b.length);
                p += b.length;
            }
            pinOff[n] = p;
        }
        byte[] wordBlob;
        int[] wordOff = new int[n + 1];
        {
            int len = 0;
            for (Entry e : byPin) len += e.word().getBytes(StandardCharsets.UTF_8).length;
            wordBlob = new byte[len];
            int p = 0;
            for (int i = 0; i < n; i++) {
                wordOff[i] = p;
                byte[] b = byPin.get(i).word().getBytes(StandardCharsets.UTF_8);
                System.arraycopy(b, 0, wordBlob, p, b.length);
                p += b.length;
            }
            wordOff[n] = p;
        }
        int[] freqArr = new int[n];
        for (int i = 0; i < n; i++) freqArr[i] = byPin.get(i).freq();
        byte[] iniBlob;
        int[] iniOff = new int[n + 1];
        int[] iniIdx = new int[n];
        {
            int len = 0;
            for (Entry e : byPin) len += e.initials().length();
            iniBlob = new byte[len];
            int p = 0;
            for (int j = 0; j < n; j++) {
                int i = byIni[j];
                iniIdx[j] = i;
                iniOff[j] = p;
                byte[] b = byPin.get(i).initials().getBytes(StandardCharsets.US_ASCII);
                System.arraycopy(b, 0, iniBlob, p, b.length);
                p += b.length;
            }
            iniOff[n] = p;
        }
        List<String> sylList = new ArrayList<>(syllables);
        byte[] sylBlob;
        int[] sylOff = new int[sylList.size() + 1];
        {
            int len = 0;
            for (String s : sylList) len += s.length();
            sylBlob = new byte[len];
            int p = 0;
            for (int i = 0; i < sylList.size(); i++) {
                sylOff[i] = p;
                byte[] b = sylList.get(i).getBytes(StandardCharsets.US_ASCII);
                System.arraycopy(b, 0, sylBlob, p, b.length);
                p += b.length;
            }
            sylOff[sylList.size()] = p;
        }

        Path out = Path.of(outPath);
        if (out.getParent() != null) Files.createDirectories(out.getParent());
        try (DataOutputStream os = new DataOutputStream(
                new BufferedOutputStream(Files.newOutputStream(out), 1 << 20))) {
            os.writeBytes("JJD1");
            os.writeInt(n);
            os.writeInt(sylList.size());
            os.writeInt(pinBlob.length);
            os.writeInt(wordBlob.length);
            os.writeInt(iniBlob.length);
            os.writeInt(sylBlob.length);
            os.write(pinBlob);
            for (int v : pinOff) os.writeInt(v);
            os.write(wordBlob);
            for (int v : wordOff) os.writeInt(v);
            for (int v : freqArr) os.writeInt(v);
            os.write(iniBlob);
            for (int v : iniOff) os.writeInt(v);
            for (int v : iniIdx) os.writeInt(v);
            os.write(sylBlob);
            for (int v : sylOff) os.writeInt(v);
        }

        long ms = System.currentTimeMillis() - t0;
        System.out.println("总行 " + total + "（注释 " + comments + "，无效 " + invalid + "）");
        System.out.println("词条 " + n + "（去重 " + dedup + "，零频 " + freqZero + "）");
        System.out.println("音节 " + sylList.size() + " 个；最大词长 " + maxWordLen(byPin) + " 字");
        System.out.println("输出 " + out.toAbsolutePath() + " = " + Files.size(out) + " B（" + ms + " ms）");
        System.out.println("抽查 你好 = " + find(byPin, "你好") + "；中国 = " + find(byPin, "中国"));
    }

    static int maxWordLen(List<Entry> byPin) {
        int m = 0;
        for (Entry e : byPin) m = Math.max(m, e.word().length());
        return m;
    }

    static String find(List<Entry> byPin, String word) {
        for (Entry e : byPin) {
            if (e.word().equals(word)) return e.full() + " " + e.freq();
        }
        return "未找到";
    }

    // —————— 抽查（近似 Android 端逻辑；不带学习与权重） ——————

    static void lookup(String binPath, String query) throws Exception {
        byte[] buf = Files.readAllBytes(Path.of(binPath));
        int p = 0;
        if (!(buf[0] == 'J' && buf[1] == 'J' && buf[2] == 'D' && buf[3] == '1'))
            throw new IllegalStateException("bad magic");
        p = 4;
        int count = readInt(buf, p); p += 4;
        int sylCount = readInt(buf, p); p += 4;
        int pinLen = readInt(buf, p); p += 4;
        int wordLen = readInt(buf, p); p += 4;
        int iniLen = readInt(buf, p); p += 4;
        int sylLen = readInt(buf, p); p += 4;
        int pinPos = p; p += pinLen;
        int pinOffPos = p; p += (count + 1) * 4;
        int wordPos = p; p += wordLen;
        int wordOffPos = p; p += (count + 1) * 4;
        int freqPos = p; p += count * 4;
        int iniPos = p; p += iniLen;
        int iniOffPos = p; p += (count + 1) * 4;
        int iniIdxPos = p; p += count * 4;
        int sylPos = p; p += sylLen;
        int sylOffPos = p;

        String q = query.trim().toLowerCase();
        System.out.println("词条 " + count + "，音节 " + sylCount + "，查询 '" + q + "'");

        // 全拼前缀
        int lo = lowerBound(buf, pinPos, pinOffPos, count, q);
        List<int[]> hit = new ArrayList<>(); // {entryIdx, freq}
        for (int i = lo; i < count; i++) {
            String s = slice(buf, pinPos + readInt(buf, pinOffPos + i * 4), readInt(buf, pinOffPos + (i + 1) * 4) - readInt(buf, pinOffPos + i * 4));
            if (!s.startsWith(q)) break;
            hit.add(new int[]{i, readInt(buf, freqPos + i * 4)});
        }
        hit.sort((a, b) -> Integer.compare(b[1], a[1]));
        System.out.println("全拼前缀命中 " + hit.size() + "：");
        for (int i = 0; i < Math.min(20, hit.size()); i++) {
            int e = hit.get(i)[0];
            System.out.println("  " + word(buf, wordPos, wordOffPos, e) + "  "
                    + slice(buf, pinPos + readInt(buf, pinOffPos + e * 4), readInt(buf, pinOffPos + (e + 1) * 4) - readInt(buf, pinOffPos + e * 4))
                    + "  freq=" + readInt(buf, freqPos + e * 4));
        }

        if (q.length() >= 2) {
            List<int[]> hit2 = new ArrayList<>();
            int lo2 = lowerBound(buf, iniPos, iniOffPos, count, q);
            for (int j = lo2; j < count; j++) {
                String s = slice(buf, iniPos + readInt(buf, iniOffPos + j * 4), readInt(buf, iniOffPos + (j + 1) * 4) - readInt(buf, iniOffPos + j * 4));
                if (!s.startsWith(q)) break;
                int e = readInt(buf, iniIdxPos + j * 4);
                hit2.add(new int[]{e, readInt(buf, freqPos + e * 4)});
            }
            hit2.sort((a, b) -> Integer.compare(b[1], a[1]));
            System.out.println("简拼前缀命中 " + hit2.size() + "：");
            for (int i = 0; i < Math.min(20, hit2.size()); i++) {
                int e = hit2.get(i)[0];
                System.out.println("  " + word(buf, wordPos, wordOffPos, e) + "  "
                        + slice(buf, pinPos + readInt(buf, pinOffPos + e * 4), readInt(buf, pinOffPos + (e + 1) * 4) - readInt(buf, pinOffPos + e * 4))
                        + "  freq=" + readInt(buf, freqPos + e * 4));
            }
        }
    }

    /** buf[blob 起点] 序列中找第一个 >= q 的下标（blob 已按升序） */
    static int lowerBound(byte[] buf, int blobPos, int offPos, int count, String q) {
        int lo = 0, hi = count;
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            int a = readInt(buf, offPos + mid * 4);
            int b = readInt(buf, offPos + (mid + 1) * 4);
            int c = compareSlice(buf, blobPos + a, b - a, q);
            if (c < 0) lo = mid + 1;
            else hi = mid;
        }
        return lo;
    }

    static int compareSlice(byte[] buf, int pos, int len, String q) {
        int n = Math.min(len, q.length());
        for (int i = 0; i < n; i++) {
            int c = (buf[pos + i] & 0xff) - (q.charAt(i) & 0xff);
            if (c != 0) return c;
        }
        return len - q.length();
    }

    static String slice(byte[] buf, int pos, int len) {
        return new String(buf, pos, len, StandardCharsets.UTF_8);
    }

    static String word(byte[] buf, int wordPos, int offPos, int i) {
        int a = readInt(buf, offPos + i * 4);
        int b = readInt(buf, offPos + (i + 1) * 4);
        return slice(buf, wordPos + a, b - a);
    }

    static int readInt(byte[] buf, int p) {
        return ((buf[p] & 0xff) << 24) | ((buf[p + 1] & 0xff) << 16) | ((buf[p + 2] & 0xff) << 8) | (buf[p + 3] & 0xff);
    }
}
