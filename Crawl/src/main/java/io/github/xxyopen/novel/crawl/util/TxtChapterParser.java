package io.github.xxyopen.novel.crawl.util;

import io.github.xxyopen.novel.crawl.dto.ParsedChapter;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * TXT 章节切分器：用正则识别章节标题行，把整本 TXT 切分成章节列表
 */
public class TxtChapterParser {

    /**
     * 默认章节标题正则（按行匹配）。可识别：
     * <ul>
     *     <li>第X章/节/回/集/部/篇/卷 + 标题（X 支持阿拉伯数字与中文数字）</li>
     *     <li>序章、序言、楔子、引子、前言、尾声、后记、终章、大结局</li>
     *     <li>番外</li>
     * </ul>
     * 标题后缀里不允许出现句读（。？！；），用于排除正文中恰好以"第X章"开头的叙述句。
     */
    public static final String DEFAULT_CHAPTER_REGEX =
            "^[ \\t\u3000]{0,6}(?:"
                    + "第[0-9零〇一二三四五六七八九十百千万两 \\t\u3000]{1,12}[章节回集部篇卷][ \\t\u3000]{0,4}[^\\n。？！；]{0,40}"
                    + "|(?:序章|序言|楔子|引子|前言|尾声|后记|终章|大结局)(?:[ \\t\u3000][^\\n。？！；]{0,30})?"
                    + "|番外[^\\n。？！；]{0,34}"
                    + ")[ \\t\u3000]*$";

    // 卷标题正则（内容近似为空的纯卷标题行会被跳过，不作为章节入库）
    private static final Pattern VOLUME_PATTERN =
            Pattern.compile("^第[0-9零〇一二三四五六七八九十百千万两 \\t\u3000]{1,12}卷");

    // 纯卷标题行下方内容少于该字数时，认为它只是分卷标记
    private static final int VOLUME_CONTENT_LIMIT = 30;

    // 提取简介时需要排除的行（书名/作者/来源/网址等元信息）
    private static final Pattern DESC_EXCLUDE_PATTERN =
            Pattern.compile("^(书名|作者|来源|类别|分类|状态|更新|下载|整理|校对|简介[:：]?$)|(https?://|www\\.|[=—\\-_*#]{4,})");

    // 简介最大长度（book_info.book_desc 为 varchar(2000)，此处取更短的展示友好长度）
    private static final int DESC_MAX_LEN = 500;

    /**
     * 私有构造，工具类不允许实例化
     */
    private TxtChapterParser() {
    }

    /**
     * TXT 切分结果
     */
    @Data
    public static class ParseResult {

        // 第一个章节标题之前的文字（常见为书籍简介/元信息）
        private String preface = "";

        // 切分出的章节列表
        private List<ParsedChapter> chapters = new ArrayList<>();
    }

    /**
     * 把整本 TXT 文本切分成章节
     *
     * @param text        TXT 全文
     * @param customRegex 自定义章节标题正则（空则使用内置 DEFAULT_CHAPTER_REGEX）
     * @return 切分结果（找不到任何章节标题时 chapters 为空、preface 为全文）
     * @throws IllegalArgumentException 自定义正则语法错误时抛出
     */
    public static ParseResult parse(String text, String customRegex) {
        ParseResult result = new ParseResult();
        if (text == null || text.isBlank()) {
            return result;
        }
        // 统一换行符
        String content = text.replace("\r\n", "\n").replace('\r', '\n');
        // 编译章节标题正则（多行模式，逐行匹配）
        Pattern pattern;
        try {
            String regex = (customRegex == null || customRegex.isBlank()) ? DEFAULT_CHAPTER_REGEX : customRegex.trim();
            pattern = Pattern.compile(regex, Pattern.MULTILINE);
        } catch (Exception e) {
            throw new IllegalArgumentException("章节标题正则语法错误：" + e.getMessage());
        }
        // 先找出所有标题行的位置
        List<int[]> spans = new ArrayList<>();
        List<String> titles = new ArrayList<>();
        Matcher matcher = pattern.matcher(content);
        while (matcher.find()) {
            spans.add(new int[]{matcher.start(), matcher.end()});
            titles.add(ContentFormatUtil.stripWide(matcher.group()));
        }
        // 找不到任何章节标题：全文作为 preface 返回
        if (spans.isEmpty()) {
            result.setPreface(content);
            return result;
        }
        // 第一个标题之前的部分作为 preface
        result.setPreface(content.substring(0, spans.get(0)[0]));
        // 依次截取每个标题到下一个标题之间的正文
        for (int i = 0; i < spans.size(); i++) {
            int contentStart = spans.get(i)[1];
            int contentEnd = (i + 1 < spans.size()) ? spans.get(i + 1)[0] : content.length();
            String chapterContent = content.substring(contentStart, contentEnd).strip();
            String title = titles.get(i);
            // 纯卷标题（如"第一卷 风云再起"）且下方没有实际内容时跳过，不作为章节
            if (VOLUME_PATTERN.matcher(title).find()
                    && chapterContent.replaceAll("\\s+", "").length() < VOLUME_CONTENT_LIMIT) {
                continue;
            }
            result.getChapters().add(new ParsedChapter(title, chapterContent));
        }
        return result;
    }

    /**
     * 按固定字数自动分章（用于没有任何章节标题的 TXT）
     *
     * @param text          TXT 全文
     * @param wordsPerSplit 每章目标字数（在段落边界处切分）
     * @return 自动分出的章节列表（标题为"第N章"）
     */
    public static List<ParsedChapter> autoSplit(String text, int wordsPerSplit) {
        List<ParsedChapter> chapters = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return chapters;
        }
        // 统一换行符后按行（段落）累计
        String content = text.replace("\r\n", "\n").replace('\r', '\n');
        int limit = Math.max(500, wordsPerSplit);
        StringBuilder buffer = new StringBuilder();
        int count = 0;
        for (String line : content.split("\n")) {
            String stripped = ContentFormatUtil.stripWide(line);
            if (stripped.isEmpty()) {
                continue;
            }
            buffer.append(stripped).append('\n');
            count += stripped.length();
            // 达到目标字数就在当前段落后切一章
            if (count >= limit) {
                chapters.add(new ParsedChapter("第" + (chapters.size() + 1) + "章", buffer.toString().strip()));
                buffer.setLength(0);
                count = 0;
            }
        }
        // 收尾：剩余不足一章的内容单独成章
        if (!buffer.isEmpty()) {
            chapters.add(new ParsedChapter("第" + (chapters.size() + 1) + "章", buffer.toString().strip()));
        }
        return chapters;
    }

    /**
     * 从 preface（第一章之前的文字）中提取书籍简介
     *
     * @param preface 第一章之前的文字
     * @return 提取出的简介；内容太少时返回 null
     */
    public static String extractDesc(String preface) {
        if (preface == null || preface.isBlank()) {
            return null;
        }
        List<String> lines = new ArrayList<>();
        int total = 0;
        for (String line : preface.replace("\r\n", "\n").replace('\r', '\n').split("\n")) {
            String stripped = ContentFormatUtil.stripWide(line);
            // 去掉行首的"简介："前缀，只留简介正文
            stripped = stripped.replaceFirst("^简介[:：]\\s*", "");
            // 跳过空行与书名/作者/网址等元信息行
            if (stripped.isEmpty() || DESC_EXCLUDE_PATTERN.matcher(stripped).find()) {
                continue;
            }
            lines.add(stripped);
            total += stripped.length();
            // 超出简介长度上限就停止收集
            if (total >= DESC_MAX_LEN) {
                break;
            }
        }
        String desc = String.join("\n", lines);
        // 有效内容太少时视为没有简介
        if (desc.replaceAll("\\s+", "").length() < 10) {
            return null;
        }
        return ContentFormatUtil.truncate(desc, DESC_MAX_LEN);
    }

}
