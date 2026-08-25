package io.github.xxyopen.novel.crawl.crawl;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 站点爬取规则（对应 application.yml 中 novel-crawl.sources 下的一项）
 *
 * <p>页面元素定位使用 Jsoup 的 CSS 选择器语法，
 * 正则表达式用于从选择器命中的文本中做二次提取（取第 1 个捕获分组）。</p>
 */
@Data
public class CrawlRule {

    // 站点标识（提交爬取任务时用它选择规则）
    private String name;

    // 站点说明备注
    private String remark;

    // 按书籍URL前缀自动匹配站点规则的前缀列表（可配多个）
    private List<String> urlPrefixes = new ArrayList<>();

    // 页面编码（留空自动识别，常见值：UTF-8、GBK）
    private String charset;

    // 请求超时（毫秒）
    private Integer timeoutMs = 15000;

    // 每章抓取间隔（毫秒），限速防封
    private Long intervalMs = 500L;

    // 失败重试次数
    private Integer retry = 3;

    // 请求 User-Agent
    private String userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";

    // 书籍信息页规则
    private BookRule book = new BookRule();

    // 目录规则
    private TocRule toc = new TocRule();

    // 章节内容页规则
    private ChapterRule chapter = new ChapterRule();

    /**
     * 书籍信息页规则
     */
    @Data
    public static class BookRule {

        // 书名选择器
        private String nameSelector;

        // 书名提取正则（可空，从选择器命中文本中再提取第1个分组）
        private String nameRegex;

        // 作者选择器
        private String authorSelector;

        // 作者提取正则（可空）
        private String authorRegex;

        // 简介选择器
        private String descSelector;

        // 封面图片选择器
        private String picSelector;

        // 封面图片取值属性（默认 src，自动转为绝对地址）
        private String picAttr = "src";

        // 连载状态选择器（可空）
        private String statusSelector;

        // 状态文本中包含该关键字则认为书籍已完结
        private String finishedKeyword = "完结";
    }

    /**
     * 目录规则
     */
    @Data
    public static class TocRule {

        // 目录中章节链接的选择器（a 标签）
        private String itemSelector;

        // 跳过目录最前面的 N 条（有的站点目录顶部是"最新章节"重复区）
        private Integer skip = 0;

        // 是否按链接地址去重（同一URL保留最后一次出现，自动去掉顶部"最新章节"重复块）
        private Boolean dedupe = true;
    }

    /**
     * 章节内容页规则
     */
    @Data
    public static class ChapterRule {

        // 章节标题选择器（留空则使用目录里的链接文字）
        private String titleSelector;

        // 正文容器选择器
        private String contentSelector;

        // 解析前先从正文容器里移除的节点选择器（脚本、广告块、链接等）
        private String removeSelectors;

        // 按行过滤广告水印的正则列表（整行匹配任一正则即删除该行）
        private List<String> filterRegexes = new ArrayList<>();

        // 章节内容分页站点的"下一页"链接选择器（留空表示章节不分页）
        private String nextPageSelector;

        // "下一页"链接文字必须包含的关键字（避免误跟"下一章"）
        private String nextPageText = "下一页";

        // 单章最多翻页数（防御性上限，防止意外死循环）
        private Integer maxPages = 20;
    }

}
