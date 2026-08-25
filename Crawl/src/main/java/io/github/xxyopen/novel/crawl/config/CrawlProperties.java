package io.github.xxyopen.novel.crawl.config;

import io.github.xxyopen.novel.crawl.crawl.CrawlRule;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * 爬虫与导入的自定义配置（对应 application.yml 中 novel-crawl 前缀的配置项）
 */
@Data
@ConfigurationProperties(prefix = "novel-crawl")
public class CrawlProperties {

    // 入库时使用的默认作家ID（爬取/导入的书没有真实作家账号，统一挂在该ID下）
    private Long defaultAuthorId = 0L;

    // 默认评分（存库值 = 展示分 x 10，如 80 代表 8.0 分）
    private Integer defaultScore = 80;

    // 默认封面地址（TXT 导入或没爬到封面时写入 book_info.pic_url）
    private String defaultPicUrl = "/bookpic/default.png";

    // 默认书籍简介
    private String defaultBookDesc = "暂无简介";

    // 章节内容存储格式：html-每段带缩进并以 <br/><br/> 分段；text-纯文本
    private String contentFormat = "html";

    // 是否把爬到的封面图片下载到本地（false 则直接把源站图片地址写入数据库）
    private Boolean downloadCover = false;

    // 封面下载保存目录
    private String coverSavePath = "./covers";

    // 封面入库路径前缀（写入数据库的地址 = 前缀 + 文件名）
    private String coverUrlPrefix = "/bookpic/";

    // TXT 里找不到章节标题时，是否按固定字数自动分章
    private Boolean autoSplitWhenNoChapter = true;

    // 自动分章时每章的字数
    private Integer autoSplitWordCount = 3000;

    // TXT 导入批量入库时每批的章节数
    private Integer batchSize = 100;

    // 站点爬取规则列表
    private List<CrawlRule> sources = new ArrayList<>();

    /**
     * 按站点标识查找爬取规则
     *
     * @param name 站点标识（sources[].name）
     * @return 命中的站点规则，找不到返回 null
     */
    public CrawlRule findSourceByName(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        // 遍历规则列表按 name 精确匹配
        return sources.stream()
                .filter(s -> name.trim().equalsIgnoreCase(s.getName()))
                .findFirst()
                .orElse(null);
    }

    /**
     * 按书籍URL前缀自动匹配站点规则
     *
     * @param bookUrl 书籍目录页地址
     * @return 命中的站点规则，匹配不到返回 null
     */
    public CrawlRule matchSourceByUrl(String bookUrl) {
        if (bookUrl == null || bookUrl.isBlank()) {
            return null;
        }
        // 逐个站点比较 url-prefixes 前缀
        for (CrawlRule rule : sources) {
            if (rule.getUrlPrefixes() == null) {
                continue;
            }
            for (String prefix : rule.getUrlPrefixes()) {
                if (prefix != null && !prefix.isBlank() && bookUrl.startsWith(prefix.trim())) {
                    return rule;
                }
            }
        }
        return null;
    }

}
