import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 简记表情面板数据管线：青简 panel/catalog.tsv（表情行）→ app 资产 emoji.bin（分类 + 表情/颜文字）
 *
 * 用法（JDK 单文件运行）：
 *   java EmojiGen.java <catalog.tsv> <emoji.bin>
 *
 * catalog.tsv 每行：类别 \t 子类 \t 内容 \t 名称。本工具只取「表情」类别，
 * 按文件中首次出现的子类顺序分组（常用在最前），组内保持文件顺序。
 *
 * emoji.bin 布局（大端，全部 int 长度）：
 *   char[4] "JJM1" | int 分类数
 *   每分类：int 名称字节长 + 名称（UTF-8）| int 条数 | 每条：int 长度 + 内容（UTF-8）
 */
public class EmojiGen {

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            System.err.println("用法: java EmojiGen.java <catalog.tsv> <emoji.bin>");
            System.exit(2);
        }
        build(args[0], args[1]);
    }

    static void build(String tsvPath, String outPath) throws Exception {
        long t0 = System.currentTimeMillis();

        int total = 0, invalid = 0;
        Map<String, List<String>> cats = new LinkedHashMap<>();
        for (String raw : Files.readAllLines(Path.of(tsvPath), StandardCharsets.UTF_8)) {
            if (raw.isEmpty() || raw.startsWith("#")) continue;
            total++;
            String[] parts = raw.split("\t");
            if (parts.length < 4 || !parts[0].equals("表情")) continue;
            String sub = parts[1].trim();
            String item = parts[2].trim();
            if (sub.isEmpty() || item.isEmpty()) {
                invalid++;
                continue;
            }
            cats.computeIfAbsent(sub, k -> new ArrayList<>()).add(item);
        }

        int totalItems = cats.values().stream().mapToInt(List::size).sum();
        Path out = Path.of(outPath);
        if (out.getParent() != null) Files.createDirectories(out.getParent());
        try (DataOutputStream os = new DataOutputStream(
                new BufferedOutputStream(Files.newOutputStream(out), 1 << 16))) {
            os.writeBytes("JJM1");
            os.writeInt(cats.size());
            for (Map.Entry<String, List<String>> e : cats.entrySet()) {
                byte[] name = e.getKey().getBytes(StandardCharsets.UTF_8);
                os.writeInt(name.length);
                os.write(name);
                os.writeInt(e.getValue().size());
                for (String item : e.getValue()) {
                    byte[] b = item.getBytes(StandardCharsets.UTF_8);
                    os.writeInt(b.length);
                    os.write(b);
                }
            }
        }

        long ms = System.currentTimeMillis() - t0;
        System.out.println("总行 " + total + "（无效 " + invalid + "）");
        System.out.println("分类 " + cats.size() + "，条目 " + totalItems);
        for (Map.Entry<String, List<String>> e : cats.entrySet()) {
            List<String> v = e.getValue();
            int show = Math.min(6, v.size());
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < show; i++) {
                if (sb.length() > 0) sb.append(' ');
                sb.append(v.get(i));
            }
            System.out.println("  " + e.getKey() + " " + v.size() + " → " + sb);
        }
        System.out.println("输出 " + out.toAbsolutePath() + " = " + Files.size(out) + " B（" + ms + " ms）");
    }
}
