import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/**
 * 简记释义表数据管线：青简 glossary-<语言>.tsv → app 资产 gloss-<语言>.bin
 *
 * 用法（JDK 21 单文件运行）：
 *   java GlossGen.java <glossary.tsv> <gloss.bin> [dict.bin]
 *   java GlossGen.java --lookup <gloss.bin> <词>
 *
 * 给了 dict.bin 时只保留词库里有的词（候选只可能来自词库，交集省体积）。
 *
 * glossary.tsv 每行： 词 \t [词性. ]译文[|读音] \t …（# 开头为注释；义项最多取前 2 条，
 * 与青简 Translation::MAX_SENSES 一致；`|` 后是读音，日语假名）。
 *
 * gloss.bin 布局（大端）：
 *   char[4] "JJG1" | int count | int senseCount | int wordBlobLen | int textBlobLen
 *   byte[wordBlobLen]      词（UTF-8，按字节序升序）
 *   int[count+1]           wordBlob 内偏移
 *   int[count+1]           义项表区间（词条 i 的义项是 [i, i+1)）
 *   byte[textBlobLen]      义项文本与读音（UTF-8）
 *   senseCount × 13 字节    义项：byte pos | int textOff | short textLen | int readOff | short readLen
 *
 * pos 词性码：0 无 / 1 n / 2 v / 3 adj / 4 adv / 5 pron / 6 prep / 7 conj / 8 num /
 *             9 m / 10 part / 11 int / 12 phr
 */
public class GlossGen {

    /** 词性码 ↔ 缩写（与青简 qingjian_core::PartOfSpeech 对齐；解析接受的别名也列在这里） */
    static final String[] POS_ABBREV = {
        "", "n.", "v.", "adj.", "adv.", "pron.", "prep.", "conj.", "num.", "m.", "part.", "int.", "phr.",
    };
    static final String[][] POS_ALIASES = {
        {}, {"n", "noun"}, {"v", "verb"}, {"adj"}, {"adv"}, {"pron"}, {"prep"}, {"conj"}, {"num"},
        {"m", "mw"}, {"part"}, {"int", "interj"}, {"phr", "phrase"},
    };

    record Sense(int pos, String text, String reading) {}

    public static void main(String[] args) throws Exception {
        if (args.length == 3 && args[0].equals("--lookup")) {
            lookup(args[1], args[2]);
            return;
        }
        if (args.length != 2 && args.length != 3) {
            System.err.println("用法: java GlossGen.java <glossary.tsv> <gloss.bin> [dict.bin]");
            System.err.println("      java GlossGen.java --lookup <gloss.bin> <词>");
            System.exit(2);
        }
        build(args[0], args[1], args.length == 3 ? args[2] : null);
    }

    // —————— 构建 ——————

    static void build(String tsvPath, String outPath, String dictPath) throws Exception {
        long t0 = System.currentTimeMillis();

        HashSet<String> dictWords = null;
        if (dictPath != null) {
            dictWords = readDictWords(dictPath);
            System.out.println("词库词表 " + dictWords.size() + " 个词");
        }

        int total = 0, comments = 0, invalid = 0, noSense = 0, dedup = 0, dropped = 0;
        Map<String, List<Sense>> map = new HashMap<>(300_000);

        for (String raw : Files.readAllLines(Path.of(tsvPath), StandardCharsets.UTF_8)) {
            if (raw.isEmpty()) continue;
            total++;
            if (raw.charAt(0) == '#') { comments++; continue; }
            String[] parts = raw.split("\t");
            if (parts.length < 2) { invalid++; continue; }
            String word = parts[0].trim();
            if (word.isEmpty()) { invalid++; continue; }
            List<Sense> senses = new ArrayList<>(2);
            for (int i = 1; i < parts.length && senses.size() < 2; i++) {
                String f = parts[i].trim();
                if (!f.isEmpty()) senses.add(parseSense(f));
            }
            if (senses.isEmpty()) { noSense++; continue; }
            if (dictWords != null && !dictWords.contains(word)) { dropped++; continue; }
            if (map.put(word, senses) != null) dedup++;
        }

        List<String> words = new ArrayList<>(map.keySet());
        // 按 UTF-8 字节序升序（Android 端按同一序二分）
        Map<String, byte[]> utf8 = new HashMap<>(words.size() * 2);
        for (String w : words) utf8.put(w, w.getBytes(StandardCharsets.UTF_8));
        words.sort((a, b) -> compareBytes(utf8.get(a), utf8.get(b)));

        int n = words.size();
        List<Sense> flat = new ArrayList<>(n * 2);
        int[] senseStart = new int[n + 1];
        for (int i = 0; i < n; i++) {
            senseStart[i] = flat.size();
            flat.addAll(map.get(words.get(i)));
        }
        senseStart[n] = flat.size();

        byte[] wordBlob = new byte[0];
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

        byte[] textBlob = new byte[0];
        int[] textOff = new int[flat.size()];
        int[] readOff = new int[flat.size()];
        short[] textLen = new short[flat.size()];
        short[] readLen = new short[flat.size()];
        {
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            for (int i = 0; i < flat.size(); i++) {
                Sense s = flat.get(i);
                byte[] tb = s.text().getBytes(StandardCharsets.UTF_8);
                byte[] rb = s.reading() == null ? new byte[0] : s.reading().getBytes(StandardCharsets.UTF_8);
                if (tb.length > 0xffff || rb.length > 0xffff) throw new IllegalStateException("文本过长: " + s.text());
                textOff[i] = bos.size();
                textLen[i] = (short) tb.length;
                bos.write(tb);
                readOff[i] = bos.size();
                readLen[i] = (short) rb.length;
                bos.write(rb);
            }
            textBlob = bos.toByteArray();
        }

        Path out = Path.of(outPath);
        if (out.getParent() != null) Files.createDirectories(out.getParent());
        try (DataOutputStream os = new DataOutputStream(
                new BufferedOutputStream(Files.newOutputStream(out), 1 << 20))) {
            os.writeBytes("JJG1");
            os.writeInt(n);
            os.writeInt(flat.size());
            os.writeInt(wordBlob.length);
            os.writeInt(textBlob.length);
            os.write(wordBlob);
            for (int v : wordOff) os.writeInt(v);
            for (int v : senseStart) os.writeInt(v);
            os.write(textBlob);
            for (int i = 0; i < flat.size(); i++) {
                os.writeByte(flat.get(i).pos());
                os.writeInt(textOff[i]);
                os.writeShort(textLen[i]);
                os.writeInt(readOff[i]);
                os.writeShort(readLen[i]);
            }
        }

        long ms = System.currentTimeMillis() - t0;
        System.out.println("总行 " + total + "（注释 " + comments + "，无效 " + invalid
                + "，无义项 " + noSense + "，重复 " + dedup
                + (dictWords != null ? "，不在词库 " + dropped : "") + "）");
        System.out.println("词条 " + n + "，义项 " + flat.size());
        System.out.println("输出 " + out.toAbsolutePath() + " = " + Files.size(out) + " B（" + ms + " ms）");
        for (String w : new String[] {"你好", "开发", "吃饭", "今天", "什么"}) {
            System.out.println("抽查 " + w + " = " + describe(map.get(w)));
        }
    }

    /** `v. develop` → 词性 + 译文；`n. 開発|かいはつ` → 读音。识别不出词性缩写时整段都是译文。 */
    static Sense parseSense(String field) {
        String text = field, reading = null;
        int bar = field.indexOf('|');
        if (bar >= 0 && !field.substring(bar + 1).trim().isEmpty()) {
            text = field.substring(0, bar).trim();
            reading = field.substring(bar + 1).trim();
        }
        int sp = text.indexOf(' ');
        if (sp > 0 && text.charAt(sp - 1) == '.') {
            int pos = parsePos(text.substring(0, sp - 1));
            if (pos > 0) return new Sense(pos, text.substring(sp + 1).trim(), reading);
        }
        return new Sense(0, text, reading);
    }

    static int parsePos(String head) {
        String h = head.trim().toLowerCase();
        for (int p = 1; p < POS_ALIASES.length; p++) {
            for (String a : POS_ALIASES[p]) if (a.equals(h)) return p;
        }
        return 0;
    }

    /** dict.bin 里全部词（只读 word blob 与偏移表） */
    static HashSet<String> readDictWords(String binPath) throws Exception {
        byte[] buf = Files.readAllBytes(Path.of(binPath));
        if (!(buf[0] == 'J' && buf[1] == 'J' && buf[2] == 'D' && buf[3] == '1'))
            throw new IllegalStateException("bad dict magic");
        int p = 4;
        int count = readInt(buf, p); p += 4;
        p += 4; // syllableCount
        int pinLen = readInt(buf, p); p += 4;
        int wordLen = readInt(buf, p); p += 4;
        p += 4 + 4;                         // initialsLen / syllablesLen
        p += pinLen + (count + 1) * 4;      // 全拼 blob + 偏移表
        int wordPos = p; p += wordLen;
        int wordOffPos = p;
        HashSet<String> set = new HashSet<>(count * 2);
        for (int i = 0; i < count; i++) {
            int a = readInt(buf, wordOffPos + i * 4);
            int b = readInt(buf, wordOffPos + (i + 1) * 4);
            set.add(new String(buf, wordPos + a, b - a, StandardCharsets.UTF_8));
        }
        return set;
    }

    static Comparator<byte[]> byteOrder() {
        return GlossGen::compareBytes;
    }

    static int compareBytes(byte[] a, byte[] b) {
        int m = Math.min(a.length, b.length);
        for (int i = 0; i < m; i++) {
            int c = (a[i] & 0xff) - (b[i] & 0xff);
            if (c != 0) return c;
        }
        return a.length - b.length;
    }

    static String describe(List<Sense> senses) {
        if (senses == null) return "无";
        StringBuilder sb = new StringBuilder();
        for (Sense s : senses) {
            if (sb.length() > 0) sb.append(" · ");
            if (s.pos() > 0) sb.append(POS_ABBREV[s.pos()]).append(' ');
            sb.append(s.text());
            if (s.reading() != null) sb.append('|').append(s.reading());
        }
        return sb.toString();
    }

    // —————— 抽查（近似 Android 端逻辑） ——————

    static void lookup(String binPath, String word) throws Exception {
        byte[] buf = Files.readAllBytes(Path.of(binPath));
        if (!(buf[0] == 'J' && buf[1] == 'J' && buf[2] == 'G' && buf[3] == '1'))
            throw new IllegalStateException("bad magic");
        int p = 4;
        int count = readInt(buf, p); p += 4;
        int senseCount = readInt(buf, p); p += 4;
        int wordLen = readInt(buf, p); p += 4;
        int textLen = readInt(buf, p); p += 4;
        int wordPos = p; p += wordLen;
        int wordOffPos = p; p += (count + 1) * 4;
        int senseStartPos = p; p += (count + 1) * 4;
        int textPos = p; p += textLen;
        int sensePos = p;

        System.out.println("词条 " + count + "，义项 " + senseCount + "，查询 '" + word + "'");
        byte[] qb = word.getBytes(StandardCharsets.UTF_8);
        int i = lowerBound(buf, wordPos, wordOffPos, count, qb);
        if (i >= count || !word(buf, wordPos, wordOffPos, i).equals(word)) {
            System.out.println("  未收录");
            return;
        }
        int a = readInt(buf, senseStartPos + i * 4);
        int b = readInt(buf, senseStartPos + (i + 1) * 4);
        for (int s = a; s < b; s++) {
            int q = sensePos + s * 13;
            int pos = buf[q] & 0xff;
            int to = readInt(buf, q + 1);
            int tl = readShort(buf, q + 5);
            int ro = readInt(buf, q + 7);
            int rl = readShort(buf, q + 11);
            System.out.println("  [" + (pos > 0 ? POS_ABBREV[pos] : "-") + "] "
                    + slice(buf, textPos + to, tl)
                    + (rl > 0 ? "|" + slice(buf, textPos + ro, rl) : ""));
        }
    }

    static int lowerBound(byte[] buf, int blobPos, int offPos, int count, byte[] q) {
        int lo = 0, hi = count;
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            int a = readInt(buf, offPos + mid * 4);
            int b = readInt(buf, offPos + (mid + 1) * 4);
            if (compareSlice(buf, blobPos + a, b - a, q) < 0) lo = mid + 1;
            else hi = mid;
        }
        return lo;
    }

    static int compareSlice(byte[] buf, int pos, int len, byte[] q) {
        int n = Math.min(len, q.length);
        for (int i = 0; i < n; i++) {
            int c = (buf[pos + i] & 0xff) - (q[i] & 0xff);
            if (c != 0) return c;
        }
        return len - q.length;
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

    static int readShort(byte[] buf, int p) {
        return ((buf[p] & 0xff) << 8) | (buf[p + 1] & 0xff);
    }
}
