package io.github.xxyopen.novel.crawl.util;

import io.github.xxyopen.novel.crawl.dto.ParsedChapter;
import org.junit.jupiter.api.Test;

import java.nio.charset.Charset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TXT 章节切分与编码识别的单元测试
 */
class TxtChapterParserTest {

    /**
     * 常规切分：阿拉伯数字与中文数字章节标题都能识别，简介归入 preface
     */
    @Test
    void testParseBasic() {
        String text = """
                这是一本测试小说的简介，讲述了一个测试的故事，情节跌宕起伏十分精彩。

                第1章 初入江湖
                少年背着行囊走出了村口。
                他回头望了一眼故乡。

                第二章 风云突变
                江湖险恶，人心难测。

                第三百二十一章 大战将起
                双方在山谷中对峙。
                """;
        TxtChapterParser.ParseResult result = TxtChapterParser.parse(text, null);
        // 三个章节都被识别
        assertEquals(3, result.getChapters().size());
        assertEquals("第1章 初入江湖", result.getChapters().get(0).getTitle());
        assertEquals("第二章 风云突变", result.getChapters().get(1).getTitle());
        assertEquals("第三百二十一章 大战将起", result.getChapters().get(2).getTitle());
        // 正文归属正确
        assertTrue(result.getChapters().get(0).getContent().contains("走出了村口"));
        // 简介在 preface 里
        assertTrue(result.getPreface().contains("测试小说的简介"));
    }

    /**
     * 特殊标题（楔子/番外）能识别，正文中以"第X章"开头的叙述句不会误切
     */
    @Test
    void testParseSpecialTitles() {
        String text = """
                楔子
                传说很久以前有一座山。

                第一章 开端
                第二章的内容还没有写完，他叹了口气说：这句话不该被切分。
                故事正式开始了。

                番外 若干年后
                多年以后的故事。
                """;
        TxtChapterParser.ParseResult result = TxtChapterParser.parse(text, null);
        // 楔子 + 第一章 + 番外 共三章（正文中带句读的"第二章…"叙述句不应被识别为标题）
        assertEquals(3, result.getChapters().size());
        assertEquals("楔子", result.getChapters().get(0).getTitle());
        assertTrue(result.getChapters().get(1).getContent().contains("不该被切分"));
    }

    /**
     * 纯卷标题行（下方无内容）不入库，卷下章节正常切分
     */
    @Test
    void testParseVolumeSkipped() {
        String text = """
                第一卷 风起
                第一章 少年
                少年出场了。

                第二章 老者
                老者出场了。
                """;
        TxtChapterParser.ParseResult result = TxtChapterParser.parse(text, null);
        // 纯卷标题被跳过，只留两个真实章节
        assertEquals(2, result.getChapters().size());
        assertEquals("第一章 少年", result.getChapters().get(0).getTitle());
    }

    /**
     * 无章节标题时返回空章节列表，全文进入 preface；自动分章能按字数切开
     */
    @Test
    void testAutoSplit() {
        StringBuilder builder = new StringBuilder();
        // 构造 40 段、每段 100 字的无标题文本
        for (int i = 0; i < 40; i++) {
            builder.append("测".repeat(100)).append('\n');
        }
        TxtChapterParser.ParseResult result = TxtChapterParser.parse(builder.toString(), null);
        assertTrue(result.getChapters().isEmpty());
        // 每章约 1000 字 -> 4000 字文本切成 4 章
        List<ParsedChapter> chapters = TxtChapterParser.autoSplit(builder.toString(), 1000);
        assertEquals(4, chapters.size());
        assertEquals("第1章", chapters.get(0).getTitle());
    }

    /**
     * 编码识别：GBK 与带 BOM 的 UTF-8 都能正确解码
     */
    @Test
    void testEncodingDetect() {
        String text = "第一章 中文编码测试，云海仙踪。";
        // GBK 字节流
        assertEquals(text, EncodingUtil.decode(text.getBytes(Charset.forName("GBK"))));
        // 带 BOM 的 UTF-8 字节流
        byte[] utf8 = text.getBytes(Charset.forName("UTF-8"));
        byte[] withBom = new byte[utf8.length + 3];
        withBom[0] = (byte) 0xEF;
        withBom[1] = (byte) 0xBB;
        withBom[2] = (byte) 0xBF;
        System.arraycopy(utf8, 0, withBom, 3, utf8.length);
        assertEquals(text, EncodingUtil.decode(withBom));
        // 不带 BOM 的 UTF-8 字节流
        assertEquals(text, EncodingUtil.decode(utf8));
    }

    /**
     * 入库格式化：html 格式带缩进与换行标签；字数统计不含空白
     */
    @Test
    void testContentFormat() {
        String text = "第一段内容。\n第二段内容。";
        String html = ContentFormatUtil.format(text, "html");
        assertEquals("&nbsp;&nbsp;&nbsp;&nbsp;第一段内容。<br/><br/>&nbsp;&nbsp;&nbsp;&nbsp;第二段内容。<br/><br/>", html);
        // 纯文本格式原样返回
        assertEquals(text, ContentFormatUtil.format(text, "text"));
        // 字数统计不含换行
        assertEquals(12, ContentFormatUtil.countWords(text));
    }

}
