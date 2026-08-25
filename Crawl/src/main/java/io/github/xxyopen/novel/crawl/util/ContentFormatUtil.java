package io.github.xxyopen.novel.crawl.util;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 正文格式化工具：清洗广告行、格式化入库内容、统计字数
 */
public class ContentFormatUtil {

    // 入库为 html 格式时的段首缩进
    private static final String HTML_INDENT = "&nbsp;&nbsp;&nbsp;&nbsp;";

    // 入库为 html 格式时的段落分隔
    private static final String HTML_BREAK = "<br/><br/>";

    // HTML 不换行空格字符（&nbsp; 反转义后的字符）
    private static final char NBSP = '\u00A0';

    /**
     * 私有构造，工具类不允许实例化
     */
    private ContentFormatUtil() {
    }

    /**
     * 清洗正文：按行去除广告水印、去掉与章节标题重复的首行、去掉空行
     *
     * @param rawText 原始正文（换行分隔的纯文本）
     * @param filters 广告过滤正则（已编译，行内命中任一正则即删除该行，可为 null）
     * @param title   章节标题（正文首行与标题重复时删除，可为 null）
     * @return 清洗后的正文（非空行以 \n 连接）
     */
    public static String cleanContent(String rawText, List<Pattern> filters, String title) {
        if (rawText == null || rawText.isEmpty()) {
            return "";
        }
        // 统一换行符
        String text = rawText.replace("\r\n", "\n").replace('\r', '\n');
        String normalizedTitle = squeeze(title);
        List<String> lines = new ArrayList<>();
        for (String line : text.split("\n")) {
            String stripped = stripWide(line);
            // 跳过空行
            if (stripped.isEmpty()) {
                continue;
            }
            // 首行与章节标题重复时跳过（很多站点正文顶部会再写一遍标题）
            if (lines.isEmpty() && !normalizedTitle.isEmpty() && squeeze(stripped).equals(normalizedTitle)) {
                continue;
            }
            // 命中广告过滤正则时跳过
            if (matchAny(stripped, filters)) {
                continue;
            }
            lines.add(stripped);
        }
        return String.join("\n", lines);
    }

    /**
     * 把纯文本正文转成入库格式
     *
     * @param text          纯文本正文（段落以 \n 分隔）
     * @param contentFormat 存储格式：html-每段带缩进并以 &lt;br/&gt;&lt;br/&gt; 分段；text-原样纯文本
     * @return 入库用的内容字符串
     */
    public static String format(String text, String contentFormat) {
        if (text == null) {
            return "";
        }
        // 纯文本格式原样返回
        if ("text".equalsIgnoreCase(contentFormat)) {
            return text;
        }
        // html 格式：逐段加缩进和换行标签
        StringBuilder builder = new StringBuilder(text.length() + 1024);
        for (String line : text.split("\n")) {
            String stripped = stripWide(line);
            if (stripped.isEmpty()) {
                continue;
            }
            builder.append(HTML_INDENT).append(stripped).append(HTML_BREAK);
        }
        return builder.toString();
    }

    /**
     * 统计正文字数（去除所有空白字符后的字符数）
     *
     * @param text 纯文本正文
     * @return 字数
     */
    public static int countWords(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        int count = 0;
        // 逐字符统计非空白字符（NBSP 不属于 Java 的空白字符，需单独排除）
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (!Character.isWhitespace(c) && c != NBSP) {
                count++;
            }
        }
        return count;
    }

    /**
     * 截断字符串到指定长度（用于适配数据库字段长度）
     *
     * @param text   原字符串（可为 null）
     * @param maxLen 最大长度
     * @return 截断后的字符串
     */
    public static String truncate(String text, int maxLen) {
        if (text == null || text.length() <= maxLen) {
            return text;
        }
        return text.substring(0, maxLen);
    }

    /**
     * 去除字符串首尾空白（包含全角空格、NBSP、制表符等）
     *
     * @param text 原字符串
     * @return 去除首尾空白后的字符串
     */
    public static String stripWide(String text) {
        if (text == null) {
            return "";
        }
        // String.strip 能处理 Unicode 空白（含全角空格 　），NBSP 不算空白需先替换成普通空格
        return text.replace(NBSP, ' ').strip();
    }

    /**
     * 压缩字符串（去掉全部空白字符与 NBSP），用于标题比较
     *
     * @param text 原字符串
     * @return 去掉全部空白后的字符串
     */
    private static String squeeze(String text) {
        if (text == null) {
            return "";
        }
        return text.replace(NBSP, ' ').replaceAll("\\s+", "");
    }

    /**
     * 判断一行文本是否命中任一过滤正则（使用 find 匹配，便于书写简单的包含式正则）
     *
     * @param line    单行文本
     * @param filters 已编译的正则列表
     * @return true-命中 false-未命中
     */
    private static boolean matchAny(String line, List<Pattern> filters) {
        if (filters == null || filters.isEmpty()) {
            return false;
        }
        // 逐个正则做查找匹配
        for (Pattern pattern : filters) {
            if (pattern.matcher(line).find()) {
                return true;
            }
        }
        return false;
    }

}
